# HolidayLink – fix pass, 22 Nov 2026

NOTE: none of this was compiled (no Android SDK where it was written). Build it, then fix any
compiler message — most likely small import/type slips.

## Fixed
- **Calls crashed on tap.** `CallService` is a `phoneCall|microphone|camera` foreground service but
  the manifest never declared `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`,
  `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_CAMERA`, `MANAGE_OWN_CALLS` (required for
  targetSdk 34+/35) → `SecurityException` in `startForeground()`. Declared them, made the service
  start fail-safe (`CallService.start/stop`), wrapped call-signalling coroutines
  (`CallManager`) and video-sink calls (`CallScreen`) so they can never kill the app.
- **Play Protect "Harmful app blocked"** (it fired on the in-app update install). Removed the
  download-and-install-APK flow and `REQUEST_INSTALL_PACKAGES` (updates now open the release in the
  browser), removed unused `USE_FULL_SCREEN_INTENT` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
  CI now builds a **release** (non-debuggable) APK instead of the debug one.
  **Do this once:** create a permanent key and store it as the `HL_KEYSTORE_BASE64` GitHub secret:
  `keytool -genkeypair -v -keystore holidaylink.jks -alias holidaylink -keyalg RSA -keysize 2048 -validity 10000 -storepass holidaylink -keypass holidaylink`
  then `base64 -w0 holidaylink.jks` → paste as the secret. Without it every CI build gets a new
  throw-away signature, which Play Protect distrusts and which cannot update the previous install.
- **Messages sent offline vanished.** `FirestoreChat.send` used a Firestore *transaction* (needs the
  server). Now a `WriteBatch`: queued on disk, sent when internet returns, shown at once with a
  clock (`ChatMessage.pending`) that becomes a tick when confirmed. Fixes voice notes/stickers
  not appearing instantly too.
- **Pictures/videos/files** now live in an app-wide `OutboxStore`: survive leaving the chat and closing
  the app, resume automatically when the network returns.
- **One emoji button** + three-section picker (`EmojiStickerPicker.kt`): ~200 emoji, built-in
  sticker packs, "My stickers" (bundled starter set from `res/raw/hl_sticker_*` + user-added pictures).
- **Send / receive sounds** (`ChatSounds`, generated tones; respect silent mode and the Settings switch).
- **Keyboard** no longer hides the newest messages (list scrolls to the end when it opens).
- **Pictures can be deleted**: a plain `clickable` swallowed the long-press, so the message menu never opened.
- **Image looked big/"loading" after sending**: outgoing preview uses the final bubble's size and the
  uploaded file is seeded into `MediaCache`.
- **Chats header**: status strip scrolls away, search bar stays pinned (`stickyHeader`).
- **"Reloads everywhere"**: chat-list peer cache, status-strip, status authors, avatar first frame and scroll
  position are now kept in memory for the session.
- **Profile**: opens instantly with what the list already knows, profile-shaped skeleton otherwise.
- **Status uploads**: card with thumbnail + progress shows immediately (`StatusUploads`), keeps running if
  you leave, shows the real error with Retry. Video upload timeouts raised (OkHttp default was 10 s).
- **Groups**: sender name (coloured) + tag (Admin or a custom tag set by admins in Group info → member → Set tag).
- **Languages**: Kiswahili, Luganda, Français selectable; `Tr.kt` translates tabs, buttons and common
  actions (other text stays English until added to the tables — native-speaker review needed).

## Still to do / known limits
- Most screens are still hard-coded English; extend `Tr.kt` or move text to `strings.xml`.
- Push for a message sent offline is only triggered once this phone gets the server's confirmation.
- Video status/chat video still needs a working upload route: Cloudinary on the server, or Firebase
  Storage enabled with rules for `statuses/` and `chat_media/`.
- TURN credentials are in `gradle.properties` in the repo — rotate them.
