package org.holidaylink.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.FirestoreChat
import org.holidaylink.app.net.User

/** Recent conversations from Firestore (same docs as the website), realtime. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    api: Api,
    currentUserId: String,
    onOpenChat: (ChatItem) -> Unit,
    showChrome: Boolean = true
) {
    val uid = currentUserId.ifBlank { FirestoreChat.myUid() }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<ChatItem?>(null) }
    var archivedOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val peerCache = remember { mutableStateMapOf<String, User>() }

    val rows by remember(uid) { FirestoreChat.chatsFlow(uid) }
        .collectAsState(initial = emptyList())

    // Realtime flow never "loads" via try/catch; surface listener liveness.
    var firstSnapshot by remember { mutableStateOf(false) }
    LaunchedEffect(rows) { if (rows.isNotEmpty() || error.isNotBlank()) firstSnapshot = true }

    // Resolve peer names for direct chats (cached per session).
    LaunchedEffect(rows) {
        rows.mapNotNull { it.peerId }.filter { !peerCache.containsKey(it) }.take(30).forEach { pid ->
            scope.launch {
                runCatching { FirestoreChat.resolvePeer(pid) }.onSuccess { peerCache[pid] = it }
            }
        }
    }

    fun toItem(row: FirestoreChat.ChatRow): ChatItem {
        val peer = row.peerId?.let { peerCache[it] }
        val kind = when {
            row.isCommunity -> "community"
            row.isBroadcast -> "channel"
            row.isGroup -> "group"
            else -> "direct"
        }
        val title = if (row.isGroup || row.isBroadcast || row.isCommunity) {
            row.title.ifBlank { "Group" }
        } else null
        return ChatItem(
            row.id, kind, title, peer,
            row.lastBody, row.lastAt?.toDate()?.toInstant()?.toString(),
            row.unread, AppSettings.isChatPinned(row.id), AppSettings.isChatArchived(row.id), false
        )
    }

    // Recomputed on every recomposition: `peerCache` is a snapshot state map, so as soon as a
    // peer resolves the row updates its title. (Wrapping this in remember(rows, query, peerCache)
    // froze the names because the map instance never changes.)
    val needle = query.trim().lowercase()
    // Pinned chats float to the top; within each section, newest activity comes first.
    // Keep the timestamp from the source row so empty chats naturally sort to the bottom.
    val activityById = rows.associate { row ->
        row.id to (row.lastAt?.toDate()?.time ?: 0L)
    }
    val all = rows.map { toItem(it) }.sortedWith(
        compareByDescending<ChatItem> { it.pinned }
            .thenByDescending { activityById[it.id] ?: 0L }
    )
    val archivedCount = all.count { it.archived }
    val mapped = all.filter { it.archived == archivedOpen }
    val visible = if (needle.isBlank()) mapped else mapped.filter {
        val title = it.peer?.displayName ?: it.title.orEmpty()
        title.lowercase().contains(needle) || (it.lastBody ?: "").lowercase().contains(needle)
    }

    Column(Modifier.fillMaxSize()) {
        if (showChrome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 18.dp, end = 4.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Chats",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                RefreshButton { /* realtime: nothing to do */ }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                placeholder = { Text("Search chats") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, "Clear search")
                        }
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
        }

        when {
            error.isNotBlank() && visible.isEmpty() -> ErrorState(error) { error = "" }
            visible.isEmpty() && archivedCount == 0 -> EmptyState(
                icon = Icons.Default.ChatBubbleOutline,
                title = "No chats yet",
                subtitle = "Tap Contacts or the new-chat button to start a conversation."
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (query.isBlank() && archivedCount > 0) {
                    item(key = "archived-toggle") {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { archivedOpen = !archivedOpen }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                if (archivedOpen) "Back to all chats" else "Archived ($archivedCount)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (archivedOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (query.isNotBlank() && visible.isEmpty()) {
                    item { NoChatsFound(query) }
                }
                items(visible, key = { it.id }) { chat ->
                    ChatRow(
                        chat = chat,
                        currentUserId = uid,
                        muted = AppSettings.isChatMuted(chat.id),
                        onClick = { onOpenChat(chat) },
                        onLongPress = { menuFor = chat }
                    )
                }
            }
        }
    }

    // WhatsApp-style long-press actions: pin, mute, mark as read.
    val open = menuFor
    if (open != null) {
        ModalBottomSheet(
            onDismissRequest = { menuFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp)) {
                val pinned = AppSettings.isChatPinned(open.id)
                val muted = AppSettings.isChatMuted(open.id)
                ChatMenuAction(if (pinned) "Unpin chat" else "Pin chat", Icons.Default.PushPin) {
                    AppSettings.setChatPinned(open.id, !pinned)
                    menuFor = null
                }
                ChatMenuAction(if (muted) "Unmute notifications" else "Mute notifications", if (muted) Icons.Default.VolumeUp else Icons.Default.VolumeOff) {
                    AppSettings.setChatMuted(open.id, !muted)
                    menuFor = null
                }
                ChatMenuAction("Mark as read", Icons.Default.DoneAll) {
                    scope.launch { runCatching { FirestoreChat.markRead(open.id) } }
                    menuFor = null
                }
                ChatMenuAction("Mark as unread", Icons.Default.MarkChatUnread) {
                    scope.launch { runCatching { FirestoreChat.markUnread(open.id) } }
                    menuFor = null
                }
                val archived = AppSettings.isChatArchived(open.id)
                ChatMenuAction(if (archived) "Unarchive chat" else "Archive chat", Icons.Default.Archive) {
                    AppSettings.setChatArchived(open.id, !archived)
                    menuFor = null
                }
                val blockableId = open.peer?.id?.takeIf { it.isNotBlank() }
                if (blockableId != null) {
                    val blocked = AppSettings.isUserBlocked(blockableId)
                    ChatMenuAction(if (blocked) "Unblock contact" else "Block contact", Icons.Default.Block) {
                        AppSettings.setUserBlocked(blockableId, !blocked)
                        menuFor = null
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMenuAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    chat: ChatItem,
    currentUserId: String,
    muted: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    // A 1-1 chat must never borrow the word "Group" while its peer name is still resolving.
    val title = chat.peer?.displayName ?: chat.title ?: if (chat.kind == "direct") "Chat" else "Group"
    val unread = chat.unreadCount > 0
    val preview = when {
        chat.peerTyping -> "typing…"
        chat.lastBody.isNullOrBlank() -> "No messages yet"
        else -> chat.lastBody
    }

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            name = title,
            photo = chat.peer?.avatarUrl,
            online = chat.peer?.online,
            size = 52.dp
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (chat.kind == "group") {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Group,
                        contentDescription = "Group chat",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (chat.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (muted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.VolumeOff,
                        contentDescription = "Muted",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        chat.peerTyping -> MaterialTheme.colorScheme.primary
                        unread -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                chat.lastAt?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        friendlyWhen(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (unread) {
            Spacer(Modifier.width(8.dp))
            CountBadge(chat.unreadCount)
        }
    }
}

@Composable
private fun NoChatsFound(query: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No chats match \"$query\"", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Try another word, or start a new chat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
