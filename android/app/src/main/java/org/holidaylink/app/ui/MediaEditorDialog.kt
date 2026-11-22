package org.holidaylink.app.ui
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.holidaylink.app.media.CropRect
import org.holidaylink.app.media.MediaEditor
import org.holidaylink.app.media.PlacedOverlay
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * The screen a picked photo, video or document goes through before it is sent.
 *
 * WhatsApp opens an editor too: you can look at what you picked, frame a real crop, draw
 * (only once you pick the pen), drop text and stickers you can drag and resize, trim a
 * video, add a caption, and only then hit send.
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
    onSend: (
        caption: String,
        rotation: Int,
        trimStartMs: Long,
        trimEndMs: Long,
        cropRect: CropRect,
        overlays: List<PlacedOverlay>,
        strokes: List<List<Pair<Float, Float>>>
    ) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var caption by remember { mutableStateOf("") }
    var rotation by remember { mutableIntStateOf(0) }
    var trimStart by remember { mutableLongStateOf(0L) }
    var trimEnd by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var preparing by remember { mutableStateOf(false) }

    // Editor "tool mode": only one of these is active at a time, and nothing draws or
    // crops until the user explicitly selects the matching tool — exactly like WhatsApp,
    // where picking up the pen is a deliberate step, not the default gesture on the photo.
    var tool by remember { mutableStateOf(EditorTool.NONE) }

    // Real crop: a fixed guide frame sits over the preview; the image behind it pans and
    // pinch-zooms while the frame stays put. What's inside the frame when the user is done
    // is what gets cropped — not a center-crop derived from a zoom slider.
    var cropAspectRatio by remember(statusMode) { mutableStateOf(if (statusMode) 9f / 16f else 1f) }
    var imageScale by remember { mutableFloatStateOf(1f) }
    var imageOffset by remember { mutableStateOf(Offset.Zero) }
    var sourceSize by remember { mutableStateOf(IntSize.Zero) }
    var previewBoxSize by remember { mutableStateOf(IntSize.Zero) }

    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    val overlays = remember { mutableStateListOf<EditableOverlay>() }
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }
    var nextOverlaySeq by remember { mutableIntStateOf(0) }
    var textEditorFor by remember { mutableStateOf<String?>(null) }
    var stickerPicker by remember { mutableStateOf(false) }

    val isImage = kind == "image"
    val isVideo = kind == "video"

    // Video length, read once when the editor opens.
    LaunchedEffect(file, isVideo) {
        if (!isVideo) return@LaunchedEffect
        duration = withContext(Dispatchers.IO) { MediaEditor.videoDurationMs(file) }
        if (duration > 0L) trimEnd = duration
    }

    fun addTextOverlay() {
        val id = "text_${nextOverlaySeq++}"
        overlays.add(EditableOverlay(id = id, kind = "text", text = "Tap to edit"))
        selectedOverlayId = id
        textEditorFor = id
    }

    fun addStickerOverlay(ref: String) {
        val id = "sticker_${nextOverlaySeq++}"
        overlays.add(EditableOverlay(id = id, kind = "sticker", stickerRef = ref))
        selectedOverlayId = id
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
                            val cropRect = computeCropRect(
                                sourceSize = sourceSize,
                                previewBoxSize = previewBoxSize,
                                imageScale = imageScale,
                                imageOffset = imageOffset,
                                cropAspectRatio = cropAspectRatio
                            )
                            val finalOverlays = overlays.map {
                                PlacedOverlay(
                                    id = it.id,
                                    kind = it.kind,
                                    text = it.text,
                                    colorArgb = it.colorArgb,
                                    stickerRef = it.stickerRef,
                                    x = it.x,
                                    y = it.y,
                                    scale = it.scale,
                                    rotationDeg = it.rotationDeg
                                )
                            }
                            onSend(
                                caption.trim(),
                                rotation,
                                trimStart,
                                trimEnd,
                                cropRect,
                                finalOverlays,
                                strokes.map { line -> line.map { p -> (p.x / 10000f).coerceIn(0f, 1f) to (p.y / 10000f).coerceIn(0f, 1f) } }
                            )
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
                    StatusEditorTool(Icons.Default.MusicNote, "Music", active = false, onClick = onAddMusic)
                    StatusEditorTool(Icons.Default.Crop, "Crop", active = tool == EditorTool.CROP) {
                        tool = if (tool == EditorTool.CROP) EditorTool.NONE else EditorTool.CROP
                        selectedOverlayId = null
                    }
                    StatusEditorTool(Icons.Default.AddReaction, "Sticker", active = stickerPicker) {
                        stickerPicker = true
                    }
                    StatusEditorTool(Icons.Default.TextFields, "Text", active = false) {
                        tool = EditorTool.NONE
                        addTextOverlay()
                    }
                    StatusEditorTool(Icons.Default.Edit, "Draw", active = tool == EditorTool.DRAW) {
                        tool = if (tool == EditorTool.DRAW) EditorTool.NONE else EditorTool.DRAW
                        selectedOverlayId = null
                    }
                }
            }

            // Preview
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (statusMode) Color(0xFF05080D) else scheme.surfaceContainerLowest)
                    .onSizeChanged { previewBoxSize = it },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isImage -> {
                        EditorImagePreview(
                            file = file,
                            rotation = rotation,
                            scale = imageScale,
                            offset = imageOffset,
                            panZoomEnabled = tool == EditorTool.CROP,
                            onSourceSize = { sourceSize = it },
                            onTransform = { scaleDelta, panDelta ->
                                imageScale = (imageScale * scaleDelta).coerceIn(1f, 5f)
                                imageOffset = clampPanOffset(imageOffset + panDelta, imageScale, previewBoxSize)
                            }
                        )

                        // Drawing layer: only live when the Draw tool is explicitly selected.
                        Canvas(Modifier.fillMaxSize().pointerInput(tool) {
                            if (tool == EditorTool.DRAW) detectDragGestures(
                                onDragStart = { point ->
                                    strokes.add(mutableStateListOf(Offset(point.x / size.width * 10000f, point.y / size.height * 10000f)))
                                },
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

                        // Crop guide frame: fixed on screen while the image moves behind it.
                        if (tool == EditorTool.CROP) {
                            CropGuideOverlay(aspectRatio = cropAspectRatio)
                        }

                        // Placed text/sticker overlays: each draggable and pinch-resizable.
                        overlays.forEach { overlay ->
                            key(overlay.id) {
                                DraggableOverlayItem(
                                    overlay = overlay,
                                    selected = selectedOverlayId == overlay.id,
                                    enabled = tool != EditorTool.DRAW && tool != EditorTool.CROP,
                                    onSelect = { selectedOverlayId = overlay.id },
                                    onEditText = { textEditorFor = overlay.id },
                                    onDelete = {
                                        overlays.remove(overlay)
                                        if (selectedOverlayId == overlay.id) selectedOverlayId = null
                                    },
                                    onMove = { dx, dy ->
                                        val boxW = previewBoxSize.width.coerceAtLeast(1)
                                        val boxH = previewBoxSize.height.coerceAtLeast(1)
                                        overlay.x = (overlay.x + dx / boxW).coerceIn(0f, 1f)
                                        overlay.y = (overlay.y + dy / boxH).coerceIn(0f, 1f)
                                    },
                                    onScaleRotate = { scaleDelta, rotDelta ->
                                        overlay.scale = (overlay.scale * scaleDelta).coerceIn(0.3f, 4f)
                                        overlay.rotationDeg += rotDelta
                                    }
                                )
                            }
                        }
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
                    if (!statusMode) {
                        // Chat attachments: keep the same tools, just without the dedicated
                        // status toolbar strip — crop/draw/text are reachable here instead.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedToolLabeled(Icons.Default.Crop, "Crop", active = tool == EditorTool.CROP) {
                                tool = if (tool == EditorTool.CROP) EditorTool.NONE else EditorTool.CROP
                            }
                            OutlinedToolLabeled(Icons.Default.Edit, "Draw", active = tool == EditorTool.DRAW) {
                                tool = if (tool == EditorTool.DRAW) EditorTool.NONE else EditorTool.DRAW
                            }
                            OutlinedToolLabeled(Icons.Default.TextFields, "Text", active = false) {
                                tool = EditorTool.NONE
                                addTextOverlay()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (tool == EditorTool.CROP) {
                        Text("Drag to reposition, pinch to zoom — the frame shows exactly what will be kept.", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    } else if (tool == EditorTool.DRAW) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("Draw with your finger on the photo", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                            TextButton(onClick = { strokes.clear() }) { Text("Clear drawing") }
                        }
                    } else if (overlays.isNotEmpty()) {
                        Text("Drag text or stickers to move them, pinch with two fingers to resize.", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
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

    val editingTextId = textEditorFor
    if (editingTextId != null) {
        val overlay = overlays.firstOrNull { it.id == editingTextId }
        if (overlay != null) {
            TextOverlayEditorDialog(
                initialText = overlay.text,
                onDismiss = { textEditorFor = null },
                onConfirm = { newText ->
                    if (newText.isBlank()) overlays.remove(overlay) else overlay.text = newText.take(120)
                    textEditorFor = null
                }
            )
        } else {
            textEditorFor = null
        }
    }

    if (stickerPicker) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val packs = remember { StickerCatalog.packs(context) }
        var selectedPack by remember { mutableStateOf(packs.firstOrNull()?.id.orEmpty()) }
        val currentPack = packs.firstOrNull { it.id == selectedPack } ?: packs.firstOrNull()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { stickerPicker = false },
            title = { Text("Animated stickers") },
            text = {
                Column {
                    if (packs.size > 1) {
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            androidx.compose.foundation.lazy.items(packs, key = { it.id }) { pack ->
                                val active = pack.id == currentPack?.id
                                Column(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .clickable { selectedPack = pack.id }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    pack.stickers.firstOrNull()?.let { preview ->
                                        AnimatedSticker(url = preview.ref, assetPath = preview.assetPath, resId = preview.resId, size = 26.dp)
                                    }
                                    Text(pack.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                        modifier = Modifier.height(300.dp)
                    ) {
                        androidx.compose.foundation.lazy.grid.items(currentPack?.stickers.orEmpty(), key = { it.ref }) { sticker ->
                            Column(
                                Modifier.clickable {
                                    addStickerOverlay(sticker.ref)
                                    stickerPicker = false
                                }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedSticker(url = sticker.ref, assetPath = sticker.assetPath, resId = sticker.resId, size = 54.dp)
                                Text(sticker.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { stickerPicker = false }) { Text("Done") } }
        )
    }
}

/** Positions this composable so its own visual center sits at pixel ([cx], [cy]) in the parent. */
private fun Modifier.centeredAt(cx: Int, cy: Int): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(cx - placeable.width / 2, cy - placeable.height / 2)
    }
}

private enum class EditorTool { NONE, CROP, DRAW }

/** A text/sticker overlay while it is still being edited (mutable so drags update it live). */
private class EditableOverlay(
    val id: String,
    val kind: String,
    text: String = "",
    val colorArgb: Int = android.graphics.Color.WHITE,
    val stickerRef: String? = null,
    x: Float = 0.5f,
    y: Float = 0.5f,
    scale: Float = 1f,
    rotationDeg: Float = 0f
) {
    var text by mutableStateOf(text)
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var scale by mutableFloatStateOf(scale)
    var rotationDeg by mutableFloatStateOf(rotationDeg)
}

/** Converts the user's pan/zoom of the source image into a normalized crop rectangle. */
private fun computeCropRect(
    sourceSize: IntSize,
    previewBoxSize: IntSize,
    imageScale: Float,
    imageOffset: Offset,
    cropAspectRatio: Float
): CropRect {
    if (sourceSize.width <= 0 || sourceSize.height <= 0 || previewBoxSize.width <= 0 || previewBoxSize.height <= 0) {
        return CropRect()
    }
    // The crop guide is the largest box of [cropAspectRatio] that fits the preview area.
    val boxW = previewBoxSize.width.toFloat()
    val boxH = previewBoxSize.height.toFloat()
    var guideW = boxW
    var guideH = guideW / cropAspectRatio
    if (guideH > boxH) {
        guideH = boxH
        guideW = guideH * cropAspectRatio
    }

    // The image is drawn with ContentScale.Fit into the preview box, then panned/scaled by
    // the user. Work out the on-screen rect of the full (fitted*scale) image.
    val fitScale = min(boxW / sourceSize.width, boxH / sourceSize.height)
    val baseW = sourceSize.width * fitScale
    val baseH = sourceSize.height * fitScale
    val drawnW = baseW * imageScale
    val drawnH = baseH * imageScale
    val imageLeft = (boxW - drawnW) / 2f + imageOffset.x
    val imageTop = (boxH - drawnH) / 2f + imageOffset.y

    val guideLeft = (boxW - guideW) / 2f
    val guideTop = (boxH - guideH) / 2f

    // Where the guide frame falls inside the drawn image, as a 0..1 fraction of that image.
    val left = ((guideLeft - imageLeft) / drawnW).coerceIn(0f, 1f)
    val top = ((guideTop - imageTop) / drawnH).coerceIn(0f, 1f)
    val right = (((guideLeft + guideW) - imageLeft) / drawnW).coerceIn(0f, 1f)
    val bottom = (((guideTop + guideH) - imageTop) / drawnH).coerceIn(0f, 1f)

    if (right <= left || bottom <= top) return CropRect()
    return CropRect(left, top, right, bottom)
}

/** Keeps the panned image from leaving the crop guide empty on any side. */
private fun clampPanOffset(offset: Offset, scale: Float, boxSize: IntSize): Offset {
    if (boxSize.width <= 0 || boxSize.height <= 0) return offset
    // Generous bound: at scale 1f no panning is allowed beyond a small margin; the higher the
    // zoom the more room there is to pan, which matches a frame that must always stay full.
    val maxX = boxSize.width * (scale - 1f) / 2f + boxSize.width * 0.5f
    val maxY = boxSize.height * (scale - 1f) / 2f + boxSize.height * 0.5f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
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
private fun OutlinedToolLabeled(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, modifier = Modifier.size(18.dp), tint = if (active) scheme.onPrimaryContainer else scheme.onSurface)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) scheme.onPrimaryContainer else scheme.onSurface)
    }
}

@Composable
private fun StatusEditorTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 5.dp)) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(if (active) Color(0xFF54D6C8) else Color(0xFF1B2228)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (active) Color.Black else Color.White)
        }
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * The photo preview, pannable/pinch-zoomable only while a crop (or other pan-needing) tool
 * is active. Reports the decoded source size so the crop math can be done in real pixels.
 */
@Composable
private fun EditorImagePreview(
    file: File,
    rotation: Int,
    scale: Float,
    offset: Offset,
    panZoomEnabled: Boolean,
    onSourceSize: (IntSize) -> Unit,
    onTransform: (scaleDelta: Float, panDelta: Offset) -> Unit
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { MediaEditor.preview(file, 1280) }
    }
    val image = bitmap
    if (image != null) {
        LaunchedEffect(image, rotation) {
            onSourceSize(IntSize(image.width, image.height))
        }
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(panZoomEnabled) {
                    if (panZoomEnabled) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            onTransform(zoom, pan)
                        }
                    }
                }
                .graphicsLayer(
                    rotationZ = rotation.toFloat(),
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    } else {
        Text("Preview unavailable", style = MaterialTheme.typography.bodySmall)
    }
}

/** The fixed crop guide: a frame of [aspectRatio] centered in the preview, dimming outside it. */
@Composable
private fun CropGuideOverlay(aspectRatio: Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        var guideW = boxW
        var guideH = guideW / aspectRatio
        if (guideH > boxH) {
            guideH = boxH
            guideW = guideH * aspectRatio
        }
        val density = LocalDensity.current
        val guideWDp = with(density) { guideW.toDp() }
        val guideHDp = with(density) { guideH.toDp() }
        Canvas(Modifier.fillMaxSize()) {
            val left = (size.width - guideW) / 2f
            val top = (size.height - guideH) / 2f
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width, top))
            drawRect(dim, topLeft = Offset(0f, top + guideH), size = androidx.compose.ui.geometry.Size(size.width, size.height - top - guideH))
            drawRect(dim, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, guideH))
            drawRect(dim, topLeft = Offset(left + guideW, top), size = androidx.compose.ui.geometry.Size(size.width - left - guideW, guideH))
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(guideWDp, guideHDp)
                .border(2.dp, Color.White, RoundedCornerShape(2.dp))
        )
    }
}

/** One placed text or sticker overlay: drag to move, two-finger pinch to scale/rotate. */
@Composable
private fun DraggableOverlayItem(
    overlay: EditableOverlay,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onEditText: () -> Unit,
    onDelete: () -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    onScaleRotate: (scaleDelta: Float, rotDelta: Float) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth
        val boxH = constraints.maxHeight
        val centerX = (overlay.x * boxW).toInt()
        val centerY = (overlay.y * boxH).toInt()

        // The draggable content, centered on (centerX, centerY): a custom layout offsets by
        // minus half its own measured size, so the box's visual center — not its corner —
        // lands exactly where the overlay is placed.
        Box(
            Modifier
                .centeredAt(centerX, centerY)
                .then(
                    if (enabled) Modifier.pointerInput(overlay.id) {
                        detectTapGestures(onTap = { onSelect() })
                    } else Modifier
                )
                .then(
                    if (enabled) Modifier.pointerInput(overlay.id) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onMove(dragAmount.x, dragAmount.y)
                            }
                        )
                    } else Modifier
                )
                .then(
                    if (enabled) Modifier.pointerInput(overlay.id) {
                        detectTransformGestures { _, _, zoom, rotationChange ->
                            onScaleRotate(zoom, rotationChange)
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .graphicsLayer(
                        scaleX = overlay.scale,
                        scaleY = overlay.scale,
                        rotationZ = overlay.rotationDeg
                    )
                    .then(
                        if (selected) Modifier.border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp)) else Modifier
                    )
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                when (overlay.kind) {
                    "sticker" -> AnimatedSticker(
                        url = overlay.stickerRef,
                        assetPath = stickerAssetPath(overlay.stickerRef),
                        size = 96.dp
                    )
                    else -> Text(
                        overlay.text,
                        color = Color(overlay.colorArgb),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .then(if (enabled) Modifier.clickable { onEditText() } else Modifier)
                    )
                }
            }
            if (selected && enabled) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(28, -28) }
                        .size(26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Delete, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Small dialog for typing/editing a status text overlay before it is placed on the canvas. */
@Composable
private fun TextOverlayEditorDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialText) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onConfirm(value) },
        title = { Text("Add text") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(120) },
                label = { Text("Text on photo") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
