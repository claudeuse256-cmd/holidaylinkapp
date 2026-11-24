package org.holidaylink.app.push

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.config.AppConfig
import org.holidaylink.app.data.AccountStore
import org.holidaylink.app.net.AppPresence
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.Notifier
import org.holidaylink.app.net.PushNotify

/**
 * Receives Firebase Cloud Messaging pushes. This is the piece that lets notifications
 * arrive when the app is closed/killed — the server sends a data message and we draw it.
 *
 * Expected data payload: { type: "message"|"call", title, body, chatId?/callId?, forUid? }
 * If the server sends the notification block as well, Android draws it while the app is in
 * the background and this callback simply is not invoked; tapping it opens the chat.
 */
class HolidayMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushTokens.save(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: "message"
        val title = data["title"] ?: message.notification?.title ?: "HolidayLink"
        val body = data["body"] ?: message.notification?.body ?: ""
        // Only a conversation can be reopened from the tray. A call has no chat of its
        // own, so tapping its alert just brings the app forward — if the call is still
        // ringing, the incoming-call screen is already waiting there.
        val chatTarget = data["chatId"]?.takeIf { it.isNotBlank() }
        // The phone has the message now: flip it to the double grey tick for the sender, the
        // way WhatsApp does when the notification arrives (no need to open the chat first).
        if (type == "message" && chatTarget != null) markDeliveredNow(chatTarget, data["forUid"])
        // While the app is on screen the in-app listener already raises the alert, so
        // drawing a second one here would only buzz twice.
        if (AppPresence.foreground) return
        // One alert per conversation: the same id the in-app listener uses, so an alert
        // that already exists is replaced rather than duplicated.
        val id = when {
            type == "call" -> Notifier.idFor(data["callId"] ?: body.take(24))
            chatTarget != null -> Notifier.idFor(chatTarget)
            else -> (data["id"] ?: body.take(24)).hashCode()
        }
        // Already looking at that conversation: no alert at all.
        if (AppPresence.isReading(chatTarget)) {
            Notifier.clearChat(applicationContext, chatTarget!!)
            return
        }
        // One phone can hold several accounts, so say which one a notification is for —
        // the way WhatsApp labels notifications from a linked account.
        val account = accountLabel(applicationContext, data["forUid"])
        if (type == "call") {
            Notifier.call(applicationContext, id, title, body, null, account)
        } else {
            Notifier.message(
                applicationContext, id,
                // In a group the title is the group name; the sender's own picture still
                // identifies who wrote it.
                title, body, chatTarget, account,
                photoUrl = data["senderPhoto"]?.takeIf { it.isNotBlank() }
            )
        }
    }

    /** Best effort, bounded: the push service only gives us a few seconds. */
    private fun markDeliveredNow(chatId: String, forUid: String?) {
        val current = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!forUid.isNullOrBlank() && forUid != current) return
        runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(8_000) {
                    runCatching { org.holidaylink.app.net.FirestoreChat.markDelivered(chatId) }
                }
            }
        }
    }

    /** Name of the saved account a push was addressed to, or null when it is the current one. */
    private fun accountLabel(context: Context, forUid: String?): String? {
        if (forUid.isNullOrBlank()) return null
        if (FirebaseAuth.getInstance().currentUser?.uid == forUid) return null
        val prefs = context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
        val name = AccountStore.all(prefs).firstOrNull { it.userId == forUid }?.displayName
        return name?.takeIf { it.isNotBlank() }?.let { "Account: $it" }
    }
}

/** Stores this device's FCM token on the user's row so the server can target it. */
object PushTokens {

    /** Last token seen this session, so sign-out can revoke it without waiting on the SDK. */
    @Volatile
    private var lastToken: String? = null

    fun register() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> save(token, uid) }
    }

    fun save(token: String, uid: String? = FirebaseAuth.getInstance().currentUser?.uid) {
        if (uid.isNullOrBlank() || token.isBlank()) return
        lastToken = token
        // Two routes on purpose. The Firestore field is what the website and older server
        // builds read; the server route survives Firestore rules that do not yet allow the
        // owner to write fcmTokens (the usual reason background pushes silently stop).
        PushNotify.registerToken(token)
        runCatching {
            FirebaseBackend.db.collection("users").document(uid)
                .set(
                    mapOf(
                        "fcmTokens" to FieldValue.arrayUnion(token),
                        "fcmTokenAt" to com.google.firebase.Timestamp.now()
                    ),
                    SetOptions.merge()
                )
                .addOnFailureListener { error ->
                    android.util.Log.w(
                        "HolidayLinkPush",
                        "Firestore refused users/$uid fcmTokens (check the deployed rules)",
                        error
                    )
                }
        }
    }

    /**
     * Drops this device from [uid]'s push list. Called while signing out, before the
     * session is cleared, because Firestore rejects owner writes once auth is gone.
     * Without this the phone kept buzzing for an account that had been signed out.
     */
    suspend fun unregisterFrom(uid: String) {
        val token = lastToken
            ?: runCatching { com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await() }
                .getOrNull()
        if (token.isNullOrBlank()) return
        runCatching {
            FirebaseBackend.db.collection("users").document(uid)
                .update("fcmTokens", FieldValue.arrayRemove(token))
                .await()
        }
        lastToken = null
    }
}
