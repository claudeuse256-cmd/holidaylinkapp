package org.holidaylink.app.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Download-once media cache for chat attachments.
 *
 * Every photo, video or document is stored as `cacheDir/media/<sha256(url)>.<ext>` the
 * first time it is seen and read from disk from then on, so scrolling back through a
 * conversation never downloads the same file twice. A small in-memory bitmap cache keeps
 * the first frame instant while the disk copy is decoded.
 *
 * The file is keyed by URL, which is stable for a given upload; a replaced upload gets a
 * new URL and therefore downloads once, exactly as "only when it updates" requires.
 */
object MediaCache {

    private val client by lazy { OkHttpClient.Builder().build() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Byte-sized so a few large photos cannot exhaust the heap the way a count-based
    // cache of full-resolution bitmaps can.
    private val memory = object : android.util.LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun key(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Avatars, status images and wallpapers live in `filesDir`, not `cacheDir`: the system
     * may wipe the cache at any time, which is exactly why photos turned back into initials
     * while offline. Chat attachments keep using the cache directory.
     */
    fun dir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    /** Local file for [url]; [fallbackExt] is used when the URL carries no extension. */
    fun file(context: Context, url: String, fallbackExt: String = "bin"): File {
        val ext = url.substringBefore('?').substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }
            ?: fallbackExt
        return File(dir(context), "${key(url)}.$ext")
    }

    fun cached(context: Context, url: String, fallbackExt: String = "bin"): File? =
        file(context, url, fallbackExt).takeIf { it.isFile && it.length() > 0L }

    /** In-memory hit, safe to call from composition for an instant first frame. */
    fun peek(url: String): Bitmap? = runCatching { memory.get(key(url)) }.getOrNull()

    /** Decodes an image from memory, then disk, then the network (once). */
    suspend fun bitmap(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(key(url))?.let { return@withContext it }
        val local = cached(context, url) ?: fetch(context, url)
        val decoded = local?.let { BitmapFactory.decodeFile(it.absolutePath) }
        decoded?.also { memory.put(key(url), it) }
    }

    /**
     * Returns the cached file, downloading it first when needed.
     * Null means the URL is unusable or the device is offline.
     */
    suspend fun fetch(context: Context, url: String, fallbackExt: String = "bin"): File? =
        withContext(Dispatchers.IO) {
            if (!url.startsWith("http")) return@withContext null
            cached(context, url, fallbackExt)?.let { return@withContext it }
            runCatching {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body ?: return@withContext null
                    if (!response.isSuccessful) return@withContext null
                    val target = file(context, url, fallbackExt)
                    val temp = File(target.parentFile, target.name + ".part")
                    body.byteStream().use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (temp.length() <= 0L) {
                        temp.delete()
                        return@withContext null
                    }
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                    trim(context)
                    target
                }
            }.getOrNull()
        }

    /** Keeps the persistent media folder bounded by dropping the oldest files first. */
    private fun trim(context: Context, maxBytes: Long = 256L * 1024 * 1024) {
        runCatching {
            val files = dir(context).listFiles()?.filter { it.isFile } ?: return@runCatching
            var total = files.sumOf { it.length() }
            if (total <= maxBytes) return@runCatching
            files.sortedBy { it.lastModified() }.forEach { f ->
                if (total <= maxBytes) return@forEach
                total -= f.length()
                f.delete()
            }
        }
    }

    /** Fire-and-forget download used when a conversation is opened. */
    fun prefetch(context: Context, urls: Collection<String>, fallbackExt: String = "bin") {
        if (urls.isEmpty()) return
        val app = context.applicationContext
        urls.asSequence()
            .filter { it.startsWith("http") }
            .distinct()
            .forEach { url ->
                if (cached(app, url, fallbackExt) == null) {
                    scope.launch { fetch(app, url, fallbackExt) }
                }
            }
    }
}
