package org.holidaylink.app.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.holidaylink.app.BuildConfig
import org.holidaylink.app.config.AppConfig
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Update check: tells the user when a newer build exists and where to download it.
 *
 * Three feeds are tried, in order:
 *   1. The HolidayLink server's `/app/latest` feed, which proxies the private GitHub release
 *      built by GitHub Actions. This is the normal path for the Android app.
 *   2. The GitHub release for this repo (`releases/latest`) when the repo is publicly readable.
 *   3. A plain `{versionName, code, url, notes}` document on holidaylink.org as a fallback.
 *
 * Whichever feed reports a code greater than [BuildConfig.VERSION_CODE] wins. The new build is
 * opened in the browser (see [openDownload]); the app never installs APKs by itself.
 */
object AppUpdater {

    data class Update(
        val versionName: String,
        val code: Int,
        val apkUrl: String,
        val notes: String,
        val publishedAt: String
    ) {
        /** True when this release has been out for at least 24 hours and must be installed. */
        fun isMandatory(nowMs: Long = System.currentTimeMillis()): Boolean {
            val publishedMs = parsePublishedAt(publishedAt) ?: return false
            return nowMs - publishedMs >= 24L * 60L * 60L * 1000L
        }
    }

    private fun parsePublishedAt(raw: String): Long? {
        if (raw.isBlank()) return null
        return runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", java.util.Locale.US)
                    .parse(raw)?.time
            }.getOrNull()
            ?: runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", java.util.Locale.US)
                    .parse(raw)?.time
            }.getOrNull()
    }

    private const val RELEASES_URL =
        "https://api.github.com/repos/claudeuse256-cmd/holidaylinkapp/releases/latest"
    private const val FALLBACK_URL = "https://holidaylink.org/app/latest.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()


    /**
     * A newer version than this build, or null when the server says we are current.
     * Throws when every feed is unreachable so the update screen can show a real error
     * instead of claiming the app is up to date.
     */
    suspend fun check(context: Context): Update? = withContext(Dispatchers.IO) {
        val serverFeed = AppConfig.resolve(context).trimEnd('/') + "/app/latest"
        var lastError: Throwable? = null
        // GitHub Releases is the public source of truth for APKs produced by Actions.
        // Check it first so a sleeping Render instance cannot make update checks appear broken.
        for (feed in listOf(RELEASES_URL, serverFeed, FALLBACK_URL)) {
            try {
                return@withContext parse(fetch(feed))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("No update feed available")
    }

    /**
     * Opens the release download in the phone's browser / download manager.
     *
     * The app deliberately does NOT download an APK itself and hand it to the package
     * installer any more. An app that fetches an APK at runtime and asks Android to install it
     * (REQUEST_INSTALL_PACKAGES) is exactly the "dropper" pattern Google Play Protect blocks as
     * a harmful app. The browser download is visible to the user, needs no special permission,
     * and the system installer is then opened by the user from the Downloads notification.
     */
    fun openDownload(context: Context, update: Update) {
        runCatching {
            val apkUrl = if (update.apkUrl.startsWith("http://") || update.apkUrl.startsWith("https://")) {
                update.apkUrl
            } else {
                AppConfig.resolve(context).trimEnd('/') + update.apkUrl
            }
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json, application/json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "HolidayLink-Android")
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
