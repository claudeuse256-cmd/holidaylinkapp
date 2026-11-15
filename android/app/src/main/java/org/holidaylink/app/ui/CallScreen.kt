package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.holidaylink.app.net.CallManager
import org.holidaylink.app.net.CallSounds
import org.holidaylink.app.net.User
import org.webrtc.EglBase
import org.webrtc.RendererCommon
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
    onClose: () -> Unit,
    onMinimize: () -> Unit = {}
) {
    // Renderers sample the camera/decoder textures, so they have to live in the same EGL
    // share group as the capturer and codecs — a private EglBase here rendered black frames.
    // The fallback keeps audio-only and early-start cases safe.
    val sharedEglContext = remember { manager.eglContext }
    val ownEgl = remember { if (sharedEglContext == null) EglBase.create() else null }
    val eglContext = sharedEglContext ?: ownEgl!!.eglBaseContext
    val context = LocalContext.current
    // Two separate facts, deliberately not one variable. `signal` is what Firestore says about
    // the call (ringing, accepted, ended); `link` is whether WebRTC has a working media path.
    // The old code collapsed them and set "connected" the instant Answer was tapped, so a call
    // with no audio and a black screen still read as a live call with a running timer.
    var signal by remember { mutableStateOf(if (incoming) "ringing" else "calling") }
    var link by remember { mutableStateOf(manager.linkState) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(kind == "video") }
    var camera by remember { mutableStateOf(true) }
    var remoteTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var localView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var answered by remember { mutableStateOf(!incoming) }
    var seconds by remember { mutableIntStateOf(0) }
    var permissionNote by remember { mutableStateOf("") }

    val state = when {
        signal == "declined" || signal == "ended" || signal == "missed" -> signal
        link == "failed" || signal == "failed" -> "failed"
        link == "connected" -> "connected"
        signal == "accepted" -> "connecting"
        else -> signal
    }

    val callPermissions = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            answered = true
            manager.accept(callId, kind)
        } else {
            permissionNote = "Allow the microphone${if (kind == "video") " and camera" else ""} in Settings to answer calls."
        }
    }

    DisposableEffect(Unit) {
        // Remember whatever handler the app had before this screen took over. When the user
        // minimises the call, this screen leaves the composition but the call keeps running;
        // restoring the app-level handler lets it clear the call when the other side hangs up
        // instead of leaving a dead "Return to call" bar on screen.
        val previousOnState = manager.onState
        val previousOnLink = manager.onLink
        val previousOnRemote = manager.onRemote
        val previousOnLocal = manager.onLocal
        manager.onState = { signal = it }
        manager.onLink = { link = it }
        link = manager.linkState
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
            manager.onState = previousOnState
            manager.onLink = previousOnLink
            manager.onRemote = previousOnRemote
            manager.onLocal = previousOnLocal
            remoteView?.let { v -> remoteTrack?.removeSink(v); v.release() }
            localView?.let { v -> localTrack?.removeSink(v); v.release() }
            runCatching { ownEgl?.release() }
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

    // The duration counts real talk time: it starts when media actually flows, not the moment
    // Answer is tapped, otherwise a call that never connected still shows a running clock.
    LaunchedEffect(state) {
        if (state == "connected") {
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
                            init(eglContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setEnableHardwareScaler(true)
                            setMirror(false)
                            remoteView = this
                            remoteTrack?.addSink(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(eglContext, null)
                        setMirror(true)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        // Attach straight away: waiting for the LaunchedEffect below left the
                        // caller staring at a black screen while the other phone was ringing.
                        localTrack?.addSink(this)
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
            if (permissionNote.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    permissionNote,
                    color = Color(0xFFFFB4AB),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
            }
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
                        // Answering must ask for the microphone (and camera) too: without it
                        // the callee joins silently and hears nothing.
                        val needed = buildList {
                            add(android.Manifest.permission.RECORD_AUDIO)
                            if (kind == "video") add(android.Manifest.permission.CAMERA)
                        }.filter {
                            androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        if (needed.isEmpty()) {
                            answered = true
                            manager.accept(callId, kind)
                        } else {
                            callPermissions.launch(needed.toTypedArray())
                        }
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

        // Minimising keeps the call and its audio alive while the user reads a chat or checks
        // something else — the same "shrink, don't hang up" behaviour as WhatsApp.
        if (answered && state != "ended" && state != "declined" &&
            state != "missed" && state != "failed"
        ) {
            FilledIconButton(
                onClick = onMinimize,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Default.PictureInPictureAlt, "Minimise call", tint = Color.White)
            }
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
        if (seconds > 0) "%d:%02d".format(seconds / 60, seconds % 60) else "Connected"
    "connecting" -> "Connecting…"
    "declined" -> "Declined"
    "missed" -> "Missed"
    "ended" -> "Call ended"
    // Reached when ICE never found a path between the two phones. Saying "Call failed" without
    // a reason left people assuming the app was broken rather than the network blocking it.
    "failed" -> if (answered) "Couldn't connect — check your network" else "Call failed"
    else -> state.replaceFirstChar { it.uppercase() }
}

/**
 * The floating bar shown after minimising a call. It stays above the normal app UI and
 * brings the call back with one tap; the end button hangs up without reopening the screen.
 */
@Composable
fun MinimizedCallBar(user: User, onOpen: () -> Unit, onEnd: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Call in progress · tap to return",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEnd) {
                Icon(Icons.Default.CallEnd, "End call", tint = Color(0xFFD32F2F))
            }
        }
    }
}
