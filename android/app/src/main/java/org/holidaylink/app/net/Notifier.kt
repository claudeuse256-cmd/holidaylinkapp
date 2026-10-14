package org.holidaylink.app.net

import android.annotation.SuppressLint
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
 * System-tray notifications (the ones that pop up outside the app), WhatsApp-style:
 * a high-importance "Messages" channel and a "Calls" channel.
 *
 * Two things feed this: a live Firestore listener over the user's `notifications`
 * documents (works while the app process is alive) and Firebase Cloud Messaging
 * (works when the app is killed, once the server sends a push).
 */
object Notifier {
    const val CHANNEL_MESSAGES = "hl_messages"
    const val CHANNEL_CALLS = "hl_calls"
    const val EXTRA_TARGET = "hl_target"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_MESSAGES) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New chat messages"
                }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_CALLS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Incoming audio and video calls"
                }
            )
        }
    }

    fun message(context: Context, id: Int, title: String, body: String, targetId: String? = null) =
        post(context, CHANNEL_MESSAGES, id, title, body, targetId)

    fun call(context: Context, id: Int, title: String, body: String, targetId: String? = null) =
        post(context, CHANNEL_CALLS, id, title, body, targetId)

    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        body: String,
        targetId: String?
    ) {
        ensureChannels(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        // Respect the per-type switches in Settings ▸ Notifications.
        runCatching {
            val allowed = if (channel == CHANNEL_CALLS) {
                org.holidaylink.app.data.AppSettings.state.notifyCalls
            } else {
                org.holidaylink.app.data.AppSettings.state.notifyMessages
            }
            if (!allowed) return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (targetId != null) putExtra(EXTRA_TARGET, targetId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context, id, intent, flags)
        // Honour the user's in-app sound/vibration switches. If the app process was started
        // straight by FCM (activity never ran) the settings aren't loaded yet, so default on.
        var sound = true
        var vibrate = true
        runCatching {
            sound = org.holidaylink.app.data.AppSettings.state.notifySound
            vibrate = org.holidaylink.app.data.AppSettings.state.notifyVibration
        }
        val defaults = when {
            sound && vibrate -> NotificationCompat.DEFAULT_ALL
            sound -> NotificationCompat.DEFAULT_SOUND
            vibrate -> NotificationCompat.DEFAULT_VIBRATE
            else -> 0
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_holidaylink)
            .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (channel == CHANNEL_CALLS) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(defaults)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted: nothing we can do, the in-app list still works.
        }
    }
}
