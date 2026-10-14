package org.holidaylink.app.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.holidaylink.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * In-app self-update so a new build never needs a manual reinstall.
 *
 * Two feeds are tried, in order:
 *   1. The GitHub release for this repo (`releases/latest`) — the release tag should be the
 *      versionCode, e.g. `5`, or a `target_code` field can be added to the release body JSON.
 *   2. A plain `{versionName, code, url, notes}` document on holidaylink.org as a fallback.
 *
 * Whichever feed reports a code greater than [BuildConfig.VERSION_CODE] wins. The APK is
 * downloaded into the app cache and handed to the system installer through a FileProvider.
 */
object AppUpdater {

    data class Update(
        val versionName: String,
        val code: Int,
        val apkUrl: String,
        val notes: String,
        val publishedAt: String
    )

    private const val RELEASES_URL =
        "https://api.github.com/repos/claudeuse256-cmd/holidaylinkapp/releases/latest"
    private const val FALLBACK_URL = "https://holidaylink.org/app/latest.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** A newer version than this build, or null when we are current / offline / feed is quiet. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        runCatching { parse(fetch(RELEASES_URL)) }.getOrNull()
            ?: runCatching { parse(fetch(FALLBACK_URL)) }.getOrNull()
    }

    /** Downloads the APK (or returns the cached copy) and reports 0-100 progress. */
    suspend fun download(context: Context, update: Update, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "holidaylink-${update.code}.apk")
                if (target.exists() && target.length() > 0L) {
                    onProgress(100)
                    return@runCatching target
                }
                val req = Request.Builder().url(update.apkUrl)
                    .header("User-Agent", "HolidayLink")
                    .build()
                client.newCall(req).execute().use { res ->
                    val body = res.body ?: throw IllegalStateException("Empty download")
                    if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}")
                    val total = body.contentLength()
                    var read = 0L
                    body.byteStream().use { ins ->
                        target.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) onProgress(((read * 100) / total).toInt().coerceIn(1, 99))
                            }
                        }
                    }
                }
                onProgress(100)
                target
            }.getOrNull()
        }

    /** Android 8+ requires the "install unknown apps" grant for this source. */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    fun requestInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun install(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json, application/json")
            .header("User-Agent", "HolidayLink")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code}")
            return res.body?.string().orEmpty()
        }
    }

    private fun parse(raw: String): Update? {
        if (raw.isBlank()) return null
        val o = JSONObject(raw)
        val current = BuildConfig.VERSION_CODE

        val code: Int
        val name: String
        val apk: String
        val notes: String
        val published: String

        if (o.has("assets")) {
            name = o.optString("name").ifBlank { o.optString("tag_name") }
            notes = o.optString("body")
            published = o.optString("published_at")
            val explicit = o.optInt("target_code", -1)
            if (explicit > 0) {
                code = explicit
            } else {
                // Release tags are the versionCode, e.g. "v5" or "5".
                val digits = Regex("\\d+").find(o.optString("tag_name"))?.value?.toIntOrNull() ?: return null
                code = digits
            }
            val assets = o.optJSONArray("assets") ?: return null
            var found: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    found = a.optString("browser_download_url")
                    break
                }
            }
            apk = found.orEmpty()
        } else {
            name = o.optString("versionName").ifBlank { o.optString("version") }
            code = o.optInt("code", -1)
            apk = o.optString("url").ifBlank { o.optString("apk") }
            notes = o.optString("notes")
            published = o.optString("publishedAt")
        }

        if (code <= current || apk.isBlank()) return null
        return Update(name.ifBlank { "v$code" }, code, apk, notes, published)
    }
}
