package org.holidaylink.app.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.Me

/**
 * Every destination reachable from the Settings hub.
 *
 * Kept as an explicit enum rather than free-form route strings so a typo becomes a compile
 * error instead of a blank screen at runtime.
 */
enum class SettingsRoute {
    Profile,
    Accounts,
    Privacy,
    Security,
    Blocked,
    Notifications,
    Appearance,
    DataStorage,
    Language,
    ChatSettings,
    AiSettings,
    Server,
    Update,
    About,
    Help,
    Terms,
    PrivacyPolicy
}

/**
 * Renders the selected settings sub-screen.
 *
 * Each sub-screen owns its own top bar and back action, so [SettingsRoute] alone is enough state
 * to restore the whole settings tree after process death or a configuration change.
 */
@Composable
fun SettingsHost(
    route: SettingsRoute,
    api: Api,
    prefs: SharedPreferences,
    me: Me?,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (org.holidaylink.app.data.AccountStore.Account) -> Unit,
    onOpenRoute: (SettingsRoute) -> Unit = { onBack() }
) {
    when (route) {
        SettingsRoute.Accounts ->
            AccountsScreen(
                prefs = prefs,
                me = me,
                onBack = onBack,
                onAddAccount = onAddAccount,
                onSwitchAccount = onSwitchAccount
            )

        SettingsRoute.Profile ->
            MyProfileScreen(api = api, onBack = onBack)

        SettingsRoute.Privacy ->
            PrivacyScreen(api = api, onBack = onBack)

        SettingsRoute.Security ->
            SecurityScreen(
                api = api,
                onBack = onBack,
                onOpenBlocked = { onOpenRoute(SettingsRoute.Blocked) }
            )

        SettingsRoute.Blocked ->
            BlockedScreen(api = api, onBack = onBack)

        SettingsRoute.Notifications ->
            NotificationsSettingsScreen(api = api, onBack = onBack)

        SettingsRoute.Appearance ->
            AppearanceScreen(onBack = onBack)

        SettingsRoute.DataStorage ->
            DataStorageScreen(onBack = onBack)

        SettingsRoute.Language ->
            LanguageScreen(onBack = onBack)

        SettingsRoute.ChatSettings ->
            ChatSettingsScreen(api = api, onBack = onBack)

        SettingsRoute.AiSettings ->
            AiSettingsScreen(api = api, onBack = onBack)

        SettingsRoute.Server ->
            ServerSettingsScreen(api = api, onBack = onBack)

        SettingsRoute.Update ->
            UpdateScreen(onBack = onBack)

        SettingsRoute.About ->
            AboutScreen(onBack = onBack)

        SettingsRoute.Help ->
            SimpleInfoScreen(
                title = "Help",
                body = HELP_TEXT,
                onBack = onBack
            )

        SettingsRoute.Terms ->
            SimpleInfoScreen(
                title = "Terms of service",
                body = TERMS_TEXT,
                onBack = onBack
            )

        SettingsRoute.PrivacyPolicy ->
            SimpleInfoScreen(
                title = "Privacy policy",
                body = PRIVACY_TEXT,
                onBack = onBack
            )
    }
}

private val HELP_TEXT = """
    Getting started

    1. Sign in with your phone number. HolidayLink checks the account first, then uses 2FA password login when enabled or SMS verification when needed.
    2. Chats is your home. Open Contacts to start conversations with people you already know.
    3. Only one-way hashes of your contacts' numbers are sent for matching. Names, photos and
       numbers stay on this phone.

    If something looks wrong

    • Messages not arriving: check your internet connection and reopen the chat.
    • Not seeing contacts on HolidayLink: the list only shows accounts whose numbers matched.
      Everyone else is listed under "Invite to HolidayLink".
    • Missing notifications: Android blocks them until you allow them. Settings > Notifications
      explains how to turn them back on.

    Still stuck? Send the exact error text and your Android version when you report it.
""".trimIndent()

private val TERMS_TEXT = """
    By using HolidayLink you agree to use it lawfully and to respect other people.

    • Do not harass, threaten, impersonate or stalk anyone.
    • Do not send spam, scams, malware or illegal content.
    • You keep ownership of the content you post. By posting it you allow HolidayLink to store
      and deliver it to the people you send it to.
    • We may remove accounts or content that breaks these rules.

    HolidayLink is provided as-is, without warranty. Text messages use end-to-end encryption, but
    media attachments are not yet E2EE. Service availability is not guaranteed.
""".trimIndent()

private val PRIVACY_TEXT = """
    What HolidayLink stores

    Your phone number, display name, username, optional email, profile photo and About text,
    plus the messages and media you send, the chats you are in, and the settings you choose.

    What HolidayLink does not do

    • It never sells your data.
    • It never sends your contacts' names, photos or numbers to the server.
    • Gemini credentials are never placed on your device. HolidayLink AI runs on HolidayLink's
      own servers and only the messages you type into the assistant are sent there.

    Your choices

    Privacy controls (last seen, photo, About, status, calls, group invites) are enforced by the
    server, so "Nobody" genuinely means nobody. You can also block people, who then cannot
    message you or see your profile details, and delete your account at any time from Settings.

    Contact matching

    Only a SHA-256 hash of each contact number is uploaded. Names, photos and raw numbers never
    leave the device, and the server cannot reverse a hash back into a phone number.

    Encryption

    Text messages use end-to-end encryption. The server stores encrypted envelopes and removes
    delivered message rows. Attachments are not yet end-to-end encrypted.
""".trimIndent()