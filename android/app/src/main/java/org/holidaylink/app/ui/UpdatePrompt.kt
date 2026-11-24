package org.holidaylink.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
 * - shows a new version as soon as it is found, as a short pop-up that can be closed ("Later")
 * - becomes mandatory (cannot be closed) 24 hours after the release was published
 */
@Composable
fun StartupUpdateGate() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<AppUpdater.Update?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        update = runCatching { AppUpdater.check(context) }.getOrNull()
    }

    val found = update ?: return
    if (dismissed && !found.isMandatory()) return

    UpdateDialog(
        update = found,
        mandatory = found.isMandatory(),
        onLater = { dismissed = true }
    )
}

/**
 * Release notes arrive as raw markdown ("# Title", "- item"). Show them as plain readable
 * lines instead of a wall of symbols.
 */
fun cleanUpdateNotes(raw: String): String =
    raw.lines()
        .map { line ->
            val t = line.trim()
            when {
                t.startsWith("#") -> t.trimStart('#').trim()
                t.startsWith("- ") || t.startsWith("* ") -> "• " + t.drop(2).trim()
                else -> t
            }
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

/**
 * One layout for both kinds of update. The action buttons sit at the TOP, right under the
 * version line, so they are always visible; the release notes scroll underneath them.
 */
@Composable
private fun UpdateDialog(update: AppUpdater.Update, mandatory: Boolean, onLater: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var readyFile by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!mandatory && !downloading) onLater() },
        properties = DialogProperties(
            dismissOnBackPress = !mandatory,
            dismissOnClickOutside = !mandatory,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = if (mandatory) Modifier.fillMaxSize() else Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            shape = if (mandatory) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (mandatory) Arrangement.Center else Arrangement.Top
            ) {
                Icon(
                    Icons.Default.CloudDownload, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (mandatory) 64.dp else 44.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (mandatory) tr("Update required") else tr("Update available"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (mandatory) {
                        "HolidayLink ${update.versionName} is required to continue. It has been out for over 24 hours."
                    } else {
                        "HolidayLink ${update.versionName} is ready."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---- buttons first ----
                Spacer(Modifier.height(16.dp))
                if (downloading) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("Downloading… $progress%", style = MaterialTheme.typography.labelMedium)
                } else {
                    if (failed) {
                        Text(
                            "Couldn't download the update. Check your connection and try again.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val file = readyFile
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (file != null) {
                                    if (AppUpdater.canInstall(context)) AppUpdater.install(context, file)
                                    else AppUpdater.requestInstallPermission(context)
                                } else {
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
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    file != null && AppUpdater.canInstall(context) -> "Install"
                                    file != null -> "Allow install"
                                    failed -> tr("Try again")
                                    else -> tr("Download update")
                                }
                            )
                        }
                        if (!mandatory) {
                            OutlinedButton(onClick = onLater) { Text("Later") }
                        }
                    }
                }

                // ---- release notes below, scrollable ----
                val notes = cleanUpdateNotes(update.notes)
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "What's new", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        Text(notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Installed: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
