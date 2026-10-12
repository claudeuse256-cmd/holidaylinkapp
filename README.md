# HolidayLink (Android + Firebase)

Phone-first messaging + study app. The Android app talks directly to the
**same Firebase/Firestore backend as https://holidaylink.org**, so app and
website stay in sync in real time. Admin pages stay on the website.

## Architecture

```
Android app ──► Firestore (chats, users, statuses, posters, quizzes,
                 papers, events, products, storeOrders, …)
            ──► imgbb (all images, same key as the website)
            ──► Render OTP service (Infobip SMS codes only)
```

* `android/` — Kotlin + Jetpack Compose app (`org.holidaylink.app`).
* `server/` — tiny OTP service only: sends Infobip SMS codes, stores hashes
  in Firestore `otpCodes/{phone}` (5-minute expiry), returns Firebase custom
  tokens. No chat, message, or user content lives here. No Postgres.
* `render.yaml` — deploys the OTP service (`npm install`, `npm start`).
* `FIRESTORE_RULES_ADDITION.md` — Firestore + Storage rules to merge into
  the website's deployed rules.

There is no SQL anywhere: `database/` was deleted with the Postgres removal.

## Auth

Phone number → Infobip SMS code (Render `/otp/request` + `/otp/verify`) →
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

`FIREBASE_SERVICE_ACCOUNT_JSON`, `INFOBIP_BASE_URL`, `INFOBIP_API_KEY`,
`INFOBIP_FROM`, `DEFAULT_COUNTRY_CODE=256`, `OTP_COOLDOWN_SECONDS=45`.
