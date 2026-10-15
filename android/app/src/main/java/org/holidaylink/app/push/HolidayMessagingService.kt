package org.holidaylink.app.push

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.holidaylink.app.config.AppConfig
import org.holidaylink.app.data.AccountStore
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.Notifier

/**
 * Receives Firebase Cloud Messaging pushes. This is the piece that lets notifications
 * arrive when the app is closed/killed — the server sends a data message and we draw it.
 *
 * Expected data payload: { type: "message"|"call", title, body, chatId?/callId?, forUid? }
 * If the server only sends a `notification` payload, Android displays it automatically.
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
        val id = (data["id"] ?: chatTarget ?: data["callId"] ?: body.take(24)).hashCode()
        // One phone can hold several accounts, so say which one a notification is for —
        // the way WhatsApp labels notifications from a linked account.
        val account = accountLabel(applicationContext, data["forUid"])
        if (type == "call") {
            Notifier.call(applicationContext, id, title, body, chatTarget, account)
        } else {
            Notifier.message(applicationContext, id, title, body, chatTarget, account)
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
    fun register() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> save(token, uid) }
    }

    fun save(token: String, uid: String? = FirebaseAuth.getInstance().currentUser?.uid) {
        if (uid.isNullOrBlank() || token.isBlank()) return
        runCatching {
            FirebaseBackend.db.collection("users").document(uid)
                .set(
                    mapOf(
                        "fcmTokens" to FieldValue.arrayUnion(token),
                        "fcmTokenAt" to com.google.firebase.Timestamp.now()
                    ),
                    SetOptions.merge()
                )
        }
    }
}
