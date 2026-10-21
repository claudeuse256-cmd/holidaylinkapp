package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.holidaylink.app.net.CallManager
import org.holidaylink.app.net.CallSounds
import org.holidaylink.app.net.User
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * WhatsApp-style call surface: full-bleed remote video (or avatar for audio), a small
 * self-preview during video calls, an incoming Answer/Decline ring, mute, speaker,
 * camera toggle, an end button and a running duration once connected.
 */
@Composable
fun CallScreen(
    manager: CallManager,
    user: User,
    callId: String,
    kind: String,
    incoming: Boolean,
    onClose: () -> Unit
) {
    val egl = remember { EglBase.create() }
    val context = LocalContext.current
    var state by remember { mutableStateOf(if (incoming) "ringing" else "calling") }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(kind == "video") }
    var camera by remember { mutableStateOf(true) }
    var remoteTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var localView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var answered by remember { mutableStateOf(!incoming) }
    var seconds by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        manager.onState = { state = it }
        manager.onRemote = { track ->
            remoteTrack = track
            val t = track
            val v = remoteView
            if (t != null && v != null) t.addSink(v)
        }
        manager.onLocal = { track ->
            localTrack = track
            val t = track
            val v = localView
            if (t != null && v != null) t.addSink(v)
        }
        // The caller's peer (and camera track) is created before this screen composes, so
        // the callbacks above would miss it: pull the current tracks now that they are wired.
        manager.currentLocalTrack?.let { localTrack = it }
        manager.currentRemoteTrack?.let { remoteTrack = it }
        manager.setSpeaker(speaker)
        onDispose {
            remoteView?.let { v -> remoteTrack?.removeSink(v); v.release() }
            localView?.let { v -> localTrack?.removeSink(v); v.release() }
            egl.release()
        }
    }

    // Attach each track as soon as both the track and its renderer exist: whichever of the
    // two arrives first, the other one's arrival re-runs this and wires them together.
    LaunchedEffect(remoteTrack, remoteView) {
        val t = remoteTrack
        val v = remoteView
        if (t != null && v != null) t.addSink(v)
    }
    LaunchedEffect(localTrack, localView) {
        val t = localTrack
        val v = localView
        if (t != null && v != null) t.addSink(v)
    }

    // Call duration, ticking once the call is actually connected.
    LaunchedEffect(state, answered) {
        if (answered && (state == "connected" || state == "accepted")) {
            while (true) {
                delay(1000)
                seconds++
            }
        }
    }

    // The other side hung up / declined: close this screen shortly after.
    LaunchedEffect(state) {
        if (state == "ended" || state == "declined" || state == "missed" || state == "failed") {
            delay(1200)
            onClose()
        }
    }

    // The phone being called gets the phone's own ringtone; the caller gets a ringback
    // tone so it is obvious the call is going out.
    var soundMode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(incoming, answered, state) {
        val mode = when {
            incoming && !answered -> "ringtone"
            !incoming && (state == "calling" || state == "ringing") -> "ringback"
            else -> null
        }
        if (mode != soundMode) {
            soundMode = mode
            when (mode) {
                "ringtone" -> CallSounds.startIncoming(context)
                "ringback" -> CallSounds.startRingback()
                else -> CallSounds.stop()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { CallSounds.stop() }
    }

    // While the other side has not picked up yet, the caller sees their own camera full
    // screen, exactly like WhatsApp, instead of a black rectangle.
    val selfViewFullScreen = kind == "video" && !incoming && remoteTrack == null

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (kind == "video") {
            if (remoteTrack != null) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(egl.eglBaseContext, null)
                            setEnableHardwareScaler(true)
                            setMirror(false)
                            remoteView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(egl.eglBaseContext, null)
                        setMirror(true)
                        setEnableHardwareScaler(true)
                        localView = this
                    }
                },
                modifier = if (selfViewFullScreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(width = 108.dp, height = 150.dp)
                        .clip(RoundedCornerShape(12.dp))
                }
            )
        }

        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            // While the caller sees their own camera full-screen (WhatsApp-style self view),
            // the avatar would just cover the video — so it is only drawn when there is no
            // live self view to show (audio calls, and video calls before/without video).
            if (!selfViewFullScreen && (kind != "video" || state == "ringing" || !answered || (remoteTrack == null && incoming))) {
                Avatar(name = user.displayName, photo = user.avatarUrl, size = 100.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                user.displayName,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                statusLabel(state, incoming, answered, seconds, kind),
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.weight(1f))

            if (incoming && !answered) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleAction(Color(0xFFD32F2F), Icons.Default.CallEnd, "Decline") {
                        manager.decline(callId)
                        onClose()
                    }
                    CircleAction(Color(0xFF16A34A), Icons.Default.Call, "Answer") {
                        answered = true
                        state = "connected"
                        manager.accept(callId, kind)
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(onClick = { muted = !muted; manager.toggleMute(muted) }) {
                        Icon(
                            if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                            if (muted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }
                    FilledIconButton(onClick = { speaker = !speaker; manager.setSpeaker(speaker) }) {
                        Icon(
                            if (speaker) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            "Speaker",
                            tint = Color.White
                        )
                    }
                    if (kind == "video") {
                        FilledIconButton(onClick = { camera = !camera; manager.toggleCamera(camera) }) {
                            Icon(
                                if (camera) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                "Camera",
                                tint = Color.White
                            )
                        }
                    }
                    FloatingActionButton(
                        onClick = { manager.end(); onClose() },
                        containerColor = Color(0xFFD32F2F)
                    ) { Icon(Icons.Default.CallEnd, "End", tint = Color.White) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CircleAction(color: Color, icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(66.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

private fun statusLabel(
    state: String,
    incoming: Boolean,
    answered: Boolean,
    seconds: Int,
    kind: String
): String = when (state) {
    // "Ringing" is the caller's side (the other phone is ringing); on the callee's side
    // it is the incoming call itself.
    "ringing" -> if (incoming && !answered) {
        if (kind == "video") "Incoming video call" else "Incoming audio call"
    } else {
        "Ringing…"
    }
    "calling" -> "Calling…"
    "accepted", "connected" ->
        if (seconds > 0) "%d:%02d".format(seconds / 60, seconds % 60) else "Connecting…"
    "connecting" -> "Connecting…"
    "declined" -> "Declined"
    "missed" -> "Missed"
    "ended" -> "Call ended"
    "failed" -> "Call failed"
    else -> state.replaceFirstChar { it.uppercase() }
}
