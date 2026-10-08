# HolidayLink App — messaging-first rebuild

This project turns HolidayLink into a phone-first messaging app:

- Android app in Kotlin + Jetpack Compose
- PostgreSQL backend (works with local PostgreSQL in Termux first, then Neon/any PostgreSQL later)
- Infobip SMS OTP (6 digits, 5 minute expiry, single use)
- Country-code selector in the app, Uganda (+256) by default
- Telegram-style unique `@username`
- Non-unique display names
- Empty Chats screen until a chat actually exists
- Registered Contacts matching (SHA-256 hashes only)
- All HolidayLink users directory
- 1-to-1 chats
- Group creation and group chats
- Notifications
- Store menu
- Floating glass navigation: Chats, Contacts, Videos, Stories, Profile and Menu
- Glassmorphism HolidayLink feature center for Quizzes, Papers, Events, Feed, Videos, AI Help, Find Buddies and other existing public features
- Public website features can open in an in-app WebView during the backend migration; admin pages are intentionally excluded
- Call signaling foundation (media/WebRTC is intentionally isolated so it can be added without changing chat/auth)
- No Firebase Auth, Firestore, or Firebase database

## Important security note

The Infobip API key belongs in the server `.env` file on your phone, never in the
Android app and never in git. If a key was shared in chat, revoke/rotate it and create
a fresh restricted key.

## Phone numbers and OTP

The app never asks the user to type `+256`:

- the country selector defaults to Uganda and can be changed at any time
- `0745473752` with `+256` selected is sent to the server as `+256745473752`
- a number that already contains `+256` (or `00256`, spaces, dashes, brackets) is not
  prefixed twice
- the server echoes back the exact normalised number, and the app reuses that same value
  for `/auth/verify-otp`, so the two requests can never disagree
- OTP input accepts spaces or dashes pasted from the SMS and is trimmed before sending

The server generates the 6-digit code, stores only a bcrypt hash in PostgreSQL, expires it
after `OTP_TTL_MINUTES`, consumes it on first successful use, supersedes older unused codes
for the same number, and rate limits both resends and wrong-code attempts.

## Backend address (development)

The API base URL is configuration, not a hardcoded constant in call sites:

- build time: `android/gradle.properties` → `HL_API_BASE_URL`,
  or override with `./gradlew assembleDebug -PHL_API_BASE_URL=http://192.168.1.20:8080`
- runtime: Menu → Settings → Server address (saved on the device, useful when the LAN IP changes)

Which address to use:

| Setup | Address |
| --- | --- |
| Node server in Termux on the same physical phone | `http://127.0.0.1:8080` |
| Android emulator, server on the host machine | `http://10.0.2.2:8080` |
| Real device on Wi-Fi, server on the same Wi-Fi | `http://<phone-lan-ip>:8080` |
| Production | `https://api.your-domain.com` |

`127.0.0.1` is correct when the server runs on the same device. Cleartext HTTP is only
permitted by the debug build (`src/debug/res/xml/network_security_config.xml`); release
builds are HTTPS-only apart from loopback.

## Project layout

- `android/` — native Android app
  - `config/AppConfig.kt` — API base URL resolution
  - `net/Api.kt` — all HTTP access, suspend functions on Dispatchers.IO, friendly errors
  - `phone/` — country list, phone normalisation, OTP sanitising, contact matching
  - `ui/` — notification permission state/banner, Settings screen
  - `MainActivity.kt` — screens (auth, chats, contacts, users, notifications, groups, chat)
- `server/` — Node.js API + WebSocket server
- `database/schema.sql` — PostgreSQL schema
- `docs/TERMUX_SETUP.md` — phone/Termux setup
- `scripts/` — helper scripts

## Permissions

- `READ_CONTACTS` is requested only from the Contacts tab, after a dialog explains that
  only one-way hashes are sent. Declining changes nothing else in the app.
- `POST_NOTIFICATIONS` (Android 13+) is never requested on first launch. The app explains
  the value of notifications after sign in and in Settings, and degrades quietly when denied.

## Production backend: Render + Neon

The production target is:

`Android app -> Render API -> Neon PostgreSQL`

Put the **Neon PostgreSQL connection string only in Render as `DATABASE_URL`**. Never put it in the Android app.

Put the **Render API URL in the Android build as `HL_API_BASE_URL`**. The included GitHub Actions workflow reads the GitHub repository variable `HL_API_BASE_URL` automatically.

See `docs/RENDER_NEON_SETUP.md` for the exact Render environment variables and deployment steps.

The Node server now runs the base schema and feature migration automatically at startup, which makes a fresh Neon database deployable without manually opening a SQL console.
