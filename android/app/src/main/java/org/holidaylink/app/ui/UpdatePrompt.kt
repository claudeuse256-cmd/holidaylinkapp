package org.holidaylink.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.holidaylink.app.BuildConfig
import org.holidaylink.app.net.AppUpdater
import java.io.File

/**
 * Startup update gate:
 * - shows a new version immediately when available
 * - becomes mandatory (non-dismissible) 24 hours after release/publish
 */
@Composable
fun StartupUpdateGate() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdater.Update?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        update = runCatching { AppUpdater.check(context) }.getOrNull()
        checked = true
    }

    val found = update ?: return
    if (dismissed && !found.isMandatory()) return

    val mandatory = found.isMandatory()
    if (mandatory) {
        MandatoryUpdateScreen(update = found)
    } else {
        OptionalUpdateDialog(
            update = found,
            onDismiss = { dismissed = true }
        )
    }
}

@Composable
private fun OptionalUpdateDialog(update: AppUpdater.Update, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var readyFile by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text("Update available") },
        text = {
            Column {
                Text("HolidayLink ${update.versionName} is available.")
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(update.notes.trim(), style = MaterialTheme.typography.bodySmall)
                }
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text("Downloading… $progress%", style = MaterialTheme.typography.labelSmall)
                }
                if (failed) {
                    Spacer(Modifier.height(8.dp))
                    Text("Couldn't download the update. Check your connection and try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val file = readyFile
            when {
                file != null -> {
                    Button(onClick = {
                        if (AppUpdater.canInstall(context)) AppUpdater.install(context, file)
                        else AppUpdater.requestInstallPermission(context)
                    }) {
                        Text(if (AppUpdater.canInstall(context)) "Install" else "Allow install")
                    }
                }
                downloading -> {}
                else -> {
                    Button(onClick = {
                        scope.launch {
                            downloading = true
                            failed = false
                            progress = 0
                            val f = AppUpdater.download(context, update) { progress = it }
                            downloading = false
                            if (f == null) failed = true else {
                                readyFile = f
                                if (!AppUpdater.canInstall(context)) AppUpdater.requestInstallPermission(context)
                            }
                        }
                    }) { Text("Update now") }
                }
            }
        },
        dismissButton = {
            if (!downloading) TextButton(onClick = onDismiss) { Text("Later") }
        }
    )
}

@Composable
private fun MandatoryUpdateScreen(update: AppUpdater.Update) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var readyFile by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* mandatory */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(14.dp))
                Text("Update required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "A newer version of HolidayLink (${update.versionName}) is required to continue. This release has been available for over 24 hours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Text(update.notes.trim(), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (downloading) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Downloading… $progress%", style = MaterialTheme.typography.labelMedium)
                } else {
                    val file = readyFile
                    if (failed) {
                        Text("Download failed. Please check your internet connection.", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (file != null) {
                            Button(onClick = {
                                if (AppUpdater.canInstall(context)) AppUpdater.install(context, file)
                                else AppUpdater.requestInstallPermission(context)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (AppUpdater.canInstall(context)) "Install update" else "Allow installing")
                            }
                        } else {
                            Button(onClick = {
                                scope.launch {
                                    downloading = true
                                    failed = false
                                    progress = 0
                                    val f = AppUpdater.download(context, update) { progress = it }
                                    downloading = false
                                    if (f == null) failed = true else {
                                        readyFile = f
                                        if (!AppUpdater.canInstall(context)) AppUpdater.requestInstallPermission(context)
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (failed) "Try again" else "Download update")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Installed: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
