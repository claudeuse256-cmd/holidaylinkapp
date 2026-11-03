package org.holidaylink.app.ui

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import org.holidaylink.app.data.StatusSeen
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.DeviceCache
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.MediaCache
import org.holidaylink.app.net.Imgbb
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.Cloudinary
import org.holidaylink.app.net.Me
import org.holidaylink.app.net.StatusUpdate

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
    onBack: () -> Unit = {}
) {
    var statuses by remember { mutableStateOf(DeviceCache.memory<List<StatusUpdate>>("statuses") ?: emptyList()) }
    var loading by remember { mutableStateOf(statuses.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var composerOpen by remember(startWithComposer) { mutableStateOf(startWithComposer) }
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
                statuses = fresh
                DeviceCache.put("statuses", fresh)
                refreshViews(fresh.map { it.id })
            }
        }
    }

    val selected = selectedUserId
    val mine = statuses.filter { it.userId == me?.id && (selected == null || selected == it.userId) }
    val others = statuses.filter { it.userId != me?.id && (selected == null || selected == it.userId) }

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
            androidx.compose.material3.Button(onClick = { composerOpen = true }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
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
                        action = { androidx.compose.material3.Button(onClick = { composerOpen = true }) { Text("+ Post status") } }
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
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .clickable { composerOpen = true }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Your status",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Share how you're doing",
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
                                        onViewers = {}
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

    if (composerOpen) {
        StatusComposer(
            api = api,
            onDismiss = { composerOpen = false },
            onPosted = {
                composerOpen = false
                reload++
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
            onDismiss = { viewerOpen = false }
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
    onViewers: () -> Unit
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
                Text(status.body, style = MaterialTheme.typography.bodyMedium)
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
                } else {
                    RemoteImage(
                        url = status.mediaUrl,
                        modifier = Modifier.fillMaxWidth(0.7f).height(190.dp).clip(MaterialTheme.shapes.medium)
                    )
                }
            }
            if (mine) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (viewCount > 0) "Seen by $viewCount — tap to see who" else "Not seen yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = viewCount > 0) { onViewers() }
                )
            }
        }
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

@Composable
private fun StatusComposer(
    api: Api,
    onDismiss: () -> Unit,
    onPosted: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var media by remember { mutableStateOf<String?>(null) }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaKind by remember { mutableStateOf("text") }
    var posting by remember { mutableStateOf(false) }
    var encoding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        encoding = true
        scope.launch {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("video/")) {
                val file = MediaPicker.inspect(context, uri)
                if (file.sizeBytes > 30L * 1024 * 1024) {
                    error = "Status videos must be 30 MB or smaller."
                } else {
                    media = null
                    mediaUri = uri
                    mediaKind = "video"
                    error = ""
                }
            } else {
                // Re-encoding a photo must never run on the main thread.
                val dataUrl = withContext(Dispatchers.IO) { MediaPicker.imageDataUrl(context, uri) }
                if (dataUrl == null) error = "That photo couldn't be read."
                else { media = dataUrl; mediaUri = null; mediaKind = "image"; error = "" }
            }
            encoding = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your status") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(280) },
                    label = { Text("What's happening?") },
                    minLines = 2,
                    supportingText = { Text("${text.length}/280") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                if (encoding) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) { Icon(Icons.Default.Image, "Add a photo") }
                    IconButton(
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    ) { Icon(Icons.Default.VideoLibrary, "Add a video") }
                    if (media != null || mediaUri != null) {
                        Text(if (mediaKind == "video") "Video attached" else "Photo attached", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { media = null; mediaUri = null; mediaKind = "text" }) {
                            Icon(Icons.Default.Close, "Remove media")
                        }
                    } else {
                        Text("Photo or video optional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (posting) return@TextButton
                    posting = true
                    error = ""
                    scope.launch {
                        runCatching { postFirestoreStatus(context, text.trim(), media, mediaUri, mediaKind) }
                            .onSuccess {
                                onPosted()
                            }
                            .onFailure {
                                posting = false
                                error = "We couldn't post that status. Check your internet."
                            }
                    }
                },
                // A photo on its own is allowed too.
                enabled = (text.isNotBlank() || media != null || mediaUri != null) && !posting && !encoding
            ) { Text(if (posting) "Posting…" else "Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
            kind = d.getString("kind")?.takeIf { it in setOf("text", "image", "video") }
                ?: if (d.getString("mediaUrl").isNullOrBlank() && d.getString("imageUrl").isNullOrBlank()) "text" else "image",
            body = d.getString("body") ?: d.getString("caption"),
            mediaUrl = d.getString("mediaUrl") ?: d.getString("imageUrl"),
            createdAt = (d.safeTimestamp("createdAt")?.toDate()?.toInstant()?.toString()).orEmpty(),
            viewerCount = 0,
            seenByMe = false
        )
    }
}

/** Post text/photo/video status with 24-hour expiry. Photos use imgbb; videos use Cloudinary. */
private suspend fun postFirestoreStatus(context: android.content.Context, text: String, mediaDataUrl: String?, mediaUri: Uri?, mediaKind: String) {
    val uid = FirebaseBackend.uid ?: throw ApiException("Sign in again first.")
    var mediaUrl: String? = null
    if (mediaKind == "video" && mediaUri != null) {
        val file = MediaPicker.inspect(context, mediaUri)
        if (file.sizeBytes > 30L * 1024 * 1024) throw ApiException("Status videos must be 30 MB or smaller.")
        val bytes = context.contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
            ?: throw ApiException("That video couldn't be read.")
        val name = file.name ?: "status.mp4"
        val mime = file.mimeType.ifBlank { "video/mp4" }
        // Cloudinary through our server; Firebase Storage stays as a fallback until the
        // Cloudinary keys are configured so video statuses never regress.
        mediaUrl = runCatching { Cloudinary.upload(context, bytes, name, mime) }
            .getOrElse {
                val extension = if (file.mimeType == "video/3gpp") "3gp" else "mp4"
                val ref = FirebaseStorage.getInstance().reference
                    .child("statuses/$uid/${System.currentTimeMillis()}.$extension")
                ref.putFile(mediaUri).await()
                ref.downloadUrl.await().toString()
            }
    }
    if (!mediaDataUrl.isNullOrBlank() && mediaDataUrl.startsWith("data:")) {
        val base64 = mediaDataUrl.substringAfter(",", "")
        if (base64.isBlank()) throw ApiException("That photo couldn't be read.")
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        mediaUrl = Imgbb.uploadJpeg(bytes)
    }
    val now = com.google.firebase.Timestamp.now()
    FirebaseBackend.db.collection("statuses").add(
        mapOf(
            "userId" to uid,
            "kind" to when { mediaUrl == null -> "text"; mediaKind == "video" -> "video"; else -> "image" },
            "body" to text,
            "mediaUrl" to mediaUrl,
            "createdAt" to now,
            "expiresAt" to com.google.firebase.Timestamp(now.seconds + 24 * 3600, 0)
        )
    ).await()
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
    onDismiss: () -> Unit
) {
    var index by remember(updates) { mutableIntStateOf(startIndex.coerceIn(0, updates.lastIndex)) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var videoPlayer by remember { mutableStateOf<VideoView?>(null) }
    val current = updates[index.coerceIn(0, updates.lastIndex)]
    val context = LocalContext.current
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
                    Text(
                        current.body.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(36.dp)
                    )
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
                } else {
                    Spacer(Modifier.height(22.dp))
                }
            }
        }
    }
}

private fun statusGradientColor(i: Int): Color {
    val palette = listOf(
        Color(0xFF075E54), Color(0xFF128C7E), Color(0xFF1E88E5),
        Color(0xFF6A1B9A), Color(0xFF00897B), Color(0xFF3949AB)
    )
    return palette[i.mod(palette.size)]
}