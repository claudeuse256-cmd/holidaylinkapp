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
- **Play Protect "Harmful app blocked"** (it fired while the in-app update was installing). The in-app
  download/install is KEPT (like Telegram). What most likely trips Play Protect is the build, not the
  updater: CI shipped the *debug* APK (debuggable, trusting user certificates) signed with a
  throw-away key that is different on every run. Fixes: CI now builds a **release** (non-debuggable)
  APK; removed unused `USE_FULL_SCREEN_INTENT` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
  **Do this once:** create a permanent key and store it as the `HL_KEYSTORE_BASE64` GitHub secret:
  `keytool -genkeypair -v -keystore holidaylink.jks -alias holidaylink -keyalg RSA -keysize 2048 -validity 10000 -storepass holidaylink -keypass holidaylink`
  then `base64 -w0 holidaylink.jks` → paste as the secret. Use that same key for every build forever:
  a stable signature is what builds trust (and lets updates install over the old app). A brand-new
  app can still get a warning until it has some reputation; there is no code-only guarantee.
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

---
# Second pass (23 Nov 2026) — also NOT compiled

- **Update pop-up**: Download/Install button now at the TOP, release notes below (markdown cleaned, scrollable).
  Closable for 24 h, mandatory after. New `UpdateCheckWorker` posts an "Update available" notification
  (once per version, even when the app is closed).
- **Links** in messages are tappable (`Linkify.kt`).
- **Notifications** (`Notifier`, `NotificationActionReceiver`): sender's round profile picture (or initial),
  **Reply** box and **Mark as read** buttons. The message is marked *delivered* (double grey tick) the moment
  the push arrives (`markDeliveredNow`) or the chat list receives it.
  Server (`server.js`) now sends DATA-ONLY pushes (needed so the app can draw all that). If some phones then
  receive nothing while closed, set `PUSH_DATA_ONLY=false` on Render to go back to system-drawn notifications.
- **Video calls**: your own picture no longer disappears when the other person joins (`setZOrderMediaOverlay`).
  **Screen sharing** button in video calls (MediaProjection + foreground-service type `mediaProjection`).
- **Home feed**: YouTube links play INSIDE the app (embedded player pop-up); direct video files use a native
  player. YouTube needs the app's Referer header — if a video shows an error 153, tell me.
- **Replies** to voice/photo/video/file/sticker now say "Voice message", "Photo"… (also for old replies);
  tapping a reply quote scrolls to and **highlights** the original message for ~2 s.
- **Online dot** is a full circle (it was clipped to half); no grey dot when offline.
- **Status rings** (green = new, grey = all seen) around avatars in the chat list, chat header, profile,
  status screen and the status strip (`StatusRings`).
- **Privacy**: the audiences (last seen, photo, About) were saved but never applied to what others see; now
  applied when a profile is read (`PrivacyGate`). New **phone number** setting: Everyone / My contacts /
  Nobody / Selected people; the number is shown on a profile only when allowed.
  NOTE: enforced by the app; a modified client could still read `users/{uid}.phone`. Full protection needs a
  Firestore-rules change (move the number to a private document).
- **Delete for everyone**: if it was the newest message, the chat list now shows "This message was deleted".
- **Stickers** tab first, then Emoji, then My stickers; the fake starter stickers are gone from My stickers.
- **Scheduled messages**: phone-side timers (notice 1 minute before + safety-net send at the chosen time),
  fixed message id `sched_<id>` so phone and server can never send it twice, wrong-day bug in the date picker
  fixed, a past time is no longer silently moved to "tomorrow 9:00". The Render free plan sleeps when idle:
  also set up a free pinger (UptimeRobot / cron-job.org) on `https://<your-service>/health` every 5 min.
