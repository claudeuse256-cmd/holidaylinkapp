package org.holidaylink.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.Me
import org.holidaylink.app.phone.Countries
import org.holidaylink.app.phone.PhoneNumbers

/**
 * The signed-in user's own profile.
 *
 * Profile photos are re-encoded to a bounded JPEG and uploaded through the backend, which writes
 * the bytes to disk and stores only a URL — image blobs never go into PostgreSQL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(api: Api, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var me by remember { mutableStateOf<Me?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmRemovePhoto by remember { mutableStateOf(false) }

    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }

    suspend fun reload() {
        loading = me == null
        try {
            val fresh = api.me()
            me = fresh
            displayName = fresh.displayName
            username = fresh.username
            about = fresh.about.orEmpty()
            error = ""
        } catch (e: Exception) {
            error = (e as? org.holidaylink.app.net.ApiException)?.message
                ?: "We couldn't load your profile."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                saving = true
                val dataUrl = MediaPicker.imageDataUrl(context, uri)
                if (dataUrl == null) {
                    snackbar.showSnackbar("That image couldn't be read. Try another one.")
                } else {
                    runCatching { api.updateProfile(avatarDataUrl = dataUrl) }
                        .onSuccess {
                            me = it
                            snackbar.showSnackbar("Profile photo updated.")
                        }
                        .onFailure {
                            snackbar.showSnackbar(
                                (it as? org.holidaylink.app.net.ApiException)?.message
                                    ?: "We couldn't update your photo."
                            )
                        }
                }
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = { Text(if (editing) "Edit profile" else "Profile") },
                navigationIcon = {
                    IconButton(onClick = { if (editing) editing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!editing) {
                        IconButton(onClick = { shareProfile(context, me) }) {
                            Icon(Icons.Default.Share, "Share profile")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            loading && me == null -> Box(Modifier.padding(padding)) { LoadingState() }
            error.isNotBlank() && me == null ->
                Box(Modifier.padding(padding)) { ErrorState(error) { scope.launch { reload() } } }
            else -> {
                val profile = me ?: return@Scaffold
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))

                    Box(contentAlignment = Alignment.BottomEnd) {
                        Avatar(
                            name = profile.displayName,
                            photo = profile.avatarUrl,
                            size = 112.dp
                        )
                        if (!editing) {
                            IconButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    "Change profile photo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (saving) {
                        Spacer(Modifier.height(10.dp))
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }

                    Spacer(Modifier.height(18.dp))

                    if (editing) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(120) },
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(32) },
                            label = { Text("Username") },
                            prefix = { Text("@") },
                            singleLine = true,
                            isError = username.length in 1..2,
                            supportingText = {
                                if (username.length in 1..2) {
                                    Text("Usernames need at least 3 characters.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = about,
                            onValueChange = { about = it.take(140) },
                            label = { Text("About") },
                            placeholder = { Text("Studying hard \uD83D\uDCAA") },
                            minLines = 3,
                            supportingText = { Text("${about.length}/140") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { editing = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    if (saving) return@Button
                                    saving = true
                                    scope.launch {
                                        runCatching {
                                            api.updateProfile(
                                                displayName = displayName.trim(),
                                                username = username.trim(),
                                                about = about.trim()
                                            )
                                        }.onSuccess {
                                            me = it
                                            editing = false
                                            snackbar.showSnackbar("Profile saved.")
                                        }.onFailure { e ->
                                            snackbar.showSnackbar(
                                                (e as? org.holidaylink.app.net.ApiException)?.message
                                                    ?: "We couldn't save your profile."
                                            )
                                        }
                                        saving = false
                                    }
                                },
                                enabled = displayName.isNotBlank() && username.length >= 3 && !saving,
                                modifier = Modifier.weight(1f)
                            ) { Text("Save") }
                        }
                        Spacer(Modifier.height(20.dp))
                    } else {
                        Text(
                            profile.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (profile.username.isNotBlank()) {
                            Text(
                                "@${profile.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!profile.about.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                profile.about,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { editing = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Edit profile")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        runCatching { shareProfile(context, me) }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Share") }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (!editing) {
                        Column(Modifier.fillMaxWidth()) {
                            SettingsSection("Account information") {
                                AccountRow("Phone number", PhoneNumbers.mask(profile.phone))
                                AccountRow("Country", countryNameFor(profile.phone))
                                AccountRow("Username", "@${profile.username}")
                                profile.createdAt?.let { AccountRow("Joined", friendlyWhen(it)) }
                            }

                            SettingsSection("Profile photo") {
                                if (!profile.avatarUrl.isNullOrBlank()) {
                                    SettingsNavRow(
                                        title = "Remove profile photo",
                                        icon = Icons.Default.Delete,
                                        onClick = { confirmRemovePhoto = true }
                                    )
                                }
                                SettingsNavRow(
                                    title = "Change profile photo",
                                    icon = Icons.Default.CameraAlt,
                                    onClick = {
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                            }

                            SettingsSection("Sharing") {
                                SettingsNavRow(
                                    title = "Share profile link",
                                    icon = Icons.Default.Share,
                                    subtitle = "Send your @username to a friend",
                                    onClick = { shareProfile(context, me) }
                                )
                                SettingsNavRow(
                                    title = "My QR code",
                                    icon = Icons.Default.QrCode2,
                                    subtitle = "Not available yet",
                                    enabled = false,
                                    onClick = {}
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }

    if (confirmRemovePhoto) {
        AlertDialog(
            onDismissRequest = { confirmRemovePhoto = false },
            title = { Text("Remove profile photo?") },
            text = { Text("You'll fall back to your initials. You can add a new photo at any time.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemovePhoto = false
                    scope.launch {
                        runCatching { api.updateProfile(removeAvatar = true) }
                            .onSuccess {
                                me = it
                                snackbar.showSnackbar("Profile photo removed.")
                            }
                            .onFailure { snackbar.showSnackbar("We couldn't remove that photo.") }
                    }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemovePhoto = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AccountRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Best-effort country label from an E.164 number, longest dial code first. */
private fun countryNameFor(phone: String): String {
    val dial = phone.filter { it.isDigit() }
    return Countries.ALL
        .sortedByDescending { it.dialCode.length }
        .firstOrNull { dial.startsWith(it.dialCode) }
        ?.name
        ?: "—"
}

/** Shares the profile through Android's share sheet. */
private fun shareProfile(context: android.content.Context, me: Me?) {
    val handle = me?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
    val text = if (handle != null) {
        "Find me on HolidayLink: $handle"
    } else {
        "I'm on HolidayLink."
    }
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share profile"))
    }
}
