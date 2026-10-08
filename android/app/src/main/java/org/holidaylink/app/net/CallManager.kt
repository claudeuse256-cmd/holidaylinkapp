package org.holidaylink.app.net

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.webrtc.*
import okhttp3.*
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CallManager(private val context: Context, private val api: Api) {
    data class Incoming(val callId:String,val kind:String,val caller:User,val status:String)
    var onIncoming: ((Incoming)->Unit)? = null
    var onState: ((String)->Unit)? = null
    var onRemote: ((VideoTrack?)->Unit)? = null
    var onLocal: ((VideoTrack?)->Unit)? = null
    private val executor=Executors.newSingleThreadExecutor()
    private var ws: WebSocket?=null
    private var pc: PeerConnection?=null
    private var factory: PeerConnectionFactory?=null
    private var localVideo: VideoTrack?=null
    private var localAudio: AudioTrack?=null
    private var currentCallId:String?=null
    private var currentKind="audio"
    private var accepted=false

    fun connect() {
        if (ws!=null) return
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        factory=PeerConnectionFactory.builder().createPeerConnectionFactory()
        ws=OkHttpClient().newWebSocket(Request.Builder().url(api.realtimeUrl()).build(), object:WebSocketListener(){
            override fun onMessage(webSocket: WebSocket, text: String) { handleSignal(JSONObject(text)) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { ws=null; onState?.invoke("offline") }
            override fun onClosed(webSocket: WebSocket, code:Int, reason:String) { ws=null }
        })
    }

    fun close(){ ws?.close(1000,"bye"); ws=null; pc?.close(); pc=null; factory?.dispose(); factory=null }

    private fun send(o:JSONObject){ ws?.send(o.toString()) }

    suspend fun start(calleeId:String, kind:String):CallRecord {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || (kind=="video" && ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            throw ApiException("Allow microphone${if(kind=="video") " and camera" else ""} permission, then try the call again.")
        }
        connect(); currentKind=kind
        val call=api.startCall(calleeId,kind); currentCallId=call.id
        createPeer(true)
        return call
    }

    fun accept(callId:String, kind:String){ currentCallId=callId; currentKind=kind; accepted=true; createPeer(false); apiScope { api.updateCall(callId,"accepted") } }
    fun decline(callId:String){ apiScope { api.updateCall(callId,"declined") }; onState?.invoke("declined") }
    fun end(){ currentCallId?.let { apiScope { api.updateCall(it,"ended") } }; pc?.close(); pc=null; onState?.invoke("ended") }
    fun toggleMute(muted:Boolean){ localAudio?.setEnabled(!muted) }
    fun toggleCamera(enabled:Boolean){ localVideo?.setEnabled(enabled) }

    private fun apiScope(block:suspend()->Unit){ GlobalScope.launch(Dispatchers.IO) { runCatching { block() } } }

    private fun createPeer(offer:Boolean){
        val f=factory ?: return
        val rtc=PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )).apply { sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN }
        pc=f.createPeerConnection(rtc, object:PeerConnection.Observer{
            override fun onIceCandidate(c:IceCandidate){ send(JSONObject().put("type","call.signal").put("callId",currentCallId).put("signal",JSONObject().put("kind","candidate").put("sdpMid",c.sdpMid).put("sdpMLineIndex",c.sdpMLineIndex).put("candidate",c.sdp))) }
            override fun onTrack(t:RtpTransceiver){ (t.receiver.track() as? VideoTrack)?.let{ onRemote?.invoke(it) } }
            override fun onConnectionChange(s:PeerConnection.PeerConnectionState){ onState?.invoke(s.name.lowercase()) }
            override fun onSignalingChange(p:PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(p:PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(b:Boolean) {}
            override fun onIceGatheringChange(p:PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c:Array<IceCandidate>) {}
            override fun onAddStream(s:MediaStream) {}
            override fun onRemoveStream(s:MediaStream) {}
            override fun onDataChannel(d:DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r:RtpReceiver,m:Array<MediaStream>) {}
        }) ?: return
        val audio=f.createAudioSource(MediaConstraints()); localAudio=f.createAudioTrack("audio",audio); pc!!.addTrack(localAudio)
        if(currentKind=="video"){
            val egl=EglBase.create(); val capt=Camera2Enumerator(context).deviceNames.firstOrNull()?.let{ name-> Camera2Enumerator(context).createCapturer(name,null) }
            if(capt!=null){ val src=f.createVideoSource(false); capt.initialize(SurfaceTextureHelper.create("HL-Cam",egl.eglBaseContext),context,src.capturerObserver); capt.startCapture(640,480,24); localVideo=f.createVideoTrack("video",src); pc!!.addTrack(localVideo); onLocal?.invoke(localVideo) }
        }
        if(offer) pc!!.createOffer(object:SdpObserverBase(){ override fun onCreateSuccess(d:SessionDescription){ pc?.setLocalDescription(this,d); send(JSONObject().put("type","call.signal").put("callId",currentCallId).put("signal",JSONObject().put("kind","offer").put("sdp",d.description))) }}, MediaConstraints())
    }

    private fun handleSignal(o:JSONObject){
        when(o.optString("type")){
            "call.incoming" -> { val c=o.optJSONObject("caller") ?: return; onIncoming?.invoke(Incoming(o.optString("callId"),o.optString("kind","audio"),User(c.optString("id"),c.optString("displayName"),c.optString("username"),c.optString("avatarUrl").takeIf{it.isNotBlank()}),o.optString("status"))) }
            "call.state" -> onState?.invoke(o.optString("status"))
            "call.signal" -> handlePeerSignal(o.optJSONObject("signal") ?: return)
        }
    }
    private fun handlePeerSignal(s:JSONObject){
        val p=pc ?: return
        when(s.optString("kind")){
            "offer" -> p.setRemoteDescription(object:SdpObserverBase(){ override fun onSetSuccess(){ p.createAnswer(object:SdpObserverBase(){ override fun onCreateSuccess(d:SessionDescription){ p.setLocalDescription(this,d); send(JSONObject().put("type","call.signal").put("callId",currentCallId).put("signal",JSONObject().put("kind","answer").put("sdp",d.description))) }},MediaConstraints()) }}, SessionDescription(SessionDescription.Type.OFFER,s.getString("sdp")))
            "answer" -> p.setRemoteDescription(SdpObserverBase(),SessionDescription(SessionDescription.Type.ANSWER,s.getString("sdp")))
            "candidate" -> p.addIceCandidate(IceCandidate(s.optString("sdpMid"),s.optInt("sdpMLineIndex"),s.getString("candidate")))
        }
    }
    open class SdpObserverBase: SdpObserver { override fun onCreateSuccess(d:SessionDescription){}; override fun onSetSuccess(){}; override fun onCreateFailure(s:String){}; override fun onSetFailure(s:String){} }
}
