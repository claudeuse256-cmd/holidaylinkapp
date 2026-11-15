package org.holidaylink.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.holidaylink.app.media.MediaEditor
import java.io.File

/**
 * The screen a picked photo, video or document goes through before it is sent.
 *
 * WhatsApp opens an editor too: you can look at what you picked, adjust it, add a
 * caption and only then hit send. Here the photo can be rotated, a video can be trimmed
 * (the samples are copied, so it stays fast and keeps its quality) and anything can get
 * a caption.
 */
@Composable
fun MediaEditorDialog(
    kind: String,
    file: File,
    fileName: String,
    mimeType: String,
    onCancel: () -> Unit,
    statusMode: Boolean = false,
    onAddMusic: () -> Unit = {},
    onAddSticker: () -> Unit = {},
    onSend: (caption: String, rotation: Int, trimStartMs: Long, trimEndMs: Long, cropZoom: Float, overlayText: String, strokes: List<List<Pair<Float, Float>>>, cropAspectRatio: Float) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var caption by remember { mutableStateOf("") }
    var rotation by remember { mutableIntStateOf(0) }
    var trimStart by remember { mutableLongStateOf(0L) }
    var trimEnd by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var preparing by remember { mutableStateOf(false) }
    var cropZoom by remember { mutableStateOf(1f) }
    var cropAspectRatio by remember(statusMode) { mutableStateOf(if (statusMode) 9f / 16f else 1f) }
    var overlayText by remember { mutableStateOf("") }
    var drawingEnabled by remember { mutableStateOf(true) }
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }

    val isImage = kind == "image"
    val isVideo = kind == "video"

    // Video length, read once when the editor opens.
    LaunchedEffect(file, isVideo) {
        if (!isVideo) return@LaunchedEffect
        duration = withContext(Dispatchers.IO) { MediaEditor.videoDurationMs(file) }
        if (duration > 0L) trimEnd = duration
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(scheme.surface)
        ) {
            // Title bar
            Row(
                Modifier.fillMaxWidth().background(if (statusMode) Color(0xFF080D16) else scheme.surface).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, "Cancel")
                }
                Text(
                    when {
                        isImage -> "Photo"
                        isVideo -> "Video"
                        else -> "Document"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Send",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (statusMode) Color(0xFF54D6C8) else scheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(enabled = !preparing) {
                            preparing = true
                            onSend(caption.trim(), rotation, trimStart, trimEnd, cropZoom, overlayText, strokes.map { line -> line.map { p -> (p.x / 10000f).coerceIn(0f,1f) to (p.y / 10000f).coerceIn(0f,1f) } }, cropAspectRatio)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (statusMode) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF101417)).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusEditorTool(Icons.Default.MusicNote, "Music", onAddMusic)
                    StatusEditorTool(Icons.Default.Crop, "Crop") { cropZoom = (cropZoom + 0.25f).coerceAtMost(2.5f) }
                    StatusEditorTool(Icons.Default.AddReaction, "Sticker", onAddSticker)
                    StatusEditorTool(Icons.Default.TextFields, "Text") { overlayText = if (overlayText.isBlank()) "Your text" else overlayText }
                    StatusEditorTool(Icons.Default.Edit, if (drawingEnabled) "Draw on" else "Draw off") { drawingEnabled = !drawingEnabled }
                }
            }

            // Preview
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (statusMode) Color(0xFF05080D) else scheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isImage -> {
                        EditorImagePreview(file, rotation, cropZoom)
                        Canvas(Modifier.fillMaxSize().pointerInput(drawingEnabled) {
                            if (drawingEnabled) detectDragGestures(
                                onDragStart = { point -> strokes.add(mutableStateListOf(Offset(point.x / size.width * 10000f, point.y / size.height * 10000f))) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    strokes.lastOrNull()?.add(Offset(change.position.x / size.width * 10000f, change.position.y / size.height * 10000f))
                                }
                            )
                        }) {
                            strokes.forEach { line ->
                                if (line.size >= 2) {
                                    val path = Path().apply { moveTo(line.first().x / 10000f * size.width, line.first().y / 10000f * size.height); line.drop(1).forEach { lineTo(it.x / 10000f * size.width, it.y / 10000f * size.height) } }
                                    drawPath(path, Color.White, style = Stroke(width = 5.dp.toPx()))
                                }
                            }
                        }
                        if (overlayText.isNotBlank()) Text(overlayText, color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp))
                    }
                    isVideo -> EditorVideoPreview(file, trimStart, trimEnd)
                    else -> EditorDocumentPreview(file, fileName, mimeType)
                }
            }

            if (preparing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // Tools
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isImage) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTool(Icons.Default.RotateLeft, "Rotate left") {
                            rotation = (rotation + 270) % 360
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedTool(Icons.Default.RotateRight, "Rotate right") {
                            rotation = (rotation + 90) % 360
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (rotation == 0) "Straight" else "Rotated $rotation°",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (isVideo && duration > 0L) {
                    Text(
                        "Trim the clip",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant
                    )
                    Text(
                        "From ${formatMs(trimStart)} to ${formatMs(trimEnd)} " +
                            "(${formatMs(trimEnd - trimStart)} long)",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    // WhatsApp-style filmstrip: real frames from the clip with two drag
                    // handles — not two abstract sliders.
                    TrimFilmstrip(
                        file = file,
                        durationMs = duration,
                        trimStartMs = trimStart,
                        trimEndMs = trimEnd
                    ) { start, end ->
                        trimStart = start
                        trimEnd = end
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (isImage) {
                    Text("Crop / zoom", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Slider(value = cropZoom, onValueChange = { cropZoom = it }, valueRange = 1f..2.5f)
                    OutlinedTextField(value = overlayText, onValueChange = { overlayText = it.take(120) }, label = { Text("Text on photo") }, singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("Draw with your finger on the photo preview", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        TextButton(onClick = { strokes.clear() }) { Text("Clear drawing") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // Caption
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (caption.isEmpty()) {
                        Text(
                            "Add a caption",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isVideo) "Videos keep their quality — only the chosen part is sent."
                    else if (isImage) "Photos are compressed for a fast send."
                    else "Documents are sent as they are.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OutlinedTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusEditorTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 5.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1B2228)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EditorImagePreview(file: File, rotation: Int, cropZoom: Float) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { MediaEditor.preview(file, 1280) }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(rotationZ = rotation.toFloat(), scaleX = cropZoom, scaleY = cropZoom)
        )
    } else {
        Text("Preview unavailable", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EditorVideoPreview(file: File, trimStartMs: Long, trimEndMs: Long) {
    val scheme = MaterialTheme.colorScheme
    var videoView by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }

    // The preview actually plays, WhatsApp-style: playback loops inside the selected
    // [trimStart, trimEnd] range so you see exactly what the other side will get.
    DisposableEffect(file) {
        onDispose { runCatching { videoView?.stopPlayback() } }
    }
    LaunchedEffect(trimStartMs, trimEndMs, playing) {
        while (playing) {
            delay(120)
            runCatching {
                val v = videoView ?: return@runCatching
                if (!v.isPlaying) return@runCatching
                val pos = v.currentPosition.toLong()
                when {
                    pos >= trimEndMs -> v.seekTo(trimStartMs.toInt())
                    pos < trimStartMs - 250L -> v.seekTo(trimStartMs.toInt())
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    setVideoPath(file.absolutePath)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        seekTo(trimStartMs.toInt())
                        start()
                        playing = true
                    }
                    setOnCompletionListener { playing = false }
                    videoView = this
                }
            },
            update = { v ->
                if (playing && !v.isPlaying) v.start()
                else if (!playing && v.isPlaying) v.pause()
            },
            modifier = Modifier.fillMaxSize()
        )
        // Tap the picture to pause/resume while editing.
        IconButton(
            onClick = {
                val v = videoView ?: return@IconButton
                if (v.isPlaying) { v.pause(); playing = false } else { v.start(); playing = true }
            },
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        Text(
            file.name,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
        )
    }
}

/**
 * The WhatsApp-style trimmer: a strip of real frames pulled from the clip, with two drag
 * handles selecting the part that gets sent (everything outside the handles is dimmed).
 */
@Composable
private fun TrimFilmstrip(
    file: File,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onChange: (Long, Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val frameCount = 8
    val frames by produceState(initialValue = emptyList<android.graphics.Bitmap?>(), file, durationMs) {
        value = withContext(Dispatchers.IO) {
            (0 until frameCount).map { i ->
                runCatching {
                    MediaEditor.videoThumbnail(file, durationMs * i / frameCount)
                }.getOrNull()
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val startFraction = if (durationMs > 0L) (trimStartMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val endFraction = if (durationMs > 0L) (trimEndMs.toFloat() / durationMs).coerceIn(0f, 1f) else 1f

        Row(Modifier.fillMaxSize()) {
            frames.forEachIndexed { i, bmp ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(scheme.surfaceContainerHighest)
                        )
                    }
                }
            }
        }
        // Dim what falls outside the selection.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(startFraction)
                .background(Color.Black.copy(alpha = 0.55f))
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(1f - endFraction)
                .background(Color.Black.copy(alpha = 0.55f))
        )
        // The two drag handles.
        TrimHandle(
            fraction = startFraction,
            widthPx = widthPx,
            onDrag = { deltaPx ->
                if (durationMs <= 0L) return@TrimHandle
                val deltaMs = (deltaPx / widthPx * durationMs).toLong()
                val next = (trimStartMs + deltaMs).coerceIn(0L, (trimEndMs - 500L).coerceAtLeast(0L))
                onChange(next, trimEndMs)
            }
        )
        TrimHandle(
            fraction = endFraction,
            widthPx = widthPx,
            onDrag = { deltaPx ->
                if (durationMs <= 0L) return@TrimHandle
                val deltaMs = (deltaPx / widthPx * durationMs).toLong()
                val next = (trimEndMs + deltaMs).coerceIn((trimStartMs + 500L).coerceAtMost(durationMs), durationMs)
                onChange(trimStartMs, next)
            }
        )
    }
}

/** One draggable trim handle (a slim grip bar at the edge of the selection). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.TrimHandle(
    fraction: Float,
    widthPx: Float,
    onDrag: (Float) -> Unit
) {
    val density = LocalDensity.current
    val handlePx = with(density) { 20.dp.toPx() }
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(20.dp)
            // Keep the whole grip inside the strip no matter where the selection sits.
            .offset { IntOffset((fraction * (widthPx - handlePx)).toInt(), 0) }
            .pointerInput(widthPx) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            }
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        // The little grip lines, so the handle reads as draggable.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.85f))
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
private fun EditorDocumentPreview(file: File, fileName: String, mimeType: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Description, null,
            tint = scheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            fileName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${humanSize(file.length())} • ${mimeType.ifBlank { "file" }}",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
    }
}

/** "1:23" style timestamp for the trim handles. */
private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** Human readable file size. */
fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> "0 KB"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
