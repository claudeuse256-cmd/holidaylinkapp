package org.holidaylink.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.holidaylink.app.config.AppConfig
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.ContactMatch
import org.holidaylink.app.net.User
import org.holidaylink.app.phone.ContactMatcher
import org.holidaylink.app.phone.Countries
import org.holidaylink.app.phone.DeviceContact
import org.holidaylink.app.phone.DeviceContacts

/** Presence dot colour used for online contacts. */
private val OnlineGreen = Color(0xFF22C55E)

/** A device contact joined with its HolidayLink account, when one exists. */
private data class ContactRow(
    val contact: DeviceContact,
    val user: User?,
    val matchedNumber: String?
) {
    val onHolidayLink: Boolean get() = user != null
}

/**
 * Contacts tab.
 *
 * Reads the real device address book and splits it into "On HolidayLink" (matched accounts, shown
 * first) and "Invite to HolidayLink" (device contacts with no account). Names, photos and raw
 * numbers never leave the device: only SHA-256 hashes of canonical numbers are sent for matching.
 */
@Composable
fun ContactsScreen(
    api: Api,
    onOpenChat: (User) -> Unit,
    onOpenProfile: (User) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var granted by remember { mutableStateOf(hasContactsPermission(context)) }
    var asked by remember { mutableStateOf(contactsPermissionAsked(context)) }
    var loading by remember { mutableStateOf(false) }
    var matching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ContactRow>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showRationale by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }

    val dialCodes: List<String> = remember(context) {
        val stored = Countries.byIso(
            context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
                .getString("countryIso", Countries.DEFAULT_ISO)
        ).dialCode
        listOfNotNull(stored, ContactMatcher.deviceRegionDialCode(context)).distinct()
    }

    suspend fun loadDevice() {
        loading = true
        error = ""
        matching = false
        try {
            val contacts = withContext(Dispatchers.IO) {
                DeviceContacts.read(context, dialCodes)
            }
            rows = contacts.map { ContactRow(it, null, null) }
            if (contacts.isEmpty()) return

            matching = true
            val hashes = contacts
                .flatMap { c -> c.phones.mapNotNull { it.hash } }
                .distinct()
                .take(ContactMatcher.MAX_HASHES)

            val matches: List<ContactMatch> = try {
                api.contactMatch(hashes)
            } catch (e: ApiException) {
                error = e.message.orEmpty()
                emptyList()
            }

            // Index matches by the hash the client sent, then attach each one to the exact
            // device number that produced that hash. The server never returns phone numbers.
            val byHash = HashMap<String, ContactMatch>(matches.size)
            matches.forEach { m -> if (m.hash.isNotBlank()) byHash[m.hash] = m }

            rows = contacts.map { contact ->
                var matchedUser: User? = null
                var matchedNumber: String? = null
                for (phone in contact.phones) {
                    val hit = phone.hash?.let { byHash[it] }
                    if (hit != null && matchedUser == null) {
                        matchedUser = hit.user
                        matchedNumber = phone.raw
                    }
                }
                ContactRow(contact, matchedUser, matchedNumber)
            }
        } catch (e: Exception) {
            error = (e as? ApiException)?.message
                ?: "We couldn't read your contacts. Please try again."
        } finally {
            loading = false
            matching = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        asked = true
        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("contactsAsked", true).apply()
        if (result) scope.launch { loadDevice() } else error = ""
    }

    LaunchedEffect(refreshToken) {
        if (granted) loadDevice()
    }

    val filtered = remember(rows, query) {
        if (query.isBlank()) {
            rows
        } else {
            val needle = query.trim().lowercase()
            rows.filter { row ->
                row.contact.searchKey.contains(needle) ||
                    row.contact.phones.any { it.raw.contains(needle) }
            }
        }
    }
    val registered = filtered.filter { it.onHolidayLink }
    val unregistered = filtered.filter { !it.onHolidayLink }
    val railLetters = remember(unregistered) {
        unregistered.map { it.contact.firstLetter() }.distinct().sorted()
    }
    // LazyColumn item offset of the first "Invite" row, used by the alphabet rail.
    val inviteOffset = (if (registered.isNotEmpty()) 1 else 0) + registered.size + 1

    Column(Modifier.fillMaxSize()) {
        ContactsHeader(
            query = query,
            onQueryChange = { query = it },
            onRefresh = { refreshToken++ },
            registeredCount = registered.size,
            inviteCount = unregistered.size,
            matching = matching
        )

        when {
            !granted -> PermissionRequired(asked = asked, onRequest = { showRationale = true })

            loading && rows.isEmpty() -> LoadingState(label = "Reading your contacts...")

            error.isNotBlank() && rows.isEmpty() ->
                ErrorState(error) { refreshToken++ }

            rows.isEmpty() && !loading -> EmptyState(
                icon = Icons.Default.Contacts,
                title = "No contacts on this device",
                subtitle = "Add someone to this phone's address book, then pull down to refresh."
            )

            else -> PullToRefreshContainer(
                refreshing = loading || matching,
                onRefresh = { refreshToken++ },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        if (query.isNotBlank() && filtered.isEmpty()) {
                            item(key = "no-results") { SearchNoResults(query) }
                        }

                        if (registered.isNotEmpty()) {
                            item(key = "hdr-on-holidaylink") {
                                SectionHeader("On HolidayLink", registered.size)
                            }
                            itemsIndexed(
                                items = registered,
                                key = { _, r -> "on-${r.contact.contactId}" }
                            ) { _, row ->
                                RegisteredContactRow(
                                    row = row,
                                    onOpen = { row.user?.let(onOpenProfile) },
                                    onMessage = { row.user?.let(onOpenChat) },
                                    onCall = { row.matchedNumber?.let { dialNumber(context, it) } }
                                )
                            }
                        }

                        if (registered.isEmpty() && unregistered.isNotEmpty()) {
                            item(key = "registered-empty") {
                                NoticeText(
                                    "Nobody in your contacts uses HolidayLink yet. " +
                                        "Invite a friend below to get started."
                                )
                            }
                        }

                        if (unregistered.isNotEmpty()) {
                            item(key = "hdr-invite") {
                                Spacer(Modifier.height(8.dp))
                                SectionHeader("Invite to HolidayLink", unregistered.size)
                            }
                            itemsIndexed(
                                items = unregistered,
                                key = { _, r -> "inv-${r.contact.contactId}" }
                            ) { _, row ->
                                InviteContactRow(row = row) { promptInvite(context, row.contact) }
                            }
                            item(key = "invite-footer") { InviteFooter() }
                        }
                    }

                    if (railLetters.size > 1) {
                        AlphabetRail(
                            letters = railLetters,
                            onPick = { letter ->
                                val target = unregistered.indexOfFirst {
                                    it.contact.firstLetter() == letter
                                }
                                if (target >= 0) {
                                    scope.launch {
                                        listState.animateScrollToItem(inviteOffset + target)
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            icon = { Icon(Icons.Default.Contacts, null) },
            title = { Text("Match your contacts?") },
            text = {
                Text(
                    "HolidayLink will read this phone's address book so it can show which of " +
                        "your contacts already use HolidayLink.\n\n" +
                        "Only a one-way SHA-256 hash of each number is sent for matching. " +
                        "Names, photos and numbers stay on this device. You can skip this and " +
                        "use the rest of the app normally."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            }
        )
    }
}

// ------------------------------------------------------------------ chrome

@Composable
private fun ContactsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    registeredCount: Int,
    inviteCount: Int,
    matching: Boolean
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 4.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Contacts",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            RefreshButton(onRefresh)
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            placeholder = { Text("Search name or number") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            }
        )

        if (matching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Matching hashed numbers…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp)
            )
        } else if (registeredCount > 0 || inviteCount > 0) {
            Text(
                "$registeredCount on HolidayLink  •  $inviteCount to invite",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RegisteredContactRow(
    row: ContactRow,
    onOpen: () -> Unit,
    onMessage: () -> Unit,
    onCall: () -> Unit
) {
    val user = row.user ?: return
    val sameName = user.displayName.equals(row.contact.displayName, ignoreCase = true)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            name = row.contact.displayName,
            photo = row.contact.photoUri ?: user.avatarUrl,
            online = user.online,
            size = 50.dp
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.contact.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!sameName && user.displayName.isNotBlank()) {
                Text(
                    user.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                presenceLabel(user),
                style = MaterialTheme.typography.labelSmall,
                color = if (user.online) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Default.Call, contentDescription = "Call ${row.contact.displayName}")
        }
        IconButton(onClick = onMessage) {
            Icon(
                Icons.Default.Chat,
                contentDescription = "Message ${row.contact.displayName}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun InviteContactRow(row: ContactRow, onInvite: () -> Unit) {
    val number = row.contact.phones.firstOrNull { it.isPrimary } ?: row.contact.phones.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = row.contact.displayName, photo = row.contact.photoUri, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.contact.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (number != null) {
                Text(
                    number.display(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onInvite,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.PersonAddAlt, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("Invite", fontSize = 13.sp)
        }
    }
}

@Composable
private fun AlphabetRail(letters: List<Char>, onPick: (Char) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(end = 2.dp)
            .width(30.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter -> AlphabetBubble(letter) { onPick(letter) } }
    }
}

@Composable
private fun PermissionRequired(asked: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Contacts,
                null,
                Modifier.size(70.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Find your friends on HolidayLink",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Allow HolidayLink to read your contacts so you can see who already uses it. " +
                    "Only one-way hashes of phone numbers are sent — never names or numbers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text(if (asked) "Allow contacts" else "Continue")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { openAppSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Android settings") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Contacts stay optional. Everything else works without them.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchNoResults(query: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No contacts match \"$query\"", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Try a different spelling.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoticeText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun InviteFooter() {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.PersonAddAlt,
            null,
            Modifier.size(38.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "That's everyone on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun presenceLabel(user: User): String = when {
    user.online -> "online"
    !user.lastSeen.isNullOrBlank() -> "last seen ${friendlyWhen(user.lastSeen)}"
    user.username.isNotBlank() -> "@${user.username}"
    else -> "on HolidayLink"
}

private fun contactsPermissionAsked(context: Context): Boolean =
    context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
        .getBoolean("contactsAsked", false)

private fun primaryNumber(contact: DeviceContact): String? =
    (contact.phones.firstOrNull { it.isPrimary } ?: contact.phones.firstOrNull())?.raw

/** Opens the system dialler using the device's own number for that contact. */
private fun dialNumber(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, "No dialler app available.", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Invites through Android's own sharing / SMS stack. When no SMS app is installed the plain
 * share sheet is used instead, so the button is never a dead end.
 */
private fun promptInvite(context: Context, contact: DeviceContact) {
    val number = primaryNumber(contact)
    val firstName = contact.displayName.trim().substringBefore(' ')
    val message = buildString {
        append("Hi ")
        append(if (firstName.isNotBlank()) firstName else "there")
        append(", I'm using HolidayLink to chat with friends and for school work. ")
        append("Join me here — sign up free with your phone number.")
    }

    if (number.isNullOrBlank()) {
        shareInvite(context, message)
        return
    }

    android.app.AlertDialog.Builder(context)
        .setTitle("Invite ${contact.displayName}")
        .setMessage("Send an SMS invitation to $number?")
        .setPositiveButton("Send SMS") { _, _ ->
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
                .putExtra("sms_body", message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                shareInvite(context, message)
            }
        }
        .setNeutralButton("Share") { _, _ -> shareInvite(context, message) }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun shareInvite(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent.createChooser(intent, "Share HolidayLink invite"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app available to share.", Toast.LENGTH_SHORT).show()
    }
}
