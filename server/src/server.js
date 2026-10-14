import 'dotenv/config';
import crypto from 'crypto';
import express from 'express';
import cors from 'cors';
import admin from 'firebase-admin';

/**
 * HolidayLink OTP service (v3).
 *
 * The app database is Firebase/Firestore (same as holidaylink.org). This tiny
 * service exists only because the website has no SMS sender: it generates a
 * 6-digit code, sends it through Infobip, stores only its SHA-256 hash in
 * Firestore (`otpCodes/{phone}`, 5-minute expiry), and on verify returns a
 * Firebase custom token so the app signs in with Firebase Auth.
 *
 * It also relays "tell the other side" pushes (POST /push/send) between devices:
 * no chat content is stored there, the preview is handed straight to FCM.
 *
 * No chat, message, or user content lives here. No Postgres.
 */

const app = express();
app.disable('x-powered-by');
app.set('trust proxy', 1);
app.use(cors());
app.use(express.json({ limit: '1mb' }));

const PORT = Number(process.env.PORT || 8080);
const DEFAULT_COUNTRY_CODE = String(process.env.DEFAULT_COUNTRY_CODE || '256').replace(/\D/g, '') || '256';
const OTP_TTL_MINUTES = 5;
const OTP_COOLDOWN_SECONDS = Math.max(0, Number(process.env.OTP_COOLDOWN_SECONDS || 45));
const OTP_MAX_ATTEMPTS = 5;

// ---------------------------------------------------------------- Firebase Admin

const svcRaw = String(process.env.FIREBASE_SERVICE_ACCOUNT_JSON || '').trim();
if (!svcRaw) {
  console.error('[config] FIREBASE_SERVICE_ACCOUNT_JSON is not set - OTP verification cannot run.');
}
const serviceAccount = svcRaw ? JSON.parse(svcRaw) : null;
if (serviceAccount) {
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
}
const auth = () => admin.auth();
const db = () => admin.firestore();

// ---------------------------------------------------------------- helpers

function normalizePhone(value) {
  let raw = String(value == null ? '' : value).trim().replace(/[\s\-().]/g, '');
  if (!raw) return '';
  if (raw.startsWith('00')) raw = '+' + raw.slice(2);
  if (raw.startsWith('+')) return '+' + raw.slice(1).replace(/\D/g, '');
  let digits = raw.replace(/\D/g, '');
  if (!digits) return '';
  if (digits.startsWith(DEFAULT_COUNTRY_CODE) && digits.length > DEFAULT_COUNTRY_CODE.length + 6) {
    return '+' + digits;
  }
  digits = digits.replace(/^0+/, '');
  if (!digits) return '';
  return '+' + DEFAULT_COUNTRY_CODE + digits;
}

function isValidPhone(phone) {
  return /^\+[1-9]\d{6,14}$/.test(phone);
}

function codeHash(phone, code) {
  return crypto.createHash('sha256').update(`${phone}:${code}`).digest('hex');
}

// In-memory abuse protection (cooldown only; attempts live on the Firestore doc).
const resendCooldown = new Map();
setInterval(() => {
  const now = Date.now();
  for (const [phone, at] of resendCooldown) {
    if (now - at > OTP_COOLDOWN_SECONDS * 1000) resendCooldown.delete(phone);
  }
}, 60_000).unref();

async function infobip(path, options = {}) {
  const base = String(process.env.INFOBIP_BASE_URL || '').replace(/\/+$/, '');
  const key = process.env.INFOBIP_API_KEY;
  if (!base || !key) throw new Error('Infobip is not configured on the server');
  const response = await fetch(`${base}${path}`, {
    ...options,
    headers: {
      Authorization: `App ${key}`,
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  });
  const text = await response.text();
  let body = {};
  try { body = JSON.parse(text); } catch { body = { raw: text }; }
  if (!response.ok) {
    const e = new Error(body?.requestError?.serviceException?.text || body?.message || 'Infobip request failed');
    e.status = response.status;
    e.body = body;
    throw e;
  }
  return body;
}

// ---------------------------------------------------------------- routes

app.get('/health', (_req, res) => {
  res.json({
    ok: true,
    service: 'holidaylink-otp',
    smsProviderConfigured: Boolean(process.env.INFOBIP_BASE_URL && process.env.INFOBIP_API_KEY),
    firebaseConfigured: Boolean(serviceAccount),
    pushConfigured: Boolean(serviceAccount),
    defaultCountryCode: DEFAULT_COUNTRY_CODE,
    otpTtlMinutes: OTP_TTL_MINUTES
  });
});

/**
 * Generate a code, SMS it through Infobip, store only its hash in Firebase.
 * The number may then log in for the next 5 minutes after verifying.
 */
app.post('/otp/request', async (req, res) => {
  const phone = normalizePhone(req.body.phone);
  if (!isValidPhone(phone)) {
    return res.status(400).json({ error: 'INVALID_PHONE', message: 'Enter a valid phone number including your country code.' });
  }
  if (!serviceAccount) {
    return res.status(503).json({ error: 'OTP_UNAVAILABLE', message: 'Verification is not configured yet. Try again shortly.' });
  }
  const at = resendCooldown.get(phone);
  if (at && Date.now() - at < OTP_COOLDOWN_SECONDS * 1000) {
    return res.status(429).json({
      error: 'OTP_COOLDOWN',
      message: 'A code was just sent to this number. Please wait before requesting another one.',
      retryAfterSeconds: Math.ceil((at + OTP_COOLDOWN_SECONDS * 1000 - Date.now()) / 1000)
    });
  }

  const code = String(crypto.randomInt(100000, 1000000));
  let sent = null;
  try {
    sent = await infobip('/sms/3/messages', {
      method: 'POST',
      body: JSON.stringify({
        messages: [{
          sender: (process.env.INFOBIP_FROM || 'HolidayLink').trim(),
          destinations: [{ to: phone.replace('+', '') }],
          content: { text: `Your HolidayLink verification code is ${code}. It expires in ${OTP_TTL_MINUTES} minutes.` }
        }]
      })
    });
  } catch (err) {
    console.error('[otp/request] sms provider error:', err.status || '', err.message);
    return res.status(502).json({ error: 'OTP_SEND_FAILED', message: 'We could not send the verification code right now. Please try again.' });
  }

  const expiresAt = admin.firestore.Timestamp.fromMillis(Date.now() + OTP_TTL_MINUTES * 60 * 1000);
  await db().collection('otpCodes').doc(phone).set({
    codeHash: codeHash(phone, code),
    expiresAt,
    attempts: 0,
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });

  resendCooldown.set(phone, Date.now());
  res.json({
    ok: true,
    phone,
    expiresInSeconds: OTP_TTL_MINUTES * 60,
    resendAfterSeconds: OTP_COOLDOWN_SECONDS,
    messageId: sent?.messages?.[0]?.messageId || null
  });
});

/**
 * Verify the code. On success the number is logged in: existing Firebase user
 * (matched by phone, like the website login) or a newly created one, and the
 * response carries a Firebase custom token the app signs in with.
 */
app.post('/otp/verify', async (req, res) => {
  const phone = normalizePhone(req.body.phone);
  const code = String(req.body.pin ?? req.body.code ?? req.body.otp ?? '').replace(/\D/g, '');
  if (!isValidPhone(phone)) {
    return res.status(400).json({ error: 'INVALID_PHONE', message: 'Enter a valid phone number including your country code.' });
  }
  if (!/^\d{6}$/.test(code)) {
    return res.status(400).json({ error: 'INVALID_OTP', message: 'That code is incorrect or has expired. Check the SMS and try again.' });
  }
  if (!serviceAccount) {
    return res.status(503).json({ error: 'OTP_UNAVAILABLE', message: 'Verification is not configured yet. Try again shortly.' });
  }

  const ref = db().collection('otpCodes').doc(phone);
  const snap = await ref.get();
  if (!snap.exists) {
    return res.status(400).json({ error: 'OTP_SESSION_EXPIRED', message: 'That code has expired. Request a new code.' });
  }
  const doc = snap.data();
  const expired = !doc.expiresAt || doc.expiresAt.toMillis() <= Date.now();
  if (expired) {
    await ref.delete().catch(() => {});
    return res.status(400).json({ error: 'OTP_SESSION_EXPIRED', message: 'That code has expired. Request a new code.' });
  }
  if ((doc.attempts || 0) >= OTP_MAX_ATTEMPTS) {
    return res.status(429).json({ error: 'TOO_MANY_ATTEMPTS', message: 'Too many incorrect attempts. Request a new code and wait for the SMS.' });
  }
  const valid = crypto.timingSafeEqual(
    Buffer.from(codeHash(phone, code)),
    Buffer.from(String(doc.codeHash || ''))
  );
  if (!valid) {
    const attempts = (doc.attempts || 0) + 1;
    await ref.update({ attempts }).catch(() => {});
    return res.status(400).json({
      error: 'INVALID_OTP',
      message: 'That code is incorrect or has expired. Check the SMS and try again.',
      attemptsRemaining: Math.max(0, OTP_MAX_ATTEMPTS - attempts)
    });
  }

  // One-time use: the code dies the moment it works.
  await ref.delete().catch(() => {});

  // Same lookup the website login uses: existing users doc by phone.
  const found = await db().collection('users').where('phone', '==', phone).limit(1).get();
  let uid;
  let isNew = false;
  if (!found.empty) {
    uid = found.docs[0].id;
  } else {
    const created = await auth().createUser({ phoneNumber: phone });
    uid = created.uid;
    await db().collection('users').doc(uid).set({
      phone,
      phoneLogin: phone,
      name: '',
      username: '',
      email: '',
      activated: false,
      premium: false,
      balance: 0,
      bio: '',
      profilePic: null,
      online: false,
      favorites: [],
      blocked: [],
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    isNew = true;
  }

  const token = await auth().createCustomToken(uid);
  res.json({ ok: true, token, uid, isNew, phone });
});

// ---------------------------------------------------------------- push notifications

const PUSH_WINDOW_MS = 60_000;
const PUSH_MAX_PER_WINDOW = 90;
const PUSH_ACTIVE_WINDOW_MS = 2 * 60 * 1000;
const pushHits = new Map();
setInterval(() => {
  const now = Date.now();
  for (const [uid, hits] of pushHits) {
    const fresh = hits.filter((t) => now - t < PUSH_WINDOW_MS);
    if (fresh.length) pushHits.set(uid, fresh);
    else pushHits.delete(uid);
  }
}, 60_000).unref();

/** Cheap in-memory guard so a stuck client cannot spin this endpoint. */
function pushAllowed(uid) {
  const now = Date.now();
  const hits = (pushHits.get(uid) || []).filter((t) => now - t < PUSH_WINDOW_MS);
  if (hits.length >= PUSH_MAX_PER_WINDOW) {
    pushHits.set(uid, hits);
    return false;
  }
  hits.push(now);
  pushHits.set(uid, hits);
  return true;
}

function bearer(req) {
  const raw = String((req.headers && req.headers.authorization) || '').trim();
  const match = /^Bearer\s+(.+)$/i.exec(raw);
  return match ? match[1].trim() : '';
}

/** Display name exactly like the app resolves it: `name`, then `displayName`. */
async function displayName(uid) {
  try {
    const snap = await db().collection('users').doc(uid).get();
    const doc = snap.data() || {};
    return String(doc.name || doc.displayName || '').trim() || 'HolidayLink user';
  } catch {
    return 'HolidayLink user';
  }
}

/** `lastSeen` may be a Firestore Timestamp, millis, or an ISO string. */
function lastSeenMillis(value) {
  if (!value) return 0;
  if (typeof value.toMillis === 'function') return value.toMillis();
  if (typeof value === 'number') return value;
  const parsed = Date.parse(String(value));
  return Number.isFinite(parsed) ? parsed : 0;
}

/**
 * Sends one high-priority FCM data message to every device on a user's row and
 * prunes tokens FCM reports as dead, so the list stays clean.
 *
 * `skipIfActive` drops the push when the recipient is demonstrably in the app
 * already (recent heartbeat), which stops tray duplicates while they are chatting.
 */
async function pushToUser(uid, data, ttlSeconds, skipIfActive) {
  const snap = await db().collection('users').doc(uid).get();
  if (!snap.exists) return { sent: 0, failed: 0, skipped: true };
  const doc = snap.data() || {};

  if (skipIfActive && doc.online === true) {
    const fresh = Date.now() - lastSeenMillis(doc.lastSeen) < PUSH_ACTIVE_WINDOW_MS;
    if (fresh) return { sent: 0, failed: 0, skipped: true };
  }

  const tokens = Array.from(new Set(
    (doc.fcmTokens || []).filter((t) => typeof t === 'string' && t.length > 0)
  ));
  if (!tokens.length) return { sent: 0, failed: 0, skipped: true };

  const result = await admin.messaging().sendEachForMulticast({
    tokens,
    data,
    android: { priority: 'high', ttl: ttlSeconds * 1000 },
    apns: { headers: { 'apns-priority': '10' } }
  });

  const dead = [];
  result.responses.forEach((r, i) => {
    if (r.success) return;
    const code = r.error && r.error.code;
    if (
      code === 'messaging/registration-token-not-registered'
      || code === 'messaging/invalid-registration-token'
      || code === 'messaging/invalid-argument'
    ) {
      dead.push(tokens[i]);
    }
  });
  if (dead.length) {
    await db().collection('users').doc(uid)
      .update({ fcmTokens: admin.firestore.FieldValue.arrayRemove(...dead) })
      .catch(() => {});
  }
  return { sent: result.successCount, failed: result.failureCount };
}

/**
 * POST /push/send — "buzz the other side" for a message or an incoming call.
 *
 * The caller proves identity with a Firebase ID token (`Authorization: Bearer`).
 * Recipients are read from the chat/call document, never from the request body,
 * so a signed-in user can only alert people they are already talking to.
 *
 * Body: { chatId? , callId?, type? "message"|"call", title?, body?, id? }
 */
app.post('/push/send', async (req, res) => {
  if (!serviceAccount) {
    return res.status(503).json({ error: 'PUSH_UNAVAILABLE', message: 'Push is not configured on the server.' });
  }
  const idToken = bearer(req);
  if (!idToken) return res.status(401).json({ error: 'UNAUTHENTICATED' });

  let sender;
  try {
    sender = await auth().verifyIdToken(idToken);
  } catch {
    return res.status(401).json({ error: 'UNAUTHENTICATED' });
  }
  if (!pushAllowed(sender.uid)) {
    return res.status(429).json({ error: 'PUSH_RATE_LIMITED', message: 'Too many notifications in a short time.' });
  }

  const chatId = String(req.body.chatId || '').trim().slice(0, 200);
  const callId = String(req.body.callId || '').trim().slice(0, 200);
  if (!chatId && !callId) {
    return res.status(400).json({ error: 'NO_TARGET', message: 'Provide chatId or callId.' });
  }

  const preview = String(req.body.body || '').slice(0, 240);
  const idOverride = String(req.body.id || '').slice(0, 200);

  let recipients = [];
  let type = String(req.body.type || '') === 'call' ? 'call' : 'message';
  let title = String(req.body.title || '').slice(0, 120);
  let body = preview;

  try {
    if (callId) {
      const call = await db().collection('calls').doc(callId).get();
      if (!call.exists) return res.status(404).json({ error: 'CALL_NOT_FOUND' });
      if (call.get('callerId') !== sender.uid) return res.status(403).json({ error: 'NOT_CALLER' });
      const callee = call.get('calleeId');
      if (callee) recipients = [callee];
      type = 'call';
      if (!title) title = await displayName(sender.uid);
      if (!body) body = String(call.get('kind') || '') === 'video' ? 'Incoming video call' : 'Incoming audio call';
    } else {
      const chat = await db().collection('chats').doc(chatId).get();
      if (!chat.exists) return res.status(404).json({ error: 'CHAT_NOT_FOUND' });
      const users = (chat.get('users') || []).filter((u) => typeof u === 'string');
      if (!users.includes(sender.uid)) return res.status(403).json({ error: 'NOT_A_MEMBER' });
      recipients = users.filter((u) => u !== sender.uid);
      if (recipients.length) {
        const groupName = String(chat.get('groupName') || '').trim();
        const senderName = await displayName(sender.uid);
        if (!title) title = groupName || senderName;
        // In a group the sender matters: "Alice: see you at 6".
        if (groupName && preview) body = `${senderName}: ${preview}`;
      }
    }
  } catch (err) {
    console.error('[push/send] lookup failed:', err.message);
    return res.status(500).json({ error: 'LOOKUP_FAILED' });
  }

  if (!title) title = 'HolidayLink';
  if (!body) body = type === 'call' ? 'Incoming call' : 'New message';
  if (!recipients.length) return res.json({ ok: true, recipients: 0, sent: 0, failed: 0, skipped: true });

  const data = {
    type,
    title,
    body,
    chatId: chatId || '',
    callId: callId || '',
    from: sender.uid
  };
  if (idOverride) data.id = idOverride;

  // Calls are urgent and short-lived; messages can wait a day.
  const ttlSeconds = type === 'call' ? 60 : 24 * 3600;
  const results = await Promise.all(recipients.map((uid) =>
    pushToUser(uid, data, ttlSeconds, type === 'message')
      .catch(() => ({ sent: 0, failed: 0, skipped: false }))
  ));

  let sent = 0;
  let failed = 0;
  let skipped = 0;
  results.forEach((r) => {
    sent += r.sent;
    failed += r.failed;
    if (r.skipped) skipped += 1;
  });

  res.json({ ok: true, recipients: recipients.length, sent, failed, skipped });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`HolidayLink OTP service listening on 0.0.0.0:${PORT}`);
});
