# HolidayLink V3 upgrade work — implementation and setup notes

This package contains source-level improvements and an implementation audit. It is not represented as a fully verified production release; a full Android build and real-device tests are required before deployment.

## Source fixes included

- Chat rows sort pinned conversations first and then by last-message timestamp (including timestamp nanoseconds for ties).
- Direct conversations no longer use the generic `Chat` label while peer data is loading; they use `Loading contact…` or a neutral `Contact` fallback.
- The push relay client now logs HTTP failures and connection errors in Android logs instead of silently discarding every failure. Search Logcat for `HolidayLinkPush` during testing.
- Existing notification service, token registration, Render push endpoint and Firebase Admin integration have been retained.

## FCM/Render checklist

1. In Firebase Console, verify Cloud Messaging is enabled for the same Firebase project used by the Android `google-services.json`.
2. In Render, set `FIREBASE_SERVICE_ACCOUNT_JSON` to the complete service-account JSON as one valid JSON value. Its `project_id` must match the Android Firebase project. Keep it private and never commit it.
3. Deploy the backend and open `/health`. It should report `firebaseConfigured: true` and `pushConfigured: true`.
4. Check Firestore: each signed-in user's `users/{uid}` document should contain `fcmTokens` with that device's current FCM registration token. Existing rules must allow the signed-in user to update their own token fields. Do not broadly allow writes to other users' documents.
5. On Android 13+, grant HolidayLink notification permission. Ensure the Messages and Calls channels are not muted in Android app notification settings.
6. Test on two real devices/accounts: sign in, open HolidayLink once on each, then background the recipient app (do not force-stop it). Send a message and check notification plus Render logs. Repeat in reverse. Also test a call separately.
7. If Render reports `sent: 0`, inspect whether the recipient's user document has tokens and whether the recipient is marked online with a recent `lastSeen`. The server currently suppresses some message pushes for users considered active to reduce duplicates.
8. Android battery optimisation, OEM background limits, missing network, revoked notification permission and a force-stopped app can prevent immediate display; no implementation can guarantee delivery in all such conditions.

### Multi-account note

The current client stores the FCM token under the currently authenticated Firebase user's document. The app has a local account switcher, but correct push delivery to a second saved account while another account is active needs an authenticated device-to-account token registration design. Do not solve this by letting one client write arbitrary users' token arrays; that would be a security flaw. This needs end-to-end testing and likely a small backend endpoint that validates the saved account session token before registering the device token for that account.

## SMTP/email recovery

Cloudflare Email Routing alone is not an SMTP mailbox/sending provider. Choose an email-sending provider first and obtain its real host and credentials. Do not set made-up values in Render. After a provider is chosen, the backend can use variables such as:

```env
SMTP_HOST=provider-smtp-host
SMTP_PORT=587
SMTP_USER=support@holidaylink.org
SMTP_PASS=provider-issued-smtp-credential
SMTP_FROM=support@holidaylink.org
SMTP_SECURE=false
```

These are proposed variable names and placeholders, not implemented/active variables in the current backend. Never put SMTP credentials in the APK or Git.

## Optional encrypted cloud backup and restore

Backup/recovery remains an opt-in feature and is not yet implemented end-to-end in this package. Before coding it into production, establish which Firebase services are enabled, choose persistent object storage, define backup schema/versioning and retention, encrypt the backup on-device, and design recovery for E2EE keys. Email verification must not by itself decrypt a backup. Do not use Render's temporary local filesystem for backup files.

## Build/test status

No claim of a successful Android APK build or two-device FCM test is made by this archive. Run the Gradle build from the Android project on a configured Android build machine and address any build errors before installing. Test with two real accounts and record the results before release.
