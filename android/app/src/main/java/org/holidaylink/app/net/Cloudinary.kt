package org.holidaylink.app.net

import android.content.Context
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.holidaylink.app.config.AppConfig
import org.json.JSONObject

/**
 * Uploads videos and documents to Cloudinary through the HolidayLink server.
 *
 * The app never holds the Cloudinary secret: it hands the bytes and the Firebase ID
 * token to `POST /media/upload`, and the server signs the Cloudinary request. That keeps
 * the key off every phone and lets the storage backend change without another APK.
 */
object Cloudinary {

    // OkHttp's default is 10 seconds for connect/write/read. A video goes to the server and
    // from there to Cloudinary, and the server on a free host can need a minute to wake up, so
    // the defaults timed every real video out before it finished and the upload "never worked".
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(240, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /**
     * Uploads [bytes] and returns the public HTTPS URL.
     * Throws [ApiException] with a human-readable message when the server refuses.
     *
     * [onProgress] receives 0..100 so the chat can draw a progress bar on the attachment.
     */
    suspend fun upload(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        onProgress: (Int) -> Unit = {}
    ): String {
        val mediaType = runCatching { mimeType.toMediaType() }
            .getOrElse { "application/octet-stream".toMediaType() }
        return send(context, bytes.toRequestBody(mediaType).withProgress(onProgress), fileName, mimeType)
    }

    /**
     * Same as [upload] but reads the file from disk while sending, so even a 200 MB video uses
     * almost no memory on the phone.
     */
    suspend fun uploadFile(
        context: Context,
        file: java.io.File,
        fileName: String,
        mimeType: String,
        onProgress: (Int) -> Unit = {}
    ): String {
        val mediaType = runCatching { mimeType.toMediaType() }
            .getOrElse { "application/octet-stream".toMediaType() }
        return send(
            context,
            file.asRequestBody(mediaType).withProgress(onProgress),
            fileName,
            mimeType
        )
    }

    private suspend fun send(
        context: Context,
        body: okhttp3.RequestBody,
        fileName: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        val token = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            ?.getIdToken(false)?.await()?.token
            ?: throw ApiException("Sign in again to upload files.")

        val base = AppConfig.resolve(context)
        val safeName = fileName
            .replace("\"", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
            .take(120)
            .ifBlank { "upload" }

        val request = Request.Builder()
            .url("$base/media/upload")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("X-Filename", safeName)
            .addHeader("X-Content-Type", mimeType)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            val message = json?.optString("message").orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(
                    message.ifBlank {
                        when (response.code) {
                            503 -> "Cloud file uploads are not configured on the server yet."
                            413 -> "That file is too large to upload."
                            else -> "Upload failed (${response.code}). Please try again."
                        }
                    }
                )
            }
            json?.optString("secureUrl")?.takeIf { it.isNotBlank() }
                ?: json?.optString("url")?.takeIf { it.isNotBlank() }
                ?: throw ApiException("The upload server returned no file URL.")
        }
    }

    /** Best-effort MIME guess for a picked file name. */
    fun mimeOf(name: String?, fallback: String = "application/octet-stream"): String {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext.isBlank()) return fallback
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: fallback
    }
}
