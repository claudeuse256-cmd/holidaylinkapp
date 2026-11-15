# HolidayLink Android feature upgrade — 12 November 2026

This source ZIP is based on the uploaded `holidaylinkapp-status-upgrade.zip`.

## Changes made in this package

- **Biometric-only app lock:** the app lock now asks for fingerprint/face authentication only. The old in-app PIN fallback and PIN setup controls were removed from the Security screen. Existing individual chat locks also use biometric authentication only. Chat-lock activation is disabled until biometric app-lock setup has been completed. There is intentionally no PIN/device-credential fallback; losing biometric access can lock the user out until biometrics are restored.
- **50 local animated stickers:** added 50 distinct emoji-based animated sticker designs. Sticker IDs are stored in Firestore instead of third-party Lottie URLs, so sending and receiving these built-in stickers does not depend on external animation hosts. Bundled Lottie JSON assets are also included under `android/app/src/main/res/raw/`.
- **Sticker sending:** the picker sends a stable `hlsticker://<id>` reference; receivers resolve the same ID locally. The chat preview now says “Sticker”, and failed sends show a retry-oriented error instead of silently disappearing. The composer has one sticker action.
- **Photo editor:** added center crop/zoom, finger drawing and text overlays. Photo edits are flattened into a new JPEG cache file before upload. Existing rotation and caption support remain.
- **Video editor:** existing video preview and trim-filmstrip workflow remains. Video trim is copied without re-encoding where the source container supports it. This package does not add text/drawing overlays or full transcoding effects to videos.
- **Profile-picture positioning:** added square crop, zoom, horizontal and vertical positioning before uploading the profile picture. The crop is baked into the uploaded image so other users see the same framing.
- **Custom wallpaper cropping:** added zoom plus horizontal/vertical positioning controls for a custom chat wallpaper. Built-in preset wallpapers are unchanged.
- **Server-backed scheduled text messages:** added authenticated Render endpoints `/schedule/create` and `/schedule/cancel`, plus a server-side Firestore dispatcher that checks due messages every 10 seconds and writes them into the existing chat message collection. The Android app creates/cancels queue records through the configured Render API. Local WorkManager/UI polling no longer sends a second copy.
- **Chat scrolling:** added item-placement animation to the chat message list; the existing jump-to-latest animated scroll remains.
- **FCM:** retained the existing Firebase messaging service, token registration, Render push relay and high-priority notification/data payload. This ZIP does not prove real-device background delivery; it must be tested with two signed-in devices and the deployed Firebase/Render configuration.
- **Status:** keeps the uploaded ZIP's status composer preview, mention and reply changes.
- **Version:** Android `2.4.6`, versionCode `19`.

## Required deployment and testing

1. Build with the same Firebase project used by `android/app/google-services.json` and the configured `HL_API_BASE_URL`.
2. Redeploy `server/` to Render. Keep `FIREBASE_SERVICE_ACCOUNT_JSON` configured; the scheduled-message and FCM server routes use Firebase Admin.
3. The Render web service must be running for scheduled delivery. If the Render instance sleeps (for example, on a free/sleeping plan), delivery can be delayed until the service wakes. For close-to-exact delivery, use an always-on instance or an external scheduled/cron worker.
4. Scheduled text currently uses the same Firestore message schema as the existing chat send path. It is **not an end-to-end-encrypted scheduling implementation**. Do not schedule sensitive content under an E2EE guarantee until client-side message encryption and recipient-key handling are implemented end to end.
5. Test scheduled sends with a future time while both Android apps are closed, then confirm the message appears once and recipients receive FCM.
6. Test FCM on two real devices/accounts with notification permission granted, Google Play services available, and battery restrictions reviewed. Android force-stop prevents normal FCM delivery until the app is launched again.
7. Test biometric app lock and chat locks on the actual phone. Because the lock is biometric-only, ensure fingerprint/face authentication is enrolled and working before enabling it.
8. Test photo crop/drawing/text, profile-photo crop positioning, wallpaper crop positioning, sticker send/receive and video trim.

## Validation performed here

- `node --check server/src/server.js` passes.
- All 50 bundled sticker JSON files parse as valid JSON.
- The Android Gradle wrapper is present, but `assembleDebug` could not run in this environment: the wrapper needed to download Gradle 8.9 and network DNS access to `services.gradle.org` was unavailable. No Android SDK/Compose dependency set is available here for a real APK compile or device test.
- A Kotlin parser-only pass reported no Kotlin syntax/`expecting` errors, but it is not a substitute for Gradle compilation; unresolved Android/Compose/Firebase symbols are expected without the Android dependency classpath.
- ZIP integrity is checked after packaging.
