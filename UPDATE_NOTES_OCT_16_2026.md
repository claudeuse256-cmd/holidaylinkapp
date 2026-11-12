# HolidayLink source upgrade — 16 October 2026

## Implemented in this source package

- **Profile photos:** avatar photos use circular clipping and crop-to-fill. Profile action buttons use compact stacked icon/label layout on narrow screens.
- **Chat list:** pinned conversations appear first; newest activity sorts first within pinned/unpinned sections.
- **Local settings backup:** Settings → Data and storage can export and restore supported app preferences as a versioned JSON file. It excludes app-lock PIN and app-lock/biometric enablement flags.
- **Encrypted cloud settings backup:** Settings can verify a recovery email using a code delivered by Render SMTP, encrypt the settings backup on-device with AES-256-GCM and a PBKDF2-derived key, and save/restore ciphertext from Firestore via the Render server. The passphrase is never sent to the server.
- **Status media:** status composer supports text, photos and videos. Video uploads use Firebase Storage, enforce a 30 MB app/rules limit, play in the full-screen status viewer, and follow playback progress. Status documents expire from the feed after 24 hours.
- **WebRTC calls:** existing in-app audio/video calling and Firestore signaling remain; outgoing call state is reset before a new call, front camera is preferred, and camera/microphone resources are released on hang-up.
- **Background notifications:** existing FCM token registration, Render `/push/send` relay, and Android messaging service remain included.
- **Groups/channels:** existing group profile, member/admin tools, privacy and group permissions remain included.
- **Version:** Android `2.3.4` / versionCode `9`.

## Required setup before testing

1. Configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_SECURE`, `SMTP_USER`, `SMTP_PASS`, and `SMTP_FROM` on the Render service, then redeploy. Details: `CLOUD_BACKUP_SETUP.md`.
2. Merge the status video Firebase Storage rule in `FIRESTORE_RULES_ADDITION.md` into the existing deployed rules; do not replace your whole ruleset.
3. Ensure `FIREBASE_SERVICE_ACCOUNT_JSON` and the correct Render base URL are configured.
4. Test on two real devices/accounts for push delivery and calls. Android force-stop, notification permission, battery restrictions, mobile-network NAT, and FCM/TURN provider availability can still affect real-time delivery.

## Validation and limitations

- `node --check server/src/server.js` passes.
- A Kotlin parser-only check reported no syntax/parse errors in the changed Kotlin files, but could not compile them because Android/Compose/Firebase/WebRTC dependencies are not available in this environment.
- No Gradle wrapper is included and Gradle is not installed here, so an Android APK build and on-device integration test could not be performed.
- The encrypted cloud backup is settings-only. It does not back up chats, media, account credentials, or E2EE keys; it is not a full WhatsApp chat-history migration.
- Status privacy must be enforced by the deployed Firestore rules/site backend; review the deployed rules before publishing, because client-side UI alone is not an access-control boundary.
