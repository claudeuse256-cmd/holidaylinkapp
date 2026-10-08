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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
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
    var videos by remember { mutableStateOf(emptyList<org.holidaylink.app.net.VideoPost>()) }
    var caption by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }
    var composerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || api == null) return@rememberLauncherForActivityResult
        val picked = runCatching { MediaPicker.inspect(context, uri) }.getOrNull() ?: return@rememberLauncherForActivityResult
        if (!picked.mimeType.startsWith("video/")) { info = "Choose a video file."; return@rememberLauncherForActivityResult }
        // Some pickers return 0 size; fall back to upload attempt with server-side 100MB guard.
        if (picked.sizeBytes > 100L * 1024 * 1024) { info = "Videos must be 100 MB or smaller."; return@rememberLauncherForActivityResult }
        uploading = true; info = "Uploading…"
        scope.launch {
            runCatching { api.uploadVideo(uri, picked.name, picked.sizeBytes.takeIf { it > 0 } ?: 99L * 1024 * 1024, caption) }
                .onSuccess { videos = listOf(it) + videos; caption = ""; info = "Video posted."; composerOpen = false }
                .onFailure { info = it.message ?: "Video upload failed. Check server address + DATABASE_URL on Render." }
            uploading = false
        }
    }
    LaunchedEffect(api) {
        if (api != null) runCatching { api.videos() }.onSuccess { videos = it }.onFailure { info = "Couldn't load videos. Check server address." }
    }
    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title = "Videos", subtitle = "TikTok-style • Same HolidayLink DB", onBack = onBack)
        if (info.isNotBlank()) Text(info, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        Button(onClick = { composerOpen = !composerOpen }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Icon(Icons.Default.CloudUpload, null); Text(if (composerOpen) "  Close composer" else "  Post video")
        }
        if (composerOpen) {
            OutlinedTextField(value = caption, onValueChange = { caption = it.take(1000) }, label = { Text("Caption") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            Button(onClick = { launcher.launch("video/*") }, enabled = api != null && !uploading, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                if (uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (uploading) "Uploading…" else "Pick video (max 100 MB)")
            }
            Spacer(Modifier.height(8.dp))
        }
        if (videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No creator videos yet. Be the first to post.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(videos, key = { it.id }) { video ->
                    TikTokVideoCard(video = video, onOpenProfile = { onOpenProfile(video.user) }, onViewed = {
                        scope.launch { runCatching { api?.markVideoViewed(video.id) } }
                    })
                }
            }
        }
    }
}

@Composable
private fun TikTokVideoCard(video: org.holidaylink.app.net.VideoPost, onOpenProfile: () -> Unit, onViewed: () -> Unit) {
    var playing by remember(video.id) { mutableStateOf(true) }
    LaunchedEffect(video.id) { onViewed() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenProfile() }) {
            org.holidaylink.app.ui.Avatar(name = video.user.displayName, photo = video.user.avatarUrl, size = 40.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("@${video.user.username.ifBlank { "user" }}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${video.views} views • ${video.likes} likes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (video.caption.isNotBlank()) Text(video.caption, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        // Real playback, not a fake card: streams /uploads mp4 from Render.
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    setOnPreparedListener { mp -> mp.isLooping = true; if (playing) start() }
                }
            },
            update = { vv ->
                if (vv.tag != video.videoUrl) {
                    vv.tag = video.videoUrl
                    vv.setVideoURI(android.net.Uri.parse(video.videoUrl))
                    vv.seekTo(1)
                }
                if (playing) { if (!vv.isPlaying) vv.start() } else vv.pause()
            },
            modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(16.dp)).clickable { playing = !playing }
        )
        Spacer(Modifier.height(6.dp))
        Text(if (playing) "Tap video to pause" else "Paused — tap to play", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
