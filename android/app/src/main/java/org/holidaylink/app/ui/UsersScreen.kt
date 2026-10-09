package org.holidaylink.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.User
import org.holidaylink.app.phone.ContactMatcher
import org.holidaylink.app.phone.DeviceContacts
import org.holidaylink.app.phone.PhoneNumbers

/**
 * Users tab: WhatsApp-style small rows.
 * Order: phone contacts first (with Contacts badge), then friends (existing
 * direct chats), then others ranked by mutual groups/channels, online, name.
 * Phone address book opens as a separate page via the button, never inline.
 */
@Composable
fun UsersScreen(
    api: Api,
    onOpenChat: (User) -> Unit,
    onOpenProfile: (User) -> Unit
) {
    var showDevice by remember { mutableStateOf(false) }
    if (showDevice) {
        DeviceContactsPage(api = api, onBack = { showDevice = false }, onOpenChat = onOpenChat, onOpenProfile = onOpenProfile)
        return
    }

    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var contactIds by remember { mutableStateOf(emptySet<String>()) }
    var friendIds by remember { mutableStateOf(emptySet<String>()) }
    var mutualCount by remember { mutableStateOf(emptyMap<String, Int>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("All") }

    LaunchedEffect(reload) {
        loading = users.isEmpty()
        try {
            val ctx = api.appContextForCache()
            val uid = FirebaseBackend.uid.orEmpty()
            // Phone matching stays on-device in spirit: read the address book
            // locally, then query Firestore only for those exact numbers.
            val dialCodes = ContactMatcher.defaultDialCodes(ctx)
            val deviceNumbers = runCatching {
                withContext(Dispatchers.IO) { DeviceContacts.read(ctx, dialCodes) }
                    .flatMap { c -> c.phones.flatMap { PhoneNumbers.candidatesFor(it.raw, dialCodes) } }
                    .distinct().take(1000)
            }.getOrDefault(emptyList())
            val foundIds = mutableSetOf<String>()
            for (chunk in deviceNumbers.chunked(10)) {
                if (chunk.isEmpty()) continue
                val snap = FirebaseBackend.db.collection("users").whereIn("phone", chunk).get().await()
                snap.documents.forEach { foundIds.add(it.id) }
            }
            contactIds = foundIds

            val dir = FirebaseBackend.db.collection("users").limit(300).get().await()
            val all = dir.documents.filter { it.id != uid }.mapNotNull { d ->
                val name = d.getString("name").orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val online = d.getBoolean("online") == true
                val seenMs = FirebaseBackend.docLastSeenMillis(d.data.orEmpty())
                User(
                    id = d.id,
                    displayName = name,
                    username = d.getString("username").orEmpty(),
                    avatarUrl = d.getString("profilePic"),
                    online = FirebaseBackend.isTrulyOnline(online, seenMs),
                    lastSeen = seenMs?.let { java.time.Instant.ofEpochMilli(it).toString() }
                )
            }
            val chatDocs = FirebaseBackend.db.collection("chats").whereArrayContains("users", uid).get().await()
            val directPeers = chatDocs.documents
                .filter { it.getBoolean("isGroup") != true }
                .mapNotNull { doc ->
                    ((doc.get("users") as? List<String>) ?: emptyList()).firstOrNull { it != uid }
                }.toSet()
            friendIds = directPeers
            // Common ground: how many groups/channels/communities share each user.
            val counts = mutableMapOf<String, Int>()
            chatDocs.documents
                .filter { it.getBoolean("isGroup") == true }
                .forEach { doc ->
                    val members = ((doc.get("members") as? List<String>)
                        ?: (doc.get("users") as? List<String>)).orEmpty()
                    members.filter { it != uid }.forEach { m -> counts[m] = (counts[m] ?: 0) + 1 }
                }
            mutualCount = counts
            users = all
            // Offline cache for Users ordering.
            runCatching { api.saveUsersCache(all) }
            error = ""
        } catch (e: Exception) {
            val cached = runCatching { api.readUsersCache() }.getOrNull()
            if (cached != null && cached.isNotEmpty()) { users = cached; error = "" }
            else error = "Couldn't load people. Check your internet."
        } finally { loading = false }
    }

    fun rankScore(u: User): Int {
        var s = 0
        if (contactIds.contains(u.id)) s += 10_000
        if (friendIds.contains(u.id)) s += 5_000
        s += (mutualCount[u.id] ?: 0) * 100
        if (u.online) s += 50
        return s
    }

    val filtered = users.filter {
        query.isBlank() || it.displayName.contains(query, true) || it.username.contains(query, true)
    }.filter {
        when (filter) {
            "Contacts" -> contactIds.contains(it.id)
            "Friends" -> friendIds.contains(it.id)
            else -> true
        }
    }.sortedWith(compareByDescending<User> { rankScore(it) }.thenBy { it.displayName.lowercase() })

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Users", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = { showDevice = true }) { Icon(Icons.Default.Contacts, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Phone contacts") }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search people") },
            leadingIcon = { Icon(Icons.Default.PersonSearch, null) }, singleLine = true, shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Contacts", "Friends").forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
        }
        when {
            loading -> LoadingState(label = "Loading people…")
            error.isNotBlank() && users.isEmpty() -> ErrorState(error) { reload++ }
            filtered.isEmpty() -> EmptyState(icon = Icons.Default.PersonSearch, title = "Nobody found", subtitle = "Try another name or open phone contacts.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { u ->
                    WhatsAppUserRow(user = u, isContact = contactIds.contains(u.id), isFriend = friendIds.contains(u.id),
                        mutual = mutualCount[u.id] ?: 0, onOpen = { onOpenProfile(u) }, onMessage = { onOpenChat(u) })
                }
            }
        }
    }
}

@Composable
private fun WhatsAppUserRow(
    user: User, isContact: Boolean, isFriend: Boolean, mutual: Int,
    onOpen: () -> Unit, onMessage: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name = user.displayName, photo = user.avatarUrl, size = 44.dp, online = user.online)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (isContact) {
                    Spacer(Modifier.width(6.dp))
                    Text("Contacts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            val sub = when {
                isFriend && mutual > 0 -> "Friend • $mutual groups in common"
                isFriend -> "Friend"
                mutual > 0 -> "$mutual groups in common"
                user.online -> "Online"
                !user.lastSeen.isNullOrBlank() -> "Last seen ${friendlyWhen(user.lastSeen)}"
                else -> "@${user.username}"
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onMessage) { Icon(Icons.Default.Chat, "Message", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun DeviceContactsPage(api: Api, onBack: () -> Unit, onOpenChat: (User) -> Unit, onOpenProfile: (User) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text("Phone contacts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
            ContactsScreen(api = api, onOpenChat = onOpenChat, onOpenProfile = onOpenProfile)
        }
    }
}
