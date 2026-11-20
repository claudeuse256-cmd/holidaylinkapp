# HolidayLink 2.4.7

## What's new
- Telegram-style sticker packs: added Orangoutang, OwledDidi, SnappyCrab, and UtyaDuck packs with in-chat pack picker.
- App updates: checks for new releases on open, shows updates immediately, and requires updating after 24 hours from release.
- Push notification delivery flow verified and kept active (FCM token registration, server relay, and message/call alerts).

HolidayLink 2.4.4

• In-app updates now check the latest GitHub Actions build automatically.
• Settings → App updates downloads the new APK and opens the Android installer.
• Calls use WebRTC with STUN/TURN relay, so carrier-grade NAT no longer blocks audio or video.
• Chat wallpapers apply only inside chats, with brightness control.
• Typing indicators, online presence and read-receipt privacy are fixed.
• Calls can be minimized while you keep using the app.
• Background notifications work when the app is closed.
## In-app updates

GitHub Actions publishes each successful main-branch APK as the latest GitHub Release asset. The Android Settings > App updates screen checks the HolidayLink server feed and GitHub Releases, compares version codes, downloads the APK, and opens the Android package installer.

For seamless installation over an existing app, configure the same signing keystore in the GitHub Actions secret `HL_KEYSTORE_BASE64` that was used to sign the installed app. Without the matching key, Android may reject an update because of a signature mismatch. On Render, set `GITHUB_REPO=claudeuse256-cmd/holidaylinkapp` and `GITHUB_TOKEN` with permission to read the repository releases.
