# HolidayLink (Android + Firebase)

Phone-first messaging + study app. The Android app talks directly to the
**same Firebase/Firestore backend as https://holidaylink.org**, so app and
website stay in sync in real time. Admin pages stay on the website.

## Architecture

```
Android app ──► Firestore (chats, users, statuses, posters, quizzes,
                 papers, events, products, storeOrders, …)
            ──► imgbb (all images, same key as the website)
            ──► Render service (Yoola SMS codes + notification pushes)
```

* `android/` — Kotlin + Jetpack Compose app (`org.holidaylink.app`).
* `server/` — tiny OTP service only: sends Yoola SMS codes, stores hashes
  in Firestore `otpCodes/{phone}` (5-minute expiry), returns Firebase custom
  tokens, and relays FCM pushes (`POST /push/send`) so a closed app still rings.
  No chat, message, or user content lives here. No Postgres.
* `render.yaml` — deploys the OTP service (`npm install`, `npm start`).
* `FIRESTORE_RULES_ADDITION.md` — Firestore + Storage rules to merge into
  the website's deployed rules.
* `PUSH_NOTIFICATIONS.md` — how notifications work end-to-end and what to set up.

There is no SQL anywhere: `database/` was deleted with the Postgres removal.

## Auth

Phone number → Yoola SMS code (Render `/otp/request` + `/otp/verify`) →
Firebase custom-token sign-in → Firestore `users/{uid}` profile (same doc
as website signup). 2FA is the website's device-approval flow
(`twoFactorRequests` + `sessions`).

## Features (all Firestore-native)

Chats (realtime, read ticks, reply/edit/delete, view-once, self-destruct),
Users (ranked directory + phone contacts), Channels/Groups/Communities,
Status (24h, views, delete), voice notes (Firestore blobs), Calls (WebRTC +
Firestore signaling), Search, Notifications, AI chat + @ai/@bot mentions +
user bots (Study Buddy, same brain as the site), Feed, Quizzes (timed play,
rewards, paid entry), Papers/Guides, Events (join/paid booking/tickets),
Store (products, cart, receipt orders), Wallet, Rankings, Invite, QR codes.

## Build (GitHub Actions)

Push to `main` → **Actions → Build HolidayLink APK** → Artifacts.
Requires `android/app/google-services.json` (Firebase console,
`org.holidaylink.app` — committed; the apiKey is public by design, same as
the website config).

## Backend env (Render, OTP service)

`FIREBASE_SERVICE_ACCOUNT_JSON`, `YOOLA_API_KEY`, `YOOLA_SENDER`,
`DEFAULT_COUNTRY_CODE=256`, `OTP_COOLDOWN_SECONDS=45`.

## Recent source update (2.3.4)

- Status composer now supports text, photo, and video updates (video uploads use Firebase Storage and are limited to 30 MB); status expiry remains 24 hours.
- Added optional email-verified, client-side encrypted cloud backup for supported app settings. See `CLOUD_BACKUP_SETUP.md` before deploying; SMTP must be configured on Render.
- Profile action buttons now use compact stacked icon/label layout on narrow phones.
- Outgoing call setup resets its connection state before a new call and prefers the front camera for video calls.
- Existing FCM relay, WebRTC signaling, group management, status viewers, and local settings backup remain in the source.

This is a source ZIP. Build and test on a machine with Android SDK/Gradle before installing; real background delivery and video uploads also depend on deployed Firebase/Render configuration.
