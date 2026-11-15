# HolidayLink encrypted cloud settings backup + email recovery

## What this feature does

- Adds a recovery email verification code sent through the Render server's SMTP connection.
- Encrypts the app settings JSON on the Android device with AES-256-GCM. The key is derived from the user's passphrase using PBKDF2-HMAC-SHA256 (210,000 rounds).
- Uploads only ciphertext, a random salt, and a random IV. The Render server cannot decrypt the settings and never receives the passphrase.
- Allows the signed-in account to restore the encrypted settings after re-entering the passphrase.
- Excludes chat history, media, account credentials, and the app-lock PIN. This is a settings backup, not a full chat-history backup.

## Render environment variables

In the existing `holidaylink-otp` Render service, add these environment variables. `render.yaml` now declares them; set the secret values in the Render dashboard:

- `SMTP_HOST` — SMTP server hostname from your email provider.
- `SMTP_PORT` — usually `587` for STARTTLS or `465` for implicit TLS.
- `SMTP_SECURE` — `false` for port 587, `true` for port 465.
- `SMTP_USER` — SMTP username.
- `SMTP_PASS` — SMTP password or provider-issued app password. Never use your email account's normal password if the provider recommends app passwords.
- `SMTP_FROM` — approved sender address, e.g. `HolidayLink <no-reply@your-domain.example>`.

Save the variables and redeploy the Render service. Do not place SMTP credentials in Android code, GitHub, or the website.

## Android / Firebase requirements

- Keep `FIREBASE_SERVICE_ACCOUNT_JSON` configured on Render. The email routes use it to authenticate the signed-in Firebase user and save the ciphertext to Firestore with Admin SDK.
- The app needs Firebase Storage rules for the `statuses/{uid}/{fileName}` path to allow signed-in users to upload their own status videos up to 30 MB. The matching rules are in `FIRESTORE_RULES_ADDITION.md`.
- Do not grant client read/write access to `userBackups` or `backupEmailCodes`.
- Build with the same Firebase project configured in `android/app/google-services.json` and the deployed Render backend URL.

## Test plan

1. Install the new build and sign in.
2. Open Settings → Data and storage → Encrypted cloud backup and email recovery.
3. Enter an email, tap Send verification code, and check the inbox/spam folder.
4. Enter the six-digit code and verify.
5. Enter a passphrase of at least eight characters and save the cloud backup.
6. Change a harmless preference, then restore with the same passphrase and confirm the preference returns.
7. Try an incorrect passphrase: restore must fail without importing settings.
8. Turn off SMTP temporarily in a test deployment: the app should show a clear setup error rather than claiming verification succeeded.

## Limitations / safety

- Losing the passphrase means the backup cannot be decrypted; the server does not hold a recovery key.
- The email is a verified recovery contact for backup settings, not a replacement for the existing phone-number sign-in.
- Android force-stop, revoked notification permission, battery restrictions, unavailable data networks, or FCM/provider outages can still prevent real-time incoming notifications/calls. Do not promise guaranteed delivery in those conditions.
