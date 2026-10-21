@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.config.AppConfig
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.data.AutoDownload
import org.holidaylink.app.data.DisappearingDuration
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.CloudSettingsBackup
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.Audience
import org.holidaylink.app.net.DeviceSession
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.FirestorePrefs
import org.holidaylink.app.net.Me
import org.holidaylink.app.net.NotificationSettings
import org.holidaylink.app.net.PrivacySettings
import org.holidaylink.app.net.StatusAudience
import org.holidaylink.app.net.StudyBuddy
import org.holidaylink.app.ui.theme.ChatDensity
import org.holidaylink.app.ui.theme.ThemeMode

/** Standard top bar used by every settings sub-screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    showBack: Boolean = true,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
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
                .verticalScroll(rememberScrollState())
        ) { content() }
    }
}

/** Generic single-choice dialog used by the privacy and appearance pickers. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = {
                                    onSelect(option)
                                    onDismiss()
                                }
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ==================================================================== settings hub

/**
 * Settings landing screen: profile header first, then compact grouped rows.
 * Deliberately not one giant card per setting — rows stay tight like a mature messaging app.
 */
@Composable
fun SettingsHub(
    api: Api,
    me: Me?,
    onOpen: (SettingsRoute) -> Unit,
    onLoggedOut: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    SettingsScaffold("Settings", onBack = onBack ?: {}, snackbar = snackbar, showBack = onBack != null) {
        // -------- profile header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                name = me?.displayName.orEmpty(),
                photo = me?.avatarUrl,
                size = 68.dp,
                onClick = { onOpen(SettingsRoute.Profile) }
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    me?.displayName?.takeIf { it.isNotBlank() } ?: "Your profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    me?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                        ?: me?.phone?.let { org.holidaylink.app.phone.PhoneNumbers.mask(it) }
                        ?: "Not signed in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onOpen(SettingsRoute.Profile) }) {
                Icon(Icons.Default.Person, "Edit profile")
            }
        }

        SettingsSection("Account") {
            SettingsNavRow("Accounts", Icons.Default.Person, "Add or switch HolidayLink accounts", onClick = { onOpen(SettingsRoute.Accounts) })
            SettingsNavRow("Profile", Icons.Default.Person, "Name, photo and About", onClick = { onOpen(SettingsRoute.Profile) })
            SettingsNavRow("Privacy", Icons.Default.Lock, "Last seen, photo, About, status", onClick = { onOpen(SettingsRoute.Privacy) })
            SettingsNavRow("Security", Icons.Default.VerifiedUser, "App lock, devices, blocked", onClick = { onOpen(SettingsRoute.Security) })
            SettingsNavRow("Blocked contacts", Icons.Default.Block, "Manage blocked people", onClick = { onOpen(SettingsRoute.Blocked) })
        }

        SettingsSection("Preferences") {
            SettingsNavRow("Notifications", Icons.Default.Notifications, "Messages, calls, status, AI", onClick = { onOpen(SettingsRoute.Notifications) })
            SettingsNavRow("Appearance", Icons.Default.Palette, "Theme, text size, wallpaper", onClick = { onOpen(SettingsRoute.Appearance) })
            SettingsNavRow("Data and storage", Icons.Default.Storage, "Storage, cache, downloads", onClick = { onOpen(SettingsRoute.DataStorage) })
            SettingsNavRow("Language", Icons.Default.Language, "App language", onClick = { onOpen(SettingsRoute.Language) })
            SettingsNavRow("Chats", Icons.Default.Groups, "Chat wallpaper and defaults", onClick = { onOpen(SettingsRoute.ChatSettings) })
            SettingsNavRow("HolidayLink AI", Icons.Default.AutoAwesome, "Assistant and AI settings", onClick = { onOpen(SettingsRoute.AiSettings) })
        }

        SettingsSection("About") {
            SettingsNavRow("App updates", Icons.Default.CloudDownload, "Download new versions in-app", onClick = { onOpen(SettingsRoute.Update) })
            SettingsNavRow("About HolidayLink", Icons.Default.Info, "Version and what's new", onClick = { onOpen(SettingsRoute.About) })
            SettingsNavRow("Help", Icons.Default.Info, "Get help using HolidayLink", onClick = { onOpen(SettingsRoute.Help) })
            SettingsNavRow("Terms of service", Icons.Default.Info, onClick = { onOpen(SettingsRoute.Terms) })
            SettingsNavRow("Privacy policy", Icons.Default.Lock, onClick = { onOpen(SettingsRoute.PrivacyPolicy) })
        }

        SettingsSection("Danger zone") {
            SettingsNavRow(
                title = "Log out",
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = { confirmLogout = true }
            )
            SettingsNavRow(
                title = "Delete account",
                icon = Icons.Default.DeleteForever,
                subtitle = "Permanently remove your account and data",
                onClick = { confirmDelete = true }
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HolidayLinkLogo(size = 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "HolidayLink 1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(30.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out of HolidayLink?") },
            text = { Text("You'll need your phone number and a new SMS code to sign back in.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLoggedOut()
                }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This permanently removes your profile, chats and messages from " +
                        "HolidayLink. It cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        deleting = true
                        scope.launch {
                            runCatching {
                                val user = FirebaseBackend.currentUser
                                    ?: throw ApiException("Sign in again first.")
                                // Remove the profile doc, then the Auth account.
                                // Chats already shared with others stay for them.
                                FirebaseBackend.db.collection("users").document(user.uid)
                                    .delete().await()
                                user.delete().await()
                            }
                                .onSuccess {
                                    snackbar.showSnackbar("Account deleted.")
                                    onLoggedOut()
                                }
                                .onFailure {
                                    deleting = false
                                    snackbar.showSnackbar(
                                        "We couldn't delete your account. If you signed in a while ago, " +
                                            "sign out and back in, then try again."
                                    )
                                }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ==================================================================== privacy

/**
 * Privacy settings. Every change is written to the server, which is the component that actually
 * enforces it — hiding rows in the UI alone would be meaningless.
 */
@Composable
fun PrivacyScreen(api: Api, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var settings by remember { mutableStateOf<PrivacySettings?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    var picker by remember { mutableStateOf<Pair<String, Audience>?>(null) }
    var statusPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            settings = FirestorePrefs.loadPrivacy()
            error = ""
        } catch (e: Exception) {
            error = "We couldn't load your privacy settings. Check your internet."
        } finally {
            loading = false
        }
    }

    fun push(next: PrivacySettings) {
        val previous = settings
        settings = next
        scope.launch {
            runCatching { FirestorePrefs.savePrivacy(next) }
                .onSuccess {
                    settings = it
                    AppSettings.setReadReceipts(it.readReceipts)
                    AppSettings.setTypingIndicators(it.typingIndicators)
                    AppSettings.setOnlineVisibility(it.lastSeen != Audience.NOBODY)
                    AppSettings.setDisappearing(DisappearingDuration.fromSeconds(it.disappearingSeconds))
                    snackbar.showSnackbar("Saved")
                }
                .onFailure {
                    settings = previous
                    snackbar.showSnackbar("Couldn't save that setting. Check your internet.")
                }
        }
    }

    val current = settings
    SettingsScaffold("Privacy", onBack, snackbar) {
        when {
            loading -> LoadingState(label = "Loading privacy settings…")
            error.isNotBlank() && current == null -> ErrorState(error)
            current == null -> ErrorState("Privacy settings are unavailable.")

            else -> Column(Modifier.padding(bottom = 30.dp)) {
                SettingsSection("Last seen and online") {
                    SettingsValueRow(
                        "Who can see my last seen and online",
                        Icons.Default.Schedule,
                        current.lastSeen.label
                    ) { picker = "lastSeen" to current.lastSeen }
                }

                SettingsSection("Profile photo") {
                    SettingsValueRow(
                        "Who can see my profile photo",
                        Icons.Default.Person,
                        current.profilePhoto.label
                    ) { picker = "profilePhoto" to current.profilePhoto }
                }

                SettingsSection("About") {
                    SettingsValueRow(
                        "Who can see my About",
                        Icons.Default.AlternateEmail,
                        current.about.label
                    ) { picker = "about" to current.about }
                }

                SettingsSection("Status") {
                    SettingsValueRow(
                        "Who can see my status updates",
                        Icons.Default.Phone,
                        current.status.label
                    ) { statusPicker = true }
                }

                SettingsSection("Groups") {
                    SettingsValueRow(
                        "Who can add me to groups",
                        Icons.Default.Groups,
                        current.groupInvites.label
                    ) { picker = "groupInvites" to current.groupInvites }
                }

                SettingsSection("Calls") {
                    SettingsValueRow(
                        "Who can call me",
                        Icons.Default.Phone,
                        current.calls.label
                    ) { picker = "calls" to current.calls }
                }

                SettingsSection("Receipts and typing") {
                    SettingsSwitchRow(
                        title = "Read receipts",
                        icon = Icons.Default.DoneAll,
                        subtitle = "Let others see when you've read their messages",
                        checked = current.readReceipts,
                        onCheckedChange = { push(current.copy(readReceipts = it)) }
                    )
                    SettingsSwitchRow(
                        title = "Typing indicators",
                        icon = Icons.Default.TextFields,
                        subtitle = "Show others when you're typing",
                        checked = current.typingIndicators,
                        onCheckedChange = { push(current.copy(typingIndicators = it)) }
                    )
                }

                SettingsSection("Disappearing messages") {
                    // The radio group below is the control itself, so no extra row is needed.
                    DisappearingChoices(selectedSeconds = current.disappearingSeconds) { chosen ->
                        push(current.copy(disappearingSeconds = chosen.seconds))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "These rules are enforced automatically. Choosing \"Nobody\" " +
                        "means your last seen, photo and About are not sent to other people at all.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                )
            }
        }
    }

    picker?.let { (key, selected) ->
        ChoiceDialog(
            title = "Who can see this?",
            options = Audience.entries.toList(),
            selected = selected,
            label = { it.label },
            onSelect = { chosen ->
                val base = settings ?: return@ChoiceDialog
                val updated = when (key) {
                    "lastSeen" -> base.copy(lastSeen = chosen)
                    "profilePhoto" -> base.copy(profilePhoto = chosen)
                    "about" -> base.copy(about = chosen)
                    "groupInvites" -> base.copy(groupInvites = chosen)
                    "calls" -> base.copy(calls = chosen)
                    else -> base
                }
                push(updated)
            },
            onDismiss = { picker = null }
        )
    }

    if (statusPicker) {
        val base = settings ?: PrivacySettings()
        ChoiceDialog(
            title = "Who can see my status updates?",
            options = StatusAudience.entries.toList(),
            selected = base.status,
            label = { it.label },
            onSelect = { chosen ->
                push(base.copy(status = chosen))
                statusPicker = false
            },
            onDismiss = { statusPicker = false }
        )
    }
}

/**
 * Inline radio group for the disappearing-message timer.
 *
 * [selectedSeconds] is passed in rather than read from [AppSettings] so the tick always reflects
 * the value the server just returned, not a stale local copy.
 */
@Composable
private fun DisappearingChoices(
    selectedSeconds: Long,
    onPick: (DisappearingDuration) -> Unit
) {
    val current = DisappearingDuration.fromSeconds(selectedSeconds)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        DisappearingDuration.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == current,
                        onClick = {
                            AppSettings.setDisappearing(option)
                            onPick(option)
                        }
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = option == current, onClick = null)
                Spacer(Modifier.width(10.dp))
                Text(option.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// ==================================================================== security

@Composable
fun SecurityScreen(api: Api, onBack: () -> Unit, onOpenBlocked: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings = AppSettings.state

    var pinDialog by remember { mutableStateOf(false) }
    var enteringPin by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    var sessions by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }
    var loadingSessions by remember { mutableStateOf(true) }
    var twoFactorEnabled by remember { mutableStateOf(false) }
    var twoFactorLoading by remember { mutableStateOf(true) }
    var twoFactorBusy by remember { mutableStateOf(false) }

    var biometricSupported by remember {
        mutableStateOf(
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        )
    }

    suspend fun loadSessions(): List<DeviceSession> {
        val uid = FirebaseBackend.uid.orEmpty()
        val snap = FirebaseBackend.db.collection("sessions")
            .whereEqualTo("userId", uid).get().await()
        val myDevice = FirebaseBackend.deviceId(context)
        return snap.documents.map { d ->
            DeviceSession(
                id = d.id,
                label = d.getString("deviceLabel") ?: "Device",
                platform = "android",
                createdAt = d.safeTimestamp("createdAt")?.toDate()?.toInstant()?.toString().orEmpty(),
                lastSeenAt = d.safeTimestamp("lastActiveAt")?.toDate()?.toInstant()?.toString(),
                current = d.id == myDevice
            )
        }.sortedByDescending { it.current }
    }

    LaunchedEffect(Unit) {
        sessions = runCatching { loadSessions() }.getOrDefault(emptyList())
        loadingSessions = false
        twoFactorEnabled = runCatching {
            FirebaseBackend.loadProfile(FirebaseBackend.uid.orEmpty())?.twoFactorEnabled == true
        }.getOrDefault(false)
        twoFactorLoading = false
    }

    fun startBiometricPrompt() {
        // androidx.biometric only accepts a FragmentActivity (or Fragment) as its host.
        val activity = context.findActivity() as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            androidx.core.content.ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppSettings.setAppLockEnabled(true)
                    AppSettings.setBiometricLockEnabled(true)
                    scope.launch { snackbar.showSnackbar("App lock is on.") }
                }
            }
        )
        // Only biometrics is used here, and the device screen lock is deliberately excluded so a
        // negative button stays legal. Enabling app lock for the first time needs a real sensor.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock HolidayLink")
            .setSubtitle("Confirm it's you to turn on app lock")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info)
    }

    SettingsScaffold("Security", onBack, snackbar) {
        Column(Modifier.padding(bottom = 30.dp)) {
            SettingsSection("App lock") {
                SettingsSwitchRow(
                    title = "App lock",
                    icon = Icons.Default.Lock,
                    subtitle = "Ask for a PIN or biometrics when HolidayLink opens",
                    checked = settings.appLockEnabled,
                    onCheckedChange = { want ->
                        if (want) {
                            if (settings.appLockPin == null) enteringPin = true else AppSettings.setAppLockEnabled(true)
                        } else {
                            AppSettings.setAppLockEnabled(false)
                            AppSettings.setBiometricLockEnabled(false)
                        }
                    }
                )
                SettingsSwitchRow(
                    title = "Biometric unlock",
                    icon = Icons.Default.Fingerprint,
                    subtitle = if (biometricSupported) {
                        "Use fingerprint or face unlock"
                    } else {
                        "Not available on this device"
                    },
                    checked = settings.biometricLockEnabled,
                    enabled = biometricSupported && settings.appLockEnabled,
                    onCheckedChange = { want ->
                        if (want) startBiometricPrompt() else AppSettings.setBiometricLockEnabled(false)
                    }
                )
                SettingsNavRow(
                    title = if (settings.appLockPin == null) "Set a PIN" else "Change PIN",
                    icon = Icons.Default.Lock,
                    onClick = { enteringPin = true }
                )
            }

            SettingsSection("Two-step login") {
                SettingsSwitchRow(
                    title = "Approve new sign-ins",
                    icon = Icons.Default.VerifiedUser,
                    subtitle = if (twoFactorEnabled) "On. New devices ask approval on a signed-in device." else "Off. Any verified SMS code signs in directly.",
                    checked = twoFactorEnabled,
                    enabled = !twoFactorLoading && !twoFactorBusy,
                    onCheckedChange = { want ->
                        twoFactorBusy = true
                        scope.launch {
                            runCatching {
                                val uid = FirebaseBackend.uid
                                    ?: throw ApiException("Sign in again first.")
                                FirebaseBackend.db.collection("users").document(uid)
                                    .update("twoFactorEnabled", want).await()
                            }
                                .onSuccess {
                                    twoFactorEnabled = want
                                    snackbar.showSnackbar(if (want) "2FA approval is on." else "2FA approval is off.")
                                }
                                .onFailure { snackbar.showSnackbar("We couldn't change that right now.") }
                            twoFactorBusy = false
                        }
                    }
                )
            }

            SettingsSection("Devices") {
                if (loadingSessions) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading devices…")
                    }
                } else if (sessions.isEmpty()) {
                    SettingsNavRow(
                        title = "No other devices",
                        icon = Icons.Default.Devices,
                        subtitle = "Only this device is signed in",
                        onClick = {}
                    )
                } else {
                    sessions.forEach { session ->
                        SettingsNavRow(
                            title = if (session.current) "${session.label} (this device)" else session.label,
                            icon = Icons.Default.Devices,
                            subtitle = session.lastSeenAt?.let { "Last active ${friendlyWhen(it)}" }
                                ?: session.platform,
                            onClick = {}
                        )
                    }
                    SettingsNavRow(
                        title = "Log out other devices",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val uid = FirebaseBackend.uid.orEmpty()
                                    val mine = FirebaseBackend.deviceId(context)
                                    val others = FirebaseBackend.db.collection("sessions")
                                        .whereEqualTo("userId", uid).get().await()
                                    val batch = FirebaseBackend.db.batch()
                                    others.documents.filter { it.id != mine }.forEach { batch.delete(it.reference) }
                                    batch.commit().await()
                                }
                                    .onSuccess {
                                        snackbar.showSnackbar("Other devices signed out.")
                                        sessions = runCatching { loadSessions() }.getOrDefault(emptyList())
                                    }
                                    .onFailure { snackbar.showSnackbar("We couldn't sign out other devices.") }
                            }
                        }
                    )
                }
            }

            SettingsSection("Safety") {
                SettingsNavRow(
                    "Blocked contacts",
                    Icons.Default.Block,
                    subtitle = "People who can't message or call you",
                    onClick = onOpenBlocked
                )
            }

            SettingsSection("Encryption note") {
                Text(
                    "Messages sync through Firebase like the website, so they are readable " +
                        "there too. Your phone also keeps an offline copy for when the " +
                        "network is gone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (enteringPin) {
        AlertDialog(
            onDismissRequest = { enteringPin = false },
            title = { Text(if (settings.appLockPin == null) "Create a 4-digit PIN" else "Change your PIN") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.filter { c -> c.isDigit() }.take(4)
                            pinError = ""
                        },
                        label = { Text("PIN") },
                        singleLine = true,
                        isError = pinError.isNotBlank(),
                        supportingText = { if (pinError.isNotBlank()) Text(pinError) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length != 4) {
                        pinError = "Enter exactly 4 digits."
                        return@TextButton
                    }
                    newPin = pinInput
                    pinInput = ""
                        if (settings.appLockPin == null) {
                            pinDialog = true
                        } else {
                            AppSettings.setAppLockPin(newPin)
                            enteringPin = false
                            scope.launch { snackbar.showSnackbar("PIN updated.") }
                        }
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { enteringPin = false }) { Text("Cancel") } }
        )
    }

    if (pinDialog) {
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("Confirm your PIN") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Repeat PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput == newPin) {
                        AppSettings.setAppLockPin(newPin)
                        AppSettings.setAppLockEnabled(true)
                        pinDialog = false
                        enteringPin = false
                        pinInput = ""
                        scope.launch { snackbar.showSnackbar("App lock is on.") }
                    } else {
                        pinError = "PINs didn't match."
                        pinDialog = false
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("Cancel") } }
        )
    }
}

// ==================================================================== blocked

@Composable
fun BlockedScreen(api: Api, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var blocked by remember { mutableStateOf<List<org.holidaylink.app.net.User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }

    suspend fun loadBlocked(): List<org.holidaylink.app.net.User> {
        val uid = FirebaseBackend.uid.orEmpty()
        @Suppress("UNCHECKED_CAST")
        val ids = (FirebaseBackend.db.collection("users").document(uid).get().await()
            .get("blocked") as? List<String>).orEmpty()
        return ids.mapNotNull { id ->
            runCatching {
                val d = FirebaseBackend.db.collection("users").document(id).get().await()
                if (!d.exists()) return@runCatching null
                org.holidaylink.app.net.User(
                    id = d.id,
                    displayName = d.getString("name")?.takeIf { it.isNotBlank() } ?: "HolidayLink user",
                    username = d.getString("username").orEmpty(),
                    avatarUrl = d.getString("profilePic")
                )
            }.getOrNull()
        }
    }

    LaunchedEffect(reload) {
        loading = true
        try {
            blocked = loadBlocked()
            error = ""
        } catch (e: Exception) {
            error = "We couldn't load your blocked list. Check your internet."
        } finally {
            loading = false
        }
    }

    SettingsScaffold("Blocked contacts", onBack, snackbar) {
        when {
            loading -> LoadingState(label = "Loading blocked contacts…")
            error.isNotBlank() -> ErrorState(error) { reload++ }
            blocked.isEmpty() -> EmptyState(
                icon = Icons.Default.Block,
                title = "No blocked contacts",
                subtitle = "People you block can't message you or see your profile details."
            )

            else -> Column(Modifier.padding(bottom = 30.dp)) {
                blocked.forEach { user ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(user.displayName, size = 46.dp, photo = user.avatarUrl)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.bodyLarge)
                            if (user.username.isNotBlank()) {
                                Text(
                                    "@${user.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        FirebaseBackend.db.collection("users")
                                            .document(FirebaseBackend.uid.orEmpty())
                                            .update(
                                                "blocked",
                                                com.google.firebase.firestore.FieldValue.arrayRemove(user.id)
                                            ).await()
                                    }
                                        .onSuccess {
                                            snackbar.showSnackbar("${user.displayName} unblocked.")
                                            reload++
                                        }
                                        .onFailure { snackbar.showSnackbar("Couldn't unblock. Check your internet.") }
                                }
                            }
                        ) { Text("Unblock") }
                    }
                }
            }
        }
    }
}

// ==================================================================== notifications

@Composable
fun NotificationsSettingsScreen(api: Api, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var settings by remember { mutableStateOf<NotificationSettings?>(null) }
    var loading by remember { mutableStateOf(true) }
    var osAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }

    LaunchedEffect(Unit) {
        settings = runCatching { FirestorePrefs.loadNotif() }.getOrNull()
        loading = false
    }

    fun push(next: NotificationSettings) {
        val previous = settings
        settings = next
        scope.launch {
            runCatching { FirestorePrefs.saveNotif(next) }
                .onSuccess { settings = it; snackbar.showSnackbar("Saved") }
                .onFailure {
                    settings = previous
                    snackbar.showSnackbar("Couldn't save that setting. Check your internet.")
                }
        }
    }

    SettingsScaffold("Notifications", onBack, snackbar) {
        val current = settings ?: NotificationSettings()
        Column(Modifier.padding(bottom = 30.dp)) {
            if (!osAllowed) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Android is blocking HolidayLink notifications.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { openAppSettings(context) }) {
                        Text("Open Android notification settings")
                    }
                }
            }

            SettingsSection("Alerts") {
                SettingsSwitchRow(
                    title = "Message notifications",
                    icon = Icons.Default.NotificationsActive,
                    checked = current.messages,
                    onCheckedChange = { push(current.copy(messages = it)) }
                )
                SettingsSwitchRow(
                    title = "Call notifications",
                    icon = Icons.Default.Phone,
                    checked = current.calls,
                    onCheckedChange = { push(current.copy(calls = it)) }
                )
                SettingsSwitchRow(
                    title = "Status notifications",
                    icon = Icons.Default.Phone,
                    checked = current.status,
                    onCheckedChange = { push(current.copy(status = it)) }
                )
                SettingsSwitchRow(
                    title = "HolidayLink AI notifications",
                    icon = Icons.Default.AutoAwesome,
                    checked = current.ai,
                    onCheckedChange = { push(current.copy(ai = it)) }
                )
            }

            SettingsSection("Sound and vibration") {
                SettingsSwitchRow(
                    title = "Sound",
                    icon = Icons.Default.NotificationsActive,
                    checked = current.sound,
                    onCheckedChange = { push(current.copy(sound = it)) }
                )
                SettingsSwitchRow(
                    title = "Vibration",
                    icon = Icons.Default.Phone,
                    checked = current.vibration,
                    onCheckedChange = { push(current.copy(vibration = it)) }
                )
                SettingsSwitchRow(
                    title = "Show message preview",
                    icon = Icons.Default.Notifications,
                    subtitle = "When off, alerts say \"New message\" without the text",
                    checked = current.preview,
                    onCheckedChange = { push(current.copy(preview = it)) }
                )
            }

            SettingsSection("Per-chat controls") {
                SettingsNavRow(
                    title = "Mute individual chats",
                    icon = Icons.Default.NotificationsOff,
                    subtitle = "Open a chat, then ⋮ → Mute notifications",
                    onClick = { scope.launch { snackbar.showSnackbar("Open any chat to mute it.") } }
                )
            }
        }
    }
}

// ==================================================================== appearance

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings = AppSettings.state

    var themePicker by remember { mutableStateOf(false) }
    var densityPicker by remember { mutableStateOf(false) }
    var fontScale by remember { mutableFloatStateOf(settings.fontScale) }
    var wallpaperGallery by remember { mutableStateOf(false) }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            AppSettings.setChatWallpaper(uri.toString())
            persistUriPermission(context, uri)
            scope.launch { snackbar.showSnackbar("Chat wallpaper updated.") }
        }
    }

    SettingsScaffold("Appearance", onBack, snackbar) {
        Column(Modifier.padding(bottom = 30.dp)) {
            SettingsSection("Theme") {
                SettingsValueRow(
                    title = "Theme",
                    icon = Icons.Default.DarkMode,
                    value = settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }
                ) { themePicker = true }
                Text(
                    "HolidayLink follows the system setting when you choose System.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            SettingsSection("Text and layout") {
                SettingsValueRow(
                    title = "Chat density",
                    icon = Icons.Default.TextFields,
                    value = settings.chatDensity.name.lowercase().replaceFirstChar { it.uppercase() }
                ) { densityPicker = true }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Text size", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${(fontScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = fontScale,
                        onValueChange = { fontScale = it },
                        onValueChangeFinished = {
                            AppSettings.setFontScale(fontScale)
                            scope.launch { snackbar.showSnackbar("Text size updated.") }
                        },
                        valueRange = 0.85f..1.45f,
                        steps = 5
                    )
                }
                SettingsSwitchRow(
                    title = "Animations",
                    icon = Icons.Default.Brightness6,
                    subtitle = "Turn off to save battery",
                    checked = settings.animationsEnabled,
                    onCheckedChange = { AppSettings.setAnimations(it) }
                )
            }

            SettingsSection("Chat wallpaper") {
                SettingsNavRow(
                    title = "Chat backgrounds",
                    icon = Icons.Default.Palette,
                    onClick = { wallpaperGallery = true }
                )
                SettingsNavRow(
                    title = "Set chat wallpaper",
                    icon = Icons.Default.Palette,
                    onClick = {
                        wallpaperPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                SettingsSwitchRow(
                    title = "Solid colour wallpaper",
                    icon = Icons.Default.Palette,
                    subtitle = "Use a plain background instead of a photo",
                    checked = settings.chatWallpaperSolid,
                    onCheckedChange = { AppSettings.setChatWallpaperSolid(it) }
                )
                if (settings.chatWallpaper != null) {
                    SettingsNavRow(
                        title = "Remove wallpaper",
                        icon = Icons.Default.CleaningServices,
                        onClick = { AppSettings.setChatWallpaper(null) }
                    )
                }
                Text(
                    "The background chosen here is the default for every chat. " +
                        "Any conversation can have its own background — open the chat and " +
                        "use the ⋮ menu.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            SettingsSection("Preview") {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(14.dp)
                    ) {
                        Text(
                            "This is how your own messages will look at ${(fontScale * 100).toInt()}% " +
                                "text size.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    if (wallpaperGallery) {
        WallpaperSheet(
            current = settings.chatWallpaperPreset,
            title = "Chat backgrounds",
            onPick = { preset -> AppSettings.setChatWallpaperPreset(preset) },
            onDismiss = { wallpaperGallery = false },
            extra = {
                Text(
                    "This is the default for every chat. A single chat can have its own " +
                        "background from the ⋮ menu inside the conversation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }

    if (themePicker) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.toList(),
            selected = settings.themeMode,
            label = {
                when (it) {
                    ThemeMode.SYSTEM -> "System default"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
            },
            onSelect = { AppSettings.setThemeMode(it) },
            onDismiss = { themePicker = false }
        )
    }

    if (densityPicker) {
        ChoiceDialog(
            title = "Chat density",
            options = ChatDensity.entries.toList(),
            selected = settings.chatDensity,
            label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            onSelect = { AppSettings.setChatDensity(it) },
            onDismiss = { densityPicker = false }
        )
    }
}

private fun persistUriPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

// ==================================================================== data & storage

@Composable
fun DataStorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings = AppSettings.state

    var cacheBytes by remember { mutableStateOf(0L) }
    var clearing by remember { mutableStateOf(false) }
    var photosPicker by remember { mutableStateOf(false) }
    var videosPicker by remember { mutableStateOf(false) }
    var docsPicker by remember { mutableStateOf(false) }
    var backupEmail by remember { mutableStateOf("") }
    var backupCode by remember { mutableStateOf("") }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupEmailVerified by remember { mutableStateOf(false) }
    var backupEmailCodeSent by remember { mutableStateOf(false) }
    var cloudBackupBusy by remember { mutableStateOf(false) }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val json = AppSettings.exportSettingsBackup()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Could not open the selected backup file")
                }
            }.onSuccess {
                snackbar.showSnackbar("Settings backup saved. Chats and media are not included.")
            }.onFailure {
                snackbar.showSnackbar("Backup failed: ${it.message ?: "unknown error"}")
            }
        }
    }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read the selected backup file")
                }
                AppSettings.importSettingsBackup(json)
            }.onSuccess {
                snackbar.showSnackbar("Settings restored. App-lock security must be configured again if needed.")
            }.onFailure {
                snackbar.showSnackbar("Restore failed: ${it.message ?: "invalid backup"}")
            }
        }
    }

    LaunchedEffect(Unit) {
        cacheBytes = directorySize(context.cacheDir)
        runCatching { CloudSettingsBackup.emailStatus(context) }.onSuccess { (verified, email) ->
            backupEmailVerified = verified
            if (verified) backupEmail = email
        }
    }

    SettingsScaffold("Data and storage", onBack, snackbar) {
        Column(Modifier.padding(bottom = 30.dp)) {
            SettingsSection("Storage") {
                SettingsNavRow(
                    title = "Cache",
                    icon = Icons.Default.CleaningServices,
                    subtitle = "${formatBytes(cacheBytes)} used",
                    onClick = {
                        if (clearing) return@SettingsNavRow
                        clearing = true
                        scope.launch {
                            val freed = clearCache(context)
                            cacheBytes = directorySize(context.cacheDir)
                            clearing = false
                            snackbar.showSnackbar(
                                "Freed ${formatBytes(freed)}. No messages were deleted."
                            )
                        }
                    }
                )
            }

            SettingsSection("Backup and restore") {
                SettingsNavRow(
                    title = "Create settings backup",
                    icon = Icons.Default.Storage,
                    subtitle = "Save preferences to a JSON file on your device",
                    onClick = { exportBackup.launch("holidaylink-settings-backup.json") }
                )
                SettingsNavRow(
                    title = "Restore settings backup",
                    icon = Icons.Default.CloudDownload,
                    subtitle = "Restore preferences from a saved JSON file",
                    onClick = { importBackup.launch(arrayOf("application/json", "text/*")) }
                )
                Text(
                    "This backup includes app preferences such as theme, chat display, and notification options. It does not back up chat messages, photos, videos, account credentials, or the app-lock PIN.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                )
            }

            SettingsSection("Encrypted cloud backup and email recovery") {
                Text(
                    "Your settings backup is encrypted on this phone before upload. The server stores ciphertext only. Set a backup email and verify it first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                )
                if (!backupEmailVerified) {
                    androidx.compose.material3.OutlinedTextField(
                        value = backupEmail, onValueChange = { backupEmail = it.take(254) },
                        label = { Text("Recovery email") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    )
                    Button(
                        enabled = !cloudBackupBusy && backupEmail.contains("@") && backupEmail.contains("."),
                        onClick = {
                            scope.launch {
                                cloudBackupBusy = true
                                runCatching { CloudSettingsBackup.startEmailVerification(context, backupEmail) }
                                    .onSuccess { backupEmailCodeSent = true; snackbar.showSnackbar("Verification code sent. Check your email.") }
                                    .onFailure { snackbar.showSnackbar(it.message ?: "Could not send verification email.") }
                                cloudBackupBusy = false
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                    ) { Text(if (cloudBackupBusy) "Please wait…" else "Send verification code") }
                    if (backupEmailCodeSent) {
                        androidx.compose.material3.OutlinedTextField(
                            value = backupCode, onValueChange = { backupCode = it.filter(Char::isDigit).take(6) },
                            label = { Text("6-digit email code") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        )
                        OutlinedButton(
                            enabled = !cloudBackupBusy && backupCode.length == 6,
                            onClick = {
                                scope.launch {
                                    cloudBackupBusy = true
                                    runCatching { CloudSettingsBackup.verifyEmail(context, backupCode) }
                                        .onSuccess { email -> backupEmail = email; backupEmailVerified = true; backupEmailCodeSent = false; backupCode = ""; snackbar.showSnackbar("Recovery email verified.") }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "Code verification failed.") }
                                    cloudBackupBusy = false
                                }
                            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                        ) { Text("Verify email") }
                    }
                } else {
                    Text("Verified recovery email: $backupEmail", modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { backupEmailVerified = false; backupEmailCodeSent = false; backupCode = "" }) { Text("Change recovery email") }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = backupPassphrase, onValueChange = { backupPassphrase = it.take(128) },
                    label = { Text("Backup passphrase (8+ characters)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                )
                Button(
                    enabled = backupEmailVerified && !cloudBackupBusy && backupPassphrase.length >= 8,
                    onClick = {
                        scope.launch {
                            cloudBackupBusy = true
                            runCatching { CloudSettingsBackup.save(context, backupPassphrase.toCharArray()) }
                                .onSuccess { snackbar.showSnackbar("Encrypted cloud settings backup saved.") }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Cloud backup failed.") }
                            cloudBackupBusy = false
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                ) { Text("Back up settings to cloud") }
                OutlinedButton(
                    enabled = backupEmailVerified && !cloudBackupBusy && backupPassphrase.length >= 8,
                    onClick = {
                        scope.launch {
                            cloudBackupBusy = true
                            runCatching { CloudSettingsBackup.restore(context, backupPassphrase.toCharArray()) }
                                .onSuccess { snackbar.showSnackbar("Cloud settings backup restored.") }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Cloud restore failed.") }
                            cloudBackupBusy = false
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                ) { Text("Restore settings from cloud") }
                Text(
                    "Keep this passphrase safe. It is never uploaded or emailed; if you lose it, HolidayLink cannot decrypt the backup. This backs up app settings only, not chats, media, credentials, or the app-lock PIN.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                )
            }

            SettingsSection("Media auto-download") {
                SettingsValueRow("Photos", Icons.Default.CloudDownload, settings.autoDownloadPhotos.label()) {
                    photosPicker = true
                }
                SettingsValueRow("Videos", Icons.Default.CloudDownload, settings.autoDownloadVideos.label()) {
                    videosPicker = true
                }
                SettingsValueRow("Documents", Icons.Default.CloudDownload, settings.autoDownloadDocuments.label()) {
                    docsPicker = true
                }
            }

            SettingsSection("Saving and data") {
                SettingsSwitchRow(
                    title = "Save media to gallery",
                    icon = Icons.Default.Storage,
                    subtitle = "Keep a copy of photos you receive",
                    checked = settings.saveMediaToGallery,
                    onCheckedChange = { AppSettings.setSaveMediaToGallery(it) }
                )
                SettingsSwitchRow(
                    title = "Data saver",
                    icon = Icons.Default.Storage,
                    subtitle = "Avoid pre-loading media on mobile networks",
                    checked = settings.dataSaver,
                    onCheckedChange = { AppSettings.setDataSaver(it) }
                )
            }

            SettingsSection("Reset") {
                SettingsNavRow(
                    title = "Reset appearance and local settings",
                    icon = Icons.Default.CleaningServices,
                    onClick = {
                        AppSettings.clearLocalCache()
                        scope.launch { snackbar.showSnackbar("Local settings cleared. Your account is untouched.") }
                    }
                )
            }

            Text(
                "Clearing cache only removes temporary files such as decoded images and voice " +
                    "recordings. Chats and messages live on the server and are never touched.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }

    photosPicker.let { open ->
        if (open) {
            ChoiceDialog(
                title = "Auto-download photos",
                options = AutoDownload.entries.toList(),
                selected = settings.autoDownloadPhotos,
                label = { it.label() },
                onSelect = { AppSettings.setAutoDownloadPhotos(it) },
                onDismiss = { photosPicker = false }
            )
        }
    }
    if (videosPicker) {
        ChoiceDialog(
            title = "Auto-download videos",
            options = AutoDownload.entries.toList(),
            selected = settings.autoDownloadVideos,
            label = { it.label() },
            onSelect = { AppSettings.setAutoDownloadVideos(it) },
            onDismiss = { videosPicker = false }
        )
    }
    if (docsPicker) {
        ChoiceDialog(
            title = "Auto-download documents",
            options = AutoDownload.entries.toList(),
            selected = settings.autoDownloadDocuments,
            label = { it.label() },
            onSelect = { AppSettings.setAutoDownloadDocuments(it) },
            onDismiss = { docsPicker = false }
        )
    }
}

private fun AutoDownload.label(): String = when (this) {
    AutoDownload.ALWAYS -> "Always"
    AutoDownload.WIFI_ONLY -> "On Wi-Fi"
    AutoDownload.NEVER -> "Never"
}

private fun directorySize(dir: java.io.File): Long =
    if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

/** Deletes cached files only. Never touches databases, shared prefs or server data. */
private fun clearCache(context: Context): Long {
    var freed = 0L
    runCatching {
        context.cacheDir.listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                freed += directorySize(entry)
                entry.deleteRecursively()
            } else {
                freed += entry.length()
                entry.delete()
            }
        }
    }
    return freed
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

// ==================================================================== language

@Composable
fun ChatSettingsScreen(api: Api, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings = AppSettings.state

    SettingsScaffold("Chats", onBack, snackbar) {
        Column(Modifier.padding(bottom = 30.dp)) {
            SettingsSection("Message behaviour") {
                SettingsSwitchRow(
                    title = "Read receipts",
                    icon = Icons.Default.DoneAll,
                    subtitle = "Let other people see when you've read their messages",
                    checked = settings.readReceipts,
                    onCheckedChange = {
                        AppSettings.setReadReceipts(it)
                        scope.launch {
                            runCatching {
                                val current = FirestorePrefs.loadPrivacy()
                                FirestorePrefs.savePrivacy(current.copy(readReceipts = it))
                            }.onFailure {
                                snackbar.showSnackbar("Saved on this device only. Check your internet.")
                            }
                        }
                    }
                )
                SettingsSwitchRow(
                    title = "Typing indicators",
                    icon = Icons.Default.TextFields,
                    subtitle = "Show others when you're typing",
                    checked = settings.typingIndicators,
                    onCheckedChange = {
                        AppSettings.setTypingIndicators(it)
                        scope.launch {
                            runCatching {
                                val current = FirestorePrefs.loadPrivacy()
                                FirestorePrefs.savePrivacy(current.copy(typingIndicators = it))
                            }.onFailure {
                                snackbar.showSnackbar("Saved on this device only. Check your internet.")
                            }
                        }
                    }
                )
            }

            SettingsSection("Default disappearing timer") {
                DisappearingChoices(selectedSeconds = settings.disappearing.seconds) { chosen ->
                    AppSettings.setDisappearing(chosen)
                    scope.launch {
                        runCatching {
                            val current = FirestorePrefs.loadPrivacy()
                            FirestorePrefs.savePrivacy(current.copy(disappearingSeconds = chosen.seconds))
                        }.onSuccess {
                            snackbar.showSnackbar("New messages now disappear after ${chosen.label.lowercase()}.")
                        }.onFailure {
                            snackbar.showSnackbar("Saved on this device only. Check your internet.")
                        }
                    }
                }
            }

            SettingsSection("Media") {
                SettingsSwitchRow(
                    title = "Save media to gallery",
                    icon = Icons.Default.Storage,
                    subtitle = "Keep a copy of photos and videos you receive",
                    checked = settings.saveMediaToGallery,
                    onCheckedChange = { AppSettings.setSaveMediaToGallery(it) }
                )
                SettingsSwitchRow(
                    title = "Data saver",
                    icon = Icons.Default.CloudDownload,
                    subtitle = "Don't load media automatically on mobile data",
                    checked = settings.dataSaver,
                    onCheckedChange = { AppSettings.setDataSaver(it) }
                )
            }

            Text(
                "Per-chat controls — mute, pin, block and clear cache — live in the chat's own " +
                    "info sheet (tap ⋮ at the top of a conversation).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val settings = AppSettings.state

    SettingsScaffold("Language", onBack, snackbar) {
        Column(Modifier.padding(bottom = 30.dp)) {
            SettingsSection("App language") {
                LanguageOption("English", "en", settings.language)
                LanguageOption("Kiswahili", "sw", settings.language, available = false)
                LanguageOption("Luganda", "lg", settings.language, available = false)
                LanguageOption("Runyankole", "rn", settings.language, available = false)
            }
            Text(
                "English is fully translated today. The other languages are prepared and will " +
                    "switch on as soon as their translations land — no rebuild needed.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    tag: String,
    current: String,
    available: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = current == tag,
                enabled = available,
                onClick = { AppSettings.setLanguage(tag) }
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (available) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            }
        )
        if (!available) {
            Text(
                "Coming soon",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            RadioButton(selected = current == tag, onClick = null)
        }
    }
}

// ==================================================================== AI settings

@Composable
fun AiSettingsScreen(api: Api, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings = AppSettings.state

    var clearing by remember { mutableStateOf(false) }

    val styles = listOf("concise" to "Concise", "balanced" to "Balanced", "detailed" to "Detailed")

    SettingsScaffold("HolidayLink AI", onBack, snackbar) {
        Column(Modifier.padding(bottom = 30.dp)) {
            SettingsSection("Assistant") {
                SettingsSwitchRow(
                    title = "Enable HolidayLink AI",
                    icon = Icons.Default.AutoAwesome,
                    subtitle = "Show the AI tab and allow AI chat",
                    checked = settings.aiEnabled,
                    onCheckedChange = { AppSettings.setAiEnabled(it) }
                )
                SettingsSwitchRow(
                    title = "Save AI chats",
                    icon = Icons.Default.Groups,
                    subtitle = "Off keeps new AI chats on this device only",
                    checked = settings.saveAiChats,
                    onCheckedChange = { AppSettings.setSaveAiChats(it) }
                )
            }

            SettingsSection("Response style") {
                styles.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = settings.aiResponseStyle == value,
                                onClick = { AppSettings.setAiResponseStyle(value) }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.aiResponseStyle == value, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    "Applies to the next answer from HolidayLink AI. Saved chats keep the " +
                        "style they were written with.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            SettingsSection("History") {
                SettingsNavRow(
                    title = "Clear AI history",
                    icon = Icons.Default.DeleteForever,
                    subtitle = "Delete the HolidayLink AI chat on this device",
                    onClick = {
                        if (clearing) return@SettingsNavRow
                        clearing = true
                        scope.launch {
                            runCatching {
                                StudyBuddy.clearHistory(
                                    context,
                                    FirebaseBackend.uid.orEmpty()
                                )
                            }
                                .onSuccess { snackbar.showSnackbar("AI history cleared.") }
                                .onFailure { snackbar.showSnackbar("We couldn't clear the AI history.") }
                            clearing = false
                        }
                    }
                )
            }

            SettingsSection("About HolidayLink AI") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        "HolidayLink AI is the built-in assistant inside HolidayLink. It can " +
                            "help with school work, homework, explanations, revision, quizzes, " +
                            "summaries, planning and how to use HolidayLink.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The assistant runs on HolidayLink's own servers. Google Gemini " +
                            "credentials are never sent to this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==================================================================== about / help

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    SettingsScaffold("About HolidayLink", onBack, snackbar) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HolidayLinkLogo(size = 76.dp)
            Spacer(Modifier.height(14.dp))
            Text("HolidayLink", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Version ${org.holidaylink.app.BuildConfig.VERSION_NAME} (${org.holidaylink.app.BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "HolidayLink is a messaging and study app built around your phone number. " +
                    "Chats, calls, contacts and HolidayLink AI all live in one place.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        SettingsSection("What's inside") {
            SettingsNavRow("Messaging", Icons.Default.Groups, "Direct and group chats", onClick = {})
            SettingsNavRow("Calls", Icons.Default.Phone, "Call history and details", onClick = {})
            SettingsNavRow("Status", Icons.Default.AlternateEmail, "Share text, photo and video updates", onClick = {})
            SettingsNavRow("HolidayLink AI", Icons.Default.AutoAwesome, "Your study assistant", onClick = {})
        }
        SettingsSection("Honest limits") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    "• Text messages use end-to-end encryption; attachments are not yet E2EE.\n" +
                        "• Calls use WebRTC; some mobile networks may need a TURN relay in future.\n" +
                        "• My QR code and in-app camera capture are not implemented yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun SimpleInfoScreen(title: String, body: String, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    SettingsScaffold(title, onBack, snackbar) {
        Column(Modifier.padding(22.dp)) {
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(30.dp))
    }
}

// ==================================================================== server settings

@Composable
fun ServerSettingsScreen(api: Api, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var input by remember {
        mutableStateOf(org.holidaylink.app.config.AppConfig.resolve(context))
    }
    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    SettingsScaffold("Backend address", onBack, snackbar) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Point the app at your HolidayLink server. Use https:// in production.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = input,
                onValueChange = { input = it; status = "" },
                label = { Text("API base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Build default: ${org.holidaylink.app.config.AppConfig.buildTimeBaseUrl}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        org.holidaylink.app.config.AppConfig.setBaseUrl(context, input)
                        input = org.holidaylink.app.config.AppConfig.resolve(context)
                        status = "Saved."
                    },
                    enabled = input.isNotBlank()
                ) { Text("Save") }
                OutlinedButton(
                    onClick = {
                        if (testing) return@OutlinedButton
                        testing = true
                        status = ""
                        scope.launch {
                            val ok = runCatching {
                                val uid = FirebaseBackend.uid.orEmpty()
                                if (uid.isBlank()) throw ApiException("Not signed in.")
                                FirebaseBackend.db.collection("users").document(uid).get().await()
                            }.isSuccess
                            status = if (ok) "Connected to HolidayLink."
                            else "Not reachable. Check your internet."
                            testing = false
                        }
                    },
                    enabled = !testing
                ) { Text(if (testing) "Testing…" else "Test connection") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                org.holidaylink.app.config.AppConfig.clearBaseUrl(context)
                input = org.holidaylink.app.config.AppConfig.resolve(context)
                status = "Reset to build default."
            }) { Text("Reset to default") }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
