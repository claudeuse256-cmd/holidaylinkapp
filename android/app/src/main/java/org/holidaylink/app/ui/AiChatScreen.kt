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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.StudyBuddy

/**
 * HolidayLink AI chat (Study Buddy, same brain as the website).
 *
 * Talks to Gemini directly with the admin-tuned config (aiSettings /
 * aiPrompt / aiCommands), keeps history on this device like the site's
 * localStorage, and can send messages as the user plus jump to app pages.
 */
@Composable
fun AiChatScreen(
    api: Api,
    conversationId: String?,
    onOpenConversation: (String) -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenPage: (String, String?) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    AiThread(
        onOpenProfile = onOpenProfile,
        onOpenPage = onOpenPage,
        onBack = onBack
    )
}

@Composable
private fun AiThread(
    onOpenProfile: () -> Unit,
    onOpenPage: (String, String?) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = FirebaseBackend.uid.orEmpty()
    var messages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) messages = StudyBuddy.loadHistory(context, uid)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || sending || uid.isBlank()) return
        draft = ""
        sending = true
        error = ""
        messages = messages + ("user" to clean)
        scope.launch {
            try {
                when (val action = StudyBuddy.ask(context, uid, clean)) {
                    is StudyBuddy.AiAction.Reply -> {
                        messages = StudyBuddy.loadHistory(context, uid)
                    }
                    is StudyBuddy.AiAction.Clarify -> {
                        val h = StudyBuddy.loadHistory(context, uid) + ("assistant" to action.text)
                        StudyBuddy.saveHistory(context, uid, h)
                        messages = h
                    }
                    is StudyBuddy.AiAction.SendMessage -> {
                        runCatching {
                            StudyBuddy.sendMessageAs(uid, action.userId, action.userName, action.text)
                        }.onSuccess { toId ->
                            val h = StudyBuddy.loadHistory(context, uid) +
                                ("assistant" to "Sent to ${action.userName ?: "them"}: ${action.text}")
                            StudyBuddy.saveHistory(context, uid, h)
                            messages = h
                        }.onFailure {
                            val h = StudyBuddy.loadHistory(context, uid) +
                                ("assistant" to "I couldn't find \"${action.userName}\". Ask for their exact name.")
                            StudyBuddy.saveHistory(context, uid, h)
                            messages = h
                        }
                    }
                    is StudyBuddy.AiAction.OpenPage -> onOpenPage(action.page, action.presearch),
                    is StudyBuddy.AiAction.ClearHistory -> {
                        messages = emptyList()
                    }
                }
            } catch (e: Exception) {
                error = (e as? ApiException)?.message ?: "HolidayLink AI is unavailable right now."
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(HolidayLinkGradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { onOpenProfile() }) {
                Text("HolidayLink AI", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@ai • tap for profile", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenProfile) { Icon(Icons.Default.Person, "AI profile") }
            if (sending) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "intro") {
                    EmptyState(
                        icon = Icons.Default.AutoAwesome,
                        title = "Say hello",
                        subtitle = "Ask for help with messages, plans or summaries. Mention @ai in any chat to send on your behalf.",
                        modifier = Modifier.height(300.dp)
                    )
                }
                item(key = "chips") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Draft a good-morning message",
                            "Summarise my week",
                            "Help me study"
                        ).forEach { suggestion ->
                            Text(
                                suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { send(suggestion) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
            items(messages, key = { it.second.hashCode().toString() + it.first }) { (role, text) ->
                AiStudyBubble(role = role, text = text)
            }
            if (error.isNotBlank()) {
                item(key = "error") {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
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
            IconButton(onClick = { send(draft) }, enabled = !sending) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
        TextButton(onClick = {
            StudyBuddy.clearHistory(context, uid)
            messages = emptyList()
        }) { Text("Clear chat") }
    }
}

@Composable
private fun AiStudyBubble(role: String, text: String) {
    val mine = role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .weight(1f, fill = false)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp
                    )
                )
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
