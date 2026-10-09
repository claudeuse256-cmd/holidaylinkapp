package org.holidaylink.app.net

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Firebase backend: same Firestore + Auth the website uses.
 *
 * Login: Render OTP service (/otp/request + /otp/verify over Infobip SMS)
 * returns a Firebase custom token; this signs in with Firebase Auth.
 * Profile, 2FA (twoFactorRequests + sessions, like security-shared.js) and
 * everything else live in Firestore — no app content touches Render.
 */
object FirebaseBackend {

    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid

    suspend fun signInWithCustomToken(token: String): FirebaseUser {
        return auth.signInWithCustomToken(token).await().user
            ?: throw ApiException("Sign-in failed. Please try again.")
    }

    fun signOut() = auth.signOut()

    /** Website users doc shape (login.html / signup.html). */
    data class FirebaseProfile(
        val name: String = "",
        val username: String = "",
        val phone: String = "",
        val email: String = "",
        val bio: String = "",
        val profilePic: String? = null,
        val banned: Boolean = false,
        val banReason: String? = null,
        val disabled: Boolean = false,
        val activated: Boolean = false,
        val twoFactorEnabled: Boolean = false
    )

    suspend fun loadProfile(uid: String): FirebaseProfile? {
        val snap = db.collection("users").document(uid).get().await()
        if (!snap.exists()) return null
        return FirebaseProfile(
            name = snap.getString("name").orEmpty(),
            username = snap.getString("username").orEmpty(),
            phone = snap.getString("phone").orEmpty(),
            email = snap.getString("email").orEmpty(),
            bio = snap.getString("bio").orEmpty(),
            profilePic = snap.getString("profilePic"),
            banned = snap.getBoolean("banned") == true,
            banReason = snap.getString("banReason"),
            disabled = snap.getBoolean("disabled") == true,
            activated = snap.getBoolean("activated") == true,
            twoFactorEnabled = snap.getBoolean("twoFactorEnabled") == true
        )
    }

    suspend fun saveProfile(uid: String, name: String, username: String, bio: String) {
        db.collection("users").document(uid).update(
            mapOf("name" to name, "username" to username.lowercase(), "bio" to bio)
        ).await()
    }

    suspend fun isUsernameTaken(username: String, exceptUid: String): Boolean {
        val found = db.collection("users")
            .whereEqualTo("username", username.lowercase())
            .limit(2).get().await()
        return found.documents.any { it.id != exceptUid }
    }

    fun deviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }

    fun deviceLabel(): String = android.os.Build.MODEL ?: "Android device"

    /** Heartbeat: marks this user online with a fresh timestamp. */
    suspend fun touchPresence() {
        val uid = uid ?: return
        runCatching {
            db.collection("users").document(uid).update(
                mapOf("online" to true, "lastSeen" to com.google.firebase.Timestamp.now())
            ).await()
        }
    }

    /** Best-effort offline mark for logout. */
    suspend fun goOffline() {
        val uid = uid ?: return
        runCatching {
            db.collection("users").document(uid).update(
                mapOf("online" to false, "lastSeen" to com.google.firebase.Timestamp.now())
            ).await()
        }
    }

    /** True only when the online flag is fresh (5 min). Stale flags lie. */
    fun isTrulyOnline(online: Boolean, lastSeenMillis: Long?): Boolean {
        if (!online) return false
        if (lastSeenMillis == null) return false
        return System.currentTimeMillis() - lastSeenMillis < 5 * 60 * 1000L
    }

    /** Reads lastSeen whether stored as Timestamp, millis Long, or ISO string. */
    fun docLastSeenMillis(data: Map<String, Any?>): Long? {
        (data["lastSeen"] as? com.google.firebase.Timestamp)?.let { return it.seconds * 1000L }
        (data["lastSeen"] as? Number)?.let { return it.toLong() }
        (data["lastSeen"] as? String)?.let {
            return runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
        }
        (data["last_seen"] as? com.google.firebase.Timestamp)?.let { return it.seconds * 1000L }
        return null
    }

    sealed interface TwoFactorGate {
        data object Allowed : TwoFactorGate
        data class Waiting(val requestId: String) : TwoFactorGate
    }

    /**
     * Website 2FA parity (security-shared.js): if users/{uid}.twoFactorEnabled
     * and this device has no sessions/{deviceId} doc while other sessions
     * exist, file a pending twoFactorRequests doc for approval on another device.
     */
    suspend fun checkTwoFactor(context: Context, uid: String): TwoFactorGate {
        val profile = loadProfile(uid) ?: return TwoFactorGate.Allowed
        if (!profile.twoFactorEnabled) {
            touchSession(context, uid)
            return TwoFactorGate.Allowed
        }
        val did = deviceId(context)
        val mine = db.collection("sessions").document(did).get().await()
        if (mine.exists()) {
            touchSession(context, uid)
            return TwoFactorGate.Allowed
        }
        val others = db.collection("sessions").whereEqualTo("userId", uid).limit(1).get().await()
        if (others.isEmpty) {
            // No other device to approve from: this becomes the trusted device.
            touchSession(context, uid)
            return TwoFactorGate.Allowed
        }
        val req = hashMapOf(
            "userId" to uid,
            "requestingDeviceId" to did,
            "requestingDeviceLabel" to deviceLabel(),
            "status" to "pending",
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        val ref = db.collection("twoFactorRequests").add(req).await()
        return TwoFactorGate.Waiting(ref.id)
    }

    suspend fun touchSession(context: Context, uid: String) {
        val did = deviceId(context)
        db.collection("sessions").document(did).set(
            mapOf(
                "userId" to uid,
                "deviceId" to did,
                "deviceLabel" to deviceLabel(),
                "lastActiveAt" to com.google.firebase.Timestamp.now(),
                "createdAt" to com.google.firebase.Timestamp.now()
            )
        ).await()
    }

    /** Calls back with true=approved, false=denied. Returns a remover. */
    fun watchTwoFactorRequest(requestId: String, onResult: (Boolean) -> Unit): ListenerRegistration {
        return db.collection("twoFactorRequests").document(requestId)
            .addSnapshotListener { snap, _ ->
                val status = snap?.getString("status") ?: return@addSnapshotListener
                if (status == "approved") onResult(true)
                else if (status == "denied") onResult(false)
            }
    }

    suspend fun cancelTwoFactorRequest(requestId: String) {
        runCatching { db.collection("twoFactorRequests").document(requestId).delete().await() }
    }
}
