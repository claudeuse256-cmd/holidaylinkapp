package org.holidaylink.app.data

import android.content.Context
import org.holidaylink.app.config.AppConfig

/**
 * Status updates this phone has already watched.
 *
 * The only record used to be the `status_views` document in Firestore, which needs a network
 * round trip, so the reel always looked "new" again after a restart or while offline. The ids
 * are kept on the phone (statuses expire in 24 h, so the list is trimmed to the most recent few
 * hundred) purely for the UI: the server document stays the source of truth for "Seen by".
 */
object StatusSeen {
    private const val KEY = "status.seen.v1"
    private const val MAX = 400

    fun ids(context: Context): Set<String> = read(prefs(context)).toSet()

    fun markAll(context: Context, ids: Collection<String>) {
        val fresh = ids.filter { it.isNotBlank() }
        if (fresh.isEmpty()) return
        val prefs = prefs(context)
        val current = read(prefs).toMutableList()
        val added = fresh.filterNot { it in current }
        if (added.isEmpty()) return
        current.addAll(added)
        prefs.edit()
            .putString(KEY, current.takeLast(MAX).joinToString("\n"))
            .apply()
    }

    private fun read(prefs: android.content.SharedPreferences): List<String> =
        prefs.getString(KEY, null).orEmpty().split('\n').filter { it.isNotBlank() }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
}
