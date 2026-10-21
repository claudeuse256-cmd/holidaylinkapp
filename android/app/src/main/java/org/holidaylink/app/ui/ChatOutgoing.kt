package org.holidaylink.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.holidaylink.app.media.MediaEditor
import java.io.File

/** Where a message that is on its way out currently is. */
enum class OutgoingStage { UPLOADING, SENDING, SENT, FAILED }

/**
 * A message that is still on its way to the server.
 *
 * The bubble appears the instant the user taps send — WhatsApp does the same — and keeps
 * telling the truth about what is happening: the upload percentage, the moment the
 * message is written to Firestore, and a retry button when something went wrong.
 */
data class OutgoingItem(
    val localId: String,
    val kind: String,
    val text: String = "",
    val fileName: String? = null,
    val localPath: String? = null,
    val mimeType: String = "",
    val stage: OutgoingStage = OutgoingStage.SENDING,
    val progress: Int = 0,
    val error: String? = null,
    val serverId: String? = null,
    val rotation: Int = 0,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    val viewOnce: Boolean = false,
    val ttlSeconds: Long = 0,
    val replyToId: String? = null,
    val replyToBody: String? = null
)

/** A picked file waiting in the editor, copied into the app cache. */
data class EditorSource(
    val kind: String,
    val file: File,
    val fileName: String,
    val mimeType: String
)

/** The bubble for a message that has not reached the server yet. */
@Composable
fun OutgoingBubble(
    item: OutgoingItem,
    onRetry: () -> Unit,
    onDiscard: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = 16.dp, bottomEnd = 4.dp
                    )
                )
                .background(scheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (item.kind != "text") {
                OutgoingMediaPreview(item)
                Spacer(Modifier.height(6.dp))
            }
            if (item.text.isNotBlank()) {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
            }
            when (item.stage) {
                OutgoingStage.UPLOADING -> {
                    LinearProgressIndicator(
                        progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(160.dp).height(4.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Uploading ${item.progress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
                OutgoingStage.SENDING -> OutgoingStatus(
                    Icons.Default.Schedule, "Sending…", scheme.onPrimaryContainer
                )
                OutgoingStage.SENT -> OutgoingStatus(
                    Icons.Default.CheckCircle, "Sent", scheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                OutgoingStage.FAILED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ErrorOutline, null,
                            tint = scheme.error,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            item.error ?: "Not sent",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = onRetry, modifier = Modifier.size(26.dp)) {
                            Icon(
                                Icons.Default.Refresh, "Retry",
                                tint = scheme.error,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(onClick = onDiscard, modifier = Modifier.size(26.dp)) {
                            Icon(
                                Icons.Default.Description, "Remove",
                                tint = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutgoingStatus(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Thumbnail of what is being sent: the photo itself, a video still, or the file name. */
@Composable
private fun OutgoingMediaPreview(item: OutgoingItem) {
    val scheme = MaterialTheme.colorScheme
    val path = item.localPath
    if (item.kind == "file" || path.isNullOrBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                item.fileName ?: "File",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
        return
    }
    val file = remember(path) { File(path) }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            if (item.kind == "video") {
                MediaEditor.videoThumbnail(file, item.trimStartMs)
            } else {
                MediaEditor.preview(file, 480)
            }
        }
    }
    val image = bitmap
    Box(
        Modifier
            .size(width = 168.dp, height = 168.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(168.dp)
            )
        } else {
            Icon(
                if (item.kind == "video") Icons.Default.Movie else Icons.Default.Description,
                null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp)
            )
        }
        if (item.kind == "video") {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(scheme.surface.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Movie, null,
                    tint = scheme.onSurface,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
