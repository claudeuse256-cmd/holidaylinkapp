package org.holidaylink.app.net

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.holidaylink.app.MainActivity
import org.holidaylink.app.R

/**
 * Pins the newest announcement to the notification shade.
 *
 * The card is posted as soon as the signed-in app opens, is `ongoing` (it cannot be
 * swiped away), and is rewritten in place whenever a newer `posters` document appears.
 * It only makes a sound the first time a given announcement is shown, so it does not
 * nag on every app launch.
 */
object AnnouncementNotifier {

    // A new id (v2): the old channel was created at LOW importance, which parks the card in
    // the phone's collapsed "silent" section — where it looked like it was missing. Channel
    // importance can't be raised after creation, so this one starts at DEFAULT.
    const val CHANNEL = "hl_announcements_v2"
    private const val LEGACY_CHANNEL = "hl_announcements"
    const val NOTIFICATION_ID = 4701

    private var registration: com.google.firebase.firestore.ListenerRegistration? = null

    private const val PREFS = "hl_announce"
    private const val KEY_TITLE = "last_title"
    private const val KEY_BODY = "last_body"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Announcements", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The latest HolidayLink announcement, kept in the shade"
                    setShowBadge(false)
                }
            )
        }
        // Nothing was ever posted on the old id; remove it so the list of channels stays clean.
        runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL) }
    }

    /** Starts (or refreshes) the listener that feeds the pinned announcement card. */
    fun start(context: Context) {
        val app = context.applicationContext
        ensureChannel(app)
        // An app update (or a reboot / "clear notifications") wipes the system tray, and
        // the Firestore snapshot can take a beat to arrive — or fail while offline. Re-post
        // the last announcement we showed straight away so the pinned card is never lost.
        repost(app)
        if (registration != null) return
        // No `orderBy`: sorting server-side silently drops every poster that lacks that
        // exact field (and needs an index), which left the pinned card empty. Fetch a page
        // and pick the newest client-side instead.
        registration = runCatching {
            FirebaseBackend.db.collection("posters")
                .limit(60)
                .addSnapshotListener { snapshot, _ ->
                    // Snapshot callbacks run on the main thread: never let a bad
                    // document take the app down.
                    runCatching {
                        val docs = snapshot?.documents ?: return@addSnapshotListener
                        val doc = docs.maxByOrNull { postedAtMillis(it) } ?: return@addSnapshotListener
                        val title = (doc.getString("title") ?: "").trim()
                        val body = firstText(doc, "message", "content", "body", "text")
                        if (title.isBlank() && body.isBlank()) return@addSnapshotListener
                        show(
                            app,
                            title.ifBlank { "HolidayLink announcement" },
                            body.ifBlank { "Open HolidayLink to read the latest announcement." }
                        )
                    }
                }
        }.getOrNull()
    }

    /** Re-posts the cached card without touching Firestore (used when the app resumes). */
    fun repost(context: Context) {
        val app = context.applicationContext
        ensureChannel(app)
        runCatching {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString(KEY_TITLE, null) ?: return
            if (title.isBlank()) return
            show(app, title, prefs.getString(KEY_BODY, null).orEmpty())
        }
    }

    private fun firstText(doc: com.google.firebase.firestore.DocumentSnapshot, vararg keys: String): String {
        keys.forEach { key -> (doc.getString(key) ?: "").trim().let { if (it.isNotBlank()) return it } }
        return ""
    }

    /** Posters use different date fields depending on how they were created. */
    private fun postedAtMillis(doc: com.google.firebase.firestore.DocumentSnapshot): Long =
        listOf("timestamp", "createdAt", "created_at", "postedAt", "date")
            .mapNotNull { doc.safeTimestamp(it)?.seconds }
            .maxOrNull() ?: 0L

    fun stop() {
        runCatching { registration?.remove() }
        registration = null
    }

    private fun show(context: Context, title: String, body: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Compare against what we already showed, not an in-memory flag: after the process is
        // killed the flag resets, and a resume would otherwise buzz again for old news.
        val firstTime = title != prefs.getString(KEY_TITLE, null) || body != prefs.getString(KEY_BODY, null)

        // Remember it so a later launch (or an app update, which cancels every
        // notification) can put the pinned card straight back without waiting on Firestore.
        runCatching {
            prefs.edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_BODY, body)
                .apply()
        }

        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_holidaylink)
            .setLargeIcon(Notifier.logo(context))
            .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!firstTime)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pi)
            .build()

        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }
}
