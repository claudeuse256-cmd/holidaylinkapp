package org.holidaylink.app.net

import android.content.Context
import android.util.Log
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.holidaylink.app.config.AppConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Thrown with a message that is already safe to show to a user. Details stay in logcat. */
class ApiException(
    message: String,
    val code: String? = null,
    val httpCode: Int = 0,
    val retryAfterSeconds: Int = 0
) : Exception(message)

/** Privacy audience shared by Last Seen, Photo, About and Status rules. */
enum class Audience(val wire: String, val label: String) {
    EVERYONE("everyone", "Everyone"),
    CONTACTS("contacts", "My contacts"),
    NOBODY("nobody", "Nobody");

    companion object {
        fun from(wire: String?): Audience =
            entries.firstOrNull { it.wire == wire } ?: EVERYONE
    }
}

/** Status/Stories has extra rules beyond the three standard audiences. */
enum class StatusAudience(val wire: String, val label: String) {
    EVERYONE("everyone", "Everyone"),
    CONTACTS("contacts", "My contacts"),
    CONTACTS_EXCEPT("contacts_except", "My contacts except..."),
    ONLY_SHARE_WITH("only_share_with", "Only share with...");

    companion object {
        fun from(wire: String?): StatusAudience =
            entries.firstOrNull { it.wire == wire } ?: CONTACTS
    }
}

data class User(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String? = null,
    val online: Boolean = false,
    val lastSeen: String? = null,
    val about: String? = null,
    /**
     * The other person's own privacy switches, read from their profile. Both sides of a
     * conversation have to agree before a blue tick or a "typing…" label is shown, which is
     * what makes turning either of them off actually mean something.
     */
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true
)

/** The signed-in user. */
data class Me(
    val id: String,
    val phone: String,
    val email: String? = null,
    val displayName: String,
    val username: String,
    val avatarUrl: String? = null,
    val about: String? = null,
    val online: Boolean = false,
    val lastSeen: String? = null,
    val createdAt: String? = null
)

/**
 * Another user's profile as the server is willing to expose it.
 * Fields the viewer is not allowed to see arrive as null rather than being hidden in the UI.
 */
data class Profile(
    val id: String,
    val displayName: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val about: String? = null,
    val phone: String? = null,
    val online: Boolean? = null,
    val lastSeen: String? = null,
    val isBlocked: Boolean = false,
    val blockedByThem: Boolean = false
)

/** Result of matching a batch of hashed device numbers. */
data class ContactMatch(
    val user: User,
    val hash: String
)

data class ChatItem(
    val id: String,
    val kind: String,
    val title: String?,
    val peer: User?,
    val lastBody: String?,
    val lastAt: String?,
    val unreadCount: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val peerTyping: Boolean = false
)

data class Reaction(val emoji: String, val count: Int, val mine: Boolean)

data class Attachment(
    val id: String,
    val kind: String,
    val url: String?,
    val name: String?,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val waveform: String? = null
)

data class Message(
    val id: String,
    val senderId: String,
    val body: String,
    val createdAt: String,
    val messageType: String = "text",
    val replyToId: String? = null,
    val replyToBody: String? = null,
    val replyToSenderName: String? = null,
    val editedAt: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val attachment: Attachment? = null,
    val status: String = "sent",
    val pinned: Boolean = false,
    val expiresAt: String? = null,
    val viewOnce: Boolean = false,
    /** Present on search results, where the server tells us which chat a hit belongs to. */
    val conversationId: String? = null,
    val conversationKind: String? = null,
    val conversationTitle: String? = null,
    val conversationPeerId: String? = null,
    val conversationPeerName: String? = null
) {
    /** Label for the chat a search hit belongs to, without an extra request. */
    val conversationLabel: String?
        get() = if (conversationKind == "direct") {
            conversationPeerName ?: "Direct chat"
        } else {
            conversationTitle ?: "Group chat"
        }
}

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: String? = null,
    /** Conversation this notification refers to, when the server included one. */
    val conversationId: String? = null,
    val senderId: String? = null
)

data class OtpRequestResult(
    val phone: String,
    val exists: Boolean,
    val resendAfterSeconds: Int
)

data class VerifyResult(
    val needsProfile: Boolean,
    val phone: String,
    val token: String? = null,
    val userId: String? = null
)

data class AuthResult(
    val token: String,
    val userId: String
)

data class StoreItem(
    val id: String, val title: String, val description: String,
    val priceMinor: Long, val currency: String, val imageUrl: String?
)

data class TwoFactorStatus(val enabled: Boolean, val updatedAt: String? = null)

data class HealthInfo(
    val ok: Boolean,
    val database: String,
    val detail: String
)

/** Server-enforced privacy rules. The client renders these, it never decides them. */
data class PrivacySettings(
    val lastSeen: Audience = Audience.EVERYONE,
    val profilePhoto: Audience = Audience.EVERYONE,
    val about: Audience = Audience.EVERYONE,
    val status: StatusAudience = StatusAudience.CONTACTS,
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val groupInvites: Audience = Audience.EVERYONE,
    val calls: Audience = Audience.EVERYONE,
    val disappearingSeconds: Long = 0L,
    val statusExclude: List<String> = emptyList(),
    val statusOnly: List<String> = emptyList()
)

/** Server-stored notification preferences. */
data class NotificationSettings(
    val messages: Boolean = true,
    val calls: Boolean = true,
    val status: Boolean = true,
    val ai: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val preview: Boolean = true
)

data class CallRecord(
    val id: String,
    val peer: User?,
    val kind: String,
    val status: String,
    val incoming: Boolean,
    val createdAt: String,
    val durationSeconds: Int = 0
) {
    val missed: Boolean get() = incoming && status == "missed"
}

data class StatusUpdate(
	    val id: String,
	    val userId: String,
	    val userName: String,
	    val avatarUrl: String? = null,
	    val kind: String = "text",
	    val body: String? = null,
	    val mediaUrl: String? = null,
	    val createdAt: String,
	    val viewerCount: Int = 0,
	    val seenByMe: Boolean = false,
	    /** Voice-note audio as base64 (voice statuses keep the blob on the document). */
	    val audioBlob: String? = null,
	    val durationMs: Long = 0,
	    /** @usernames mentioned in the body, stored so taps can open the mentioned profile. */
	    val mentions: List<String> = emptyList()
	)

data class DeviceSession(
    val id: String,
    val label: String,
    val platform: String,
    val createdAt: String,
    val lastSeenAt: String? = null,
    val current: Boolean = false
)

data class AiConversation(
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String
)

data class AiMessage(
    val id: String,
    val role: String,
    val text: String,
    val createdAt: String,
    val failed: Boolean = false
)

data class AiReply(
    val conversationId: String,
    val messageId: String,
    val text: String
)

data class AiBot(
    val id: String,
    val username: String,
    val displayName: String,
    val ownerId: String,
    val ownerName: String? = null,
    val mine: Boolean = false,
    val createdAt: String? = null
)

/** One bucket of the global search screen. */
data class SearchResults(
    val chats: List<ChatItem> = emptyList(),
    val contacts: List<User> = emptyList(),
    val messages: List<Message> = emptyList(),
    val users: List<User> = emptyList(),
    val ai: List<AiConversation> = emptyList(),
    val total: Int = 0
)

/**
 * All HTTP access to the HolidayLink backend.
 *
 * Every public function is a suspend function that runs on Dispatchers.IO, so no screen
 * ever blocks the main thread. The base URL comes from AppConfig, never from a call site.
 */
class Api(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs = appContext
        .getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val e2ee = E2ee(appContext)

    private fun baseUrl(): String = AppConfig.resolve(appContext)

    fun sessionToken(): String? = prefs.getString("token", null)?.takeIf { it.isNotBlank() }

    fun userId(): String? = prefs.getString("userId", null)?.takeIf { it.isNotBlank() }

    fun realtimeUrl(): String {
        val u=baseUrl(); return (if(u.startsWith("https://")) "wss://" else "ws://") + u.removePrefix("https://").removePrefix("http://") + "/ws?token=${URLEncoder.encode(sessionToken().orEmpty(), "UTF-8")}"
    }

    suspend fun updateCall(callId:String,status:String){ patchJson("calls/${enc(callId)}", JSONObject().put("status",status)) }

    // ---------------------------------------------------------------- auth


    suspend fun loginWithTwoFactor(internationalPhone: String, password: String): AuthResult {
        val body = postJson("auth/login-2fa", JSONObject().put("phone", internationalPhone).put("password", password))
        return AuthResult(body.optString("token"), body.optJSONObject("user")?.optString("id").orEmpty())
    }

    suspend fun twoFactorStatus(): TwoFactorStatus {
        val body = getJson("auth/2fa/status")
        return TwoFactorStatus(body.optBoolean("enabled"), body.optString("updatedAt").takeIf { it.isNotBlank() })
    }

    suspend fun setTwoFactorPassword(password: String): Boolean {
        val body = postJson("auth/2fa/password", JSONObject().put("password", password))
        return body.optBoolean("enabled")
    }

    suspend fun disableTwoFactorPassword(): Boolean {
        val body = postJson("auth/2fa/password/disable", JSONObject())
        return body.optBoolean("enabled")
    }

    // ---------------------------------------------------------------- Firebase OTP (Render otp service)

    data class FirebaseOtpRequest(val phone: String, val resendAfterSeconds: Int)
    data class FirebaseOtpResult(val customToken: String, val uid: String, val isNew: Boolean, val phone: String)

    /** Request an Infobip SMS code. The code hash lives in Firebase for 5 minutes. */
    suspend fun otpRequest(internationalPhone: String): FirebaseOtpRequest {
        val body = postJson("otp/request", JSONObject().put("phone", internationalPhone))
        val normalized = body.optString("phone").takeIf { it.isNotBlank() } ?: internationalPhone
        return FirebaseOtpRequest(
            phone = normalized,
            resendAfterSeconds = body.optInt("resendAfterSeconds", 45).coerceAtLeast(0)
        )
    }

    /** Verify the SMS code. Returns a Firebase custom token to sign in with. */
    suspend fun otpVerify(internationalPhone: String, pin: String): FirebaseOtpResult {
        val body = postJson(
            "otp/verify",
            JSONObject().put("phone", internationalPhone).put("pin", pin)
        )
        val token = body.optString("token")
        if (token.isBlank()) throw ApiException("That code is incorrect or has expired. Check the SMS and try again.")
        return FirebaseOtpResult(
            customToken = token,
            uid = body.optString("uid"),
            isNew = body.optBoolean("isNew"),
            phone = body.optString("phone").takeIf { it.isNotBlank() } ?: internationalPhone
        )
    }

    data class PhoneCheck(val phone:String,val exists:Boolean,val twoFactorEnabled:Boolean)

    suspend fun checkPhone(internationalPhone:String): PhoneCheck {
        val b=postJson("auth/check-phone",JSONObject().put("phone",internationalPhone));
        return PhoneCheck(b.optString("phone",internationalPhone),b.optBoolean("exists"),b.optBoolean("twoFactorEnabled"))
    }

    suspend fun requestOtp(internationalPhone: String): OtpRequestResult {
        val body = postJson("auth/request-otp", JSONObject().put("phone", internationalPhone))
        val normalized = body.optString("phone").takeIf { it.isNotBlank() } ?: internationalPhone
        return OtpRequestResult(
            phone = normalized,
            exists = body.optBoolean("exists"),
            resendAfterSeconds = body.optInt("resendAfterSeconds", 60).coerceAtLeast(0)
        )
    }

    suspend fun verifyOtp(internationalPhone: String, pin: String): VerifyResult {
        val body = postJson(
            "auth/verify-otp",
            JSONObject().put("phone", internationalPhone).put("pin", pin)
        )
        val normalized = body.optString("phone").takeIf { it.isNotBlank() } ?: internationalPhone
        if (body.optBoolean("needsProfile")) {
            return VerifyResult(needsProfile = true, phone = normalized)
        }
        val user = body.optJSONObject("user")
        return VerifyResult(
            needsProfile = false,
            phone = normalized,
            token = body.optString("token").takeIf { it.isNotBlank() },
            userId = user?.optString("id")?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun completeProfile(
        internationalPhone: String,
        displayName: String,
        username: String,
        email: String,
        twoFactorPassword: String? = null
    ): AuthResult {
        val body = postJson(
            "auth/complete-profile",
            JSONObject()
                .put("phone", internationalPhone)
                .put("displayName", displayName)
                .put("username", username)
                .put("email", email)
                .also { twoFactorPassword?.let { p -> it.put("twoFactorPassword", p) } }
        )
        return AuthResult(
            token = body.optString("token"),
            userId = body.optJSONObject("user")?.optString("id").orEmpty()
        )
    }

    suspend fun statusesCached(): List<StatusUpdate> {
        return try { statuses() } catch (e: Exception) {
            val raw = prefs.getString("cache.statuses", null) ?: throw e
            val array = org.json.JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it).toStatus() }
        }
    }

    suspend fun createChannel(title: String, memberIds: List<String>): String =
        postJson("conversations/channel", JSONObject().put("title", title).put("memberIds", JSONArray(memberIds))).getString("id")

    suspend fun createCommunity(title: String, memberIds: List<String>): String =
        postJson("conversations/community", JSONObject().put("title", title).put("memberIds", JSONArray(memberIds))).getString("id")

    fun appContextForCache(): android.content.Context = appContext

    suspend fun conversationMembers(conversationId: String): List<String> {
        val array = getArray("conversations/${enc(conversationId)}/members")
        return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }

    fun saveUsersCache(users: List<User>) {
        runCatching {
            val arr = org.json.JSONArray()
            users.take(500).forEach { u ->
                arr.put(org.json.JSONObject().put("id", u.id).put("display_name", u.displayName).put("username", u.username).put("avatar_url", u.avatarUrl).put("online", u.online))
            }
            prefs.edit().putString("cache.users", arr.toString()).putLong("cache.users.at", System.currentTimeMillis()).apply()
        }
    }

    fun readUsersCache(): List<User> {
        val raw = prefs.getString("cache.users", null) ?: return emptyList()
        val array = org.json.JSONArray(raw)
        return (0 until array.length()).map { array.getJSONObject(it).toUser() }
    }

    suspend fun storeItems(): List<StoreItem> {
        val array = getArray("store/items")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            StoreItem(o.optString("id"), o.optString("title"), o.optString("description"), o.optLong("price_minor"), o.optString("currency", "UGX"), o.optString("image_url").takeIf { it.isNotBlank() })
        }
    }

    /** Used by the hidden diagnostics screen. Never throws. */
    suspend fun health(): HealthInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(baseUrl() + "/health").get().build()
            val text = client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ApiException("Server responded with HTTP ${response.code}", null, response.code)
                }
                raw
            }
            val body = JSONObject(text.ifBlank { "{}" })
            HealthInfo(
                ok = body.optBoolean("ok"),
                database = body.optString("database", "unknown"),
                detail = body.optString("message", body.optString("service", "HolidayLink server"))
            )
        } catch (e: ApiException) {
            HealthInfo(false, "unavailable", e.message ?: "Server unreachable")
        } catch (e: IOException) {
            Log.w(TAG, "health check failed: ${e.message}")
            HealthInfo(false, "unavailable", "Can't reach HolidayLink. Check your internet and try again.")
        }
    }

    // ---------------------------------------------------------------- me & profile

    suspend fun me(): Me { val m=getJson("me").toMe(); runCatching { ensureDeviceKey() }; return m }

    /**
     * Updates the signed-in user's profile.
     * [avatarDataUrl] is an already-downscaled JPEG data URL; the server stores it as a file
     * and returns the public URL, so no image bytes are written into Firestore.
     * Passing [removeAvatar] clears the picture.
     */
    suspend fun updateProfile(
        displayName: String? = null,
        username: String? = null,
        about: String? = null,
        avatarDataUrl: String? = null,
        removeAvatar: Boolean = false
    ): Me {
        val payload = JSONObject()
        displayName?.let { payload.put("displayName", it) }
        username?.let { payload.put("username", it) }
        about?.let { payload.put("about", it) }
        avatarDataUrl?.let { payload.put("avatarDataUrl", it) }
        if (removeAvatar) payload.put("removeAvatar", true)
        return patchJson("me/profile", payload).toMe()
    }

    suspend fun userProfile(userId: String): Profile =
        getJson("users/${enc(userId)}").toProfile()

    // ---------------------------------------------------------------- people

    suspend fun users(query: String = ""): List<User> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val array = getArray("users?q=$encoded")
        return (0 until array.length()).map { array.getJSONObject(it).toUser() }
    }

    /**
     * Matches hashed device numbers against HolidayLink accounts.
     *
     * The request body contains SHA-256 hashes only. The reply carries the public fields each
     * matched user allows plus [ContactMatch.hash], so the client can tell which of a contact's
     * numbers matched without ever receiving anyone's phone number.
     */
    suspend fun contactMatch(phoneHashes: List<String>): List<ContactMatch> {
        if (phoneHashes.isEmpty()) return emptyList()
        val array = postArray(
            "contacts/match",
            JSONObject().put("phoneHashes", JSONArray(phoneHashes))
        )
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ContactMatch(user = o.toUser(), hash = o.optString("matchedHash"))
        }
    }

    // ---------------------------------------------------------------- block & report

    suspend fun setBlocked(userId: String, blocked: Boolean) {
        val path = "users/${enc(userId)}/block"
        if (blocked) postJson(path, JSONObject()) else deleteJson(path)
    }

    suspend fun blockedUsers(): List<User> {
        val array = getArray("blocked-users")
        return (0 until array.length()).map { array.getJSONObject(it).toUser() }
    }

    suspend fun reportUser(userId: String, reason: String) {
        postJson("users/${enc(userId)}/report", JSONObject().put("reason", reason))
    }

    suspend fun reportMessage(messageId: String, reason: String) {
        postJson("reports", JSONObject().put("messageId", messageId).put("reason", reason))
    }

    private fun decryptChat(chat: ChatItem): ChatItem {
        val plain=chat.lastBody?.let { e2ee.decryptEnvelope(it,userId().orEmpty()) }
        return if(plain!=null) chat.copy(lastBody=plain) else chat
    }

    // ---------------------------------------------------------------- end-to-end encryption

    private suspend fun ensureDeviceKey() {
        if (sessionToken() == null || userId() == null) return
        runCatching { putJson("me/device-key", JSONObject().put("publicKey", e2ee.publicKey())) }
    }

    private suspend fun conversationKeys(conversationId: String): Map<String, String> {
        ensureDeviceKey()
        val array = getArray("conversations/${enc(conversationId)}/keys")
        val out = linkedMapOf<String,String>()
        for (i in 0 until array.length()) {
            val o=array.getJSONObject(i); val key=o.optString("public_key"); val id=o.optString("user_id")
            if (id.isNotBlank() && key.isNotBlank()) out[id]=key
        }
        return out
    }

    // ---------------------------------------------------------------- chat

    suspend fun conversations(): List<ChatItem> {
        return try {
            val array = getArray("conversations")
            prefs.edit().putString("cache.conversations", array.toString()).apply()
            (0 until array.length()).map { i -> decryptChat(array.getJSONObject(i).toChat()) }
        } catch (e: Exception) {
            val raw=prefs.getString("cache.conversations", null)
            if (raw.isNullOrBlank()) throw e
            val array=JSONArray(raw)
            (0 until array.length()).map { i -> decryptChat(array.getJSONObject(i).toChat()) }
        }
    }

    suspend fun messages(conversationId: String, limit: Int = 200): List<Message> {
        val key="cache.messages.$conversationId"
        return try {
            val array = getArray("conversations/${enc(conversationId)}/messages?limit=$limit")
            prefs.edit().putString(key, array.toString()).apply()
            (0 until array.length()).map { i ->
                val raw=array.getJSONObject(i); val m=raw.toMessage()
                val plain=e2ee.decryptEnvelope(m.body, userId().orEmpty())
                if (plain != null) m.copy(body=plain) else m
            }
        } catch (e: Exception) {
            val raw=prefs.getString(key,null) ?: throw e
            val array=JSONArray(raw)
            (0 until array.length()).map { i ->
                val m=array.getJSONObject(i).toMessage(); val plain=e2ee.decryptEnvelope(m.body,userId().orEmpty())
                if(plain!=null) m.copy(body=plain) else m
            }
        }
    }

    suspend fun sendMessage(conversationId: String, body: String, replyToId: String? = null): Message {
        val keys=conversationKeys(conversationId)
        if (keys.isEmpty()) throw ApiException("Secure messaging keys are not available yet. Please try again.")
        val payload = JSONObject().put("body", e2ee.envelope(body, keys)).put("e2ee", true)
        replyToId?.let { payload.put("replyTo", it) }
        val m=postJson("conversations/${enc(conversationId)}/messages", payload).toMessage()
        return m.copy(body=body)
    }

    suspend fun sendAttachment(
        conversationId: String,
        kind: String,
        name: String?,
        dataUrl: String,
        durationMs: Long = 0,
        waveform: String? = null,
        viewOnce: Boolean = false,
        ttlSeconds: Long = 0
    ): Message {
        val payload = JSONObject()
            .put("body", "")
            .put("attachmentKind", kind)
            .put("attachmentName", name ?: "")
            .put("attachmentDataUrl", dataUrl)
        if (durationMs > 0) payload.put("durationMs", durationMs)
        waveform?.let { payload.put("waveform", it) }
        if (viewOnce) payload.put("viewOnce", true)
        if (ttlSeconds > 0) payload.put("ttlSeconds", ttlSeconds)
        return postJson("conversations/${enc(conversationId)}/messages", payload).toMessage()
    }

    suspend fun markMessageViewed(conversationId: String, messageId: String) {
        runCatching { postJson("conversations/${enc(conversationId)}/messages/${enc(messageId)}/viewed", JSONObject()) }
    }

    suspend fun editMessage(conversationId: String, messageId: String, body: String): Message =
        patchJson(
            "conversations/${enc(conversationId)}/messages/${enc(messageId)}",
            JSONObject().put("body", body)
        ).toMessage()

    suspend fun deleteMessage(conversationId: String, messageId: String) {
        deleteJson("conversations/${enc(conversationId)}/messages/${enc(messageId)}")
    }

    suspend fun reactToMessage(conversationId: String, messageId: String, emoji: String): Message =
        postJson(
            "conversations/${enc(conversationId)}/messages/${enc(messageId)}/reactions",
            JSONObject().put("emoji", emoji)
        ).toMessage()

    suspend fun pinMessage(conversationId: String, messageId: String, pinned: Boolean): Message =
        postJson(
            "conversations/${enc(conversationId)}/messages/${enc(messageId)}/pin",
            JSONObject().put("pinned", pinned)
        ).toMessage()

    suspend fun pinnedMessages(conversationId: String): List<Message> {
        val array = getArray("conversations/${enc(conversationId)}/pinned")
        return (0 until array.length()).map { i -> array.getJSONObject(i).toMessage() }
    }

    suspend fun markConversationRead(conversationId: String) {
        postJson("conversations/${enc(conversationId)}/read", JSONObject())
    }

    suspend fun searchMessages(conversationId: String, query: String): List<Message> {
        val array = getArray(
            "conversations/${enc(conversationId)}/messages?search=${enc(query)}"
        )
        return (0 until array.length()).map { i -> array.getJSONObject(i).toMessage() }
    }

    suspend fun startDirect(userId: String): String =
        postJson("conversations/direct", JSONObject().put("userId", userId)).getString("id")

    suspend fun createGroup(title: String, memberIds: List<String>): String =
        postJson(
            "conversations/group",
            JSONObject().put("title", title).put("memberIds", JSONArray(memberIds))
        ).getString("id")

    // ---------------------------------------------------------------- calls & status

    suspend fun callHistory(): List<CallRecord> {
        val array = getArray("calls")
        return (0 until array.length()).map { i -> array.getJSONObject(i).toCall() }
    }

    /** Records a call attempt. Media transport is not implemented yet, so this is a log entry. */
    suspend fun startCall(calleeId: String, kind: String): CallRecord =
        postJson("calls", JSONObject().put("calleeId", calleeId).put("kind", kind)).toCall()

    suspend fun statuses(): List<StatusUpdate> {
        val array = getArray("statuses")
        runCatching { prefs.edit().putString("cache.statuses", array.toString()).apply() }
        return (0 until array.length()).map { i -> array.getJSONObject(i).toStatus() }
    }

    suspend fun createStatus(body: String, mediaDataUrl: String?): StatusUpdate =
        postJson(
            "statuses",
            JSONObject().apply {
                put("body", body)
                mediaDataUrl?.let { put("mediaDataUrl", it) }
            }
        ).toStatus()

    // ---------------------------------------------------------------- notifications

    suspend fun notifications(): List<AppNotification> {
        val array = getArray("notifications")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val data = o.optJSONObject("data")
            AppNotification(
                id = o.getString("id"),
                type = o.optString("type"),
                title = o.optString("title"),
                body = o.optString("body"),
                read = o.optBoolean("read", !o.isNull("read_at")),
                createdAt = str(o, "created_at"),
                conversationId = str(data, "conversationId"),
                senderId = str(data, "senderId")
            )
        }
    }

    suspend fun markNotificationRead(id: String) {
        postJson("notifications/${enc(id)}/read", JSONObject())
    }

    suspend fun markAllNotificationsRead() {
        postJson("notifications/read-all", JSONObject())
    }

    suspend fun notificationSettings(): NotificationSettings =
        getJson("notifications/settings").toNotificationSettings()

    suspend fun updateNotificationSettings(value: NotificationSettings): NotificationSettings =
        patchJson(
            "notifications/settings",
            JSONObject().apply {
                put("messages", value.messages)
                put("calls", value.calls)
                put("status", value.status)
                put("ai", value.ai)
                put("sound", value.sound)
                put("vibration", value.vibration)
                put("preview", value.preview)
            }
        ).toNotificationSettings()

    // ---------------------------------------------------------------- privacy & settings

    suspend fun privacy(): PrivacySettings = getJson("privacy").toPrivacy()

    suspend fun updatePrivacy(value: PrivacySettings): PrivacySettings =
        patchJson(
            "privacy",
            JSONObject().apply {
                put("lastSeen", value.lastSeen.wire)
                put("profilePhoto", value.profilePhoto.wire)
                put("about", value.about.wire)
                put("status", value.status.wire)
                put("readReceipts", value.readReceipts)
                put("typingIndicators", value.typingIndicators)
                put("groupInvites", value.groupInvites.wire)
                put("calls", value.calls.wire)
                put("disappearingSeconds", value.disappearingSeconds)
                put("statusExclude", JSONArray(value.statusExclude))
                put("statusOnly", JSONArray(value.statusOnly))
            }
        ).toPrivacy()

    suspend fun sessions(): List<DeviceSession> {
        val array = getArray("devices")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            DeviceSession(
                id = o.optString("id"),
                label = o.optString("label", "Unknown device"),
                platform = o.optString("platform", "android"),
                createdAt = str(o, "created_at").orEmpty(),
                lastSeenAt = o.optString("last_seen_at").takeIf { it.isNotBlank() },
                current = o.optBoolean("current")
            )
        }
    }

    suspend fun logoutOtherSessions() {
        postJson("devices/logout-others", JSONObject())
    }

    /** One tap to remove every other signed-in device and invalidate their tokens. */
    suspend fun deleteSession(id: String) {
        deleteJson("devices/${enc(id)}")
    }

    // ---------------------------------------------------------------- AI

    /**
     * Sends a prompt to HolidayLink AI.
     * The Gemini credential lives only in the server environment; this app never sees it.
     *
     * [style] is the "Response style" preference. With [save] false the server writes nothing and
     * answers from the [history] the caller already holds, so turning history off really does keep
     * the exchange on this device.
     */
    suspend fun aiChat(
        conversationId: String?,
        prompt: String,
        style: String? = null,
        save: Boolean = true,
        history: List<AiMessage> = emptyList()
    ): AiReply {
        val payload = JSONObject().put("message", prompt).put("save", save)
        conversationId?.let { payload.put("conversationId", it) }
        style?.let { payload.put("style", it) }
        if (!save) {
            payload.put(
                "history",
                JSONArray().apply {
                    history.takeLast(40).forEach { turn ->
                        put(JSONObject().put("role", turn.role).put("text", turn.text))
                    }
                }
            )
        }
        val o = postJson("ai/chat", payload)
        return AiReply(
            conversationId = o.optString("conversationId"),
            messageId = o.optString("messageId"),
            text = o.optString("text")
        )
    }

    suspend fun aiConversations(): List<AiConversation> {
        val array = getArray("ai/conversations")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AiConversation(
                id = o.getString("id"),
                title = o.optString("title", "New chat"),
                createdAt = o.optString("created_at"),
                updatedAt = o.optString("updated_at")
            )
        }
    }

    suspend fun aiMessages(conversationId: String): List<AiMessage> {
        val array = getArray("ai/conversations/${enc(conversationId)}/messages")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AiMessage(
                id = o.getString("id"),
                role = o.optString("role", "user"),
                text = o.optString("text"),
                createdAt = o.optString("created_at"),
                failed = o.optBoolean("failed")
            )
        }
    }

    suspend fun deleteAiConversation(conversationId: String) {
        deleteJson("ai/conversations/${enc(conversationId)}")
    }

    suspend fun clearAiHistory() {
        deleteJson("ai/conversations")
    }

    suspend fun aiBots(): List<AiBot> {
        val array = getArray("ai/bots")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AiBot(
                id = o.optString("id"),
                username = o.optString("username"),
                displayName = str(o, "display_name") ?: "Bot",
                ownerId = str(o, "owner_id").orEmpty(),
                ownerName = str(o, "owner_name"),
                mine = o.optBoolean("mine"),
                createdAt = str(o, "created_at")
            )
        }
    }

    suspend fun createBot(username: String, displayName: String, instructions: String): AiBot {
        val o = postJson(
            "ai/bots",
            JSONObject().put("username", username).put("displayName", displayName).put("instructions", instructions)
        )
        return AiBot(
            id = o.optString("id"),
            username = o.optString("username"),
            displayName = str(o, "display_name") ?: displayName,
            ownerId = str(o, "owner_id").orEmpty(),
            mine = true
        )
    }

    suspend fun deleteBot(botId: String) { deleteJson("ai/bots/${enc(botId)}") }

    /** Instruct the AI to write a message and post it into the chat as your own message. */
    suspend fun aiSend(conversationId: String, prompt: String, botId: String? = null): Message {
        val payload = JSONObject().put("conversationId", conversationId).put("prompt", prompt)
        botId?.takeIf { it.isNotBlank() }?.let { payload.put("botId", it) }
        return postJson("ai/send", payload).optJSONObject("message")?.toMessage()
            ?: throw ApiException("HolidayLink AI couldn't answer just now. Please try again.")
    }

    // ---------------------------------------------------------------- global search

    suspend fun search(query: String): SearchResults {
        if (query.isBlank()) return SearchResults()
        val root = getJson("search?q=${enc(query)}")
        fun <T> parse(key: String, mapper: (JSONObject) -> T): List<T> {
            val arr = root.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { mapper(arr.getJSONObject(it)) }
        }
        return SearchResults(
            chats = parse("chats") { it.toChat() },
            contacts = parse("contacts") { it.toUser() },
            messages = parse("messages") { it.toMessage() },
            users = parse("users") { it.toUser() },
            ai = parse("ai") {
                AiConversation(
                    id = it.optString("id"),
                    title = it.optString("title", "New chat"),
                    createdAt = it.optString("created_at"),
                    updatedAt = it.optString("updated_at")
                )
            }
        ).copy(total = root.optInt("total"))
    }

    // ---------------------------------------------------------------- account

    suspend fun deleteAccount() {
        deleteJson("account")
    }

    // ---------------------------------------------------------------- plumbing

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private suspend fun getArray(path: String): JSONArray = withContext(Dispatchers.IO) {
        val request = authorized(Request.Builder().url(baseUrl() + "/" + path).get()).build()
        JSONArray(execute(request).ifBlank { "[]" })
    }

    private suspend fun postArray(path: String, json: JSONObject): JSONArray = withContext(Dispatchers.IO) {
        val body = json.toString().toRequestBody(jsonMediaType)
        val request = authorized(Request.Builder().url(baseUrl() + "/" + path).post(body)).build()
        JSONArray(execute(request).ifBlank { "[]" })
    }

    private suspend fun putJson(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request=authorized(Request.Builder().url(baseUrl()+"/"+path).put(payload.toString().toRequestBody(jsonMediaType))).build()
        client.newCall(request).execute().use { r -> val raw=r.body?.string().orEmpty(); if(!r.isSuccessful) throw httpError(r.code,raw); JSONObject(raw.ifBlank{"{}"}) }
    }

    private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val request = authorized(Request.Builder().url(baseUrl() + "/" + path).get()).build()
        JSONObject(execute(request).ifBlank { "{}" })
    }

    private suspend fun postJson(path: String, json: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = authorized(Request.Builder().url(baseUrl() + "/" + path).post(body)).build()
            JSONObject(execute(request).ifBlank { "{}" })
        }

    private suspend fun patchJson(path: String, json: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = authorized(Request.Builder().url(baseUrl() + "/" + path).patch(body)).build()
            JSONObject(execute(request).ifBlank { "{}" })
        }

    private suspend fun deleteJson(path: String): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject().toString().toRequestBody(jsonMediaType)
        val request = authorized(Request.Builder().url(baseUrl() + "/" + path).delete(body)).build()
        execute(request)
        Unit
    }

    private fun authorized(builder: Request.Builder): Request.Builder {
        sessionToken()?.let { builder.addHeader("Authorization", "Bearer $it") }
        return builder
    }

    private fun execute(request: Request): String {
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpError(response.code, text)
                text
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw ApiException("The server took too long to respond. Please try again.", "TIMEOUT")
        } catch (e: UnknownHostException) {
            throw ApiException(
                "Can't connect right now. Check your internet connection and try again.",
                "OFFLINE"
            )
        } catch (e: IOException) {
            Log.w(TAG, "network failure: ${e.javaClass.simpleName}: ${e.message}")
            throw ApiException(
                "Can't connect right now. Check your internet connection and try again.",
                "NETWORK"
            )
        }
    }

    private fun httpError(code: Int, body: String): ApiException {
        val errorCode = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
        val serverMessage = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
        val retryAfter = runCatching { JSONObject(body).optInt("retryAfterSeconds") }.getOrNull() ?: 0
        Log.w(TAG, "HTTP $code $errorCode: ${body.take(300)}")

        val friendly = when (errorCode) {
            "INVALID_PHONE" -> "Enter a valid phone number and try again."
            "INVALID_OTP", "OTP_VERIFY_FAILED" ->
                "That code is incorrect or has expired. Check the SMS and try again."
            "OTP_SESSION_EXPIRED" -> "That code has expired. Request a new code."
            "TOO_MANY_ATTEMPTS" -> "Too many incorrect attempts. Request a new code and wait for the SMS."
            "OTP_COOLDOWN" -> serverMessage.ifBlank { "Please wait a moment before requesting another code." }
            "OTP_SEND_FAILED" -> "We couldn't send the code. Please try again."
            "2FA_NOT_ENABLED" -> "2FA password login is not enabled for this number. Use SMS verification."
            "INVALID_2FA_PASSWORD" -> "That 2FA password is incorrect."
            "VIDEO_TOO_LARGE" -> "Videos must be 100 MB or smaller."
            "INVALID_DISPLAY_NAME" -> "Enter a display name (up to 120 characters)."
            "INVALID_USERNAME" -> "Username must be 3-32 characters: letters, numbers or underscore."
            "INVALID_ABOUT" -> "Your About text is too long. Keep it under 140 characters."
            "PHONE_OR_USERNAME_ALREADY_REGISTERED" -> "That phone number or username is already registered."
            "USER_BLOCKED" -> "You can't do that because of your block settings."
            "NOT_MEMBER" -> "You're no longer part of this chat."
            "MESSAGE_NOT_FOUND" -> "That message no longer exists."
            "AI_DISABLED" -> "HolidayLink AI is turned off. Enable it in AI settings."
            "AI_NOT_CONFIGURED" ->
                "HolidayLink AI isn't available right now. Please try again shortly."
            "AI_RATE_LIMITED" ->
                "You're sending messages to HolidayLink AI too quickly. Take a short break."
            "AI_UPSTREAM_ERROR" ->
                "HolidayLink AI couldn't answer just now. Please try again."
            "AI_TIMEOUT" -> "HolidayLink AI took too long to answer. Please try again."
            "UNAUTHORIZED" -> "Your session has expired. Please sign in again."
            "ACCOUNT_BANNED" -> serverMessage.ifBlank { "This account was banned." }
            "DATABASE_UNAVAILABLE", "SERVICE_UNAVAILABLE" ->
                "HolidayLink is temporarily unavailable. Please try again shortly."
            "TIMEOUT" -> "The server took too long to respond. Please try again."
            else -> serverMessage.ifBlank { "Something went wrong. Please try again." }
        }
        return ApiException(friendly, errorCode.ifBlank { null }, code, retryAfter)
    }

    companion object {
        private const val TAG = "HolidayLinkApi"

        /** Shared, app-wide parser helpers. */
        fun str(o: JSONObject, key: String): String? =
            o.optString(key).takeIf { it.isNotBlank() && it != "null" }

        fun JSONObject.toUser(): User = User(
            id = optString("id"),
            displayName = str(this, "display_name") ?: str(this, "displayName") ?: "HolidayLink user",
            username = str(this, "username") ?: "",
            avatarUrl = str(this, "avatar_url") ?: str(this, "avatarUrl"),
            online = optBoolean("online"),
            lastSeen = str(this, "last_seen") ?: str(this, "lastSeen"),
            about = str(this, "about") ?: str(this, "bio")
        )

        fun JSONObject.toMe(): Me = Me(
            id = optString("id"),
            phone = str(this, "phone").orEmpty(),
            email = str(this, "email"),
            displayName = str(this, "display_name") ?: "HolidayLink user",
            username = str(this, "username").orEmpty(),
            avatarUrl = str(this, "avatar_url"),
            about = str(this, "about") ?: str(this, "bio"),
            online = optBoolean("online"),
            lastSeen = str(this, "last_seen"),
            createdAt = str(this, "created_at")
        )

        fun JSONObject.toProfile(): Profile = Profile(
            id = optString("id"),
            displayName = str(this, "display_name") ?: "HolidayLink user",
            username = str(this, "username"),
            avatarUrl = str(this, "avatar_url"),
            about = str(this, "about") ?: str(this, "bio"),
            phone = str(this, "phone"),
            online = if (isNull("online")) null else optBoolean("online"),
            lastSeen = str(this, "last_seen"),
            isBlocked = optBoolean("is_blocked") || optBoolean("isBlocked"),
            blockedByThem = optBoolean("blocked_by_them") || optBoolean("blockedByThem")
        )

        fun JSONObject.toChat(): ChatItem {
            val peerObj = optJSONObject("peer")
            val last = optJSONObject("last_message")
            return ChatItem(
                id = optString("id"),
                kind = optString("kind", "direct"),
                title = str(this, "title"),
                peer = peerObj?.toUser(),
                lastBody = last?.let { l ->
                    str(l, "body") ?: str(l, "attachment_name") ?: str(l, "attachmentName")
                },
                lastAt = last?.let { l ->
                    str(l, "created_at") ?: str(l, "createdAt")
                },
                unreadCount = optInt("unread_count"),
                pinned = optBoolean("pinned"),
                archived = optBoolean("archived"),
                peerTyping = optBoolean("peer_typing") || optBoolean("peerTyping")
            )
        }

        fun JSONObject.toAttachment(): Attachment? {
            val o = optJSONObject("attachment") ?: return null
            return Attachment(
                id = o.optString("id"),
                kind = o.optString("kind", "file"),
                url = str(o, "url"),
                name = str(o, "name"),
                sizeBytes = o.optLong("size_bytes"),
                width = if (o.isNull("width")) null else o.optInt("width"),
                height = if (o.isNull("height")) null else o.optInt("height"),
                durationMs = if (o.isNull("duration_ms")) null else o.optLong("duration_ms"),
                waveform = str(o, "waveform")
            )
        }

        fun JSONObject.toMessage(): Message {
            val reactions = optJSONArray("reactions")
            val reactionList = if (reactions == null) emptyList() else (0 until reactions.length()).map { i ->
                val r = reactions.getJSONObject(i)
                Reaction(
                    emoji = r.optString("emoji", "\uD83D\uDC4D"),
                    count = r.optInt("count", 1),
                    mine = r.optBoolean("mine")
                )
            }
            return Message(
                id = optString("id"),
                senderId = str(this, "sender_id") ?: str(this, "senderId").orEmpty(),
                body = optString("body"),
                createdAt = str(this, "created_at") ?: str(this, "createdAt").orEmpty(),
                messageType = str(this, "message_type") ?: str(this, "messageType") ?: "text",
                replyToId = str(this, "reply_to_id") ?: str(this, "replyToId"),
                replyToBody = str(this, "reply_to_body") ?: str(this, "replyToBody"),
                replyToSenderName = str(this, "reply_to_sender_name") ?: str(this, "replyToSenderName"),
                editedAt = str(this, "edited_at") ?: str(this, "editedAt"),
                reactions = reactionList,
                attachment = toAttachment(),
                status = optString("status", "sent"),
                pinned = optBoolean("pinned"),
                expiresAt = str(this, "expires_at") ?: str(this, "expiresAt"),
                viewOnce = optBoolean("view_once") || optBoolean("viewOnce"),
                conversationId = str(this, "conversation_id") ?: str(this, "conversationId"),
                conversationKind = str(this, "conversation_kind") ?: str(this, "conversationKind"),
                conversationTitle = str(this, "conversation_title") ?: str(this, "conversationTitle"),
                conversationPeerId = str(this, "conversation_peer_id")
                    ?: str(this, "conversationPeerId"),
                conversationPeerName = str(this, "conversation_peer_name")
                    ?: str(this, "conversationPeerName")
            )
        }

        fun JSONObject.toCall(): CallRecord = CallRecord(
            id = optString("id"),
            peer = optJSONObject("peer")?.toUser(),
            kind = optString("kind", "audio"),
            status = optString("status", "ringing"),
            incoming = optBoolean("incoming"),
            createdAt = str(this, "created_at").orEmpty(),
            durationSeconds = optInt("duration_seconds")
        )

        fun JSONObject.toStatus(): StatusUpdate = StatusUpdate(
            id = optString("id"),
            userId = str(this, "user_id").orEmpty(),
            userName = str(this, "user_name") ?: "HolidayLink user",
            avatarUrl = str(this, "avatar_url"),
            kind = optString("kind", "text"),
            body = str(this, "body"),
            mediaUrl = str(this, "media_url"),
            createdAt = optString("created_at"),
            viewerCount = optInt("viewer_count"),
            seenByMe = optBoolean("seen_by_me")
        )

        fun JSONObject.toPrivacy(): PrivacySettings = PrivacySettings(
            lastSeen = Audience.from(str(this, "last_seen")),
            profilePhoto = Audience.from(str(this, "profile_photo")),
            about = Audience.from(str(this, "about")),
            status = StatusAudience.from(str(this, "status")),
            readReceipts = optBoolean("read_receipts", true),
            typingIndicators = optBoolean("typing_indicators", true),
            groupInvites = Audience.from(str(this, "group_invites")),
            calls = Audience.from(str(this, "calls")),
            disappearingSeconds = optLong("disappearing_seconds"),
            statusExclude = strArray("status_exclude"),
            statusOnly = strArray("status_only")
        )

        fun JSONObject.toNotificationSettings(): NotificationSettings = NotificationSettings(
            messages = optBoolean("messages", true),
            calls = optBoolean("calls", true),
            status = optBoolean("status", true),
            ai = optBoolean("ai", true),
            sound = optBoolean("sound", true),
            vibration = optBoolean("vibration", true),
            preview = optBoolean("preview", true)
        )

        private fun JSONObject.strArray(key: String): List<String> {
            val arr = optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }
    }

}
