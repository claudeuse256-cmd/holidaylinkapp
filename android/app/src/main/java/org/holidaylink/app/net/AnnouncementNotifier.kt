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

    const val CHANNEL = "hl_announcements"
    private const val NOTIFICATION_ID = 4701

    private var registration: com.google.firebase.firestore.ListenerRegistration? = null
    private var lastShown: String = ""

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Announcements", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The latest HolidayLink announcement, kept in the shade"
                    setShowBadge(false)
                }
            )
        }
    }

    /** Starts (or refreshes) the listener that feeds the pinned announcement card. */
    fun start(context: Context) {
        val app = context.applicationContext
        ensureChannel(app)
        if (registration != null) return
        registration = runCatching {
            FirebaseBackend.db.collection("posters")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, _ ->
                    // Snapshot callbacks run on the main thread: never let a bad
                    // document take the app down.
                    runCatching {
                        val doc = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                        val title = (doc.getString("title") ?: "").trim()
                        val body = (doc.getString("message") ?: doc.getString("content") ?: "").trim()
                        show(
                            app,
                            title.ifBlank { "HolidayLink announcement" },
                            body.ifBlank { "Open HolidayLink to read the latest announcement." }
                        )
                    }
                }
        }.getOrNull()
    }

    fun stop() {
        runCatching { registration?.remove() }
        registration = null
    }

    private fun show(context: Context, title: String, body: String) {
        val fingerprint = "$title\u0000$body"
        val firstTime = fingerprint != lastShown
        lastShown = fingerprint

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
            .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!firstTime)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pi)
            .build()

        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }
}
