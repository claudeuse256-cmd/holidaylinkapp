package org.holidaylink.app.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lives-and-dies with the visible app, not with the process.
 *
 * WhatsApp-style delivery depends on the server knowing whether the phone is *actually*
 * being looked at. Before this existed, the presence heartbeat kept running while the app
 * sat in the background, so the recipient always looked "online" and the server skipped
 * every push for up to two minutes — which is why notifications only appeared once the app
 * was reopened. Now going to the background marks us offline straight away, so the very
 * next message is pushed to the phone (and drawn by the FCM service) even when the app is
 * closed. Coming back to the foreground marks us online again.
 */
object AppPresence {

    @Volatile
    var foreground: Boolean = false
        private set

    /** Conversation currently open, used to swallow the alert for a chat you are reading. */
    @Volatile
    var openChatId: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onForeground() {
        foreground = true
        scope.launch { runCatching { FirebaseBackend.touchPresence() } }
    }

    fun onBackground() {
        foreground = false
        scope.launch { runCatching { FirebaseBackend.goOffline() } }
    }

    /** True when an alert for [chatId] would be redundant: the user is reading that chat. */
    fun isReading(chatId: String?): Boolean =
        !chatId.isNullOrBlank() && foreground && openChatId == chatId

    /** Best-effort refresh used by the once-a-minute housekeeping loop. */
    suspend fun heartbeatIfVisible() {
        if (!foreground) return
        runCatching { FirebaseBackend.touchPresence() }
    }
}
