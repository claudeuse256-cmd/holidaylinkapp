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
 * The TURN relay every call depends on.
 *
 * STUN on its own cannot cross the carrier-grade NAT that most phones sit behind, so without a
 * working relay the two phones never find a path: the call rings, Firestore says "accepted", the
 * timer runs — and there is no audio and no video in either direction. That is exactly what the
 * previous configuration did, because `openrelay.metered.ca` was shut down and now resolves to
 * `100::1` / `192.0.2.1`, which are discard-only sinkhole addresses.
 *
 * Fill these three in from the Metered dashboard (the free tier is enough to test with). While
 * they are blank the app runs STUN-only: calls connect when both phones share one Wi-Fi network
 * and fail honestly everywhere else.
 */
private val TURN_HOST = org.holidaylink.app.BuildConfig.TURN_HOST
private val TURN_USERNAME = org.holidaylink.app.BuildConfig.TURN_USERNAME
private val TURN_PASSWORD = org.holidaylink.app.BuildConfig.TURN_PASSWORD

/** How long a picked-up call waits for media before it is declared dead. */
private const val LINK_TIMEOUT_MS = 25_000L

private fun iceServers(): List<PeerConnection.IceServer> {
    val servers = mutableListOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )
    if (TURN_HOST.isNotBlank() && TURN_USERNAME.isNotBlank()) {
        val host = TURN_HOST.removePrefix("turns:").removePrefix("turn:").trimEnd('/')
        // The TCP and TLS variants are not optional extras: plenty of carrier, campus and
        // hotel networks block UDP outright, so a UDP-only relay silently fails there.
        // This is exactly the URL set Metered hands out for its free TURN service.
        servers.add(
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer()
        )
        listOf(
            "turn:$host:80",
            "turn:$host:80?transport=tcp",
            "turn:$host:443",
            "turns:$host:443?transport=tcp"
        ).forEach { url ->
            servers.add(
                PeerConnection.IceServer.builder(url)
                    .setUsername(TURN_USERNAME)
                    .setPassword(TURN_PASSWORD)
                    .createIceServer()
            )
        }
    }
    return servers
}

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
    /**
     * The WebRTC link state (`connecting`, `connected`, `failed`…), kept apart from [onState].
     *
     * [onState] carries what Firestore says about the call — ringing, accepted, ended — and
     * "accepted" only means the other phone tapped Answer. Whether media is actually moving is
     * a separate fact, and blending the two is what let a call read as connected while it stayed
     * silent and black.
     */
    var onLink: ((String) -> Unit)? = null
    /** Last value passed to [onLink]; a screen that composes late reads the state from here. */
    @Volatile var linkState = "new"
        private set
    var onRemote: ((VideoTrack?) -> Unit)? = null
    var onLocal: ((VideoTrack?) -> Unit)? = null

    /**
     * The latest local/remote video tracks. The caller's peer (and with it the local camera
     * track) is created *before* the call screen composes and registers its callbacks, so a
     * screen that only listens would never receive the track and would show a black frame.
     * Reading these right after wiring the callbacks closes that race.
     */
    @Volatile var currentLocalTrack: VideoTrack? = null
        private set
    @Volatile var currentRemoteTrack: VideoTrack? = null
        private set

    /**
     * The EGL share group used by the codecs, the camera capturer and the screen renderers.
     *
     * They *must* share one root context: the capturer produces OpenGL textures, and a
     * renderer (or encoder) created from a different EglBase cannot sample them, which shows
     * up as a black self-preview and black remote video. Null while WebRTC is unavailable.
     */
    @Volatile var eglContext: EglBase.Context? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db get() = FirebaseBackend.db
    private var incomingReg: ListenerRegistration? = null
    private var callReg: ListenerRegistration? = null
    private var candReg: ListenerRegistration? = null
    private var pc: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    /** Shared GL context for the video codecs; must live as long as [factory]. */
    private var factoryEgl: EglBase? = null
    private var localVideo: VideoTrack? = null
    private var localAudio: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var currentCallId: String? = null
    private var currentKind = "audio"
    private var isCaller = false
    // Written from Firestore listener threads and read from the UI and the call watchdog, so
    // without @Volatile a stale value keeps a finished call alive or answers twice.
    @Volatile private var answerApplied = false
    @Volatile private var accepted = false
    /** The other person in the current call, so it can be written into the chat log. */
    private var peerId: String? = null
    /** Wall-clock moment the call was actually picked up; 0 while it is still ringing. */
    private var acceptedAtMs = 0L
    /** One log row per call: both the local hang-up and the remote one can fire. */
    private var callLogged = false
    /** Call id whose media watchdog is running, so it is never armed twice for one call. */
    @Volatile private var linkWatchdogCall: String? = null
    private val seenCandidates = mutableSetOf<String>()
    /** Call ids already rung, so a re-snapshot never rings the same call twice. */
    private val ringingCalls = mutableSetOf<String>()
    /** ICE that arrived before the remote description was set; applied once it is. */
    private val pendingCandidates = mutableListOf<IceCandidate>()
    @Volatile private var remoteSet = false

    fun connect() {
        if (factory != null) return
        factory = try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
            )
            // Without explicit codec factories no video codec is registered, and a video
            // offer then fails negotiation and takes the audio call down with it.
            val egl = EglBase.create()
            factoryEgl = egl
            eglContext = egl.eglBaseContext
            PeerConnectionFactory.builder()
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
                )
                .setVideoDecoderFactory(
                    DefaultVideoDecoderFactory(egl.eglBaseContext)
                )
                .createPeerConnectionFactory()
        } catch (_: Throwable) {
            // A device with no usable WebRTC native library must not take the whole app
            // down with it; calls simply stay unavailable until the next launch.
            null
        }
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
                    val created = d.safeTimestamp("createdAt") ?: return@forEach
                    if (System.currentTimeMillis() / 1000 - created.seconds > 60) return@forEach
                    val callerId = d.getString("callerId").orEmpty()
                    scope.launch {
                        val caller = runCatching { FirestoreChat.resolvePeer(callerId) }.getOrNull()
                            ?: User(callerId, "HolidayLink user", "")
                        // Watch the call while it rings too, so cancelling on the caller's
                        // side closes the ring screen instead of ringing forever.
                        watchCall(d.id, caller = false)
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
        cleanupCall()
        factory?.dispose(); factory = null
        runCatching { factoryEgl?.release() }; factoryEgl = null
        eglContext = null
        currentCallId = null
    }

    suspend fun start(calleeId: String, kind: String): CallRecord {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (kind == "video" && ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        ) {
            throw ApiException("Allow microphone${if (kind == "video") " and camera" else ""} permission, then try the call again.")
        }
        connect()
        // When the WebRTC native library could not start there is no point dialling: the
        // other phone would ring and hear nothing. Fail loudly instead.
        if (factory == null) throw ApiException("Calls couldn't start on this phone. Close the app, reopen it and try again.")
        currentKind = kind
        enterCallAudioMode()
        isCaller = true
        accepted = false
        peerId = calleeId
        acceptedAtMs = 0
        callLogged = false
        answerApplied = false
        remoteSet = false
        linkState = "new"
        pendingCandidates.clear()
        seenCandidates.clear()
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
                logCallToChat("missed")
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
        acceptedAtMs = System.currentTimeMillis()
        callLogged = false
        answerApplied = false
        remoteSet = false
        linkState = "new"
        // Answering used to skip the permission prompt entirely, so a callee who had never
        // placed a call sent silence (mic denied) or died on the camera (SecurityException).
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (kind == "video" && ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        ) {
            onState?.invoke("failed")
            return
        }
        enterCallAudioMode()
        scope.launch {
            var sdp = ""
            // The caller writes the SDP a beat after the ring doc appears, so retry: answering
            // fast used to hang because the offer was not there yet. Ten seconds, because on a
            // slow network three was often not enough and the call then failed silently.
            for (attempt in 0 until 40) {
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
                // The offer never arrived, so there is nothing to answer. Writing "accepted"
                // here is what produced a call that looked connected on both phones and carried
                // no media at all: without an answer SDP the caller never starts sending.
                runCatching { db.collection("calls").document(callId).update("status", "failed").await() }
                onState?.invoke("failed")
                cleanupCall()
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
        logCallToChat(if (missed) "missed" else "ended")
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
            // isSpeakerphoneOn is ignored unless the call audio mode is active, which is why
            // the speaker button appeared to do nothing.
            am?.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            am?.isSpeakerphoneOn = on
        }
    }

    /**
     * Puts the phone into call audio routing. Without this the microphone captures at
     * "media" volume and the voice comes out of the wrong speaker on most devices.
     */
    private fun enterCallAudioMode() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = currentKind == "video"
            // WebRTC plays into the voice-call stream, not the media one. A phone whose
            // voice-call volume happens to be at zero is silent even with a perfect
            // connection, so lift it to a level that can actually be heard.
            val stream = android.media.AudioManager.STREAM_VOICE_CALL
            val max = am.getStreamMaxVolume(stream)
            if (max > 0 && am.getStreamVolume(stream) * 2 < max) {
                am.setStreamVolume(stream, (max * 0.75f).toInt(), 0)
            }
        }
    }

    private fun leaveCallAudioMode() {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            am.isSpeakerphoneOn = false
            am.mode = android.media.AudioManager.MODE_NORMAL
        }
    }

    /**
     * Writes the finished call into the conversation so it shows up in the chat like WhatsApp.
     * Only the caller writes it, and only once, because the callee's phone sees the same
     * status change and would otherwise add a second row.
     */
    private fun logCallToChat(status: String) {
        if (!isCaller || callLogged) return
        val peer = peerId ?: return
        callLogged = true
        val seconds = if (acceptedAtMs > 0) {
            ((System.currentTimeMillis() - acceptedAtMs) / 1000).coerceAtLeast(0)
        } else 0
        val kind = currentKind
        scope.launch { runCatching { FirestoreChat.logCall(peer, kind, status, seconds) } }
    }

    private fun cleanupCall() {
        callReg?.remove(); callReg = null
        candReg?.remove(); candReg = null
        currentCallId = null
        answerApplied = false
        remoteSet = false
        linkWatchdogCall = null
        pendingCandidates.clear()
        seenCandidates.clear()
        leaveCallAudioMode()
        // Release camera/microphone resources on hang-up so the next call does not inherit
        // a busy camera or keep the microphone active in the background.
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null
        runCatching { localVideo?.dispose() }; localVideo = null
        runCatching { localAudio?.dispose() }; localAudio = null
        currentLocalTrack = null
        currentRemoteTrack = null
        runCatching { videoSource?.dispose() }; videoSource = null
        runCatching { audioSource?.dispose() }; audioSource = null
        runCatching { surfaceTextureHelper?.dispose() }; surfaceTextureHelper = null
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
                if (status == "accepted" || status == "connected") {
                    accepted = true
                    if (acceptedAtMs == 0L) acceptedAtMs = System.currentTimeMillis()
                    // Both phones have answered, so from here the only question is whether
                    // media actually flows. Start the clock on that.
                    armLinkWatchdog(callId)
                }
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
                if (status == "declined" || status == "ended" || status == "missed" || status == "failed") {
                    logCallToChat(status)
                    pc?.close(); pc = null
                    cleanupCall()
                }
            }
        // Remote description + ICE from the other side.
        candReg?.remove()
        // Re-read every candidate document: the peer may have been recreated (accept) and
        // the ones gathered during ringing are still the only path to the other phone.
        seenCandidates.clear()
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
        when (data["kind"] as? String) {
            "answer" -> {
                val p = pc ?: return
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
                    (data["sdpMLineIndex"] as? Long)?.toInt() ?: (data["sdpMLineIndex"] as? Int ?: 0),
                    data["candidate"] as? String ?: return
                )
                // Buffer until the peer exists and the offer/answer is set: adding early
                // silently fails, and dropping the candidate loses the connection entirely.
                val p = pc
                if (p != null && remoteSet) runCatching { p.addIceCandidate(cand) } else pendingCandidates.add(cand)
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
        // pendingCandidates is deliberately kept: ICE that arrived while the incoming call
        // was ringing is applied as soon as the answer's remote description lands.
        // STUN alone fails on many carrier NATs, which looked like "calls never connect".
        // A public TURN relay is added so audio/video can get through.
        val rtc = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceCandidatePoolSize = 2
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            // Keep gathering after the first candidates: on mobile data the first few often
            // fail and a later pair is what actually connects.
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        pc = f.createPeerConnection(rtc, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                sendSignal("candidate", cand = c)
            }
            override fun onTrack(t: RtpTransceiver) {
                val track = t.receiver.track()
                when (track) {
                    is VideoTrack -> {
                        currentRemoteTrack = track
                        onRemote?.invoke(track)
                    }
                    // The remote audio track is never drawn anywhere, but it still has to be
                    // enabled: an inbound receiver that starts disabled plays nothing at all,
                    // which is the "call is connected but completely silent" case.
                    is AudioTrack -> {
                        runCatching { track.setEnabled(true) }
                    }
                }
            }
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                setLink(s.name.lowercase())
            }
            override fun onSignalingChange(p: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(p: PeerConnection.IceConnectionState) {
                // IceConnectionState goes `failed` earlier and more reliably than
                // PeerConnectionState on a lot of devices, so it feeds the same channel.
                val s = p.name.lowercase()
                if (s == "failed" || s == "disconnected" || s == "connected" || s == "completed") {
                    setLink(if (s == "completed") "connected" else s)
                }
            }
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
        audioSource = audio
        localAudio = f.createAudioTrack("audio", audio)
        pc!!.addTrack(localAudio)
        if (currentKind == "video") {
            // A camera that refuses to start (another app holding it, permission revoked
            // mid-call) must not take the whole call down: fall back to audio only.
            runCatching {
                val shared = eglContext ?: throw IllegalStateException("no shared EGL context")
                val enumerator = Camera2Enumerator(context)
                val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                    ?: enumerator.deviceNames.firstOrNull()
                val capt = cameraName?.let { name -> enumerator.createCapturer(name, null) }
                if (capt != null) {
                    videoCapturer = capt
                    val helper = SurfaceTextureHelper.create("HL-Cam", shared)
                    surfaceTextureHelper = helper
                    val src = f.createVideoSource(false)
                    videoSource = src
                    capt.initialize(helper, context, src.capturerObserver)
                    capt.startCapture(640, 480, 24)
                    localVideo = f.createVideoTrack("video", src)
                    pc!!.addTrack(localVideo)
                    currentLocalTrack = localVideo
                    onLocal?.invoke(localVideo)
                }
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

    private fun setLink(state: String) {
        if (linkState == state) return
        linkState = state
        if (state == "connected" || state == "failed") linkWatchdogCall = null
        onLink?.invoke(state)
    }

    /**
     * A picked-up call whose media never arrives must not sit on "Connecting…" forever.
     * Once the answer is in, ICE gets [LINK_TIMEOUT_MS] to find a working path; after that the
     * call is ended and logged as one that did not connect, which is the truth.
     */
    private fun armLinkWatchdog(callId: String) {
        if (linkWatchdogCall == callId) return
        linkWatchdogCall = callId
        scope.launch {
            kotlinx.coroutines.delay(LINK_TIMEOUT_MS)
            if (currentCallId != callId || linkWatchdogCall != callId) return@launch
            linkWatchdogCall = null
            runCatching {
                db.collection("calls").document(callId)
                    .update(mapOf("status" to "failed", "endedAt" to Timestamp.now())).await()
            }
            logCallToChat("failed")
            setLink("failed")
            pc?.close(); pc = null
            cleanupCall()
        }
    }
}
