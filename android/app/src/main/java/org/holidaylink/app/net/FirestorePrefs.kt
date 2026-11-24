package org.holidaylink.app.net

import kotlinx.coroutines.tasks.await

/**
 * Per-user preferences on Firestore users/{uid} (same doc as the website).
 * The website ignores these extra fields; the app enforces them locally
 * and when rendering other people's data it already holds.
 */
object FirestorePrefs {

    private fun uid(): String = FirebaseBackend.uid.orEmpty()

    suspend fun loadPrivacy(): PrivacySettings {
        val d = FirebaseBackend.db.collection("users").document(uid()).get().await()
        fun aud(key: String) = Audience.from(d.getString(key))
        return PrivacySettings(
            lastSeen = aud("privacy_lastSeen"),
            profilePhoto = aud("privacy_profilePhoto"),
            about = aud("privacy_about"),
            status = StatusAudience.from(d.getString("privacy_status")),
            readReceipts = d.getBoolean("privacy_readReceipts") ?: true,
            typingIndicators = d.getBoolean("privacy_typing") ?: true,
            groupInvites = aud("privacy_groupInvites"),
            calls = aud("privacy_calls"),
            disappearingSeconds = d.getLong("privacy_disappearing") ?: 0L,
            phone = d.getString("privacy_phone") ?: "everyone",
            phoneOnly = (d.get("privacy_phoneOnly") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        )
    }

    suspend fun savePrivacy(s: PrivacySettings): PrivacySettings {
        FirebaseBackend.db.collection("users").document(uid()).update(
            mapOf(
                "privacy_lastSeen" to s.lastSeen.wire,
                "privacy_profilePhoto" to s.profilePhoto.wire,
                "privacy_about" to s.about.wire,
                "privacy_status" to s.status.wire,
                "privacy_readReceipts" to s.readReceipts,
                "privacy_typing" to s.typingIndicators,
                "privacy_groupInvites" to s.groupInvites.wire,
                "privacy_calls" to s.calls.wire,
                "privacy_disappearing" to s.disappearingSeconds,
                "privacy_phone" to s.phone,
                "privacy_phoneOnly" to s.phoneOnly
            )
        ).await()
        return s
    }

    suspend fun loadNotif(): NotificationSettings {
        val d = FirebaseBackend.db.collection("users").document(uid()).get().await()
        @Suppress("UNCHECKED_CAST")
        val m = d.get("notifPrefs") as? Map<String, Boolean> ?: emptyMap()
        return NotificationSettings(
            messages = m["messages"] ?: true,
            calls = m["calls"] ?: true,
            status = m["status"] ?: true,
            ai = m["ai"] ?: true,
            sound = m["sound"] ?: true,
            vibration = m["vibration"] ?: true,
            preview = m["preview"] ?: true
        )
    }

    suspend fun saveNotif(s: NotificationSettings): NotificationSettings {
        FirebaseBackend.db.collection("users").document(uid()).update(
            "notifPrefs", mapOf(
                "messages" to s.messages,
                "calls" to s.calls,
                "status" to s.status,
                "ai" to s.ai,
                "sound" to s.sound,
                "vibration" to s.vibration,
                "preview" to s.preview
            )
        ).await()
        return s
    }
}
