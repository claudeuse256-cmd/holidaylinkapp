package org.holidaylink.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.net.FirestoreChat
import org.holidaylink.app.net.Notifier

/**
 * The two buttons under a message notification, like WhatsApp:
 *  - Reply: sends the typed text to that conversation straight from the notification shade;
 *  - Mark as read: clears the unread count and lets the sender see the blue ticks.
 *
 * Works while the app is closed: the receiver wakes the process just long enough to do it.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(Notifier.EXTRA_CHAT_ID)?.takeIf { it.isNotBlank() } ?: return
        val notificationId = intent.getIntExtra(Notifier.EXTRA_NOTIFICATION_ID, Notifier.idFor(chatId))
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val receipts = runCatching { AppSettings.state.readReceipts }.getOrDefault(true)
                when (intent.action) {
                    Notifier.ACTION_MARK_READ -> {
                        withTimeoutOrNull(8_000) { runCatching { FirestoreChat.markRead(chatId, receipts) } }
                        Notifier.clearChat(app, chatId)
                    }

                    Notifier.ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(Notifier.KEY_REPLY_TEXT)?.toString()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            // Queued by Firestore, so it also goes out later if there is no signal now.
                            withTimeoutOrNull(8_000) { runCatching { FirestoreChat.send(chatId, text) } }
                            withTimeoutOrNull(5_000) { runCatching { FirestoreChat.markRead(chatId, receipts) } }
                            // Re-post with the reply shown, which also stops the "sending" spinner.
                            Notifier.message(
                                app, notificationId,
                                intent.getStringExtra(Notifier.EXTRA_TITLE).orEmpty().ifBlank { "HolidayLink" },
                                intent.getStringExtra(Notifier.EXTRA_BODY).orEmpty(),
                                chatId, null,
                                photoUrl = intent.getStringExtra(Notifier.EXTRA_PHOTO),
                                replyHistory = listOf(text),
                                quiet = true
                            )
                        } else {
                            Notifier.clearChat(app, chatId)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
