# Notifications: how they work and what to set up

Short version: the app shows notifications three ways, and only one of them needs
anything from you. Everything needs the **notification permission** and the right
**Firestore rules**. The Render service needs a **redeploy** for the new push endpoint.

## The three paths

| Path | Works when | Who sends it |
| --- | --- | --- |
| `notifications` listener | app process alive | whatever writes the doc (website or app) |
| `unread_<uid>` chat counter | app process alive | the sender's app (automatic) |
| **FCM push** (`POST /push/send`) | **app closed or killed** | the sender's app → Render → FCM |

The first two are in-process Firestore listeners. They are why notifications used to
look broken: the queries paired `whereEqualTo(...)` with `orderBy(...)`, which needs a
Firestore **composite index**. When the index is missing the query fails **silently**,
so the list came back empty and nothing ever buzzed. That is fixed — every list query
now uses a single filter and sorts on the device.

## 1. Phone: allow notifications (Android 13+)

Tap **Allow** on the banner at the top of the Chats tab (or Settings ▸ Notifications).
If it was denied before, re-enable under *Android Settings ▸ Apps ▸ HolidayLink ▸
Notifications*. `POST_NOTIFICATIONS` is declared in the manifest and requested in-app;
without the grant Android drops everything.

Also, on aggressive OEMs (Xiaomi, Huawei, Oppo, Vivo, Samsung, Tecno, Infinix) enable
*Autostart* and disable *battery optimisation* for HolidayLink, or background pushes
get delayed or dropped.

Older builds may have created the `Messages`/`Calls` channels at a lower importance,
and Android will not let an app raise it. Toggle the channel off→on in Android settings,
or clear app data once.

## 2. Firestore rules

The user must be able to read (and mark read) their own notification rows, and to store
their own FCM token. Add:

```firestore
match /notifications/{id} {
  // Read / mark-read / delete only your own rows.
  allow read, update, delete: if request.auth != null
    && resource.data.userId == request.auth.uid;
  // Anyone signed in may notify someone (the app only does so to chat partners).
  allow create: if request.auth != null;
}
```

```firestore
match /users/{uid} {
  // Needed so the device can write users/{uid}.fcmTokens — without this, no push.
  allow update: if request.auth != null && request.auth.uid == uid;
}
```

Merge these into your deployed site rules (see `FIRESTORE_RULES_ADDITION.md`); do not
replace the site's rules wholesale.

No composite indexes are needed. If you ever add `orderBy` next to a filter, you must
also deploy a matching index or the query will fail silently again.

## 3. Render service: redeploy

`server/src/server.js` gained `POST /push/send`. It needs **no new environment
variables** — it reuses `FIREBASE_SERVICE_ACCOUNT_JSON`. Just redeploy the service
(Render auto-deploys on `main`, or hit Manual Deploy) and check:

```
GET https://<your-service>/health
```

You should see `"pushConfigured": true`.

## 4. How the push actually flows

```
sender taps Send
  └─ FirestoreChat.send() writes the message, then calls PushNotify.message(chatId, preview)
       └─ POST /push/send   Authorization: Bearer <Firebase ID token>
            ├─ server verifies the ID token           → sender uid
            ├─ server reads chats/{chatId}.users      → recipients (never from the body)
            ├─ skips recipients who are online right now (recent heartbeat)
            └─ FCM data message → each recipient's devices (users/{uid}.fcmTokens)
                 └─ HolidayMessagingService builds the tray notification
```

Calls use the same route with `callId`; the server reads `calls/{callId}` and only ever
notifies the doc's `calleeId`, and only if the caller is the doc's `callerId`.

### Endpoint contract

`POST /push/send` — header `Authorization: Bearer <Firebase ID token>`

```json
{ "chatId": "abc_xyz", "type": "message", "body": "see you at 6", "id": "msgId" }
{ "callId": "callDocId", "type": "call" }
```

* Recipients are **never** taken from the request — they come from `chats/{chatId}.users`
  or `calls/{callId}.calleeId`. A signed-in user can only alert people they already talk to.
* The title is built server-side: the group name in a group (body becomes
  `"Sender: preview"`), otherwise the sender's display name.
* Recipients whose `online` flag is true with a `lastSeen` under 2 minutes old are
  skipped, so you do not get a tray pop-up while chatting in the app.
* Dead tokens are removed from `users/{uid}.fcmTokens` automatically.
* Response: `{ ok, recipients, sent, failed, skipped }`.

## 5. What still needs the website

If the website wants to notify app users from its own events (an order shipped, an
admin broadcast), it can either:

1. write a `notifications/{id}` doc with `userId`, `type`, `title`, `body`, `timestamp`
   (and `chatId`/`callId` when relevant) — the app picks it up while alive; or
2. call the same `POST /push/send` with a service-account-derived token, if it needs to
   reach closed apps.

## Testing

1. Install the build on **two** devices (or one device + a browser session as the website).
2. Grant notifications on both.
3. Put device B's app to sleep / close it (do not force-stop — Android blocks data
   messages to force-stopped apps).
4. From A, send a message. B should get a tray notification within a few seconds.
5. Start a call from A. B should get a "Incoming … call" alert.

If nothing arrives: check `/health` shows `pushConfigured: true`, confirm
`users/{B uid}.fcmTokens` has an entry, confirm B granted notifications, and check the
Render logs for `[push/send]`.
