package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.net.AiConversation
import org.holidaylink.app.net.AiMessage
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException

/**
 * HolidayLink AI.
 *
 * The app only ever talks to this app's own backend on `POST /ai/chat`. The Gemini credential
 * is held by the server environment, so no key or model name is embedded in the APK.
 *
 * [conversationId] null shows the list of previous AI chats. Choosing "New chat" opens the
 * composer with no id; the server creates the conversation when it stores the first message, so
 * an empty chat never has to exist.
 */
@Composable
fun AiChatScreen(
    api: Api,
    conversationId: String?,
    onOpenConversation: (String) -> Unit,
    onOpenProfile: () -> Unit = {},
    onBack: () -> Unit
) {
    var composingNewChat by remember { mutableStateOf(false) }

    if (conversationId == null && !composingNewChat) {
        AiConversationList(
            api = api,
            onOpenConversation = onOpenConversation,
            onNewConversation = { composingNewChat = true },
            onOpenProfile = onOpenProfile,
            onBack = onBack
        )
        return
    }

    AiConversationView(
        api = api,
        conversationId = conversationId,
        onOpenConversation = { id ->
            composingNewChat = false
            onOpenConversation(id)
        },
        onOpenProfile = onOpenProfile,
        onBack = {
            // Going back from a brand new chat returns to the list instead of leaving AI.
            if (composingNewChat) composingNewChat = false else onBack()
        }
    )
}

@Composable
private fun AiTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}

@Composable
private fun AiGlyph(size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(Brush.linearGradient(HolidayLinkGradient)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
private fun AiConversationList(
    api: Api,
    onOpenConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenProfile: () -> Unit,
    onBack: () -> Unit
) {
    var conversations by remember { mutableStateOf<List<AiConversation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        try {
            conversations = api.aiConversations()
            error = ""
        } catch (e: Exception) {
            error = (e as? ApiException)?.message ?: "We couldn't load your AI chats."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        AiTopBar("HolidayLink AI", onBack) {
            IconButton(onClick = onOpenProfile) {
                Icon(Icons.Default.Person, "AI profile")
            }
            IconButton(onClick = { onNewConversation() }) {
                Icon(Icons.Default.Add, "New AI chat")
            }
        }

        when {
            loading -> LoadingState(label = "Loading…")

            error.isNotBlank() -> ErrorState(error) {
                loading = true
                error = ""
                reloadKey += 1
            }

            conversations.isEmpty() -> EmptyState(
                icon = Icons.Default.AutoAwesome,
                title = "Ask HolidayLink AI",
                subtitle = "Get help drafting messages, summarising long chats and planning " +
                    "your next holiday.",
                action = {
                    TextButton(onClick = { onNewConversation() }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start a chat")
                    }
                }
            )

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                item(key = "new") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onNewConversation() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AiGlyph(40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "New chat",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Start a fresh conversation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(conversations, key = { it.id }) { chat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConversation(chat.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AiGlyph(42.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                chat.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                friendlyWhen(chat.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item(key = "clear") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { confirmClear = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "Clear all AI chats",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all AI chats?") },
            text = { Text("Every conversation you have had with HolidayLink AI will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    conversations = emptyList()
                    scope.launch { runCatching { api.clearAiHistory() } }
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AiConversationView(
    api: Api,
    conversationId: String?,
    onOpenConversation: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onBack: () -> Unit
) {
    var messages by remember { mutableStateOf<List<AiMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(conversationId != null) }
    var error by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("New AI chat") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Settings > HolidayLink AI decides whether turns are stored and how verbose answers are.
    val settings = AppSettings.state
    val keepHistory = settings.saveAiChats
    val style = settings.aiResponseStyle

    suspend fun reload() {
        if (conversationId == null) return
        try {
            messages = api.aiMessages(conversationId)
            error = ""
        } catch (e: Exception) {
            error = (e as? ApiException)?.message ?: "We couldn't load that conversation."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(conversationId) {
        loading = conversationId != null
        reload()
    }

    // Once the server creates the conversation, keep this screen pointed at its real id.
    LaunchedEffect(conversationId) {
        if (conversationId == null) return@LaunchedEffect
        runCatching { api.aiConversations() }
            .getOrNull()
            ?.firstOrNull { it.id == conversationId }
            ?.let { title = it.title }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        AiTopBar(title, onBack) {
            if (sending) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            IconButton(onClick = onOpenProfile) {
                Icon(Icons.Default.Person, "AI profile")
            }
        }

        if (loading) {
            LoadingState(label = "Loading…")
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error.isNotBlank()) {
                    item(key = "error") {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (messages.isEmpty() && error.isBlank()) {
                    item(key = "intro") {
                        EmptyState(
                            icon = Icons.Default.AutoAwesome,
                            title = "Say hello",
                            subtitle = "Ask for help with messages, plans or summaries.",
                            modifier = Modifier.height(320.dp)
                        )
                    }
                }
                items(messages, key = { it.id }) { message ->
                    AiBubble(message)
                }
            }

            if (!keepHistory) {
                Text(
                    "This AI chat is not being saved. Nothing you write here is stored on " +
                        "HolidayLink's servers.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask HolidayLink AI") },
                    maxLines = 4,
                    enabled = !sending
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val prompt = draft.trim()
                        if (prompt.isEmpty() || sending) return@IconButton
                        draft = ""
                        sending = true
                        error = ""
                        val pendingId = "pending-${System.currentTimeMillis()}"
                        messages = messages + AiMessage(
                            id = pendingId,
                            role = "user",
                            text = prompt,
                            createdAt = java.time.Instant.now().toString()
                        )
                        scope.launch {
                            runCatching {
                                api.aiChat(
                                    conversationId = conversationId,
                                    prompt = prompt,
                                    style = style,
                                    save = keepHistory,
                                    // The pending turn is already in `messages`, so it is left
                                    // out of the history the server is given.
                                    history = messages.filter { it.id != pendingId }
                                )
                            }
                                .onSuccess { reply ->
                                    if (keepHistory) {
                                        // The server created a new chat: switch onto its real id.
                                        if (conversationId == null) onOpenConversation(reply.conversationId)
                                        reload()
                                    } else {
                                        // Nothing was stored, so the answer only lives here.
                                        messages = messages + AiMessage(
                                            id = "local-${System.currentTimeMillis()}",
                                            role = "assistant",
                                            text = reply.text,
                                            createdAt = java.time.Instant.now().toString()
                                        )
                                    }
                                }
                                .onFailure { e ->
                                    error = (e as? ApiException)?.message
                                        ?: "HolidayLink AI is unavailable right now."
                                }
                            sending = false
                        }
                    },
                    enabled = draft.isNotBlank() && !sending
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}

@Composable
private fun AiBubble(message: AiMessage) {
    val fromUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (fromUser) 16.dp else 4.dp,
                        bottomEnd = if (fromUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (fromUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (fromUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                friendlyWhen(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (fromUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}