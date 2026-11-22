package org.holidaylink.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.holidaylink.app.config.AppConfig

private const val TAG = "HolidayLinkPerms"

/** True when the OS level "show notifications" permission is granted (or not needed). */
fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The runtime permission(s) needed to read the phone's own photo/video gallery for the
 * in-app "Add status" grid. Android 13+ splits media access into images/video; older
 * versions use the single READ_EXTERNAL_STORAGE permission.
 */
fun mediaGalleryPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun hasMediaGalleryPermission(context: Context): Boolean =
    mediaGalleryPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

fun openAppSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "could not open app settings: ${e.message}")
    }
}

fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

data class NotificationPermissionState(
    val supported: Boolean,
    val granted: Boolean,
    val asked: Boolean,
    val permanentlyDenied: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit
)

/**
 * Android 13+ POST_NOTIFICATIONS state.
 * Nothing is requested automatically on first launch: the caller decides when to ask,
 * after explaining why notifications are useful.
 */
@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val prefs = remember(context) {
        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
    }
    var granted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var asked by remember { mutableStateOf(prefs.getBoolean("notificationsAsked", false)) }
    var rationale by remember {
        val activity = context.findActivity()
        mutableStateOf(
            supported && activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                )
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        asked = true
        rationale = false
        prefs.edit().putBoolean("notificationsAsked", true).apply()
        // A refusal must not break anything else in the app.
        if (!result) Log.i(TAG, "POST_NOTIFICATIONS denied; app continues without notifications")
    }

    return NotificationPermissionState(
        supported = supported,
        granted = granted,
        asked = asked,
        permanentlyDenied = supported && asked && !granted && !rationale,
        request = {
            if (supported) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        openSettings = { openAppSettings(context) }
    )
}

/**
 * Explains the value of notifications before the system prompt appears.
 * Shown once after sign in, and dismissible, so it is never a surprise on first launch.
 */
@Composable
fun NotificationPermissionBanner(
    state: NotificationPermissionState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dismissedKey = "notificationsBannerDismissed"
    var dismissed by remember {
        mutableStateOf(
            context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .getBoolean(dismissedKey, false)
        )
    }

    if (!state.supported || state.granted || dismissed) return

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF63E6BE)
                )
                Text(
                    "  Stay updated on HolidayLink",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Allow notifications to get new messages, event reminders and offers as soon as they happen. " +
                    "HolidayLink never sends notifications you did not ask for.",
                color = Color.LightGray
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = {
                    dismissed = true
                    context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(dismissedKey, true).apply()
                }) { Text("Not now", color = MaterialTheme.colorScheme.secondary) }

                Button(onClick = { state.request() }) { Text("Turn on notifications") }
            }
        }
    }
}

/** True once the OS will no longer defer/kill this app's background work for battery saving. */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Sends the user straight to the system "allow unrestricted battery usage" prompt for this
 * app — the same one-tap dialog WhatsApp shows, rather than a settings page they'd have to
 * navigate themselves. Falls back to the app's battery-usage settings page if the direct
 * dialog isn't available on this OEM's build.
 */
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    try {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "could not open battery optimization prompt: ${e.message}")
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: Exception) {
            Log.w(TAG, "could not open battery optimization settings either: ${e2.message}")
            openAppSettings(context)
        }
    }
}

data class BatteryOptimizationState(
    val supported: Boolean,
    val exempted: Boolean,
    val request: () -> Unit
)

/**
 * Tracks whether this app is exempt from Doze/battery-optimization background limits.
 * Re-checked whenever the app resumes, since the user grants this from a system dialog or
 * Settings and control returns straight back to the app.
 */
@Composable
fun rememberBatteryOptimizationState(): BatteryOptimizationState {
    val context = LocalContext.current
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    var exempted by remember { mutableStateOf(!supported || isIgnoringBatteryOptimizations(context)) }

    // The user grants this from a system dialog (or Settings) that hands control straight
    // back to the app, so re-check on every resume rather than only once at first composition.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        exempted = !supported || isIgnoringBatteryOptimizations(context)
    }

    return BatteryOptimizationState(
        supported = supported,
        exempted = exempted,
        request = { requestIgnoreBatteryOptimizations(context) }
    )
}

/**
 * Explains why the app needs to be exempt from battery optimization — otherwise some phones
 * (Xiaomi/MIUI, Huawei, Samsung, stock Doze) delay or drop message/call notifications while
 * the app is closed, exactly like WhatsApp warns about on those devices. Shown once, and
 * dismissible, so it never blocks using the app.
 */
@Composable
fun BatteryOptimizationBanner(
    state: BatteryOptimizationState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dismissedKey = "batteryOptimizationBannerDismissed"
    var dismissed by remember {
        mutableStateOf(
            context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .getBoolean(dismissedKey, false)
        )
    }

    if (!state.supported || state.exempted || dismissed) return

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF63E6BE)
                )
                Text(
                    "  Don't miss messages when the app is closed",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Some phones pause apps in the background to save battery, which can delay or block " +
                    "new message and call notifications. Allow HolidayLink to run without restriction " +
                    "so notifications arrive right away, even when the app is closed — the same setting " +
                    "WhatsApp asks for.",
                color = Color.LightGray
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = {
                    dismissed = true
                    context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(dismissedKey, true).apply()
                }) { Text("Not now", color = MaterialTheme.colorScheme.secondary) }

                Button(onClick = { state.request() }) { Text("Allow") }
            }
        }
    }
}
