package org.holidaylink.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.User
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

/**
 * HolidayLink's non-messaging feature center. Admin tools intentionally do not appear here.
 * Public website functionality is opened inside the app through WebFeatureScreen while the
 * native Render/Neon migration is completed feature-by-feature.
 */
data class HolidayFeature(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: List<Color>,
    val route: String
)

private val features = listOf(
    HolidayFeature("Home Feed", "Announcements and the latest HolidayLink updates", Icons.Default.Newspaper, listOf(Color(0xFF06B6D4), Color(0xFF2563EB)), "native:status"),
    HolidayFeature("Find Buddies", "Discover students and connect around interests", Icons.Default.Groups, listOf(Color(0xFF7C3AED), Color(0xFFEC4899)), "native:users"),
    HolidayFeature("Homework", "Organise holiday work and study tasks", Icons.Default.MenuBook, listOf(Color(0xFF2563EB), Color(0xFF06B6D4)), "native:ai"),
    HolidayFeature("Quizzes", "Test yourself and track your progress", Icons.Default.Quiz, listOf(Color(0xFF8B5CF6), Color(0xFF2563EB)), "native:ai"),
    HolidayFeature("Past Papers", "Find revision papers by subject and year", Icons.Default.Description, listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), "native:ai"),
    HolidayFeature("Events", "Discover and join HolidayLink events", Icons.Default.CalendarMonth, listOf(Color(0xFF10B981), Color(0xFF06B6D4)), "native:videos"),
    HolidayFeature("AI Homework Help", "Ask HolidayLink AI for study help", Icons.Default.SmartToy, listOf(Color(0xFF06B6D4), Color(0xFF7C3AED)), "native:ai"),
    HolidayFeature("Video Lessons", "Watch HolidayLink educational videos", Icons.Default.PlayCircle, listOf(Color(0xFFEF4444), Color(0xFFF59E0B)), "native:videos"),
    HolidayFeature("Daily Feed", "Read short updates and entertainment posts", Icons.Default.Newspaper, listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)), "native:status"),
    HolidayFeature("Store", "HolidayLink plans, packs and available items", Icons.Default.ShoppingBag, listOf(Color(0xFF22C55E), Color(0xFF06B6D4)), "native:store"),
    HolidayFeature("Rankings", "See quiz points and student rankings", Icons.Default.Leaderboard, listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), "native:users"),
    HolidayFeature("Invite Friends", "Share HolidayLink with your friends", Icons.Default.PersonAdd, listOf(Color(0xFF2563EB), Color(0xFF7C3AED)), "native:invite"),
    HolidayFeature("Calls", "Open your audio and video call history", Icons.Default.Chat, listOf(Color(0xFF06B6D4), Color(0xFF2563EB)), "native:calls"),
    HolidayFeature("Messages", "Open the full HolidayLink messaging experience", Icons.Default.Chat, listOf(Color(0xFF06B6D4), Color(0xFF2563EB)), "native:chats"),
    HolidayFeature("Wallet", "Open your HolidayLink wallet area", Icons.Default.AccountBalanceWallet, listOf(Color(0xFF10B981), Color(0xFF22C55E)), "native:store")
)

@Composable
fun FeatureHubScreen(
    onBack: () -> Unit,
    onOpen: (HolidayFeature) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title = "HolidayLink", subtitle = "Learn • Connect • Grow", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassHeroCard()
                Spacer(Modifier.height(6.dp))
                Text(
                    "Everything from HolidayLink in one place",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(features, key = { it.title }) { feature ->
                FeatureCard(feature) { onOpen(feature) }
            }
        }
    }
}

@Composable
fun VideoHubScreen(
    api: Api? = null,
    onBack: () -> Unit,
    onOpenLessons: () -> Unit,
    onOpenProfile: (User) -> Unit = {}
) {
    var tab by remember { mutableStateOf("Feed") }
    var videos by remember { mutableStateOf(emptyList<org.holidaylink.app.net.VideoPost>()) }
    var mine by remember { mutableStateOf(emptyList<org.holidaylink.app.net.VideoPost>()) }
    var loading by remember { mutableStateOf(true) }
    var caption by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var info by remember { mutableStateOf("") }
    var composerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    suspend fun reloadAll() {
        if (api == null) return
        loading = videos.isEmpty() && mine.isEmpty()
        runCatching { videos = api.videos() }.onFailure { if (videos.isEmpty()) info = "Couldn't load videos. Showing cache." }
        runCatching { mine = api.myVideos() }
        loading = false
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || api == null) return@rememberLauncherForActivityResult
        val picked = runCatching { MediaPicker.inspect(context, uri) }.getOrNull() ?: return@rememberLauncherForActivityResult
        if (!picked.mimeType.startsWith("video/")) { info = "Choose a video file."; return@rememberLauncherForActivityResult }
        if (picked.sizeBytes > 100L * 1024 * 1024) { info = "Videos must be 100 MB or smaller."; return@rememberLauncherForActivityResult }
        uploading = true; uploadProgress = 0.05f; info = "Uploading…"
        scope.launch {
            // Indeterminate progress: streams, server reports 100MB cap.
            uploadProgress = 0.5f
            runCatching { api.uploadVideo(uri, picked.name, picked.sizeBytes.takeIf { it > 0 } ?: 99L * 1024 * 1024, caption) }
                .onSuccess { uploadProgress = 1f; videos = listOf(it) + videos; mine = listOf(it) + mine; caption = ""; info = "Video posted."; composerOpen = false }
                .onFailure { info = it.message ?: "Video upload failed. Check your internet and try again." }
            uploading = false; uploadProgress = 0f
        }
    }
    LaunchedEffect(api) { reloadAll() }
    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title = "Videos", subtitle = "TikTok-style • Same HolidayLink DB", onBack = onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(selected = tab == "Feed", onClick = { tab = "Feed" }, label = { Text("Feed") })
            androidx.compose.material3.FilterChip(selected = tab == "Manage", onClick = { tab = "Manage"; scope.launch { runCatching { if (api != null) mine = api.myVideos() } } }, label = { Text("Manage (${mine.size})") })
        }
        if (info.isNotBlank()) Text(info, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Button(onClick = { composerOpen = !composerOpen }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Icon(Icons.Default.CloudUpload, null); Text(if (composerOpen) "  Close composer" else "  + Post video")
        }
        if (composerOpen) {
            OutlinedTextField(value = caption, onValueChange = { caption = it.take(1000) }, label = { Text("Caption") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            if (uploading) { androidx.compose.material3.LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)); Spacer(Modifier.height(6.dp)) }
            Button(onClick = { launcher.launch("video/*") }, enabled = api != null && !uploading, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                if (uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (uploading) "Uploading…" else "Pick video (max 100 MB)")
            }
            Spacer(Modifier.height(8.dp))
        }
        when {
            loading -> LoadingState(label = "Loading videos…")
            tab == "Manage" && mine.isEmpty() -> EmptyState(icon = Icons.Default.PlayCircle, title = "No uploads yet", subtitle = "Your posted videos, views, likes and delete live here.", action = { Button(onClick = { composerOpen = true }) { Text("Post your first video") } })
            tab == "Feed" && videos.isEmpty() -> EmptyState(icon = Icons.Default.PlayCircle, title = "No videos yet", subtitle = "Be the first to post.", action = { Button(onClick = { composerOpen = true }) { Text("Post video") } })
            tab == "Manage" -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(mine, key = { it.id }) { video ->
                    ManageVideoRow(video = video, onDelete = {
                        scope.launch { runCatching { api?.deleteVideo(video.id) }.onSuccess { mine = mine.filterNot { v -> v.id == video.id }; videos = videos.filterNot { v -> v.id == video.id }; info = "Deleted." }.onFailure { info = "Delete failed." } }
                    })
                }
            }
            else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(videos, key = { it.id }) { video ->
                    TikTokVideoCard(api = api, video = video, onOpenProfile = { onOpenProfile(video.user) }, onChanged = { updated -> videos = videos.map { if (it.id == updated.id) updated else it } }, onDeleted = { videos = videos.filterNot { it.id == video.id } })
                }
            }
        }
    }
}

@Composable
private fun ManageVideoRow(video: org.holidaylink.app.net.VideoPost, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)) {
        Text(video.caption.ifBlank { "Untitled video" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("${video.views} views • ${video.likes} likes • ${video.comments} comments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.LinearProgressIndicator(progress = { ((video.views % 1000) / 1000f).coerceIn(0.05f, 1f) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = { confirm = true }, modifier = Modifier.weight(1f)) { Text("Delete") }
        }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete video?") }, text = { Text("This removes it for everyone.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text("Cancel") } })
    }
}

@Composable
private fun TikTokVideoCard(api: Api?, video: org.holidaylink.app.net.VideoPost, onOpenProfile: () -> Unit, onChanged: (org.holidaylink.app.net.VideoPost) -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var playing by remember(video.id) { mutableStateOf(true) }
    var liked by remember(video.id) { mutableStateOf(video.liked) }
    var likes by remember(video.id) { mutableStateOf(video.likes) }
    var saved by remember(video.id) { mutableStateOf(video.saved) }
    var commentsOpen by remember(video.id) { mutableStateOf(false) }
    var commentCount by remember(video.id) { mutableStateOf(video.comments) }
    LaunchedEffect(video.id) { scope.launch { runCatching { api?.markVideoViewed(video.id) } } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenProfile() }) {
            org.holidaylink.app.ui.Avatar(name = video.user.displayName, photo = video.user.avatarUrl, size = 40.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("@${video.user.username.ifBlank { "user" }}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${video.views} views • $likes likes • $commentCount comments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (video.caption.isNotBlank()) Text(video.caption, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> android.widget.VideoView(ctx).apply { setOnPreparedListener { mp -> mp.isLooping = true; if (playing) start() } } },
            update = { vv ->
                if (vv.tag != video.videoUrl) { vv.tag = video.videoUrl; vv.setVideoURI(android.net.Uri.parse(video.videoUrl)); vv.seekTo(1) }
                if (playing) { if (!vv.isPlaying) vv.start() } else vv.pause()
            },
            modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(16.dp)).clickable { playing = !playing }
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            androidx.compose.material3.IconButton(onClick = {
                scope.launch { val a = api ?: return@launch; runCatching { a.toggleVideoLike(video.id) }.onSuccess { (l, c) -> liked = l; likes = c; onChanged(video.copy(liked = l, likes = c)) } }
            }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (liked) Color.Red else MaterialTheme.colorScheme.onSurface); Text("$likes", style = MaterialTheme.typography.labelSmall) } }
            androidx.compose.material3.IconButton(onClick = { commentsOpen = !commentsOpen }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Comment, "Comments"); Text("$commentCount", style = MaterialTheme.typography.labelSmall) } }
            androidx.compose.material3.IconButton(onClick = {
                scope.launch { val a = api ?: return@launch; runCatching { a.toggleVideoSave(video.id) }.onSuccess { s -> saved = s; onChanged(video.copy(saved = s)) } }
            }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Favorite", tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface); Text(if (saved) "Saved" else "Save", style = MaterialTheme.typography.labelSmall) } }
        }
        if (commentsOpen) VideoComments(api = api, videoId = video.id, onCount = { commentCount = it; onChanged(video.copy(comments = it)) })
        Text(if (playing) "Tap video to pause • scroll for more" else "Paused — tap to play", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VideoComments(api: Api?, videoId: String, onCount: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var comments by remember(videoId) { mutableStateOf(emptyList<org.holidaylink.app.net.VideoComment>()) }
    var draft by remember(videoId) { mutableStateOf("") }
    var loading by remember(videoId) { mutableStateOf(true) }
    LaunchedEffect(videoId) {
        runCatching { api?.videoComments(videoId) ?: emptyList() }.onSuccess { comments = it; onCount(it.size) }
        loading = false
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        comments.takeLast(5).forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("@${c.user.username}: ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(c.body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { draft = it.take(1000) }, placeholder = { Text("Add a comment…") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.size(8.dp))
            Button(onClick = {
                val t = draft.trim(); if (t.isEmpty() || api == null) return@Button
                scope.launch { runCatching { api.postVideoComment(videoId, t) }.onSuccess { comments = comments + it; onCount(comments.size); draft = "" } }
            }) { Text("Send") }
        }
    }
}

@Composable
private fun FeatureCard(feature: HolidayFeature, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.74f))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(feature.accent)),
            contentAlignment = Alignment.Center
        ) {
            Icon(feature.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(feature.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun StoreScreen(api: Api?, onBack: () -> Unit) {
    var items by remember { mutableStateOf(emptyList<org.holidaylink.app.net.StoreItem>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(api) {
        try { items = api?.storeItems() ?: emptyList(); error = "" }
        catch (e: Exception) { error = "Couldn't load store. Check server + Postgres." }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title = "Store", subtitle = "Plans • Packs • Same Postgres", onBack = onBack)
        when {
            loading -> LoadingState(label = "Loading store…")
            error.isNotBlank() && items.isEmpty() -> ErrorState(error)
            items.isEmpty() -> EmptyState(icon = Icons.Default.ShoppingBag, title = "Store is empty", subtitle = "Admin adds plans later. You said to ignore admin for now.")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Bold)
                            Text(item.description.take(140), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Text("${item.priceMinor} ${item.currency}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassHeroCard(
    title: String = "More Than Just a Chat App",
    subtitle: String = "Learn • Connect • Grow with HolidayLink",
    icon: ImageVector = Icons.Default.AutoAwesome
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(
            Brush.linearGradient(
                listOf(
                    Color(0xFF0B3B70).copy(alpha = 0.90f),
                    Color(0xFF164E63).copy(alpha = 0.86f),
                    Color(0xFF4C1D95).copy(alpha = 0.88f)
                )
            )
        ).padding(20.dp)
    ) {
        Column {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color(0xFF67E8F9), modifier = Modifier.size(27.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.78f))
        }
    }
}

@Composable
private fun GlassTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, "Back")
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
