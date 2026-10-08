# Termux setup

## 1. Install tools

```bash
pkg update
pkg install git nodejs postgresql openjdk-17
```

If your Termux repository provides a different JDK package, use the available OpenJDK package.

## 2. Start PostgreSQL

```bash
initdb $PREFIX/var/lib/postgresql
pg_ctl -D $PREFIX/var/lib/postgresql start
createdb holidaylink
psql holidaylink
```

Inside `psql`:

```sql
\i /path/to/holidaylink-app-v1/database/schema.sql
```

Then exit with `\q`.

## 3. Start the server

```bash
cd server
cp .env.example .env
nano .env
npm install
npm run check     # syntax check, does not touch the database
npm run dev
```

Minimum `.env` values for the first phone test:

```env
PORT=8080
DATABASE_URL=postgres://YOUR_DB_USER:YOUR_DB_PASSWORD@127.0.0.1:5432/holidaylink
JWT_SECRET=<long random string>
DEFAULT_COUNTRY_CODE=256
OTP_TTL_MINUTES=5
OTP_COOLDOWN_SECONDS=45
INFOBIP_BASE_URL=https://YOUR-INFOBIP-SUBDOMAIN.api.infobip.com
INFOBIP_API_KEY=PASTE_YOUR_NEW_SMS_API_KEY_HERE
INFOBIP_FROM=ServiceSMS
```

Do NOT put the Infobip key in the Android app and do not commit `.env` (it is gitignored).

Check the server is healthy:

```bash
curl http://127.0.0.1:8080/health
```

`"database":"ok"` means PostgreSQL is reachable. `"database":"unavailable"` with HTTP 503
means the database connection failed — check `DATABASE_URL` and that `pg_ctl` is running.

## 4. Android test: which address to use

The Node server binds to `0.0.0.0`, so pick the address that matches your setup:

| Setup | API base URL |
| --- | --- |
| Server runs in Termux on the **same physical phone** as the app | `http://127.0.0.1:8080` |
| App in the Android emulator, server on the host machine | `http://10.0.2.2:8080` |
| App on a real device, server reachable over the same Wi-Fi | `http://<phone-lan-ip>:8080` |

`127.0.0.1` inside the Android app means the phone itself. Because the Termux server runs on
that same phone, loopback reaches it directly. If your setup differs, use `10.0.2.2`
(emulator host alias) or the phone's Wi-Fi IP.

Set the address in either place:

1. `android/gradle.properties` → `HL_API_BASE_URL=...` (then rebuild), or
   `./gradlew assembleDebug -PHL_API_BASE_URL=http://192.168.1.20:8080`
2. In the app: Menu → Settings → Server address → Save, then **Test connection**

## 5. Build

```bash
cd android
gradle assembleDebug        # or open android/ in Android Studio
```

Requirements: Gradle 8.9, JDK 17, Android SDK 35. Java and Kotlin both target 17 through
`jvmToolchain(17)`.

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`

## 6. First login

1. Leave the country selector on Uganda (+256) or pick your country.
2. Type the national number, e.g. `0745473752` — the app sends `+256745473752`.
3. Tap Continue. The button shows "Sending code..." and only advances when the server
   confirms the SMS was sent.
4. Enter the 6-digit code from the SMS. Spaces or dashes are ignored.
5. Wrong or expired code → "That code is incorrect or has expired."
6. Resend is available after the countdown; "Change number" returns to step 1.
7. Existing phone → sign in. New phone → choose display name and unique `@username`.
8. App opens directly to Chats.

## 7. Infobip

Infobip's normal SMS API (`/sms/3/messages`) is used; no 2FA application or message ID is
required. The API key needs the `sms:message:send` scope, and on a trial the destination
generally has to be a verified number. The API key lives only on the server.

## 8. Troubleshooting

| Symptom | Cause |
| --- | --- |
| "Can't connect right now..." | Wrong base URL, or the server is not running. Check Settings → Test connection. |
| "We couldn't send the code." | Infobip rejected the send. Check `INFOBIP_BASE_URL`/`INFOBIP_API_KEY` in `.env` and the server log. |
| "A code was just sent..." | Resend cooldown (`OTP_COOLDOWN_SECONDS`, default 45s). |
| "Too many incorrect attempts." | 5 wrong codes in 15 minutes. Request a new code. |
| "That code has expired." | Older than `OTP_TTL_MINUTES` (default 5) or already used once. |
