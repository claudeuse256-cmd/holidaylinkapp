# HolidayLink: Render + Neon deployment

## 1. Neon PostgreSQL

Create a Neon PostgreSQL project and copy its **pooled connection string**.
It normally looks like:

`postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require`

Do not put this value in the Android app or in GitHub.

## 2. Render

Create a new **Web Service** from the HolidayLink GitHub repository.
The included `render.yaml` can also be used as a Blueprint.

Build command:

`cd server && npm ci`

Start command:

`cd server && npm start`

The server already listens on Render's `PORT` and on `0.0.0.0`.

## 3. Render environment variables

The important values are:

- `DATABASE_URL` = the Neon pooled PostgreSQL URL
- `JWT_SECRET` = a long random secret (Render can generate it)
- `INFOBIP_BASE_URL` = your Infobip base URL
- `INFOBIP_API_KEY` = your Infobip server key
- `INFOBIP_FROM` = your SMS sender
- `GEMINI_API_KEY` = your Gemini server key
- `GEMINI_MODELS` = the model fallback chain
- `DEFAULT_COUNTRY_CODE` = `256`
- `PGSSL_DISABLE` = `false`

The backend automatically runs `database/schema.sql` and `database/migrations/002_features.sql` on startup. The migration files use additive/IF NOT EXISTS operations so they can be safely re-run.

## 4. Get the Render URL

After the Render service deploys, Render gives you a URL such as:

`https://holidaylink-api.onrender.com`

Test:

`https://holidaylink-api.onrender.com/health`

You want JSON containing `"ok": true` and `"database": "ok"`.

## 5. Put the Render URL into the Android app

The Android app uses `HL_API_BASE_URL` for its build-time server address.
You can set it in `android/gradle.properties`:

`HL_API_BASE_URL=https://holidaylink-api.onrender.com`

or build from the Android directory with:

`./gradlew assembleDebug -PHL_API_BASE_URL=https://holidaylink-api.onrender.com`

The app also has a runtime server-address setting, so you can change the API host without editing Kotlin.

## Important distinction

**Neon URL -> Render only -> `DATABASE_URL`**

**Render URL -> Android app -> `HL_API_BASE_URL`**

Never put the Neon database URL inside the Android app. It would expose database credentials to anyone who extracts the APK.
