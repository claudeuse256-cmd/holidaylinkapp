package org.holidaylink.app.net

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Group / channel / community management over the same `chats/{id}` documents the website
 * reads. Admin ids live in `admins`, visibility in `privacy`/`visibility`, and the
 * WhatsApp-like per-group permissions in `settings.<key>`. Every field is optional so groups
 * created before this existed keep working.
 */
object FirestoreGroups {

    data class GroupInfo(
        val id: String,
        val name: String,
        val description: String,
        val photo: String?,
        val members: List<String>,
        val admins: List<String>,
        val createdBy: String,
        val privacy: String,
        val isBroadcast: Boolean,
        val isCommunity: Boolean,
        val sendMessagesAdminsOnly: Boolean,
        val editInfoAdminsOnly: Boolean,
        val approveNewMembers: Boolean
    ) {
        fun isAdmin(uid: String): Boolean = admins.contains(uid) || createdBy == uid
    }

    fun groupFlow(chatId: String): Flow<GroupInfo?> = callbackFlow {
        val reg = FirebaseBackend.db.collection("chats").document(chatId)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(read(snap.id, snap.data.orEmpty()))
            }
        awaitClose { reg.remove() }
    }

    suspend fun groupInfo(chatId: String): GroupInfo? = runCatching {
        val snap = FirebaseBackend.db.collection("chats").document(chatId).get().await()
        if (!snap.exists()) null else read(snap.id, snap.data.orEmpty())
    }.getOrNull()

    /** Resolves a batch of member ids to profiles, keeping the given order. */
    suspend fun resolveUsers(ids: List<String>): List<User> = ids.map { id ->
        runCatching { FirestoreChat.resolvePeer(id) }.getOrNull()
            ?: User(id, "HolidayLink user", "")
    }

    private fun read(id: String, d: Map<String, Any?>): GroupInfo {
        val users = (d["users"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val members = (d["members"] as? List<*>)?.mapNotNull { it as? String }.orEmpty().ifEmpty { users }
        val createdBy = (d["createdBy"] as? String).orEmpty()
        val admins = (d["admins"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            .ifEmpty { if (createdBy.isNotBlank()) listOf(createdBy) else emptyList() }
        @Suppress("UNCHECKED_CAST")
        val settings = d["settings"] as? Map<String, Any?> ?: emptyMap()
        return GroupInfo(
            id = id,
            name = (d["groupName"] as? String ?: d["name"] as? String).orEmpty(),
            description = (d["description"] as? String ?: d["groupDescription"] as? String).orEmpty(),
            photo = (d["groupPic"] as? String ?: d["groupPhoto"] as? String ?: d["photoURL"] as? String)
                ?.takeIf { it.isNotBlank() },
            members = members.distinct(),
            admins = admins.distinct(),
            createdBy = createdBy,
            privacy = (d["privacy"] as? String ?: d["visibility"] as? String)?.lowercase() ?: "private",
            isBroadcast = d["isBroadcast"] == true,
            isCommunity = d["isCommunity"] == true,
            sendMessagesAdminsOnly = settings["sendMessagesAdminsOnly"] == true,
            editInfoAdminsOnly = settings["editInfoAdminsOnly"] == true,
            approveNewMembers = settings["approveNewMembers"] == true
        )
    }

    private suspend fun patch(chatId: String, fields: Map<String, Any?>) {
        val doc = HashMap<String, Any?>(fields)
        doc["updatedAt"] = Timestamp.now()
        FirebaseBackend.db.collection("chats").document(chatId).update(doc).await()
    }

    suspend fun setName(chatId: String, name: String) =
        patch(chatId, mapOf("groupName" to name.trim().take(60)))

    suspend fun setDescription(chatId: String, text: String) {
        val clean = text.trim().take(500)
        patch(chatId, mapOf("description" to clean, "groupDescription" to clean))
    }

    suspend fun setPhoto(chatId: String, url: String) =
        patch(chatId, mapOf("groupPic" to url, "groupPhoto" to url))

    suspend fun setPrivacy(chatId: String, privacy: String) =
        patch(chatId, mapOf("privacy" to privacy, "visibility" to privacy))

    suspend fun setRule(chatId: String, key: String, value: Boolean) =
        patch(chatId, mapOf("settings.$key" to value))

    suspend fun addMembers(chatId: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val arr = ids.toTypedArray()
        FirebaseBackend.db.collection("chats").document(chatId).update(
            mapOf(
                "users" to FieldValue.arrayUnion(*arr),
                "members" to FieldValue.arrayUnion(*arr),
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    suspend fun removeMember(chatId: String, id: String) {
        FirebaseBackend.db.collection("chats").document(chatId).update(
            mapOf(
                "users" to FieldValue.arrayRemove(id),
                "members" to FieldValue.arrayRemove(id),
                "admins" to FieldValue.arrayRemove(id),
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    suspend fun setAdmin(chatId: String, id: String, admin: Boolean) {
        val value = if (admin) FieldValue.arrayUnion(id) else FieldValue.arrayRemove(id)
        patch(chatId, mapOf("admins" to value))
    }

    /** Leaves a group, handing admin to the next member when we are the last admin. */
    suspend fun leaveGroup(chatId: String) {
        val uid = FirestoreChat.myUid()
        if (uid.isBlank()) return
        val info = groupInfo(chatId)
        if (info != null && info.admins == listOf(uid)) {
            info.members.firstOrNull { it != uid }?.let { setAdmin(chatId, it, true) }
        }
        removeMember(chatId, uid)
    }

    suspend fun joinGroup(chatId: String) {
        val uid = FirestoreChat.myUid()
        if (uid.isBlank()) return
        addMembers(chatId, listOf(uid))
    }

    /** Public groups other people can find and join. */
    suspend fun publicGroups(): List<GroupInfo> = runCatching {
        // Single equality keeps this working without a composite index.
        val snap = FirebaseBackend.db.collection("chats")
            .whereEqualTo("privacy", "public")
            .limit(100).get().await()
        snap.documents.filter { it.getBoolean("isGroup") == true }
            .map { read(it.id, it.data.orEmpty()) }
    }.getOrDefault(emptyList())

    /** Admins deleting a group: drop its messages and the chat document. */
    suspend fun deleteGroup(chatId: String) {
        val db = FirebaseBackend.db
        runCatching {
            val msgs = db.collection("chats").document(chatId).collection("messages").limit(400).get().await()
            val batch = db.batch()
            msgs.documents.forEach { batch.delete(it.reference) }
            batch.delete(db.collection("chats").document(chatId))
            batch.commit().await()
        }
    }
}
