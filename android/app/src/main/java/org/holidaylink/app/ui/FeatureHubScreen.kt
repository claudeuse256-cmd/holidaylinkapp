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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.holidaylink.app.net.Api
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
    HolidayFeature("Events", "Discover and join HolidayLink events", Icons.Default.CalendarMonth, listOf(Color(0xFF10B981), Color(0xFF06B6D4)), "native:status"),
    HolidayFeature("AI Homework Help", "Ask HolidayLink AI for study help", Icons.Default.SmartToy, listOf(Color(0xFF06B6D4), Color(0xFF7C3AED)), "native:ai"),
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
