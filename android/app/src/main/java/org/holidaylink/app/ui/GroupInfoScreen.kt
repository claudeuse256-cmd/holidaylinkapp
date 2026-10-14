@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.FirestoreGroups
import org.holidaylink.app.net.Imgbb
import org.holidaylink.app.net.User

/**
 * WhatsApp/Telegram-style group profile: photo, name, description, members, admin tools and
 * public/private visibility, all backed by the shared `chats/{id}` document.
 */
@Composable
fun GroupInfoScreen(
    chat: ChatItem,
    meId: String,
    onBack: () -> Unit,
    onOpenUser: (User) -> Unit,
    onLeft: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var info by remember { mutableStateOf<FirestoreGroups.GroupInfo?>(null) }
    var members by remember { mutableStateOf<List<User>>(emptyList()) }
    var allUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var editName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf(false) }
    var descDraft by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var actionMember by remember { mutableStateOf<User?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = MediaPicker.imageJpegBytes(context, uri) ?: throw IllegalStateException("unreadable")
                val url = Imgbb.uploadJpeg(bytes)
                FirestoreGroups.setPhoto(chat.id, url)
                snackbar.showSnackbar("Group photo updated")
            } catch (e: Exception) {
                snackbar.showSnackbar("Couldn't update the group photo.")
            }
        }
    }

    LaunchedEffect(chat.id) {
        FirestoreGroups.groupFlow(chat.id).collect { g ->
            info = g
            loading = false
            if (g != null) {
                members = FirestoreGroups.resolveUsers(g.members)
            }
        }
    }

    suspend fun loadAllUsers() {
        if (allUsers.isNotEmpty()) return
        runCatching {
            val snap = FirebaseBackend.db.collection("users").limit(300).get().await()
            allUsers = snap.documents.mapNotNull { d ->
                val name = (d.getString("name") ?: d.getString("displayName")).orEmpty()
                if (name.isBlank()) return@mapNotNull null
                User(
                    id = d.id,
                    displayName = name,
                    username = d.getString("username").orEmpty(),
                    avatarUrl = d.getString("profilePic") ?: d.getString("avatarUrl"),
                    online = d.getBoolean("online") == true
                )
            }
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text(
                        info?.name?.takeIf { it.isNotBlank() } ?: chat.title.orEmpty().ifBlank { "Group" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (info?.isAdmin(meId) == true) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, "Delete group", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val g = info
        if (loading && g == null) {
            Box(Modifier.padding(padding).fillMaxSize()) { LoadingState(label = "Loading group…") }
        } else {
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        Avatar(name = g?.name ?: chat.title.orEmpty().ifBlank { "Group" }, photo = g?.photo, size = 96.dp)
                        if (g != null && g.isAdmin(meId)) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { photoPicker.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AddAPhoto, "Change photo",
                                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            g?.name?.takeIf { it.isNotBlank() } ?: "Group",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (g != null && g.isAdmin(meId)) {
                            Spacer(Modifier.width(6.dp))
                            val currentName = g.name
                            IconButton(onClick = { nameDraft = currentName; editName = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, "Edit name", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (g?.privacy == "public") Icons.Default.Public else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${members.size} members · ${if (g?.privacy == "public") "Public" else "Private"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (g != null && !g.members.contains(meId)) {
                    val groupName = g.name
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { FirestoreGroups.joinGroup(chat.id) }
                                    .onSuccess { snackbar.showSnackbar("Joined $groupName") }
                                    .onFailure { snackbar.showSnackbar("Couldn't join the group") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    ) { Text("Join group") }
                    Spacer(Modifier.height(10.dp))
                }

                // Description
                if (g != null) {
                    val gDesc = g.description
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Description", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (g.isAdmin(meId)) {
                                TextButton(onClick = { descDraft = gDesc; editDesc = true }) { Text("Edit") }
                            }
                        }
                        Text(
                            gDesc.ifBlank { "No description yet." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Admin controls
                if (g != null && g.isAdmin(meId)) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        "Group settings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 2.dp)
                    )
                    SwitchRow(
                        title = "Public group",
                        subtitle = "Anyone can find this group and join",
                        checked = g.privacy == "public"
                    ) { on ->
                        scope.launch {
                            runCatching { FirestoreGroups.setPrivacy(chat.id, if (on) "public" else "private") }
                                .onFailure { snackbar.showSnackbar("Couldn't update visibility") }
                        }
                    }
                    SwitchRow(
                        title = "Only admins can send messages",
                        subtitle = "Announcement mode, like a channel",
                        checked = g.sendMessagesAdminsOnly
                    ) { on -> scope.launch { runCatching { FirestoreGroups.setRule(chat.id, "sendMessagesAdminsOnly", on) } } }
                    SwitchRow(
                        title = "Only admins can edit group info",
                        subtitle = "Lock the name, photo and description",
                        checked = g.editInfoAdminsOnly
                    ) { on -> scope.launch { runCatching { FirestoreGroups.setRule(chat.id, "editInfoAdminsOnly", on) } } }
                    SwitchRow(
                        title = "Approve new members",
                        subtitle = "Admins must admit people who join",
                        checked = g.approveNewMembers
                    ) { on -> scope.launch { runCatching { FirestoreGroups.setRule(chat.id, "approveNewMembers", on) } } }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                if (g != null && g.isAdmin(meId)) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch { loadAllUsers() }
                            addOpen = true
                        }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Add, "Add members", tint = MaterialTheme.colorScheme.onPrimary) }
                        Spacer(Modifier.width(14.dp))
                        Text("Add members", fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    "${members.size} members",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                )

                members.forEach { u ->
                    val isTargetAdmin = g?.isAdmin(u.id) == true
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (u.id != meId) onOpenUser(u)
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name = u.displayName, photo = u.avatarUrl, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (u.id == meId) {
                                Text("You", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (isTargetAdmin) {
                            Text(
                                "Admin",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (g != null && g.isAdmin(meId) && u.id != meId) {
                            IconButton(onClick = { actionMember = u }) { Icon(Icons.Default.MoreVert, "Member options") }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                if (g == null || g.members.contains(meId)) {
                    OutlinedButton(
                        onClick = { confirmLeave = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Leave group")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ---- dialogs ----

    if (editName) {
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Group name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(60) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nameDraft.isNotBlank(),
                    onClick = {
                        editName = false
                        scope.launch {
                            runCatching { FirestoreGroups.setName(chat.id, nameDraft) }
                                .onSuccess { snackbar.showSnackbar("Name updated") }
                                .onFailure { snackbar.showSnackbar("Couldn't rename the group") }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } }
        )
    }

    if (editDesc) {
        AlertDialog(
            onDismissRequest = { editDesc = false },
            title = { Text("Description") },
            text = {
                OutlinedTextField(
                    value = descDraft,
                    onValueChange = { descDraft = it.take(500) },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editDesc = false
                    scope.launch {
                        runCatching { FirestoreGroups.setDescription(chat.id, descDraft) }
                            .onSuccess { snackbar.showSnackbar("Description updated") }
                            .onFailure { snackbar.showSnackbar("Couldn't update the description") }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editDesc = false }) { Text("Cancel") } }
        )
    }

    if (addOpen) {
        var picked by remember { mutableStateOf(setOf<String>()) }
        val candidates = allUsers.filter { u -> members.none { it.id == u.id } }
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("Add members") },
            text = {
                if (candidates.isEmpty()) {
                    Text("No one new to add right now.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(candidates, key = { it.id }) { u ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        picked = if (picked.contains(u.id)) picked - u.id else picked + u.id
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(name = u.displayName, photo = u.avatarUrl, size = 38.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(u.displayName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Checkbox(checked = picked.contains(u.id), onCheckedChange = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = picked.isNotEmpty(),
                    onClick = {
                        val ids = picked.toList()
                        addOpen = false
                        scope.launch {
                            runCatching { FirestoreGroups.addMembers(chat.id, ids) }
                                .onSuccess { snackbar.showSnackbar("Added ${ids.size} member${if (ids.size == 1) "" else "s"}") }
                                .onFailure { snackbar.showSnackbar("Couldn't add members") }
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Cancel") } }
        )
    }

    val targetMember = actionMember
    if (targetMember != null) {
        val isTargetAdmin = info?.isAdmin(targetMember.id) == true
        AlertDialog(
            onDismissRequest = { actionMember = null },
            title = { Text(targetMember.displayName) },
            text = {
                Column {
                    TextButton(onClick = {
                        actionMember = null
                        scope.launch {
                            runCatching { FirestoreGroups.setAdmin(chat.id, targetMember.id, !isTargetAdmin) }
                                .onFailure { snackbar.showSnackbar("Couldn't change admin rights") }
                        }
                    }) { Text(if (isTargetAdmin) "Dismiss as admin" else "Make group admin") }
                    TextButton(onClick = {
                        actionMember = null
                        scope.launch {
                            runCatching { FirestoreGroups.removeMember(chat.id, targetMember.id) }
                                .onSuccess { snackbar.showSnackbar("Removed from group") }
                                .onFailure { snackbar.showSnackbar("Couldn't remove that member") }
                        }
                    }) { Text("Remove from group", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { actionMember = null }) { Text("Close") } }
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave group?") },
            text = { Text("You will stop receiving messages from this group.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    leaveNow(scope, chat.id, onLeft)
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete group?") },
            text = { Text("This removes the group and its messages for everyone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { FirestoreGroups.deleteGroup(chat.id) }
                        onLeft()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun leaveNow(scope: kotlinx.coroutines.CoroutineScope, chatId: String, onLeft: () -> Unit) {
    scope.launch {
        runCatching { FirestoreGroups.leaveGroup(chatId) }
        onLeft()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
