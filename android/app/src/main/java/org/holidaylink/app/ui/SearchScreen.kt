package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.FirestoreChat
import org.holidaylink.app.net.Message
import org.holidaylink.app.net.StudyBuddy
import org.holidaylink.app.net.User

/** One line of the search result list, flattened so the LazyColumn can render it directly. */
private sealed interface SearchRow {
    val key: String

    data class Header(override val key: String, val title: String, val count: Int) : SearchRow
    data class Person(override val key: String, val user: User) : SearchRow
    data class Conversation(override val key: String, val chat: ChatItem) : SearchRow
    data class Note(override val key: String, val message: Message) : SearchRow
    data class Ai(override val key: String, val title: String) : SearchRow
}

/**
 * Global search across chats, people and messages — all on Firestore,
 * so results never contain anything you could not already open.
 */
@Composable
fun SearchScreen(
    api: Api,
    onBack: () -> Unit,
    onOpenChat: (ChatItem) -> Unit,
    onOpenUser: (User) -> Unit,
    onOpenAiChat: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<User>>(emptyList()) }
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var notes by remember { mutableStateOf<List<Message>>(emptyList()) }
    var aiHits by remember { mutableStateOf<List<String>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    val trimmed = query.trim()
    val longEnough = trimmed.length >= 2

    LaunchedEffect(trimmed) {
        if (trimmed.length < 2) {
            people = emptyList(); chats = emptyList(); notes = emptyList(); aiHits = emptyList()
            searched = false
            searching = false
            error = ""
            return@LaunchedEffect
        }
        searching = true
        // Debounce so typing does not fire reads per keystroke.
        delay(400)
        try {
            val uid = FirebaseBackend.uid.orEmpty()
            val needle = trimmed.lowercase()
            val dir = FirebaseBackend.db.collection("users").limit(300).get().await()
            people = dir.documents.filter { it.id != uid }.mapNotNull { d ->
                val name = d.getString("name").orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val username = d.getString("username").orEmpty()
                if (!name.lowercase().contains(needle) && !username.lowercase().contains(needle)) return@mapNotNull null
                User(
                    id = d.id, displayName = name, username = username,
                    avatarUrl = d.getString("profilePic"), online = d.getBoolean("online") == true
                )
            }.take(20)
            val chatDocs = FirebaseBackend.db.collection("chats").whereArrayContains("users", uid).get().await()
            val peerCache = mutableMapOf<String, User>()
            val mapped = chatDocs.documents.mapNotNull { d ->
                val users = (d.get("users") as? List<String>) ?: emptyList()
                if (!users.contains(uid)) return@mapNotNull null
                val isGroup = d.getBoolean("isGroup") == true
                val kind = when {
                    d.getBoolean("isCommunity") == true -> "community"
                    d.getBoolean("isBroadcast") == true -> "channel"
                    isGroup -> "group"
                    else -> "direct"
                }
                val peer = if (!isGroup) {
                    val pid = users.firstOrNull { it != uid }.orEmpty()
                    peerCache.getOrPut(pid) {
                        runCatching { FirestoreChat.resolvePeer(pid) }.getOrNull()
                            ?: User(pid, "HolidayLink user", "")
                    }
                } else null
                val title = if (isGroup) d.getString("groupName") else peer?.displayName
                ChatItem(
                    id = d.id, kind = kind, title = title, peer = peer,
                    lastBody = d.getString("lastText"),
                    lastAt = d.safeTimestamp("lastAt")?.toDate()?.toInstant()?.toString(),
                    unreadCount = 0, pinned = false, archived = false, peerTyping = false
                )
            }
            chats = mapped.filter {
                val title = it.peer?.displayName?.takeIf { name -> name.isNotBlank() }
                    ?: it.title.orEmpty()
                title.lowercase().contains(needle) || (it.lastBody ?: "").lowercase().contains(needle)
            }.take(20)
            val hits = mutableListOf<Message>()
            for (doc in chatDocs.documents.take(20)) {
                if (hits.size >= 20) break
                val msgs = runCatching {
                    FirebaseBackend.db.collection("chats").document(doc.id).collection("messages")
                        .orderBy("timestamp").limitToLast(50).get().await()
                }.getOrDefault(null) ?: continue
                val isGroup = doc.getBoolean("isGroup") == true
                val peerName = if (!isGroup) {
                    val pid = ((doc.get("users") as? List<String>) ?: emptyList()).firstOrNull { it != uid }
                    peerCache[pid]?.displayName
                } else doc.getString("groupName")
                msgs.documents.forEach { m ->
                    val body = m.getString("text").orEmpty()
                    if (body.lowercase().contains(needle)) {
                        hits += Message(
                            id = m.id,
                            senderId = m.getString("senderId").orEmpty(),
                            body = body,
                            createdAt = m.safeTimestamp("timestamp")?.toDate()?.toInstant()?.toString().orEmpty(),
                            conversationId = doc.id,
                            conversationKind = if (isGroup) "group" else "direct",
                            conversationTitle = doc.getString("groupName"),
                            conversationPeerName = peerName
                        )
                    }
                }
            }
            notes = hits.take(20)
            val history = StudyBuddy.loadHistory(context, uid)
            aiHits = history.filter { it.second.lowercase().contains(needle) }
                .take(5).map { (role, text) -> "${if (role == "user") "You" else "AI"}: ${text.take(80)}" }
            error = ""
            searched = true
        } catch (e: Exception) {
            error = "Search isn't available right now. Check your internet."
        } finally {
            searching = false
        }
    }

    val rows = remember(people, chats, notes, aiHits, searched) {
        val out = mutableListOf<SearchRow>()
        fun <T> addGroup(id: String, title: String, items: List<T>, map: (T) -> SearchRow) {
            if (items.isEmpty()) return
            out += SearchRow.Header("hdr-$id", title, items.size)
            items.forEach { out += map(it) }
        }
        addGroup("people", "People", people) {
            SearchRow.Person("user-${it.id}", it)
        }
        addGroup("chats", "Chats", chats) {
            SearchRow.Conversation("chat-${it.id}", it)
        }
        addGroup("messages", "Messages", notes) {
            SearchRow.Note("msg-${it.id}-${it.conversationId}", it)
        }
        addGroup("ai", "HolidayLink AI", aiHits) {
            SearchRow.Ai("ai-${it.hashCode()}", it)
        }
        out
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(
                "Search",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            label = { Text("Search HolidayLink") },
            placeholder = { Text("Chats, people, messages, AI") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            }
        )

        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            !longEnough -> SearchHint()

            error.isNotBlank() && rows.isEmpty() -> ErrorState(error)

            rows.isEmpty() && !searching -> NoMatches(trimmed)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                if (error.isNotBlank()) {
                    item(key = "error") {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is SearchRow.Header -> SectionHeader(row.title, row.count)
                        is SearchRow.Person -> SearchPersonRow(row.user) { onOpenUser(row.user) }
                        is SearchRow.Conversation -> SearchChatRow(row.chat) { onOpenChat(row.chat) }
                        is SearchRow.Note -> SearchMessageRow(row.message) {
                            row.message.conversationId?.let { id ->
                                val hit = row.message
                                onOpenChat(
                                    ChatItem(
                                        id = id,
                                        kind = hit.conversationKind ?: "direct",
                                        title = hit.conversationTitle,
                                        peer = if (hit.conversationKind == "direct") {
                                            User(
                                                id = hit.conversationPeerId.orEmpty(),
                                                displayName = hit.conversationPeerName?.takeIf { it.isNotBlank() }
                                                    ?: hit.conversationLabel?.takeIf { it.isNotBlank() }
                                                    ?: "Contact",
                                                username = ""
                                            )
                                        } else {
                                            null
                                        },
                                        lastBody = null,
                                        lastAt = null
                                    )
                                )
                            }
                        }
                        is SearchRow.Ai -> SearchAiRow(row.title) { onOpenAiChat("thread") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHint() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(14.dp))
        Text("Search everything", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Type at least two characters to search chats, people, your messages and your " +
                "HolidayLink AI conversations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoMatches(query: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Nothing matches \"$query\"", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Try a shorter word, or check the spelling.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchPersonRow(user: User, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = user.displayName, photo = user.avatarUrl, online = user.online, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                user.username.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "HolidayLink user",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchChatRow(chat: ChatItem, onClick: () -> Unit) {
    val title = chat.peer?.displayName?.takeIf { it.isNotBlank() }
        ?: chat.title?.takeIf { it.isNotBlank() }
        ?: if (chat.kind == "direct") "Contact" else "Group"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = title, photo = chat.peer?.avatarUrl, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                chat.lastBody?.takeIf { it.isNotBlank() } ?: "No messages yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        chat.lastAt?.let {
            Text(
                friendlyWhen(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchMessageRow(message: Message, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                message.body.ifBlank { message.attachment?.name ?: "Attachment" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    message.conversationLabel?.let { append(it).append(" · ") }
                    append(friendlyWhen(message.createdAt))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchAiRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "HolidayLink AI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}