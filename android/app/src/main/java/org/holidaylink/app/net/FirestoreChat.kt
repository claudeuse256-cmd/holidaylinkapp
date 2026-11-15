package org.holidaylink.app.net

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
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
        val unread: Int,
        val peerTyping: Boolean = false
    )

    /**
     * How long a typing marker counts as live. The marker is a timestamp, not a flag, so a
     * phone that was closed mid-sentence simply goes stale instead of leaving "typing…"
     * stuck on somebody else's screen forever.
     */
    private const val TYPING_WINDOW_MS = 6_000L

    private fun freshMillis(value: Any?): Long? = when (value) {
        is Timestamp -> value.seconds * 1000L
        is Number -> value.toLong()
        else -> null
    }

    /** True when somebody other than [uid] wrote a typing marker inside the window. */
    private fun someoneTyping(data: Map<String, Any?>, uid: String): Boolean {
        val now = System.currentTimeMillis()
        return data.entries.any { (key, value) ->
            key.startsWith("typing_") && key != "typing_$uid" &&
                (freshMillis(value)?.let { now - it in 0..TYPING_WINDOW_MS } ?: false)
        }
    }

    /**
     * Says "I am typing" in this conversation, or stops saying it.
     *
     * A marker is written at most every few seconds by the caller — one Firestore write per
     * keystroke would be both slow and expensive — and it expires on its own, so a crash or a
     * killed app cannot leave the other person staring at a permanent "typing…".
     */
    suspend fun setTyping(chatId: String, typing: Boolean) {
        val uid = myUid()
        if (uid.isBlank()) return
        val field = "typing_$uid"
        runCatching {
            if (typing) {
                db.collection("chats").document(chatId)
                    .update(field, Timestamp.now()).await()
            } else {
                db.collection("chats").document(chatId)
                    .update(field, FieldValue.delete()).await()
            }
        }
    }

    /** Live "is the other person typing right now" for one open conversation. */
    fun typingFlow(chatId: String): Flow<Boolean> = callbackFlow {
        val uid = myUid()
        val data = java.util.concurrent.atomic.AtomicReference<Map<String, Any?>>(emptyMap())
        val reg = db.collection("chats").document(chatId)
            .addSnapshotListener { snap, _ ->
                data.set(snap?.data.orEmpty())
                trySend(someoneTyping(data.get(), uid))
            }
        // A marker expires by age, not by an event: a phone that died mid-sentence never
        // writes again, so keep re-evaluating while a stale one could still be on screen.
        val ticker = launch {
            while (true) {
                kotlinx.coroutines.delay(2_000)
                trySend(someoneTyping(data.get(), uid))
            }
        }
        awaitClose { ticker.cancel(); reg.remove() }
    }

    fun chatsFlow(uid: String): Flow<List<ChatRow>> = callbackFlow {
        val reg = db.collection("chats").whereArrayContains("users", uid)
            .addSnapshotListener { snap, _ ->
                // Snapshot listeners run on the main thread: a document with an unexpected
                // field type must never be allowed to crash the app, so a bad row is skipped.
                try {
                    val rows = snap?.documents?.mapNotNull { d ->
                        val users = (d.get("users") as? List<String>) ?: emptyList()
                        if (!users.contains(uid)) return@mapNotNull null
                        val isGroup = d.getBoolean("isGroup") == true
                        val others = users.filter { it != uid }
                        val rowData = d.data.orEmpty()
                        ChatRow(
                            id = d.id,
                            isGroup = isGroup,
                            isBroadcast = d.getBoolean("isBroadcast") == true,
                            isCommunity = d.getBoolean("isCommunity") == true,
                            title = d.getString("groupName").orEmpty(),
                            peerId = if (!isGroup) others.firstOrNull() else null,
                            lastBody = d.getString("lastText"),
                            lastAt = d.safeTimestamp("lastAt") ?: d.safeTimestamp("updatedAt"),
                            unread = (d.getLong("unread_$uid") ?: 0).toInt(),
                            peerTyping = someoneTyping(rowData, uid)
                        )
                    }?.sortedWith(
                        // Two chats can share a whole second; the nanoseconds decide the tie
                        // so the list stops reshuffling between snapshots.
                        compareByDescending<ChatRow> { it.lastAt?.seconds ?: 0L }
                            .thenByDescending { it.lastAt?.nanoseconds ?: 0 }
                    ) ?: emptyList()
                    trySend(rows)
                } catch (_: Exception) {
                    // Keep the previous list on screen rather than crashing.
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun resolvePeer(peerId: String): User {
        val doc = db.collection("users").document(peerId).get().await()
        return userFrom(peerId, doc.data.orEmpty())
    }

    /**
     * Several peers in a handful of reads instead of one document each. `whereIn` on the
     * document id has a cap, so the ids go out in chunks. The chat list uses this to refresh
     * every visible name, photo and presence dot at once, rather than holding the copy it
     * fetched at launch for the rest of the session.
     */
    suspend fun resolvePeers(ids: List<String>): Map<String, User> {
        val wanted = ids.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return emptyMap()
        val out = HashMap<String, User>()
        wanted.chunked(10).forEach { chunk ->
            runCatching {
                db.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
            }.getOrNull()?.documents?.forEach { d ->
                out[d.id] = userFrom(d.id, d.data.orEmpty())
            }
        }
        return out
    }

    /**
     * The one place a users/{uid} document becomes a [User], so presence and the privacy
     * switches are read the same way everywhere.
     */
    private fun userFrom(peerId: String, data: Map<String, Any?>): User {
        val username = (data["username"] as? String).orEmpty()
        val name = ((data["name"] as? String) ?: (data["displayName"] as? String)).orEmpty()
            .takeIf { it.isNotBlank() }
            // A profile with no name must never render an empty row: fall back to the
            // handle, then to the neutral placeholder.
            ?: username.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: "HolidayLink user"
        val avatar = ((data["profilePic"] as? String) ?: (data["avatarUrl"] as? String))
            ?.takeIf { it.isNotBlank() }
        val seenMillis = FirebaseBackend.docLastSeenMillis(data)
        return User(
            id = peerId,
            displayName = name,
            username = username,
            avatarUrl = avatar,
            online = FirebaseBackend.isTrulyOnline(data["online"] == true, seenMillis),
            lastSeen = seenMillis?.let { java.time.Instant.ofEpochMilli(it).toString() },
            readReceipts = data["privacy_readReceipts"] as? Boolean ?: true,
            typingIndicators = data["privacy_typing"] as? Boolean ?: true
        )
    }

    suspend fun startDirect(peerId: String): String {
        val uid = myUid()
        val chatId = directId(uid, peerId)
        db.collection("chats").document(chatId).set(
            mapOf("users" to listOf(uid, peerId), "isGroup" to false),
            SetOptions.merge()
        ).await()
        return chatId
    }

    /** The one-to-one conversation id is derived from both uids, so it needs no lookup. */
    fun directId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

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
        val replyToName: String? = null,
        val audioBlob: String? = null,
        val edited: Boolean = false,
        /** Set by the recipient's device when the message reaches it (double grey tick). */
        val delivered: Boolean = false,
        /** emoji -> uids who reacted. Empty map means no reactions. */
        val reactions: Map<String, List<String>> = emptyMap(),
        /** True once the sender deleted it for everyone: the bubble becomes a placeholder. */
        val deleted: Boolean = false,
        /** Only set on `type = "call"` rows, which are the in-chat call log. */
        val callKind: String? = null,
        val callStatus: String? = null,
        val callSeconds: Long? = null
    )

    fun messagesFlow(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp").limitToLast(200)
            .addSnapshotListener { snap, _ ->
                try {
                    val rows = snap?.documents?.mapNotNull { d ->
                        ChatMessage(
                            id = d.id,
                            senderId = d.getString("senderId").orEmpty(),
                            text = d.getString("text").orEmpty(),
                            type = d.getString("type") ?: "text",
                            createdAt = d.safeTimestamp("timestamp"),
                            read = d.getBoolean("read") == true,
                            imageUrl = d.getString("imageUrl"),
                            fileUrl = d.getString("fileUrl"),
                            fileName = d.getString("fileName"),
                            durationMs = d.getLong("durationMs"),
                            viewOnce = d.getBoolean("viewOnce") == true,
                            expiresAt = d.safeTimestamp("expiresAt"),
                            replyToId = d.getString("replyToId"),
                            replyToBody = d.getString("replyToBody"),
                            replyToName = d.getString("replyToName"),
                            audioBlob = d.getString("audioBlob"),
                            edited = d.getBoolean("edited") == true,
                            delivered = d.getBoolean("delivered") == true,
                            reactions = parseReactions(d.get("reactions")),
                            deleted = d.getBoolean("deleted") == true,
                            callKind = d.getString("callKind"),
                            callStatus = d.getString("callStatus"),
                            callSeconds = d.getLong("callSeconds")
                        )
                    } ?: emptyList()
                    trySend(rows)
                } catch (_: Exception) {
                    // A single malformed message must not close the whole conversation.
                }
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
        replyToName: String? = null,
        audioBlob: String? = null
    ): String {
        val uid = myUid()
        val now = Timestamp.now()
        val data = hashMapOf<String, Any?>(
            "senderId" to uid,
            "text" to text,
            "type" to kind,
            "timestamp" to now,
            "read" to false,
            "delivered" to false
        )
        imageUrl?.let { data["imageUrl"] = it }
        fileUrl?.let { data["fileUrl"] = it }
        fileName?.let { data["fileName"] = it }
        durationMs?.let { data["durationMs"] = it }
        replyToId?.let { data["replyToId"] = it }
        replyToBody?.let { data["replyToBody"] = it }
        replyToName?.let { data["replyToName"] = it }
        // Voice blobs live in Firestore (no Storage): 60s cap keeps docs < 1 MiB.
        audioBlob?.let { data["audioBlob"] = it }
        if (viewOnce) data["viewOnce"] = true
        if (ttlSeconds > 0) {
            data["expiresAt"] = Timestamp(now.seconds + ttlSeconds.coerceIn(5, 7 * 24 * 3600), 0)
        }
        val chatRef = db.collection("chats").document(chatId)
        val preview = when {
            kind == "sticker" -> "Sticker"
            viewOnce -> "View-once photo"
            !audioBlob.isNullOrBlank() -> "Voice message"
            imageUrl != null -> "Photo"
            fileUrl != null -> if (kind == "video") "Video" else fileName ?: "File"
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

        // Nudge the other side's phones so the alert lands even if their app is closed.
        // Best effort only: on failure the message is already in Firestore for them.
        runCatching { PushNotify.message(chatId, preview, msgRef.id) }
        // The id lets the sender match its optimistic bubble to the stored one.
        return msgRef.id
    }

    suspend fun markRead(chatId: String, sendReceipts: Boolean = true) {
        val uid = myUid()
        val unread = db.collection("chats").document(chatId).collection("messages")
            .whereEqualTo("read", false).get().await()
        val batch = db.batch()
        // Clearing the local unread badge is always safe. Telling the sender that the
        // message was read is a receipt, and receipts are a privacy choice: with them off
        // the other side must keep seeing the single tick even though this phone opened
        // the conversation.
        if (sendReceipts) {
            unread.documents.filter { it.getString("senderId") != uid }.forEach {
                batch.update(it.reference, "read", true)
            }
        }
        batch.update(db.collection("chats").document(chatId), "unread_$uid", 0)
        batch.commit().await()
    }

    /** Flips the chat back to "unread" so it shows a badge, WhatsApp-style. */
    suspend fun markUnread(chatId: String) {
        val uid = myUid()
        if (uid.isBlank()) return
        db.collection("chats").document(chatId).update("unread_$uid", 1).await()
    }

    /**
     * The recipient's device received these messages: flip them from single to double grey tick.
     * Only flicks messages the current user did not send.
     */
    suspend fun markDelivered(chatId: String) {
        val uid = myUid()
        val undelivered = db.collection("chats").document(chatId).collection("messages")
            .whereEqualTo("delivered", false).get().await()
        val batch = db.batch()
        var dirty = false
        undelivered.documents.filter { it.getString("senderId") != uid }.forEach {
            batch.update(it.reference, "delivered", true)
            dirty = true
        }
        if (dirty) batch.commit().await()
    }

    /** Delete expired (self-destruct) docs; returns surviving messages. */
    suspend fun purgeExpired(chatId: String) {
        val now = Timestamp.now()
        val snap = db.collection("chats").document(chatId).collection("messages").get().await()
        val batch = db.batch()
        var dirty = false
        snap.documents.forEach { d ->
            val exp = d.safeTimestamp("expiresAt")
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

    /**
     * "Delete for everyone": the row survives with its content stripped so both phones can
     * render "This message was deleted" instead of silently losing the place in the thread.
     */
    suspend fun markDeleted(chatId: String, messageId: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(
                mapOf(
                    "deleted" to true,
                    "text" to "",
                    "imageUrl" to null,
                    "fileUrl" to null,
                    "audioBlob" to null,
                    "reactions" to null
                )
            ).await()
    }

    /**
     * Writes the call into the conversation itself, so a finished, missed or declined call
     * shows up in the chat the same way it does in WhatsApp. Only the caller logs it: both
     * phones would otherwise write a duplicate row.
     */
    suspend fun logCall(peerId: String, kind: String, status: String, seconds: Long) {
        val uid = myUid()
        if (uid.isBlank() || peerId.isBlank() || peerId == uid) return
        val chatId = directId(uid, peerId)
        val chatRef = db.collection("chats").document(chatId)
        val now = Timestamp.now()
        val preview = when (status) {
            "missed" -> if (kind == "video") "Missed video call" else "Missed voice call"
            "declined" -> if (kind == "video") "Declined video call" else "Declined voice call"
            else -> if (kind == "video") "Video call" else "Voice call"
        }
        runCatching {
            chatRef.set(mapOf("users" to listOf(uid, peerId), "isGroup" to false), SetOptions.merge()).await()
            chatRef.collection("messages").document().set(
                mapOf(
                    "senderId" to uid,
                    "text" to preview,
                    "type" to "call",
                    "timestamp" to now,
                    "read" to true,
                    "delivered" to true,
                    "callKind" to kind,
                    "callStatus" to status,
                    "callSeconds" to seconds.coerceAtLeast(0)
                )
            ).await()
            chatRef.update(mapOf("lastText" to preview, "lastAt" to now, "updatedAt" to now)).await()
        }
    }

    suspend fun editMessage(chatId: String, messageId: String, text: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(mapOf("text" to text, "edited" to true)).await()
    }

    /**
     * Adds or removes the current user's emoji reaction. Stored as
     * `reactions.<emoji> = [uid, ...]` so the website can read the same shape.
     */
    suspend fun react(chatId: String, messageId: String, emoji: String, add: Boolean) {
        val uid = myUid()
        if (uid.isBlank() || emoji.isBlank()) return
        val ref = db.collection("chats").document(chatId).collection("messages").document(messageId)
        val value = if (add) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
        ref.update("reactions.$emoji", value).await()
    }

    /** Safely reads the nested `reactions` field into a clean Map<emoji, List<uid>>. */
    private fun parseReactions(raw: Any?): Map<String, List<String>> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        val out = LinkedHashMap<String, List<String>>()
        map.forEach { (k, v) ->
            val emoji = k as? String ?: return@forEach
            val uids = (v as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            if (uids.isNotEmpty()) out[emoji] = uids
        }
        return out
    }
}
