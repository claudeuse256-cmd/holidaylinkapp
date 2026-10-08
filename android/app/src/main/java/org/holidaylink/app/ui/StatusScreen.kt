package org.holidaylink.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.Me
import org.holidaylink.app.net.StatusUpdate

/**
 * Status updates: text and single-image updates that disappear after 24 hours.
 *
 * Visibility is decided by the server from the "Who can see my status updates" rule in Privacy.
 * The audience is therefore not re-implemented here; this screen only reads what the server sends.
 */
@Composable
fun StatusScreen(
    api: Api,
    me: Me?
) {
    var statuses by remember { mutableStateOf<List<StatusUpdate>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var composerOpen by remember { mutableStateOf(false) }
    // Which person's reel entry the list is currently filtered to; null shows everyone.
    var selectedUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        if (reload > 0) refreshing = true
        try {
            statuses = api.statuses()
            error = ""
        } catch (e: Exception) {
            error = (e as? ApiException)?.message ?: "We couldn't load status updates."
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            runCatching { statuses = api.statuses() }
        }
    }

    val selected = selectedUserId
    val mine = statuses.filter { it.userId == me?.id && (selected == null || selected == it.userId) }
    val others = statuses.filter { it.userId != me?.id && (selected == null || selected == it.userId) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 18.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Status",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
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
                    onPick = { picked ->
                        // Tapping the active entry again clears the filter.
                        selectedUserId = if (selected == picked.userId) null else picked.userId
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
                        subtitle = "Post a short note or photo. Updates disappear after 24 hours."
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
                                items(mine, key = { it.id }) { StatusRow(it) }
                            }

                            if (others.isNotEmpty()) {
                                item(key = "hdr-friends") {
                                    SectionHeader("Friends", others.size)
                                }
                                items(others, key = { it.id }) { StatusRow(it) }
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
}

@Composable
private fun StatusRow(status: StatusUpdate) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Avatar(name = status.userName, photo = status.avatarUrl, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                status.userName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                RemoteImage(
                    url = status.mediaUrl,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(190.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }
            if (status.viewerCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Seen by ${status.viewerCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusComposer(
    api: Api,
    onDismiss: () -> Unit,
    onPosted: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var media by remember { mutableStateOf<String?>(null) }
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
            // Re-encoding a photo must never run on the main thread.
            val dataUrl = withContext(Dispatchers.IO) { MediaPicker.imageDataUrl(context, uri) }
            encoding = false
            if (dataUrl == null) error = "That photo couldn't be read." else media = dataUrl
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
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.Image, "Add a photo")
                    }
                    if (media != null) {
                        Text("Photo attached", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { media = null }) {
                            Icon(Icons.Default.Close, "Remove photo")
                        }
                    } else {
                        Text(
                            "Photo optional",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        runCatching { api.createStatus(text.trim(), media) }
                            .onSuccess {
                                onPosted()
                            }
                            .onFailure { e ->
                                posting = false
                                error = (e as? ApiException)?.message
                                    ?: "We couldn't post that status."
                            }
                    }
                },
                // The server accepts a photo on its own, so a text-less post is allowed too.
                enabled = (text.isNotBlank() || media != null) && !posting && !encoding
            ) { Text(if (posting) "Posting…" else "Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Horizontal strip of people who have a status, shown above the list. */
@Composable
private fun StatusReel(
    statuses: List<StatusUpdate>,
    currentUserId: String?,
    selectedId: String?,
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
                        // A bright ring marks the person currently being viewed.
                        .then(
                            if (selectedId == latest.userId) {
                                Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(name = latest.userName, photo = latest.avatarUrl, size = 56.dp)
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