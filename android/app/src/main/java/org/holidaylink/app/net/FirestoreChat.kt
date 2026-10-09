package org.holidaylink.app.net

import com.google.firebase.Timestamp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore chats: same docs the website reads/writes (room.html).
 *
 * Direct: chats/{sortedUidA_uidB} {users:[..], isGroup:false}.
 * Group: chats/{id} {isGroup:true, groupName, members:[..], users:[..]}.
 * Messages: chats/{id}/messages {senderId, text, type, timestamp, read,
 *   imageUrl?, fileUrl?, fileName?, viewOnce?, expiresAt?}.
 * Extra fields the site ignores (lastText, lastAt, unread_<uid>) power
 * ordering and unread badges.
 *
 * NOTE: website parity means message text is plaintext like room.html.
 * The old Postgres E2EE envelope layer has no home in this model.
 */
object FirestoreChat {

    private val db get() = FirebaseBackend.db

    fun myUid(): String = FirebaseBackend.uid.orEmpty()

    // ------------------------------------------------------------------ chats

    data class ChatRow(
        val id: String,
        val isGroup: Boolean,
        val isBroadcast: Boolean,
        val isCommunity: Boolean,
        val title: String,
        val peerId: String?,
        val lastBody: String?,
        val lastAt: Timestamp?,
        val unread: Int
    )

    fun chatsFlow(uid: String): Flow<List<ChatRow>> = callbackFlow {
        val reg = db.collection("chats").whereArrayContains("users", uid)
            .addSnapshotListener { snap, _ ->
                val rows = snap?.documents?.mapNotNull { d ->
                    val users = (d.get("users") as? List<String>) ?: emptyList()
                    if (!users.contains(uid)) return@mapNotNull null
                    val isGroup = d.getBoolean("isGroup") == true
                    val others = users.filter { it != uid }
                    ChatRow(
                        id = d.id,
                        isGroup = isGroup,
                        isBroadcast = d.getBoolean("isBroadcast") == true,
                        isCommunity = d.getBoolean("isCommunity") == true,
                        title = d.getString("groupName").orEmpty(),
                        peerId = if (!isGroup) others.firstOrNull() else null,
                        lastBody = d.getString("lastText"),
                        lastAt = d.getTimestamp("lastAt") ?: d.getTimestamp("updatedAt"),
                        unread = (d.getLong("unread_$uid") ?: 0).toInt()
                    )
                }?.sortedByDescending { it.lastAt?.seconds ?: 0 } ?: emptyList()
                trySend(rows)
            }
        awaitClose { reg.remove() }
    }

    suspend fun resolvePeer(peerId: String): User {
        val p = FirebaseBackend.loadProfile(peerId)
        return User(
            id = peerId,
            displayName = p?.name?.takeIf { it.isNotBlank() } ?: "HolidayLink user",
            username = p?.username.orEmpty(),
            avatarUrl = p?.profilePic,
            online = p?.let { false } == true
        )
    }

    suspend fun startDirect(peerId: String): String {
        val uid = myUid()
        val chatId = listOf(uid, peerId).sorted().joinToString("_")
        db.collection("chats").document(chatId).set(
            mapOf("users" to listOf(uid, peerId), "isGroup" to false)
        ).await()
        return chatId
    }

    suspend fun createGroup(title: String, memberIds: List<String>, broadcast: Boolean = false, community: Boolean = false): String {
        val uid = myUid()
        val users = (memberIds + uid).distinct()
        val ref = db.collection("chats").document()
        ref.set(
            mapOf(
                "users" to users,
                "members" to users,
                "isGroup" to true,
                "isBroadcast" to broadcast,
                "isCommunity" to community,
                "groupName" to title.trim().take(60),
                "createdBy" to uid,
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
        ).await()
        return ref.id
    }

    // ------------------------------------------------------------------ messages

    data class ChatMessage(
        val id: String,
        val senderId: String,
        val text: String,
        val type: String,
        val createdAt: Timestamp?,
        val read: Boolean,
        val imageUrl: String?,
        val fileUrl: String?,
        val fileName: String?,
        val durationMs: Long?,
        val viewOnce: Boolean,
        val expiresAt: Timestamp?,
        val replyToId: String? = null,
        val replyToBody: String? = null,
        val replyToName: String? = null
    )

    fun messagesFlow(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp").limitToLast(200)
            .addSnapshotListener { snap, _ ->
                val rows = snap?.documents?.mapNotNull { d ->
                    ChatMessage(
                        id = d.id,
                        senderId = d.getString("senderId").orEmpty(),
                        text = d.getString("text").orEmpty(),
                        type = d.getString("type") ?: "text",
                        createdAt = d.getTimestamp("timestamp"),
                        read = d.getBoolean("read") == true,
                        imageUrl = d.getString("imageUrl"),
                        fileUrl = d.getString("fileUrl"),
                        fileName = d.getString("fileName"),
                        durationMs = d.getLong("durationMs"),
                        viewOnce = d.getBoolean("viewOnce") == true,
                        expiresAt = d.getTimestamp("expiresAt"),
                        replyToId = d.getString("replyToId"),
                        replyToBody = d.getString("replyToBody"),
                        replyToName = d.getString("replyToName")
                    )
                } ?: emptyList()
                trySend(rows)
            }
        awaitClose { reg.remove() }
    }

    suspend fun send(
        chatId: String,
        text: String,
        imageUrl: String? = null,
        fileUrl: String? = null,
        fileName: String? = null,
        kind: String = "text",
        durationMs: Long? = null,
        viewOnce: Boolean = false,
        ttlSeconds: Long = 0,
        replyToId: String? = null,
        replyToBody: String? = null,
        replyToName: String? = null
    ) {
        val uid = myUid()
        val now = Timestamp.now()
        val data = hashMapOf<String, Any?>(
            "senderId" to uid,
            "text" to text,
            "type" to kind,
            "timestamp" to now,
            "read" to false
        )
        imageUrl?.let { data["imageUrl"] = it }
        fileUrl?.let { data["fileUrl"] = it }
        fileName?.let { data["fileName"] = it }
        durationMs?.let { data["durationMs"] = it }
        replyToId?.let { data["replyToId"] = it }
        replyToBody?.let { data["replyToBody"] = it }
        replyToName?.let { data["replyToName"] = it }
        if (viewOnce) data["viewOnce"] = true
        if (ttlSeconds > 0) {
            data["expiresAt"] = Timestamp(now.seconds + ttlSeconds.coerceIn(5, 7 * 24 * 3600), 0)
        }
        val chatRef = db.collection("chats").document(chatId)
        val preview = when {
            viewOnce -> "View-once photo"
            imageUrl != null -> "Photo"
            fileUrl != null -> fileName ?: "File"
            else -> text.take(80)
        }
        val msgRef = chatRef.collection("messages").document()
        db.runTransaction { tx ->
            val snap = tx.get(chatRef)
            val members = (snap.get("users") as? List<String>) ?: listOf(uid)
            tx.set(msgRef, data)
            val updates = hashMapOf<String, Any?>(
                "lastText" to preview,
                "lastAt" to now,
                "updatedAt" to now
            )
            members.filter { it != uid }.forEach { updates["unread_$it"] = ((snap.getLong("unread_$it") ?: 0) + 1) }
            tx.update(chatRef, updates)
        }.await()
    }

    suspend fun markRead(chatId: String) {
        val uid = myUid()
        val unread = db.collection("chats").document(chatId).collection("messages")
            .whereEqualTo("read", false).get().await()
        val batch = db.batch()
        unread.documents.filter { it.getString("senderId") != uid }.forEach {
            batch.update(it.reference, "read", true)
        }
        batch.update(db.collection("chats").document(chatId), "unread_$uid", 0)
        batch.commit().await()
    }

    /** Delete expired (self-destruct) docs; returns surviving messages. */
    suspend fun purgeExpired(chatId: String) {
        val now = Timestamp.now()
        val snap = db.collection("chats").document(chatId).collection("messages").get().await()
        val batch = db.batch()
        var dirty = false
        snap.documents.forEach { d ->
            val exp = d.getTimestamp("expiresAt")
            if (exp != null && exp.seconds <= now.seconds) {
                batch.delete(d.reference)
                dirty = true
            }
        }
        if (dirty) batch.commit().await()
    }

    /** View-once open: destroy the doc (and its Storage file, best effort). */
    suspend fun consumeViewOnce(chatId: String, msg: ChatMessage, storagePath: String? = null) {
        if (msg.senderId == myUid()) return
        runCatching {
            db.collection("chats").document(chatId).collection("messages").document(msg.id).delete().await()
        }
        storagePath?.let { path ->
            runCatching {
                com.google.firebase.storage.FirebaseStorage.getInstance().getReference(path).delete().await()
            }
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId).delete().await()
    }

    suspend fun editMessage(chatId: String, messageId: String, text: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(mapOf("text" to text, "edited" to true)).await()
    }
}
