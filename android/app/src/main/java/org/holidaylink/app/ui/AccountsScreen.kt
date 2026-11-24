package org.holidaylink.app.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.data.AccountStore
import org.holidaylink.app.net.AccountSession
import org.holidaylink.app.net.Me
import org.holidaylink.app.phone.PhoneNumbers

@Composable
fun AccountsScreen(
    prefs: SharedPreferences,
    me: Me?,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (AccountStore.Account) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<AccountStore.Account?>(null) }
    // AccountStore bumps `version` on every write, so an account whose token was minted in
    // the background shows up here without needing a restart.
    val version = AccountStore.version
    val accounts = remember(version) { AccountStore.all(prefs) }
    val full = accounts.size >= AccountStore.MAX
    val snackbar = remember { SnackbarHostState() }
    SettingsScaffold("Accounts", onBack, snackbar) {
        Column(Modifier.padding(bottom = 30.dp)) {
            Text(
                "Keep up to ${AccountStore.MAX} HolidayLink accounts on this phone and switch " +
                    "between them without signing in again. Messages and calls for both still " +
                    "reach this phone.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            accounts.forEach { account ->
                val current = account.userId == me?.id
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(account.displayName, size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            account.username.takeIf { it.isNotBlank() }?.let { "@$it" } ?: PhoneNumbers.mask(account.phone),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (current) {
                        Icon(Icons.Default.CheckCircle, "Current account", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = { onSwitchAccount(account) }) { Text("Switch") }
                    }
                    if (!current) {
                        IconButton(onClick = { confirm = account }) { Icon(Icons.Default.Delete, "Remove account") }
                    }
                }
            }
            SettingsNavRow(
                title = "Add account",
                icon = Icons.Default.Add,
                subtitle = if (full) {
                    "This phone already has ${AccountStore.MAX} accounts — remove one first."
                } else {
                    "Sign in to another HolidayLink number"
                },
                onClick = { if (!full) onAddAccount() }
            )
            if (accounts.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Your signed-in account will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    confirm?.let { account ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remove saved account?") },
            text = { Text("This removes the saved sign-in from this phone, so it stops notifying you here. It does not delete the HolidayLink account.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    val token = account.token
                    val userId = account.userId
                    AccountStore.remove(prefs, userId)
                    // Also take this device off the account's push list server-side, otherwise
                    // its messages would keep landing on a phone that no longer lists it.
                    scope.launch {
                        val fcm = runCatching {
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                        }.getOrNull()
                        runCatching { AccountSession.revoke(context, token, fcm) }
                    }
                }) { Text(tr("Remove")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Cancel")) } }
        )
    }
}
