package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.holidaylink.app.ui.theme.HolidayLinkTheme
import org.holidaylink.app.ui.theme.ThemeMode

/**
 * Design-only preview gallery for Android Studio. This uses sample data and does not
 * initialize Firebase, authentication, permissions, or the production backend.
 * Use it to check spacing, typography, colors, and responsive layout quickly.
 */
@Preview(name = "HolidayLink · Chats · Light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HolidayLinkChatsPreviewLight() {
    HolidayLinkTheme(themeMode = ThemeMode.LIGHT) {
        HolidayLinkPreviewContent()
    }
}

@Preview(name = "HolidayLink · Chats · Dark", showBackground = true, widthDp = 393, heightDp = 852, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HolidayLinkChatsPreviewDark() {
    HolidayLinkTheme(themeMode = ThemeMode.DARK) {
        HolidayLinkPreviewContent()
    }
}

@Composable
private fun HolidayLinkPreviewContent() {
    val people = remember {
        listOf(
            PreviewChat("Amina N.", "Are we still meeting this afternoon?", "09:41", "A", true),
            PreviewChat("Brian Kato", "Sent you a photo", "09:18", "B", false),
            PreviewChat("HolidayLink Community", "Sarah: Welcome everyone!", "Yesterday", "H", false),
            PreviewChat("Mariam", "Voice message · 0:18", "Yesterday", "M", true),
            PreviewChat("Daniel", "Thanks, talk later", "Mon", "D", false),
            PreviewChat("Family Group", "You: See you soon", "Sun", "F", false)
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("HolidayLink", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Stay close to your people", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text("E", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PreviewStatus("Your status", "＋", false)
                    PreviewStatus("Amina", "A", true)
                    PreviewStatus("Brian", "B", true)
                    PreviewStatus("Mariam", "M", true)
                    PreviewStatus("Daniel", "D", false)
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Search chats") }
                )
                Spacer(Modifier.height(12.dp))
                Text("Messages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
                items(people) { chat -> PreviewChatRow(chat) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewNavItem("Chats", true)
                PreviewNavItem("Users", false)
                PreviewNavItem("Channels", false)
                PreviewNavItem("Settings", false)
                PreviewNavItem("Menu", false)
            }
        }
    }
}

@Composable
private fun PreviewStatus(name: String, initial: String, hasUpdate: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(if (hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                Text(initial, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private data class PreviewChat(val name: String, val message: String, val time: String, val initial: String, val unread: Boolean)

@Composable
private fun PreviewChatRow(chat: PreviewChat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(chat.initial, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(chat.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(chat.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (chat.unread) {
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

/** Telegram-style tab: icon on top, small label below, tinted when selected. */
@Composable
private fun PreviewNavItem(label: String, selected: Boolean) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier.clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(previewTabIcon(label)),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

private fun previewTabIcon(label: String) = when (label) {
    "Users" -> org.holidaylink.app.R.drawable.ic_tg_users
    "Channels" -> org.holidaylink.app.R.drawable.ic_tg_channels
    "Settings" -> org.holidaylink.app.R.drawable.ic_tg_settings
    "Menu" -> org.holidaylink.app.R.drawable.ic_tg_menu
    else -> org.holidaylink.app.R.drawable.ic_tg_chats
}

// ---------------------------------------------------------------------------
// Other main pages, same design-only sample data as above.
// ---------------------------------------------------------------------------

@Preview(name = "HolidayLink · Chat thread", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HolidayLinkChatThreadPreviewLight() {
    HolidayLinkTheme(themeMode = ThemeMode.LIGHT) { PreviewChatThreadContent() }
}

@Preview(name = "HolidayLink · Calls", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HolidayLinkCallsPreviewLight() {
    HolidayLinkTheme(themeMode = ThemeMode.LIGHT) { PreviewCallsContent() }
}

@Preview(name = "HolidayLink · Contacts", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HolidayLinkContactsPreviewLight() {
    HolidayLinkTheme(themeMode = ThemeMode.LIGHT) { PreviewContactsContent() }
}

@Preview(name = "HolidayLink · Status", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HolidayLinkStatusPreviewLight() {
    HolidayLinkTheme(themeMode = ThemeMode.LIGHT) { PreviewStatusPageContent() }
}

@Preview(name = "HolidayLink · Settings", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HolidayLinkSettingsPreviewLight() {
    HolidayLinkTheme(themeMode = ThemeMode.LIGHT) { PreviewSettingsContent() }
}

@Preview(name = "HolidayLink · Profile", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HolidayLinkProfilePreviewLight() {
    HolidayLinkTheme(themeMode = ThemeMode.LIGHT) { PreviewProfileContent() }
}

@Preview(name = "HolidayLink · Menu", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
fun HolidayLinkMenuPreviewLight() {
    HolidayLinkTheme(themeMode = ThemeMode.LIGHT) { PreviewMenuContent() }
}

@Composable
private fun PreviewPageScaffold(
    title: String,
    subtitle: String? = null,
    selectedTab: String,
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text("E", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), content = body)
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Chats", "Users", "Channels", "Settings", "Menu").forEach { tab ->
                    PreviewNavItem(tab, tab == selectedTab)
                }
            }
        }
    }
}

@Composable
private fun PreviewBackHeader(title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text("A", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun PreviewChatThreadContent() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            PreviewBackHeader("Amina N.", "online")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PreviewBubble("Hey! Are we still meeting this afternoon?", mine = false, time = "09:38")
                PreviewBubble("Yes, same place as last time.", mine = true, time = "09:39")
                PreviewBubble("I might be 10 minutes late, the bus is slow.", mine = false, time = "09:41")
                PreviewBubble("No problem, I will grab a table.", mine = true, time = "09:41")
                PreviewBubble("Perfect. See you soon!", mine = false, time = "09:42")
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text("Message", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Text(">", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun PreviewBubble(text: String, mine: Boolean, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .width(268.dp)
                .clip(
                    if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                )
                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = if (mine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreviewCallsContent() {
    val calls = remember {
        listOf(
            Triple("Amina N.", "Incoming · 09:41", false),
            Triple("Brian Kato", "Missed · 08:52", true),
            Triple("Mariam", "Outgoing · Yesterday", false),
            Triple("Daniel", "Missed · Yesterday", true),
            Triple("Family Group", "Group call · Mon", false),
            Triple("HolidayLink Community", "Incoming · Sun", false)
        )
    }
    PreviewPageScaffold(title = "Calls", subtitle = "Voice and video history", selectedTab = "Menu") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewFilterPill("All", true)
            PreviewFilterPill("Missed", false)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
            items(calls) { call ->
                PreviewListRow(
                    initial = call.first.take(1),
                    title = call.first,
                    subtitle = call.second,
                    subtitleIsAlert = call.third,
                    meta = if (call.third) "Missed" else "Call"
                )
            }
        }
    }
}

@Composable
private fun PreviewFilterPill(label: String, selected: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewContactsContent() {
    val online = remember { listOf("Amina N.", "Brian Kato", "Daniel", "Mariam", "Sarah K.") }
    val invite = remember { listOf("Joan A.", "Peter M.", "Ruth N.") }
    PreviewPageScaffold(title = "Contacts", subtitle = "People you can reach", selectedTab = "Users") {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Search contacts") }
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
            item { PreviewSectionLabel("ON HOLIDAYLINK · ${online.size}") }
            items(online) { name ->
                PreviewListRow(name.take(1), name, "Available", meta = "Message")
            }
            item { Spacer(Modifier.height(8.dp)); PreviewSectionLabel("INVITE TO HOLIDAYLINK") }
            items(invite) { name ->
                PreviewListRow(name.take(1), name, "Not on HolidayLink yet", meta = "Invite")
            }
        }
    }
}

@Composable
private fun PreviewStatusPageContent() {
    val updates = remember {
        listOf(
            Triple("Amina N.", "2 new photos · 12 min", true),
            Triple("Brian Kato", "Video · 45 min", true),
            Triple("Mariam", "Text status · 2 h", false),
            Triple("Daniel", "Photo · 5 h", false)
        )
    }
    PreviewPageScaffold(title = "Status", subtitle = "Updates disappear after 24 hours", selectedTab = "Chats") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text("+", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("My status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Tap to add an update", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
            item { PreviewSectionLabel("RECENT UPDATES") }
            items(updates) { update ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(54.dp).clip(CircleShape)
                            .background(if (update.third) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                            Text(update.first.take(1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(update.first, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(update.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSettingsContent() {
    PreviewPageScaffold(title = "Settings", subtitle = "Account, privacy and appearance", selectedTab = "Settings") {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text("E", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Emma Nakato", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("+256 700 123 456", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
            }
            item { PreviewSectionLabel("ACCOUNT") }
            items(listOf("Profile", "Accounts", "Privacy", "Security")) { row -> PreviewSettingsRow(row, "") }
            item { Spacer(Modifier.height(6.dp)); PreviewSectionLabel("CHATS") }
            items(listOf("Notifications" to "On", "Wallpaper" to "Default", "Chat backup" to "Last: today")) { row ->
                PreviewSettingsRow(row.first, row.second)
            }
            item { Spacer(Modifier.height(6.dp)); PreviewSectionLabel("APP") }
            items(listOf("Appearance" to "Light", "Text size" to "Default", "Storage" to "1.2 GB")) { row ->
                PreviewSettingsRow(row.first, row.second)
            }
        }
    }
}

@Composable
private fun PreviewSettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewProfileContent() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            PreviewBackHeader("Profile")
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text("E", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(14.dp))
                Text("Emma Nakato", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("+256 700 123 456", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text("Available", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Message", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Call", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth()) {
                    PreviewSectionLabel("ABOUT")
                    Text(
                        "Busy building HolidayLink. Reach me any time after 6pm.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    PreviewSettingsRow("Phone", "+256 700 123 456")
                    PreviewSettingsRow("Username", "@emma")
                    PreviewSettingsRow("Shared media", "24 items")
                }
            }
        }
    }
}

@Composable
private fun PreviewSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

@Composable
private fun PreviewListRow(
    initial: String,
    title: String,
    subtitle: String,
    subtitleIsAlert: Boolean = false,
    meta: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(initial, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (subtitleIsAlert) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// ---------------------------------------------------------------------------
// Menu (feature hub) — mirrors FeatureHubScreen's real structure.
// ---------------------------------------------------------------------------

private data class PreviewFeature(
    val title: String,
    val subtitle: String,
    val glyph: String,
    val accent: List<Color>
)

private val previewMenuSections: List<Pair<String, List<PreviewFeature>>> = listOf(
    "Stay updated" to listOf(
        PreviewFeature("Home Feed", "Announcements and the latest HolidayLink updates", "N", listOf(Color(0xFF06B6D4), Color(0xFF2563EB))),
        PreviewFeature("Events", "Discover and join HolidayLink events", "E", listOf(Color(0xFF10B981), Color(0xFF06B6D4)))
    ),
    "Study" to listOf(
        PreviewFeature("Quizzes", "Test yourself and track your progress", "Q", listOf(Color(0xFF8B5CF6), Color(0xFF2563EB))),
        PreviewFeature("Past Papers", "Find revision papers by subject and year", "P", listOf(Color(0xFFF59E0B), Color(0xFFEF4444))),
        PreviewFeature("AI Homework Help", "Ask HolidayLink AI for study help", "AI", listOf(Color(0xFF06B6D4), Color(0xFF7C3AED))),
        PreviewFeature("Rankings", "See quiz points and student rankings", "R", listOf(Color(0xFFF59E0B), Color(0xFFEF4444)))
    ),
    "Messaging" to listOf(
        PreviewFeature("Messages", "Open the full HolidayLink messaging experience", "M", listOf(Color(0xFF06B6D4), Color(0xFF2563EB))),
        PreviewFeature("Calls", "Open your audio and video call history", "C", listOf(Color(0xFF06B6D4), Color(0xFF2563EB))),
        PreviewFeature("Starred messages", "Keep and revisit messages you starred", "S", listOf(Color(0xFFF59E0B), Color(0xFFFACC15))),
        PreviewFeature("Status", "Updates that vanish after 24 hours", "ST", listOf(Color(0xFF10B981), Color(0xFF06B6D4)))
    ),
    "Store & more" to listOf(
        PreviewFeature("Store", "HolidayLink plans, packs and available items", "B", listOf(Color(0xFF22C55E), Color(0xFF06B6D4))),
        PreviewFeature("Wallet", "Balance, withdrawals and transactions", "W", listOf(Color(0xFF10B981), Color(0xFF22C55E))),
        PreviewFeature("Invite Friends", "Share HolidayLink with your friends", "I", listOf(Color(0xFF2563EB), Color(0xFF7C3AED)))
    )
)

@Composable
private fun PreviewMenuContent() {
    PreviewPageScaffold(
        title = "Menu",
        subtitle = "Everything HolidayLink can do",
        selectedTab = "Menu"
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "hero") { PreviewHeroCard() }
            previewMenuSections.forEach { (section, features) ->
                item(key = "hdr-$section") {
                    Text(
                        section.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 1.dp)
                    )
                }
                items(features, key = { it.title }) { feature -> PreviewFeatureCard(feature) }
            }
        }
    }
}

@Composable
private fun PreviewHeroCard() {
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
            ) {
                Text("HL", color = Color(0xFF67E8F9), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "More Than Just a Chat App",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Learn • Connect • Grow with HolidayLink",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun PreviewFeatureCard(feature: PreviewFeature) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.74f))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(feature.accent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                feature.glyph,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                feature.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}
