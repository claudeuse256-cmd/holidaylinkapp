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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.holidaylink.app.net.AiBot
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException

/**
 * HolidayLink AI profile: tappable identity like a user profile.
 * Handle @ai, description, Start chat, mention help, and Telegram-style
 * user-created bots (mentionable @names with custom instructions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProfileScreen(
    api: Api,
    onBack: () -> Unit,
    onStartChat: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var bots by remember { mutableStateOf<List<AiBot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    var botName by remember { mutableStateOf("") }
    var botUsername by remember { mutableStateOf("") }
    var botInstructions by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<AiBot?>(null) }

    suspend fun reload() {
        loading = bots.isEmpty()
        try {
            bots = api.aiBots()
        } catch (e: Exception) {
            snackbar.showSnackbar((e as? ApiException)?.message ?: "Couldn't load bots.")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HolidayLink AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF7A2CFF)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("HolidayLink AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("@ai • Assistant", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Your built-in assistant. Mention @ai in any chat to get an answer " +
                    "and post it as your message, or chat here any time. " +
                    "It drafts, summarizes and plans — and sends on your behalf only when you tap Send.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onStartChat, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Chat, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Start chat")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { creating = !creating }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(if (creating) "Close bot creator" else "Create a bot")
            }

            if (creating) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = botName, onValueChange = { botName = it.take(120) },
                    label = { Text("Bot display name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = botUsername,
                    onValueChange = { botUsername = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(32) },
                    label = { Text("Bot @username") }, prefix = { Text("@") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = botInstructions, onValueChange = { botInstructions = it.take(2000) },
                    label = { Text("Instructions (personality, what it knows)") },
                    minLines = 3, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { api.createBot(botUsername.trim(), botName.trim(), botInstructions.trim()) }
                                .onSuccess {
                                    bots = listOf(it) + bots
                                    botName = ""; botUsername = ""; botInstructions = ""; creating = false
                                    snackbar.showSnackbar("Bot @${it.username} created. Mention it in any chat.")
                                }
                                .onFailure { snackbar.showSnackbar((it as? ApiException)?.message ?: "Couldn't create that bot.") }
                        }
                    },
                    enabled = botName.isNotBlank() && botUsername.length >= 3,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Create bot") }
            }

            Spacer(Modifier.height(20.dp))
            Text("Bots (${bots.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            if (loading) {
                Spacer(Modifier.height(12.dp)); CircularProgressIndicator(Modifier.size(24.dp))
            } else if (bots.isEmpty()) {
                Text(
                    "No bots yet. Create one above, then mention @name in any chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
                bots.forEach { bot ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF7C3AED)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bot.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "@${bot.username}" + if (bot.mine) " • yours" else " • by ${bot.ownerName ?: "someone"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (bot.mine) {
                            IconButton(onClick = { confirmDelete = bot }) {
                                Icon(Icons.Default.Delete, "Delete bot", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    val doomed = confirmDelete
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete @${doomed.username}?") },
            text = { Text("Mentions of this bot will stop working in chats.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        runCatching { api.deleteBot(doomed.id) }
                            .onSuccess { bots = bots.filterNot { it.id == doomed.id } }
                            .onFailure { scope.launch { snackbar.showSnackbar("Couldn't delete that bot.") } }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}
