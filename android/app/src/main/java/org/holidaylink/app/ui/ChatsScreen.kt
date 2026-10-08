package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ChatItem

/** Recent conversations with pinned-first ordering, unread badges, mute and preview. */
@Composable
fun ChatsScreen(
    api: Api,
    currentUserId: String,
    onOpenChat: (ChatItem) -> Unit,
    showChrome: Boolean = true
) {
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        if (reload > 0) refreshing = true
        try {
            chats = api.conversations()
            error = ""
        } catch (e: Exception) {
            error = (e as? org.holidaylink.app.net.ApiException)?.message
                ?: "We couldn't load your chats."
        } finally {
            loading = false
            refreshing = false
        }
    }

    // Light polling keeps presence and new messages fresh without a WebSocket client.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            if (error.isBlank()) {
                runCatching { chats = api.conversations() }
            }
        }
    }

    val visible = remember(chats, query) {
        val needle = query.trim().lowercase()
        val filtered = if (needle.isBlank()) chats else chats.filter {
            val title = it.peer?.displayName ?: it.title.orEmpty()
            title.lowercase().contains(needle) || (it.lastBody ?: "").lowercase().contains(needle)
        }
        filtered.sortedWith(
            compareByDescending<ChatItem> { it.pinned }.thenByDescending { it.lastAt.orEmpty() }
        )
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
                RefreshButton { reload++ }
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
            loading -> LoadingState(label = "Loading chats…")
            error.isNotBlank() -> ErrorState(error) { reload++ }
            chats.isEmpty() -> EmptyState(
                icon = Icons.Default.ChatBubbleOutline,
                title = "No chats yet",
                subtitle = "Tap Contacts or the new-chat button to start a conversation."
            )

            else -> PullToRefreshContainer(
                refreshing = refreshing,
                onRefresh = { reload++ },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (query.isNotBlank() && visible.isEmpty()) {
                        item { NoChatsFound(query) }
                    }
                    items(visible, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            currentUserId = currentUserId,
                            muted = AppSettings.isChatMuted(chat.id),
                            onClick = { onOpenChat(chat) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatItem,
    currentUserId: String,
    muted: Boolean,
    onClick: () -> Unit
) {
    val title = chat.peer?.displayName ?: chat.title ?: "Group"
    val unread = chat.unreadCount > 0
    val preview = when {
        chat.peerTyping -> "typing…"
        chat.lastBody.isNullOrBlank() -> "No messages yet"
        else -> chat.lastBody
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
