package org.holidaylink.app.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * Writes media into the phone's gallery.
 *
 * Android 10+ needs no permission for a MediaStore insert; older releases need
 * WRITE_EXTERNAL_STORAGE, which the caller has to request before calling in.
 */
object GallerySave {

    private const val IMAGE_DIR = "Pictures/HolidayLink"
    private const val VIDEO_DIR = "Movies/HolidayLink"

    fun bitmap(context: Context, image: Bitmap, name: String = "holidaylink"): Boolean {
        val stamp = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name-$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, IMAGE_DIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            val wrote = resolver.openOutputStream(uri)?.use { out ->
                image.compress(Bitmap.CompressFormat.JPEG, 95, out)
            } ?: false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            wrote
        }.getOrDefault(false)
    }

    fun video(context: Context, file: File, name: String = "holidaylink"): Boolean {
        if (!file.isFile) return false
        val stamp = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name-$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_DIR)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            val wrote = resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
                true
            } ?: false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            wrote
        }.getOrDefault(false)
    }

    /** True when saving needs the legacy storage permission on this device. */
    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
}
