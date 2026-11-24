package org.holidaylink.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.holidaylink.app.media.MediaEditor
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.Cloudinary
import org.holidaylink.app.net.FirestoreChat
import org.holidaylink.app.net.Imgbb
import org.holidaylink.app.net.MediaCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Messages that are still on their way out, kept for the whole life of the app.
 *
 * The outbox used to live inside the chat screen, so opening another chat (or closing the app)
 * threw away every picture or video that had not finished uploading — and cancelled its
 * upload. Here the list and the upload both belong to the app, not to a screen:
 *  - leaving the conversation does not stop anything and the bubble is still there on return;
 *  - pending media is written to disk, so it comes back after the app is closed;
 *  - an item that failed only because there was no internet is sent again automatically when
 *    the connection returns, the way WhatsApp does it.
 *
 * Text, stickers and voice notes do not need this: Firestore queues those writes itself.
 */
object OutboxStore {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> }
    )

    /** chatId -> items, observable from Compose. */
    val boxes = mutableStateMapOf<String, List<OutgoingItem>>()

    private var app: Context? = null
    private val inFlight = mutableSetOf<String>()
    private const val PREFS = "hl_outbox"

    fun items(chatId: String): List<OutgoingItem> = boxes[chatId].orEmpty()

    /** Runs [block] on an app-wide scope that outlives any screen. */
    fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        runCatching { restore() }
        runCatching { watchNetwork() }
    }

    // ------------------------------------------------------------------ list edits

    fun add(chatId: String, item: OutgoingItem) {
        boxes[chatId] = items(chatId) + item
        persist()
    }

    fun update(chatId: String, localId: String, block: (OutgoingItem) -> OutgoingItem) {
        scope.launch {
            boxes[chatId] = items(chatId).map { if (it.localId == localId) block(it) else it }
            persist()
        }
    }

    fun remove(chatId: String, localId: String) = removeAll(chatId, listOf(localId))

    fun removeAll(chatId: String, localIds: Collection<String>) {
        val kept = items(chatId).filter { it.localId !in localIds }
        if (kept.isEmpty()) boxes.remove(chatId) else boxes[chatId] = kept
        persist()
    }

    // ------------------------------------------------------------------ delivery

    /** Tries a failed picture/video/file again. */
    fun retry(chatId: String, item: OutgoingItem) {
        val revived = item.copy(stage = OutgoingStage.UPLOADING, error = null, progress = 0, autoRetry = false)
        update(chatId, item.localId) { revived }
        deliver(chatId, revived)
    }

    /** Uploads the attachment and stores the message, reporting progress while it runs. */
    fun deliver(chatId: String, item: OutgoingItem) {
        val ctx = app ?: return
        if (!inFlight.add(item.localId)) return
        scope.launch {
            try {
                val path = item.localPath ?: throw ApiException("The file is no longer available.")
                val original = File(path)
                if (!original.isFile) throw ApiException("The file is no longer available.")
                val prepared = withContext(Dispatchers.IO) { prepareFile(ctx, original, item) }
                val bytes = withContext(Dispatchers.IO) { prepared.readBytes() }
                val url: String
                if (item.kind == "image") {
                    val jpeg = withContext(Dispatchers.IO) {
                        val scaled = MediaEditor.jpegBytes(prepared) ?: bytes
                        MediaEditor.rotateJpeg(scaled, item.rotation) ?: scaled
                    }
                    url = Imgbb.uploadJpeg(jpeg) { p -> update(chatId, item.localId) { it.copy(progress = p) } }
                    withContext(Dispatchers.IO) { MediaCache.seed(ctx, url, jpeg, "jpg") }
                } else {
                    if (bytes.size > MediaPicker.maxAttachmentBytes) {
                        throw ApiException("That file is too large (max 20 MB).")
                    }
                    val name = item.fileName ?: "File"
                    val mime = item.mimeType.ifBlank { Cloudinary.mimeOf(name) }
                    url = runCatching {
                        Cloudinary.upload(ctx, bytes, name, mime) { p ->
                            update(chatId, item.localId) { it.copy(progress = p) }
                        }
                    }.getOrElse { first ->
                        // The server route is not set up (or is asleep): fall back to Firebase
                        // Storage. If that fails too, report the first error as well.
                        runCatching {
                            val ext = name.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: "bin"
                            val ref = com.google.firebase.storage.FirebaseStorage.getInstance()
                                .getReference("chat_media/$chatId/${UUID.randomUUID()}.$ext")
                            ref.putBytes(bytes).await()
                            ref.downloadUrl.await().toString()
                        }.getOrElse { throw first }
                    }
                    withContext(Dispatchers.IO) {
                        MediaCache.seed(ctx, url, prepared, if (item.kind == "video") "mp4" else "bin")
                    }
                }
                update(chatId, item.localId) { it.copy(stage = OutgoingStage.SENDING, progress = 100) }
                val serverId = FirestoreChat.send(
                    chatId,
                    item.text,
                    imageUrl = if (item.kind == "image") url else null,
                    fileUrl = if (item.kind == "image") null else url,
                    fileName = if (item.kind == "image") null else item.fileName,
                    kind = if (item.kind == "image") "image" else if (item.kind == "video") "video" else "file",
                    viewOnce = item.viewOnce,
                    ttlSeconds = item.ttlSeconds
                )
                update(chatId, item.localId) {
                    it.copy(stage = OutgoingStage.SENT, serverId = serverId, error = null, autoRetry = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val offline = !isOnline(ctx) || isNetworkError(e)
                update(chatId, item.localId) {
                    it.copy(
                        stage = OutgoingStage.FAILED,
                        error = if (offline) "Waiting for connection…"
                        else (e as? ApiException)?.message ?: "Not sent — tap to retry",
                        autoRetry = offline
                    )
                }
            } finally {
                inFlight.remove(item.localId)
            }
        }
    }

    /** Applies the edits the user made (video trim) and returns the file that is uploaded. */
    private fun prepareFile(ctx: Context, source: File, item: OutgoingItem): File {
        if (item.kind != "video") return source
        val duration = MediaEditor.videoDurationMs(source)
        if (duration <= 0L) return source
        val untouched = item.trimStartMs <= 0L && item.trimEndMs >= duration
        if (untouched) return source
        val dir = File(ctx.cacheDir, "edited").apply { mkdirs() }
        val out = File(dir, "trim-${System.currentTimeMillis()}.mp4")
        return MediaEditor.trimVideo(source, item.trimStartMs, item.trimEndMs, out) ?: source
    }

    // ------------------------------------------------------------------ connectivity

    private fun isOnline(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    private fun isNetworkError(e: Throwable): Boolean {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 6) {
            if (t is java.io.IOException || t is com.google.firebase.FirebaseNetworkException) return true
            t = t.cause
            depth++
        }
        return false
    }

    private fun watchNetwork() {
        val ctx = app ?: return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { retryWaiting() }
            }
        })
        // Also try once at start-up: items restored from disk may be able to go straight away.
        scope.launch { retryWaiting() }
    }

    private fun retryWaiting() {
        boxes.entries.toList().forEach { entry ->
            entry.value
                .filter { it.stage == OutgoingStage.FAILED && it.autoRetry && it.kind != "text" }
                .forEach { retry(entry.key, it) }
        }
    }

    // ------------------------------------------------------------------ persistence

    private fun persist() {
        val prefs = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        val array = JSONArray()
        boxes.entries.toList().forEach { entry ->
            entry.value
                .filter { it.kind != "text" && it.stage != OutgoingStage.SENT && !it.localPath.isNullOrBlank() }
                .forEach { i ->
                    array.put(
                        JSONObject()
                            .put("chatId", entry.key)
                            .put("localId", i.localId)
                            .put("kind", i.kind)
                            .put("text", i.text)
                            .put("fileName", i.fileName.orEmpty())
                            .put("localPath", i.localPath.orEmpty())
                            .put("mimeType", i.mimeType)
                            .put("rotation", i.rotation)
                            .put("trimStartMs", i.trimStartMs)
                            .put("trimEndMs", i.trimEndMs)
                            .put("viewOnce", i.viewOnce)
                            .put("ttlSeconds", i.ttlSeconds)
                    )
                }
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun restore() {
        val raw = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString("items", null) ?: return
        val array = JSONArray(raw)
        for (n in 0 until array.length()) {
            val o = array.optJSONObject(n) ?: continue
            val path = o.optString("localPath")
            val chatId = o.optString("chatId")
            if (path.isBlank() || chatId.isBlank() || !File(path).isFile) continue
            val item = OutgoingItem(
                localId = o.optString("localId").ifBlank { UUID.randomUUID().toString() },
                kind = o.optString("kind", "file"),
                text = o.optString("text"),
                fileName = o.optString("fileName").ifBlank { null },
                localPath = path,
                mimeType = o.optString("mimeType"),
                stage = OutgoingStage.FAILED,
                error = "Waiting for connection…",
                rotation = o.optInt("rotation"),
                trimStartMs = o.optLong("trimStartMs"),
                trimEndMs = o.optLong("trimEndMs"),
                viewOnce = o.optBoolean("viewOnce"),
                ttlSeconds = o.optLong("ttlSeconds"),
                autoRetry = true
            )
            boxes[chatId] = boxes[chatId].orEmpty() + item
        }
    }
}
