@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package org.holidaylink.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.data.DisappearingDuration
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.AiBot
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.Attachment
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.Message
import org.holidaylink.app.net.Profile
import org.holidaylink.app.net.User
import org.holidaylink.app.voice.VoiceClip
import org.holidaylink.app.voice.VoiceRecorder

private val ReactionEmojis = listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F")

/**
 * Conversation screen.
 *
 * Every control here is wired to a real backend call or a real Android capability: there are no
 * placeholder buttons. Features the backend does not implement yet (calls, status) are surfaced
 * on their own screens rather than as dead icons here.
 */
@Composable
fun ChatScreen(
    api: Api,
    chat: ChatItem,
    currentUserId: String,
    onBack: () -> Unit,
    onOpenProfile: (User) -> Unit,
    onOpenCalls: (User) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var peerTyping by remember { mutableStateOf(false) }

    var sheetFor by remember { mutableStateOf<Message?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    // Telegram-style sending options, kept sticky like the timer.
    var viewOnceSend by remember { mutableStateOf(false) }
    var selfDestructSecs by remember { mutableLongStateOf(0L) }
    var viewOnceTarget by remember { mutableStateOf<Message?>(null) }
    var showEmoji by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showPinned by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Message?>(null) }

    var recorder by remember { mutableStateOf<VoiceRecorder?>(null) }
    var recordingMs by remember { mutableLongStateOf(0L) }
    var amplitude by remember { mutableIntStateOf(0) }
    var micDenied by remember { mutableStateOf(false) }
    var aiBots by remember { mutableStateOf<List<AiBot>>(emptyList()) }
    var aiSending by remember { mutableStateOf(false) }

    // @ai / @bot mentions for the Meta-AI style assistant. Loaded once per chat.
    LaunchedEffect(chat.id) {
        runCatching { aiBots = api.aiBots() }
    }

    /** Returns (handle, botId?, instruction) when the draft mentions the assistant. */
    fun parseMention(text: String): Triple<String, String?, String>? {
        val match = Regex("@([A-Za-z0-9_]{2,32})\\s+([^\\n]+)").find(text) ?: return null
        val handle = match.groupValues[1].lowercase()
        val instruction = match.groupValues[2].trim()
        if (instruction.isEmpty()) return null
        if (handle == "ai") return Triple("@ai", null, instruction)
        val bot = aiBots.firstOrNull { it.username.lowercase() == handle } ?: return null
        return Triple("@${bot.username}", bot.id, instruction)
    }

    val title = chat.peer?.displayName ?: chat.title ?: "Group"
    val settings = AppSettings.state
    // Search can open a chat before its peer is fully resolved; actions that need a real
    // person (profile, call) stay hidden until the id is known.
    val peerUser = chat.peer?.takeIf { it.id.isNotBlank() }

    // ---------------------------------------------------------------- loading

    suspend fun reloadMessages() {
        try {
            messages = api.messages(chat.id)
            error = ""
            runCatching { api.markConversationRead(chat.id) }
        } catch (e: Exception) {
            error = (e as? ApiException)?.message ?: "We couldn't load this conversation."
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(chat.id, reload) {
        loading = messages.isEmpty()
        reloadMessages()
    }

    // Light polling stands in for a WebSocket client and keeps typing + receipts fresh.
    LaunchedEffect(chat.id) {
        while (true) {
            delay(7_000)
            if (recorder?.isRecording != true) {
                runCatching {
                    val fresh = api.conversations().firstOrNull { it.id == chat.id }
                    if (fresh != null) peerTyping = fresh.peerTyping
                    val latest = api.messages(chat.id, limit = 50)
                    if (latest.size != messages.size) {
                        messages = latest
                        runCatching { api.markConversationRead(chat.id) }
                    }
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // ---------------------------------------------------------------- actions

    fun send() {
        val body = draft.trim()
        if (body.isEmpty() || sending) return
        val editId = editingId
        sending = true
        scope.launch {
            try {
                if (editId != null) {
                    api.editMessage(chat.id, editId, body)
                    editingId = null
                } else {
                    api.sendMessage(chat.id, body, replyingTo?.id)
                    replyingTo = null
                }
                draft = ""
                reloadMessages()
            } catch (e: Exception) {
                snackbar.showSnackbar((e as? ApiException)?.message ?: "Message not sent.")
            } finally {
                sending = false
            }
        }
    }

    fun sendAttachment(uri: Uri, kind: String) {
        sending = true
        val once = viewOnceSend && (kind == "image" || kind == "video")
        val ttl = selfDestructSecs
        scope.launch {
            try {
                val picked = MediaPicker.inspect(context, uri)
                val dataUrl = when (kind) {
                    "image" -> MediaPicker.imageDataUrl(context, uri)
                    else -> MediaPicker.fileDataUrl(context, uri, picked.mimeType)
                }
                if (dataUrl == null) {
                    snackbar.showSnackbar("That file is too large to send (max 20 MB).")
                } else {
                    api.sendAttachment(chat.id, kind, picked.name, dataUrl, viewOnce = once, ttlSeconds = ttl)
                    reloadMessages()
                }
            } catch (e: Exception) {
                snackbar.showSnackbar((e as? ApiException)?.message ?: "Attachment not sent.")
            } finally {
                sending = false
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { sendAttachment(it, "image") } }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { sendAttachment(it, "video") } }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { sendAttachment(it, "document") } }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micDenied = !granted
        if (granted) {
            val instance = VoiceRecorder(context)
            if (instance.start()) {
                recorder = instance
                recordingMs = 0L
            } else {
                scope.launch { snackbar.showSnackbar("The microphone is unavailable right now.") }
            }
        }
    }

    fun beginRecording() {
        if (hasMicPermission(context)) {
            val instance = VoiceRecorder(context)
            if (instance.start()) {
                recorder = instance
                recordingMs = 0L
            } else {
                scope.launch { snackbar.showSnackbar("The microphone is unavailable right now.") }
            }
        } else {
            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun finishRecording(send: Boolean) {
        val active = recorder ?: return
        recorder = null
        val clip = active.stop()
        if (!send || clip == null) return
        sending = true
        scope.launch {
            try {
                val dataUrl = MediaPicker.fileDataUrl(context, Uri.fromFile(clip.file), "audio/mp4")
                if (dataUrl == null) {
                    snackbar.showSnackbar("Recording couldn't be read.")
                } else {
                    api.sendAttachment(chat.id, "voice", "voice-${clip.durationMs}.m4a", dataUrl, clip.durationMs, clip.waveform)
                    reloadMessages()
                }
            } catch (e: Exception) {
                snackbar.showSnackbar((e as? ApiException)?.message ?: "Voice message not sent.")
            } finally {
                sending = false
                runCatching { clip.file.delete() }
            }
        }
    }

    // While recording, sample the amplitude for the waveform and auto-stop at the cap.
    LaunchedEffect(recorder != null) {
        while (recorder != null) {
            delay(120)
            val active = recorder ?: break
            active.sample()
            amplitude = active.amplitude()
            recordingMs += 120
            if (active.isOvertime()) {
                finishRecording(send = true)
                break
            }
        }
    }

    fun openInfo() {
        showInfo = true
    }

    // ---------------------------------------------------------------- scaffold

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    Row(
                            Modifier.clickable { peerUser?.let(onOpenProfile) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Avatar(
                            name = title,
                            photo = chat.peer?.avatarUrl,
                            online = chat.peer?.online,
                            size = 38.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                text = when {
                                    peerTyping -> "typing…"
                                    chat.peer?.online == true -> "online"
                                    settings.disappearing != DisappearingDuration.OFF ->
                                        "messages disappear after ${settings.disappearing.label.lowercase()}"
                                    else -> chat.peer?.username?.let { "@$it" } ?: "HolidayLink"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (peerTyping) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                },
                actions = {
                    peerUser?.let { peer ->
                        IconButton(onClick = { onOpenCalls(peer) }) {
                            Icon(Icons.Default.Call, "Call")
                        }
                    }
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Default.Search, "Search in chat")
                    }
                    IconButton(onClick = { openInfo() }) {
                        Icon(Icons.Default.MoreVert, "Chat info")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val mention = remember(draft, aiBots) { parseMention(draft) }
            Column {
                if (mention != null && !sending && !aiSending) {
                    val (handle, botId, instruction) = mention
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                aiSending = true
                                scope.launch {
                                    runCatching { api.aiSend(chat.id, instruction, botId) }
                                        .onSuccess {
                                            draft = ""
                                            reloadMessages()
                                            snackbar.showSnackbar("Sent via $handle")
                                        }
                                        .onFailure {
                                            snackbar.showSnackbar((it as? ApiException)?.message ?: "HolidayLink AI couldn't answer.")
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
                        if (aiSending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                ChatComposer(
                draft = draft,
                onDraftChange = { draft = it },
                editingId = editingId,
                replyingTo = replyingTo,
                onCancelReply = {
                    replyingTo = null
                    editingId = null
                    draft = ""
                },
                sending = sending,
                recording = recorder != null,
                recordingMs = recordingMs,
                amplitude = amplitude,
                onAttach = { showAttach = true },
                onEmoji = { showEmoji = !showEmoji },
                onSend = { send() },
                onStartRecording = { beginRecording() },
                onStopRecording = { finishRecording(send = true) },
                onCancelRecording = { finishRecording(send = false) }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (error.isNotBlank() && messages.isEmpty()) {
                ErrorState(error) { reload++ }
                return@Column
            }
            if (chat.peer != null && !settings.disappearing.equals(DisappearingDuration.OFF)) {
                DisappearingBanner(settings.disappearing)
            }
            if (loading && messages.isEmpty()) {
                LoadingState(label = "Loading messages…")
                return@Column
            }

            PullToRefreshContainer(
                refreshing = refreshing,
                onRefresh = { refreshing = true; reload++ },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (messages.isEmpty()) {
                        EmptyConversation(title = title)
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    mine = message.senderId == currentUserId,
                                    onLongPress = { sheetFor = message },
                                    onOpenViewOnce = { target ->
                                        if (target.senderId == currentUserId) {
                                            scope.launch { snackbar.showSnackbar("View-once: the recipient opens it one time.") }
                                        } else {
                                            viewOnceTarget = target
                                        }
                                    }
                                )
                            }
                            if (peerTyping) {
                                item(key = "typing") { TypingBubble() }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- sheets

    if (sheetFor != null) {
        val message = sheetFor!!
        ModalBottomSheet(
            onDismissRequest = { sheetFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            MessageActionsSheet(
                message = message,
                mine = message.senderId == currentUserId,
                onReply = {
                    replyingTo = message
                    sheetFor = null
                },
                onEdit = {
                    editingId = message.id
                    draft = message.body
                    sheetFor = null
                },
                onDelete = {
                    scope.launch {
                        runCatching { api.deleteMessage(chat.id, message.id) }
                            .onSuccess {
                                snackbar.showSnackbar("Message deleted")
                                reloadMessages()
                            }
                            .onFailure { snackbar.showSnackbar("Couldn't delete that message") }
                    }
                    sheetFor = null
                },
                onPin = {
                    scope.launch {
                        runCatching { api.pinMessage(chat.id, message.id, !message.pinned) }
                            .onSuccess {
                                snackbar.showSnackbar(if (message.pinned) "Unpinned" else "Pinned to chat")
                                reloadMessages()
                            }
                    }
                    sheetFor = null
                },
                onCopy = {
                    copyToClipboard(context, message.body)
                    sheetFor = null
                },
                onForward = {
                    shareText(context, message.body)
                    sheetFor = null
                },
                onReport = {
                    reportTarget = message
                    sheetFor = null
                },
                onReact = { emoji ->
                    scope.launch {
                        runCatching { api.reactToMessage(chat.id, message.id, emoji) }
                            .onSuccess { reloadMessages() }
                            .onFailure { snackbar.showSnackbar("Reaction failed") }
                    }
                    sheetFor = null
                }
            )
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
                    imagePicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                onVideo = {
                    showAttach = false
                    videoPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.VideoOnly
                        )
                    )
                },
                onDocument = {
                    showAttach = false
                    filePicker.launch(arrayOf("*/*"))
                },
                onCamera = {
                    showAttach = false
                    scope.launch {
                        snackbar.showSnackbar("Use the photo picker for now — in-app camera is coming.")
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

    val onceTarget = viewOnceTarget
    if (onceTarget?.attachment != null) {
        SecureViewOnceDialog(
            attachment = onceTarget.attachment,
            onClose = {
                viewOnceTarget = null
                // Destroy server-side after the single opening, then drop the local copy.
                scope.launch {
                    runCatching { api.markMessageViewed(chat.id, onceTarget.id) }
                    messages = messages.filterNot { it.id == onceTarget.id }
                    reloadMessages()
                }
            }
        )
    }

    if (showSearch) {
        ChatSearchSheet(
            api = api,
            conversationId = chat.id,
            onDismiss = { showSearch = false },
            onJump = { messageId ->
                showSearch = false
                scope.launch {
                    val index = messages.indexOfFirst { it.id == messageId }
                    if (index >= 0) listState.animateScrollToItem(index)
                }
            }
        )
    }

    if (showPinned) {
        PinnedSheet(
            api = api,
            conversationId = chat.id,
            onDismiss = { showPinned = false },
            onJump = { messageId ->
                showPinned = false
                scope.launch {
                    val index = messages.indexOfFirst { it.id == messageId }
                    if (index >= 0) listState.animateScrollToItem(index)
                }
            }
        )
    }

    if (showInfo) {
        ChatInfoSheet(
            api = api,
            chat = chat,
            onDismiss = { showInfo = false },
            onOpenPinned = {
                showInfo = false
                showPinned = true
            },
            onOpenProfile = { peer ->
                showInfo = false
                onOpenProfile(peer)
            },
            onMessage = { message ->
                scope.launch { snackbar.showSnackbar(message) }
                showInfo = false
            }
        )
    }

    if (reportTarget != null) {
        ReportDialog(
            onDismiss = { reportTarget = null },
            onSubmit = { reason ->
                val target = reportTarget
                reportTarget = null
                if (target != null) {
                    scope.launch {
                        runCatching { api.reportMessage(target.id, reason) }
                            .onSuccess { snackbar.showSnackbar("Thanks — the report was sent for review.") }
                            .onFailure { snackbar.showSnackbar("We couldn't send that report.") }
                    }
                }
            }
        )
    }
}

// ------------------------------------------------------------------ bubbles

@Composable
private fun MessageBubble(
    message: Message,
    mine: Boolean,
    onLongPress: () -> Unit,
    onOpenViewOnce: (Message) -> Unit
) {
    val compact = AppSettings.state.chatDensity == org.holidaylink.app.ui.theme.ChatDensity.COMPACT
    val bubbleColor = if (mine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (mine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (message.replyToBody != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Column {
                            message.replyToSenderName?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
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

                if (message.viewOnce && message.attachment != null) {
                    // Never render the bytes inline: one protected opening only.
                    ViewOnceRow(mine = mine, onOpen = { onOpenViewOnce(message) })
                } else {
                    message.attachment?.let { AttachmentView(it) }
                }

                if (message.body.isNotBlank()) {
                    Text(
                        message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }

                if (message.editedAt != null) {
                    Text(
                        "edited",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                clockOf(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (message.expiresAt != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Timer,
                    "Self-destructs",
                    Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (message.pinned) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.PushPin,
                    "Pinned",
                    Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (mine) {
                Spacer(Modifier.width(4.dp))
                StatusTick(message.status)
            }
        }

        if (message.reactions.isNotEmpty()) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                message.reactions.forEach { reaction ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (reaction.mine) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            "${reaction.emoji} ${reaction.count}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTick(status: String) {
    val tint = when (status) {
        "read" -> MaterialTheme.colorScheme.primary
        "delivered" -> MaterialTheme.colorScheme.onSurfaceVariant
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
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.VisibilityOff,
            null,
            Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
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
 * Closing destroys the message server-side via markMessageViewed.
 */
@Composable
private fun SecureViewOnceDialog(
    attachment: Attachment,
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
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RemoteImage(
                    url = attachment.url.orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .padding(16.dp)
                )
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
private fun AttachmentView(attachment: Attachment) {
    when (attachment.kind) {
        "image", "video" -> {
            val url = attachment.url
            if (url == null) {
                Text("Photo unavailable", style = MaterialTheme.typography.bodySmall)
            } else {
                RemoteImage(
                    url = url,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                if (attachment.kind == "video") {
                    Text(
                        "Video",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        "voice" -> VoiceNoteView(attachment)

        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                attachment.name ?: "File",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VoiceNoteView(attachment: Attachment) {
    val bars = remember(attachment.waveform) {
        attachment.waveform
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: List(24) { 12 }
    }
    Row(
        Modifier
            .widthIn(min = 160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.KeyboardVoice, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bars.take(28).forEach { level ->
                Box(
                    Modifier
                        .width(2.dp)
                        .height((4 + level * 0.22f).coerceIn(4f, 26f).dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatDuration(attachment.durationMs ?: 0L),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
internal fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = java.net.URL(url).openStream().use { it.readBytes() }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image == null) {
        Box(
            modifier
                .height(140.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading…", style = MaterialTheme.typography.labelSmall)
        }
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "Attachment",
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    }
}

@Composable
private fun TypingBubble() {
    Row(
        Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("typing…", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EmptyConversation(title: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name = title, size = 72.dp)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "This is the start of your conversation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DisappearingBanner(duration: DisappearingDuration) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.PushPin,
            null,
            Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "New messages disappear after ${duration.label.lowercase()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

// ------------------------------------------------------------------ composer

@Composable
private fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    editingId: String?,
    replyingTo: Message?,
    onCancelReply: () -> Unit,
    sending: Boolean,
    recording: Boolean,
    recordingMs: Long,
    amplitude: Int,
    onAttach: () -> Unit,
    onEmoji: () -> Unit,
    onSend: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            if (editingId != null || replyingTo != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (editingId != null) "Editing message" else "Replying",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            (if (editingId != null) draft else replyingTo?.body).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                }
            }

            if (recording) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Recording  ${formatDuration(recordingMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (amplitude / 100f).coerceIn(0.02f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = onCancelRecording) {
                        Icon(Icons.Default.Close, "Discard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onStopRecording) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send voice message", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(onClick = onAttach) {
                        Icon(Icons.Default.AttachFile, "Attach")
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                "Message",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 5
                        )
                    }
                    IconButton(onClick = onEmoji) {
                        Icon(
                            Icons.Default.EmojiEmotions,
                            "Emoji",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (draft.isNotBlank()) {
                        IconButton(onClick = onSend, enabled = !sending) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Send",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(onClick = onStartRecording) {
                            Icon(
                                Icons.Default.Mic,
                                "Record voice message",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ sheets

@Composable
private fun MessageActionsSheet(
    message: Message,
    mine: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onReport: () -> Unit,
    onReact: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        SectionHeader("React")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReactionEmojis.forEach { emoji ->
                Text(
                    emoji,
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onReact(emoji) }
                        .padding(8.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingsSection("Message") {
            SheetAction("Reply", Icons.AutoMirrored.Filled.ArrowBack, onReply)
            if (message.body.isNotBlank()) {
                SheetAction("Copy text", Icons.Default.ContentCopy, onCopy)
                SheetAction("Forward", Icons.Default.Forward, onForward)
            }
            if (mine && message.body.isNotBlank() && message.messageType == "text") {
                SheetAction("Edit", Icons.Default.Edit, onEdit)
            }
            SheetAction(
                text = if (message.pinned) "Unpin from chat" else "Pin to chat",
                icon = Icons.Default.PushPin,
                onClick = onPin
            )
            SheetAction("Delete", Icons.Default.Delete, onDelete)
            SheetAction("Report", Icons.Default.Flag, onReport)
        }
    }
}

@Composable
private fun SheetAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
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
) {
    val timers = listOf(0L to "Off", 5L to "5s", 30L to "30s", 60L to "1m", 3600L to "1h", 86400L to "1d")
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
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(emoji) }
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSearchSheet(
    api: Api,
    conversationId: String,
    onDismiss: () -> Unit,
    onJump: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Message>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        try {
            results = api.searchMessages(conversationId, query.trim())
            error = ""
        } catch (e: Exception) {
            error = "Search failed."
        } finally {
            searching = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            label = { Text("Search in this chat") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        when {
            searching -> LinearProgressIndicator(Modifier.fillMaxWidth())
            error.isNotBlank() -> Text(
                error,
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error
            )
            query.isNotBlank() && results.isEmpty() -> Text(
                "No messages match \"$query\".",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            results.isNotEmpty() -> results.forEach { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onJump(message.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            message.body.ifBlank { message.attachment?.name ?: "Attachment" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            friendlyWhen(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Close")
        }
    }
}

@Composable
private fun PinnedSheet(
    api: Api,
    conversationId: String,
    onDismiss: () -> Unit,
    onJump: (String) -> Unit
) {
    var pinned by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        pinned = runCatching { api.pinnedMessages(conversationId) }.getOrDefault(emptyList())
        loading = false
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        SectionHeader("Pinned messages", pinned.size)
        when {
            loading -> LoadingState(label = "Loading pinned messages…")
            pinned.isEmpty() -> Text(
                "Nothing is pinned yet. Long-press any message to pin it here.",
                Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> pinned.forEach { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onJump(message.id) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            message.body.ifBlank { message.attachment?.name ?: "Attachment" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            friendlyWhen(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Close")
        }
    }
}

@Composable
private fun ChatInfoSheet(
    api: Api,
    chat: ChatItem,
    onDismiss: () -> Unit,
    onOpenPinned: () -> Unit,
    onOpenProfile: (User) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val peer = chat.peer
    var muted by remember { mutableStateOf(AppSettings.isChatMuted(chat.id)) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var blocked by remember { mutableStateOf(false) }

    LaunchedEffect(peer?.id) {
        peer?.let { profile = runCatching { api.userProfile(it.id) }.getOrNull() }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        peer?.let { user ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(
                    name = user.displayName,
                    photo = user.avatarUrl,
                    size = 96.dp
                )
                Spacer(Modifier.height(10.dp))
                Text(user.displayName, style = MaterialTheme.typography.titleLarge)
                if (user.username.isNotBlank()) {
                    Text(
                        "@${user.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                profile?.about?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                if (blocked) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Blocked — you can't message each other.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        SettingsSection("Chat") {
            SettingsNavRow("Pinned messages", Icons.Default.PushPin, onClick = onOpenPinned)
            SettingsSwitchRow(
                title = "Mute notifications",
                icon = Icons.Default.NotificationsOff,
                checked = muted,
                onCheckedChange = {
                    muted = it
                    AppSettings.setChatMuted(chat.id, it)
                    onMessage(if (it) "Notifications muted" else "Notifications unmuted")
                }
            )
            if (peer != null) {
                SettingsNavRow(
                    title = "View profile",
                    icon = Icons.Default.Person,
                    onClick = { onOpenProfile(peer) }
                )
                SettingsSwitchRow(
                    title = "Block ${peer.displayName}",
                    icon = Icons.Default.Block,
                    checked = blocked,
                    onCheckedChange = { want ->
                        scope.launch {
                            runCatching { api.setBlocked(peer.id, want) }
                                .onSuccess {
                                    blocked = want
                                    profile = runCatching { api.userProfile(peer.id) }.getOrNull()
                                    onMessage(if (want) "Contact blocked" else "Contact unblocked")
                                }
                                .onFailure { onMessage("We couldn't update the block.") }
                        }
                    }
                )
                SettingsNavRow(
                    title = "Report ${peer.displayName}",
                    icon = Icons.Default.Flag,
                    onClick = {
                        scope.launch {
                            runCatching { api.reportUser(peer.id, "Reported from chat") }
                                .onSuccess { onMessage("Report sent for review.") }
                                .onFailure { onMessage("We couldn't send that report.") }
                        }
                    }
                )
            }
            SettingsNavRow(
                title = "Clear local cache",
                icon = Icons.Default.CleaningServices,
                onClick = {
                    AppSettings.clearLocalCache()
                    onMessage("Local cache cleared. Nothing was deleted from the server.")
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Close")
        }
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val reasons = listOf(
        "Spam or scam",
        "Harassment or bullying",
        "Hate speech",
        "False information",
        "Something else"
    )
    var selected by remember { mutableStateOf(reasons.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report message") },
        text = {
            Column {
                reasons.forEach { reason ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = reason }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selected == reason,
                            onClick = { selected = reason }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(selected) }) { Text("Submit report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ helpers

private fun hasMicPermission(context: Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("HolidayLink message", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Forward message"))
    }
}

fun clockOf(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val instant = runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }
        .getOrElse { runCatching { java.time.Instant.parse(iso) }.getOrNull() }
        ?: return ""
    return java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
        .format(instant)
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
