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

    // The Reply / Mark as read buttons (see push/NotificationActionReceiver).
    const val ACTION_REPLY = "org.holidaylink.app.action.REPLY"
    const val ACTION_MARK_READ = "org.holidaylink.app.action.MARK_READ"
    const val KEY_REPLY_TEXT = "hl_reply_text"
    const val EXTRA_CHAT_ID = "hl_chat_id"
    const val EXTRA_NOTIFICATION_ID = "hl_notification_id"
    const val EXTRA_TITLE = "hl_title"
    const val EXTRA_BODY = "hl_body"
    const val EXTRA_PHOTO = "hl_photo"

    /** chatId → notification ids posted for it, so "seen" can clear every variant. */
    private val posted = ConcurrentHashMap<String, MutableSet<Int>>()

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
        subText: String? = null,
        /** Sender's profile picture, shown large like WhatsApp (initial on a colour if none). */
        photoUrl: String? = null,
        /** Texts already sent from the notification's Reply box, shown under the message. */
        replyHistory: List<String> = emptyList(),
        /** Update without sound or vibration (used after a reply). */
        quiet: Boolean = false
    ) = post(context, CHANNEL_MESSAGES, id, title, body, targetId, subText, photoUrl, replyHistory, quiet)

    /** "A new version is available". Tapping opens the app, which shows the update pop-up. */
    fun updateAvailable(context: Context, title: String, body: String) =
        post(context, CHANNEL_MESSAGES, 0x7E57, title, body, null, null, null, emptyList(), false, withActions = false)

    /** Heads-up about a scheduled message ("going out in 1 minute" / "sent"). Taps open the chat. */
    fun scheduled(context: Context, id: Int, title: String, body: String, chatId: String) =
        post(context, CHANNEL_MESSAGES, id, title, body, chatId, null, null, emptyList(), false, withActions = false)

    /** Round picture for the notification: the sender's photo, or their initial on a colour. */
    private fun avatarBitmap(context: Context, name: String, photoUrl: String?): android.graphics.Bitmap? = runCatching {
        val size = 128
        val photo = photoUrl?.takeIf { it.startsWith("http") }?.let { url ->
            runCatching {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    kotlinx.coroutines.withTimeoutOrNull(4_000) { MediaCache.bitmap(context, url) }
                }
            }.getOrNull()
        }
        val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        if (photo != null) {
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            val side = minOf(photo.width, photo.height)
            val from = android.graphics.Rect(
                (photo.width - side) / 2, (photo.height - side) / 2,
                (photo.width + side) / 2, (photo.height + side) / 2
            )
            canvas.drawBitmap(photo, from, android.graphics.Rect(0, 0, size, size), paint)
        } else {
            val palette = intArrayOf(0xFF2563EB.toInt(), 0xFF0891B2.toInt(), 0xFF7C3AED.toInt(), 0xFFDB2777.toInt(), 0xFFEA580C.toInt(), 0xFF059669.toInt())
            paint.color = palette[(name.trim().lowercase().hashCode() and 0x7fffffff) % palette.size]
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.textSize = size * 0.5f
            paint.isFakeBoldText = true
            val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
            canvas.drawText(initial, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        }
        out
    }.getOrNull()

    fun call(
        context: Context,
        id: Int,
        title: String,
        body: String,
        targetId: String? = null,
        subText: String? = null
    ) = post(context, CHANNEL_CALLS, id, title, body, targetId, subText, null, emptyList(), false)

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
        subText: String?,
        photoUrl: String?,
        replyHistory: List<String>,
        quiet: Boolean,
        withActions: Boolean = true
    ) {
        ensureChannels(context)
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
        // Never buzz for the conversation that is already open on screen. A call always
        // alerts, even when the conversation it belongs to happens to be on display.
        if (channel == CHANNEL_MESSAGES && AppPresence.isReading(targetId)) {
            clearChat(context, targetId!!)
            return
        }
        val preview = runCatching { org.holidaylink.app.data.AppSettings.state.notifyPreview }
            .getOrDefault(true)
        val shownBody = if (preview) body else "New message"
        // Notifications always use Android's notification shade. There is deliberately
        // no custom in-app toast/banner surface: when a different chat receives a message,
        // Android handles it exactly like any other device notification. The currently
        // open conversation is filtered above so it never notifies itself.
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
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
            quiet -> 0
            sound && vibrate -> NotificationCompat.DEFAULT_ALL
            sound -> NotificationCompat.DEFAULT_SOUND
            vibrate -> NotificationCompat.DEFAULT_VIBRATE
            else -> 0
        }
        val isMessage = channel == CHANNEL_MESSAGES && targetId != null && withActions
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_holidaylink)
            .setLargeIcon(if (isMessage) avatarBitmap(context, title, photoUrl) ?: logo(context) else logo(context))
            .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(shownBody)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(shownBody))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (channel == CHANNEL_CALLS) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(defaults)
            .setOnlyAlertOnce(quiet)
            .setContentIntent(pi)
        if (replyHistory.isNotEmpty()) {
            builder.setRemoteInputHistory(replyHistory.map { it as CharSequence }.toTypedArray())
        }
        if (isMessage) {
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            fun actionIntent(action: String) = Intent(context, org.holidaylink.app.push.NotificationActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_CHAT_ID, targetId)
                .putExtra(EXTRA_NOTIFICATION_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BODY, body)
                .putExtra(EXTRA_PHOTO, photoUrl)
            val replyInput = androidx.core.app.RemoteInput.Builder(KEY_REPLY_TEXT).setLabel("Reply").build()
            val replyPi = PendingIntent.getBroadcast(
                context, id, actionIntent(ACTION_REPLY), PendingIntent.FLAG_UPDATE_CURRENT or mutable
            )
            builder.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_stat_holidaylink, "Reply", replyPi)
                    .addRemoteInput(replyInput)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .build()
            )
            val readPi = PendingIntent.getBroadcast(
                context, id, actionIntent(ACTION_MARK_READ), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_stat_holidaylink, "Mark as read", readPi)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build()
            )
        }
        val notification = builder.build()
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
