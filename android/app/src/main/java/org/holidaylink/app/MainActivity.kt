@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app

import org.holidaylink.app.ui.tr
import org.holidaylink.app.ui.segmentedStatusRing
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.holidaylink.app.config.AppConfig
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.data.AccountStore
import org.holidaylink.app.data.ProfileCache
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.AccountSession
import org.holidaylink.app.net.AnnouncementNotifier
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.awaitOrNull
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.FirestoreChat
import org.holidaylink.app.net.FirestoreGroups
import org.holidaylink.app.net.CallManager
import org.holidaylink.app.ui.CallScreen
import org.holidaylink.app.ui.MinimizedCallBar
import org.holidaylink.app.net.ChatItem
import org.holidaylink.app.net.Me
import org.holidaylink.app.net.Notifier
import org.holidaylink.app.net.User
import org.holidaylink.app.net.StatusUpdate
import org.holidaylink.app.push.PushTokens
import org.holidaylink.app.phone.Countries
import org.holidaylink.app.phone.CountryCode
import org.holidaylink.app.phone.PhoneNumbers
import org.holidaylink.app.ui.AiChatScreen
import org.holidaylink.app.ui.AiProfileScreen
import org.holidaylink.app.ui.EventsScreen
import org.holidaylink.app.ui.FeedScreen
import org.holidaylink.app.ui.InviteScreen
import org.holidaylink.app.ui.PapersScreen
import org.holidaylink.app.ui.QuizzesScreen
import org.holidaylink.app.ui.RankingsScreen
import org.holidaylink.app.ui.WalletScreen
import org.holidaylink.app.ui.AppLockGate
import org.holidaylink.app.ui.CallsScreen
import org.holidaylink.app.ui.ChatScreen
import org.holidaylink.app.ui.ChatsScreen
import org.holidaylink.app.ui.ContactsScreen
import org.holidaylink.app.ui.UsersScreen
import org.holidaylink.app.ui.MyProfileScreen
import org.holidaylink.app.ui.BatteryOptimizationBanner
import org.holidaylink.app.ui.NewChatScreen
import org.holidaylink.app.ui.NotificationListScreen
import org.holidaylink.app.ui.NotificationPermissionBanner
import org.holidaylink.app.ui.SearchScreen
import org.holidaylink.app.ui.SettingsHub
import org.holidaylink.app.ui.SettingsHost
import org.holidaylink.app.ui.SettingsRoute
import org.holidaylink.app.ui.StatusScreen
import org.holidaylink.app.ui.SubscriptionScreen
import org.holidaylink.app.ui.StarredMessagesScreen
import org.holidaylink.app.ui.UserProfileScreen
import org.holidaylink.app.ui.rememberBatteryOptimizationState
import org.holidaylink.app.ui.rememberNotificationPermissionState
import org.holidaylink.app.ui.theme.HolidayLinkTheme
import org.holidaylink.app.ui.StartupUpdateGate
import org.holidaylink.app.ui.FeatureHubScreen
import org.holidaylink.app.ui.GroupInfoScreen
import org.holidaylink.app.ui.StoreScreen
import org.holidaylink.app.ui.WebFeatureScreen
import org.holidaylink.app.ui.HolidayFeature
import org.holidaylink.app.ui.Avatar
import org.holidaylink.app.ui.LoadingState
import org.holidaylink.app.ui.EmptyState
import org.holidaylink.app.ui.ErrorState
import org.holidaylink.app.ui.floatingNavBottomPadding
import org.holidaylink.app.ui.ChatLockScreen
import org.holidaylink.app.ui.ScheduledMessagesScreen
import org.holidaylink.app.ui.NavTransitionHost
import org.holidaylink.app.ui.navTransitionOrNone

/**
 * FragmentActivity, not ComponentActivity: the app-lock gate and Settings use
 * androidx.biometric.BiometricPrompt, which needs a FragmentActivity host.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        // A crash report from a previous run no longer replaces the app: crashes are still
        // recorded privately (see CrashGuard) for support, but they never block opening.
        NotificationRouter.consume(intent)
        org.holidaylink.app.ui.IncomingShare.consume(intent)
        setContent { HolidayLinkApp(this) }
    }

    override fun onStart() {
        super.onStart()
        // Presence is tied to the visible app: the moment we leave the screen we go offline
        // so the server stops treating this phone as "already reading" and pushes instead.
        org.holidaylink.app.net.AppPresence.onForeground()
        // Coming back from Settings, a reboot, or "clear all" wipes the ongoing card, so put
        // the cached announcement back straight away instead of waiting on Firestore.
        if (FirebaseBackend.uid?.isNotBlank() == true) {
            AnnouncementNotifier.repost(this)
        }
    }

    override fun onStop() {
        org.holidaylink.app.net.AppPresence.onBackground()
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationRouter.consume(intent)
        org.holidaylink.app.ui.IncomingShare.consume(intent)
    }
}

/**
 * Carries a tapped-notification target into the Compose tree. The system tray intent carries
 * only a conversation id, so [HolidayLinkApp] turns it back into a real chat screen.
 */
private object NotificationRouter {
    var pendingChatId by mutableStateOf<String?>(null)
        private set

    fun consume(intent: android.content.Intent?) {
        if (intent == null) return
        // Our own notifications carry "hl_target"; notifications drawn by the system from a
        // Firebase push carry the raw data payload instead, so accept both shapes.
        val id = intent.getStringExtra(org.holidaylink.app.net.Notifier.EXTRA_TARGET)
            ?: intent.getStringExtra("chatId")
        if (!id.isNullOrBlank() && id != "null") pendingChatId = id
    }

    fun clear() {
        pendingChatId = null
    }

    /** Open this conversation (used after sharing something into it). */
    fun open(chatId: String) {
        pendingChatId = chatId
    }
}

// ---------------------------------------------------------------- destinations

private enum class Tab(val title: String) {
    Chats("Chats"),
    Users("Users"),
    Channels("Channels"),
    Settings("Settings"),
    Menu("Menu")
}

/** Every screen outside the four main tabs. */
private sealed interface Destination {
    data object Tabs : Destination
    data class Chat(val chat: ChatItem, val statusReply: StatusUpdate? = null) : Destination
    data class GroupInfo(val chat: ChatItem) : Destination
    data class PersonProfile(val user: User) : Destination
    data object MyProfile : Destination
    data object NewChat : Destination
    data object NewGroup : Destination
    data object NewChannel : Destination
    data object NewCommunity : Destination
    data object Search : Destination
    data object Notifications : Destination
    data object Settings : Destination
    data object Ai : Destination
    data class AiChat(val conversationId: String) : Destination
    data object AiProfile : Destination
    data object Calls : Destination
    data class WebFeature(val title: String, val url: String) : Destination
    data object FeatureHub : Destination
    data class Status(
        val startWithComposer: Boolean = false,
        val userId: String? = null,
        /** Something shared into HolidayLink from another app, to post as a status. */
        val sharedUri: android.net.Uri? = null,
        val sharedIsVideo: Boolean = false,
        val sharedText: String? = null
    ) : Destination
    data class ScheduledMessages(val chatId: String, val chatLabel: String) : Destination
    data object Store : Destination
    data object Subscription : Destination
    data object Feed : Destination
    data object Quizzes : Destination
    data object Papers : Destination
    data object Events : Destination
    data object Wallet : Destination
    data object Rankings : Destination
    data object Invite : Destination
    data object Starred : Destination
}

/**
 * All feature-center entries open native screens backed by the same Firebase
 * database — never a website. Only unknown external URLs fall back to WebView.
 */
private fun nativeDestinationFor(route: String): Destination = when {
    route == "native:calls" -> Destination.Calls
    route == "native:status" || route == "status.html" -> Destination.Status(false)
    route == "native:feed" || route == "feed.html" -> Destination.Feed
    route == "native:quizzes" || route == "quizzes.html" -> Destination.Quizzes
    route == "native:papers" || route == "papers.html" -> Destination.Papers
    route == "native:events" || route == "events.html" -> Destination.Events
    route == "native:wallet" -> Destination.Wallet
    route == "native:rankings" -> Destination.Rankings
    route == "native:ai" || route == "aihelp.html" -> Destination.Ai
    route == "native:users" -> Destination.Search
    route == "native:chats" -> Destination.Tabs
    route == "native:store" -> Destination.Store
    route == "native:subscription" || route == "subscription.html" -> Destination.Subscription
    route == "native:invite" -> Destination.Invite
    route == "native:starred" -> Destination.Starred
    route == "dashboard.html" || route.contains("dashboard") -> Destination.Feed
    route == "store.html" -> Destination.Store
    route == "room.html" -> Destination.Tabs
    route == "invite.html" -> Destination.Invite
    route.startsWith("http") -> Destination.WebFeature("HolidayLink", route)
    route.endsWith(".html") -> Destination.Feed
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
        // Every screen paints on the theme background, so the Menu hub and any page that does
        // not draw its own Scaffold still follows light/dark mode instead of the raw window.
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
        StartupUpdateGate()
        AppLockGate {
            var signedIn by remember { mutableStateOf(FirebaseBackend.currentUser != null) }
            var addingAccount by remember { mutableStateOf(false) }
            var switchingTo by remember { mutableStateOf<String?>(null) }
            var sessionKey by remember { mutableStateOf(0) }
            var bannedMsg by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            val banMsg = bannedMsg

            // Hop to an account already saved on this phone. Its stored token buys a fresh
            // Firebase session (no code, no SMS); if that token has expired or the server is
            // unreachable we fall back to the normal sign-in screen so nobody gets stuck.
            val switchAccount: (AccountStore.Account) -> Unit = { account ->
                if (account.token.isBlank()) {
                    addingAccount = true
                } else {
                    switchingTo = account.userId
                    scope.launch {
                        val ok = runCatching {
                            val session = AccountSession.switch(context, account.token)
                            FirebaseBackend.signInWithCustomToken(session.customToken)
                            val phone = session.phone.takeIf { it.isNotBlank() } ?: account.phone
                            finishFirebaseLogin(prefs, session.uid, phone, countryIsoFor(phone, prefs))
                            // This device belongs on the new account's push list too.
                            runCatching { PushTokens.register() }
                        }.isSuccess
                        // A stale/expired token (or an unreachable server) sends the user to the
                        // normal sign-in screen rather than leaving them nowhere.
                        if (ok) sessionKey += 1 else addingAccount = true
                        switchingTo = null
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                // No wallpaper here on purpose. A background is a property of a conversation,
                // so ChatScreen draws its own; every other screen sits on the plain theme
                // background rather than showing chat artwork behind Settings and the hubs.
                when {
                    banMsg != null -> BannedScreen(message = banMsg, onSwitchNumber = {
                        FirebaseBackend.signOut()
                        prefs.edit().remove("token").remove("userId").apply()
                        bannedMsg = null
                        signedIn = false
                    })

                    // Adding / switching account: keep the current session until verification
                    // completes, and allow backing out so nobody gets stranded on the login screen.
                    signedIn && addingAccount ->
                        AuthFlow(api, prefs, onCancel = { addingAccount = false }) { addingAccount = false }

                    signedIn -> key(sessionKey) {
                        // Make sure this account is in the local list (and has a token) so it can
                        // be switched back to later. A no-op once it is already saved.
                        LaunchedEffect(sessionKey) { rememberCurrentAccount(context, prefs) }
                        SignedInApp(
                            api = api,
                            prefs = prefs,
                            onAddAccount = { addingAccount = true },
                            onSwitchAccount = switchAccount,
                            onLoggedOut = {
                                scope.launch { runCatching { FirebaseBackend.goOffline() } }
                                FirebaseBackend.signOut()
                                signedIn = false
                            },
                            onBanned = { bannedMsg = it }
                        )
                    }

                    else -> AuthFlow(api, prefs) { signedIn = true }
                }

                if (switchingTo != null) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(14.dp))
                            Text("Switching account…", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
    onAddAccount: () -> Unit,
    onSwitchAccount: (AccountStore.Account) -> Unit,
    onLoggedOut: () -> Unit,
    onBanned: (String) -> Unit
) {
    val context = LocalContext.current
    // Seeded from the phone so Settings and the avatar are right before (or without) a network
    // round trip; the Firestore copy replaces it a moment later when there is internet.
    var me by remember {
        mutableStateOf(cachedMe(prefs, FirebaseBackend.uid.orEmpty()))
    }
    var tab by remember { mutableStateOf(Tab.Chats) }
    val stack = remember { mutableStateListOf<Destination>(Destination.Tabs) }
    val scope = rememberCoroutineScope()

    val callManager = remember { CallManager(context, api) }
    var activeCall by remember { mutableStateOf<Triple<String,String,User>?>(null) }
    var incomingCall by remember { mutableStateOf(false) }
    // A minimised call keeps running: the full-screen surface is swapped for a small bar so
    // the user can keep using the rest of the app without dropping the call.
    var callMinimized by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        callManager.onIncoming = { inc -> activeCall = Triple(inc.callId, inc.kind, inc.caller); incomingCall = true }
        // Active while the call screen is not composed (i.e. while minimised). The call screen
        // saves and restores this handler, so a hang-up from the other side clears the bar.
        callManager.onState = { state ->
            if (state == "ended" || state == "declined" || state == "missed" || state == "failed") {
                activeCall = null
                incomingCall = false
                callMinimized = false
            }
        }
        callManager.connect()
        onDispose { callManager.close() }
    }

    // Keep an answered call alive and visible in the notification shade even when the user
    // leaves the app or locks the screen.
    LaunchedEffect(activeCall) {
        val call = activeCall
        if (call != null) {
            // Never throws: a refused foreground service must not close the app mid-call.
            org.holidaylink.app.call.CallService.start(context, call.first, call.third.displayName, call.second)
        } else {
            org.holidaylink.app.call.CallService.stop(context)
        }
    }

    // The pinned announcement notification: refreshed on every app open and rewritten
    // whenever a newer poster document lands.
    LaunchedEffect(Unit) {
        runCatching { AnnouncementNotifier.start(context) }
    }
    // Every time the screen comes back (from Settings, the permission dialog, "clear all",
    // or after the app was killed) the ongoing card is put back from the cached copy.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        runCatching { AnnouncementNotifier.repost(context) }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { AnnouncementNotifier.stop() } }
    }

    // A call only starts once the microphone (and camera for video) is granted. Before
    // this, a denied permission made CallManager.start throw and the tap did nothing.
    var pendingCall by remember { mutableStateOf<Pair<User, String>?>(null) }
    // The call screen is shown at once ("Calling…") while the connection is being prepared.
    var dialing by remember { mutableStateOf<Pair<User, String>?>(null) }
    var dialCancelled by remember { mutableStateOf(false) }
    fun launchCall(user: User, kind: String) {
        dialing = user to kind
        dialCancelled = false
        scope.launch {
            runCatching { callManager.start(user.id, kind) }
                .onSuccess {
                    dialing = null
                    if (dialCancelled) runCatching { callManager.end() }
                    else { activeCall = Triple(it.id, kind, user); incomingCall = false }
                }
                .onFailure { e ->
                    dialing = null
                    Toast.makeText(
                        context,
                        (e as? ApiException)?.message ?: e.message ?: "Couldn't start the call.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
    val callPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val pending = pendingCall
        pendingCall = null
        if (pending == null) return@rememberLauncherForActivityResult
        if (granted.values.all { it }) {
            launchCall(pending.first, pending.second)
        } else {
            Toast.makeText(
                context,
                "Allow the microphone${if (pending.second == "video") " and camera" else ""} to make calls.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    fun beginCall(user: User, kind: String) {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (kind == "video") add(Manifest.permission.CAMERA)
        }
        val missing = needed.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            launchCall(user, kind)
        } else {
            pendingCall = user to kind
            callPermission.launch(missing.toTypedArray())
        }
    }

    val push: (Destination) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    LaunchedEffect(Unit) {
        runCatching {
            val uid = FirebaseBackend.uid ?: return@runCatching
            FirebaseBackend.touchPresence()
            // Bounded on purpose: offline Firestore keeps this call pending for a long time,
            // and the profile from the phone is already on screen, so there is nothing to gain
            // by waiting — without the timeout the app sat on "Not signed in".
            val profile = withTimeoutOrNull(8_000) { FirebaseBackend.loadProfile(uid) }
                ?: return@runCatching
            val fresh = Me(
                id = uid,
                phone = profile.phone,
                email = profile.email.takeIf { it.isNotBlank() },
                displayName = profile.name.takeIf { it.isNotBlank() } ?: "HolidayLink user",
                username = profile.username,
                avatarUrl = profile.profilePic,
                about = profile.bio.takeIf { it.isNotBlank() },
                online = true
            )
            me = fresh
            ProfileCache.save(prefs, fresh)
        }
        // A website-admin ban lands mid-session too: re-check quietly every minute.
        // Same tick keeps presence truthful (online + fresh lastSeen) — but only while the
        // app is actually on screen, otherwise the server would never push a notification.
        while (true) {
            delay(60_000)
            runCatching { org.holidaylink.app.net.AppPresence.heartbeatIfVisible() }
            val uid = FirebaseBackend.uid
            val banned = uid != null && runCatching {
                FirebaseBackend.loadProfile(uid)?.banned == true
            }.getOrDefault(false)
            if (banned) {
                val reason = runCatching { FirebaseBackend.loadProfile(uid!!)?.banReason }.getOrNull()
                onBanned(reason?.takeIf { it.isNotBlank() }?.let { "This account was banned: $it" }
                    ?: "This account was banned.")
                break
            }
        }
    }

    val userId = me?.id ?: FirebaseBackend.uid.orEmpty()

    // Real system notifications: create the channels and register this device's FCM token…
    LaunchedEffect(userId) {
        if (userId.isBlank()) return@LaunchedEffect
        // Belt and braces: a failure here (channels, FCM token) must never kill startup.
        runCatching { Notifier.ensureChannels(context) }
        runCatching { PushTokens.register() }
    }
    // …then watch this user's notification documents so a new alert actually lands in the
    // phone's notification shade while the app is alive (FCM covers the app-killed case).
    DisposableEffect(userId) {
        if (userId.isBlank()) return@DisposableEffect onDispose {}
        val seen = mutableSetOf<String>()
        var primed = false
        // Query wide with a single equality filter and order client-side: a
        // `whereEqualTo + orderBy` pair needs a composite index, which would fail silently.
        val reg = FirebaseBackend.db.collection("notifications")
            .whereEqualTo("userId", userId)
            .limit(40)
            .addSnapshotListener { snap, e ->
                if (snap == null || e != null) return@addSnapshotListener
                // This runs on the main thread, so a single document with an unexpected
                // field type must never be allowed to crash the process.
                try {
                    val ordered = snap.documents
                        .sortedByDescending { it.safeTimestamp("timestamp")?.seconds ?: 0L }
                if (!primed) {
                        // First snapshot is the existing backlog: learn it, don't buzz for it.
                        ordered.forEach { seen.add(it.id) }
                        primed = true
                        return@addSnapshotListener
                    }
                    snap.documentChanges.forEach { change ->
                        if (change.type.name != "ADDED") return@forEach
                        val d = change.document
                        if (!seen.add(d.id)) return@forEach
                        if (d.getBoolean("read") == true) return@forEach
                        val type = d.getString("type") ?: "message"
                        // Plain messages are already covered by the unread-counter listener below;
                        // skipping them here stops the same message buzzing twice.
                        if (type == "message") return@forEach
                        val title = d.getString("title") ?: "HolidayLink"
                        val body = d.getString("body") ?: ""
                        // A call alert opens no conversation of its own — passing the callId as a
                        // chat id would open (and even create) a bogus "direct" chat.
                        val chatTarget = d.getString("chatId") ?: d.getString("conversationId")
                        if (type == "call") {
                            Notifier.call(context, d.id.hashCode(), title, body, chatTarget)
                        } else {
                            Notifier.message(context, d.id.hashCode(), title, body, chatTarget)
                        }
                    }
                } catch (_: Exception) {
                    // Notifications are best effort; a bad document must never be fatal.
                }
            }
        onDispose { reg.remove() }
    }

    // Live message notifications: a chat doc's `unread_<uid>` counter only rises when a
    // message actually arrives for us, so we watch it and raise a system notification.
    LaunchedEffect(userId) {
        if (userId.isBlank()) return@LaunchedEffect
        val lastUnread = HashMap<String, Int>()
        var primed = false
        // A Firestore/flow error must not escape into the composition and crash the app.
        runCatching {
            FirestoreChat.chatsFlow(userId).collect { rows ->
                runCatching { org.holidaylink.app.ui.ShareShortcuts.publish(context.applicationContext, rows) }
                rows.forEach { row ->
                    row.peerId?.let { peer ->
                        if (org.holidaylink.app.ui.UnreadCounts.byPeer[peer] != row.unread) {
                            org.holidaylink.app.ui.UnreadCounts.byPeer[peer] = row.unread
                        }
                    }
                }
                if (!primed) {
                    rows.forEach { lastUnread[it.id] = it.unread }
                    primed = true
                    return@collect
                }
                rows.forEach { row ->
                    val prev = lastUnread[row.id] ?: 0
                    if (row.unread > prev) {
                        // The message reached this phone: double grey tick for the sender.
                        runCatching { FirestoreChat.markDelivered(row.id) }
                        val openHere = (stack.lastOrNull() as? Destination.Chat)?.chat?.id == row.id
                        if (openHere) {
                            // Reading it here and now: no alert, and any old alert disappears.
                            Notifier.clearChat(context, row.id)
                        } else {
                            // The other person (direct chats only): their name and picture.
                            val peerUser = if (!row.isGroup && !row.peerId.isNullOrBlank()) {
                                runCatching { FirestoreChat.resolvePeer(row.peerId.orEmpty()) }.getOrNull()
                            } else null
                            val who = row.title.takeIf { it.isNotBlank() }
                                ?: peerUser?.displayName
                                ?: "New message"
                            Notifier.message(
                                context, Notifier.idFor(row.id), who, row.lastBody ?: "New message", row.id,
                                photoUrl = peerUser?.avatarUrl
                            )
                        }
                    }
                    lastUnread[row.id] = row.unread
                }
            }
        }
    }

    val openDirectChat: (User) -> Unit = { user ->
        scope.launch {
            runCatching { FirestoreChat.startDirect(user.id) }
                .onSuccess { id ->
                    stack.add(Destination.Chat(ChatItem(id, "direct", null, user, null, null)))
                }
        }
    }

    // A notification only carries a conversation id, so the chat list is consulted to recover the
    // real title, kind and peer instead of opening a header labelled "Group" for a direct chat.
    val openConversationById: (String) -> Unit = { conversationId ->
        scope.launch {
            val doc = runCatching {
                FirebaseBackend.db.collection("chats").document(conversationId).get().await()
            }.getOrNull()
            val chat = if (doc != null && doc.exists()) {
                val users = (doc.get("users") as? List<String>) ?: emptyList()
                val isGroup = doc.getBoolean("isGroup") == true
                val me = FirebaseBackend.uid.orEmpty()
                if (!isGroup) {
                    val peerId = users.firstOrNull { it != me }.orEmpty()
                    val peer = runCatching { FirestoreChat.resolvePeer(peerId) }.getOrNull()
                    ChatItem(conversationId, "direct", null, peer, null, null)
                } else {
                    val kind = when {
                        doc.getBoolean("isCommunity") == true -> "community"
                        doc.getBoolean("isBroadcast") == true -> "channel"
                        else -> "group"
                    }
                    ChatItem(conversationId, kind, doc.getString("groupName"), null, null, null)
                }
            } else {
                ChatItem(conversationId, "direct", null, null, null, null)
            }
            stack.add(Destination.Chat(chat))
        }
    }

    // Scheduled messages are dispatched by the authenticated Render server, not this UI loop.

    // Tapping a system notification opens the conversation it refers to.
    val notifiedChatId = NotificationRouter.pendingChatId
    LaunchedEffect(notifiedChatId) {
        if (notifiedChatId != null) {
            openConversationById(notifiedChatId)
            NotificationRouter.clear()
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // Another app shared something to HolidayLink: choose where it goes.
    if (org.holidaylink.app.ui.IncomingShare.hasContent) {
        org.holidaylink.app.ui.ShareToScreen(
            onOpenChat = { chatId -> NotificationRouter.open(chatId) },
            onShareToStatus = {
                val first = org.holidaylink.app.ui.IncomingShare.items.firstOrNull()
                val destination = Destination.Status(
                    sharedUri = first?.uri,
                    sharedIsVideo = first?.mime?.startsWith("video/") == true,
                    sharedText = org.holidaylink.app.ui.IncomingShare.text
                )
                org.holidaylink.app.ui.IncomingShare.clear()
                push(destination)
            },
            onCancel = { org.holidaylink.app.ui.IncomingShare.clear() }
        )
        return
    }

    val dialingNow = dialing
    if (dialingNow != null && activeCall == null) {
        org.holidaylink.app.ui.DialingScreen(
            user = dialingNow.first,
            kind = dialingNow.second,
            onCancel = { dialCancelled = true; dialing = null }
        )
        return
    }

    val ongoingCall = activeCall
    if (ongoingCall != null && !callMinimized) {
        CallScreen(
            callManager,
            ongoingCall.third,
            ongoingCall.first,
            ongoingCall.second,
            incomingCall,
            onClose = { activeCall = null; incomingCall = false; callMinimized = false },
            // Once the call has been answered it is no longer "incoming": if the screen is
            // restored from the minimised bar it must come back as a live call, not ring again.
            onMinimize = { callMinimized = true; incomingCall = false }
        )
        return
    }

    // Navigation itself is a hard cut; any movement comes from the transition the user picked
    // in Settings > Animations, and the default is none.
    val settings = AppSettings.state
    Box(Modifier.fillMaxSize()) {
    NavTransitionHost(
        transition = navTransitionOrNone(settings.transitionStyle, settings.animationsEnabled),
        key = stack.last(),
        enabled = true
    ) {
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
            onOpenMenu = { push(Destination.FeatureHub) },
            onOpenFeature = { feature ->
                push(nativeDestinationFor(feature.route))
            },
            onOpenProfileTab = { push(Destination.MyProfile) },
            onOpenStatus = { push(Destination.Status(false)) },
            onOpenUserStatus = { push(Destination.Status(false, it)) },
            onPostStatus = { push(Destination.Status(true)) },
            onNewGroup = { push(Destination.NewGroup) },
            onNewChannel = { push(Destination.NewChannel) },
            onNewCommunity = { push(Destination.NewCommunity) }
        )

        is Destination.Chat -> {
            val chatId = current.chat.id
            if (AppSettings.isChatLocked(chatId) && !AppSettings.isChatUnlockedNow(chatId)) {
                ChatLockScreen(
                    chatLabel = current.chat.peer?.displayName?.takeIf { it.isNotBlank() }
                        ?: current.chat.title?.takeIf { it.isNotBlank() }
                        ?: "This chat",
                    onUnlocked = { AppSettings.markChatUnlocked(chatId) },
                    onCancel = pop
                )
            } else {
                ChatScreen(
                    api = api,
                    chat = current.chat,
                    currentUserId = userId,
                    onBack = pop,
                    onOpenProfile = { push(Destination.PersonProfile(it)) },
                    onOpenGroupInfo = { push(Destination.GroupInfo(it)) },
                    onStartCall = { user, kind -> beginCall(user, kind) },
                    initialStatusReply = current.statusReply,
                    onOpenStatus = { push(Destination.Status(false, it)) }
                )
            }
        }

        is Destination.GroupInfo -> GroupInfoScreen(
            chat = current.chat,
            meId = userId,
            onBack = pop,
            onOpenUser = openDirectChat,
            onScheduled = { push(Destination.ScheduledMessages(current.chat.id, current.chat.title ?: "this chat")) },
            onLeft = { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
        )

        is Destination.PersonProfile -> UserProfileScreen(
            api = api,
            user = current.user,
            onBack = pop,
            onOpenChat = { user ->
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
                openDirectChat(user)
            },
            onStartCall = { user, kind -> beginCall(user, kind) },
            onOpenScheduled = { chatId, label -> push(Destination.ScheduledMessages(chatId, label)) }
        )

        is Destination.ScheduledMessages -> ScheduledMessagesScreen(
            chatId = current.chatId,
            chatLabel = current.chatLabel,
            onBack = pop
        )

        Destination.MyProfile -> MyProfileScreen(api = api, onBack = pop)

        Destination.NewChat -> NewChatScreen(
            api = api,
            onBack = pop,
            onOpenChat = { push(Destination.Chat(it)) }
        )

        Destination.NewGroup -> NewChatScreen(
            api = api,
            onBack = pop,
            onOpenChat = { push(Destination.Chat(it)) },
            initialMode = org.holidaylink.app.ui.NewChatMode.GROUP
        )

        Destination.NewChannel -> NewChatScreen(
            api = api,
            onBack = pop,
            onOpenChat = { push(Destination.Chat(it)) },
            initialMode = org.holidaylink.app.ui.NewChatMode.CHANNEL
        )

        Destination.NewCommunity -> NewChatScreen(
            api = api,
            onBack = pop,
            onOpenChat = { push(Destination.Chat(it)) },
            initialMode = org.holidaylink.app.ui.NewChatMode.COMMUNITY
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

        Destination.Starred -> StarredMessagesScreen(
            onBack = pop,
            onOpenChat = { chatId -> openConversationById(chatId) }
        )

        Destination.FeatureHub -> FeatureHubScreen(
            onBack = pop,
            onOpen = { feature ->
                push(nativeDestinationFor(feature.route))
            }
        )

        Destination.Store -> org.holidaylink.app.ui.StoreScreen(api = api, onBack = pop)

        Destination.Subscription -> SubscriptionScreen(onBack = pop)

        Destination.Feed -> FeedScreen(api = api, onBack = pop)

        Destination.Quizzes -> QuizzesScreen(api = api, onBack = pop)

        Destination.Papers -> PapersScreen(api = api, onBack = pop)

        Destination.Events -> EventsScreen(api = api, onBack = pop)

        Destination.Wallet -> WalletScreen(api = api, onBack = pop)

        Destination.Rankings -> RankingsScreen(api = api, onBack = pop)

        Destination.Invite -> InviteScreen(api = api, onBack = pop)

        is Destination.Status -> StatusScreen(
            api = api,
            me = me,
            startWithComposer = current.startWithComposer,
            initialUserId = current.userId,
            sharedUri = current.sharedUri,
            sharedIsVideo = current.sharedIsVideo,
            sharedText = current.sharedText,
            onBack = pop,
            onReply = { status ->
                val uid = FirebaseBackend.uid.orEmpty()
                if (uid.isNotBlank() && status.userId != uid) {
                    scope.launch {
                        val chatId = runCatching { FirestoreChat.startDirect(status.userId) }.getOrNull() ?: return@launch
                        push(
                            Destination.Chat(
                                chat = ChatItem(
                                    id = chatId,
                                    kind = "direct",
                                    title = status.userName,
                                    peer = User(id = status.userId, displayName = status.userName, username = ""),
                                    lastBody = null,
                                    lastAt = null
                                ),
                                statusReply = status
                            )
                        )
                    }
                }
            },
            // Mention taps resolve the selected chat UID into the real profile screen.
            onOpenProfile = { mentionedUid ->
                scope.launch {
                    val profile = runCatching { FirestoreChat.resolvePeer(mentionedUid) }.getOrNull()
                    if (profile != null) push(Destination.PersonProfile(profile))
                }
            }
        )

        is Destination.WebFeature -> WebFeatureScreen(
            url = current.url,
            onBack = pop
        )

        Destination.Settings -> SettingsFlow(
            api = api,
            prefs = prefs,
            me = me,
            onBack = pop,
            onAddAccount = onAddAccount,
            onSwitchAccount = onSwitchAccount,
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
            onOpenProfile = { push(Destination.AiProfile) },
            onOpenPage = { page, _ -> push(aiPageDestination(page)) },
            onBack = pop
        )

        Destination.AiProfile -> AiProfileScreen(
            api = api,
            onBack = pop,
            onStartChat = { push(Destination.Ai) }
        )

        is Destination.AiChat -> AiChatScreen(
            api = api,
            conversationId = current.conversationId,
            onOpenConversation = { id ->
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
                stack.add(Destination.AiChat(id))
            },
            onOpenProfile = { push(Destination.AiProfile) },
            onOpenPage = { page, _ -> push(aiPageDestination(page)) },
            onBack = pop
        )
    }
    }

    // The minimised call bar sits above every screen but only covers the top strip, so the
    // rest of the app stays usable while the call keeps its audio.
    if (ongoingCall != null && callMinimized) {
        MinimizedCallBar(
            user = ongoingCall.third,
            onOpen = { callMinimized = false },
            onEnd = {
                callManager.end()
                activeCall = null
                incomingCall = false
                callMinimized = false
            }
        )
    }
    }
}

/** Study Buddy open_page targets mapped onto native destinations. */
private fun aiPageDestination(page: String): Destination = when (page.lowercase()) {
    "quizzes", "papers", "study" -> Destination.Ai
    "events", "videos", "daily_feed", "feed", "dashboard" -> Destination.Status(false)
    "store", "wallet" -> Destination.Store
    "chat", "room" -> Destination.Tabs
    "invite" -> Destination.NewChat
    "profile" -> Destination.MyProfile
    "buddies", "leaderboard", "skills" -> Destination.Search
    else -> Destination.FeatureHub
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
    // System back inside a sub-screen returns to the hub instead of leaving Settings.
    androidx.activity.compose.BackHandler(enabled = open != null) { route = null }
    if (open == null) {
        SettingsHub(
            api = api,
            me = me,
            onOpen = { route = it },
            onLoggedOut = onLoggedOut,
            onBack = onBack
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
    onOpenMenu: () -> Unit,
    onOpenFeature: (HolidayFeature) -> Unit,
    onOpenProfileTab: () -> Unit,
    onOpenStatus: () -> Unit = {},
    /** Open ONE person's status (tapping their picture in the chat list). */
    onOpenUserStatus: (String) -> Unit = {},
    onPostStatus: () -> Unit = {},
    onNewGroup: () -> Unit = {},
    onNewChannel: () -> Unit = {},
    onNewCommunity: () -> Unit = {}
) {
    val notificationPermission = rememberNotificationPermissionState()
    val batteryOptimization = rememberBatteryOptimizationState()
    val glassBlue = Color(0xFF0B5CFF)
    val glassCyan = Color(0xFF00D9FF)
    val glassPurple = Color(0xFF7A2CFF)
    var fabOpen by remember { mutableStateOf(false) }

    Scaffold(
        // Transparent: the app-wide wallpaper is drawn once at the root and shows through.
        containerColor = Color.Transparent,
        bottomBar = {
            GlassBottomBar(tab = tab, onTab = onTab, onMenu = onOpenMenu, onSettings = onSettings)
        },
        floatingActionButton = {
            // Telegram-style: + opens New chat / New group / New channel / New community.
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (fabOpen) {
                    TelegramFabOption("New chat", Icons.Default.Chat) { fabOpen = false; onNewChat() }
                    TelegramFabOption("New group", Icons.Default.Groups) { fabOpen = false; onNewGroup() }
                    TelegramFabOption("New channel", Icons.Default.Campaign) { fabOpen = false; onNewChannel() }
                    TelegramFabOption("Community", Icons.Default.Groups) { fabOpen = false; onNewCommunity() }
                    TelegramFabOption("Post status", Icons.Default.AutoStories) { fabOpen = false; onPostStatus() }
                }
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(glassCyan.copy(alpha=.95f), glassBlue, glassPurple)))
                        .border(1.5.dp, glassCyan.copy(alpha=.8f), CircleShape)
                        .clickable { fabOpen = !fabOpen },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (fabOpen) Icons.Default.Close else Icons.Default.Add, "Create", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                // Only the top inset is applied: the lists run behind the floating nav bar
                // instead of stopping above it, which is what used to read as a hard edge.
                .padding(top = padding.calculateTopPadding())
        ) {
            NotificationPermissionBanner(notificationPermission)
            // Shown only once the OS-level permission is settled, so the user isn't asked
            // for two things in the same breath — this is the second, WhatsApp-style step
            // that keeps notifications arriving once the app is closed.
            if (notificationPermission.granted || !notificationPermission.supported) {
                BatteryOptimizationBanner(batteryOptimization)
            }

            // Chats keeps the glass header + status + search. Users/Channels stay clean.
            GlassHeader(
                me = me,
                onNewChat = onNewChat,
                onUsers = { onTab(Tab.Users) },
                onNotifications = onNotifications,
                onProfile = onOpenProfileTab
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Chats -> ChatsScreen(
                        api = api,
                        currentUserId = me?.id.orEmpty(),
                        onOpenChat = onOpenChat,
                        onOpenStatus = onOpenUserStatus,
                        // The status row and search bar live inside the list now, so they
                        // scroll away with the chats instead of collapsing on their own.
                        header = {
                            GlassStatusRow(
                                api = api,
                                me = me,
                                onOpenStatus = onOpenStatus,
                                onPostStatus = onPostStatus
                            )
                        },
                        pinnedHeader = { GlassSearchBar(onClick = onSearch) }
                    )
                    Tab.Users -> UsersScreen(
                        api = api,
                        onOpenChat = onOpenDirect,
                        onOpenProfile = onOpenProfile
                    )
                    Tab.Channels -> ChannelsScreen(
                        api = api,
                        onOpenChat = onOpenChat,
                        onNewChannel = onNewChannel,
                        onNewGroup = onNewGroup,
                        onNewCommunity = onNewCommunity
                    )
                    Tab.Settings -> SettingsHub(
                        api = api,
                        me = me,
                        onOpen = { route -> onSettings() },
                        onLoggedOut = {}
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
        Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF1565FF), Color(0xFF7A2CFF)))).border(1.dp, Color(0xFF3DE7FF), CircleShape).clickable { onProfile() }, contentAlignment = Alignment.Center) {
            if (me != null) Avatar(me.displayName, size = 32.dp, photo = me.avatarUrl)
            else Icon(Icons.Default.Person, "Profile", tint = Color.White)
        }
    }
}

@Composable
private fun GlassCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(scheme.primaryContainer)
            .border(1.dp, scheme.outlineVariant, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun TelegramFabOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(24.dp)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp)); Text(label, color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Last status strip seen this session; survives the tab being rebuilt. */
private object StatusStripCache {
    var people: List<Triple<String, String, String?>> = emptyList()
    var at: Long = 0L
    val profiles = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String?>>()
}

@Composable
private fun GlassStatusRow(api: Api, me: Me?, onOpenStatus: () -> Unit, onPostStatus: () -> Unit = onOpenStatus) {
    val scheme = MaterialTheme.colorScheme
    // Seeded from the last copy this session, so coming back to the chats tab (or back from a
    // chat) paints the strip at once instead of showing it empty while it is fetched again.
    var people by remember { mutableStateOf(StatusStripCache.people) }
    val ringContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (people.isNotEmpty() && System.currentTimeMillis() - StatusStripCache.at < 45_000L) {
            return@LaunchedEffect
        }
        // Painted from the phone's own cache first (instant on every reopen, even offline), then
        // refreshed from the server; people are looked up together, not one by one.
        suspend fun build(snap: com.google.firebase.firestore.QuerySnapshot) {
            val unknown = snap.documents.mapNotNull { it.getString("userId") }
                .filter { it.isNotBlank() && StatusStripCache.profiles[it] == null }.distinct()
            val resolved = if (unknown.isEmpty()) emptyMap() else {
                runCatching { FirestoreChat.resolvePeers(unknown) }.getOrDefault(emptyMap())
            }
            val seen = linkedMapOf<String, Triple<String, String, String?>>()
            snap.documents.forEach { d ->
                val userId = d.getString("userId").orEmpty()
                if (userId.isBlank() || seen.containsKey(userId)) return@forEach
                val known = StatusStripCache.profiles[userId]
                if (known != null) {
                    seen[userId] = Triple(userId, known.first, known.second)
                } else {
                    val p = resolved[userId]
                    val name = p?.displayName?.takeIf { it.isNotBlank() } ?: "HolidayLink user"
                    if (p != null) StatusStripCache.profiles[userId] = name to p.avatarUrl
                    seen[userId] = Triple(userId, name, p?.avatarUrl)
                }
            }
            // Green / grey rings around profile pictures (new vs already seen statuses).
            org.holidaylink.app.ui.StatusRings.update(
                ringContext,
                snap.documents
                    .groupBy { it.getString("userId").orEmpty() }
                    .filterKeys { it.isNotBlank() }
                    .mapValues { entry -> entry.value.map { doc -> doc.id } }
            )
            people = seen.values.take(12)
            StatusStripCache.people = people
            StatusStripCache.at = System.currentTimeMillis()
        }
        runCatching {
            val now = com.google.firebase.Timestamp.now()
            val query = FirebaseBackend.db.collection("statuses")
                .whereGreaterThan("expiresAt", now).orderBy("expiresAt").limit(60)
            if (people.isEmpty()) {
                runCatching { build(query.get(com.google.firebase.firestore.Source.CACHE).await()) }
            }
            build(query.get().await())
        }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPostStatus() }) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF7A2CFF)))).padding(3.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Your status", tint = scheme.primary, modifier = Modifier.size(22.dp)) }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (org.holidaylink.app.ui.StatusUploads.active.any { it.error == null }) "Uploading…" else "Your status",
                    color = scheme.onBackground, fontSize = 11.sp
                )
            }
        }
        itemsIndexed(people) { _, person ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenStatus() }) {
                // One arc per status (green = new, grey = seen), like WhatsApp.
                Box(
                    Modifier.size(54.dp).segmentedStatusRing(person.first).padding(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(person.second, photo = person.third, size = 44.dp)
                }
                Spacer(Modifier.height(4.dp)); Text(person.second.take(10), color = scheme.onBackground, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GlassSearchBar(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.85f))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp)); Text("Search chats, users, channels...", color = scheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun GlassBottomBar(tab: Tab, onTab: (Tab) -> Unit, onMenu: () -> Unit, onSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    // Telegram style: a floating, translucent pill over a soft scrim so the page behind it
    // stays visible. The pill carries exactly one hairline edge — an elevation shadow *and* a
    // border together read as a second outline on top of the first.
    Box(
        Modifier
            .fillMaxWidth()
            // A short, soft fade only: rows now scroll behind the pill, so the scrim just
            // keeps them readable. It used to cover the full bar height at up to 0.9 alpha,
            // which read as a solid band — the "boundary" above the nav.
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background.copy(alpha = 0f),
                        scheme.background.copy(alpha = 0.35f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(scheme.surface.copy(alpha = 0.9f))
                .border(0.6.dp, scheme.outlineVariant.copy(alpha = 0.45f), shape)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Triple(Tab.Chats, R.drawable.ic_tg_chats, tr("Chats")),
                Triple(Tab.Users, R.drawable.ic_tg_users, "Users"),
                Triple(Tab.Channels, R.drawable.ic_tg_channels, "Channels"),
                Triple(Tab.Settings, R.drawable.ic_tg_settings, tr("Settings")),
                Triple(Tab.Menu, R.drawable.ic_tg_menu, "Menu")
            )
            items.forEach { (entry, iconRes, title) ->
                val selected = tab == entry
                val tint = if (selected) scheme.primary else scheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .clickable { when (entry) { Tab.Menu -> onMenu(); Tab.Settings -> onSettings(); else -> onTab(entry) } }
                        .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.clip(CircleShape)
                            .background(if (selected) scheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(iconRes),
                            contentDescription = title,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(title, color = tint, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ChannelsScreen(
    api: Api,
    onOpenChat: (ChatItem) -> Unit,
    onNewChannel: () -> Unit,
    onNewGroup: () -> Unit,
    onNewCommunity: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var rows by remember { mutableStateOf<List<FirestoreChat.ChatRow>>(emptyList()) }
    var publicRows by remember { mutableStateOf<List<FirestoreGroups.GroupInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("All") }
    val uid = FirebaseBackend.uid.orEmpty()
    LaunchedEffect(reload) {
        loading = rows.isEmpty()
        try {
            val snap = FirebaseBackend.db.collection("chats").whereArrayContains("users", uid).get().awaitOrNull()
                ?: throw IllegalStateException("channels timeout")
            rows = snap
                .documents.mapNotNull { d ->
                    val users = (d.get("users") as? List<String>) ?: emptyList()
                    if (!users.contains(uid)) return@mapNotNull null
                    FirestoreChat.ChatRow(
                        id = d.id,
                        isGroup = d.getBoolean("isGroup") == true,
                        isBroadcast = d.getBoolean("isBroadcast") == true,
                        isCommunity = d.getBoolean("isCommunity") == true,
                        title = d.getString("groupName").orEmpty(),
                        peerId = null,
                        lastBody = d.getString("lastText"),
                        lastAt = d.safeTimestamp("lastAt"),
                        unread = 0
                    )
                }
            // Public groups anyone can find and join, even if they aren't a member yet.
            publicRows = runCatching { FirestoreGroups.publicGroups() }.getOrDefault(emptyList())
            error = ""
        } catch (e: Exception) { error = "Couldn't load. Check your internet." }
        loading = false
    }
    val groups = rows.filter { it.isGroup && !it.isBroadcast && !it.isCommunity }
    val publicGroups = publicRows.filter { it.privacy == "public" && !it.members.contains(uid) }
    val channels = rows.filter { it.isBroadcast }
    val communities = rows.filter { it.isCommunity }
    val shown = when (filter) {
        "Channels" -> channels
        "Groups" -> groups
        "Communities" -> communities
        else -> channels + communities + groups
    }
    val scope = rememberCoroutineScope()
    fun toItem(row: FirestoreChat.ChatRow): ChatItem {
        val kind = when {
            row.isCommunity -> "community"
            row.isBroadcast -> "channel"
            else -> "group"
        }
        return ChatItem(row.id, kind, row.title.ifBlank { "Group" }, null, row.lastBody, row.lastAt?.toDate()?.toInstant()?.toString(), 0, false, false, false)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Channels", color = scheme.onBackground, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Broadcast channels, groups and communities — WhatsApp/Telegram style", color = scheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Channels", "Groups", "Communities").forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNewChannel, modifier = Modifier.weight(1f)) { Text("New channel") }
            OutlinedButton(onClick = onNewGroup, modifier = Modifier.weight(1f)) { Text("New group") }
            OutlinedButton(onClick = onNewCommunity, modifier = Modifier.weight(1f)) { Text("Community") }
        }
        Spacer(Modifier.height(10.dp))
        val showPublic = (filter == "All" || filter == "Groups") && publicGroups.isNotEmpty()
        when {
            loading -> LoadingState(label = "Loading channels…")
            error.isNotBlank() && rows.isEmpty() && !showPublic -> ErrorState(error) { reload++ }
            shown.isEmpty() && !showPublic -> EmptyState(
                icon = Icons.Default.Campaign,
                title = "Nothing here yet",
                subtitle = "Create the first $filter to get started.",
                action = { Button(onClick = onNewChannel) { Text("Create") } }
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = floatingNavBottomPadding()) {
                itemsIndexed(shown) { _, row ->
                    val chat = toItem(row)
                    val badge = when {
                        row.isCommunity -> "COMMUNITY"
                        row.isBroadcast -> "CHANNEL"
                        else -> "GROUP"
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(scheme.surfaceContainerHigh)
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(18.dp))
                            .clickable { onOpenChat(chat) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF7A2CFF)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when (chat.kind) {
                                    "channel" -> Icons.Default.Campaign
                                    "community" -> Icons.Default.Groups
                                    else -> Icons.Default.Group
                                },
                                null, tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                chat.title?.takeIf { it.isNotBlank() }
                                    ?: chat.peer?.displayName?.takeIf { it.isNotBlank() }
                                    ?: badge,
                                fontWeight = FontWeight.Bold, color = scheme.onSurface
                            )
                            Text("$badge • ${chat.lastBody?.take(60) ?: "No messages yet"}", fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                if (showPublic) {
                    item("public-header") {
                        Text(
                            "Public groups",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    }
                    itemsIndexed(publicGroups) { _, g ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(scheme.surfaceContainerHigh)
                                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(18.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFF00D9FF), Color(0xFF7A2CFF)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(g.name, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                                Text("${g.members.size} members · public", fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                            }
                            Button(onClick = { scope.launch { runCatching { FirestoreGroups.joinGroup(g.id) }; reload++ } }) {
                                Text("Join")
                            }
                        }
                    }
                }
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
                text = { Text(tr("Settings")) },
                leadingIcon = { Icon(Icons.Default.Settings, null) },
                onClick = {
                    expanded = false
                    onSettings()
                }
            )
        }
    }
}

@Composable
private fun BannedScreen(message: String, onSwitchNumber: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Block, null, tint = scheme.error, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text("Account banned", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSwitchNumber) { Text("Use a different number") }
    }
}

// ---------------------------------------------------------------- auth

private fun friendlyMessage(e: Throwable, fallback: String): String =
    (e as? ApiException)?.message?.takeIf { it.isNotBlank() } ?: fallback

private fun finishFirebaseLogin(prefs: SharedPreferences, uid: String, phone: String, countryIso: String) {
    // Firebase Auth holds the session; prefs keep the uid/phone the rest of the app reads.
    prefs.edit().putString("userId", uid).putString("phone", phone).putString("countryIso", countryIso).apply()
}

private fun saveSession(prefs: SharedPreferences, token: String?, userId: String?) {
    val editor = prefs.edit()
    if (!token.isNullOrBlank()) editor.putString("token", token)
    if (!userId.isNullOrBlank()) editor.putString("userId", userId)
    editor.apply()
}

/**
 * Keeps the account that is signed in right now in the local account list so it can be
 * switched back to later. The long-lived token is minted only the first time (or after it
 * was revoked), so this is a no-op on every later launch.
 */
private suspend fun rememberCurrentAccount(context: Context, prefs: SharedPreferences) {
    val uid = FirebaseBackend.uid ?: return
    if (AccountStore.all(prefs).any { it.userId == uid && it.token.isNotBlank() }) return
    val token = runCatching { AccountSession.mintToken(context) }.getOrNull() ?: return
    val profile = runCatching { FirebaseBackend.loadProfile(uid) }.getOrNull()
    AccountStore.upsert(
        prefs,
        AccountStore.Account(
            userId = uid,
            token = token,
            phone = profile?.phone?.takeIf { it.isNotBlank() }
                ?: prefs.getString("phone", "").orEmpty(),
            displayName = profile?.name?.takeIf { it.isNotBlank() } ?: "HolidayLink user",
            username = profile?.username.orEmpty()
        )
    )
}

/**
 * The signed-in user as this phone remembers them, so a launch without internet still knows
 * who is signed in. The Firestore profile overwrites this as soon as it can be fetched.
 */
private fun cachedMe(prefs: SharedPreferences, uid: String): Me? {
    if (uid.isBlank()) return null
    ProfileCache.load(prefs, uid)?.let { return it }
    val account = AccountStore.all(prefs).firstOrNull { it.userId == uid } ?: return null
    return Me(
        id = uid,
        phone = account.phone.ifBlank { prefs.getString("phone", "").orEmpty() },
        displayName = account.displayName.ifBlank { "HolidayLink user" },
        username = account.username
    )
}

/** Best guess at the country of a switched-to number; keeps the stored one when unsure. */private fun countryIsoFor(phone: String, prefs: SharedPreferences): String {
    val hit = Countries.ALL.firstOrNull { phone.startsWith("+${it.dialCode}") }
    return hit?.iso ?: prefs.getString("countryIso", Countries.DEFAULT_ISO) ?: Countries.DEFAULT_ISO
}

private const val OTP_LENGTH = 6

/**
 * Sign-in: phone number, Infobip SMS code, Firebase custom-token login.
 * Profile lives in Firestore users/{uid} (same doc as the website).
 * 2FA is the website's device-approval flow (twoFactorRequests + sessions).
 */
@Composable
fun AuthFlow(api: Api, prefs: SharedPreferences, onCancel: (() -> Unit)? = null, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    var country by remember {
        mutableStateOf(Countries.byIso(prefs.getString("countryIso", Countries.DEFAULT_ISO)))
    }
    var national by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var signingUp by remember { mutableStateOf(false) }
    var verifiedPhone by remember { mutableStateOf("") }
    var cooldown by remember { mutableStateOf(0) }
    var waitingRequestId by remember { mutableStateOf<String?>(null) }
    var firebaseUid by remember { mutableStateOf("") }

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

    // 2FA approval listener: another device approves → finish login.
    val waitingId = waitingRequestId
    DisposableEffect(waitingId) {
        val reg = waitingId?.let { id ->
            FirebaseBackend.watchTwoFactorRequest(id) { approved ->
                if (approved) {
                    scope.launch {
                        runCatching { FirebaseBackend.touchSession(context, firebaseUid) }
                        finishFirebaseLogin(prefs, firebaseUid, verifiedPhone.ifBlank { international }, country.iso)
                        waitingRequestId = null
                        onLoggedIn()
                    }
                } else {
                    scope.launch {
                        FirebaseBackend.cancelTwoFactorRequest(id)
                        FirebaseBackend.signOut()
                        waitingRequestId = null
                        error = "Sign-in was denied on your other device."
                        step = 0
                    }
                }
            }
        }
        onDispose { reg?.remove() }
    }

    fun sendCode(resend: Boolean) {
        if (sending) return
        if (resend && cooldown > 0) return
        sending = true
        error = ""
        info = ""
        scope.launch {
            try {
                val result = api.otpRequest(international)
                verifiedPhone = result.phone
                prefs.edit()
                    .putString("phone", result.phone)
                    .putString("countryIso", country.iso)
                    .apply()
                cooldown = result.resendAfterSeconds
                info = "Verification code sent to ${PhoneNumbers.mask(result.phone)}. It works for 5 minutes."
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
        sendCode(resend = false)
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
                val result = api.otpVerify(verifiedPhone.ifBlank { international }, code)
                val user = FirebaseBackend.signInWithCustomToken(result.customToken)
                val uid = user.uid
                firebaseUid = uid
                val profile = FirebaseBackend.loadProfile(uid)
                if (profile == null) {
                    error = "We couldn't load your profile. Please try again."
                    FirebaseBackend.signOut()
                    return@launch
                }
                if (profile.banned) {
                    error = profile.banReason?.takeIf { it.isNotBlank() }?.let { "This account was banned: $it" }
                        ?: "This account was banned."
                    FirebaseBackend.signOut()
                    return@launch
                }
                if (profile.disabled) {
                    error = "This account was disabled. Contact support."
                    FirebaseBackend.signOut()
                    return@launch
                }
                val gate = FirebaseBackend.checkTwoFactor(context, uid)
                if (gate is FirebaseBackend.TwoFactorGate.Waiting) {
                    waitingRequestId = gate.requestId
                    pin = ""
                    step = 3
                    return@launch
                }
                if (profile.name.isBlank() || result.isNew) {
                    verifiedPhone = result.phone
                    display = profile.name
                    username = profile.username
                    pin = ""
                    step = 2
                } else {
                    finishFirebaseLogin(prefs, uid, result.phone, country.iso)
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
                val uid = firebaseUid.ifBlank { FirebaseBackend.uid } ?: throw ApiException("Sign-in expired. Verify again.")
                val cleanName = display.trim().take(120)
                val cleanUsername = username.trim().lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(32)
                if (cleanName.isBlank() || cleanUsername.length < 3) {
                    error = "Enter a display name and a username of 3+ characters."
                    return@launch
                }
                if (FirebaseBackend.isUsernameTaken(cleanUsername, uid)) {
                    error = "That @username is already taken."
                    return@launch
                }
                FirebaseBackend.saveProfile(uid, cleanName, cleanUsername, "")
                finishFirebaseLogin(prefs, uid, verifiedPhone.ifBlank { international }, country.iso)
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
        if (onCancel != null) {
            androidx.compose.material3.TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
                Spacer(Modifier.width(6.dp))
                Text("Back to HolidayLink")
            }
            Spacer(Modifier.height(4.dp))
        }
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
                        3 -> "Approve on your other device"
                        else -> "HolidayLink"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (step) {
                    0 -> {
                        Text(
                            "Enter your number. A verification code arrives by SMS and works for 5 minutes.",
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
                                Text(tr("Continue"))
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
                        Text("Check your other device", style=MaterialTheme.typography.titleLarge)
                        Text(
                            "This account uses 2FA approval. Open HolidayLink on a signed-in device and approve this sign-in.",
                            color=MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Waiting for approval…")
                        }
                        TextButton(onClick={
                            waitingRequestId?.let { id ->
                                scope.launch {
                                    FirebaseBackend.cancelTwoFactorRequest(id)
                                    FirebaseBackend.signOut()
                                }
                            }
                            waitingRequestId = null
                            step = 0
                        }){ Text("Cancel and use a different number") }
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