package org.holidaylink.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.holidaylink.app.config.AppConfig
import org.holidaylink.app.data.ProfileCache
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.media.MediaEditor
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.Imgbb
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
    val prefs = remember(context) {
        context.getSharedPreferences(AppConfig.PREFS, android.content.Context.MODE_PRIVATE)
    }

    // Painted from the phone's copy first, so opening your profile without internet shows
    // your name and photo instead of a spinner that never finishes.
    var me by remember { mutableStateOf<Me?>(ProfileCache.load(prefs, FirebaseBackend.uid.orEmpty())) }
    var loading by remember { mutableStateOf(me == null) }
    var error by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmRemovePhoto by remember { mutableStateOf(false) }
    var avatarEditBytes by remember { mutableStateOf<ByteArray?>(null) }
    var avatarZoom by remember { mutableStateOf(1f) }
    var avatarOffsetX by remember { mutableStateOf(0f) }
    var avatarOffsetY by remember { mutableStateOf(0f) }

    var displayName by remember { mutableStateOf(me?.displayName.orEmpty()) }
    var username by remember { mutableStateOf(me?.username.orEmpty()) }
    var about by remember { mutableStateOf(me?.about.orEmpty()) }
    var showQr by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = me == null
        try {
            val uid = FirebaseBackend.uid ?: throw org.holidaylink.app.net.ApiException("Sign in again first.")
            val p = withTimeoutOrNull(8_000) { FirebaseBackend.loadProfile(uid) }
                ?: throw org.holidaylink.app.net.ApiException("We couldn't load your profile.")
            val fresh = Me(
                id = uid,
                phone = p.phone,
                email = p.email.takeIf { it.isNotBlank() },
                displayName = p.name.takeIf { it.isNotBlank() } ?: "HolidayLink user",
                username = p.username,
                avatarUrl = p.profilePic,
                about = p.bio.takeIf { it.isNotBlank() }
            )
            me = fresh
            ProfileCache.save(prefs, fresh)
            displayName = fresh.displayName
            username = fresh.username
            about = fresh.about.orEmpty()
            error = ""
        } catch (e: Exception) {
            // A cached profile is still a usable screen; only complain if there is nothing.
            if (me == null) error = "We couldn't load your profile. Check your internet."
        } finally {
            loading = false
        }
    }

    suspend fun saveAvatarUrl(url: String?) {
        val uid = FirebaseBackend.uid ?: throw org.holidaylink.app.net.ApiException("Sign in again first.")
        if (url == null) {
            FirebaseBackend.db.collection("users").document(uid)
                .update("profilePic", com.google.firebase.firestore.FieldValue.delete()).await()
        } else {
            FirebaseBackend.db.collection("users").document(uid).update("profilePic", url).await()
        }
    }

    LaunchedEffect(Unit) { reload() }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                saving = true
                avatarEditBytes = MediaPicker.imageJpegBytes(context, uri)
                avatarZoom = 1f; avatarOffsetX = 0f; avatarOffsetY = 0f
                saving = false
                if (avatarEditBytes == null) snackbar.showSnackbar("That image couldn't be read. Try another one.")
            }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                            ) { Text(tr("Cancel")) }
                            Button(
                                onClick = {
                                    if (saving) return@Button
                                    saving = true
                                    scope.launch {
                                        runCatching {
                                            val uid = FirebaseBackend.uid
                                                ?: throw org.holidaylink.app.net.ApiException("Sign in again first.")
                                            val cleanName = displayName.trim().take(120)
                                            val cleanUsername = username.trim().lowercase()
                                                .filter { it.isLetterOrDigit() || it == '_' }.take(32)
                                            if (cleanName.isBlank() || cleanUsername.length < 3) {
                                                throw org.holidaylink.app.net.ApiException("Enter a display name and a 3+ character username.")
                                            }
                                            if (FirebaseBackend.isUsernameTaken(cleanUsername, uid)) {
                                                throw org.holidaylink.app.net.ApiException("That @username is already taken.")
                                            }
                                            FirebaseBackend.saveProfile(uid, cleanName, cleanUsername, about.trim().take(140))
                                            reload()
                                        }.onSuccess {
                                            editing = false
                                            snackbar.showSnackbar("Profile saved.")
                                        }.onFailure {
                                            snackbar.showSnackbar("We couldn't save your profile. Check your internet.")
                                        }
                                        saving = false
                                    }
                                },
                                enabled = displayName.isNotBlank() && username.length >= 3 && !saving,
                                modifier = Modifier.weight(1f)
                            ) { Text(tr("Save")) }
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
                            ) { Text(tr("Share")) }
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
                                    subtitle = if (profile.username.isNotBlank()) "@${profile.username}" else "Show your code",
                                    onClick = { showQr = true }
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }

    if (showQr && me != null) {
        QrCodeDialog(me = me!!, onDismiss = { showQr = false })
    }

    val sourceAvatarBytes = avatarEditBytes
    if (sourceAvatarBytes != null) {
        val croppedPreview = remember(sourceAvatarBytes, avatarZoom, avatarOffsetX, avatarOffsetY) {
            MediaEditor.cropSquareJpeg(sourceAvatarBytes, avatarZoom, avatarOffsetX, avatarOffsetY)
                ?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        AlertDialog(
            onDismissRequest = { avatarEditBytes = null },
            title = { Text("Position profile photo") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (croppedPreview != null) Image(
                        bitmap = croppedPreview.asImageBitmap(), contentDescription = "Profile photo crop preview",
                        modifier = Modifier.size(190.dp).clip(RoundedCornerShape(100.dp))
                    )
                    Text("Zoom", style = MaterialTheme.typography.labelLarge)
                    Slider(value = avatarZoom, onValueChange = { avatarZoom = it }, valueRange = 1f..2.5f)
                    Text("Horizontal position", style = MaterialTheme.typography.labelLarge)
                    Slider(value = avatarOffsetX, onValueChange = { avatarOffsetX = it }, valueRange = -1f..1f)
                    Text("Vertical position", style = MaterialTheme.typography.labelLarge)
                    Slider(value = avatarOffsetY, onValueChange = { avatarOffsetY = it }, valueRange = -1f..1f)
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    scope.launch {
                        saving = true
                        runCatching {
                            val bytes = MediaEditor.cropSquareJpeg(sourceAvatarBytes, avatarZoom, avatarOffsetX, avatarOffsetY)
                                ?: error("Crop failed")
                            val url = Imgbb.uploadJpeg(bytes)
                            saveAvatarUrl(url); reload()
                        }.onSuccess {
                            avatarEditBytes = null
                            snackbar.showSnackbar("Profile photo updated.")
                        }.onFailure {
                            snackbar.showSnackbar("We couldn't update your photo. Check your internet.")
                        }
                        saving = false
                    }
                }) { Text("Use photo") }
            },
            dismissButton = { TextButton(onClick = { avatarEditBytes = null }) { Text(tr("Cancel")) } }
        )
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
                        runCatching { saveAvatarUrl(null); reload() }
                            .onSuccess {
                                snackbar.showSnackbar("Profile photo removed.")
                            }
                            .onFailure { snackbar.showSnackbar("We couldn't remove that photo.") }
                    }
                }) { Text(tr("Remove")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemovePhoto = false }) { Text(tr("Cancel")) }
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

/** Renders text as a black-on-white QR bitmap, or null when it cannot be encoded. */
private fun qrBitmap(text: String, sizePx: Int = 640): Bitmap? = runCatching {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        text, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx
    )
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    bmp
}.getOrNull()

@Composable
private fun QrCodeDialog(me: Me, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val payload = "https://holidaylink.org/@${me.username.ifBlank { me.id }}"
    val bitmap = remember(payload) { qrBitmap(payload) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(me.displayName) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Profile QR code",
                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White).padding(8.dp)
                    )
                } else {
                    Text("QR code unavailable.", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "@${me.username.ifBlank { me.id }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { shareProfile(context, me); onDismiss() }) { Text(tr("Share")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Close")) }
        }
    )
}
