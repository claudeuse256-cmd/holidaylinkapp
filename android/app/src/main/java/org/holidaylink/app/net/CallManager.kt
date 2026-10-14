package org.holidaylink.app.net

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.*

/**
 * Calls over Firestore signaling (same docs as the website room):
 * calls/{id} {callerId, calleeId, kind, status, offer, answer},
 * calls/{id}/callerCandidates + calleeCandidates {sdpMid, sdpMLineIndex, candidate}.
 * WebRTC carries the media peer-to-peer; Firestore only signals.
 */
class CallManager(private val context: Context, private val api: Api) {
    data class Incoming(val callId: String, val kind: String, val caller: User, val status: String)
    var onIncoming: ((Incoming) -> Unit)? = null
    var onState: ((String) -> Unit)? = null
    var onRemote: ((VideoTrack?) -> Unit)? = null
    var onLocal: ((VideoTrack?) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db get() = FirebaseBackend.db
    private var incomingReg: ListenerRegistration? = null
    private var callReg: ListenerRegistration? = null
    private var candReg: ListenerRegistration? = null
    private var pc: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var localVideo: VideoTrack? = null
    private var localAudio: AudioTrack? = null
    private var currentCallId: String? = null
    private var currentKind = "audio"
    private var isCaller = false
    private var answerApplied = false
    private var accepted = false
    private val seenCandidates = mutableSetOf<String>()
    /** Call ids already rung, so a re-snapshot never rings the same call twice. */
    private val ringingCalls = mutableSetOf<String>()
    /** ICE that arrived before the remote description was set; applied once it is. */
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteSet = false

    fun connect() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val uid = FirebaseBackend.uid ?: return
        // Calls addressed to this device. Only one equality filter: pairing it with
        // `status` needs a composite index that may not be deployed, which is exactly
        // why incoming calls never rang. Status/age are filtered in code instead.
        incomingReg?.remove()
        incomingReg = db.collection("calls")
            .whereEqualTo("calleeId", uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                snap.documentChanges.filter { it.type.name == "ADDED" }.forEach { change ->
                    val d = change.document
                    if (d.getString("status") != "ringing") return@forEach
                    if (!ringingCalls.add(d.id)) return@forEach
                    // Ignore stale rings (e.g. replayed from the on-disk cache on cold start).
                    val created = d.getTimestamp("createdAt") ?: return@forEach
                    if (System.currentTimeMillis() / 1000 - created.seconds > 60) return@forEach
                    val callerId = d.getString("callerId").orEmpty()
                    scope.launch {
                        val caller = runCatching { FirestoreChat.resolvePeer(callerId) }.getOrNull()
                            ?: User(callerId, "HolidayLink user", "")
                        onIncoming?.invoke(
                            Incoming(d.id, d.getString("kind") ?: "audio", caller, "ringing")
                        )
                    }
                }
            }
    }

    fun close() {
        incomingReg?.remove(); incomingReg = null
        callReg?.remove(); callReg = null
        candReg?.remove(); candReg = null
        pc?.close(); pc = null
        factory?.dispose(); factory = null
        currentCallId = null
    }

    suspend fun start(calleeId: String, kind: String): CallRecord {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (kind == "video" && ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        ) {
            throw ApiException("Allow microphone${if (kind == "video") " and camera" else ""} permission, then try the call again.")
        }
        connect()
        currentKind = kind
        isCaller = true
        val uid = FirebaseBackend.uid ?: throw ApiException("Sign in again first.")
        val ref = db.collection("calls").add(
            mapOf(
                "callerId" to uid,
                "calleeId" to calleeId,
                "kind" to kind,
                "status" to "ringing",
                "createdAt" to Timestamp.now()
            )
        ).await()
        currentCallId = ref.id
        createPeer(offer = true)
        watchCall(ref.id, caller = true)
        // Ring the callee's phone even when their app is closed.
        runCatching { PushNotify.call(ref.id) }
        // WhatsApp-like: no answer after ~45s is a missed call, not an endless ring.
        scope.launch {
            kotlinx.coroutines.delay(45_000)
            if (currentCallId == ref.id && !accepted) {
                runCatching {
                    db.collection("calls").document(ref.id)
                        .update(mapOf("status" to "missed", "endedAt" to Timestamp.now())).await()
                }
                onState?.invoke("missed")
            }
        }
        val peer = runCatching { FirestoreChat.resolvePeer(calleeId) }.getOrNull()
        return CallRecord(ref.id, peer, kind, "ringing", incoming = false, createdAt = "", durationSeconds = 0)
    }

    fun accept(callId: String, kind: String) {
        currentCallId = callId
        currentKind = kind
        isCaller = false
        accepted = true
        scope.launch {
            var sdp = ""
            // The caller writes the SDP a beat after the ring doc appears, so retry briefly:
            // answering fast used to hang because the offer was not there yet.
            for (attempt in 0 until 12) {
                val snap = runCatching { db.collection("calls").document(callId).get().await() }.getOrNull()
                @Suppress("UNCHECKED_CAST")
                val offer = snap?.get("offer") as? Map<String, String>
                sdp = offer?.get("sdp").orEmpty()
                if (sdp.isNotBlank()) break
                kotlinx.coroutines.delay(250)
            }
            createPeer(offer = false)
            if (sdp.isNotBlank()) {
                pc?.setRemoteDescription(
                    object : SdpObserverBase() {
                        override fun onSetSuccess() {
                            flushPendingCandidates()
                            pc?.createAnswer(object : SdpObserverBase() {
                                override fun onCreateSuccess(d: SessionDescription) {
                                    pc?.setLocalDescription(this, d)
                                    scope.launch {
                                        runCatching {
                                            db.collection("calls").document(callId)
                                                .update(
                                                    mapOf(
                                                        "answer" to mapOf("sdp" to d.description),
                                                        "status" to "accepted"
                                                    )
                                                )
                                                .await()
                                        }
                                    }
                                }
                            }, MediaConstraints())
                        }
                    },
                    SessionDescription(SessionDescription.Type.OFFER, sdp)
                )
            } else {
                runCatching { db.collection("calls").document(callId).update("status", "accepted").await() }
            }
        }
        watchCall(callId, caller = false)
    }

    fun decline(callId: String) {
        scope.launch {
            runCatching { db.collection("calls").document(callId).update("status", "declined").await() }
        }
        onState?.invoke("declined")
        cleanupCall()
    }

    fun end() {
        val id = currentCallId
        val missed = !accepted && isCaller
        if (id != null) {
            scope.launch {
                runCatching {
                    db.collection("calls").document(id)
                        .update(
                            mapOf(
                                "status" to if (missed) "missed" else "ended",
                                "endedAt" to Timestamp.now()
                            )
                        ).await()
                }
            }
        }
        pc?.close(); pc = null
        setSpeaker(false)
        onState?.invoke("ended")
        cleanupCall()
    }

    fun toggleMute(muted: Boolean) {
        localAudio?.setEnabled(!muted)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideo?.setEnabled(enabled)
    }

    /** Route audio to the loudspeaker (video calls / speakerphone) or the earpiece. */
    fun setSpeaker(on: Boolean) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            am?.isSpeakerphoneOn = on
        }
    }

    private fun cleanupCall() {
        callReg?.remove(); callReg = null
        candReg?.remove(); candReg = null
        currentCallId = null
        answerApplied = false
        remoteSet = false
        pendingCandidates.clear()
        seenCandidates.clear()
    }

    /** Remote description is in place: apply any ICE that arrived early. */
    private fun flushPendingCandidates() {
        remoteSet = true
        val p = pc ?: return
        pendingCandidates.toList().forEach { runCatching { p.addIceCandidate(it) } }
        pendingCandidates.clear()
    }

    private fun watchCall(callId: String, caller: Boolean) {
        callReg?.remove()
        callReg = db.collection("calls").document(callId)
            .addSnapshotListener { snap, _ ->
                val status = snap?.getString("status") ?: return@addSnapshotListener
                if (status == "accepted" || status == "connected") accepted = true
                onState?.invoke(status)
                // Caller picks up the callee's answer from the call doc.
                if (caller && !answerApplied) {
                    @Suppress("UNCHECKED_CAST")
                    val answer = snap.get("answer") as? Map<String, String>
                    val sdp = answer?.get("sdp").orEmpty()
                    if (sdp.isNotBlank()) {
                        answerApplied = true
                        pc?.setRemoteDescription(
                            object : SdpObserverBase() {
                                override fun onSetSuccess() { flushPendingCandidates() }
                            },
                            SessionDescription(SessionDescription.Type.ANSWER, sdp)
                        )
                    }
                }
                if (status == "declined" || status == "ended" || status == "missed") {
                    pc?.close(); pc = null
                    cleanupCall()
                }
            }
        // Remote description + ICE from the other side.
        candReg?.remove()
        val remoteCol = if (caller) "calleeCandidates" else "callerCandidates"
        candReg = db.collection("calls").document(callId).collection(remoteCol)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.filter { it.type.name == "ADDED" }?.forEach { change ->
                    val key = change.document.id
                    if (!seenCandidates.add(key)) return@forEach
                    applyRemoteSignal(change.document.data)
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyRemoteSignal(data: Map<String, Any?>) {
        val p = pc ?: return
        when (data["kind"] as? String) {
            "answer" -> {
                val sdp = data["sdp"] as? String ?: return
                p.setRemoteDescription(
                    object : SdpObserverBase() {
                        override fun onSetSuccess() { flushPendingCandidates() }
                    },
                    SessionDescription(SessionDescription.Type.ANSWER, sdp)
                )
            }
            "candidate" -> {
                val cand = IceCandidate(
                    data["sdpMid"] as? String,
                    (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0,
                    data["candidate"] as? String ?: return
                )
                // Buffer until the offer/answer is set; adding early silently fails otherwise.
                if (remoteSet) runCatching { p.addIceCandidate(cand) } else pendingCandidates.add(cand)
            }
        }
    }

    private fun signalDoc(kind: String, sdp: String? = null, cand: IceCandidate? = null): Map<String, Any?> {
        return when (kind) {
            "offer", "answer" -> mapOf("kind" to kind, "sdp" to (sdp.orEmpty()))
            else -> mapOf(
                "kind" to "candidate",
                "sdpMid" to (cand?.sdpMid ?: ""),
                "sdpMLineIndex" to cand?.sdpMLineIndex,
                "candidate" to (cand?.sdp ?: "")
            )
        }
    }

    private fun sendSignal(kind: String, sdp: String? = null, cand: IceCandidate? = null) {
        val id = currentCallId ?: return
        val col = if (isCaller) "callerCandidates" else "calleeCandidates"
        // Offers/answers ride the call doc; ICE rides candidate subcollections.
        if (kind == "offer") {
            scope.launch {
                db.collection("calls").document(id)
                    .update("offer", mapOf("sdp" to (sdp.orEmpty()))).await()
            }
            return
        }
        scope.launch {
            db.collection("calls").document(id).collection(col).add(signalDoc(kind, sdp, cand)).await()
        }
    }

    private fun createPeer(offer: Boolean) {
        val f = factory ?: return
        remoteSet = false
        pendingCandidates.clear()
        // STUN alone fails on many carrier NATs, which looked like "calls never connect".
        // A public TURN relay is added so audio/video can get through.
        val rtc = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                    .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                    .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceCandidatePoolSize = 2
        }
        pc = f.createPeerConnection(rtc, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                sendSignal("candidate", cand = c)
            }
            override fun onTrack(t: RtpTransceiver) {
                (t.receiver.track() as? VideoTrack)?.let { onRemote?.invoke(it) }
            }
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                onState?.invoke(s.name.lowercase())
            }
            override fun onSignalingChange(p: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(p: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(p: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c: Array<IceCandidate>) {}
            override fun onAddStream(s: MediaStream) {}
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver, m: Array<MediaStream>) {}
        }) ?: return
        val audio = f.createAudioSource(MediaConstraints())
        localAudio = f.createAudioTrack("audio", audio)
        pc!!.addTrack(localAudio)
        if (currentKind == "video") {
            val egl = EglBase.create()
            val capt = Camera2Enumerator(context).deviceNames.firstOrNull()?.let { name ->
                Camera2Enumerator(context).createCapturer(name, null)
            }
            if (capt != null) {
                val src = f.createVideoSource(false)
                capt.initialize(SurfaceTextureHelper.create("HL-Cam", egl.eglBaseContext), context, src.capturerObserver)
                capt.startCapture(640, 480, 24)
                localVideo = f.createVideoTrack("video", src)
                pc!!.addTrack(localVideo)
                onLocal?.invoke(localVideo)
            }
        }
        if (offer) {
            pc!!.createOffer(object : SdpObserverBase() {
                override fun onCreateSuccess(d: SessionDescription) {
                    pc?.setLocalDescription(this, d)
                    sendSignal("offer", sdp = d.description)
                }
            }, MediaConstraints())
        }
    }

    open class SdpObserverBase : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(s: String) {}
        override fun onSetFailure(s: String) {}
    }
}
