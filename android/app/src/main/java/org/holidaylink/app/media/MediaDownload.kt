package org.holidaylink.app.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment

/**
 * Saves a remote file (a past paper, guide, document) with the system DownloadManager so it
 * lands in Downloads and shows a progress notification. On Android 10+ the public Downloads
 * folder needs no permission; older releases use the app's own external folder.
 */
object MediaDownload {

    fun save(context: Context, url: String, title: String): Boolean = runCatching {
        val name = (title.ifBlank { "holidaylink" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(60)) + extension(url)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title.ifBlank { "HolidayLink" })
            .setDescription("Downloading…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        true
    }.getOrDefault(false)

    private fun extension(url: String): String {
        val ext = url.substringAfterLast('.', "").takeWhile { it.isLetterOrDigit() }.take(5)
        return if (ext.isBlank()) ".pdf" else ".$ext"
    }
}
