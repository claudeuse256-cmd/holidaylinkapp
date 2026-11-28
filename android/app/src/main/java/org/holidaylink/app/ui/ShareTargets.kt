package org.holidaylink.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.holidaylink.app.MainActivity
import org.holidaylink.app.R
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.FirestoreChat
import org.holidaylink.app.net.Notifier
import java.io.File
import java.util.UUID

/**
 * What another app (gallery, files, TikTok, a browser…) asked HolidayLink to share:
 * pictures, videos, files and/or a piece of text such as a link.
 */
object IncomingShare {
    data class Item(val uri: Uri, val mime: String)

    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var text by mutableStateOf<String?>(null)
        private set
    /** Set when the user picked one of our "direct share" chat shortcuts in the share sheet. */
    var directChatId by mutableStateOf<String?>(null)
        private set

    val hasContent: Boolean get() = items.isNotEmpty() || !text.isNullOrBlank()

    @Suppress("DEPRECATION")
    fun consume(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val uris = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        } else {
            val single = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            if (single != null) uris.add(single)
        }
        val mime = intent.type.orEmpty()
        items = uris.map { Item(it, mime) }
        text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        directChatId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)?.removePrefix("chat-")?.takeIf { it.isNotBlank() }
        // Handled: a rotation or a second delivery must not share it again.
        intent.action = null
    }

    fun clear() {
        items = emptyList()
        text = null
        directChatId = null
    }
}

private data class ShareTarget(val chatId: String, val name: String, val photo: String?, val isGroup: Boolean)

/** Copies a shared file into the app and starts sending it. Returns false if it was too big. */
private suspend fun sendSharedFiles(context: Context, chatId: String): Boolean {
    val items = IncomingShare.items
    val text = IncomingShare.text
    var ok = true
    val sendCaption = items.size == 1 && !text.isNullOrBlank() &&
        (items[0].mime.startsWith("image/") || items[0].mime.startsWith("video/"))
    items.forEachIndexed { index, shared ->
        val info = MediaPicker.inspect(context, shared.uri)
        if (info.sizeBytes > MediaPicker.maxAttachmentBytes) {
            ok = false
            return@forEachIndexed
        }
        val ext = info.name?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 } ?: "bin"
        val copy = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "pending").apply { mkdirs() }
            val target = File(dir, "share-${System.currentTimeMillis()}-$index.$ext")
            runCatching {
                context.contentResolver.openInputStream(shared.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            target.takeIf { it.isFile && it.length() > 0L }
        } ?: run { ok = false; return@forEachIndexed }
        val kind = when {
            info.mimeType.startsWith("image/") -> "image"
            info.mimeType.startsWith("video/") -> "video"
            else -> "file"
        }
        val outgoing = OutgoingItem(
            localId = UUID.randomUUID().toString(),
            kind = kind,
            text = if (sendCaption) text.orEmpty() else "",
            fileName = info.name ?: copy.name,
            localPath = copy.absolutePath,
            mimeType = info.mimeType,
            stage = OutgoingStage.UPLOADING
        )
        OutboxStore.add(chatId, outgoing)
        OutboxStore.deliver(chatId, outgoing)
    }
    if (!text.isNullOrBlank() && !sendCaption) {
        runCatching { FirestoreChat.send(chatId, text) }
    }
    return ok
}

/**
 * "Share to": appears when another app shares into HolidayLink. Pick My status or any chat.
 */
@Composable
fun ShareToScreen(
    onOpenChat: (String) -> Unit,
    onShareToStatus: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var targets by remember { mutableStateOf<List<ShareTarget>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }

    fun send(chatId: String) {
        if (sending) return
        sending = true
        scope.launch {
            val ok = sendSharedFiles(context, chatId)
            if (!ok) {
                Toast.makeText(context, "Some files were too large or could not be read", Toast.LENGTH_LONG).show()
            }
            IncomingShare.clear()
            onOpenChat(chatId)
        }
    }

    LaunchedEffect(Unit) {
        // Picked a chat shortcut in the share sheet: no list, send straight there.
        IncomingShare.directChatId?.let { send(it); return@LaunchedEffect }
        val uid = FirebaseBackend.uid.orEmpty()
        val rows = runCatching { FirestoreChat.chatsFlow(uid).first() }.getOrDefault(emptyList())
        val peers = runCatching {
            FirestoreChat.resolvePeers(rows.filter { !it.isGroup }.mapNotNull { it.peerId }.distinct())
        }.getOrDefault(emptyMap())
        targets = rows.map { row ->
            val peer = row.peerId?.let { peers[it] }
            ShareTarget(
                chatId = row.id,
                name = if (row.isGroup) row.title.ifBlank { "Group" } else peer?.displayName ?: "Contact",
                photo = if (row.isGroup) row.photo else peer?.avatarUrl,
                isGroup = row.isGroup
            )
        }
        loading = false
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
                Column(Modifier.weight(1f)) {
                    Text("Share to…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val what = when {
                        IncomingShare.items.size > 1 -> "${IncomingShare.items.size} files"
                        IncomingShare.items.size == 1 -> "1 file"
                        else -> "a message"
                    }
                    Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (sending) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(Modifier.fillMaxSize()) {
                // My status first: pictures, videos and links all work as a status.
                item(key = "share-status") {
                    Row(
                        Modifier.fillMaxWidth().clickable { onShareToStatus() }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name = "My status", photo = null, size = 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("My status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (loading) {
                    item(key = "share-loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                items(
                    targets.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) },
                    key = { it.chatId }
                ) { target ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !sending) { send(target.chatId) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name = target.name, photo = target.photo, size = 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(target.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            if (target.isGroup) {
                                Text("Group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Publishes the most recent chats as "direct share" shortcuts, so HolidayLink and your people
 * show up at the TOP of the system share sheet (this is the only way to rank higher: Android
 * decides the rest from how often an app is used).
 */
object ShareShortcuts {
    const val CATEGORY = "org.holidaylink.app.category.SHARE_TARGET"
    private var lastPublished = 0L

    suspend fun publish(context: Context, rows: List<FirestoreChat.ChatRow>) {
        val now = System.currentTimeMillis()
        if (now - lastPublished < 120_000L) return
        lastPublished = now
        val top = rows.take(6)
        val peers = runCatching {
            FirestoreChat.resolvePeers(top.filter { !it.isGroup }.mapNotNull { it.peerId })
        }.getOrDefault(emptyMap())
        top.forEach { row ->
            val label = if (row.isGroup) row.title.ifBlank { "Group" } else peers[row.peerId]?.displayName ?: return@forEach
            val open = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(Notifier.EXTRA_TARGET, row.id)
            val shortcut = ShortcutInfoCompat.Builder(context, "chat-${row.id}")
                .setShortLabel(label.take(10).ifBlank { label })
                .setLongLabel(label)
                .setLongLived(true)
                .setCategories(setOf(CATEGORY))
                .setIcon(IconCompat.createWithResource(context, R.drawable.holidaylink_icon))
                .setIntent(open)
                .build()
            runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
        }
    }
}
