package org.holidaylink.app.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import org.holidaylink.app.data.StatusSeen
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.media.MediaEditor
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.DeviceCache
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.FirestoreChat
import org.holidaylink.app.net.MediaCache
import org.holidaylink.app.net.Imgbb
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.Cloudinary
import org.holidaylink.app.net.Me
import org.holidaylink.app.net.StatusUpdate

/** A photo or video picked from the in-app gallery grid, waiting to go through the editor. */
private data class PendingStatusMedia(val file: File, val kind: String)

/**
 * Status updates: text, photo and video updates that disappear from the feed after 24 hours.
 *
 * Visibility is decided by the server from the "Who can see my status updates" rule in Privacy.
 * The audience is therefore not re-implemented here; this screen only reads what the server sends.
 */
@Composable
fun StatusScreen(
    api: Api,
    me: Me?,
    startWithComposer: Boolean = false,
    onBack: () -> Unit = {},
    /** Open a direct chat with the status author when someone replies to a status. */
    onReply: ((StatusUpdate) -> Unit)? = null,
    /** Open a mentioned profile from a status update. */
    onOpenProfile: ((String) -> Unit)? = null
) {
    var statuses by remember { mutableStateOf(DeviceCache.memory<List<StatusUpdate>>("statuses") ?: emptyList()) }
    var loading by remember { mutableStateOf(statuses.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    // "Add status" is a dedicated full-screen page (gallery grid + editor), never the old
    // cramped dialog. Viewing any status — another person's, or one you already posted —
    // always opens the full-screen StatusViewer directly instead.
    var addStatusOpen by remember(startWithComposer) { mutableStateOf(startWithComposer) }
    var textComposerOpen by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<PendingStatusMedia?>(null) }
    // Which person's reel entry the list is currently filtered to; null shows everyone.
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var viewCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var viewersFor by remember { mutableStateOf<StatusUpdate?>(null) }
    var confirmDelete by remember { mutableStateOf<StatusUpdate?>(null) }
    // Full-screen WhatsApp-style viewer.
    var viewerList by remember { mutableStateOf<List<StatusUpdate>>(emptyList()) }
    var viewerStart by remember { mutableIntStateOf(0) }
    var viewerMine by remember { mutableStateOf(false) }
    var viewerOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Watched ids live on the phone, so the reel keeps its "seen" look while offline.
    var seenIds by remember { mutableStateOf(StatusSeen.ids(context)) }
    val scope = rememberCoroutineScope()

    // Only updates from people the user chats with (plus their own) appear in the feed —
    // statuses are a private circle, not a public broadcast.
    var chatUsers by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        val uid = FirebaseBackend.uid.orEmpty()
        runCatching {
            FirestoreChat.chatsFlow(uid).collect { rows ->
                chatUsers = rows.filter { !it.isGroup }.mapNotNull { it.peerId }.toSet() + uid
            }
        }
    }

    suspend fun refreshViews(ids: List<String>) {
        val counts = mutableMapOf<String, Int>()
        ids.forEach { id ->
            counts[id] = runCatching {
                FirebaseBackend.db.collection("statuses").document(id)
                    .collection("status_views").get().await().size()
            }.getOrDefault(0)
        }
        viewCounts = counts
    }

    suspend fun recordViews(list: List<StatusUpdate>) {
        val uid = FirebaseBackend.uid.orEmpty()
        list.filter { it.userId != uid }.forEach { s ->
            runCatching {
                FirebaseBackend.db.collection("statuses").document(s.id)
                    .collection("status_views").document(uid)
                    .set(mapOf("viewedAt" to com.google.firebase.Timestamp.now())).await()
            }
        }
    }

    LaunchedEffect(reload) {
        if (reload > 0) refreshing = true
        val fresh = DeviceCache.load("statuses", { source -> loadFirestoreStatuses(source) }) { data ->
            statuses = data
            error = ""
            loading = false
        }
        if (fresh == null && statuses.isEmpty()) {
            error = "We couldn't load status updates. Check your internet."
        }
        runCatching { refreshViews(statuses.map { it.id }) }
        runCatching { recordViews(statuses) }
        loading = false
        refreshing = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            runCatching {
                val fresh = loadFirestoreStatuses()
                // A tick that was waiting offline succeeds the instant the network comes back,
                // and would otherwise swap in a brand-new (but content-identical) list right at
                // that moment — Compose then sees a changed `statuses` reference and the whole
                // feed visibly "reloads". Only touch state when something actually changed, so
                // reconnecting is silent, the way it is while nothing happened at all.
                if (fresh != statuses) {
                    statuses = fresh
                    DeviceCache.put("statuses", fresh)
                    refreshViews(fresh.map { it.id })
                }
            }
        }
    }

    val selected = selectedUserId
    val visible = statuses.filter { it.userId == me?.id || it.userId in chatUsers }
    val mine = visible.filter { it.userId == me?.id && (selected == null || selected == it.userId) }
    val others = visible.filter { it.userId != me?.id && (selected == null || selected == it.userId) }

    // Opens the full-screen reel for one person, starting on the tapped update.
    val openViewer: (StatusUpdate) -> Unit = { tapped ->
        val person = statuses.filter { it.userId == tapped.userId }.sortedBy { it.createdAt }
        viewerList = person
        viewerStart = person.indexOfFirst { it.id == tapped.id }.coerceAtLeast(0)
        viewerMine = tapped.userId == me?.id
        viewerOpen = true
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // This page is always pushed on top of the chats, so it always gets a way back.
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(
                "Status",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            androidx.compose.material3.Button(onClick = { addStatusOpen = true }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Post")
            }
            Spacer(Modifier.width(4.dp))
            RefreshButton { reload++ }
        }

        when {
            loading -> LoadingState(label = "Loading status…")
            error.isNotBlank() && statuses.isEmpty() -> ErrorState(error) { reload++ }

            else -> Column(Modifier.fillMaxSize()) {
                StatusReel(
                    statuses = statuses,
                    currentUserId = me?.id,
                    selectedId = selected,
                    seenIds = seenIds,
                    onPick = { picked ->
                        // WhatsApp behaviour: tapping a person opens their reel full-screen.
                        openViewer(picked)
                    }
                )

                if (selected != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            statuses.firstOrNull { it.userId == selected }?.userName.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { selectedUserId = null }) { Text("See all") }
                    }
                }

                if (statuses.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Campaign,
                        title = "No status updates",
                        subtitle = "Post a short note or photo. Updates disappear after 24 hours.",
                        action = { androidx.compose.material3.Button(onClick = { addStatusOpen = true }) { Text("+ Post status") } }
                    )
                } else if (selected != null && mine.isEmpty() && others.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Campaign,
                        title = "Nothing new",
                        subtitle = "This person has no active status updates right now."
                    )
                } else {
                    PullToRefreshContainer(
                        refreshing = refreshing,
                        onRefresh = { reload++ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 28.dp)
                        ) {
                            item(key = "my-status") {
                                val hasMine = mine.isNotEmpty()
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .clickable {
                                            // WhatsApp behaviour: tapping your own status (when you
                                            // already have one) opens the full-screen viewer directly;
                                            // only "Add status" ever opens the management page.
                                            if (hasMine) openViewer(mine.first()) else addStatusOpen = true
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(46.dp),
                                        contentAlignment = Alignment.BottomEnd
                                    ) {
                                        if (hasMine) {
                                            Avatar(name = me?.displayName.orEmpty(), photo = mine.first().avatarUrl, size = 46.dp)
                                        } else {
                                            Box(
                                                Modifier
                                                    .size(46.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(HolidayLinkGradient),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = androidx.compose.ui.graphics.Color.White
                                                )
                                            }
                                        }
                                        if (hasMine) {
                                            Box(
                                                Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                    .clickable { addStatusOpen = true },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Add another status",
                                                    tint = androidx.compose.ui.graphics.Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Your status",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            if (hasMine) "Tap to view · ${mine.size} update${if (mine.size == 1) "" else "s"}" else "Share how you're doing",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (mine.isNotEmpty()) {
                                item(key = "hdr-mine") { SectionHeader("My updates", mine.size) }
                                items(mine, key = { it.id }) {
                                    StatusRow(
                                        status = it,
                                        mine = true,
                                        viewCount = viewCounts[it.id] ?: 0,
                                        onOpen = { openViewer(it) },
                                        onDelete = { confirmDelete = it },
                                        onViewers = { viewersFor = it }
                                    )
                                }
                            }

                            if (others.isNotEmpty()) {
                                item(key = "hdr-friends") {
                                    SectionHeader("Friends", others.size)
                                }
                                items(others, key = { it.id }) {
                                    StatusRow(
                                        status = it,
                                        mine = false,
                                        viewCount = 0,
                                        onOpen = { openViewer(it) },
                                        onDelete = {},
                                        onViewers = {},
                                        onReply = { onReply?.invoke(it) },
                                        onOpenProfile = onOpenProfile
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Status updates are governed by the \"Who can see my status updates\" rule in " +
                "Settings, and the server enforces it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }

    // ---------------------------------------------------------------- Add status (full screen)
    //
    // WhatsApp-style flow: a full-screen gallery grid (camera tile + recent photos/videos)
    // is the only way in; picking a thumbnail (or the camera) goes straight to the editor.
    // The old cramped AlertDialog composer is gone entirely.
    if (addStatusOpen) {
        Dialog(onDismissRequest = { addStatusOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            StatusGalleryPicker(
                onBack = { addStatusOpen = false },
                onPickCamera = {
                    // TODO: wire a real camera capture (ActivityResultContracts.TakePicture /
                    // TakeVideo) here, the same way WhatsApp's camera tile opens the device
                    // camera directly. Not part of this pass — falls back to the text composer,
                    // which still exposes voice/music, so the tile is never a dead end.
                    addStatusOpen = false
                    textComposerOpen = true
                },
                onPickMedia = { uri, isVideo ->
                    addStatusOpen = false
                    scope.launch {
                        val local = withContext(Dispatchers.IO) {
                            val ext = if (isVideo) "mp4" else "jpg"
                            val target = File(context.cacheDir, "status_pick_${System.currentTimeMillis()}.$ext")
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    target.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                            target.takeIf { it.exists() && it.length() > 0L }
                        }
                        if (local != null) {
                            pendingEdit = PendingStatusMedia(file = local, kind = if (isVideo) "video" else "image")
                        } else {
                            error = "That photo or video couldn't be opened."
                        }
                    }
                },
                onWriteTextStatus = {
                    addStatusOpen = false
                    textComposerOpen = true
                }
            )
        }
    }

    if (textComposerOpen) {
        Dialog(onDismissRequest = { textComposerOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            StatusTextComposerPage(
                api = api,
                onDismiss = { textComposerOpen = false },
                onPosted = {
                    textComposerOpen = false
                    reload++
                }
            )
        }
    }

    val editing = pendingEdit
    if (editing != null) {
        MediaEditorDialog(
            kind = editing.kind,
            file = editing.file,
            fileName = editing.file.name,
            mimeType = if (editing.kind == "video") "video/mp4" else "image/jpeg",
            statusMode = true,
            onAddMusic = { /* music is attached from the text composer's own tools today */ },
            onCancel = {
                runCatching { editing.file.delete() }
                pendingEdit = null
            },
            onSend = { caption, rotation, trimStart, trimEnd, cropRect, overlays, strokes ->
                pendingEdit = null
                val stickerIds = overlays.filter { it.kind == "sticker" }.mapNotNull { it.stickerRef }
                scope.launch {
                    runCatching {
                        if (editing.kind == "video") {
                            postFirestoreStatus(
                                context = context,
                                text = caption,
                                mediaDataUrl = null,
                                mediaUri = Uri.fromFile(editing.file),
                                mediaKind = "video",
                                fileName = editing.file.name,
                                editedVideoFile = editing.file,
                                trimStartMs = trimStart,
                                trimEndMs = trimEnd,
                                stickerIds = stickerIds
                            )
                        } else {
                            val rendered = withContext(Dispatchers.IO) {
                                MediaEditor.applyPhotoEdits(editing.file, rotation, cropRect, overlays, strokes)
                            } ?: editing.file
                            val bytes = withContext(Dispatchers.IO) { rendered.readBytes() }
                            val dataUrl = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            if (rendered != editing.file) runCatching { rendered.delete() }
                            postFirestoreStatus(
                                context = context,
                                text = caption,
                                mediaDataUrl = dataUrl,
                                mediaUri = null,
                                mediaKind = "image",
                                fileName = editing.file.name,
                                stickerIds = stickerIds
                            )
                        }
                    }.onSuccess {
                        reload++
                    }.onFailure {
                        error = "We couldn't post that status. Check your internet."
                    }
                    runCatching { editing.file.delete() }
                }
            }
        )
    }

    val doomed = confirmDelete
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this update?") },
            text = { Text("It disappears for everyone immediately, before the 24 hours are up.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        runCatching {
                            FirebaseBackend.db.collection("statuses").document(doomed.id).delete().await()
                        }.onSuccess { reload++ }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } }
        )
    }

    viewersFor?.let { StatusViewersSheet(status = it, onDismiss = { viewersFor = null }) }

    if (viewerOpen && viewerList.isNotEmpty()) {
        StatusViewer(
            updates = viewerList,
            startIndex = viewerStart,
            mine = viewerMine,
            viewCounts = viewCounts,
            onSeen = { id ->
                if (id !in seenIds) {
                    StatusSeen.markAll(context, listOf(id))
                    seenIds = seenIds + id
                }
            },
            onDelete = { confirmDelete = it },
            onViewers = { viewersFor = it },
            onDismiss = { viewerOpen = false },
            onReply = { status -> onReply?.invoke(status) },
            onOpenProfile = onOpenProfile
        )
    }
}

@Composable
private fun StatusRow(
	    status: StatusUpdate,
	    mine: Boolean,
	    viewCount: Int,
	    onOpen: () -> Unit,
	    onDelete: () -> Unit,
	    onViewers: () -> Unit,
	    onReply: (() -> Unit)? = null,
	    onOpenProfile: ((String) -> Unit)? = null
	) {
	    Row(
	        Modifier
	            .fillMaxWidth()
	            .clickable { onOpen() }
	            .padding(horizontal = 16.dp, vertical = 8.dp),
	        verticalAlignment = Alignment.Top
	    ) {
	        Avatar(name = status.userName, photo = status.avatarUrl, size = 46.dp)
	        Spacer(Modifier.width(14.dp))
	        Column(Modifier.weight(1f)) {
	            Row(verticalAlignment = Alignment.CenterVertically) {
	                Text(
	                    status.userName,
	                    style = MaterialTheme.typography.bodyLarge,
	                    fontWeight = FontWeight.SemiBold,
	                    maxLines = 1,
	                    overflow = TextOverflow.Ellipsis,
	                    modifier = Modifier.weight(1f)
	                )
	                if (mine) {
	                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
	                        Icon(
	                            Icons.Default.Delete,
	                            "Delete this update",
	                            tint = MaterialTheme.colorScheme.error,
	                            modifier = Modifier.size(18.dp)
	                        )
	                    }
	                }
	            }
	            Text(
	                friendlyWhen(status.createdAt),
	                style = MaterialTheme.typography.labelSmall,
	                color = MaterialTheme.colorScheme.onSurfaceVariant
	            )
	            if (!status.body.isNullOrBlank()) {
	                Spacer(Modifier.height(4.dp))
	                Column {
	                    Text(status.body, style = MaterialTheme.typography.bodyMedium)
	                    if (status.mentions.isNotEmpty()) {
	                        Spacer(Modifier.height(6.dp))
	                        Text("Mentions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
	                        LazyRow(
	                            horizontalArrangement = Arrangement.spacedBy(6.dp)
	                        ) {
	                            items(status.mentions, key = { it }) { mention ->
	                                Box(
	                                    Modifier
	                                        .padding(horizontal = 6.dp, vertical = 2.dp)
	                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
	                                        .clickable(onClick = {
                                            val mentionIndex = status.mentionHandles.indexOf(mention)
                                            val mentionedUid = status.mentionUserIds.getOrNull(mentionIndex)
                                            if (!mentionedUid.isNullOrBlank()) onOpenProfile?.invoke(mentionedUid)
                                        })
	                                ) {
	                                    Text("@$mention", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimaryContainer)
	                                }
	                            }
	                        }
	                    }
	                }
	            }
	            if (!status.mediaUrl.isNullOrBlank()) {
	                Spacer(Modifier.height(8.dp))
	                if (status.kind == "video") {
	                    Row(
	                        Modifier.fillMaxWidth(0.7f).height(88.dp).clip(MaterialTheme.shapes.medium)
	                            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
	                        verticalAlignment = Alignment.CenterVertically
	                    ) {
	                        Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
	                        Spacer(Modifier.width(10.dp)); Text("Video status · tap to play", style = MaterialTheme.typography.bodyMedium)
	                    }
	                } else if (status.kind == "file") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(56.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onOpen() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
	                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
	                        Spacer(Modifier.width(10.dp))
	                        Column {
	                            Text("File status · tap to download", style = MaterialTheme.typography.bodyMedium)
	                            Text(status.body?.takeIf { it.isNotBlank() } ?: "File", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
	                        }
	                    }
	                } else {
	                    RemoteImage(
	                        url = status.mediaUrl,
	                        modifier = Modifier.fillMaxWidth(0.7f).height(190.dp).clip(MaterialTheme.shapes.medium)
	                    )
	                }
	            }
	            if (status.kind == "voice" && status.audioBlob != null) {
	                Spacer(Modifier.height(8.dp))
	                StatusVoice(status.audioBlob, status.durationMs)
	            }
            if (mine) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (viewCount > 0) "Seen by $viewCount — tap to see who" else "Not seen yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = viewCount > 0) { onViewers() }
                )
            } else if (onReply != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onReply) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Message, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("Reply", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
	        }
	    }
	}

	/** Plays a voice status stored as a base64 blob on the document. */
	@Composable
	private fun StatusVoice(blob: String, durationMs: Long) {
	    var playing by remember { mutableStateOf(false) }
	    val player = remember { MediaPlayer() }
	    val context = LocalContext.current
	    val file = remember(blob) {
	        val bytes = android.util.Base64.decode(blob, android.util.Base64.DEFAULT)
	        File(context.cacheDir, "status_${System.currentTimeMillis()}.m4a").apply { writeBytes(bytes) }
	    }
	    DisposableEffect(player) {
	        onDispose { player.release(); runCatching { file.delete() } }
	    }
	    Row(Modifier.fillMaxWidth(0.7f).height(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
	        IconButton(onClick = {
	            if (playing) player.pause() else {
	                player.reset()
	                player.setDataSource(file.absolutePath)
	                player.prepare()
	                player.start()
	            }
	            playing = !playing
	        }) {
	            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
	        }
	        Spacer(Modifier.width(10.dp))
	        Text("Voice · ${formatDuration(durationMs)}", style = MaterialTheme.typography.bodyMedium)
	    }
	}

@Composable
private fun StatusViewersSheet(status: StatusUpdate, onDismiss: () -> Unit) {
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(status.id) {
        names = runCatching {
            val views = FirebaseBackend.db.collection("statuses").document(status.id)
                .collection("status_views").get().await()
            views.documents.mapNotNull { v ->
                val p = runCatching { FirebaseBackend.loadProfile(v.id) }.getOrNull()
                p?.name?.takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seen by ${names.size}") },
        text = {
            when {
                loading -> Text("Loading…")
                names.isEmpty() -> Text("Nobody has opened this update yet.")
                else -> Column {
                    names.take(50).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}


/** Firestore statuses: same docs as the website (24h expiry, imgbb photos). */
private suspend fun loadFirestoreStatuses(
    source: com.google.firebase.firestore.Source = com.google.firebase.firestore.Source.DEFAULT
): List<StatusUpdate> {
    val now = com.google.firebase.Timestamp.now()
    val snap = FirebaseBackend.db.collection("statuses")
        .whereGreaterThan("expiresAt", now)
        .orderBy("expiresAt")
        .limit(200).get(source).await()
    val docs = snap.documents.sortedByDescending { it.safeTimestamp("createdAt")?.seconds ?: 0 }
    val authors = mutableMapOf<String, Pair<String, String?>>()
    return docs.mapNotNull { d ->
        val userId = d.getString("userId").orEmpty()
        if (userId.isBlank()) return@mapNotNull null
        val author = authors.getOrPut(userId) {
            val p = runCatching { FirebaseBackend.loadProfile(userId) }.getOrNull()
            (p?.name?.takeIf { it.isNotBlank() } ?: "HolidayLink user") to p?.profilePic
        }
        StatusUpdate(
            id = d.id,
            userId = userId,
            userName = author.first,
            avatarUrl = author.second,
            kind = d.getString("kind")?.takeIf { it in setOf("text", "image", "video", "voice", "file") }
                ?: if (d.getString("mediaUrl").isNullOrBlank() && d.getString("imageUrl").isNullOrBlank()) "text" else "image",
            body = d.getString("body") ?: d.getString("caption"),
            mediaUrl = d.getString("mediaUrl") ?: d.getString("imageUrl"),
            audioBlob = d.getString("audioBlob"),
            durationMs = d.getLong("durationMs") ?: 0L,
            mentions = (d.get("mentions") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            mentionUserIds = (d.get("mentionUserIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            mentionHandles = (d.get("mentionHandles") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            stickerIds = (d.get("stickerIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            musicUrl = d.getString("musicUrl"),
            createdAt = (d.safeTimestamp("createdAt")?.toDate()?.toInstant()?.toString()).orEmpty(),
            viewerCount = 0,
            seenByMe = false
        )
    }
}

/** Post text/photo/video/file/voice status with 24-hour expiry. */
internal suspend fun postFirestoreStatus(
    context: android.content.Context,
    text: String,
    mediaDataUrl: String?,
    mediaUri: Uri?,
    mediaKind: String,
    fileName: String,
    voiceDurationMs: Long = 0L,
    mentionedUserIds: Map<String, String> = emptyMap(),
    editedVideoFile: File? = null,
    trimStartMs: Long = 0L,
    trimEndMs: Long = 0L,
    stickerIds: List<String> = emptyList(),
    musicUri: Uri? = null
) {
    val uid = FirebaseBackend.uid ?: throw ApiException("Sign in again first.")
    var mediaUrl: String? = null
    var audioBlob: String? = null
    var durationMs: Long = 0
    var finalText = text
    // Voice note: the base64 blob lives on the document, plays offline, no mediaUrl.
    if (mediaKind == "voice" && mediaDataUrl != null) {
        audioBlob = mediaDataUrl
        durationMs = voiceDurationMs
    } else if (mediaKind == "file" && mediaUri != null) {
        val file = MediaPicker.inspect(context, mediaUri)
        if (file.sizeBytes > MediaPicker.maxAttachmentBytes) throw ApiException("That file is too large (max 20 MB).")
        val bytes = context.contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
            ?: throw ApiException("That file couldn't be read.")
        val name = fileName.ifBlank { file.name ?: "File" }
        val mime = file.mimeType.ifBlank { "application/octet-stream" }
        mediaUrl = runCatching { Cloudinary.upload(context, bytes, name, mime) }
            .getOrElse {
                val ref = FirebaseStorage.getInstance().reference
                    .child("statuses/$uid/${System.currentTimeMillis()}")
                ref.putBytes(bytes).await()
                ref.downloadUrl.await().toString()
            }
        if (finalText.isBlank()) finalText = name
    } else if (mediaKind == "video" && mediaUri != null) {
        // When the caller already has a local file (editedVideoFile — e.g. the gallery-grid
        // "Add status" flow, which copies the picked video to cacheDir before editing), reading
        // size/name through the content resolver is unreliable for a file:// URI and is also
        // redundant: inspect the file directly instead of round-tripping through MediaPicker.
        val pickedName: String?
        val pickedSizeBytes: Long
        if (editedVideoFile != null) {
            pickedName = editedVideoFile.name
            pickedSizeBytes = editedVideoFile.length()
        } else {
            val picked = MediaPicker.inspect(context, mediaUri)
            pickedName = picked.name
            pickedSizeBytes = picked.sizeBytes
        }
        if (pickedSizeBytes > 30L * 1024 * 1024) throw ApiException("Status videos must be 30 MB or smaller.")
        val source = editedVideoFile ?: withContext(Dispatchers.IO) {
            File(context.cacheDir, "status_source_${System.currentTimeMillis()}.mp4").also { target ->
                context.contentResolver.openInputStream(mediaUri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            }
        }
        val uploadFile = if (trimEndMs > trimStartMs && (trimStartMs > 0L || trimEndMs < MediaEditor.videoDurationMs(source))) {
            val trimmed = File(context.cacheDir, "status_trim_${System.currentTimeMillis()}.mp4")
            MediaEditor.trimVideo(source, trimStartMs, trimEndMs, trimmed)
                ?: throw ApiException("This video format couldn't be trimmed. Try another video.")
        } else source
        if (uploadFile.length() > 30L * 1024 * 1024) throw ApiException("Edited status videos must be 30 MB or smaller.")
        val bytes = withContext(Dispatchers.IO) { uploadFile.readBytes() }
        val name = pickedName ?: "status.mp4"
        val mime = "video/mp4"
        mediaUrl = runCatching { Cloudinary.upload(context, bytes, name, mime) }
            .getOrElse {
                val ref = FirebaseStorage.getInstance().reference
                    .child("statuses/$uid/${System.currentTimeMillis()}.mp4")
                ref.putFile(Uri.fromFile(uploadFile)).await()
                ref.downloadUrl.await().toString()
            }
        if (uploadFile != source) runCatching { uploadFile.delete() }
        if (editedVideoFile == null) runCatching { source.delete() }
    }
    if (!mediaDataUrl.isNullOrBlank() && mediaDataUrl.startsWith("data:") && mediaKind != "voice") {
        val base64 = mediaDataUrl.substringAfter(",", "")
        if (base64.isBlank()) throw ApiException("That photo couldn't be read.")
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        mediaUrl = Imgbb.uploadJpeg(bytes)
    }
    var musicUrl: String? = null
    if (musicUri != null) {
        val musicInfo = MediaPicker.inspect(context, musicUri)
        if (musicInfo.sizeBytes > 20L * 1024 * 1024) throw ApiException("Status music must be 20 MB or smaller.")
        val ref = FirebaseStorage.getInstance().reference.child("statuses/$uid/music_${System.currentTimeMillis()}")
        ref.putFile(musicUri).await()
        musicUrl = ref.downloadUrl.await().toString()
    }
    val now = com.google.firebase.Timestamp.now()
    // @usernames mentioned in the body are stored so viewers can tap them.
    val mentions = org.holidaylink.app.net.StatusMentionParser.handles(finalText)
    val statusRef = FirebaseBackend.db.collection("statuses").document()
    statusRef.set(
        mapOf(
            "userId" to uid,
            "kind" to when {
                mediaKind == "voice" -> "voice"
                mediaKind == "file" -> "file"
                mediaUrl == null -> "text"
                mediaKind == "video" -> "video"
                else -> "image"
            },
            "body" to finalText,
            "mediaUrl" to mediaUrl,
            "audioBlob" to audioBlob,
            "durationMs" to durationMs,
            "mentions" to mentions,
            "mentionUserIds" to mentionedUserIds.keys.toList(),
            "mentionHandles" to mentionedUserIds.values.toList(),
            "stickerIds" to stickerIds,
            "musicUrl" to musicUrl,
            "createdAt" to now,
            "expiresAt" to com.google.firebase.Timestamp(now.seconds + 24 * 3600, 0)
        )
    ).await()

    // Deliver each mention into the recipient's direct chat and notification inbox.
    val senderName = FirebaseBackend.loadProfile(uid)?.name?.ifBlank { "Someone" } ?: "Someone"
    mentionedUserIds.forEach { (recipientId, handle) ->
        if (recipientId == uid) return@forEach
        runCatching {
            val chatId = FirestoreChat.startDirect(recipientId)
            val messageId = FirestoreChat.send(
                chatId = chatId,
                text = "$senderName mentioned you in their status: ${finalText.take(180)}",
                kind = "text",
                replyToId = statusRef.id,
                replyToBody = finalText.take(120).ifBlank { "Photo or video status" },
                replyToName = "Status mention"
            )
            FirebaseBackend.db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("statusId", statusRef.id, "statusMention", true, "mentionedUsername", handle).await()
            FirebaseBackend.db.collection("notifications").add(
                mapOf(
                    "userId" to recipientId,
                    "type" to "status_mention",
                    "title" to "You were mentioned",
                    "message" to "$senderName mentioned you in a status",
                    "read" to false,
                    "timestamp" to now,
                    "conversationId" to chatId,
                    "senderId" to uid,
                    "data" to mapOf("statusId" to statusRef.id, "conversationId" to chatId, "senderId" to uid)
                )
            ).await()
        }
    }
}

/** Horizontal strip of people who have a status, shown above the list. */
@Composable
private fun StatusReel(
    statuses: List<StatusUpdate>,
    currentUserId: String?,
    selectedId: String?,
    seenIds: Set<String>,
    onPick: (StatusUpdate) -> Unit
) {
    val grouped = remember(statuses, currentUserId) {
        statuses
            .sortedWith(
                compareByDescending<StatusUpdate> { it.userId == currentUserId }
                    .thenByDescending { it.createdAt }
            )
            .distinctBy { it.userId }
    }
    // A person counts as "unseen" while any of their updates has not been watched yet.
    val unseenUsers = remember(statuses, seenIds, currentUserId) {
        statuses.filter { it.id !in seenIds && it.userId != currentUserId }
            .mapTo(HashSet()) { it.userId }
    }
    if (grouped.isEmpty()) return

    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(grouped, key = { it.userId }) { latest ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(66.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPick(latest) }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        // Bright ring = something not watched yet, faded ring = already seen,
                        // thicker ring = the person currently being viewed.
                        .then(
                            when {
                                selectedId == latest.userId -> Modifier.border(
                                    2.5.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                latest.userId in unseenUsers -> Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                else -> Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(name = latest.userName, photo = latest.avatarUrl, size = 54.dp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    latest.userName.substringBefore(' '),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ------------------------------------------------------------------ full-screen reel

/**
 * WhatsApp-style status viewer: full-screen portrait, segmented progress bars, auto-advance
 * (tap right / tap left to step, hold to pause), header with avatar + time, delete for your
 * own updates and a "Seen by" footer.
 */
@Composable
private fun StatusViewer(
    updates: List<StatusUpdate>,
    startIndex: Int,
    mine: Boolean,
    viewCounts: Map<String, Int>,
    onSeen: (String) -> Unit,
    onDelete: (StatusUpdate) -> Unit,
    onViewers: (StatusUpdate) -> Unit,
    onDismiss: () -> Unit,
    onReply: ((StatusUpdate) -> Unit)? = null,
    onOpenProfile: ((String) -> Unit)? = null
) {
    var index by remember(updates) { mutableIntStateOf(startIndex.coerceIn(0, updates.lastIndex)) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var videoPlayer by remember { mutableStateOf<VideoView?>(null) }
    val current = updates[index.coerceIn(0, updates.lastIndex)]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Status videos are kept on the phone once watched, so replaying one offline still works.
    var videoFile by remember(current.id) {
        mutableStateOf(
            current.mediaUrl
                ?.takeIf { current.kind == "video" && it.isNotBlank() }
                ?.let { MediaCache.cached(context, it, "mp4") }
        )
    }
    var videoFailed by remember(current.id) { mutableStateOf(false) }

    LaunchedEffect(current.id) {
        onSeen(current.id)
    }

    LaunchedEffect(current.id) {
        val url = current.mediaUrl
        if (current.kind != "video" || url.isNullOrBlank() || videoFile != null) return@LaunchedEffect
        // Playback streams from the URL; this copy is what makes it available offline later.
        MediaCache.fetch(context, url, "mp4")?.let { videoFile = it }
    }

    fun stepForward() {
        if (index < updates.lastIndex) {
            index++
            progress = 0f
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(index, paused, current.kind, videoFailed) {
        if (paused) {
            videoPlayer?.pause()
            return@LaunchedEffect
        }
        if (current.kind == "video") {
            // Video status progress follows actual playback; do not skip a video after the
            // five-second image timer. VideoView's completion callback advances the reel.
            if (videoFailed) return@LaunchedEffect
            while (true) {
                kotlinx.coroutines.delay(80)
                val player = videoPlayer ?: continue
                val duration = player.duration
                if (duration > 0) progress = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            }
        } else {
            while (progress < 1f) {
                kotlinx.coroutines.delay(80)
                progress = (progress + 0.016f).coerceAtMost(1f)
            }
            stepForward()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (!current.mediaUrl.isNullOrBlank() && current.kind == "video") {
                key(current.id) {
                    AndroidView(
                        factory = { ctx -> VideoView(ctx).apply {
                            videoPlayer = this
                            val local = videoFile
                            if (local != null) setVideoPath(local.absolutePath)
                            else setVideoURI(Uri.parse(current.mediaUrl))
                            setOnPreparedListener { if (paused) pause() else start() }
                            setOnCompletionListener { stepForward() }
                            // Streaming can fail with no connection; keep the reel usable.
                            setOnErrorListener { _, _, _ -> videoFailed = true; true }
                        } },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (videoFailed) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "This video isn't available offline yet.",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                }
            } else if (!current.mediaUrl.isNullOrBlank() && current.kind == "file") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, null, Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.height(10.dp))
                        Text("File status · tap to download", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        IconButton(onClick = {
                            current.mediaUrl?.let { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        }) {
                            Icon(Icons.Default.FileDownload, null, Modifier.size(32.dp), tint = Color.White)
                        }
                    }
                }
            } else if (current.kind == "voice" && current.audioBlob != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    StatusVoice(current.audioBlob, current.durationMs)
                }
            } else if (!current.mediaUrl.isNullOrBlank()) {
                RemoteImage(url = current.mediaUrl, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(statusGradientColor(index), statusGradientColor(index + 2))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            current.body.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(36.dp).widthIn(max = 260.dp)
                        )
                        if (current.mentions.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(current.mentions, key = { it }) { mention ->
                                    Box(
                                        Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                            .clickable(onClick = {
                                            val mentionIndex = current.mentionHandles.indexOf(mention)
                                            val mentionedUid = current.mentionUserIds.getOrNull(mentionIndex)
                                            if (!mentionedUid.isNullOrBlank()) onOpenProfile?.invoke(mentionedUid)
                                        })
                                    ) {
                                        Text("@$mention", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (current.stickerIds.isNotEmpty()) {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    current.stickerIds.take(4).forEach { stickerId ->
                        run { val ref = statusStickerRef(stickerId); AnimatedSticker(url = ref, assetPath = stickerAssetPath(ref), size = 104.dp) }
                    }
                }
            }

            // Tap right two-thirds = next, left third = back; press and hold = pause.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(index) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                tryAwaitRelease()
                                paused = false
                            },
                            onTap = { offset ->
                                if (offset.x < size.width * 0.3f) {
                                    if (index > 0) index--
                                    progress = 0f
                                } else {
                                    stepForward()
                                }
                            }
                        )
                    }
            )

            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    updates.forEachIndexed { i, _ ->
                        val frac = when {
                            i < index -> 1f
                            i == index -> progress
                            else -> 0f
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.35f))
                        ) {
                            if (frac > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(frac)
                                        .fillMaxHeight()
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = Color.White
                        )
                    }
                    Avatar(name = current.userName, photo = current.avatarUrl, size = 36.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.userName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            friendlyWhen(current.createdAt),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (mine) {
                        IconButton(onClick = { onDelete(current) }) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (!current.mediaUrl.isNullOrBlank() && !current.body.isNullOrBlank()) {
                    Text(
                        current.body,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }

                current.musicUrl?.let { url ->
                    StatusMusicControl(url = url)
                }

                if (mine) {
                    val count = viewCounts[current.id] ?: 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = count > 0) { onViewers(current) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (count > 0) "Seen by $count · tap to see who" else "Not seen yet",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else if (onReply != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("❤️", "😂", "😮", "😢", "🙏", "🔥").forEach { reaction ->
                            TextButton(onClick = {
                                val uid = FirebaseBackend.uid.orEmpty()
                                if (uid.isNotBlank()) {
                                    scope.launch {
                                        runCatching {
                                            FirebaseBackend.db.collection("statuses").document(current.id)
                                                .collection("status_reactions").document(uid)
                                                .set(mapOf("userId" to uid, "emoji" to reaction, "createdAt" to com.google.firebase.Timestamp.now())).await()
                                            val chatId = FirestoreChat.startDirect(current.userId)
                                            FirestoreChat.send(
                                                chatId = chatId,
                                                text = "Reacted $reaction to your status",
                                                kind = "text",
                                                replyToId = current.id,
                                                replyToBody = current.body?.take(100) ?: "Photo or video status",
                                                replyToName = "Status reaction"
                                            )
                                            FirebaseBackend.db.collection("notifications").add(
                                                mapOf("userId" to current.userId, "type" to "status_reaction", "title" to "Status reaction", "message" to "Someone reacted $reaction to your status", "read" to false, "timestamp" to com.google.firebase.Timestamp.now(), "conversationId" to chatId, "senderId" to uid, "data" to mapOf("statusId" to current.id, "conversationId" to chatId, "senderId" to uid))
                                            ).await()
                                        }
                                    }
                                }
                            }) { Text(reaction, fontSize = 21.sp) }
                        }
                    }
                    TextButton(onClick = { onReply(current) }, modifier = Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Message, null, Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Reply", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                        }
                    }
                } else {
                    Spacer(Modifier.height(22.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusMusicControl(url: String) {
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var ready by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    DisposableEffect(url) {
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setOnPreparedListener { ready = true }
        mediaPlayer.setOnCompletionListener { playing = false; runCatching { it.seekTo(0) } }
        runCatching { mediaPlayer.setDataSource(url); mediaPlayer.prepareAsync() }
        onDispose { runCatching { mediaPlayer.release() }; player = null }
    }
    TextButton(enabled = ready, onClick = {
        val p = player ?: return@TextButton
        runCatching { if (playing) p.pause() else p.start() }
        playing = !playing
    }) {
        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(if (playing) "Pause music" else "Play music", color = Color.White)
    }
}

private fun formatMs(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

private fun statusGradientColor(i: Int): Color {
    val palette = listOf(
        Color(0xFF075E54), Color(0xFF128C7E), Color(0xFF1E88E5),
        Color(0xFF6A1B9A), Color(0xFF00897B), Color(0xFF3949AB)
    )
    return palette[i.mod(palette.size)]
}

private fun statusStickerRef(idOrRef: String): String {
    return when {
        idOrRef.startsWith("hlsticker://") -> idOrRef
        idOrRef.startsWith("pack:") -> {
            val rest = idOrRef.removePrefix("pack:")
            val parts = rest.split(":", limit = 2)
            if (parts.size == 2) "hlsticker://pack/${parts[0]}/${parts[1]}" else "hlsticker://$idOrRef"
        }
        idOrRef.contains('/') -> "hlsticker://$idOrRef"
        else -> "hlsticker://$idOrRef"
    }
}
