package org.holidaylink.app.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Local multi-account vault. Tokens remain only in the app's private SharedPreferences. */
object AccountStore {
    private const val KEY = "accounts.v1"

    data class Account(
        val userId: String,
        val token: String,
        val phone: String,
        val displayName: String,
        val username: String
    )

    fun all(prefs: SharedPreferences): List<Account> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val token = o.optString("token")
                val userId = o.optString("userId")
                if (token.isBlank() || userId.isBlank()) null else Account(
                    userId, token, o.optString("phone"), o.optString("displayName", "HolidayLink user"), o.optString("username")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun upsert(prefs: SharedPreferences, account: Account) {
        val list = all(prefs).filterNot { it.userId == account.userId }.toMutableList()
        list.add(0, account)
        write(prefs, list.take(5))
    }

    fun remove(prefs: SharedPreferences, userId: String) {
        write(prefs, all(prefs).filterNot { it.userId == userId })
    }

    fun clear(prefs: SharedPreferences) { prefs.edit().remove(KEY).apply() }

    private fun write(prefs: SharedPreferences, list: List<Account>) {
        val a = JSONArray()
        list.forEach { x ->
            a.put(JSONObject().put("userId", x.userId).put("token", x.token).put("phone", x.phone).put("displayName", x.displayName).put("username", x.username))
        }
        prefs.edit().putString(KEY, a.toString()).apply()
    }
}
