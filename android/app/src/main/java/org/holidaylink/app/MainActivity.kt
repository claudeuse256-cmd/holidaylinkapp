@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.holidaylink.app.config.AppConfig
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.data.AccountStore
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.CallManager
import org.holidaylink.app.ui.CallScreen
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.Me
import org.holidaylink.app.net.User
import org.holidaylink.app.phone.Countries
import org.holidaylink.app.phone.CountryCode
import org.holidaylink.app.phone.PhoneNumbers
import org.holidaylink.app.ui.AiChatScreen
import org.holidaylink.app.ui.AppLockGate
import org.holidaylink.app.ui.CallsScreen
import org.holidaylink.app.ui.ChatScreen
import org.holidaylink.app.ui.ChatsScreen
import org.holidaylink.app.ui.ContactsScreen
import org.holidaylink.app.ui.MyProfileScreen
import org.holidaylink.app.ui.NewChatScreen
import org.holidaylink.app.ui.NotificationListScreen
import org.holidaylink.app.ui.NotificationPermissionBanner
import org.holidaylink.app.ui.SearchScreen
import org.holidaylink.app.ui.SettingsHub
import org.holidaylink.app.ui.SettingsHost
import org.holidaylink.app.ui.SettingsRoute
import org.holidaylink.app.ui.StatusScreen
import org.holidaylink.app.ui.UserProfileScreen
import org.holidaylink.app.ui.rememberNotificationPermissionState
import org.holidaylink.app.ui.theme.HolidayLinkTheme
import org.holidaylink.app.ui.FeatureHubScreen
import org.holidaylink.app.ui.VideoHubScreen
import org.holidaylink.app.ui.WebFeatureScreen
import org.holidaylink.app.ui.HolidayFeature
import org.holidaylink.app.ui.Avatar

/**
 * FragmentActivity, not ComponentActivity: the app-lock gate and Settings use
 * androidx.biometric.BiometricPrompt, which needs a FragmentActivity host.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        setContent { HolidayLinkApp(this) }
    }
}

// ---------------------------------------------------------------- destinations

private enum class Tab(val title: String) {
    Chats("Chats"),
    Users("Users"),
    Channels("Channels"),
    Settings("Settings"),
    Videos("Videos"),
    Menu("Menu")
}

/** Every screen outside the four main tabs. */
private sealed interface Destination {
    data object Tabs : Destination
    data class Chat(val chat: ChatItem) : Destination
    data class PersonProfile(val user: User) : Destination
    data object MyProfile : Destination
    data object NewChat : Destination
    data object Search : Destination
    data object Notifications : Destination
    data object Settings : Destination
    data object Ai : Destination
    data class AiChat(val conversationId: String) : Destination
    data object Calls : Destination
    data class WebFeature(val title: String, val url: String) : Destination
    data object FeatureHub : Destination
    data object Videos : Destination
    data object Status : Destination
}

/**
 * All feature-center entries open native screens backed by the same Render+Neon
 * database — never a website. Only unknown external URLs fall back to WebView.
 */
private fun nativeDestinationFor(route: String): Destination = when {
    route == "native:calls" -> Destination.Calls
    route == "native:status" || route == "status.html" -> Destination.Status
    route == "native:videos" || route == "videos.html" -> Destination.Videos
    route == "native:ai" || route == "aihelp.html" -> Destination.Ai
    route == "native:users" -> Destination.Search
    route == "native:chats" -> Destination.Tabs
    route == "native:store" -> Destination.Settings
    route == "native:invite" -> Destination.NewChat
    route == "dashboard.html" || route.contains("dashboard") -> Destination.Status
    route == "feed.html" -> Destination.Status
    route == "events.html" -> Destination.Videos
    route == "quizzes.html" || route == "papers.html" -> Destination.Ai
    route == "store.html" -> Destination.Settings
    route == "room.html" -> Destination.Tabs
    route == "invite.html" -> Destination.NewChat
    route.startsWith("http") -> Destination.WebFeature("HolidayLink", route)
    route.endsWith(".html") -> Destination.Status
    else -> Destination.FeatureHub
}

// ---------------------------------------------------------------- app root

@Composable
private fun HolidayLinkApp(activity: FragmentActivity) {
    val context = activity.applicationContext
    val prefs = remember(context) {
        context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
    }
    val api = remember(context) { Api(context) }

    HolidayLinkTheme(
        themeMode = AppSettings.state.themeMode,
        fontScale = AppSettings.state.fontScale
    ) {
        AppLockGate {
            var signedIn by remember { mutableStateOf(api.sessionToken() != null) }
            if (signedIn) {
                SignedInApp(
                    api = api,
                    prefs = prefs,
                    onLoggedOut = { signedIn = false }
                )
            } else {
                AuthFlow(api, prefs) { signedIn = true }
            }
        }
    }
}

/**
 * Everything behind sign-in.
 *
 * Navigation is a plain back stack of [Destination] values rather than a navigation graph:
 * the tree is small and entirely state-driven, and Back then behaves correctly at every level
 * (tab, chat, profile, settings sub-screen) with no extra configuration.
 */
@Composable
private fun SignedInApp(
    api: Api,
    prefs: SharedPreferences,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    var me by remember { mutableStateOf<Me?>(null) }
    var tab by remember { mutableStateOf(Tab.Chats) }
    val stack = remember { mutableStateListOf<Destination>(Destination.Tabs) }
    val scope = rememberCoroutineScope()

    val callManager = remember { CallManager(context, api) }
    var activeCall by remember { mutableStateOf<Triple<String,String,User>?>(null) }
    var incomingCall by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        callManager.onIncoming = { inc -> activeCall = Triple(inc.callId, inc.kind, inc.caller); incomingCall = true }
        callManager.connect()
        onDispose { callManager.close() }
    }

    val push: (Destination) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    LaunchedEffect(Unit) {
        runCatching { api.me() }.onSuccess { loaded ->
            me = loaded
            api.sessionToken()?.let { token ->
                AccountStore.upsert(prefs, AccountStore.Account(loaded.id, token, loaded.phone, loaded.displayName, loaded.username))
            }
        }
    }

    val userId = me?.id ?: api.userId().orEmpty()

    val openDirectChat: (User) -> Unit = { user ->
        scope.launch {
            runCatching { api.startDirect(user.id) }
                .onSuccess { id ->
                    stack.add(Destination.Chat(ChatItem(id, "direct", null, user, null, null)))
                }
        }
    }

    // A notification only carries a conversation id, so the chat list is consulted to recover the
    // real title, kind and peer instead of opening a header labelled "Group" for a direct chat.
    val openConversationById: (String) -> Unit = { conversationId ->
        scope.launch {
            val chat = runCatching { api.conversations() }
                .getOrNull()
                ?.firstOrNull { it.id == conversationId }
                ?: ChatItem(conversationId, "direct", null, null, null, null)
            stack.add(Destination.Chat(chat))
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    activeCall?.let { c ->
        CallScreen(callManager, c.third, c.first, c.second, incomingCall, onClose={ activeCall=null; incomingCall=false })
        return
    }

    when (val current = stack.last()) {
        Destination.Tabs -> TabShell(
            api = api,
            me = me,
            tab = tab,
            onTab = { tab = it },
            onOpenChat = { push(Destination.Chat(it)) },
            onOpenProfile = { push(Destination.PersonProfile(it)) },
            onMyProfile = { push(Destination.MyProfile) },
            onNewChat = { push(Destination.NewChat) },
            onSearch = { push(Destination.Search) },
            onNotifications = { push(Destination.Notifications) },
            onSettings = { push(Destination.Settings) },
            onAi = { push(Destination.Ai) },
            onOpenDirect = openDirectChat,
            onOpenVideos = { push(Destination.Videos) },
            onOpenVideoLessons = { push(Destination.Status) },
            onOpenMenu = { push(Destination.FeatureHub) },
            onOpenFeature = { feature ->
                push(nativeDestinationFor(feature.route))
            },
            onOpenProfileTab = { push(Destination.MyProfile) },
            onOpenStatus = { push(Destination.Status) }
        )

        is Destination.Chat -> ChatScreen(
            api = api,
            chat = current.chat,
            currentUserId = userId,
            onBack = pop,
            onOpenProfile = { push(Destination.PersonProfile(it)) },
            onOpenCalls = {
                stack.add(Destination.Calls)
            }
        )

        is Destination.PersonProfile -> UserProfileScreen(
            api = api,
            user = current.user,
            onBack = pop,
            onOpenChat = { user ->
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
                openDirectChat(user)
            },
            onStartCall = { user, kind ->
                scope.launch {
                    runCatching { callManager.start(user.id, kind) }.onSuccess { activeCall=Triple(it.id,kind,user); incomingCall=false }
                }
            }
        )

        Destination.MyProfile -> MyProfileScreen(api = api, onBack = pop)

        Destination.NewChat -> NewChatScreen(
            api = api,
            onBack = pop,
            onOpenChat = { push(Destination.Chat(it)) }
        )

        // Search results are already scoped to the signed-in user, so they open in place.
        Destination.Search -> SearchScreen(
            api = api,
            onBack = pop,
            onOpenChat = { push(Destination.Chat(it)) },
            onOpenUser = { push(Destination.PersonProfile(it)) },
            // Search and notifications can still surface an AI chat; the setting closes every
            // way in, not just the icon in the top bar.
            onOpenAiChat = { if (AppSettings.state.aiEnabled) push(Destination.AiChat(it)) }
        )

        Destination.Notifications -> NotificationListScreen(
            api = api,
            onBack = pop,
            onOpenChat = { conversationId -> openConversationById(conversationId) },
            onOpenAi = { if (AppSettings.state.aiEnabled) push(Destination.Ai) }
        )

        Destination.Calls -> CallsScreen(
            api = api,
            onOpenChat = openDirectChat
        )

        Destination.FeatureHub -> FeatureHubScreen(
            onBack = pop,
            onOpen = { feature ->
                push(nativeDestinationFor(feature.route))
            }
        )

        Destination.Videos -> VideoHubScreen(
            api = api,
            onBack = pop,
            onOpenProfile = { push(Destination.PersonProfile(it)) },
            onOpenLessons = { push(Destination.Status) }
        )

        Destination.Status -> StatusScreen(api = api, me = me)

        is Destination.WebFeature -> WebFeatureScreen(
            url = current.url,
            onBack = pop
        )

        Destination.Settings -> SettingsFlow(
            api = api,
            prefs = prefs,
            me = me,
            onBack = pop,
            onAddAccount = {
                me?.let { loaded ->
                    api.sessionToken()?.let { token ->
                        AccountStore.upsert(prefs, AccountStore.Account(loaded.id, token, loaded.phone, loaded.displayName, loaded.username))
                    }
                }
                prefs.edit().remove("token").remove("userId").apply()
                stack.clear()
                stack.add(Destination.Tabs)
                onLoggedOut()
            },
            onSwitchAccount = { account ->
                prefs.edit()
                    .putString("token", account.token)
                    .putString("userId", account.userId)
                    .putString("phone", account.phone)
                    .apply()
                me = null
                stack.clear()
                stack.add(Destination.Tabs)
                scope.launch { runCatching { api.me() }.onSuccess { me = it } }
            },
            onLoggedOut = {
                prefs.edit().remove("token").remove("userId").apply()
                stack.clear()
                stack.add(Destination.Tabs)
                onLoggedOut()
            }
        )

        // A null id means "pick an AI chat, or start a new one"; AiChatScreen keeps that state.
        Destination.Ai -> AiChatScreen(
            api = api,
            conversationId = null,
            onOpenConversation = { push(Destination.AiChat(it)) },
            onBack = pop
        )

        is Destination.AiChat -> AiChatScreen(
            api = api,
            conversationId = current.conversationId,
            onOpenConversation = { id ->
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
                stack.add(Destination.AiChat(id))
            },
            onBack = pop
        )
    }
}

/** Settings hub plus its sub-screens, kept as one stack so Back behaves predictably. */
@Composable
private fun SettingsFlow(
    api: Api,
    prefs: SharedPreferences,
    me: Me?,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (AccountStore.Account) -> Unit,
    onLoggedOut: () -> Unit
) {
    var route by remember { mutableStateOf<SettingsRoute?>(null) }
    val open = route
    if (open == null) {
        SettingsHub(
            api = api,
            me = me,
            onOpen = { route = it },
            onLoggedOut = onLoggedOut
        )
    } else {
        SettingsHost(
            route = open,
            api = api,
            prefs = prefs,
            me = me,
            onBack = { route = null },
            onAddAccount = onAddAccount,
            onSwitchAccount = onSwitchAccount,
            // Sub-screens can hand off to each other, e.g. Security to the blocked list.
            onOpenRoute = { route = it }
        )
    }
}

// ---------------------------------------------------------------- tabs

@Composable
private fun TabShell(
    api: Api,
    me: Me?,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onOpenChat: (ChatItem) -> Unit,
    onOpenProfile: (User) -> Unit,
    onMyProfile: () -> Unit,
    onNewChat: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    onAi: () -> Unit,
    onOpenDirect: (User) -> Unit,
    onOpenVideos: () -> Unit,
    onOpenVideoLessons: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenFeature: (HolidayFeature) -> Unit,
    onOpenProfileTab: () -> Unit,
    onOpenStatus: () -> Unit = {}
) {
    val notificationPermission = rememberNotificationPermissionState()
    val glassBlue = Color(0xFF0B5CFF)
    val glassCyan = Color(0xFF00D9FF)
    val glassPurple = Color(0xFF7A2CFF)
    val scheme = MaterialTheme.colorScheme
    // Dark/light aware: use theme background, keep glass tint only as overlay.
    val pageBg = Brush.verticalGradient(
        listOf(scheme.background, scheme.surfaceContainerLow, scheme.background)
    )

    Scaffold(
        containerColor = scheme.background,
        bottomBar = {
            GlassBottomBar(tab = tab, onTab = onTab, onVideos = onOpenVideos, onMenu = onOpenMenu, onSettings = onSettings)
        },
        floatingActionButton = {
            if (tab == Tab.Chats || tab == Tab.Users) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(glassCyan.copy(alpha=.95f), glassBlue, glassPurple)))
                        .border(1.5.dp, glassCyan.copy(alpha=.8f), CircleShape)
                        .clickable { onNewChat() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, "Start a new chat", tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(pageBg)
        ) {
            NotificationPermissionBanner(notificationPermission)

            // Exact reference layout: glass header, status row, then one combined search field.
            GlassHeader(
                me = me,
                onNewChat = onNewChat,
                onUsers = { onTab(Tab.Users) },
                onNotifications = onNotifications,
                onProfile = onOpenProfileTab
            )

            GlassStatusRow(
                api = api,
                me = me,
                onOpenStatus = onOpenStatus
            )

            GlassSearchBar(onClick = onSearch)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Chats -> ChatsScreen(
                        api = api,
                        currentUserId = me?.id.orEmpty(),
                        onOpenChat = onOpenChat,
                        showChrome = false
                    )
                    Tab.Users -> ContactsScreen(
                        api = api,
                        onOpenChat = onOpenDirect,
                        onOpenProfile = onOpenProfile
                    )
                    Tab.Channels -> ChannelsGlassScreen(onOpenFeature = onOpenFeature)
                    Tab.Settings -> SettingsHub(
                        api = api,
                        me = me,
                        onOpen = { route -> onSettings() },
                        onLoggedOut = {}
                    )
                    Tab.Videos -> VideoHubScreen(
                        api = api,
                        onBack = { onTab(Tab.Chats) },
                        onOpenLessons = onOpenVideoLessons,
                        onOpenProfile = onOpenProfile
                    )
                    Tab.Menu -> FeatureHubScreen(onBack = { onTab(Tab.Chats) }, onOpen = onOpenFeature)
                }
            }
        }
    }
}

@Composable
private fun GlassHeader(
    me: Me?,
    onNewChat: () -> Unit,
    onUsers: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(shape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.85f))
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("HolidayLink", color = scheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        GlassCircleButton(Icons.Default.Add, "New chat", onNewChat)
        Spacer(Modifier.width(6.dp))
        GlassCircleButton(Icons.Default.Groups, "Users", onUsers)
        Spacer(Modifier.width(6.dp))
        GlassCircleButton(Icons.Default.Notifications, "Notifications", onNotifications)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF1565FF), Color(0xFF7A2CFF)))).border(1.dp, Color(0xFF3DE7FF), CircleShape).clickable { onProfile() }, contentAlignment = Alignment.Center) {
            if (me != null) Avatar(me.displayName, size = 39.dp, photo = me.avatarUrl)
            else Icon(Icons.Default.Person, "Profile", tint = Color.White)
        }
    }
}

@Composable
private fun GlassCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(scheme.primaryContainer)
            .border(1.dp, scheme.outlineVariant, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = scheme.onPrimaryContainer, modifier = Modifier.size(25.dp)) }
}

@Composable
private fun GlassStatusRow(api: Api, me: Me?, onOpenStatus: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var statuses by remember { mutableStateOf<List<org.holidaylink.app.net.StatusUpdate>>(emptyList()) }
    var postTick by remember { mutableStateOf(0) }
    LaunchedEffect(postTick) { runCatching { statuses = api.statuses() } }
    val people = statuses.distinctBy { it.userId }.take(12)
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenStatus() }) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF7A2CFF)))).padding(3.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Your status", tint = scheme.primary, modifier = Modifier.size(30.dp)) }
                }
                Spacer(Modifier.height(4.dp)); Text("Your status", color = scheme.onBackground, fontSize = 11.sp)
            }
        }
        items(people) { status ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenStatus() }) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF8B2DFF)))).padding(3.dp), contentAlignment = Alignment.Center) {
                    Avatar(status.userName, photo = status.avatarUrl, size = 58.dp)
                }
                Spacer(Modifier.height(4.dp)); Text(status.userName.take(10), color = scheme.onBackground, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GlassSearchBar(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.85f))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(28.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(27.dp))
        Spacer(Modifier.width(14.dp)); Text("Search chats, users, channels...", color = scheme.onSurfaceVariant, fontSize = 16.sp)
    }
}

@Composable
private fun GlassBottomBar(tab: Tab, onTab: (Tab) -> Unit, onVideos: () -> Unit, onMenu: () -> Unit, onSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).clip(shape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.95f))
            .border(1.dp, scheme.outlineVariant, shape).padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        val items = listOf(
            Triple(Tab.Chats, Icons.Default.Chat, "Chats"),
            Triple(Tab.Users, Icons.Default.Contacts, "Users"),
            Triple(Tab.Channels, Icons.Default.Campaign, "Channels"),
            Triple(Tab.Settings, Icons.Default.Settings, "Settings"),
            Triple(Tab.Videos, Icons.Default.VideoLibrary, "Videos"),
            Triple(Tab.Menu, Icons.Default.Menu, "Menu")
        )
        items.forEach { (entry, icon, title) ->
            val selected = tab == entry
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .background(if (selected) Brush.horizontalGradient(listOf(Color(0xFF1152C8).copy(alpha=.75f), Color(0xFF5B2CFF).copy(alpha=.55f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { when (entry) { Tab.Videos -> onVideos(); Tab.Menu -> onMenu(); Tab.Settings -> onSettings(); else -> onTab(entry) } }
                    .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Text(title, color = if (selected) scheme.primary else scheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                if (selected) Box(Modifier.padding(top = 3.dp).size(width = 28.dp, height = 3.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF00D9FF), Color(0xFF8B2DFF)))))
            }
        }
    }
}

@Composable
private fun ChannelsGlassScreen(onOpenFeature: (HolidayFeature) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val channelFeatures = listOf(
        "Home Feed" to "Announcements and HolidayLink updates",
        "Daily Feed" to "Entertainment and student updates",
        "Events" to "Discover HolidayLink events",
        "Video Lessons" to "Educational broadcasts and lessons"
    )
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Channels", color = scheme.onBackground, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Follow public HolidayLink channels", color = scheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        channelFeatures.forEach { (title, subtitle) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surfaceContainerHigh.copy(alpha = 0.9f)).border(1.dp, scheme.outlineVariant, RoundedCornerShape(22.dp)).clickable {
                val feature = when(title) {
                    "Events" -> HolidayFeature(title, subtitle, Icons.Default.Campaign, listOf(Color(0xFF06B6D4), Color(0xFF2563EB)), "native:videos")
                    "Video Lessons" -> HolidayFeature(title, subtitle, Icons.Default.VideoLibrary, listOf(Color(0xFF06B6D4), Color(0xFF7C3AED)), "native:videos")
                    "Daily Feed" -> HolidayFeature(title, subtitle, Icons.Default.Campaign, listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)), "native:status")
                    else -> HolidayFeature(title, subtitle, Icons.Default.Campaign, listOf(Color(0xFF06B6D4), Color(0xFF2563EB)), "native:status")
                }
                onOpenFeature(feature)
            }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(50.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF7A2CFF)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Campaign, null, tint = Color.White) }
                Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, color = scheme.onSurface, fontWeight = FontWeight.Bold); Text(subtitle, color = scheme.onSurfaceVariant, fontSize = 12.sp) }
            }
        }
    }
}

private fun websiteUrl(path: String): String {
    val clean = path.removePrefix("/")
    return when {
        clean.startsWith("http://") || clean.startsWith("https://") -> clean
        clean.contains("#") -> "https://holidaylink.org/${clean.substringBefore('#')}#${clean.substringAfter('#')}"
        else -> "https://holidaylink.org/$clean"
    }
}

@Composable
private fun OverflowMenu(onSettings: () -> Unit, onMyProfile: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Settings, "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("My profile") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                onClick = {
                    expanded = false
                    onMyProfile()
                }
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Default.Settings, null) },
                onClick = {
                    expanded = false
                    onSettings()
                }
            )
        }
    }
}

// ---------------------------------------------------------------- auth

private fun friendlyMessage(e: Throwable, fallback: String): String =
    (e as? ApiException)?.message?.takeIf { it.isNotBlank() } ?: fallback

private fun saveSession(prefs: SharedPreferences, token: String?, userId: String?) {
    val editor = prefs.edit()
    if (!token.isNullOrBlank()) editor.putString("token", token)
    if (!userId.isNullOrBlank()) editor.putString("userId", userId)
    editor.apply()
}

private const val OTP_LENGTH = 6

/**
 * Sign-in: phone number, SMS code, then the profile details for a brand new account.
 * The number the server says it will verify against is always the number we send back for
 * verification, so the two requests can never disagree.
 */
@Composable
fun AuthFlow(api: Api, prefs: SharedPreferences, onLoggedIn: () -> Unit) {
    var country by remember {
        mutableStateOf(Countries.byIso(prefs.getString("countryIso", Countries.DEFAULT_ISO)))
    }
    var national by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var twoFactorPassword by remember { mutableStateOf("") }
    var useTwoFactor by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var signingUp by remember { mutableStateOf(false) }
    var verifiedPhone by remember { mutableStateOf("") }
    var cooldown by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    val international = remember(national, country) {
        PhoneNumbers.toInternational(country.dialCode, national)
    }
    val phoneValid = remember(international) { PhoneNumbers.isValidInternational(international) }


    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    fun loginTwoFactor() {
        if (twoFactorPassword.length < 8) { error = "Enter your 2FA password."; return }
        scope.launch {
            runCatching { api.loginWithTwoFactor(verifiedPhone.ifBlank { international }, twoFactorPassword) }
                .onSuccess { result -> saveSession(prefs, result.token, result.userId); onLoggedIn() }
                .onFailure { error = if (it is ApiException) friendlyMessage(it, "2FA login failed.") else "2FA login failed." }
        }
    }

    fun sendCode(resend: Boolean) {
        if (sending) return
        if (resend && cooldown > 0) return
        sending = true
        error = ""
        info = ""
        scope.launch {
            try {
                val result = api.requestOtp(international)
                verifiedPhone = result.phone
                prefs.edit()
                    .putString("phone", result.phone)
                    .putString("countryIso", country.iso)
                    .apply()
                cooldown = result.resendAfterSeconds
                info = "Verification code sent to ${PhoneNumbers.mask(result.phone)}."
                if (!resend) step = 1
            } catch (e: ApiException) {
                error = friendlyMessage(e, "We couldn't send the code. Please try again.")
                if (e.retryAfterSeconds > 0) cooldown = e.retryAfterSeconds
            } catch (_: Exception) {
                error = "We couldn't send the code. Please try again."
            } finally {
                sending = false
            }
        }
    }


    fun continueFromPhone() {
        if (sending) return
        sending=true; error=""; info=""
        scope.launch {
            try {
                val check=api.checkPhone(international); verifiedPhone=check.phone
                if(check.exists && check.twoFactorEnabled){ step=3 } else { sending=false; sendCode(resend=false); return@launch }
            } catch(e:ApiException){ error=friendlyMessage(e,"We couldn't check that number.") }
            finally { sending=false }
        }
    }

    fun verifyCode() {
        if (verifying) return
        val code = PhoneNumbers.sanitizeOtp(pin)
        if (code.length != OTP_LENGTH) {
            error = "Enter the $OTP_LENGTH-digit code from the SMS."
            return
        }
        verifying = true
        error = ""
        info = ""
        scope.launch {
            try {
                val result = api.verifyOtp(verifiedPhone.ifBlank { international }, code)
                if (result.needsProfile) {
                    verifiedPhone = result.phone
                    pin = ""
                    step = 2
                } else {
                    saveSession(prefs, result.token, result.userId)
                    onLoggedIn()
                }
            } catch (e: ApiException) {
                error = friendlyMessage(e, "That code is incorrect or has expired. Check the SMS and try again.")
            } catch (_: Exception) {
                error = "That code is incorrect or has expired. Check the SMS and try again."
            } finally {
                verifying = false
            }
        }
    }

    fun createAccount() {
        if (signingUp) return
        signingUp = true
        error = ""
        scope.launch {
            try {
                val result = api.completeProfile(
                    verifiedPhone.ifBlank { international },
                    display.trim(),
                    username.trim(),
                    email.trim(),
                    twoFactorPassword.trim().takeIf { it.length >= 8 }
                )
                saveSession(prefs, result.token, result.userId)
                onLoggedIn()
            } catch (e: ApiException) {
                error = friendlyMessage(e, "We couldn't create your account. Please try again.")
            } catch (_: Exception) {
                error = "We couldn't create your account. Please try again."
            } finally {
                signingUp = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("HolidayLink", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (step) {
                        0 -> "Your chats. Your people. Your holiday life."
                        1 -> "Enter the SMS code"
                        2 -> "Create your HolidayLink profile"
                        3 -> "Enter your 2FA password"
                        else -> "HolidayLink"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (step) {
                    0 -> {
                        Text(
                            "Enter your number. HolidayLink will check the account first, then use 2FA or SMS as required.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CountryCodeField(country, width = 116.dp) { selected ->
                                country = selected
                                prefs.edit().putString("countryIso", selected.iso).apply()
                                error = ""
                            }
                            OutlinedTextField(
                                value = national,
                                onValueChange = { input ->
                                    national = input.filter { it.isDigit() }.take(15)
                                    error = ""
                                },
                                label = { Text("Phone number") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            if (phoneValid) "Code will be sent to $international"
                            else "Enter your number without the ${country.dialLabel} prefix.",
                            color = if (phoneValid) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { continueFromPhone() },
                            enabled = phoneValid && !sending,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (sending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text("  Sending code...")
                            } else {
                                Text("Continue")
                            }
                        }
                    }

                    1 -> {
                        Text(
                            "Verification code sent to ${PhoneNumbers.mask(verifiedPhone.ifBlank { international })}.",
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { input ->
                                pin = PhoneNumbers.sanitizeOtp(input)
                                error = ""
                            },
                            label = { Text("$OTP_LENGTH-digit verification code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { verifyCode() },
                            enabled = pin.isNotBlank() && !verifying,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (verifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text("  Verifying...")
                            } else {
                                Text("Verify")
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { sendCode(resend = true) },
                                enabled = !sending && cooldown == 0
                            ) {
                                Text(
                                    when {
                                        sending -> "Sending code..."
                                        cooldown > 0 -> "Resend code in ${cooldown}s"
                                        else -> "Resend code"
                                    }
                                )
                            }
                            TextButton(onClick = {
                                step = 0
                                pin = ""
                                error = ""
                                info = ""
                            }) { Text("Change number") }
                        }
                    }

                    3 -> {
                        Text("2FA password required", style=MaterialTheme.typography.titleLarge)
                        Text("This number is already registered with 2FA enabled.", color=MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(twoFactorPassword, { twoFactorPassword = it }, label={Text("2FA password")}, singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password), modifier=Modifier.fillMaxWidth())
                        Button(onClick={loginTwoFactor()}, enabled=twoFactorPassword.length>=8, modifier=Modifier.fillMaxWidth()){ Text("Login") }
                        TextButton(onClick={step=0;twoFactorPassword=""}){ Text("Use a different number") }
                    }

                    2 -> {
                        Text(
                            "Signed in as ${PhoneNumbers.mask(verifiedPhone.ifBlank { international })}. " +
                                "Pick how you appear in HolidayLink.",
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            display, { display = it },
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            username, { username = it },
                            label = { Text("@username - must be unique") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            email, { email = it },
                            label = { Text("Email (optional backup)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            twoFactorPassword, { twoFactorPassword = it },
                            label = { Text("2FA password (optional, 8+ characters)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { createAccount() },
                            enabled = !signingUp && display.isNotBlank() && username.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (signingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text("  Creating account...")
                            } else {
                                Text("Create account")
                            }
                        }
                        TextButton(
                            onClick = {
                                step = 0
                                error = ""
                                info = ""
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("Use a different number") }
                    }
                }

                if (info.isNotBlank() && step == 0) {
                    Text(info, color = MaterialTheme.colorScheme.primary)
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CountryCodeField(
    country: CountryCode,
    width: Dp,
    onSelect: (CountryCode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.width(width)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(12.dp)
                )
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(country.flag, fontSize = 15.sp)
            Text(
                " ${country.dialLabel}",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Choose country",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Countries.ALL.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.searchLabel, maxLines = 1) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}