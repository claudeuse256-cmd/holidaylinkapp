@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.User

private enum class NewChatMode { DIRECT, GROUP }

/**
 * "New chat" picker.
 *
 * Direct mode opens an existing one-to-one conversation (or creates it server-side).
 * Group mode creates a new group with the ticked members.
 */
@Composable
fun NewChatScreen(
    api: Api,
    onBack: () -> Unit,
    onOpenChat: (ChatItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var mode by remember { mutableStateOf(NewChatMode.DIRECT) }
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var title by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        try {
            users = api.users(query.trim())
            error = ""
        } catch (e: Exception) {
            error = (e as? ApiException)?.message ?: "We couldn't load people."
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == NewChatMode.DIRECT) "New chat" else "New group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mode == NewChatMode.DIRECT,
                    onClick = { mode = NewChatMode.DIRECT },
                    label = { Text("Person") },
                    leadingIcon = {
                        Icon(Icons.Default.PersonSearch, null, Modifier.size(18.dp))
                    }
                )
                FilterChip(
                    selected = mode == NewChatMode.GROUP,
                    onClick = { mode = NewChatMode.GROUP },
                    label = { Text("Group") },
                    leadingIcon = { Icon(Icons.Default.Group, null, Modifier.size(18.dp)) }
                )
            }

            OutlinedTextField(
                value = if (mode == NewChatMode.GROUP) title else query,
                onValueChange = {
                    if (mode == NewChatMode.GROUP) title = it.take(60) else query = it
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                label = {
                    Text(if (mode == NewChatMode.GROUP) "Group name" else "Search people")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (mode == NewChatMode.GROUP && selected.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selected.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { selected = emptySet() }) { Text("Clear") }
                }
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            when {
                loading -> LoadingState(label = "Loading people…")

                users.isEmpty() -> EmptyState(
                    icon = if (mode == NewChatMode.DIRECT) Icons.Default.PersonSearch
                    else Icons.Default.Group,
                    title = if (mode == NewChatMode.DIRECT) "Nobody found" else "No people to add",
                    subtitle = if (mode == NewChatMode.DIRECT)
                        "Try another name or username."
                    else
                        "Search a different name to find someone to add."
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(users, key = { it.id }) { user ->
                        val checked = selected.contains(user.id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    when (mode) {
                                        NewChatMode.DIRECT -> {
                                            if (busy) return@clickable
                                            busy = true
                                            scope.launch {
                                                runCatching { api.startDirect(user.id) }
                                                    .onSuccess { id ->
                                                        onOpenChat(ChatItem(id, "direct", null, user, null, null))
                                                    }
                                                    .onFailure { e ->
                                                        busy = false
                                                        snackbar.showSnackbar(
                                                            (e as? ApiException)?.message
                                                                ?: "We couldn't open that chat."
                                                        )
                                                    }
                                            }
                                        }

                                        NewChatMode.GROUP -> {
                                            selected = if (checked) selected - user.id
                                            else selected + user.id
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(name = user.displayName, photo = user.avatarUrl, size = 46.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    user.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "@${user.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (mode == NewChatMode.GROUP) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selected = if (checked) selected - user.id
                                        else selected + user.id
                                    }
                                )
                            } else if (user.online == true) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Online",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (mode == NewChatMode.GROUP) {
                TextButton(
                    onClick = {
                        if (busy) return@TextButton
                        if (title.isBlank() || selected.isEmpty()) return@TextButton
                        busy = true
                        scope.launch {
                            runCatching { api.createGroup(title.trim(), selected.toList()) }
                                .onSuccess { id ->
                                    busy = false
                                    onOpenChat(ChatItem(id, "group", title.trim(), null, null, null))
                                }
                                .onFailure { e ->
                                    busy = false
                                    snackbar.showSnackbar(
                                        (e as? ApiException)?.message
                                            ?: "We couldn't create that group."
                                    )
                                }
                        }
                    },
                    enabled = title.isNotBlank() && selected.isNotEmpty() && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(if (busy) "Creating…" else "Create group")
                }
            }
        }
    }
}