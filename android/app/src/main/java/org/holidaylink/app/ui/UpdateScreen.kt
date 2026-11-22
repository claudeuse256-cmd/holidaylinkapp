@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import kotlinx.coroutines.launch
import org.holidaylink.app.BuildConfig
import org.holidaylink.app.net.AppUpdater

/**
 * Settings › App updates: checks for a newer build and opens its download in the browser.
 */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdater.Update?>(null) }
    var upToDate by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    suspend fun runCheck() {
        checking = true
        failed = false
        update = null
        upToDate = false
        runCatching { AppUpdater.check(context) }
            .onSuccess { found ->
                if (found == null) upToDate = true else update = found
            }
            .onFailure { failed = true }
        checking = false
    }

    LaunchedEffect(Unit) { runCheck() }

    SettingsScaffold("App updates", onBack, snackbar) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            Text("HolidayLink", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Installed: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            val found = update
            when {
                checking -> CircularProgressIndicator(Modifier.size(34.dp))

                found != null -> {
                    Cardish {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Version ${found.versionName} available", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "You have ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (found.notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(found.notes.trim(), style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { AppUpdater.openDownload(context, found) },
                                modifier = Modifier.weight(1f)
                            ) { Text(tr("Download update")) }
                            OutlinedButton(onClick = { scope.launch { runCheck() } }) { Text(tr("Recheck")) }
                        }
                    }
                }

                upToDate -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("You're on the latest version", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "New builds are picked up automatically when they are published.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { scope.launch { runCheck() } }) { Text("Check again") }
                }

                failed -> {
                    Text(
                        "Couldn't reach the update server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { scope.launch { runCheck() } }) { Text(tr("Try again")) }
                }

                else -> {
                    Button(onClick = { scope.launch { runCheck() } }) { Text("Check for updates") }
                }
            }
        }
    }
}

/** Small elevated card used only by this screen. */
@Composable
private fun Cardish(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}
