# Firestore rules additions for the Android app (merge into holidaylink.org rules)

The app talks to the SAME Firestore as the website. Most collections are
already readable per the site. Merge these matches; everything else stays
exactly as the website has it. Admin pages and powers do not move.

```firestore
// OTP codes: server-only (Render OTP service uses Admin SDK, bypasses rules).
// Clients must never read or write these.
match /otpCodes/{phone} {
  allow read, write: if false;
}

// E2EE device keys: anyone signed in may read a public key, but only the
// owner may write their own row. { userId, publicKey }
match /device_keys/{userId} {
  allow read: if request.auth != null;
  allow write: if request.auth != null && request.auth.uid == userId;
}

// Study Buddy admin config: readable by signed-in app users (same as the
// website widget). Writes stay admin-only (website admin panel).
match /aiSettings/{doc}   { allow read: if request.auth != null; allow write: if false; }
match /aiPrompt/{doc}     { allow read: if request.auth != null; allow write: if false; }
match /aiCommands/{doc}   { allow read: if request.auth != null; allow write: if false; }
```

## Must verify against the DEPLOYED rules (not just the repo copy)

1. `users` reads: the app lists users and resolves @names (`loadFriends`,
   username checks). The repo `firestore.rules` allows owner-only reads, but
   the live site clearly reads wider (dashboard buddies list). Confirm the
   deployed rules permit what the website already does — the app needs the
   same.
2. `chats/{id}/messages` writes use field `users: [uidA, uidB]` on the chat
   doc (website convention, also used by Study Buddy send_message). The repo
   rules check `participants[]` — confirm deployed rules match the website's
   actual field or update them; otherwise both site and app writes fail.
3. `notifications` create-if-signed-in must stay (AI send_message + admin
   broadcast depend on it).
4. `sessions` / `twoFactorRequests` owner-`userId` rules must stay (app 2FA
   device approval depends on them).
```

Render OTP service needs only:
- FIREBASE_SERVICE_ACCOUNT_JSON (service account, Project settings > Service accounts)
- INFOBIP_BASE_URL + INFOBIP_API_KEY + INFOBIP_FROM
- DEFAULT_COUNTRY_CODE=256, OTP_COOLDOWN_SECONDS=45
