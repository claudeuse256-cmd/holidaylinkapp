@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.FirestoreGroups
import org.holidaylink.app.net.Imgbb
import org.holidaylink.app.net.User

/**
 * WhatsApp/Telegram-style group profile: photo, name, description, members, per-chat
 * notification and wallpaper choices for everyone, plus the admin toolkit (permissions,
 * visibility, member management, delete) for admins.
 */
@Composable
fun GroupInfoScreen(
    chat: ChatItem,
    meId: String,
    onBack: () -> Unit,
    onOpenUser: (User) -> Unit,
    onLeft: () -> Unit,
    onScheduled: () -> Unit = {}
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
    var tagTarget by remember { mutableStateOf<User?>(null) }
    var tagText by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var wallpaperOpen by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(AppSettings.isChatMuted(chat.id)) }

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
            if (g != null) members = FirestoreGroups.resolveUsers(g.members)
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
                    online = FirebaseBackend.snapshotOnline(d)
                )
            }
        }
    }

    val g = info
    val amAdmin = g?.isAdmin(meId) == true
    val amMember = g == null || g.members.contains(meId)
    val canEditInfo = g != null && (amAdmin || !g.editInfoAdminsOnly)
    val canAddMembers = g != null && (amAdmin || !g.addMembersAdminsOnly)

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        g?.name?.takeIf { it.isNotBlank() } ?: chat.title.orEmpty().ifBlank { "Group" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (amAdmin) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, "Delete group", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (loading && g == null) {
            Box(Modifier.padding(padding).fillMaxSize()) { LoadingState(label = "Loading group…") }
        } else {
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                // ---- hero ----
                Column(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        Avatar(name = g?.name ?: chat.title.orEmpty().ifBlank { "Group" }, photo = g?.photo, size = 96.dp)
                        if (canEditInfo) {
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
                        if (canEditInfo) {
                            Spacer(Modifier.width(6.dp))
                            val currentName = g?.name.orEmpty()
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

                // ---- quick actions (WhatsApp-style circles) ----
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GroupAction(
                        icon = Icons.AutoMirrored.Filled.Message,
                        label = "Message",
                        onClick = onBack
                    )
                    GroupAction(
                        icon = if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        label = if (muted) "Unmute" else "Mute",
                        highlighted = muted
                    ) {
                        val want = !muted
                        muted = want
                        AppSettings.setChatMuted(chat.id, want)
                    }
                    GroupAction(
                        icon = Icons.Default.Wallpaper,
                        label = "Wallpaper"
                    ) { wallpaperOpen = true }
                    if (canAddMembers) {
                        GroupAction(
                            icon = Icons.Default.PersonAdd,
                            label = "Add",
                            onClick = {
                                scope.launch { loadAllUsers() }
                                addOpen = true
                            }
                        )
                    }
                }

                if (g != null && !amMember) {
                    Spacer(Modifier.height(16.dp))
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
                }

                // ---- about ----
                if (g != null) {
                    val gDesc = g.description
                    SettingsSection("About") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    gDesc.ifBlank { "No description yet." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (canEditInfo) {
                                    TextButton(onClick = { descDraft = gDesc; editDesc = true }) { Text(tr("Edit")) }
                                }
                            }
                        }
                    }
                }

                // ---- per-person settings every member gets ----
                SettingsSection("Notifications & appearance") {
                    SettingsSwitchRow(
                        title = "Mute notifications",
                        icon = if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        subtitle = if (muted) "This group stays quiet" else "Sound and vibrate for new messages",
                        checked = muted,
                        onCheckedChange = { want ->
                            muted = want
                            AppSettings.setChatMuted(chat.id, want)
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Group wallpaper",
                        icon = Icons.Default.Wallpaper,
                        subtitle = "Only for this group",
                        onClick = { wallpaperOpen = true }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Share group",
                        icon = Icons.Default.Share,
                        subtitle = "Send the group name to a friend",
                        onClick = { shareGroup(context, g?.name ?: chat.title.orEmpty(), chat.id) }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Copy group link",
                        icon = Icons.Default.Link,
                        subtitle = "Paste it in a chat on this phone",
                        onClick = {
                            copyGroupLink(context)
                            scope.launch { snackbar.showSnackbar("Group link copied") }
                        }
                    )
                }

                ChatExtrasSection(
                    chatId = chat.id,
                    chatLabel = g?.name ?: chat.title.orEmpty(),
                    onOpenScheduled = onScheduled,
                    showWallpaper = false
                )

                // ---- admin permissions ----
                if (amAdmin && g != null) {
                    SettingsSection("Group permissions") {
                        SettingsSwitchRow(
                            title = "Only admins can send messages",
                            icon = Icons.Default.Security,
                            subtitle = "Announcement mode, like a channel",
                            checked = g.sendMessagesAdminsOnly
                        ) { on -> setRule(scope, chat.id, "sendMessagesAdminsOnly", on, snackbar) }
                        SettingsDivider()
                        SettingsSwitchRow(
                            title = "Only admins can edit group info",
                            icon = Icons.Default.Edit,
                            subtitle = "Lock the name, photo and description",
                            checked = g.editInfoAdminsOnly
                        ) { on -> setRule(scope, chat.id, "editInfoAdminsOnly", on, snackbar) }
                        SettingsDivider()
                        SettingsSwitchRow(
                            title = "Only admins can add members",
                            icon = Icons.Default.PersonAdd,
                            subtitle = "Members must ask an admin to invite people",
                            checked = g.addMembersAdminsOnly
                        ) { on -> setRule(scope, chat.id, "addMembersAdminsOnly", on, snackbar) }
                        SettingsDivider()
                        SettingsSwitchRow(
                            title = "Approve new members",
                            icon = Icons.Default.Group,
                            subtitle = "Admins must admit people who join",
                            checked = g.approveNewMembers
                        ) { on -> setRule(scope, chat.id, "approveNewMembers", on, snackbar) }
                        SettingsDivider()
                        SettingsSwitchRow(
                            title = "Public group",
                            icon = if (g.privacy == "public") Icons.Default.Public else Icons.Default.Lock,
                            subtitle = "Anyone can find this group and join",
                            checked = g.privacy == "public"
                        ) { on ->
                            scope.launch {
                                runCatching { FirestoreGroups.setPrivacy(chat.id, if (on) "public" else "private") }
                                    .onFailure { snackbar.showSnackbar("Couldn't update visibility") }
                            }
                        }
                    }
                }

                // ---- members ----
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${members.size} members",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (amAdmin) {
                        Text(
                            "${g?.admins?.size ?: 0} admin${if ((g?.admins?.size ?: 0) == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

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
                            g?.memberTags?.get(u.id)?.let { tag ->
                                Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
                        if (amAdmin && u.id != meId) {
                            IconButton(onClick = { actionMember = u }) { Icon(Icons.Default.MoreVert, "Member options") }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                if (amMember) {
                    OutlinedButton(
                        onClick = { confirmLeave = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Leave group")
                    }
                }
                if (amAdmin) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete group", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    // ---- dialogs & sheets ----

    if (wallpaperOpen) {
        WallpaperSheet(
            current = AppSettings.wallpaperPresetFor(chat.id),
            title = "Group background",
            onPick = { preset -> AppSettings.setChatWallpaperPresetFor(chat.id, preset) },
            onDismiss = { wallpaperOpen = false },
            extra = {
                TextButton(onClick = {
                    AppSettings.setChatWallpaperPresetFor(chat.id, null)
                    wallpaperOpen = false
                }) { Text("Use the default background") }
            }
        )
    }

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
                ) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text(tr("Cancel")) } }
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
                }) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { editDesc = false }) { Text(tr("Cancel")) } }
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
                ) { Text(tr("Add")) }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text(tr("Cancel")) } }
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
                        onOpenUser(targetMember)
                    }) { Text(tr("View profile")) }
                    TextButton(onClick = {
                        actionMember = null
                        tagText = info?.memberTags?.get(targetMember.id).orEmpty()
                        tagTarget = targetMember
                    }) { Text(tr("Set tag")) }
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
            confirmButton = { TextButton(onClick = { actionMember = null }) { Text(tr("Close")) } }
        )
    }

    val taggedMember = tagTarget
    if (taggedMember != null) {
        AlertDialog(
            onDismissRequest = { tagTarget = null },
            title = { Text("Tag for ${taggedMember.displayName}") },
            text = {
                Column {
                    Text(
                        "A short label shown next to their name in the group, like \"Class rep\" or \"Treasurer\". Leave empty to remove it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tagText,
                        onValueChange = { tagText = it.take(24) },
                        singleLine = true,
                        label = { Text(tr("Tag")) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = taggedMember
                    val value = tagText
                    tagTarget = null
                    scope.launch {
                        runCatching { FirestoreGroups.setMemberTag(chat.id, target.id, value) }
                            .onFailure { snackbar.showSnackbar("Couldn't save the tag") }
                    }
                }) { Text(tr("Save")) }
            },
            dismissButton = { TextButton(onClick = { tagTarget = null }) { Text(tr("Cancel")) } }
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
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(tr("Cancel")) } }
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
                }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) } }
        )
    }
}

private fun leaveNow(scope: kotlinx.coroutines.CoroutineScope, chatId: String, onLeft: () -> Unit) {
    scope.launch {
        runCatching { FirestoreGroups.leaveGroup(chatId) }
        onLeft()
    }
}

/** A round quick action under the hero, like WhatsApp's Message / Mute / Add circles. */
@Composable
private fun GroupAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (highlighted) scheme.primary else scheme.primary.copy(alpha = 0.12f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (highlighted) scheme.onPrimary else scheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun setRule(
    scope: kotlinx.coroutines.CoroutineScope,
    chatId: String,
    key: String,
    value: Boolean,
    snackbar: SnackbarHostState
) {
    scope.launch {
        runCatching { FirestoreGroups.setRule(chatId, key, value) }
            .onFailure { snackbar.showSnackbar("Couldn't save that setting") }
    }
}

/** Shares the group name (and id) through the system share sheet. */
private fun shareGroup(context: Context, name: String, chatId: String) {
    val text = "Join \"${name.ifBlank { "our group" }}\" on HolidayLink.\n" +
        "Open HolidayLink → New chat → Group, or ask for the invite.\n" +
        "Group ID: $chatId"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share group")) }
}

private fun copyGroupLink(context: Context, chatId: String = "") {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val text = if (chatId.isBlank()) "https://holidaylink.app/group"
    else "https://holidaylink.app/group/$chatId"
    manager.setPrimaryClip(ClipData.newPlainText("HolidayLink group", text))
}
