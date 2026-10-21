import 'dotenv/config';
import crypto from 'crypto';
import express from 'express';
import cors from 'cors';
import admin from 'firebase-admin';
import nodemailer from 'nodemailer';

/**
 * HolidayLink OTP service (v3).
 *
 * The app database is Firebase/Firestore (same as holidaylink.org). This tiny
 * service exists only because the website has no SMS sender: it generates a
 * 6-digit code, sends it through Yoola SMS, stores only its SHA-256 hash in
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
app.use(express.json({ limit: '3mb' }));

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

// ---------------------------------------------------------------- SMS (Yoola)

// Yoola SMS (yoolasms.com) REST API v1. The key lives only on this server,
// never in the app. Sender IDs are alphanumeric (letters and numbers only).
const YOOLA_BASE_URL = String(process.env.YOOLA_BASE_URL || 'https://yoolasms.com/api/v1').replace(/\/+$/, '');
const YOOLA_API_KEY = String(
  process.env.YOOLA_API_KEY || process.env.YOOLA_SMS_API_KEY || process.env.YOOLASMS_API_KEY || ''
).trim();
const YOOLA_SENDER = String(
  process.env.YOOLA_SENDER || process.env.YOOLA_SENDER_ID || process.env.YOOLA_FROM || 'YoolaSMS'
).trim();

function smsConfigured() {
  return Boolean(YOOLA_API_KEY);
}

async function yoolaSms(path, payload) {
  if (!YOOLA_API_KEY) throw new Error('Yoola SMS is not configured on the server');
  const response = await fetch(`${YOOLA_BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ ...payload, api_key: YOOLA_API_KEY })
  });
  const text = await response.text();
  let body = {};
  try { body = JSON.parse(text); } catch { body = { raw: text }; }
  const status = String(body?.status || '').toLowerCase();
  if (!response.ok || body?.success === false || status === 'error' || status === 'failed') {
    const e = new Error(body?.message || body?.error || `Yoola request failed (${response.status})`);
    e.status = response.ok ? 502 : response.status;
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
    smsProvider: 'yoola',
    smsProviderConfigured: smsConfigured(),
    firebaseConfigured: Boolean(serviceAccount),
    pushConfigured: Boolean(serviceAccount),
    cloudinaryConfigured: cloudinaryConfigured(),
    defaultCountryCode: DEFAULT_COUNTRY_CODE,
    otpTtlMinutes: OTP_TTL_MINUTES
  });
});

/**
 * Generate a code, SMS it through Yoola SMS, store only its hash in Firebase.
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
    sent = await yoolaSms('/send', {
      phone: phone.replace('+', ''),
      sender: YOOLA_SENDER,
      message: `Your HolidayLink verification code is ${code}. It expires in ${OTP_TTL_MINUTES} minutes.`
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
    messageId: sent?.message_id || sent?.messageId || sent?.id || null
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

  // The push carries BOTH a `notification` block and the raw `data`. Android draws the
  // notification itself while the app is closed or in the background (so the alert really
  // arrives without the app running), and the data payload travels with the tap intent so
  // the right conversation opens. When the app is alive the messaging service takes over
  // and draws it, which keeps the per-account label ("Account: ...") that the system block
  // cannot know about.
  const chatId = String(data.chatId || '');
  const tag = chatId ? `chat-${chatId}` : `call-${String(data.callId || '')}`;
  const channelId = data.type === 'call' ? 'hl_calls' : 'hl_messages';
  // The cross-platform notification block only knows title and body — every Android
  // specific setting (channel, icon, tag, sound) belongs to android.notification, and
  // Firebase rejects unknown fields.
  const notification = {
    title: String(data.title || 'HolidayLink'),
    body: String(data.body || '')
  };

  const result = await admin.messaging().sendEachForMulticast({
    tokens,
    data,
    notification,
    android: {
      priority: 'high',
      ttl: ttlSeconds * 1000,
      notification: {
        channel_id: channelId,
        icon: 'ic_stat_holidaylink',
        color: '#0B5CFF',
        tag,
        sound: 'default',
        default_vibrate_timings: true,
        default_sound: true,
        notification_priority: 'PRIORITY_HIGH'
      }
    },
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
    // `forUid` lets a phone that holds several accounts label the notification with the
    // account it arrived for, the way WhatsApp marks its linked accounts.
    pushToUser(uid, { ...data, forUid: uid }, ttlSeconds, type === 'message')
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


// ---------------------------------------------------------------- encrypted cloud settings backup + email recovery verification

const BACKUP_EMAIL_TTL_MS = 10 * 60 * 1000;
const backupEmailHits = new Map();
const SMTP_HOST = String(process.env.SMTP_HOST || '').trim();
const SMTP_PORT = Number(process.env.SMTP_PORT || 587);
const SMTP_USER = String(process.env.SMTP_USER || '').trim();
const SMTP_PASS = String(process.env.SMTP_PASS || '').trim();
const SMTP_FROM = String(process.env.SMTP_FROM || SMTP_USER).trim();
const SMTP_SECURE = String(process.env.SMTP_SECURE || '').toLowerCase() === 'true' || SMTP_PORT === 465;
function smtpConfigured() { return Boolean(SMTP_HOST && SMTP_USER && SMTP_PASS && SMTP_FROM); }
function smtpTransport() {
  if (!smtpConfigured()) throw new Error('SMTP is not configured');
  return nodemailer.createTransport({ host: SMTP_HOST, port: SMTP_PORT, secure: SMTP_SECURE,
    auth: { user: SMTP_USER, pass: SMTP_PASS }, connectionTimeout: 12000, socketTimeout: 15000 });
}
function validEmail(value) {
  const email = String(value || '').trim().toLowerCase();
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? email : '';
}
function backupCodeHash(uid, email, code) {
  return crypto.createHash('sha256').update(`${uid}:${email}:${code}`).digest('hex');
}
function backupEmailRateAllowed(uid) {
  const now = Date.now();
  const prev = backupEmailHits.get(uid) || 0;
  if (now - prev < 60_000) return false;
  backupEmailHits.set(uid, now);
  return true;
}

app.get('/backup/email/status', async (req, res) => {
  if (!serviceAccount) return res.status(503).json({ error: 'UNAVAILABLE' });
  const user = await verifyBearer(req);
  if (!user) return res.status(401).json({ error: 'UNAUTHENTICATED' });
  const snap = await db().collection('users').doc(user.uid).get();
  const row = snap.data() || {};
  return res.json({ verified: Boolean(row.backupEmailVerifiedAt && row.backupEmail), email: row.backupEmail || '' });
});

app.post('/backup/email/start', async (req, res) => {
  if (!serviceAccount) return res.status(503).json({ error: 'UNAVAILABLE' });
  const user = await verifyBearer(req);
  if (!user) return res.status(401).json({ error: 'UNAUTHENTICATED' });
  const email = validEmail(req.body.email);
  if (!email) return res.status(400).json({ error: 'INVALID_EMAIL', message: 'Enter a valid email address.' });
  if (!smtpConfigured()) return res.status(503).json({ error: 'SMTP_NOT_CONFIGURED', message: 'Email recovery is not configured on the server yet.' });
  if (!backupEmailRateAllowed(user.uid)) return res.status(429).json({ error: 'RATE_LIMITED', message: 'Wait one minute before requesting another code.' });
  const code = String(crypto.randomInt(0, 1_000_000)).padStart(6, '0');
  const ref = db().collection('backupEmailCodes').doc(user.uid);
  await ref.set({ email, codeHash: backupCodeHash(user.uid, email, code), attempts: 0, createdAt: Date.now(), expiresAt: Date.now() + BACKUP_EMAIL_TTL_MS });
  try {
    await smtpTransport().sendMail({
      from: SMTP_FROM, to: email, subject: 'HolidayLink backup verification code',
      text: `Your HolidayLink backup email verification code is ${code}. It expires in 10 minutes. If you did not request this, you can ignore this email.`,
      html: `<p>Your HolidayLink backup email verification code is:</p><p style="font-size:28px;font-weight:bold;letter-spacing:6px">${code}</p><p>It expires in 10 minutes. If you did not request this, ignore this email.</p>`
    });
  } catch (err) {
    await ref.delete().catch(() => {});
    console.error('[backup/email/start] SMTP send failed:', err.message);
    return res.status(502).json({ error: 'EMAIL_SEND_FAILED', message: 'Could not send the email. Check the SMTP settings on Render.' });
  }
  return res.json({ ok: true, message: 'Verification code sent.' });
});

app.post('/backup/email/verify', async (req, res) => {
  if (!serviceAccount) return res.status(503).json({ error: 'UNAVAILABLE' });
  const user = await verifyBearer(req);
  if (!user) return res.status(401).json({ error: 'UNAUTHENTICATED' });
  const code = String(req.body.code || '').replace(/\D/g, '').slice(0, 6);
  if (code.length !== 6) return res.status(400).json({ error: 'INVALID_CODE', message: 'Enter the six-digit code.' });
  const ref = db().collection('backupEmailCodes').doc(user.uid);
  const snap = await ref.get();
  if (!snap.exists) return res.status(400).json({ error: 'CODE_EXPIRED', message: 'Request a new email code.' });
  const row = snap.data() || {};
  if (Number(row.expiresAt || 0) < Date.now()) {
    await ref.delete().catch(() => {});
    return res.status(400).json({ error: 'CODE_EXPIRED', message: 'Request a new email code.' });
  }
  if (Number(row.attempts || 0) >= 5) {
    await ref.delete().catch(() => {});
    return res.status(429).json({ error: 'TOO_MANY_ATTEMPTS', message: 'Too many attempts. Request a new code.' });
  }
  const expected = String(row.codeHash || '');
  const supplied = backupCodeHash(user.uid, String(row.email || ''), code);
  const a = Buffer.from(expected, 'hex'); const b = Buffer.from(supplied, 'hex');
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    await ref.update({ attempts: admin.firestore.FieldValue.increment(1) });
    return res.status(400).json({ error: 'INVALID_CODE', message: 'That code is not correct.' });
  }
  const email = String(row.email || '');
  await db().collection('users').doc(user.uid).set({ backupEmail: email, backupEmailVerifiedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
  await ref.delete().catch(() => {});
  return res.json({ ok: true, email });
});

// The app encrypts the settings JSON with a passphrase before upload. The server only stores
// ciphertext and cannot read the settings or recover the passphrase.
app.put('/backup/cloud', async (req, res) => {
  if (!serviceAccount) return res.status(503).json({ error: 'UNAVAILABLE' });
  const user = await verifyBearer(req);
  if (!user) return res.status(401).json({ error: 'UNAUTHENTICATED' });
  const doc = await db().collection('users').doc(user.uid).get();
  if (!doc.exists || !doc.get('backupEmailVerifiedAt') || !doc.get('backupEmail')) {
    return res.status(403).json({ error: 'EMAIL_NOT_VERIFIED', message: 'Verify a backup email first.' });
  }
  const encrypted = String(req.body.encrypted || '');
  const salt = String(req.body.salt || '');
  const iv = String(req.body.iv || '');
  if (encrypted.length < 16 || encrypted.length > 900_000 || salt.length > 100 || iv.length > 100) {
    return res.status(400).json({ error: 'INVALID_BACKUP', message: 'Backup data is invalid or too large.' });
  }
  await db().collection('userBackups').doc(user.uid).set({ encrypted, salt, iv, version: 1, updatedAt: admin.firestore.FieldValue.serverTimestamp() });
  return res.json({ ok: true, updatedAt: new Date().toISOString() });
});

app.get('/backup/cloud', async (req, res) => {
  if (!serviceAccount) return res.status(503).json({ error: 'UNAVAILABLE' });
  const user = await verifyBearer(req);
  if (!user) return res.status(401).json({ error: 'UNAUTHENTICATED' });
  const doc = await db().collection('users').doc(user.uid).get();
  if (!doc.exists || !doc.get('backupEmailVerifiedAt')) return res.status(403).json({ error: 'EMAIL_NOT_VERIFIED', message: 'Verify a backup email first.' });
  const backup = await db().collection('userBackups').doc(user.uid).get();
  if (!backup.exists) return res.status(404).json({ error: 'BACKUP_NOT_FOUND', message: 'No cloud backup exists for this account yet.' });
  const row = backup.data() || {};
  return res.json({ encrypted: row.encrypted, salt: row.salt, iv: row.iv, version: row.version || 1, updatedAt: row.updatedAt?.toDate?.()?.toISOString?.() || null });
});

// ---------------------------------------------------------------- media uploads (Cloudinary)

/**
 * Video and document storage.
 *
 * The APK never holds a Cloudinary secret. It sends the raw bytes here with its Firebase
 * ID token; this service signs the Cloudinary request and returns the public HTTPS URL
 * that is written to Firestore. Images keep using imgbb, exactly like the website.
 *
 * Configure either:
 *   CLOUDINARY_CLOUD_NAME + CLOUDINARY_API_KEY + CLOUDINARY_API_SECRET  (signed upload)
 * or
 *   CLOUDINARY_CLOUD_NAME + CLOUDINARY_UPLOAD_PRESET                    (unsigned preset)
 */
const CLOUDINARY_CLOUD_NAME = String(process.env.CLOUDINARY_CLOUD_NAME || '').trim();
const CLOUDINARY_API_KEY = String(process.env.CLOUDINARY_API_KEY || '').trim();
const CLOUDINARY_API_SECRET = String(process.env.CLOUDINARY_API_SECRET || '').trim();
const CLOUDINARY_UPLOAD_PRESET = String(process.env.CLOUDINARY_UPLOAD_PRESET || '').trim();
const CLOUDINARY_FOLDER = String(process.env.CLOUDINARY_FOLDER || 'holidaylink').trim();

function cloudinaryConfigured() {
  if (!CLOUDINARY_CLOUD_NAME) return false;
  return Boolean(CLOUDINARY_UPLOAD_PRESET) || Boolean(CLOUDINARY_API_KEY && CLOUDINARY_API_SECRET);
}

app.post(
  '/media/upload',
  express.raw({ type: '*/*', limit: '32mb' }),
  async (req, res) => {
    if (!serviceAccount) return res.status(503).json({ error: 'UNAVAILABLE' });
    const user = await verifyBearer(req);
    if (!user) return res.status(401).json({ error: 'UNAUTHENTICATED' });
    if (!cloudinaryConfigured()) {
      return res.status(503).json({
        error: 'CLOUDINARY_NOT_CONFIGURED',
        message: 'Video and document uploads are not configured on the server yet.'
      });
    }

    const bytes = Buffer.isBuffer(req.body) ? req.body : null;
    if (!bytes || bytes.length === 0) {
      return res.status(400).json({ error: 'EMPTY_UPLOAD', message: 'The file was empty.' });
    }
    if (bytes.length > 31 * 1024 * 1024) {
      return res.status(413).json({ error: 'TOO_LARGE', message: 'That file is too large to upload.' });
    }

    const rawName = String(req.header('X-Filename') || 'upload').replace(/[^\w.\- ]+/g, '_').slice(0, 120) || 'upload';
    const mimeType = String(req.header('X-Content-Type') || 'application/octet-stream').slice(0, 120);

    try {
      const form = new FormData();
      form.append('file', new Blob([bytes], { type: mimeType }), rawName);

      if (CLOUDINARY_UPLOAD_PRESET) {
        form.append('upload_preset', CLOUDINARY_UPLOAD_PRESET);
      } else {
        const timestamp = Math.floor(Date.now() / 1000);
        const folder = CLOUDINARY_FOLDER;
        const toSign = `folder=${folder}&timestamp=${timestamp}`;
        const signature = crypto.createHash('sha1').update(toSign + CLOUDINARY_API_SECRET).digest('hex');
        form.append('api_key', CLOUDINARY_API_KEY);
        form.append('timestamp', String(timestamp));
        form.append('folder', folder);
        form.append('signature', signature);
      }

      const upload = await fetch(
        `https://api.cloudinary.com/v1_1/${encodeURIComponent(CLOUDINARY_CLOUD_NAME)}/auto/upload`,
        { method: 'POST', body: form }
      );
      const payload = await upload.json().catch(() => ({}));
      if (!upload.ok || !payload.secure_url) {
        console.error('[cloudinary] upload failed', upload.status, payload?.error?.message || payload);
        return res.status(502).json({
          error: 'UPLOAD_FAILED',
          message: 'The file could not be uploaded. Please try again.'
        });
      }
      return res.json({
        ok: true,
        url: payload.secure_url,
        secureUrl: payload.secure_url,
        publicId: payload.public_id || null,
        resourceType: payload.resource_type || null,
        bytes: payload.bytes || bytes.length
      });
    } catch (err) {
      console.error('[cloudinary] upload error', err?.message || err);
      return res.status(502).json({
        error: 'UPLOAD_FAILED',
        message: 'The file could not be uploaded. Please try again.'
      });
    }
  }
);

// ---------------------------------------------------------------- in-app account switching

/**
 * A phone can hold two HolidayLink accounts and switch between them without a new SMS.
 *
 * Firebase Auth only ever has one signed-in user, so the app keeps a long-lived
 * *account token* per saved account and trades it for a fresh Firebase custom token.
 * Only its SHA-256 hash is stored, exactly like the OTP codes; the raw value never
 * leaves the phone that earned it.
 */
const LOGIN_TOKEN_TTL_MS = 180 * 24 * 60 * 60 * 1000;
const ACCOUNT_TOKEN_WINDOW_MS = 60 * 60 * 1000;
const ACCOUNT_TOKEN_MAX_PER_WINDOW = 10;
const accountTokenHits = new Map();

function loginTokenHash(raw) {
  return crypto.createHash('sha256').update(String(raw)).digest('hex');
}

/** Cheap per-user guard so a looping client cannot fill Firestore with token rows. */
function accountTokenAllowed(uid) {
  const now = Date.now();
  const hits = (accountTokenHits.get(uid) || []).filter((t) => now - t < ACCOUNT_TOKEN_WINDOW_MS);
  if (hits.length >= ACCOUNT_TOKEN_MAX_PER_WINDOW) {
    accountTokenHits.set(uid, hits);
    return false;
  }
  hits.push(now);
  accountTokenHits.set(uid, hits);
  return true;
}

setInterval(() => {
  const now = Date.now();
  for (const [uid, hits] of accountTokenHits) {
    const fresh = hits.filter((t) => now - t < ACCOUNT_TOKEN_WINDOW_MS);
    if (fresh.length) accountTokenHits.set(uid, fresh);
    else accountTokenHits.delete(uid);
  }
}, ACCOUNT_TOKEN_WINDOW_MS).unref();

/** The decoded ID token behind an `Authorization: Bearer` header, or null. */
async function verifyBearer(req) {
  const idToken = bearer(req);
  if (!idToken) return null;
  try {
    return await auth().verifyIdToken(idToken);
  } catch {
    return null;
  }
}

/**
 * POST /account/token — mint a long-lived account token for whoever is signed in now.
 *
 * Identity comes from the Firebase ID token (same as /push/send), so this can only ever
 * mint a token for the account that already holds the session.
 */
app.post('/account/token', async (req, res) => {
  if (!serviceAccount) return res.status(503).json({ error: 'UNAVAILABLE' });
  const decoded = await verifyBearer(req);
  if (!decoded) return res.status(401).json({ error: 'UNAUTHENTICATED' });
  if (!accountTokenAllowed(decoded.uid)) {
    return res.status(429).json({ error: 'RATE_LIMITED', message: 'Please try again in a little while.' });
  }
  const accountToken = crypto.randomBytes(32).toString('hex');
  await db().collection('loginTokens').doc(loginTokenHash(accountToken)).set({
    uid: decoded.uid,
    phone: String(decoded.phone_number || ''),
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    expiresAt: Date.now() + LOGIN_TOKEN_TTL_MS
  });
  res.json({ ok: true, accountToken });
});

/**
 * POST /account/switch — trade a stored account token for a fresh Firebase custom token.
 * This is what makes switching instant: no code, no SMS.
 */
app.post('/account/switch', async (req, res) => {
  if (!serviceAccount) return res.status(503).json({ error: 'UNAVAILABLE' });
  const raw = String(req.body.accountToken || '').trim();
  const invalid = (message) => ({ error: 'ACCOUNT_TOKEN_INVALID', message });
  if (!raw || raw.length > 200) {
    return res.status(400).json(invalid('Sign in to this account again.'));
  }
  const ref = db().collection('loginTokens').doc(loginTokenHash(raw));
  const snap = await ref.get();
  const doc = snap.exists ? snap.data() || {} : null;
  const expired = !doc || (doc.expiresAt && Number(doc.expiresAt) <= Date.now());
  if (expired) {
    if (doc) await ref.delete().catch(() => {});
    return res.status(401).json(invalid('Sign in to this account again.'));
  }
  const uid = String(doc.uid || '');
  if (!uid) return res.status(401).json(invalid('Sign in to this account again.'));

  const token = await auth().createCustomToken(uid);
  await ref.update({ lastUsedAt: admin.firestore.FieldValue.serverTimestamp() }).catch(() => {});
  res.json({ ok: true, token, uid, phone: String(doc.phone || '') });
});

/**
 * POST /account/revoke — "remove this account from the phone". Deletes the account token
 * and takes this device's FCM token off that account, so a phone that no longer lists an
 * account stops being notified for it.
 */
app.post('/account/revoke', async (req, res) => {
  if (!serviceAccount) return res.status(503).json({ error: 'UNAVAILABLE' });
  const raw = String(req.body.accountToken || '').trim();
  if (!raw || raw.length > 200) return res.status(400).json({ error: 'ACCOUNT_TOKEN_INVALID' });
  const ref = db().collection('loginTokens').doc(loginTokenHash(raw));
  const snap = await ref.get();
  if (!snap.exists) return res.json({ ok: true });
  const doc = snap.data() || {};
  const uid = String(doc.uid || '');
  const fcmToken = String(req.body.fcmToken || '').trim();
  await ref.delete().catch(() => {});
  if (uid && fcmToken) {
    await db().collection('users').doc(uid)
      .update({ fcmTokens: admin.firestore.FieldValue.arrayRemove(fcmToken) })
      .catch(() => {});
  }
  res.json({ ok: true });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`HolidayLink OTP service listening on 0.0.0.0:${PORT}`);
});
