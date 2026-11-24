# Build and deploy HolidayLink 2.3.4

## Build an APK

The Android project is in `android/`. This source archive does not include a Gradle wrapper, so use Android Studio's Gradle support or install a compatible Gradle 8.x distribution and Java 17.

From the project root, with Android SDK installed and `HL_API_BASE_URL` set to your Render service:

```sh
cd android
gradle assembleDebug --no-daemon -PHL_API_BASE_URL="https://holidaylink-server.onrender.com"
```

The APK is normally created at `android/app/build/outputs/apk/debug/app-debug.apk`. The debug APK is for testing, not a Play Store release. Keep the same signing key for future release updates.

## Render

Push the updated `server/`, `render.yaml`, and `server/package.json` to the existing repository so Render can redeploy. Add the SMTP secrets listed in `CLOUD_BACKUP_SETUP.md` in the Render dashboard. Keep `FIREBASE_SERVICE_ACCOUNT_JSON` private.

## Firebase

Merge the status-video Storage rule from `FIRESTORE_RULES_ADDITION.md` into the current Storage rules. Do not replace existing rules wholesale. Check the existing Firestore rules also protect `backupEmailCodes` and `userBackups` from client reads/writes.

## First tests

- Test registration/login with a second account and verify each device has an FCM token in its user document.
- Send a message while the other app is backgrounded; then repeat with the app closed (not force-stopped).
- Place and answer audio and video calls on two devices, including over mobile data.
- Post text, photo and video status updates; confirm they appear in the viewer and expire from the feed after 24 hours.
- Verify recovery email, save encrypted cloud settings, change a preference and restore it with the same passphrase. Confirm a wrong passphrase fails without restoring.
