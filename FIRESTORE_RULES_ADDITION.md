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
   broadcast depend on it). The app also needs to **read/update its own rows**
   (`resource.data.userId == request.auth.uid`) so the Notifications screen and
   mark-as-read work.
4. `sessions` / `twoFactorRequests` owner-`userId` rules must stay (app 2FA
   device approval depends on them).
5. The owner must be able to `update` their own `users/{uid}` row, including
   `fcmTokens`, or device push tokens are never stored and background
   notifications cannot be delivered.
```

Render OTP service needs only:
- FIREBASE_SERVICE_ACCOUNT_JSON (service account, Project settings > Service accounts)
- YOOLA_API_KEY + YOOLA_SENDER (sender ID is optional, defaults to YoolaSMS)
- DEFAULT_COUNTRY_CODE=256, OTP_COOLDOWN_SECONDS=45

The same service also serves `POST /push/send` (FCM relay for notifications while a
phone's app is closed). It needs **no extra env vars** — just a redeploy so the new
route exists. See `PUSH_NOTIFICATIONS.md` for the full end-to-end setup, the request
contract and how to test it.

## Group management fields (new in the Android app)

The in-app group profile writes extra fields on `chats/{id}`:

```
members: [uid, ...]      // full membership (falls back to `users` when absent)
admins:  [uid, ...]      // creator is always treated as an admin
createdBy: uid
privacy: "public" | "private"
groupPic / groupPhoto: url
settings: { sendMessagesAdminsOnly, editInfoAdminsOnly, approveNewMembers }
```

Rules must let a group participant update these, otherwise:
- "Only admins can send messages" / "edit info" / "approve new members" toggles
  and public/private visibility will silently fail.
- `privacy == "public"` is queried by single equality filter (no index needed),
  so anyone signed in must be able to read a public group row.

## No composite indexes required

Every list query in the app now uses at most ONE equality filter followed by a
client-side sort. A `whereEqualTo(...) + orderBy(...)` pair requires a composite
index and fails silently when the index is missing (that was the cause of the
call-history error, the empty notifications list and incoming calls not ringing).
Do not add `orderBy` back next to a filter.

## In-app self-update feed

Settings ▸ App updates checks, in order:

1. `https://api.github.com/repos/claudeuse256-cmd/holidaylinkapp/releases/latest`
   — publish a GitHub **Release** with an `.apk` asset. Use the `versionCode`
   as the tag (e.g. `5`) or add `target_code` to the release body JSON.
2. `https://holidaylink.org/app/latest.json` — plain JSON fallback:
   `{ "versionName": "2.3.0", "code": 5, "url": "https://.../app.apk", "notes": "..." }`

If neither reports a `code` greater than the installed build's `versionCode`,
the app reports "You're on the latest version".

## Firebase Storage rules (chat voice/video/files + any Storage use)

```storage
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Signed-in users may read any chat media (same openness as the site).
    match /chat_media/{chatId}/{file} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.resource.size < 20 * 1024 * 1024;
    }
    match /chat_voice/{chatId}/{file} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.resource.size < 20 * 1024 * 1024;
    }
  }
}
```

## Status videos (Firebase Storage)

Status videos are uploaded to `statuses/{uid}/{filename}` and are limited to 30 MB by the app. Merge this Storage rule with the existing chat-media rules (do not replace the entire ruleset):

```storage
match /statuses/{uid}/{fileName} {
  allow read: if request.auth != null;
  allow write: if request.auth != null
    && request.auth.uid == uid
    && request.resource.size < 30 * 1024 * 1024
    && request.resource.contentType.matches('video/.*');
}
```

Cloud backup records are written only by the Render Admin SDK to `userBackups/{uid}`; clients should not be granted direct access to that collection. Email verification codes in `backupEmailCodes/{uid}` must also remain server-only.

## Status reactions and mention notifications (new Android status workflow)

Status reactions are stored at `statuses/{statusId}/status_reactions/{uid}`. Merge this
match into the deployed Firestore rules. A user can only create/update/delete their own
reaction document; signed-in status viewers can read aggregate reactions when the status
itself is readable.

```firestore
match /statuses/{statusId}/status_reactions/{uid} {
  allow read: if request.auth != null;
  allow create, update, delete: if request.auth != null
    && request.auth.uid == uid;
}
```

Status mention and reaction notifications use the existing `notifications` collection
and the same owner-read rule in `PUSH_NOTIFICATIONS.md`. The mention/reaction sender also
writes a context message into the direct chat so the event is visible in the conversation.
Do not open notification reads to all signed-in users.

## Status music (Firebase Storage)

The status editor stores selected audio under `statuses/{uid}/music_*`. The previous video-only
rule does not permit this upload. Merge the audio match below alongside the existing video
match; do not loosen the video rule to arbitrary content types.

```storage
match /statuses/{uid}/{fileName} {
  allow read: if request.auth != null;
  allow write: if request.auth != null
    && request.auth.uid == uid
    && request.resource.size < 20 * 1024 * 1024
    && request.resource.contentType.matches('audio/.*');
}
```

## Encryption release blocker — verify before advertising E2EE

The Android Firestore chat path (`FirestoreChat`) currently writes message `text` directly
into `chats/{chatId}/messages`. The ECDH/AES-GCM envelope implementation in `E2ee.kt` is
used by the legacy Render conversations API (`Api.sendMessage`), not by the active Firestore
chat UI. Therefore the active Firestore chat path must **not** be described as end-to-end
encrypted until the Android and website clients share a compatible encrypted-message protocol,
key distribution, multi-device recovery and migration plan. Do not silently switch the
Firestore `text` field to ciphertext: that would break website chat clients and old messages.
