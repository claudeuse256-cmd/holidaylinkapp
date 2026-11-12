@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.Profile
import org.holidaylink.app.net.User
import org.holidaylink.app.phone.PhoneNumbers

/**
 * Another user's profile.
 *
 * Everything here comes from `GET /users/:id`, which already hides fields the viewer is not
 * allowed to see: a missing phone number or last-seen here means the other person chose
 * "Nobody", not that the screen failed to load.
 */
@Composable
fun UserProfileScreen(
    api: Api,
    user: User,
    onBack: () -> Unit,
    onOpenChat: (User) -> Unit,
    onStartCall: (User, String) -> Unit,
    onOpenScheduled: (String, String) -> Unit = { _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var profile by remember { mutableStateOf<Profile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmReport by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }

    suspend fun reload() {
        try {
            val me = FirebaseBackend.uid.orEmpty()
            val snap = FirebaseBackend.db.collection("users").document(user.id).get().await()
            if (!snap.exists()) throw ApiException("That profile no longer exists.")
            val myBlocked = runCatching {
                @Suppress("UNCHECKED_CAST")
                (FirebaseBackend.db.collection("users").document(me).get().await().get("blocked") as? List<String>).orEmpty()
            }.getOrDefault(emptyList())
            @Suppress("UNCHECKED_CAST")
            val theirBlocked = (snap.get("blocked") as? List<String>).orEmpty()
            profile = Profile(
                id = user.id,
                displayName = snap.getString("name")?.takeIf { it.isNotBlank() } ?: "HolidayLink user",
                username = snap.getString("username"),
                avatarUrl = snap.getString("profilePic"),
                about = snap.getString("bio"),
                phone = snap.getString("phone"),
                online = FirebaseBackend.snapshotOnline(snap),
                lastSeen = snap.safeTimestamp("lastSeen")?.toDate()?.toInstant()?.toString(),
                isBlocked = myBlocked.contains(user.id),
                blockedByThem = theirBlocked.contains(me)
            )
            error = ""
        } catch (e: Exception) {
            // Never a dead screen: fall back to the already-known listing data.
            if (profile == null && user.displayName.isNotBlank()) {
                profile = Profile(
                    id = user.id,
                    displayName = user.displayName,
                    username = user.username.takeIf { it.isNotBlank() },
                    avatarUrl = user.avatarUrl,
                    about = null,
                    phone = null,
                    online = null,
                    lastSeen = null
                )
            }
            if (profile == null) error = "We couldn't load that profile. Check your internet."
        } finally {
            loading = false
        }
    }

    suspend fun setBlocked(block: Boolean) {
        val me = FirebaseBackend.uid ?: throw ApiException("Sign in again first.")
        val ref = FirebaseBackend.db.collection("users").document(me)
        if (block) ref.update("blocked", com.google.firebase.firestore.FieldValue.arrayUnion(user.id)).await()
        else ref.update("blocked", com.google.firebase.firestore.FieldValue.arrayRemove(user.id)).await()
    }

    suspend fun directChatId(peerId: String): String? = runCatching {
        val me = FirebaseBackend.uid.orEmpty()
        FirebaseBackend.db.collection("chats").whereArrayContains("users", me).limit(60).get().await()
            .documents.firstOrNull { d ->
                d.getBoolean("isGroup") != true &&
                    ((d.get("users") as? List<*>)?.filterIsInstance<String>()?.contains(peerId) == true)
            }?.id
    }.getOrNull()

    LaunchedEffect(user.id) {
        reload()
        runCatching { directChatId(user.id) }.getOrNull()?.let { muted = AppSettings.isChatMuted(it) }
    }

    val shown = profile
    val blocked = shown?.isBlocked == true

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            loading -> LoadingState(Modifier.padding(padding), label = "Loading profile…")

            error.isNotBlank() && shown == null ->
                ErrorState(error, Modifier.padding(padding)) { scope.launch { reload() } }

            shown != null -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                ProfileHero(
                    name = shown.displayName,
                    username = shown.username,
                    avatarUrl = shown.avatarUrl,
                    status = when {
                        shown.blockedByThem -> "You can't see their details while you're blocked"
                        trulyOnline(shown) -> "Online"
                        else -> shown.lastSeen?.let { "Last seen ${friendlyWhen(it)}" } ?: "Last seen recently"
                    },
                    onAvatar = { shown.avatarUrl?.takeIf { it.isNotBlank() }?.let { viewerUrl = it } }
                )

                // Three round actions, Telegram-style, centred under the photo.
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    ProfileAction(Icons.AutoMirrored.Filled.Message, "Message", !blocked && !shown.blockedByThem && !busy) { onOpenChat(user) }
                    Spacer(Modifier.width(18.dp))
                    ProfileAction(Icons.Default.Call, "Audio", !blocked && !shown.blockedByThem && !busy) { onStartCall(user, "audio") }
                    Spacer(Modifier.width(18.dp))
                    ProfileAction(Icons.Default.Videocam, "Video", !blocked && !shown.blockedByThem && !busy) { onStartCall(user, "video") }
                }

                Spacer(Modifier.height(14.dp))
                SettingsSection("About") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            shown.about?.takeIf { it.isNotBlank() } ?: "No bio yet.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                SettingsSection("Contact details") {
                    if (!shown.username.isNullOrBlank()) {
                        SettingsValueRow(
                            title = "Username",
                            icon = Icons.Default.AlternateEmail,
                            value = "@${shown.username}"
                        ) { copyToClipboard(context, "@${shown.username}", snackbar, scope, "Username copied") }
                        SettingsDivider()
                    }
                    val visiblePhone = shown.phone
                    if (!visiblePhone.isNullOrBlank()) {
                        SettingsValueRow(
                            title = "Phone",
                            icon = Icons.Default.Call,
                            value = PhoneNumbers.mask(visiblePhone)
                        ) { openDialer(context, visiblePhone) }
                        SettingsDivider()
                        SettingsNavRow(
                            title = "Copy phone number",
                            icon = Icons.Default.ContentCopy,
                            subtitle = PhoneNumbers.mask(visiblePhone)
                        ) { copyToClipboard(context, visiblePhone, snackbar, scope, "Phone number copied") }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "This person hides their number from you",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                SettingsSection("Notifications") {
                    SettingsSwitchRow(
                        title = "Mute notifications",
                        icon = if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        subtitle = if (muted) "You won't be alerted about new messages" else "Message alerts are on",
                        checked = muted,
                        onCheckedChange = { want ->
                            muted = want
                            scope.launch {
                                val chatId = directChatId(user.id)
                                if (chatId != null) AppSettings.setChatMuted(chatId, want)
                                else snackbar.showSnackbar("Open a chat with ${shown.displayName} first.")
                            }
                        }
                    )
                }

                Spacer(Modifier.height(18.dp))
                val chatId = org.holidaylink.app.net.FirestoreChat.directId(
                    FirebaseBackend.uid.orEmpty(),
                    user.id
                )
                ChatExtrasSection(
                    chatId = chatId,
                    chatLabel = shown.displayName,
                    onOpenScheduled = { onOpenScheduled(chatId, shown.displayName) }
                )

                Spacer(Modifier.height(18.dp))
                SettingsSection("Safety") {
                    SettingsNavRow(
                        title = if (blocked) "Unblock ${shown.displayName}" else "Block ${shown.displayName}",
                        icon = Icons.Default.Block,
                        enabled = !busy,
                        onClick = { confirmBlock = true }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Report this profile",
                        icon = Icons.Default.Flag,
                        subtitle = "Sends a report to HolidayLink moderators",
                        enabled = !busy,
                        onClick = { confirmReport = true }
                    )
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text(if (blocked) "Unblock?" else "Block ${shown?.displayName ?: "this person"}?") },
            text = {
                Text(
                    if (blocked) "They will be able to message you again."
                    else "They will not be able to message you, call you or see your profile details."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    scope.launch {
                        runCatching { setBlocked(!blocked) }
                            .onSuccess {
                                snackbar.showSnackbar(
                                    if (blocked) "Unblocked." else "Blocked."
                                )
                                reload()
                            }
                            .onFailure {
                                snackbar.showSnackbar("That didn't work. Check your internet.")
                            }
                    }
                }) {
                    Text(
                        if (blocked) "Unblock" else "Block",
                        color = if (blocked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } }
        )
    }

    if (confirmReport) {
        AlertDialog(
            onDismissRequest = { confirmReport = false },
            title = { Text("Report this profile?") },
            text = { Text("HolidayLink moderators will review it. We never share your details with them.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReport = false
                    scope.launch {
                        runCatching {
                            FirebaseBackend.db.collection("reports").add(
                                mapOf(
                                    "reporterId" to (FirebaseBackend.uid.orEmpty()),
                                    "reportedId" to user.id,
                                    "reason" to "profile",
                                    "createdAt" to com.google.firebase.Timestamp.now()
                                )
                            ).await()
                        }
                            .onSuccess { snackbar.showSnackbar("Report sent. Thank you.") }
                            .onFailure {
                                snackbar.showSnackbar("We couldn't send that report.")
                            }
                    }
                }) { Text("Report", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReport = false }) { Text("Cancel") } }
        )
    }

    viewerUrl?.let { url ->
        PhotoViewer(url = url, title = shown?.displayName.orEmpty(), onDismiss = { viewerUrl = null })
    }
}

/** Telegram-style header: tinted band, large photo, name, handle and presence. */
@Composable
private fun ProfileHero(
    name: String,
    username: String?,
    avatarUrl: String?,
    status: String,
    onAvatar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(scheme.primary.copy(alpha = 0.16f), scheme.surface.copy(alpha = 0f))
                )
            )
            .padding(top = 18.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name = name, photo = avatarUrl, size = 104.dp, onClick = onAvatar)
            Spacer(Modifier.height(12.dp))
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!username.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "@$username",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(scheme.primary.copy(alpha = if (enabled) 0.12f else 0.06f))
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

private fun trulyOnline(p: Profile): Boolean = p.online == true && runCatching {
    val ms = p.lastSeen?.let { java.time.Instant.parse(it).toEpochMilli() }
    ms == null || System.currentTimeMillis() - ms < 5 * 60 * 1000L
}.getOrDefault(false)

private fun copyToClipboard(
    context: Context,
    text: String,
    snackbar: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    message: String
) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("HolidayLink", text))
    }
    scope.launch { snackbar.showSnackbar(message) }
}

/**
 * Opens the system dialler pre-filled with [phone]. ACTION_DIAL is used rather than ACTION_CALL
 * so no permission is needed and the user presses call themselves. Returns false when the device
 * has no dialling app at all.
 */
private fun openDialer(context: Context, phone: String? = null): Boolean {
    val number = phone?.filter { it.isDigit() || it == '+' }.orEmpty()
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}