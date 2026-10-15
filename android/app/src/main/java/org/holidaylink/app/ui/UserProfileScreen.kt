@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    onStartCall: (User, String) -> Unit
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
                online = snap.getBoolean("online"),
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

    LaunchedEffect(user.id) { reload() }

    val shown = profile
    val blocked = shown?.isBlocked == true

    Scaffold(
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
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(20.dp))
                Avatar(
                    name = shown.displayName,
                    photo = shown.avatarUrl,
                    size = 108.dp,
                    onClick = { shown.avatarUrl?.takeIf { it.isNotBlank() }?.let { viewerUrl = it } }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    shown.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!shown.username.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "@${shown.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(10.dp))
                val trulyOnline = shown.online == true && runCatching {
                    val ms = shown.lastSeen?.let { java.time.Instant.parse(it).toEpochMilli() }
                    ms == null || System.currentTimeMillis() - ms < 5 * 60 * 1000L
                }.getOrDefault(false)
                Text(
                    when {
                        shown.blockedByThem -> "You can't see their details while you're blocked"
                        trulyOnline -> "Online now"
                        else -> shown.lastSeen?.let { "Last seen ${friendlyWhen(it)}" } ?: "Last seen hidden"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!shown.about.isNullOrBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        shown.about,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onOpenChat(user) },
                        enabled = !blocked && !shown.blockedByThem && !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Message")
                    }
                    OutlinedButton(
                        onClick = { onStartCall(user, "audio") },
                        enabled = !blocked && !shown.blockedByThem && !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Call, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Audio")
                    }
                    OutlinedButton(
                        onClick = { onStartCall(user, "video") },
                        enabled = !blocked && !shown.blockedByThem && !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Videocam, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Video")
                    }
                }

                Spacer(Modifier.height(26.dp))
                SettingsSection("Contact details") {
                    val visiblePhone = shown.phone
                    if (!visiblePhone.isNullOrBlank()) {
                        SettingsValueRow(
                            title = "Phone",
                            icon = Icons.Default.Call,
                            value = PhoneNumbers.mask(visiblePhone)
                        ) { openDialer(context, visiblePhone) }
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
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

                Spacer(Modifier.height(20.dp))
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