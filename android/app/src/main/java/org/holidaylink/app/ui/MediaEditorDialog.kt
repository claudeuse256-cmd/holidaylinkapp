package org.holidaylink.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
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
    onSend: (caption: String, rotation: Int, trimStartMs: Long, trimEndMs: Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var caption by remember { mutableStateOf("") }
    var rotation by remember { mutableIntStateOf(0) }
    var trimStart by remember { mutableLongStateOf(0L) }
    var trimEnd by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var preparing by remember { mutableStateOf(false) }

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
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
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
                    color = scheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(enabled = !preparing) {
                            preparing = true
                            onSend(caption.trim(), rotation, trimStart, trimEnd)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Preview
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(scheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isImage -> EditorImagePreview(file, rotation)
                    isVideo -> EditorVideoPreview(file, trimStart)
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
                    Slider(
                        value = trimStart.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                        onValueChange = {
                            val next = it.toLong().coerceIn(0L, (trimEnd - 500L).coerceAtLeast(0L))
                            trimStart = next
                        },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Slider(
                        value = trimEnd.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                        onValueChange = {
                            val next = it.toLong().coerceIn((trimStart + 500L).coerceAtMost(duration), duration)
                            trimEnd = next
                        },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(),
                        modifier = Modifier.fillMaxWidth()
                    )
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
private fun EditorImagePreview(file: File, rotation: Int) {
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
                .graphicsLayer(rotationZ = rotation.toFloat())
        )
    } else {
        Text("Preview unavailable", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EditorVideoPreview(file: File, atMs: Long) {
    val scheme = MaterialTheme.colorScheme
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, file, atMs) {
        value = withContext(Dispatchers.IO) { MediaEditor.videoThumbnail(file, atMs) }
    }
    val image = bitmap
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Movie, "Video", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Text(
            file.name,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
        )
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
