package org.holidaylink.app.config

import android.content.Context
import org.holidaylink.app.BuildConfig

/**
 * Single place that knows where the HolidayLink backend lives.
 *
 * Resolution order:
 *  1. runtime override stored in SharedPreferences (Menu -> Settings)
 *  2. BuildConfig.API_BASE_URL, which comes from -PHL_API_BASE_URL or android/gradle.properties
 *
 * No API call site hardcodes a host any more.
 */
object AppConfig {
    const val PREFS = "holidaylink"
    private const val KEY_API_BASE_URL = "apiBaseUrl"
    private const val FALLBACK_BASE_URL = "http://127.0.0.1:8080"

    /** Address baked in at build time, already normalised. */
    val buildTimeBaseUrl: String get() = normalize(BuildConfig.API_BASE_URL) ?: FALLBACK_BASE_URL

    fun resolve(context: Context): String {
        val stored = prefs(context).getString(KEY_API_BASE_URL, null)
        return normalize(stored ?: "") ?: buildTimeBaseUrl
    }

    fun setBaseUrl(context: Context, value: String) {
        prefs(context).edit().putString(KEY_API_BASE_URL, sanitize(value)).apply()
    }

    fun clearBaseUrl(context: Context) {
        prefs(context).edit().remove(KEY_API_BASE_URL).apply()
    }

    /** Adds a scheme when missing and drops trailing slashes; falls back to the build value. */
    fun sanitize(value: String): String = normalize(value) ?: buildTimeBaseUrl

    /** WebSocket endpoint derived from the same base URL, so ws:// and https:// stay in sync. */
    fun webSocketUrl(baseUrl: String): String {
        val base = sanitize(baseUrl)
        val scheme = if (base.startsWith("https://")) "wss://" else "ws://"
        return scheme + base.substringAfter("://") + "/ws"
    }

    private fun normalize(value: String): String? {
        var v = value.trim()
        if (v.isEmpty()) return null
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://$v"
        while (v.endsWith("/")) v = v.dropLast(1)
        return v.ifEmpty { null }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
