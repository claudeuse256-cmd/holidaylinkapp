package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import org.holidaylink.app.net.CallManager
import org.holidaylink.app.net.User
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(manager: CallManager, user: User, callId: String, kind: String, incoming: Boolean, onClose:()->Unit){
    val context=LocalContext.current
    val egl=remember { EglBase.create() }
    var state by remember { mutableStateOf(if(incoming) "ringing" else "calling") }
    var muted by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(true) }
    val remote=remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val local=remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    DisposableEffect(Unit){
        manager.onState={ state=it }
        manager.onRemote={ track -> remote.value?.let{ track?.addSink(it) } }
        manager.onLocal={ track -> local.value?.let{ track?.addSink(it) } }
        onDispose { remote.value?.release(); local.value?.release(); egl.release() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)){
        if(kind=="video") AndroidView({ SurfaceViewRenderer(context).apply{ init(egl.eglBaseContext,null); setMirror(true); setEnableHardwareScaler(true); remote.value=this } }, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment=Alignment.CenterHorizontally){
            Spacer(Modifier.height(32.dp)); Avatar(name=user.displayName,photo=user.avatarUrl,size=86.dp)
            Spacer(Modifier.height(14.dp)); Text(user.displayName,color=Color.White,style=MaterialTheme.typography.headlineSmall)
            Text(when(state){"ringing"->"Ringing…";"calling"->"Calling…";"connected"->"Connected";"declined"->"Declined";"ended"->"Call ended";else->state.replaceFirstChar{it.uppercase()}},color=Color.LightGray)
            Spacer(Modifier.weight(1f))
            if(incoming && state=="ringing") Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
                Button(onClick={manager.accept(callId,kind); state="connected"}){ Text("Answer") }
                OutlinedButton(onClick={manager.decline(callId);onClose()}){ Text("Decline") }
            } else Row(horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={muted=!muted;manager.toggleMute(muted)}){ Icon(if(muted) Icons.Default.MicOff else Icons.Default.Mic,null,tint=Color.White) }
                if(kind=="video") IconButton(onClick={camera=!camera;manager.toggleCamera(camera)}){ Icon(if(camera) Icons.Default.Videocam else Icons.Default.VideocamOff,null,tint=Color.White) }
                FloatingActionButton(onClick={manager.end();onClose()},containerColor=Color(0xFFD32F2F)){ Icon(Icons.Default.CallEnd,"End",tint=Color.White) }
            }
        }
    }
}
