package org.holidaylink.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.WindowManager
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.data.AutoDownload
import org.holidaylink.app.media.MediaEditor
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.AiBot
import org.holidaylink.app.net.AppPresence
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.Cloudinary
import org.holidaylink.app.net.DeviceCache
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.FirestoreBots
import org.holidaylink.app.net.FirestoreChat
import org.holidaylink.app.net.Imgbb
import org.holidaylink.app.net.MediaCache
import org.holidaylink.app.net.Message
import org.holidaylink.app.net.Notifier
import org.holidaylink.app.net.StudyBuddy
import org.holidaylink.app.net.User
import org.holidaylink.app.net.StatusUpdate
import org.holidaylink.app.voice.VoiceRecorder
import org.holidaylink.app.ui.AnimatedSticker
import org.holidaylink.app.ui.StickerCatalog
import org.holidaylink.app.ui.StickerItem
import org.holidaylink.app.ui.stickerAssetPath
import java.util.UUID

/**
 * Conversation screen on Firestore (same docs as the website room).
 * Realtime messages, text + reply/edit/delete, photo (imgbb) / video /
 * voice / document (Firebase Storage), view-once + FLAG_SECURE viewer,
 * self-destruct timers, read ticks, @ai/@bot send-on-behalf.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    api: Api,
    chat: ChatItem,
    currentUserId: String,
    onBack: () -> Unit,
    onOpenProfile: (User) -> Unit,
    onOpenGroupInfo: (ChatItem) -> Unit,
    onStartCall: (User, String) -> Unit,
    initialStatusReply: StatusUpdate? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    // True when the newest message is already on screen; drives the "jump to latest" button.
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    val uid = currentUserId.ifBlank { FirestoreChat.myUid() }

    var draft by remember { mutableStateOf("") }
    var replyingTo by remember(initialStatusReply?.id) {
        mutableStateOf(
            initialStatusReply?.let { status ->
                FirestoreChat.ChatMessage(
                    id = status.id,
                    senderId = status.userId,
                    text = status.body?.takeIf { it.isNotBlank() } ?: "Photo or video status",
                    type = "status",
                    createdAt = com.google.firebase.Timestamp.now(),
                    read = true,
                    imageUrl = status.mediaUrl,
                    fileUrl = null,
                    fileName = null,
                    durationMs = null,
                    viewOnce = false,
                    expiresAt = null,
                    replyToName = "${status.userName}'s status"
                )
            }
        )
    }
    var editingId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    // Messages that are on their way out. They appear the moment send is tapped and keep
    // showing what is happening (upload %, sending, failed + retry).
    var outbox by remember { mutableStateOf<List<OutgoingItem>>(emptyList()) }
    // The "edit before sending" screen for a picked photo, video or document.
    var editorFor by remember { mutableStateOf<EditorSource?>(null) }
    var error by remember { mutableStateOf("") }
    var peerUser by remember { mutableStateOf(chat.peer) }
    var peerOnline by remember { mutableStateOf<Boolean?>(null) }
    // Whether the other person is writing right now. Both sides have to allow typing
    // indicators — the same rule the read receipts follow — otherwise the switch in
    // Settings would only ever work in one direction.
    val typingAllowed = AppSettings.state.typingIndicators && (peerUser?.typingIndicators ?: true)
    var peerTyping by remember(chat.id) { mutableStateOf(false) }

    var sheetFor by remember { mutableStateOf<FirestoreChat.ChatMessage?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var viewOnceSend by remember { mutableStateOf(false) }
    var selfDestructSecs by remember { mutableLongStateOf(0L) }
    var viewOnceTarget by remember { mutableStateOf<FirestoreChat.ChatMessage?>(null) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var reportTarget by remember { mutableStateOf<FirestoreChat.ChatMessage?>(null) }
    var infoFor by remember { mutableStateOf<FirestoreChat.ChatMessage?>(null) }
    var searching by remember(chat.id) { mutableStateOf(false) }
    var query by remember(chat.id) { mutableStateOf("") }
    var forwardMsg by remember { mutableStateOf<FirestoreChat.ChatMessage?>(null) }
    var forwardChats by remember { mutableStateOf<List<FirestoreChat.ChatRow>>(emptyList()) }
    var forwardNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var aiBots by remember { mutableStateOf<List<FirestoreBots.StudyBot>>(emptyList()) }
    var aiSending by remember { mutableStateOf(false) }
    // Bodies just sent but not yet echoed back by Firestore: single grey tick.
    var pendingTexts by remember { mutableStateOf<List<String>>(emptyList()) }
    // Index of the first unread incoming message, captured once so we can draw the
    // WhatsApp-style "Unread messages" line even after markRead flips the flags.
    var unreadAnchor by remember(chat.id) { mutableStateOf(-1) }

    var recorder by remember { mutableStateOf<VoiceRecorder?>(null) }
    var recordingMs by remember { mutableStateOf(0L) }
    var amplitude by remember { mutableIntStateOf(0) }
    var recLocked by remember { mutableStateOf(false) }
    var recPaused by remember { mutableStateOf(false) }
    var voiceListenOnce by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }

    val title = peerUser?.displayName?.takeIf { it.isNotBlank() }
        ?: chat.peer?.displayName?.takeIf { it.isNotBlank() }
        ?: chat.title?.takeIf { it.isNotBlank() }
        ?: if (chat.kind == "direct") "Contact" else "Group"

    // Coming back into a conversation used to start from an empty list, so the screen flashed
    // "no messages" for a beat before Firestore replayed the thread. The last copy seen in this
    // session seeds the list instead and the live snapshot replaces it silently.
    val cachedMessages = remember(chat.id) {
        DeviceCache.memory<List<FirestoreChat.ChatMessage>>("messages.${chat.id}") ?: emptyList()
    }
    val rawMessages by remember(chat.id) { FirestoreChat.messagesFlow(chat.id) }
        .collectAsState(initial = cachedMessages)
    LaunchedEffect(rawMessages) {
        if (rawMessages.isNotEmpty()) DeviceCache.put("messages.${chat.id}", rawMessages)
    }
    // "Delete for me" keeps the row on this device but strips its content, so the thread
    // still reads in order with "This message was deleted" in the gap. On the server the
    // message is untouched and everyone else still sees it, exactly like WhatsApp.
    val hiddenMessages = AppSettings.state.hiddenMessages
    val messages = remember(rawMessages, hiddenMessages) {
        if (hiddenMessages.isEmpty()) rawMessages
        else rawMessages.map { message ->
            if (hiddenMessages["${chat.id}/${message.id}"] == true) {
                message.copy(
                    deleted = true,
                    text = "",
                    imageUrl = null,
                    fileUrl = null,
                    audioBlob = null,
                    reactions = emptyMap()
                )
            } else message
        }
    }
    // Server copies of the messages that are still travelling. They are hidden while the
    // local bubble stands in for them, so a message is never shown twice.
    val hiddenServerIds = remember(outbox, messages, uid) {
        val ids = mutableSetOf<String>()
        val mine = messages.filter { it.senderId == uid }
        outbox.forEach { item ->
            item.serverId?.let { ids.add(it) }
            if (item.stage == OutgoingStage.UPLOADING || item.stage == OutgoingStage.SENDING) {
                mine.filter { m ->
                    m.text == item.text &&
                        (item.kind == "text" || m.fileName == item.fileName)
                }.forEach { ids.add(it.id) }
            }
        }
        ids
    }
    val shownMessages = remember(messages, hiddenServerIds) { messages.filter { it.id !in hiddenServerIds } }

    // Fetch every attachment once, the moment the conversation opens, and keep the file on
    // disk. Scrolling back — or reopening the chat — reads the cache instead of downloading
    // again; a replaced upload gets a new URL, so only real updates are fetched.
    LaunchedEffect(chat.id, messages.size) {
        val settings = AppSettings.state
        val photos = downloadsAllowed(settings.autoDownloadPhotos, context)
        val videos = downloadsAllowed(settings.autoDownloadVideos, context)
        val documents = downloadsAllowed(settings.autoDownloadDocuments, context)
        val urls = messages.mapNotNull { message ->
            when {
                message.imageUrl != null && message.type != "sticker" && photos -> message.imageUrl
                message.fileUrl != null && (if (message.type == "video") videos else documents) -> message.fileUrl
                else -> null
            }
        }
        if (urls.isNotEmpty()) MediaCache.prefetch(context, urls)
    }

    LaunchedEffect(chat.id) {
        runCatching { FirestoreChat.purgeExpired(chat.id) }
        peerUser = chat.peer
        var pid = chat.peer?.id?.takeIf { it.isNotBlank() && !it.startsWith("00000000") }
        if (pid == null && chat.kind == "direct") {
            // Header must never read "Group" for a 1-1 chat: recover the peer
            // from the conversation members instead.
            pid = runCatching {
                FirebaseBackend.db.collection("chats").document(chat.id).get().await()
                    .let { doc -> ((doc.get("users") as? List<String>) ?: emptyList()).firstOrNull { it != uid } }
            }.getOrNull()
        }
        if (pid != null) {
            runCatching { FirestoreChat.resolvePeer(pid) }.onSuccess {
                peerUser = it
                peerOnline = FirebaseBackend.isTrulyOnline(
                    it.online,
                    it.lastSeen?.let { iso -> runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() }
                )
            }
        }
        runCatching { aiBots = FirestoreBots.list() }
    }

    // Presence is a claim that goes stale: while this conversation stays open, re-check who is
    // actually on the other end instead of showing the state from the moment it was opened.
    LaunchedEffect(chat.id) {
        while (true) {
            delay(45_000)
            val pid = peerUser?.id?.takeIf { it.isNotBlank() && !it.startsWith("00000000") } ?: continue
            runCatching { FirestoreChat.resolvePeer(pid) }.onSuccess {
                peerUser = it
                peerOnline = it.online
            }
        }
    }

    LaunchedEffect(chat.id, typingAllowed) {
        if (!typingAllowed) {
            peerTyping = false
            return@LaunchedEffect
        }
        runCatching {
            FirestoreChat.typingFlow(chat.id).collect { peerTyping = it }
        }
    }

    // Saying "I am typing" without flooding Firestore: one write every few seconds while keys
    // are being pressed, then one more to stop. The marker also expires on the other phone by
    // age, so a killed app cannot leave somebody staring at a permanent "typing…".
    var typingMarked by remember(chat.id) { mutableStateOf(false) }
    var lastTypingWrite by remember(chat.id) { mutableLongStateOf(0L) }
    LaunchedEffect(draft) {
        if (!AppSettings.state.typingIndicators) return@LaunchedEffect
        if (draft.isBlank()) {
            if (typingMarked) {
                typingMarked = false
                runCatching { FirestoreChat.setTyping(chat.id, false) }
            }
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        if (!typingMarked || now - lastTypingWrite >= 3_000) {
            typingMarked = true
            lastTypingWrite = now
            runCatching { FirestoreChat.setTyping(chat.id, true) }
        }
        delay(5_000)
        typingMarked = false
        runCatching { FirestoreChat.setTyping(chat.id, false) }
    }
    DisposableEffect(chat.id) {
        onDispose {
            if (typingMarked) {
                val id = chat.id
                scope.launch { runCatching { FirestoreChat.setTyping(id, false) } }
            }
        }
    }

    // Bring the typing bubble into view, but only for somebody already reading the newest
    // message: yanking a person back down while they scroll through history is worse than
    // leaving the bubble just off screen.
    LaunchedEffect(peerTyping) {
        if (peerTyping && atBottom) {
            listState.animateScrollToItem(shownMessages.size + outbox.size)
        }
    }

    LaunchedEffect(messages, outbox.size) {
        // The typing bubble is a real list item, so the newest row is one further down when
        // it is on screen.
        val total = shownMessages.size + outbox.size + (if (peerTyping) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
        if (messages.isNotEmpty()) {
            if (pendingTexts.isNotEmpty()) {
                val echoed = messages.filter { it.senderId == uid }.map { it.text }.toSet()
                pendingTexts = pendingTexts.filter { it !in echoed }
            }
            // The conversation is on screen, so its alert goes away — even when the alert
            // itself was never tapped.
            Notifier.clearChat(context, chat.id)
            // Remember where the unread block starts before markRead clears the flags.
            if (unreadAnchor < 0) {
                unreadAnchor = messages.indexOfFirst { it.senderId != uid && !it.read }
            }
            if (messages.any { it.senderId != uid && !it.read }) {
                runCatching {
                    FirestoreChat.markRead(
                        chat.id,
                        sendReceipts = AppSettings.state.readReceipts &&
                            (peerUser?.readReceipts ?: true)
                    )
                }
            }
            // Receiving these on this device flips the sender's single tick to a double tick.
            if (messages.any { it.senderId != uid && !it.delivered }) {
                runCatching { FirestoreChat.markDelivered(chat.id) }
            }
        }
    }

    // When the stored message finally arrives, the local bubble has done its job.
    LaunchedEffect(messages, outbox) {
        val ids = messages.map { it.id }.toSet()
        val done = outbox.filter { it.serverId != null && it.serverId in ids }.map { it.localId }
        if (done.isNotEmpty()) outbox = outbox.filter { it.localId !in done }
    }

    // Reading a conversation marks it as read: the tray alert disappears, and a message
    // that arrives while we are looking at it does not buzz at all.
    DisposableEffect(chat.id) {
        AppPresence.openChatId = chat.id
        Notifier.clearChat(context, chat.id)
        onDispose {
            if (AppPresence.openChatId == chat.id) AppPresence.openChatId = null
        }
    }

    // The "Unread messages" line is transient, exactly like WhatsApp.
    LaunchedEffect(unreadAnchor) {
        if (unreadAnchor >= 0) {
            delay(6000)
            unreadAnchor = -1
        }
    }

    // Load the chat list when the user starts forwarding a message.
    LaunchedEffect(forwardMsg) {
        if (forwardMsg != null && forwardChats.isEmpty()) {
            runCatching {
                val rows = FirestoreChat.chatsFlow(uid).first()
                forwardChats = rows
                forwardNames = rows.mapNotNull { r ->
                    r.peerId?.let { pid ->
                        pid to runCatching { FirestoreChat.resolvePeer(pid).displayName }.getOrDefault("Contact")
                    }
                }.toMap()
            }
        }
    }

    fun parseMention(text: String): Triple<String, String?, String>? {
        val match = Regex("@([A-Za-z0-9_]{2,32})\\s+([^\\n]+)").find(text) ?: return null
        val handle = match.groupValues[1].lowercase()
        val instruction = match.groupValues[2].trim()
        if (instruction.isEmpty()) return null
        if (handle == "ai") return Triple("@ai", null, instruction)
        val bot = aiBots.firstOrNull { it.username.lowercase() == handle } ?: return null
        return Triple("@${bot.username}", bot.id, instruction)
    }

    /**
     * Applies a change to one outgoing message.
     *
     * The upload progress arrives on a network thread, so the change is always applied on
     * the main thread — that keeps the list from flickering and the state consistent.
     */
    fun updateOutgoing(localId: String, block: (OutgoingItem) -> OutgoingItem) {
        scope.launch {
            outbox = outbox.map { if (it.localId == localId) block(it) else it }
        }
    }

    /** Writes a text message to Firestore and keeps the local bubble in step with it. */
    fun deliverText(localId: String, body: String, replyToId: String?, replyToBody: String?) {
        scope.launch {
            try {
                val serverId = FirestoreChat.send(
                    chatId = chat.id,
                    text = body,
                    kind = "text",
                    ttlSeconds = 0,
                    replyToId = replyToId,
                    replyToBody = replyToBody,
                    replyToName = null
                )
                updateOutgoing(localId) { it.copy(stage = OutgoingStage.SENT, serverId = serverId) }
            } catch (e: Exception) {
                updateOutgoing(localId) {
                    it.copy(stage = OutgoingStage.FAILED, error = "Not sent — tap to retry")
                }
            }
        }
    }

    fun send() {
        val body = draft.trim()
        if (body.isEmpty()) return
        val editId = editingId
        val reply = replyingTo
        if (editId != null) {
            // Editing an existing message: the change lands in place.
            draft = ""
            editingId = null
            replyingTo = null
            sending = true
            scope.launch {
                try {
                    FirestoreChat.editMessage(chat.id, editId, body)
                } catch (e: Exception) {
                    snackbar.showSnackbar("Could not edit the message.")
                } finally {
                    sending = false
                }
            }
            return
        }
        // Sending is instant: the bubble shows up the moment the button is tapped and the
        // tick follows when the message is really stored.
        draft = ""
        replyingTo = null
        val localId = UUID.randomUUID().toString()
        outbox = outbox + OutgoingItem(
            localId = localId,
            kind = "text",
            text = body,
            stage = OutgoingStage.SENDING,
            replyToId = reply?.id,
            replyToBody = reply?.text?.take(120)?.ifBlank { "[photo]" }
        )
        deliverText(
            localId,
            body,
            reply?.id,
            reply?.text?.take(120)?.ifBlank { "[photo]" }
        )
    }

    /** Copies the picked file into the app cache so the editor and the retry both work. */
    fun copyToCache(context: Context, uri: Uri, kind: String): EditorSource? {
        return try {
            val picked = MediaPicker.inspect(context, uri)
            val name = picked.name ?: if (kind == "video") "Video" else if (kind == "image") "Photo" else "File"
            val mime = picked.mimeType.ifBlank { Cloudinary.mimeOf(name) }
            val ext = name.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                ?: when (kind) {
                    "video" -> "mp4"
                    "image" -> "jpg"
                    else -> "bin"
                }
            val dir = java.io.File(context.cacheDir, "pending").apply { mkdirs() }
            val file = java.io.File(dir, "p-${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            EditorSource(kind = kind, file = file, fileName = name, mimeType = mime)
        } catch (_: Exception) {
            null
        }
    }

    /** Puts a picked file into the editor instead of sending it straight away. */
    fun openEditor(uri: Uri, kind: String) {
        scope.launch {
            val source = withContext(kotlinx.coroutines.Dispatchers.IO) {
                copyToCache(context, uri, kind)
            }
            if (source == null) {
                snackbar.showSnackbar("That file couldn't be read.")
            } else {
                editorFor = source
            }
        }
    }

    /** Applies the edits the user made and hands the file to the sender. */
    fun prepareFile(source: EditorSource, item: OutgoingItem): java.io.File {
        if (item.kind != "video") return source.file
        val duration = MediaEditor.videoDurationMs(source.file)
        if (duration <= 0L) return source.file
        val untouched = item.trimStartMs <= 0L && item.trimEndMs >= duration
        if (untouched) return source.file
        val dir = java.io.File(context.cacheDir, "edited").apply { mkdirs() }
        val out = java.io.File(dir, "trim-${System.currentTimeMillis()}.mp4")
        return MediaEditor.trimVideo(source.file, item.trimStartMs, item.trimEndMs, out) ?: source.file
    }

    /** Uploads the attachment and stores the message, reporting progress while it runs. */
    fun deliverMedia(item: OutgoingItem, source: EditorSource) {
        scope.launch {
            try {
                val prepared = withContext(kotlinx.coroutines.Dispatchers.IO) { prepareFile(source, item) }
                val bytes = withContext(kotlinx.coroutines.Dispatchers.IO) { prepared.readBytes() }
                val url: String
                if (item.kind == "image") {
                    val jpeg = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val scaled = MediaEditor.jpegBytes(prepared) ?: bytes
                        MediaEditor.rotateJpeg(scaled, item.rotation) ?: scaled
                    }
                    url = Imgbb.uploadJpeg(jpeg) { p ->
                        updateOutgoing(item.localId) { it.copy(progress = p) }
                    }
                } else {
                    if (bytes.size > MediaPicker.maxAttachmentBytes) {
                        throw ApiException("That file is too large (max 20 MB).")
                    }
                    val name = item.fileName ?: "File"
                    val mime = item.mimeType.ifBlank { Cloudinary.mimeOf(name) }
                    url = runCatching {
                        Cloudinary.upload(context, bytes, name, mime) { p ->
                            updateOutgoing(item.localId) { it.copy(progress = p) }
                        }
                    }.getOrElse {
                        // Cloudinary is not set up on the backend yet: keep working through
                        // Firebase Storage instead of failing the send outright.
                        val ext = name.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: "bin"
                        val path = "chat_media/${chat.id}/${UUID.randomUUID()}.$ext"
                        val ref = com.google.firebase.storage.FirebaseStorage.getInstance().getReference(path)
                        ref.putBytes(bytes).await()
                        ref.downloadUrl.await().toString()
                    }
                }
                updateOutgoing(item.localId) { it.copy(stage = OutgoingStage.SENDING, progress = 100) }
                val serverId = FirestoreChat.send(
                    chat.id,
                    item.text,
                    imageUrl = if (item.kind == "image") url else null,
                    fileUrl = if (item.kind == "image") null else url,
                    fileName = if (item.kind == "image") null else item.fileName,
                    kind = if (item.kind == "image") "image" else if (item.kind == "video") "video" else "file",
                    viewOnce = item.viewOnce,
                    ttlSeconds = item.ttlSeconds
                )
                updateOutgoing(item.localId) { it.copy(stage = OutgoingStage.SENT, serverId = serverId) }
            } catch (e: Exception) {
                updateOutgoing(item.localId) {
                    it.copy(
                        stage = OutgoingStage.FAILED,
                        error = (e as? ApiException)?.message ?: "Not sent — tap to retry"
                    )
                }
            }
        }
    }

    /** Adds the edited attachment to the outbox and starts sending it. */
    fun enqueueMedia(source: EditorSource, caption: String, rotation: Int, trimStart: Long, trimEnd: Long) {
        val once = viewOnceSend && (source.kind == "image" || source.kind == "video")
        val ttl = selfDestructSecs
        val item = OutgoingItem(
            localId = UUID.randomUUID().toString(),
            // "document" is called "file" everywhere else in the app.
            kind = if (source.kind == "document") "file" else source.kind,
            text = caption,
            fileName = source.fileName,
            localPath = source.file.absolutePath,
            mimeType = source.mimeType,
            stage = OutgoingStage.UPLOADING,
            rotation = rotation,
            trimStartMs = trimStart,
            trimEndMs = trimEnd,
            viewOnce = once,
            ttlSeconds = ttl
        )
        outbox = outbox + item
        deliverMedia(item, source)
    }

    /** Tries the failed message once more. */
    fun retryOutgoing(item: OutgoingItem) {
        if (item.kind == "text") {
            updateOutgoing(item.localId) {
                it.copy(stage = OutgoingStage.SENDING, error = null, progress = 0)
            }
            deliverText(item.localId, item.text, item.replyToId, item.replyToBody)
            return
        }
        val path = item.localPath ?: return
        val source = EditorSource(
            kind = item.kind,
            file = java.io.File(path),
            fileName = item.fileName ?: "File",
            mimeType = item.mimeType
        )
        val revived = item.copy(stage = OutgoingStage.UPLOADING, error = null, progress = 0)
        updateOutgoing(item.localId) { revived }
        deliverMedia(revived, source)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { openEditor(it, "image") }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { openEditor(it, "video") }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { openEditor(it, "document") }
    }

    // In-app camera: writes the shot into cacheDir/camera and hands the FileProvider URI
    // to the system camera activity, then sends it through the normal photo path.
    var pendingCamera by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (ok && uri != null) openEditor(uri, "image")
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingCamera = true
        } else {
            scope.launch { snackbar.showSnackbar("Allow camera access to take a photo.") }
        }
    }
    LaunchedEffect(pendingCamera) {
        if (!pendingCamera) return@LaunchedEffect
        pendingCamera = false
        runCatching {
            val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
            val file = java.io.File(dir, "cam-${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }.onFailure {
            scope.launch { snackbar.showSnackbar("The camera could not be opened on this device.") }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val instance = VoiceRecorder(context)
            if (instance.start()) {
                recorder = instance
                recordingMs = 0L
                recLocked = true
                recPaused = false
            } else {
                scope.launch { snackbar.showSnackbar("The microphone is unavailable right now.") }
            }
        }
    }

    fun beginRecording(locked: Boolean) {
        if (hasMicPermission(context)) {
            val instance = VoiceRecorder(context)
            if (instance.start()) {
                recorder = instance
                recordingMs = 0L
                recLocked = locked
                recPaused = false
            } else {
                scope.launch { snackbar.showSnackbar("The microphone is unavailable right now.") }
            }
        } else {
            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun finishRecording(sendIt: Boolean) {
        val active = recorder ?: return
        recorder = null
        recLocked = false
        recPaused = false
        val clip = active.stop()
        if (!sendIt || clip == null) {
            if (clip != null) runCatching { clip.file.delete() }
            return
        }
        val reply = replyingTo
        uploading = true
        scope.launch {
            try {
                val bytes = clip.file.readBytes()
                if (bytes.size > 900_000) throw ApiException("Voice note too long (60s max).")
                val blob = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                FirestoreChat.send(
                    chat.id, "",
                    fileName = "Voice message (${formatDuration(clip.durationMs)})",
                    kind = "voice",
                    durationMs = clip.durationMs,
                    audioBlob = blob,
                    viewOnce = voiceListenOnce,
                    ttlSeconds = selfDestructSecs,
                    replyToId = reply?.id,
                    replyToBody = reply?.text?.take(120)?.ifBlank { "[photo]" }
                )
                replyingTo = null
            } catch (e: Exception) {
                snackbar.showSnackbar((e as? ApiException)?.message ?: "Voice message not sent.")
            } finally {
                uploading = false
                runCatching { clip.file.delete() }
            }
        }
    }

    LaunchedEffect(recorder != null) {
        while (recorder != null) {
            delay(120)
            val active = recorder ?: break
            if (!active.paused) {
                active.sample()
                amplitude = active.amplitude()
                recordingMs = active.elapsedMs()
            }
            if (active.isOvertime()) {
                finishRecording(sendIt = true)
                break
            }
        }
    }

    val blockedPeerId = peerUser?.id?.takeIf { it.isNotBlank() }
    val isBlocked = blockedPeerId != null && AppSettings.isUserBlocked(blockedPeerId)

    Box(Modifier.fillMaxSize()) {
        // One continuous background: the wallpaper now also runs behind the composer and the
        // top bar instead of stopping where the message list ends.
        ChatWallpaper(
            uri = AppSettings.state.chatWallpaper,
            solid = AppSettings.state.chatWallpaperSolid,
            preset = AppSettings.wallpaperPresetFor(chat.id),
            modifier = Modifier.matchParentSize()
        )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (searching) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                        }
                    },
                    title = {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text("Search in chat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            }
                        )
                    }
                )
            } else {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Row(
                        Modifier.clickable {
                            if (chat.kind != "direct") onOpenGroupInfo(chat) else peerUser?.let(onOpenProfile)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name = title, photo = peerUser?.avatarUrl ?: chat.peer?.avatarUrl, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            if (peerTyping) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "typing",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    TypingDots(
                                        color = MaterialTheme.colorScheme.primary,
                                        dotSize = 4.dp
                                    )
                                }
                            } else {
                                Text(
                                    text = when {
                                        peerOnline == true -> "online"
                                        peerUser?.lastSeen != null -> "last seen ${friendlyWhen(peerUser?.lastSeen)}"
                                        else -> peerUser?.username?.let { "@$it" } ?: chat.peer?.username?.let { "@$it" } ?: "HolidayLink"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search") }
                    peerUser?.let { peer ->
                        IconButton(onClick = { onStartCall(peer, "audio") }) {
                            Icon(Icons.Default.Call, "Voice call")
                        }
                        IconButton(onClick = { onStartCall(peer, "video") }) {
                            Icon(Icons.Default.Videocam, "Video call")
                        }
                    }
                }
            )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val mention = remember(draft, aiBots) { parseMention(draft) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            // Keyboard and 3-button navigation never stack: use whichever is taller so the
            // composer sits above both the keyboard and the system navigation bar.
            val bottomInset = maxOf(
                WindowInsets.ime.getBottom(density),
                WindowInsets.navigationBars.getBottom(density)
            )
            Column(Modifier.padding(bottom = with(density) { bottomInset.toDp() })) {
                if (isBlocked && blockedPeerId != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "You blocked this contact. Unblock to keep chatting.",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { AppSettings.setUserBlocked(blockedPeerId, false) }) {
                            Text("Unblock")
                        }
                    }
                } else {
                if (mention != null && !sending && !aiSending) {
                    val (handle, botId, instruction) = mention
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                aiSending = true
                                scope.launch {
                                    try {
                                        val text = aiAnswerText(context, uid, instruction, botId)
                                        if (text.isBlank()) {
                                            snackbar.showSnackbar("HolidayLink AI couldn't answer.")
                                        } else {
                                            FirestoreChat.send(chat.id, text, kind = "text")
                                            draft = ""
                                            snackbar.showSnackbar("Sent via $handle")
                                        }
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar("HolidayLink AI couldn't answer.")
                                    }
                                    aiSending = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Send via $handle",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                ChatComposer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    editingId = editingId,
                    replyPreview = replyingTo?.let { it.text.take(80).ifBlank { "[photo]" } },
                    onCancelReply = { replyingTo = null; editingId = null; draft = "" },
                    sending = sending || uploading,
                    recording = recorder != null,
                    recordingMs = recordingMs,
                    amplitude = amplitude,
                    recLocked = recLocked,
                    recPaused = recorder?.paused == true,
                    listenOnce = voiceListenOnce,
                    onListenOnceChange = { voiceListenOnce = it },
                    onAttach = { showAttach = true },
                    onStickers = { showStickers = true },
                    onEmoji = { showEmoji = !showEmoji },
                    onSend = { send() },
                    onStartRecording = { beginRecording(locked = true); holding = false },
                    onHoldRecording = { beginRecording(locked = false); holding = true },
                    onStopRecording = { finishRecording(sendIt = true) },
                    onCancelRecording = { finishRecording(sendIt = false) },
                    onTogglePause = {
                        recorder?.let {
                            if (it.setPaused(!it.paused)) recPaused = it.paused
                        }
                    },
                    onLockRecording = { recLocked = true },
                    onReleaseHold = {
                        if (holding && recorder != null && !recLocked) {
                            holding = false
                            finishRecording(sendIt = true)
                        } else {
                            holding = false
                        }
                    }
                )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
            if (searching && query.isNotBlank()) {
                ChatSearchResults(
                    messages = messages,
                    query = query,
                    myUid = uid,
                    peerLabel = title,
                    onPick = { id ->
                        searching = false
                        query = ""
                        val i = messages.indexOfFirst { it.id == id }
                        if (i >= 0) scope.launch { listState.animateScrollToItem(i) }
                    }
                )
                return@Column
            }
            if (error.isNotBlank() && messages.isEmpty()) {
                ErrorState(error) { error = "" }
                return@Column
            }
            if (messages.isEmpty() && outbox.isEmpty()) {
                EmptyConversation(title = title)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(shownMessages, key = { _, m -> m.id }) { index, message ->
                        val day = dayOf(message)
                        val prevDay = if (index > 0) dayOf(shownMessages[index - 1]) else null
                        Column(Modifier.fillMaxWidth()) {
                            if (day != null && day != prevDay) {
                            DateSeparator(dayLabel(day))
                        }
                        if (index == unreadAnchor) {
                            UnreadDivider()
                        }
                        FsMessageBubble(
                            message = message,
                            mine = message.senderId == uid,
                            myUid = uid,
                            // Blue ticks need both people to allow them; a group has no single
                            // peer to ask, so only the local switch applies there.
                            receipts = AppSettings.state.readReceipts &&
                                (peerUser?.readReceipts ?: true),
                            starred = AppSettings.isMessageStarred(chat.id, message.id),
                            pending = message.senderId == uid && !message.read && message.text in pendingTexts,
                            onLongPress = { sheetFor = message },
                            onOpenImage = { photoUrl = it },
                            onJumpTo = { targetId ->
                                val i = shownMessages.indexOfFirst { it.id == targetId }
                                if (i >= 0) scope.launch { listState.animateScrollToItem(i) }
                            },
                            onReact = { emoji ->
                                val reacted = message.reactions[emoji]?.contains(uid) == true
                                scope.launch {
                                    runCatching { FirestoreChat.react(chat.id, message.id, emoji, !reacted) }
                                        .onFailure { snackbar.showSnackbar("Couldn't react") }
                                }
                            },
                            onOpenViewOnce = { target ->
                                if (target.senderId == uid) {
                                    scope.launch { snackbar.showSnackbar("View-once: the recipient opens it one time.") }
                                } else {
                                    viewOnceTarget = target
                                }
                            },
                            onSwipeReply = {
                                editingId = null
                                replyingTo = message
                            }
                        )
                        }
                    }
                    // Messages that are still travelling: they show the upload progress and
                    // a retry button when something went wrong.
                    items(outbox, key = { it.localId }) { item ->
                        OutgoingBubble(
                            item = item,
                            onRetry = { retryOutgoing(item) },
                            onDiscard = { outbox = outbox.filter { it.localId != item.localId } }
                        )
                    }
                    // WhatsApp's typing bubble: three moving dots in an incoming bubble at the
                    // very bottom, so the reply reads as being written rather than just announced.
                    if (peerTyping) {
                        item(key = "typing-bubble") { TypingBubble() }
                    }
                }
            }
        }
            // WhatsApp/Telegram-style: only shows when you have scrolled away from the newest.
            if (!atBottom && (messages.isNotEmpty() || outbox.isNotEmpty())) {
                SmallFloatingActionButton(
                    onClick = {
                        val total = shownMessages.size + outbox.size + (if (peerTyping) 1 else 0)
                        if (total > 0) scope.launch { listState.animateScrollToItem(total - 1) }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                ) { Icon(Icons.Default.KeyboardArrowDown, "Scroll to latest") }
            }
        }
    }
    }

    photoUrl?.let { url -> PhotoViewer(url = url, onDismiss = { photoUrl = null }) }

    // Everything the user picks goes through the editor first, exactly like WhatsApp.
    val editor = editorFor
    if (editor != null) {
        MediaEditorDialog(
            kind = editor.kind,
            file = editor.file,
            fileName = editor.fileName,
            mimeType = editor.mimeType,
            onCancel = { editorFor = null },
            onSend = { caption, rotation, trimStart, trimEnd, cropZoom, overlayText, strokes, cropAspectRatio ->
                editorFor = null
                scope.launch {
                    val editedSource = if (editor.kind == "image" && (rotation != 0 || cropZoom > 1f || overlayText.isNotBlank() || strokes.isNotEmpty())) {
                        val rendered = withContext(Dispatchers.IO) {
                            MediaEditor.applyPhotoEdits(editor.file, rotation, cropZoom, overlayText, strokes, cropAspectRatio)
                        }
                        if (rendered != null) editor.copy(file = rendered) else {
                            snackbar.showSnackbar("Some photo edits could not be applied; sending the original photo instead.")
                            editor
                        }
                    } else editor
                    enqueueMedia(editedSource, caption, if (editedSource === editor) rotation else 0, trimStart, trimEnd)
                }
            }
        )
    }

    // ---------------------------------------------------------------- sheets

    val sheetMsg = sheetFor
    if (sheetMsg != null) {
        val mine = sheetMsg.senderId == uid
        // Nothing to react to, copy or forward on a deleted row or a call log entry.
        val placeholder = sheetMsg.deleted || sheetMsg.type == "call"
        // WhatsApp rule: text messages editable within 2 minutes of sending.
        val editable = mine && sheetMsg.type == "text" && runCatching {
            val created = sheetMsg.createdAt?.seconds ?: 0
            (System.currentTimeMillis() / 1000 - created) < 120
        }.getOrDefault(false)
        ModalBottomSheet(
            onDismissRequest = { sheetFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp)) {
                if (placeholder) {
                    SheetAction("Message info", Icons.Default.Info, {
                        infoFor = sheetMsg
                        sheetFor = null
                    })
                } else {
                // Quick reactions (WhatsApp/Telegram). Tapping again removes your reaction.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("👍", "❤️", "😂", "😮", "😢", "🙏").forEach { emoji ->
                        val reacted = sheetMsg.reactions[emoji]?.contains(uid) == true
                        Text(
                            emoji,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    if (reacted) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    scope.launch {
                                        runCatching { FirestoreChat.react(chat.id, sheetMsg.id, emoji, !reacted) }
                                            .onFailure { snackbar.showSnackbar("Couldn't react") }
                                    }
                                    sheetFor = null
                                }
                                .padding(8.dp),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                if (!mine) {
                    SheetAction("Reply", Icons.Default.Reply, {
                        replyingTo = sheetMsg
                        sheetFor = null
                    })
                } else {
                    SheetAction("Reply", Icons.Default.Reply, {
                        replyingTo = sheetMsg
                        sheetFor = null
                    })
                    if (editable) {
                        SheetAction("Edit", Icons.Default.Edit, {
                            editingId = sheetMsg.id
                            draft = sheetMsg.text
                            sheetFor = null
                        })
                    }
                    SheetAction("Delete for everyone", Icons.Default.Delete, {
                        scope.launch {
                            // The row survives with its content stripped, so both phones show
                            // "This message was deleted" where it used to be.
                            runCatching { FirestoreChat.markDeleted(chat.id, sheetMsg.id) }
                                .onFailure { snackbar.showSnackbar("Couldn't delete that message") }
                        }
                        sheetFor = null
                    })
                }
                SheetAction("Forward", Icons.Default.Forward, {
                    forwardMsg = sheetMsg
                    sheetFor = null
                })
                val starredMsg = AppSettings.isMessageStarred(chat.id, sheetMsg.id)
                SheetAction(if (starredMsg) "Unstar message" else "Star message", if (starredMsg) Icons.Default.Star else Icons.Default.StarBorder) {
                    AppSettings.setMessageStarred(chat.id, sheetMsg.id, !starredMsg)
                    sheetFor = null
                }
                SheetAction("Copy text", Icons.Default.ContentCopy, {
                    copyToClipboard(context, sheetMsg.text)
                    sheetFor = null
                })
                SheetAction("Delete for me", Icons.Default.VisibilityOff, {
                    AppSettings.setMessageHidden(chat.id, sheetMsg.id, true)
                    sheetFor = null
                    scope.launch { snackbar.showSnackbar("Deleted for you") }
                })
                SheetAction("Message info", Icons.Default.Info, {
                    infoFor = sheetMsg
                    sheetFor = null
                })
                if (!mine) {
                    SheetAction("Report", Icons.Default.Flag, {
                        reportTarget = sheetMsg
                        sheetFor = null
                    })
                }
                }
            }
        }
    }

    val fwd = forwardMsg
    if (fwd != null) {
        ModalBottomSheet(
            onDismissRequest = { forwardMsg = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text(
                    "Forward to…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp)
                )
                if (forwardChats.isEmpty()) {
                    Text(
                        "Loading your chats…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp)
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(forwardChats, key = { it.id }) { row ->
                            val label = forwardNames[row.peerId]
                                ?: row.title.ifBlank {
                                    if (row.isGroup || row.isBroadcast || row.isCommunity) "Group" else "Contact"
                                }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            runCatching {
                                                FirestoreChat.send(
                                                    chatId = row.id,
                                                    text = fwd.text,
                                                    imageUrl = fwd.imageUrl,
                                                    fileUrl = fwd.fileUrl,
                                                    fileName = fwd.fileName,
                                                    kind = fwd.type,
                                                    durationMs = fwd.durationMs,
                                                    audioBlob = fwd.audioBlob
                                                )
                                            }.onSuccess { snackbar.showSnackbar("Forwarded") }
                                                .onFailure { snackbar.showSnackbar("Couldn't forward") }
                                        }
                                        forwardMsg = null
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(
                                    name = label,
                                    photo = null,
                                    online = false,
                                    size = 40.dp
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAttach) {
        ModalBottomSheet(
            onDismissRequest = { showAttach = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            AttachSheet(
                viewOnce = viewOnceSend,
                onViewOnceChange = { viewOnceSend = it },
                selfDestructSecs = selfDestructSecs,
                onSelfDestructChange = { selfDestructSecs = it },
                onImage = {
                    showAttach = false
                    imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onVideo = {
                    showAttach = false
                    videoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                onDocument = {
                    showAttach = false
                    filePicker.launch(arrayOf("*/*"))
                },
                onCamera = {
                    showAttach = false
                    if (hasCameraPermission(context)) {
                        pendingCamera = true
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            )
        }
    }

    if (showEmoji) {
        ModalBottomSheet(
            onDismissRequest = { showEmoji = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            EmojiSheet { emoji ->
                draft += emoji
                showEmoji = false
            }
        }
    }

    if (showStickers) {
        val packs = remember(context) { StickerCatalog.packs(context) }
        var selectedPack by remember(showStickers) { mutableStateOf(packs.firstOrNull()?.id.orEmpty()) }
        val current = packs.firstOrNull { it.id == selectedPack } ?: packs.firstOrNull()
        ModalBottomSheet(
            onDismissRequest = { showStickers = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("Stickers", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (packs.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        items(packs, key = { it.id }) { pack ->
                            val active = pack.id == current?.id
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .clickable { selectedPack = pack.id }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val preview = pack.stickers.firstOrNull()
                                if (preview != null) {
                                    AnimatedSticker(
                                        url = preview.ref,
                                        assetPath = preview.assetPath,
                                        resId = preview.resId,
                                        size = 30.dp
                                    )
                                }
                                Text(
                                    pack.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 180.dp, max = 420.dp)
                ) {
                    gridItems(current?.stickers.orEmpty(), key = { it.ref }) { s ->
                        StickerItem(
                            title = s.title,
                            ref = s.ref,
                            resId = s.resId,
                            assetPath = s.assetPath,
                            onPick = { stickerRef ->
                                scope.launch {
                                    runCatching { FirestoreChat.send(chat.id, "", kind = "sticker", imageUrl = stickerRef) }
                                        .onFailure { snackbar.showSnackbar("Sticker not sent. Check your connection and try again.") }
                                }
                                showStickers = false
                            }
                        )
                    }
                }
            }
        }
    }

    val onceTarget = viewOnceTarget
    if (onceTarget != null) {
        SecureViewOnceDialog(
            imageUrl = onceTarget.imageUrl,
            fileUrl = onceTarget.fileUrl,
            audioBlob = onceTarget.audioBlob,
            audioId = onceTarget.id,
            onClose = {
                viewOnceTarget = null
                scope.launch {
                    val storagePath = onceTarget.fileUrl?.let { urlToStoragePath(it) }
                    runCatching { FirestoreChat.consumeViewOnce(chat.id, onceTarget, storagePath) }
                }
            }
        )
    }

    val reportMsg = reportTarget
    if (reportMsg != null) {
        ReportDialog(
            onDismiss = { reportTarget = null },
            onSubmit = { reason ->
                reportTarget = null
                scope.launch {
                    runCatching {
                        FirebaseBackend.db.collection("reports").add(
                            mapOf(
                                "reporterId" to uid,
                                "chatId" to chat.id,
                                "messageId" to reportMsg.id,
                                "senderId" to reportMsg.senderId,
                                "reason" to reason,
                                "createdAt" to Timestamp.now()
                            )
                        ).await()
                    }.onSuccess { snackbar.showSnackbar("Thanks — the report was sent for review.") }
                        .onFailure { snackbar.showSnackbar("We couldn't send that report.") }
                }
            }
        )
    }

    val info = infoFor
    if (info != null) {
        MessageInfoDialog(
            chatId = chat.id,
            message = info,
            myUid = uid,
            receipts = AppSettings.state.readReceipts && (peerUser?.readReceipts ?: true)
        ) { infoFor = null }
    }
}

/** Resolve an @ai/@bot instruction into sendable text (Study Buddy brain). */
private suspend fun aiAnswerText(context: Context, uid: String, instruction: String, botId: String?): String {
    val action = StudyBuddy.ask(context, uid, instruction)
    return when (action) {
        is StudyBuddy.AiAction.Reply -> action.text
        is StudyBuddy.AiAction.SendMessage -> action.text
        is StudyBuddy.AiAction.Clarify -> action.text
        else -> ""
    }.let { text ->
        // Bot persona instructions apply when a bot was mentioned.
        if (!botId.isNullOrBlank() && text.isBlank()) "" else text
    }
}

private fun urlToStoragePath(url: String): String? {
    // https://firebasestorage.googleapis.com/.../o/<encoded-path>?alt=media
    return runCatching {
        val after = url.substringAfter("/o/", "")
        if (after.isBlank()) null else java.net.URLDecoder.decode(after.substringBefore("?"), "UTF-8")
    }.getOrNull()
}

/** In-chat search results (tap a hit to jump to it in the conversation). */
@Composable
private fun ChatSearchResults(
    messages: List<FirestoreChat.ChatMessage>,
    query: String,
    myUid: String,
    peerLabel: String,
    onPick: (String) -> Unit
) {
    val found = remember(messages, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else messages.filter { it.text.contains(q, ignoreCase = true) }
    }
    if (found.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No messages found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(found, key = { it.id }) { msg ->
            Column(
                Modifier.fillMaxWidth()
                    .clickable { onPick(msg.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    if (msg.senderId == myUid) "You" else peerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    msg.text.ifBlank { "[attachment]" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** WhatsApp-style message info: status, time, edit marker and reactions. */
@Composable
private fun MessageInfoDialog(
    chatId: String,
    message: FirestoreChat.ChatMessage,
    myUid: String,
    receipts: Boolean = true,
    onDismiss: () -> Unit
) {
    val mine = message.senderId == myUid
    val sentText = message.createdAt?.let { ts ->
        runCatching {
            android.text.format.DateFormat.format("MMM d, yyyy • h:mm a", ts.toDate()).toString()
        }.getOrNull()
    } ?: "—"
    val status = when {
        !mine -> "Received"
        // With read receipts off on either side the tick stays grey, so this dialog must not
        // leak the one thing the conversation has deliberately hidden.
        message.read && receipts -> "Read"
        message.read || message.delivered -> "Delivered"
        else -> "Sent"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Message info") },
        text = {
            Column {
                Text("Status: $status", style = MaterialTheme.typography.bodyMedium)
                Text("Sent: $sentText", style = MaterialTheme.typography.bodyMedium)
                if (message.edited) Text("Edited", style = MaterialTheme.typography.bodyMedium)
                if (message.reactions.isNotEmpty()) {
                    Text(
                        "Reactions: " + message.reactions.keys.joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "ID: $chatId/${message.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

// ------------------------------------------------------------------ bubbles

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FsMessageBubble(
    message: FirestoreChat.ChatMessage,
    mine: Boolean,
    myUid: String,
    starred: Boolean,
    pending: Boolean,
    onLongPress: () -> Unit,
    onOpenImage: (String) -> Unit,
    onReact: (String) -> Unit,
    onJumpTo: (String) -> Unit,
    onOpenViewOnce: (FirestoreChat.ChatMessage) -> Unit,
    onSwipeReply: () -> Unit = {},
    receipts: Boolean = true
) {
    val bubbleColor = if (mine) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (mine) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    // The full-screen in-app video player, opened by tapping a video bubble.
    var playingVideo by remember { mutableStateOf<java.io.File?>(null) }

    // A message deleted for everyone and a call log entry carry no content of their own,
    // so they get a plain pill and cannot be replied to or swiped.
    if (message.deleted || message.type == "call") {
        SystemBubble(message = message, mine = mine)
        return
    }

    // Swipe a message into the empty side of the row to reply, like Telegram: right for
    // messages you received, left for your own. Only horizontal drags are taken, so
    // scrolling the conversation up and down still works.
    var dragX by remember(message.id) { mutableFloatStateOf(0f) }
    val triggerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 54.dp.toPx() }
    // Own bubbles sit against the right edge, so they can only travel left without leaving
    // the screen; received bubbles sit against the left edge and travel right.
    val travel = if (mine) -triggerPx * 1.5f..0f else 0f..triggerPx * 1.5f

    Box(Modifier.fillMaxWidth()) {
        if (kotlin.math.abs(dragX) > 4f) {
            Icon(
                Icons.Default.Reply,
                contentDescription = "Swipe to reply",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = (kotlin.math.abs(dragX) / triggerPx).coerceIn(0.15f, 1f)
                ),
                modifier = Modifier
                    .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 8.dp)
                    .size(20.dp)
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(vertical = 1.dp)
                .offset { androidx.compose.ui.unit.IntOffset(dragX.toInt(), 0) }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragX) >= triggerPx) onSwipeReply()
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragX = (dragX + amount).coerceIn(travel.start, travel.endInclusive)
                        }
                    )
                },
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start
        ) {
        val isStickerMessage = message.type == "sticker" && !message.imageUrl.isNullOrBlank()
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (isStickerMessage) Modifier
                    else Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (mine) 16.dp else 4.dp,
                                bottomEnd = if (mine) 4.dp else 16.dp
                            )
                        )
                        .background(bubbleColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
        ) {
            Column {
                if (message.replyToBody != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .clickable { message.replyToId?.let(onJumpTo) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Column {
                            message.replyToName?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                message.replyToBody,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (message.viewOnce && (message.imageUrl != null || message.fileUrl != null || message.audioBlob != null)) {
                    ViewOnceRow(mine = mine, onOpen = { onOpenViewOnce(message) })
                } else if (message.type == "sticker" && !message.imageUrl.isNullOrBlank()) {
                    AnimatedSticker(
                        url = message.imageUrl,
                        assetPath = stickerAssetPath(message.imageUrl),
                        size = 164.dp,
                        modifier = Modifier.padding(2.dp)
                    )
                } else if (message.audioBlob != null) {
                    BlobVoicePlayer(
                        messageId = message.id,
                        blob = message.audioBlob,
                        name = message.fileName ?: "Voice message (${formatDuration(message.durationMs ?: 0L)})"
                    )
                    Spacer(Modifier.height(6.dp))
                    if (message.text.isNotBlank()) {
                        Text(message.text, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                } else {
                    message.imageUrl?.let { url ->
                        RemoteImage(
                            url = url,
                            modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenImage(url) },
                            preserveAspect = true
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    message.fileUrl?.let { url ->
                        if (message.type == "video") {
                            // WhatsApp-style video: a thumbnail with a play button that
                            // opens the in-app full-screen player (no other app needed).
                            VideoBubble(
                                url = url,
                                name = message.fileName ?: "Video",
                                onOpen = { file -> playingVideo = file }
                            )
                        } else {
                            FsFileRow(
                                name = message.fileName ?: "File",
                                kind = message.type,
                                url = url
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (message.text.isNotBlank()) {
                        Text(message.text, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                }
            }
        }
        if (message.reactions.isNotEmpty()) {
            Row(
                Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                message.reactions.forEach { (emoji, uids) ->
                    val mineReacted = uids.contains(myUid)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (mineReacted) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable { onReact(emoji) }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, style = MaterialTheme.typography.labelMedium)
                        if (uids.size > 1) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${uids.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            if (starred) {
                Icon(
                    Icons.Default.Star,
                    "Starred",
                    Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                clockOf(message.createdAt?.toDate()?.toInstant()?.toString()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (message.edited) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "edited",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            if (message.expiresAt != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Timer, "Self-destructs", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (mine) {
                Spacer(Modifier.width(4.dp))
                // Sent (single grey) → delivered (double grey) → read (double blue).
                // `delivered` is set by the recipient's device, so a freshly sent message
                // stays on a single tick until it actually arrives — not immediately.
                StatusTick(
                    when {
                        message.read -> "read"
                        message.delivered -> "delivered"
                        else -> "sent"
                    },
                    receipts
                )
            }
        }
        }
    }
    playingVideo?.let { file ->
        VideoPlayerDialog(file = file, onDismiss = { playingVideo = null })
    }
}

/**
 * The rows that stand in for content that is gone or was never a message: "This message was
 * deleted" after a delete-for-everyone, and the call log (missed, declined or the length of
 * a call that was picked up). Both stay in the thread so it still reads in order.
 */
@Composable
private fun SystemBubble(message: FirestoreChat.ChatMessage, mine: Boolean) {
    val deleted = message.deleted
    val video = message.callKind == "video"
    val seconds = message.callSeconds ?: 0
    val label = when {
        deleted -> "This message was deleted"
        message.callStatus == "missed" -> if (video) "Missed video call" else "Missed voice call"
        message.callStatus == "declined" -> if (video) "Declined video call" else "Declined voice call"
        message.callStatus == "failed" -> if (video) "Video call didn't connect" else "Voice call didn't connect"
        seconds > 0 -> if (video) "Video call · ${formatDuration(seconds * 1000)}"
        else "Voice call · ${formatDuration(seconds * 1000)}"
        else -> if (video) "Video call" else "Voice call"
    }
    val tint = when {
        deleted -> MaterialTheme.colorScheme.onSurfaceVariant
        message.callStatus == "missed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Row(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (deleted) Icons.Default.Block else if (video) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (deleted) FontStyle.Italic else FontStyle.Normal,
                color = if (deleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text(
                clockOf(message.createdAt?.toDate()?.toInstant()?.toString()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FsFileRow(name: String, kind: String, url: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extension = if (kind == "video") "mp4" else "bin"
    var busy by remember(url) { mutableStateOf(false) }
    var cached by remember(url) { mutableStateOf(MediaCache.cached(context, url, extension) != null) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .clickable {
                if (busy) return@clickable
                busy = true
                scope.launch {
                    // Downloaded once, then opened from the app cache every time after.
                    val file = MediaCache.fetch(context, url, extension)
                    busy = false
                    cached = file != null && file.exists()
                    if (file == null) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        return@launch
                    }
                    runCatching {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, if (kind == "video") "video/*" else mimeForFile(name))
                                addFlags(
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                )
                            }
                        )
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Default.Description,
                null,
                Modifier.size(22.dp),
                tint = if (cached) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** MIME used when handing a cached document to another app. */
private fun mimeForFile(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "ppt" -> "application/vnd.ms-powerpoint"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "txt" -> "text/plain"
    "zip" -> "application/zip"
    "mp4" -> "video/mp4"
    "3gp" -> "video/3gpp"
    "mkv" -> "video/x-matroska"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    else -> "*/*"
}

/**
 * A video message, WhatsApp-style: the first frame as the thumbnail with a play button
 * over it. Tapping opens the in-app full-screen player — the file is downloaded once and
 * then replayed straight from the app cache, no other app involved.
 */
@Composable
private fun VideoBubble(url: String, name: String, onOpen: (java.io.File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(url) { mutableStateOf(false) }
    var thumb by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(url) {
        // A cached file gets its real first frame; otherwise the dark placeholder shows
        // until the first tap downloads it (and the thumb then appears for replays).
        val cached = MediaCache.cached(context, url, "mp4") ?: return@LaunchedEffect
        thumb = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(cached.absolutePath)
                val frame = retriever.getFrameAtTime(0)
                retriever.release()
                frame
            }.getOrNull()
        }
    }

    Box(
        Modifier.width(220.dp).height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable {
                if (busy) return@clickable
                val alreadyCached = MediaCache.cached(context, url, "mp4")
                if (alreadyCached != null) {
                    onOpen(alreadyCached)
                } else {
                    busy = true
                    scope.launch {
                        val file = MediaCache.fetch(context, url, "mp4")
                        busy = false
                        if (file != null) {
                            // First frame for the bubble now that it lives on this phone.
                            thumb = withContext(Dispatchers.IO) {
                                runCatching {
                                    val retriever = android.media.MediaMetadataRetriever()
                                    retriever.setDataSource(file.absolutePath)
                                    val frame = retriever.getFrameAtTime(0)
                                    retriever.release()
                                    frame
                                }.getOrNull()
                            }
                            onOpen(file)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        thumb?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(23.dp))
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * The in-app video viewer: full-screen playback with the platform's own controls
 * (play/pause, seek, fullscreen) plus a close button — same feel as opening a video
 * inside WhatsApp instead of bouncing out to another player app.
 */
@Composable
private fun VideoPlayerDialog(file: java.io.File, onDismiss: () -> Unit) {
    var videoView by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    val saveToGallery = rememberSaveToGallery()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoPath(file.absolutePath)
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            duration = mp.duration
                            start()
                            playing = true
                        }
                        setOnCompletionListener { playing = false }
                        videoView = this
                    }
                },
                update = { view ->
                    if (playing && !view.isPlaying) view.start()
                    else if (!playing && view.isPlaying) view.pause()
                },
                modifier = Modifier.fillMaxSize().clickable { controlsVisible = !controlsVisible }
            )
            // Track playback progress for the scrubber.
            LaunchedEffect(playing) {
                while (playing) {
                    delay(250)
                    runCatching {
                        val v = videoView ?: return@runCatching
                        if (v.isPlaying) {
                            position = v.currentPosition
                            duration = v.duration
                        }
                    }
                }
            }
            if (controlsVisible) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = { saveToGallery(null, file) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Icon(Icons.Default.Download, "Save to gallery", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = {
                        val v = videoView ?: return@IconButton
                        if (v.isPlaying) { v.pause(); playing = false } else { v.start(); playing = true }
                    },
                    modifier = Modifier.align(Alignment.Center).size(64.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(32.dp))
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatDuration(position.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                    val total = duration.coerceAtLeast(1)
                    Slider(
                        value = position.toFloat().coerceAtMost(total.toFloat()),
                        onValueChange = { seekTo ->
                            videoView?.seekTo(seekTo.toInt())
                            position = seekTo.toInt()
                        },
                        valueRange = 0f..total.toFloat(),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(
                        formatDuration(duration.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
    DisposableEffect(file) {
        onDispose { runCatching { videoView?.stopPlayback() } }
    }
}

@Composable
private fun BlobVoicePlayer(messageId: String, blob: String, name: String) {
    val context = LocalContext.current
    // Decoded once into the app cache: the note stays on this phone for replay.
    val filePath = remember(messageId, blob.length) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "voice").apply { mkdirs() }
            val file = java.io.File(dir, "msg-$messageId.m4a")
            if (!file.exists() || file.length() == 0L) {
                val bytes = android.util.Base64.decode(blob, android.util.Base64.DEFAULT)
                file.writeBytes(bytes)
            }
            file.absolutePath
        }.getOrNull()
    }
    if (filePath == null) {
        Text("Voice note couldn't be loaded.", style = MaterialTheme.typography.bodySmall)
        return
    }
    VoicePlayer(filePath = filePath, name = name)
}

@Composable
private fun VoicePlayer(url: String, name: String) {
    VoicePlayer(filePath = null, streamUrl = url, name = name)
}

@Composable
private fun VoicePlayer(filePath: String? = null, streamUrl: String? = null, name: String) {
    val key = filePath ?: streamUrl.orEmpty()
    var playing by remember(key) { mutableStateOf(false) }
    var progress by remember(key) { mutableFloatStateOf(0f) }
    var duration by remember(key) { mutableIntStateOf(0) }
    var position by remember(key) { mutableIntStateOf(0) }
    var failed by remember(key) { mutableStateOf(false) }
    val player = remember(key) { android.media.MediaPlayer() }
    DisposableEffect(key) {
        onDispose {
            runCatching { player.reset(); player.release() }
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            delay(250)
            runCatching {
                if (player.isPlaying) {
                    position = player.currentPosition
                    val d = player.duration.coerceAtLeast(1)
                    duration = d
                    progress = position.toFloat() / d
                } else if (position >= duration && duration > 0) {
                    playing = false
                    position = 0
                    progress = 0f
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // No inner surface box: the note now blends into the message bubble itself
            // instead of showing a box-inside-a-box "boundary".
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            failed = false
            runCatching {
                if (playing) {
                    player.pause()
                    playing = false
                } else {
                    if (position <= 0) {
                        player.reset()
                        when {
                            filePath != null -> player.setDataSource(filePath)
                            streamUrl != null -> player.setDataSource(streamUrl)
                            else -> throw IllegalStateException("no audio source")
                        }
                        player.prepare()
                        duration = player.duration
                    }
                    player.start()
                    playing = true
                }
            }.onFailure { failed = true }
        }) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (failed) {
                Text(
                    "Couldn't play this note.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                "${formatDuration(position.toLong())} / ${formatDuration(duration.toLong())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The "somebody is writing" bubble: an incoming bubble holding nothing but the three dots. */
@Composable
private fun TypingBubble() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            TypingDots(dotSize = 7.dp)
        }
    }
}

@Composable
private fun StatusTick(status: String, receipts: Boolean = true) {
    val tint = when {
        // Read receipts off on either side means no blue ticks in this conversation, which is
        // the whole point of the switch: it has to hold in both directions at once.
        status == "read" && receipts -> MaterialTheme.colorScheme.primary
        status == "read" || status == "delivered" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Icon(
        imageVector = if (status == "sent") Icons.Default.Done else Icons.Default.DoneAll,
        contentDescription = status,
        modifier = Modifier.size(14.dp),
        tint = tint
    )
}

@Composable
private fun ViewOnceRow(mine: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.VisibilityOff, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
            if (mine) "View-once sent" else "View once — tap to open",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Fullscreen view-once viewer. FLAG_SECURE blocks screenshots and screen
 * recording while open (a physical camera photo is still possible).
 * Closing destroys the message via consumeViewOnce.
 */
@Composable
private fun SecureViewOnceDialog(
    imageUrl: String?,
    fileUrl: String?,
    audioBlob: String? = null,
    audioId: String = "",
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: context as? android.app.Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (imageUrl != null) {
                    RemoteImage(
                        url = imageUrl,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(16.dp)
                    )
                } else if (audioBlob != null) {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        BlobVoicePlayer(
                            messageId = "once-$audioId",
                            blob = audioBlob,
                            name = "View-once voice note"
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Listen once — closing destroys it", color = Color.White)
                } else {
                    Icon(Icons.Default.Movie, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("View-once video — tap to close and destroy it", color = Color.White)
                }
                Text(
                    "Screenshot and recording are blocked • closes after viewing",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachSheet(
    viewOnce: Boolean,
    onViewOnceChange: (Boolean) -> Unit,
    selfDestructSecs: Long,
    onSelfDestructChange: (Long) -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit,
    onDocument: () -> Unit,
    onCamera: () -> Unit
) {    val timers = listOf(0L to "Off", 5L to "5s", 30L to "30s", 60L to "1m", 3600L to "1h", 86400L to "1d")
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp)) {
        SheetAction("Photo", Icons.Default.PhotoCamera, onImage)
        SheetAction("Video", Icons.Default.Movie, onVideo)
        SheetAction("Document", Icons.Default.Description, onDocument)
        SheetAction("Camera", Icons.Default.PhotoCamera, onCamera)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VisibilityOff, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("View once", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Photo/video opens one time, no screenshot",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = viewOnce, onCheckedChange = onViewOnceChange)
        }
        Text(
            "Self-destruct timer",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            timers.forEach { (secs, label) ->
                FilterChip(
                    selected = selfDestructSecs == secs,
                    onClick = { onSelfDestructChange(secs) },
                    label = { Text(label) }
                )
            }
        }
    }
}

// ------------------------------------------------------------------ composer

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    editingId: String?,
    replyPreview: String?,
    onCancelReply: () -> Unit,
    sending: Boolean,
    recording: Boolean,
    recordingMs: Long,
    amplitude: Int,
    recLocked: Boolean,
    recPaused: Boolean,
    listenOnce: Boolean,
    onListenOnceChange: (Boolean) -> Unit,
    onAttach: () -> Unit,
    onStickers: () -> Unit,
    onEmoji: () -> Unit,
    onSend: () -> Unit,
    onStartRecording: () -> Unit,
    onHoldRecording: () -> Unit,
    onReleaseHold: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onTogglePause: () -> Unit,
    onLockRecording: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (editingId != null || replyPreview != null) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (if (editingId != null) "Editing: " else "Replying: ") + (replyPreview ?: draft),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCancelReply, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Cancel", Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (recording) {
            // WhatsApp-style bar: hold, or swipe up to lock, left to delete.
            var dragX by remember { mutableStateOf(0f) }
            var dragY by remember { mutableStateOf(0f) }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragX += dragAmount.x
                                dragY += dragAmount.y
                                if (dragX < -160) {
                                    dragX = 0f; dragY = 0f
                                    onCancelRecording()
                                } else if (dragY < -160) {
                                    dragY = 0f
                                    onLockRecording()
                                }
                            },
                            onDragEnd = { dragX = 0f; dragY = 0f }
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "%d:%02d".format(recordingMs / 60000, (recordingMs / 1000) % 60),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (recPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                // Amplitude shimmer.
                Box(
                    Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        Modifier.fillMaxWidth((amplitude / 100f).coerceIn(0.05f, 1f)).height(24.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    )
                }
                IconButton(onClick = onTogglePause) {
                    Icon(
                        if (recPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        if (recPaused) "Resume" else "Pause"
                    )
                }
                IconButton(
                    onClick = { onListenOnceChange(!listenOnce) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.VisibilityOff, "Listen once",
                        tint = if (listenOnce) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (recLocked) {
                    IconButton(onClick = onStopRecording) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Text(
                        "release to send",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCancelRecording) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (!recLocked) {
                Text(
                    "Hold to record • slide up to lock • slide left to delete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                )
            }
            return
        }
        Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach) { Icon(Icons.Default.AttachFile, "Attach") }
                IconButton(onClick = onStickers) { Icon(Icons.Default.EmojiEmotions, "Stickers") }
                BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text("Message", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            )
            IconButton(onClick = onEmoji) { Icon(Icons.Default.EmojiEmotions, "Emoji") }
            if (draft.isNotBlank() || editingId != null) {
                // Always tappable: the bubble appears straight away, so the button never
                // feels stuck while the network is busy.
                IconButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                // Tap = locked hands-free, hold = push-to-talk (release sends).
                Box(
                    Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onStartRecording() },
                                onLongPress = { onHoldRecording() },
                                onPress = {
                                    val released = try {
                                        tryAwaitRelease()
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (released) onReleaseHold()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, "Voice message")
                }
            }
        }
    }
}

@Composable
private fun SheetAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmojiSheet(onPick: (String) -> Unit) {
    val picker = listOf(
        "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83D\uDE21",
        "\uD83D\uDE22", "\uD83D\uDE0A", "\uD83D\uDE14", "\uD83D\uDE15", "\uD83D\uDE00",
        "\u2764\uFE0F", "\uD83D\uDC96", "\uD83D\uDE4F", "\uD83D\uDD25", "\uD83C\uDF89",
        "\uD83C\uDFAF", "\uD83D\uDCDD", "\u2705", "\u274C", "\u2B50",
        "\uD83D\uDCDA", "\uD83D\uDDE3", "\uD83D\uDE80", "\u26A1", "\uD83C\uDF1E"
    )
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        picker.chunked(6).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { emoji ->
                    Text(
                        emoji,
                        fontSize = 24.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onPick(emoji) }.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report message") },
        text = {
            Column {
                Text("Why are you reporting this message?")
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 3,
                    decorationBox = { inner ->
                        if (reason.isEmpty()) Text("Reason (optional)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(reason.trim()) }) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    preserveAspect: Boolean = false
) {
    val context = LocalContext.current
    // Instant first frame from memory; otherwise decode the disk copy; otherwise download
    // the image once and keep it for every future open.
    var bitmap by remember(url) { mutableStateOf(MediaCache.peek(url)) }
    LaunchedEffect(url) {
        if (bitmap == null) bitmap = MediaCache.bitmap(context, url)
    }
    val image = bitmap
    if (image == null) {
        Box(
            modifier.height(140.dp).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading…", style = MaterialTheme.typography.labelSmall)
        }
    } else {
        // Match the box to the picture's aspect ratio so ContentScale.Fit never leaves the
        // bubble colour (the "green bit") showing along an edge of the photo.
        val ratio = image.width.toFloat() / image.height.toFloat().coerceAtLeast(1f)
        val sized = if (preserveAspect && ratio > 0f) modifier.aspectRatio(ratio) else modifier
        androidx.compose.foundation.Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "Attachment",
            modifier = sized,
            contentScale = contentScale
        )
    }
}

@Composable
private fun EmptyConversation(title: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name = title, size = 72.dp)
            Spacer(Modifier.height(14.dp))
            Text("No messages yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Say hello to start the conversation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun clockOf(iso: String?): String = friendlyWhen(iso)

private fun hasMicPermission(context: Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun hasCameraPermission(context: Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** Used by "download on Wi-Fi only" so prefetching respects mobile-data limits. */
private fun onWifi(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return@runCatching false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
}.getOrDefault(false)

/** Whether the user's auto-download setting allows fetching this kind of attachment now. */
private fun downloadsAllowed(mode: AutoDownload, context: Context): Boolean = when (mode) {
    AutoDownload.ALWAYS -> true
    AutoDownload.NEVER -> false
    AutoDownload.WIFI_ONLY -> onWifi(context)
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("HolidayLink", text))
}

private fun dayOf(message: org.holidaylink.app.net.FirestoreChat.ChatMessage): java.time.LocalDate? =
    message.createdAt?.toDate()?.toInstant()
        ?.atZone(java.time.ZoneId.systemDefault())
        ?.toLocalDate()

private fun dayLabel(date: java.time.LocalDate): String {
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun UnreadDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            "Unread messages",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
