package org.holidaylink.app.data

import android.content.SharedPreferences
import org.holidaylink.app.net.Me
import org.json.JSONObject

/**
 * Last known copy of the signed-in user's own profile.
 *
 * Firebase Auth restores the session from disk, but the profile lives in Firestore, so on a
 * cold launch without internet [org.holidaylink.app.net.FirebaseBackend.loadProfile] cannot
 * answer and the app used to fall back to "Not signed in". This keeps the profile on the phone
 * so Settings, the profile screen and the avatar render immediately and stay correct offline.
 */
object ProfileCache {
    private const val KEY = "me.cache.v1"

    fun save(prefs: SharedPreferences, me: Me) {
        val o = JSONObject()
            .put("id", me.id)
            .put("phone", me.phone)
            .put("email", me.email)
            .put("displayName", me.displayName)
            .put("username", me.username)
            .put("avatarUrl", me.avatarUrl)
            .put("about", me.about)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    fun load(prefs: SharedPreferences, uid: String): Me? {
        if (uid.isBlank()) return null
        val raw = prefs.getString(KEY, null) ?: return null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (o.optString("id") != uid) return null
        val phone = o.optString("phone")
        val displayName = o.optString("displayName")
        if (phone.isBlank() && displayName.isBlank()) return null
        return Me(
            id = uid,
            phone = phone,
            email = o.optString("email").takeIf { it.isNotBlank() },
            displayName = displayName.takeIf { it.isNotBlank() } ?: "HolidayLink user",
            username = o.optString("username"),
            avatarUrl = o.optString("avatarUrl").takeIf { it.isNotBlank() },
            about = o.optString("about").takeIf { it.isNotBlank() },
            online = false
        )
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove(KEY).apply()
    }
}
