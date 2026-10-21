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
import java.util.concurrent.ConcurrentHashMap

/**
 * System-tray notifications (the ones that pop up outside the app), WhatsApp-style:
 * a high-importance "Messages" channel and a "Calls" channel.
 *
 * One notification per conversation (the notification id is derived from the chat id),
 * so a new message in the same chat replaces the previous alert instead of stacking.
 * A conversation's notification is removed the moment it is *seen* — opening the chat
 * clears it even when the alert itself was never tapped.
 */
object Notifier {
    const val CHANNEL_MESSAGES = "hl_messages"
    const val CHANNEL_CALLS = "hl_calls"
    const val EXTRA_TARGET = "hl_target"

    /** chatId → notification ids posted for it, so "seen" can clear every variant. */
    private val posted = ConcurrentHashMap<String, MutableSet<Int>()

    /** The full-colour HolidayLink logo, shown large in the expanded notification. */
    @Volatile
    private var logoCache: android.graphics.Bitmap? = null

    /**
     * Decodes (once) and downscales the brand logo so the expanded panel shows the real
     * mark. The tiny status-bar icon is only a silhouette; this is what makes the
     * notification read as "HolidayLink" at a glance.
     */
    fun logo(context: Context): android.graphics.Bitmap? {
        logoCache?.let { return it }
        return runCatching {
            val src = android.graphics.BitmapFactory.decodeResource(
                context.resources, org.holidaylink.app.R.drawable.holidaylink_icon
            ) ?: return null
            val size = 160
            val scaled = android.graphics.Bitmap.createScaledBitmap(
                src, size, (src.height.toLong() * size / src.width).toInt().coerceAtLeast(1), true
            )
            if (scaled !== src) src.recycle()
            logoCache = scaled
            scaled
        }.getOrNull()
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_MESSAGES) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New chat messages"
                    enableVibration(true)
                }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_CALLS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Incoming audio and video calls"
                    enableVibration(true)
                }
            )
        }
    }

    fun message(
        context: Context,
        id: Int,
        title: String,
        body: String,
        targetId: String? = null,
        subText: String? = null
    ) = post(context, CHANNEL_MESSAGES, id, title, body, targetId, subText)

    fun call(
        context: Context,
        id: Int,
        title: String,
        body: String,
        targetId: String? = null,
        subText: String? = null
    ) = post(context, CHANNEL_CALLS, id, title, body, targetId, subText)

    /** A stable per-conversation notification id (same value the FCM service uses). */
    fun idFor(chatId: String): Int = chatId.hashCode()

    /** The tag the server asks Android to use, so one alert per conversation. */
    fun tagFor(chatId: String): String = "chat-$chatId"

    /**
     * Removes every alert for this conversation. Called when the chat is opened or when new
     * messages arrive while it is already on screen — WhatsApp's "seen means gone".
     */
    fun clearChat(context: Context, chatId: String) {
        if (chatId.isBlank()) return
        val nm = NotificationManagerCompat.from(context)
        runCatching { nm.cancel(idFor(chatId)) }
        // Alerts drawn by Android itself (from a Firebase push) carry the conversation tag.
        runCatching { nm.cancel(tagFor(chatId), 0) }
        posted.remove(chatId)?.forEach { runCatching { nm.cancel(it) } }
    }

    /** Clears the active announcement/pinned alert (kept from the announcement card). */
    fun clearAnnouncement(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(AnnouncementNotifier.NOTIFICATION_ID)
        }
    }

    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        body: String,
        targetId: String?,
        subText: String?
    ) {
        ensureChannels(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        // Never buzz for the conversation that is already open on screen. A call always
        // alerts, even when the conversation it belongs to happens to be on display.
        if (channel == CHANNEL_MESSAGES && AppPresence.isReading(targetId)) {
            clearChat(context, targetId!!)
            return
        }
        // Respect the per-type switches in Settings ▸ Notifications.
        runCatching {
            val allowed = if (channel == CHANNEL_CALLS) {
                org.holidaylink.app.data.AppSettings.state.notifyCalls
            } else {
                org.holidaylink.app.data.AppSettings.state.notifyMessages
            }
            if (!allowed) return
        }
        // A muted conversation stays silent in the shade too.
        if (channel == CHANNEL_MESSAGES && targetId != null) {
            val muted = runCatching { org.holidaylink.app.data.AppSettings.isChatMuted(targetId) }
                .getOrDefault(false)
            if (muted) return
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
        var preview = true
        runCatching {
            sound = org.holidaylink.app.data.AppSettings.state.notifySound
            vibrate = org.holidaylink.app.data.AppSettings.state.notifyVibration
            preview = org.holidaylink.app.data.AppSettings.state.notifyPreview
        }
        val defaults = when {
            sound && vibrate -> NotificationCompat.DEFAULT_ALL
            sound -> NotificationCompat.DEFAULT_SOUND
            vibrate -> NotificationCompat.DEFAULT_VIBRATE
            else -> 0
        }
        val shownBody = if (preview) body else "New message"
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_holidaylink)
            .setLargeIcon(logo(context))
            .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(shownBody)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(shownBody))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (channel == CHANNEL_CALLS) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(defaults)
            .setContentIntent(pi)
            .build()
        if (targetId != null) {
            posted.getOrPut(targetId) { ConcurrentHashMap.newKeySet() }.add(id)
        }
        try {
            nm.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted: nothing we can do, the in-app list still works.
        }
    }
}
