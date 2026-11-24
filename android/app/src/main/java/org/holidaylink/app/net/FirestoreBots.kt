package org.holidaylink.app.net

import kotlinx.coroutines.tasks.await

/**
 * User-created AI bots (Telegram-style) on Firestore collection `aiBots`:
 * {ownerId, username, displayName, instructions, createdAt}.
 * Mentionable as @username in any chat via the Study Buddy mention bar.
 */
object FirestoreBots {

    data class StudyBot(
        val id: String,
        val username: String,
        val displayName: String,
        val instructions: String,
        val ownerId: String,
        val ownerName: String? = null,
        val mine: Boolean = false
    )

    private val db get() = FirebaseBackend.db

    suspend fun list(): List<StudyBot> {
        val uid = FirebaseBackend.uid.orEmpty()
        val snap = db.collection("aiBots").orderBy("createdAt").limit(200).get().await()
        val owners = mutableMapOf<String, String>()
        return snap.documents.mapNotNull { d ->
            val ownerId = d.getString("ownerId").orEmpty()
            val ownerName = owners.getOrPut(ownerId) {
                runCatching { FirebaseBackend.loadProfile(ownerId)?.name.orEmpty() }.getOrDefault("")
            }
            StudyBot(
                id = d.id,
                username = d.getString("username").orEmpty(),
                displayName = d.getString("displayName") ?: "Bot",
                instructions = d.getString("instructions").orEmpty(),
                ownerId = ownerId,
                ownerName = ownerName.takeIf { it.isNotBlank() },
                mine = ownerId == uid
            )
        }
    }

    suspend fun create(username: String, displayName: String, instructions: String): StudyBot {
        val uid = FirebaseBackend.uid ?: throw ApiException("Sign in again first.")
        val clean = username.trim().lowercase()
        if (!clean.matches(Regex("^[a-z0-9_]{3,32}$"))) throw ApiException("Bot username must be 3-32 characters.")
        if (displayName.trim().isEmpty()) throw ApiException("Give the bot a display name.")
        val clashUsers = db.collection("users").whereEqualTo("username", clean).limit(1).get().await()
        val clashBots = db.collection("aiBots").whereEqualTo("username", clean).limit(1).get().await()
        if (!clashUsers.isEmpty || !clashBots.isEmpty) throw ApiException("That @name is already taken.")
        val ref = db.collection("aiBots").add(
            mapOf(
                "ownerId" to uid,
                "username" to clean,
                "displayName" to displayName.trim().take(120),
                "instructions" to instructions.trim().take(2000),
                "createdAt" to com.google.firebase.Timestamp.now()
            )
        ).await()
        return StudyBot(ref.id, clean, displayName.trim().take(120), instructions.trim().take(2000), uid, null, true)
    }

    suspend fun delete(bot: StudyBot) {
        val uid = FirebaseBackend.uid ?: throw ApiException("Sign in again first.")
        if (bot.ownerId != uid) throw ApiException("Only the bot owner can delete it.")
        db.collection("aiBots").document(bot.id).delete().await()
    }
}
