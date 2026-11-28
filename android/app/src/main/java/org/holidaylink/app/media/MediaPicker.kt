package org.holidaylink.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/** Metadata about a picked file, read from the content provider. */
data class PickedFile(
    val uri: Uri,
    val name: String?,
    val sizeBytes: Long,
    val mimeType: String
)

/**
 * Turns picked media into small base64 payloads for the backend.
 *
 * Images are re-encoded as JPEG and capped at [imageMaxDimension] so uploads stay fast on
 * mobile data. The server writes the decoded bytes to disk under /uploads and stores only the
 * URL in Firestore, so image blobs never end up in the database.
 */
object MediaPicker {

    private const val TAG = "HolidayLinkMedia"
    const val imageMaxDimension = 1280
    const val jpegQuality = 78

    /** Hard ceiling for a single attachment, enforced on both sides. */
    const val maxAttachmentBytes = 200L * 1024 * 1024

    fun inspect(context: Context, uri: Uri): PickedFile {
        var name: String? = null
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && c.moveToFirst()) name = c.getString(nameIndex)
                if (sizeIndex >= 0 && !c.isNull(sizeIndex)) size = c.getLong(sizeIndex)
            }
        }.onFailure { Log.w(TAG, "could not inspect $uri: ${it.message}") }

        val mime = context.contentResolver.getType(uri) ?: guessMime(name)
        return PickedFile(uri, name, size, mime)
    }

    private fun guessMime(name: String?): String {
        val lower = name?.lowercase().orEmpty()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".3gp") -> "video/3gpp"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /** Down-scales an image and returns it as a JPEG data URL, or null when it cannot be read. */
    fun imageDataUrl(context: Context, uri: Uri): String? = runCatching {
        val bytes = imageJpegBytes(context, uri) ?: return@runCatching null
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.onFailure { Log.w(TAG, "image encode failed: ${it.message}") }.getOrNull()

    /** Down-scales an image and returns raw JPEG bytes, or null when it cannot be read. */
    fun imageJpegBytes(context: Context, uri: Uri): ByteArray? = runCatching {
        val decoded = decodeDownscaled(context, uri, imageMaxDimension)
            ?: return@runCatching null
        val out = ByteArrayOutputStream()
        decoded.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
        decoded.recycle()
        out.toByteArray()
    }.onFailure { Log.w(TAG, "image encode failed: ${it.message}") }.getOrNull()

    /** Reads a document / audio / video file verbatim as a data URL. */
    fun fileDataUrl(context: Context, uri: Uri, mime: String): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        if (bytes.size > maxAttachmentBytes) {
            Log.w(TAG, "attachment too large: ${bytes.size} bytes")
            return@runCatching null
        }
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.onFailure { Log.w(TAG, "file encode failed: ${it.message}") }.getOrNull()

    private fun decodeDownscaled(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        // Phone cameras and WhatsApp images store rotation in EXIF; without applying it the
        // re-encoded JPEG (which drops EXIF) comes out sideways/flipped. Rotate it into pixels.
        val upright = applyExifOrientation(resolver.openInputStream(uri), bitmap)

        val longest = maxOf(upright.width, upright.height)
        if (longest <= maxDimension) return upright

        val ratio = maxDimension.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            upright,
            (upright.width * ratio).toInt().coerceAtLeast(1),
            (upright.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled != upright) upright.recycle()
        return scaled
    }

    /** Bakes the EXIF orientation tag into the bitmap so re-encoded JPEGs display upright. */
    private fun applyExifOrientation(stream: java.io.InputStream?, bitmap: Bitmap): Bitmap {
        if (stream == null) return bitmap
        val orientation = runCatching {
            stream.use {
                android.media.ExifInterface(it).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)

        val matrix = android.graphics.Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }
}
