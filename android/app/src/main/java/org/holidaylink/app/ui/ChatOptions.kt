@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.holidaylink.app.data.AppSettings

/**
 * The per-conversation controls that live on a chat's info page rather than in its top bar:
 * background, lock and scheduled messages. Shared by the direct-chat profile and group info so
 * both offer exactly the same things.
 */
@Composable
fun ChatExtrasSection(
    chatId: String,
    chatLabel: String,
    onOpenScheduled: () -> Unit,
    /** Group info already offers the background as a quick action, so it passes false. */
    showWallpaper: Boolean = true
) {
    val settings = AppSettings.state
    var wallpaperOpen by remember { mutableStateOf(false) }
    val locked = settings.lockedChats[chatId] == true
    val pending = settings.scheduledMessages.count { it.chatId == chatId }

    SettingsSection("Chat") {
        if (showWallpaper) {
            SettingsNavRow(
                title = "Chat background",
                icon = Icons.Default.Wallpaper,
                subtitle = wallpaperLabel(settings.chatWallpaperPresets[chatId])
            ) { wallpaperOpen = true }
            SettingsDivider()
        }
        SettingsSwitchRow(
            title = "Chat lock",
            icon = Icons.Default.Lock,
            subtitle = if (locked) "Fingerprint, face or PIN needed to open"
            else "This chat opens straight away",
            checked = locked,
            onCheckedChange = { AppSettings.setChatLocked(chatId, it) }
        )
        SettingsDivider()
        SettingsNavRow(
            title = "Scheduled messages",
            icon = Icons.Default.Schedule,
            subtitle = if (pending == 0) "Write now, send later" else "$pending waiting to send",
            onClick = onOpenScheduled
        )
    }

    if (wallpaperOpen) {
        WallpaperSheet(
            current = AppSettings.wallpaperPresetFor(chatId),
            title = "Chat background",
            onPick = { preset -> AppSettings.setChatWallpaperPresetFor(chatId, preset) },
            onDismiss = { wallpaperOpen = false },
            extra = {
                TextButton(onClick = {
                    AppSettings.setChatWallpaperPresetFor(chatId, null)
                    wallpaperOpen = false
                }) { Text("Use the app background") }
            }
        )
    }
}

private fun wallpaperLabel(perChat: String?): String = when {
    perChat == null -> "Following the app background"
    perChat == WALLPAPER_DEFAULT -> "Default design"
    else -> WallpaperPresets.firstOrNull { it.id == perChat }?.label ?: perChat
}

/**
 * Gate in front of a locked conversation.
 *
 * Biometrics first — the prompt appears on its own and the button only re-runs it. The PIN is
 * the last resort and stays hidden until the user asks for it, but it is always reachable so a
 * locked chat can never become unreadable.
 */
@Composable
fun ChatLockScreen(chatLabel: String, onUnlocked: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val settings = AppSettings.state
    val hasPin = settings.appLockPin != null

    var showPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var prompted by remember { mutableStateOf(false) }

    val biometricUsable = remember(activity) {
        val host = activity ?: return@remember false
        BiometricManager.from(host).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BIOMETRIC_SUCCESS
    }

    LaunchedEffect(biometricUsable, prompted) {
        val host = activity ?: return@LaunchedEffect
        if (!biometricUsable || prompted) return@LaunchedEffect
        prompted = true
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Open $chatLabel")
            .setSubtitle("This chat is locked")
        if (hasPin) {
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK).setNegativeButtonText("Use PIN")
        } else {
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        }
        BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (code != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        code != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        error = "Couldn't verify it was you. Try again."
                    }
                }
            }
        ).authenticate(builder.build())
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                chatLabel,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "This chat is locked",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            if (biometricUsable) {
                Button(onClick = { prompted = false; error = "" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock")
                }
                if (hasPin) {
                    TextButton(onClick = { showPin = !showPin }) {
                        Text(if (showPin) "Hide the PIN" else "Use PIN instead")
                    }
                }
            }

            if (hasPin && (showPin || !biometricUsable)) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (pin == settings.appLockPin) onUnlocked()
                        else { error = "That PIN isn't right."; pin = "" }
                    },
                    enabled = pin.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Unlock") }
            } else if (!biometricUsable && !hasPin) {
                Text(
                    "Turn on the app lock in Settings > Security to unlock this chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onCancel) { Text("Back to chats") }
        }
    }
}

private val scheduleDayFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private val scheduleTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

/** Human-readable "when" for a scheduled message. */
fun formatScheduledAt(atMs: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = atMs }
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val day = when {
        calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        calendar.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) -> "Tomorrow"
        else -> scheduleDayFormat.format(calendar.time)
    }
    return "$day at ${scheduleTimeFormat.format(calendar.time)}"
}

/** Next sensible default: tomorrow at 09:00, so scheduling never lands in the past. */
private fun defaultScheduleAt(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 9)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Write a message now and have HolidayLink send it later.
 *
 * The queue lives on this phone and is drained while the app is open, which is stated plainly
 * on the screen rather than promising delivery from a server that has no such job.
 */
@Composable
fun ScheduledMessagesScreen(chatId: String, chatLabel: String, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val settings = AppSettings.state
    val queued = settings.scheduledMessages.filter { it.chatId == chatId }.sortedBy { it.atMs }

    var text by remember { mutableStateOf("") }
    var atMs by remember { mutableStateOf(defaultScheduleAt()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    // SettingsScaffold already provides the single vertical scroll. Nesting another one
    // inside it makes Compose measure the inner scrollable with an infinite max height and
    // throws "Vertically scrollable component was measured with an infinity maximum height
    // constraints" — which closed the app the moment this screen opened.
    SettingsScaffold(title = "Scheduled messages", onBack = onBack, snackbar = snackbar) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            SettingsSection("Write one") {
                Text(
                    "HolidayLink sends it from this phone at the time you pick, so keep the app " +
                        "open (or reopen it) around then.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    label = { Text("Message to $chatLabel") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val calendar = Calendar.getInstance().apply { timeInMillis = atMs }
                    OutlinedButtonRow(
                        icon = Icons.Default.DateRange,
                        label = scheduleDayFormat.format(calendar.time),
                        modifier = Modifier.weight(1f)
                    ) { showDate = true }
                    OutlinedButtonRow(
                        icon = Icons.Default.AccessTime,
                        label = scheduleTimeFormat.format(calendar.time),
                        modifier = Modifier.weight(1f)
                    ) { showTime = true }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (text.isBlank()) return@Button
                        if (atMs <= System.currentTimeMillis()) {
                            atMs = defaultScheduleAt()
                        }
                        AppSettings.addScheduled(chatId, chatLabel, text.trim(), atMs)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Schedule for ${formatScheduledAt(atMs)}")
                }
            }

            SettingsSection(if (queued.isEmpty()) "Nothing waiting" else "Waiting to send") {
                if (queued.isEmpty()) {
                    Text(
                        "Messages you schedule for $chatLabel will be listed here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                queued.forEachIndexed { index, item ->
                    if (index > 0) SettingsDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatScheduledAt(item.atMs),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { AppSettings.removeScheduled(item.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                "Cancel this message",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Every other queue on this phone, so nothing scheduled is ever out of sight.
            val elsewhere = settings.scheduledMessages.filter { it.chatId != chatId }
            if (elsewhere.isNotEmpty()) {
                SettingsSection("Scheduled in other chats") {
                    elsewhere.sortedBy { it.atMs }.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.chatLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    item.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatScheduledAt(item.atMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { AppSettings.removeScheduled(item.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    "Cancel this message",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = atMs
            }.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked -> atMs = combineDateAndTime(picked, atMs) }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val calendar = Calendar.getInstance().apply { timeInMillis = atMs }
        val state = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Send at") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    atMs = Calendar.getInstance().apply {
                        timeInMillis = atMs
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } }
        )
    }
}

/** The date picker returns UTC midnight; the hour and minute stay local. */
private fun combineDateAndTime(utcMidnight: Long, currentAtMs: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    val time = Calendar.getInstance().apply { timeInMillis = currentAtMs }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, time.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
private fun OutlinedButtonRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
