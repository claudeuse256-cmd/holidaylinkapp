package org.holidaylink.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.holidaylink.app.media.MediaEditor
import org.holidaylink.app.net.ApiException
import java.io.File
import java.util.UUID

/**
 * Status posts that are being uploaded right now.
 *
 * Posting a photo or video used to close the editor and show nothing until the upload had
 * finished, so the status seemed to vanish and then pop up later (or never, when the upload
 * failed without a word). Now the post appears at once as a card with a thumbnail and a real
 * progress bar, keeps running if the user leaves the screen, and shows the reason plus a retry
 * button when something goes wrong.
 */
object StatusUploads {

    data class Upload(
        val id: String,
        val kind: String,
        val previewPath: String?,
        val progress: Int = 0,
        val error: String? = null
    )

    /** Uploads in progress or failed; observable from Compose. */
    val active = mutableStateListOf<Upload>()

    /** Goes up every time a post finishes, so the status list knows to refresh. */
    var completed by mutableIntStateOf(0)
        private set

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> }
    )
    private val jobs = HashMap<String, suspend ((Int) -> Unit) -> Unit>()
    private val cleanups = HashMap<String, File?>()

    /** Shows the card straight away and runs [work] with a progress callback. */
    fun start(kind: String, preview: File?, cleanup: File?, work: suspend ((Int) -> Unit) -> Unit) {
        val id = UUID.randomUUID().toString()
        active.add(Upload(id = id, kind = kind, previewPath = preview?.absolutePath))
        jobs[id] = work
        cleanups[id] = cleanup
        run(id)
    }

    fun retry(id: String) {
        if (jobs[id] == null) return
        run(id)
    }

    fun dismiss(id: String) {
        active.removeAll { it.id == id }
        jobs.remove(id)
        cleanups.remove(id)?.let { runCatching { it.delete() } }
    }

    private fun change(id: String, block: (Upload) -> Upload) {
        val index = active.indexOfFirst { it.id == id }
        if (index >= 0) active[index] = block(active[index])
    }

    private fun run(id: String) {
        val work = jobs[id] ?: return
        change(id) { it.copy(progress = 0, error = null) }
        scope.launch {
            try {
                work { percent -> scope.launch { change(id) { it.copy(progress = percent.coerceIn(0, 100)) } } }
                dismiss(id)
                completed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                change(id) {
                    it.copy(
                        error = (e as? ApiException)?.message
                            ?: "Couldn't post the status. Check your connection and try again."
                    )
                }
            }
        }
    }
}

/** The cards for statuses that are still uploading (or failed). Empty when there are none. */
@Composable
fun StatusUploadBanner() {
    StatusUploads.active.toList().forEach { upload ->
        key(upload.id) {
            val thumb by produceState<Bitmap?>(initialValue = null, upload.previewPath) {
                value = withContext(Dispatchers.IO) {
                    upload.previewPath?.let { path ->
                        runCatching {
                            val file = File(path)
                            if (upload.kind == "video") MediaEditor.videoThumbnail(file, 0L)
                            else MediaEditor.preview(file, 160)
                        }.getOrNull()
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    thumb?.let {
                        Image(
                            it.asImageBitmap(), null,
                            Modifier.size(46.dp), contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (upload.error == null) {
                        Text(
                            if (upload.kind == "video") "Posting your video status…" else "Posting your status…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.size(6.dp))
                        LinearProgressIndicator(
                            progress = { upload.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${upload.progress}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Status not posted",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            upload.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (upload.error != null) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        TextButton(onClick = { StatusUploads.retry(upload.id) }) { Text(tr("Retry")) }
                        TextButton(onClick = { StatusUploads.dismiss(upload.id) }) { Text(tr("Remove")) }
                    }
                }
            }
        }
    }
}
