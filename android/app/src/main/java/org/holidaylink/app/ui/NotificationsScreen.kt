@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.AppNotification
import org.holidaylink.app.net.DeviceCache
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.FirebaseBackend

/**
 * The in-app notification history kept by the server.
 *
 * Tapping an unread row marks it read on the server and opens whatever it refers to, so a
 * notification is never a dead end.
 */
@Composable
fun NotificationListScreen(
    api: Api,
    onBack: () -> Unit,
    onOpenChat: ((String) -> Unit)? = null,
    onOpenAi: (() -> Unit)? = null
) {
    var items by remember { mutableStateOf(DeviceCache.memory<List<AppNotification>>("notifications") ?: emptyList()) }
    var loading by remember { mutableStateOf(items.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    val permission = rememberNotificationPermissionState()
    val scope = rememberCoroutineScope()

    fun markReadAndOpen(notification: AppNotification) {
        if (!notification.read) {
            items = items.map { if (it.id == notification.id) it.copy(read = true) else it }
            scope.launch {
                runCatching {
                    FirebaseBackend.db.collection("notifications").document(notification.id)
                        .update("read", true).await()
                }
            }
        }
        if (notification.type == "ai") {
            onOpenAi?.invoke()
            return
        }
        val conversationId = notification.conversationId
        if (!conversationId.isNullOrBlank() && onOpenChat != null) onOpenChat(conversationId)
    }

    suspend fun loadNotifications(source: com.google.firebase.firestore.Source): List<AppNotification> {
        val uid = FirebaseBackend.uid.orEmpty()
        // Single equality filter only — `whereEqualTo + orderBy` needs a composite index,
        // so ordering happens after the read instead.
        val snap = FirebaseBackend.db.collection("notifications")
            .whereEqualTo("userId", uid)
            .limit(100).get(source).await()
        return snap.documents
            .sortedByDescending { it.safeTimestamp("timestamp")?.seconds ?: 0L }
            .map { d ->
            val data = d.get("data")
            AppNotification(
                id = d.id,
                type = d.getString("type") ?: "message",
                title = d.getString("title") ?: "HolidayLink",
                body = d.getString("message") ?: d.getString("body").orEmpty(),
                read = d.getBoolean("read") == true,
                createdAt = d.safeTimestamp("timestamp")?.toDate()?.toInstant()?.toString(),
                conversationId = when (data) {
                    is Map<*, *> -> data["conversationId"] as? String
                    else -> d.getString("conversationId")
                },
                senderId = when (data) {
                    is Map<*, *> -> data["senderId"] as? String
                    else -> d.getString("senderId")
                }
            )
        }
    }

    LaunchedEffect(reload) {
        if (reload > 0) refreshing = true
        val fresh = DeviceCache.load("notifications", { source -> loadNotifications(source) }) { data ->
            items = data
            error = ""
            loading = false
        }
        if (fresh == null && items.isEmpty()) {
            error = "We couldn't load notifications. Check your internet."
        }
        loading = false
        refreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (items.any { !it.read }) {
                        IconButton(
                            onClick = {
                                items = items.map { it.copy(read = true) }
                                scope.launch {
                                    runCatching {
                                        val batch = FirebaseBackend.db.batch()
                                        items.forEach {
                                            batch.update(
                                                FirebaseBackend.db.collection("notifications").document(it.id),
                                                "read", true
                                            )
                                        }
                                        batch.commit().await()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.DoneAll, "Mark all as read")
                        }
                    }
                    RefreshButton { reload++ }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!permission.granted) {
                NotificationPermissionBanner(permission)
            }

            when {
                loading -> LoadingState(label = "Loading notifications…")
                error.isNotBlank() -> ErrorState(error) { reload++ }
                items.isEmpty() -> EmptyState(
                    icon = Icons.Default.NotificationsNone,
                    title = "Nothing new",
                    subtitle = "Messages, calls and status updates from your contacts appear here."
                )

                else -> PullToRefreshContainer(
                    refreshing = refreshing,
                    onRefresh = { reload++ },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(items, key = { it.id }) { notification ->
                            NotificationRow(notification) { markReadAndOpen(notification) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (notification.read) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            iconFor(notification.type),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                notification.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (notification.body.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    notification.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            friendlyWhen(notification.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun iconFor(type: String): ImageVector = when (type) {
    "call", "missed_call" -> Icons.Filled.Call
    "status" -> Icons.Default.Campaign
    "ai" -> Icons.Default.AutoAwesome
    "receipt" -> Icons.Default.DoneAll
    "group" -> Icons.AutoMirrored.Filled.Chat
    else -> Icons.Default.ChatBubbleOutline
}