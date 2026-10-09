import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import fs from 'fs/promises';
import pg from 'pg';
import { WebSocketServer } from 'ws';
import { registerFeatures } from './features.js';

const { Pool } = pg;

const app = express();
app.disable('x-powered-by');
// Render / reverse-proxy: needed so req.protocol reflects https via X-Forwarded-Proto,
// otherwise avatar/video /uploads URLs are built as http:// and break on device.
app.set('trust proxy', 1);
app.use(cors());
// 32mb so a 20 MB file still fits once base64 inflates it (~1.37x) inside one JSON request.
app.use(express.json({ limit: '150mb' }));

const PORT = Number(process.env.PORT || 8080);
const JWT_SECRET = process.env.JWT_SECRET || 'change-me';
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: Number(process.env.PG_POOL_MAX || 10),
  connectionTimeoutMillis: Number(process.env.PG_CONNECT_TIMEOUT_MS || 8000),
  ssl: process.env.PGSSL_DISABLE === 'true' ? false : (process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : undefined)
});

pool.on('error', err => console.error('[pg] idle client error:', err.message));

if (!process.env.JWT_SECRET) console.warn('[config] JWT_SECRET is not set - using an insecure development secret.');
if (!process.env.JWT_SECRET || process.env.JWT_SECRET.startsWith('replace-this')) {
  console.warn('[config] JWT_SECRET still uses the placeholder value. Generate a real one before going live.');
}
// A short secret is guessable: anyone who can reach this port could sign a token for any user
// id and sign in as them. Local development is the only place this is acceptable.
if (process.env.JWT_SECRET && process.env.JWT_SECRET.length < 32) {
  console.warn('[config] JWT_SECRET is shorter than 32 characters. It is brute-forceable - replace it before deploying.');
}
if (!process.env.DATABASE_URL) console.warn('[config] DATABASE_URL is not set - PostgreSQL connections will fail.');

// Country code used when a client sends a national-format number without a "+" prefix.
const DEFAULT_COUNTRY_CODE = String(process.env.DEFAULT_COUNTRY_CODE || '256').replace(/\D/g, '') || '256';
const OTP_TTL_MINUTES = Math.max(1, Number(process.env.OTP_TTL_MINUTES || 5));
const OTP_COOLDOWN_SECONDS = Math.max(0, Number(process.env.OTP_COOLDOWN_SECONDS || 45));
const OTP_MAX_ATTEMPTS = Math.max(3, Number(process.env.OTP_MAX_ATTEMPTS || 5));
const OTP_ATTEMPT_WINDOW_MINUTES = Math.max(1, Number(process.env.OTP_ATTEMPT_WINDOW_MINUTES || 15));

// In-memory abuse protection. Deliberately not persisted so the OTP table schema stays unchanged.
const resendCooldown = new Map();
const failedAttempts = new Map();

setInterval(() => {
  const now = Date.now();
  for (const [phone, at] of resendCooldown) {
    if (now - at > OTP_COOLDOWN_SECONDS * 1000) resendCooldown.delete(phone);
  }
  for (const [phone, entry] of failedAttempts) {
    if (now - entry.firstAt > OTP_ATTEMPT_WINDOW_MINUTES * 60 * 1000) failedAttempts.delete(phone);
  }
}, 60_000).unref();

function normalizePhone(value) {
  let raw = String(value == null ? '' : value).trim().replace(/[\s\-().]/g, '');
  if (!raw) return '';
  if (raw.startsWith('00')) raw = '+' + raw.slice(2);
  if (raw.startsWith('+')) return '+' + raw.slice(1).replace(/\D/g, '');

  let digits = raw.replace(/\D/g, '');
  if (!digits) return '';
  // "256745473752" - country code typed without a plus sign.
  if (digits.startsWith(DEFAULT_COUNTRY_CODE) && digits.length > DEFAULT_COUNTRY_CODE.length + 6) {
    return '+' + digits;
  }
  // "0745473752" - national format with trunk zero.
  digits = digits.replace(/^0+/, '');
  if (!digits) return '';
  return '+' + DEFAULT_COUNTRY_CODE + digits;
}

function isValidPhone(phone) {
  return /^\+[1-9]\d{6,14}$/.test(phone);
}

function cooldownRemainingMs(phone) {
  const at = resendCooldown.get(phone);
  if (!at) return 0;
  return Math.max(0, at + OTP_COOLDOWN_SECONDS * 1000 - Date.now());
}

function registerFailedAttempt(phone) {
  const now = Date.now();
  const entry = failedAttempts.get(phone);
  if (!entry || now - entry.firstAt > OTP_ATTEMPT_WINDOW_MINUTES * 60 * 1000) {
    failedAttempts.set(phone, { count: 1, firstAt: now });
    return 1;
  }
  entry.count += 1;
  return entry.count;
}

function clearFailedAttempts(phone) {
  failedAttempts.set(phone, { count: 0, firstAt: Date.now() });
}

function isDatabaseError(err) {
  const code = String(err?.code || '');
  return code.startsWith('28') || code === 'ECONNREFUSED' || code === 'ENOTFOUND' || code === 'ETIMEDOUT' || err?.name === 'AggregateError';
}

function fail(res, err, context) {
  console.error(`[${context}]`, err && err.message ? err.message : err);
  if (isDatabaseError(err)) {
    return res.status(503).json({
      error: 'DATABASE_UNAVAILABLE',
      message: 'HolidayLink is temporarily unavailable. Please try again shortly.'
    });
  }
  return res.status(500).json({
    error: 'INTERNAL_ERROR',
    message: 'Something went wrong on our side. Please try again.'
  });
}

function phoneHash(phone) {
  return crypto.createHash('sha256').update(phone).digest('hex');
}

function signUser(user) {
  const sessionKey = crypto.randomBytes(24).toString('hex');
  return {
    token: jwt.sign(
      { sub: user.id, username: user.username, sid: sessionKey },
      JWT_SECRET,
      { expiresIn: '30d' }
    ),
    sessionKey
  };
}

/**
 * Records this sign-in so the device can later be listed and revoked from Settings. A failure
 * here must never block login, so the insert is best effort.
 */
async function recordSession(user, sessionKey, req) {
  try {
    const label = String(req.get('user-agent') || '').slice(0, 120) || 'Unknown device';
    await db(
      `INSERT INTO user_sessions(session_key,user_id,label,platform,last_seen_at)
       VALUES($1,$2,$3,'android',NOW())
       ON CONFLICT (session_key) DO UPDATE SET last_seen_at=NOW()`,
      [sessionKey, user.id, label]
    );
  } catch (err) {
    console.warn('[session] could not record sign-in:', err.message);
  }
}

function auth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  let claims;
  try {
    claims = jwt.verify(token, JWT_SECRET);
  } catch {
    return res.status(401).json({ error: 'UNAUTHORIZED' });
  }
  req.user = claims;
  // A session revoked from Settings must stop working immediately, not when the token expires.
  // Banned accounts are rejected everywhere too, so a website-admin ban takes effect at once.
  // Skipped when the tables are unavailable so the app still runs on an older database.
  const checkSession = claims.sid
    ? db('SELECT revoked FROM user_sessions WHERE session_key=$1', [claims.sid])
    : Promise.resolve(null);
  Promise.all([
    checkSession,
    db('SELECT banned, ban_reason FROM users WHERE id=$1', [claims.sub]).catch(() => null)
  ]).then(([session, user]) => {
    if (session && session.rowCount && session.rows[0].revoked) {
      return res.status(401).json({
        error: 'UNAUTHORIZED',
        message: 'This device was signed out.'
      });
    }
    if (user && user.rowCount && user.rows[0].banned) {
      return res.status(403).json({
        error: 'ACCOUNT_BANNED',
        message: user.rows[0].ban_reason
          ? `This account was banned: ${user.rows[0].ban_reason}`
          : 'This account was banned.'
      });
    }
    next();
  }).catch(() => next());
}

async function db(text, params = []) {
  const result = await pool.query(text, params);
  return result;
}

async function runDatabaseMigrations() {
  if (!process.env.DATABASE_URL) return;
  const root = new URL('../../', import.meta.url);
  const schema = await fs.readFile(new URL('database/schema.sql', root), 'utf8');
  const featureMigration = await fs.readFile(new URL('database/migrations/002_features.sql', root), 'utf8');
  const authVideoMigration = await fs.readFile(new URL('database/migrations/003_auth_videos.sql', root), 'utf8');
  const e2eeMigration = await fs.readFile(new URL('database/migrations/004_e2ee_and_calls.sql', root), 'utf8');
  const channelsMigration = await fs.readFile(new URL('database/migrations/005_channels_saves.sql', root), 'utf8');
  const viewonceMigration = await fs.readFile(new URL('database/migrations/006_viewonce_bans.sql', root), 'utf8');
  const botsMigration = await fs.readFile(new URL('database/migrations/007_ai_bots.sql', root), 'utf8');
  await pool.query(schema);
  await pool.query(featureMigration);
  await pool.query(authVideoMigration);
  await pool.query(e2eeMigration);
  await pool.query(channelsMigration);
  await pool.query(viewonceMigration);
  await pool.query(botsMigration);
  console.log('[db] schema and feature migrations are ready');
}

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

app.get('/health', async (_req, res) => {
  const startedAt = Date.now();
  try {
    await db('SELECT 1');
    res.json({
      ok: true,
      service: 'holidaylink-server',
      database: 'ok',
      databaseLatencyMs: Date.now() - startedAt,
      uptimeSeconds: Math.round(process.uptime()),
      smsProviderConfigured: Boolean(process.env.INFOBIP_BASE_URL && process.env.INFOBIP_API_KEY),
      defaultCountryCode: DEFAULT_COUNTRY_CODE,
      otpTtlMinutes: OTP_TTL_MINUTES
    });
  } catch (err) {
    console.error('[health] database check failed:', err.message);
    res.status(503).json({
      ok: false,
      service: 'holidaylink-server',
      database: 'unavailable',
      message: 'Database is unavailable'
    });
  }
});

app.post('/auth/check-phone', async (req,res)=>{
  const phone=normalizePhone(req.body.phone);
  if(!isValidPhone(phone)) return res.status(400).json({error:'INVALID_PHONE',message:'Enter a valid phone number including your country code.'});
  try { const r=await db('SELECT id,two_factor_enabled FROM users WHERE phone=$1',[phone]); res.json({exists:Boolean(r.rowCount),twoFactorEnabled:Boolean(r.rows[0]?.two_factor_enabled),phone}); }
  catch(err){ return fail(res,err,'check-phone'); }
});

app.post('/auth/request-otp', async (req, res) => {
  const phone = normalizePhone(req.body.phone);
  if (!isValidPhone(phone)) {
    return res.status(400).json({
      error: 'INVALID_PHONE',
      message: 'Enter a valid phone number including your country code.'
    });
  }

  const remainingMs = cooldownRemainingMs(phone);
  if (remainingMs > 0) {
    return res.status(429).json({
      error: 'OTP_COOLDOWN',
      message: 'A code was just sent to this number. Please wait before requesting another one.',
      retryAfterSeconds: Math.ceil(remainingMs / 1000)
    });
  }

  let sent;
  try {
    const existing = await db('SELECT id FROM users WHERE phone=$1', [phone]);

    // Generate the OTP locally. Only its bcrypt hash is stored in PostgreSQL.
    const pin = String(crypto.randomInt(100000, 1000000));
    const pinHash = await bcrypt.hash(pin, 10);

    try {
      sent = await infobip('/sms/3/messages', {
        method: 'POST',
        body: JSON.stringify({
          messages: [{
            sender: (process.env.INFOBIP_FROM || 'ServiceSMS').trim(),
            destinations: [{ to: phone.replace('+', '') }],
            content: {
              text: `Your HolidayLink verification code is ${pin}. It expires in ${OTP_TTL_MINUTES} minutes.`
            }
          }]
        })
      });
    } catch (err) {
      console.error('[request-otp] sms provider error:', err.status || '', err.message);
      return res.status(502).json({
        error: 'OTP_SEND_FAILED',
        message: 'We could not send the verification code right now. Please try again.'
      });
    }

    // Supersede any earlier unused code for this number so only the newest SMS works.
    await db('UPDATE auth_sessions SET consumed=true WHERE phone=$1 AND consumed=false', [phone]);
    await db(
      `INSERT INTO auth_sessions(phone,pin_id,expires_at)
       VALUES($1,$2,NOW()+($3::int * INTERVAL '1 minute'))`,
      [phone, pinHash, String(OTP_TTL_MINUTES)]
    );

    resendCooldown.set(phone, Date.now());
    clearFailedAttempts(phone);

    res.json({
      ok: true,
      exists: existing.rowCount > 0,
      phone,
      expiresInSeconds: OTP_TTL_MINUTES * 60,
      resendAfterSeconds: OTP_COOLDOWN_SECONDS,
      messageId: sent?.messages?.[0]?.messageId || null
    });
  } catch (err) {
    return fail(res, err, 'request-otp');
  }
});

app.post('/auth/verify-otp', async (req, res) => {
  const phone = normalizePhone(req.body.phone);
  // Accept pin/code/otp and ignore spaces or dashes pasted from the SMS.
  const pin = String(req.body.pin ?? req.body.code ?? req.body.otp ?? '').replace(/\D/g, '');

  if (!isValidPhone(phone)) {
    return res.status(400).json({
      error: 'INVALID_PHONE',
      message: 'Enter a valid phone number including your country code.'
    });
  }

  if (!/^\d{6}$/.test(pin)) {
    return res.status(400).json({
      error: 'INVALID_OTP',
      message: 'That code is incorrect or has expired. Check the SMS and try again.'
    });
  }

  const prior = failedAttempts.get(phone);
  if (prior && prior.count >= OTP_MAX_ATTEMPTS) {
    return res.status(429).json({
      error: 'TOO_MANY_ATTEMPTS',
      message: 'Too many incorrect attempts. Request a new code and wait for the SMS.'
    });
  }

  try {
    const session = await db(
      `SELECT * FROM auth_sessions
       WHERE phone=$1 AND consumed=false AND expires_at>NOW()
       ORDER BY created_at DESC LIMIT 1`,
      [phone]
    );

    if (!session.rowCount) {
      return res.status(400).json({
        error: 'OTP_SESSION_EXPIRED',
        message: 'That code has expired. Request a new code.'
      });
    }

    const valid = await bcrypt.compare(pin, session.rows[0].pin_id);
    if (!valid) {
      const used = registerFailedAttempt(phone);
      console.warn(`[verify-otp] wrong code for ${phone.slice(0, 4)}*** (attempt ${used})`);
      return res.status(400).json({
        error: 'INVALID_OTP',
        message: 'That code is incorrect or has expired. Check the SMS and try again.',
        attemptsRemaining: Math.max(0, OTP_MAX_ATTEMPTS - used)
      });
    }

    // One-time use: consume the session immediately on success.
    await db('UPDATE auth_sessions SET consumed=true WHERE id=$1', [session.rows[0].id]);
    clearFailedAttempts(phone);

    const existing = await db(
      'SELECT id,phone,email,display_name,username,avatar_url,bio,banned,ban_reason FROM users WHERE phone=$1',
      [phone]
    );

    if (existing.rowCount) {
      const user = existing.rows[0];
      if (user.banned) {
        return res.status(403).json({
          error: 'ACCOUNT_BANNED',
          message: user.ban_reason ? `This account was banned: ${user.ban_reason}` : 'This account was banned.'
        });
      }
      const { token, sessionKey } = signUser(user);
      await db(
        'UPDATE users SET online=true,last_seen=NOW(),updated_at=NOW() WHERE id=$1',
        [user.id]
      );
      await recordSession(user, sessionKey, req);
      return res.json({ ok: true, existing: true, needsProfile: false, token, user });
    }

    res.json({ ok: true, existing: false, needsProfile: true, phone });
  } catch (err) {
    return fail(res, err, 'verify-otp');
  }
});


app.post('/auth/login-2fa', async (req, res) => {
  try {
    const phone = normalizePhone(req.body.phone);
    const password = String(req.body.password || '');
    if (!isValidPhone(phone) || password.length < 8) {
      return res.status(400).json({ error: 'INVALID_2FA_PASSWORD', message: 'Enter your phone number and 2FA password.' });
    }
    const r = await db(
      `SELECT id,phone,email,display_name,username,avatar_url,bio,two_factor_enabled,two_factor_password_hash,banned,ban_reason
       FROM users WHERE phone=$1`, [phone]
    );
    if (!r.rowCount || !r.rows[0].two_factor_enabled || !r.rows[0].two_factor_password_hash) {
      return res.status(401).json({ error: '2FA_NOT_ENABLED', message: '2FA password login is not enabled for this number.' });
    }
    if (r.rows[0].banned) {
      return res.status(403).json({
        error: 'ACCOUNT_BANNED',
        message: r.rows[0].ban_reason ? `This account was banned: ${r.rows[0].ban_reason}` : 'This account was banned.'
      });
    }
    const valid = await bcrypt.compare(password, r.rows[0].two_factor_password_hash);
    if (!valid) return res.status(401).json({ error: 'INVALID_2FA_PASSWORD', message: 'That 2FA password is incorrect.' });
    const row = r.rows[0];
    // Never return the bcrypt hash to the Android client. Only public profile fields belong in the token response.
    const user = {
      id: row.id, phone: row.phone, email: row.email, display_name: row.display_name,
      username: row.username, avatar_url: row.avatar_url, bio: row.bio,
      two_factor_enabled: row.two_factor_enabled
    };
    const { token, sessionKey } = signUser(user);
    await db('UPDATE users SET online=true,last_seen=NOW(),updated_at=NOW() WHERE id=$1', [user.id]);
    await recordSession(user, sessionKey, req);
    res.json({ ok: true, token, user });
  } catch (err) { return fail(res, err, 'login-2fa'); }
});

app.get('/auth/2fa/status', auth, async (req, res) => {
  try {
    const r = await db('SELECT two_factor_enabled,two_factor_updated_at FROM users WHERE id=$1', [req.user.sub]);
    if (!r.rowCount) return res.status(404).json({ error: 'NOT_FOUND', message: 'Account not found.' });
    res.json({ ok: true, enabled: Boolean(r.rows[0].two_factor_enabled), updatedAt: r.rows[0].two_factor_updated_at });
  } catch (err) { return fail(res, err, '2fa-status'); }
});

app.post('/auth/2fa/password', auth, async (req, res) => {
  try {
    const password = String(req.body.password || '');
    if (password.length < 8 || password.length > 128) {
      return res.status(400).json({ error: 'INVALID_2FA_PASSWORD', message: 'Use a password between 8 and 128 characters.' });
    }
    const hash = await bcrypt.hash(password, 12);
    await db('UPDATE users SET two_factor_enabled=true,two_factor_password_hash=$1,two_factor_updated_at=NOW(),updated_at=NOW() WHERE id=$2', [hash, req.user.sub]);
    res.json({ ok: true, enabled: true });
  } catch (err) { return fail(res, err, 'set-2fa'); }
});

app.post('/auth/2fa/password/disable', auth, async (req, res) => {
  try {
    await db('UPDATE users SET two_factor_enabled=false,two_factor_password_hash=NULL,two_factor_updated_at=NOW(),updated_at=NOW() WHERE id=$1', [req.user.sub]);
    res.json({ ok: true, enabled: false });
  } catch (err) { return fail(res, err, 'disable-2fa'); }
});

app.post('/auth/complete-profile', async (req, res) => {
  try {
    const phone = normalizePhone(req.body.phone);
    const displayName = String(req.body.displayName || '').trim();
    const username = String(req.body.username || '').trim().replace(/^@/, '').toLowerCase();
    const email = String(req.body.email || '').trim() || null;
    const twoFactorPassword = String(req.body.twoFactorPassword || '').trim();

    if (!isValidPhone(phone)) return res.status(400).json({ error: 'INVALID_PHONE', message: 'Enter a valid phone number including your country code.' });
    if (!displayName || displayName.length > 120) return res.status(400).json({ error: 'INVALID_DISPLAY_NAME', message: 'Enter a display name (up to 120 characters).' });
    if (!/^[a-z0-9_]{3,32}$/.test(username)) return res.status(400).json({ error: 'INVALID_USERNAME', message: 'Username must be 3-32 characters using letters, numbers or underscore.' });

    const exists = await db('SELECT id FROM users WHERE phone=$1 OR LOWER(username)=LOWER($2)', [phone, username]);
    if (exists.rowCount) return res.status(409).json({ error: 'PHONE_OR_USERNAME_ALREADY_REGISTERED', message: 'That phone number or username is already registered.' });

    const twoFactorHash = twoFactorPassword ? await bcrypt.hash(twoFactorPassword, 12) : null;
    const result = await db(
      `INSERT INTO users(phone,phone_hash,email,display_name,username,online,last_seen,two_factor_enabled,two_factor_password_hash,two_factor_updated_at)
       VALUES($1,$2,$3,$4,$5,true,NOW(),$6,$7,CASE WHEN $6 THEN NOW() ELSE NULL END)
       RETURNING id,phone,email,display_name,username,avatar_url,bio,two_factor_enabled`,
      [phone, phoneHash(phone), email, displayName, username, Boolean(twoFactorHash), twoFactorHash]
    );

    const user = result.rows[0];
    const { token, sessionKey } = signUser(user);
    await recordSession(user, sessionKey, req);
    res.json({ ok: true, token, user });
  } catch (e) {
    return fail(res, e, 'complete-profile');
  }
});

app.get('/me', auth, async (req, res) => {
  const r = await db(
    `SELECT id,phone,email,display_name,username,avatar_url,bio,online,last_seen
     FROM users WHERE id=$1`,
    [req.user.sub]
  );
  if (!r.rowCount) return res.status(404).json({ error: 'NOT_FOUND' });
  res.json(r.rows[0]);
});

app.get('/users', auth, async (req, res) => {
  const q = String(req.query.q || '').trim();
  const params = [req.user.sub];
  // A block in either direction hides the person, and privacy rules decide which of the
  // remaining fields are allowed out at all.
  let sql = `SELECT u.id,u.display_name,u.username,u.avatar_url,u.bio,u.online,u.last_seen,
                    p.profile_photo AS profile_photo_rule,
                    p.about AS about_rule,
                    p.last_seen AS last_seen_rule
             FROM users u
             LEFT JOIN user_privacy p ON p.user_id=u.id
             WHERE u.id<>$1
               AND NOT EXISTS (
                 SELECT 1 FROM user_blocks b
                 WHERE (b.blocker_id=$1 AND b.blocked_id=u.id)
                    OR (b.blocker_id=u.id AND b.blocked_id=$1)
               )`;
  if (q) {
    params.push(`%${q}%`);
    sql += ` AND (LOWER(u.display_name) LIKE LOWER($${params.length})
              OR LOWER(u.username) LIKE LOWER($${params.length}))`;
  }
  sql += ` ORDER BY u.online DESC, u.display_name ASC LIMIT 200`;
  const r = await db(sql, params);

  // "My contacts" means the two of you already have a direct chat, matching every other rule.
  const shared = new Set();
  if (r.rows.length) {
    const connected = await db(
      `SELECT DISTINCT b.user_id FROM conversation_members a
       JOIN conversation_members b ON b.conversation_id=a.conversation_id
       JOIN conversations c ON c.id=a.conversation_id
       WHERE a.user_id=$1 AND b.user_id = ANY($2::uuid[]) AND c.kind='direct'`,
      [req.user.sub, r.rows.map(row => row.id)]
    );
    for (const row of connected.rows) shared.add(row.user_id);
  }
  // A missing row means the account never changed its defaults, which are the most permissive.
  const visible = (rule, userId) => {
    const value = rule == null ? 'everyone' : rule;
    if (value === 'nobody') return false;
    if (value === 'contacts') return shared.has(userId);
    return true;
  };

  res.json(
    r.rows.map(row => ({
      id: row.id,
      display_name: row.display_name,
      username: row.username,
      avatar_url: visible(row.profile_photo_rule, row.id) ? row.avatar_url : null,
      bio: visible(row.about_rule, row.id) ? row.bio : null,
      online: visible(row.last_seen_rule, row.id) ? row.online : null,
      last_seen: visible(row.last_seen_rule, row.id) ? row.last_seen : null
    }))
  );
});



app.get('/conversations', auth, async (req, res) => {
  const r = await db(
    `SELECT c.id,c.kind,c.title,c.avatar_url,c.created_at,
       (
         SELECT json_build_object(
           'id',m.id,'body',m.body,'senderId',m.sender_id,'createdAt',m.created_at,'type',m.message_type
         )
         FROM messages m
         WHERE m.conversation_id=c.id AND m.deleted_at IS NULL
         ORDER BY m.created_at DESC LIMIT 1
       ) AS last_message,
       CASE WHEN c.kind='direct' THEN (
         SELECT json_build_object(
           'id',u.id,'displayName',u.display_name,'username',u.username,
'avatarUrl',CASE WHEN COALESCE(pp.profile_photo,'everyone')<>'nobody'
                             THEN u.avatar_url ELSE NULL END,
            'online',CASE WHEN COALESCE(pp.last_seen,'everyone')<>'nobody'
                          THEN u.online ELSE NULL END)
         FROM conversation_members cm2
         JOIN users u ON u.id=cm2.user_id
         LEFT JOIN user_privacy pp ON pp.user_id=u.id
         WHERE cm2.conversation_id=c.id AND cm2.user_id<>$1 LIMIT 1
       ) ELSE NULL END AS peer,
       COALESCE(s.pinned,false) AS pinned,
       COALESCE(s.archived,false) AS archived,
       (
         SELECT COUNT(*)::int FROM messages m2
         WHERE m2.conversation_id=c.id AND m2.deleted_at IS NULL AND m2.sender_id<>$1
           AND m2.created_at>COALESCE(
             (SELECT MAX(r.read_at) FROM message_receipts r
              WHERE r.user_id=$1 AND r.message_id IN
                (SELECT id FROM messages WHERE conversation_id=c.id)),
             '-infinity'::timestamptz)
       ) AS unread_count
     FROM conversations c
     JOIN conversation_members cm ON cm.conversation_id=c.id
     LEFT JOIN conversation_settings s ON s.conversation_id=c.id AND s.user_id=$1
     WHERE cm.user_id=$1 AND COALESCE(s.archived,false)=false
     ORDER BY COALESCE(s.pinned,false) DESC,
              COALESCE((SELECT MAX(m2.created_at) FROM messages m2 WHERE m2.conversation_id=c.id),c.created_at) DESC`,
    [req.user.sub]
  );
  res.json(r.rows);
});

app.post('/conversations/direct', auth, async (req, res) => {
  const targetId = String(req.body.userId || '');
  if (!targetId || targetId === req.user.sub) return res.status(400).json({ error: 'INVALID_TARGET' });

  const user = await db('SELECT id FROM users WHERE id=$1', [targetId]);
  if (!user.rowCount) return res.status(404).json({ error: 'USER_NOT_FOUND' });

  const blocked = await db(
    `SELECT 1 FROM user_blocks
     WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1`,
    [req.user.sub, targetId]
  );
  if (blocked.rowCount) {
    return res.status(403).json({ error: 'USER_BLOCKED', message: "You can't message this person." });
  }

  const existing = await db(
    `SELECT c.id FROM conversations c
     JOIN conversation_members a ON a.conversation_id=c.id AND a.user_id=$1
     JOIN conversation_members b ON b.conversation_id=c.id AND b.user_id=$2
     WHERE c.kind='direct'
     LIMIT 1`,
    [req.user.sub, targetId]
  );
  if (existing.rowCount) return res.json({ id: existing.rows[0].id });

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const c = await client.query(
      `INSERT INTO conversations(kind,created_by) VALUES('direct',$1) RETURNING id`,
      [req.user.sub]
    );
    await client.query(
      `INSERT INTO conversation_members(conversation_id,user_id,role)
       VALUES($1,$2,'member'),($1,$3,'member')`,
      [c.rows[0].id, req.user.sub, targetId]
    );
    await client.query('COMMIT');
    res.json({ id: c.rows[0].id });
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
});

app.post('/conversations/group', auth, async (req, res) => {
  const title = String(req.body.title || '').trim();
  const memberIds = Array.isArray(req.body.memberIds) ? req.body.memberIds : [];
  const unique = [...new Set([req.user.sub, ...memberIds.map(String)])];
  if (!title || unique.length < 1 || unique.length > 200) return res.status(400).json({ error: 'INVALID_GROUP' });

  // Nobody may be added to a group they haven't allowed, and a block always wins.
  const invited = unique.filter(id => id !== req.user.sub);
  if (invited.length) {
    const prefs = await db(
      'SELECT user_id,group_invites FROM user_privacy WHERE user_id = ANY($1::uuid[])',
      [invited]
    );
    const rules = new Map(prefs.rows.map(row => [row.user_id, row.group_invites || 'everyone']));
    // "Contacts" means a direct chat already exists, the same rule privacy uses everywhere else.
    const connected = await db(
      `SELECT DISTINCT b.user_id FROM conversation_members a
       JOIN conversation_members b ON b.conversation_id=a.conversation_id
       JOIN conversations c ON c.id=a.conversation_id
       WHERE a.user_id=$1 AND b.user_id = ANY($2::uuid[]) AND c.kind='direct'`,
      [req.user.sub, invited]
    );
    const shared = new Set(connected.rows.map(row => row.user_id));
    for (const id of invited) {
      const rule = rules.get(id) || 'everyone';
      if (rule === 'nobody' || (rule === 'contacts' && !shared.has(id))) {
        return res.status(403).json({
          error: 'GROUP_INVITES_DISABLED',
          message: "One of the people you picked can't be added to a group."
        });
      }
    }
    const blocked = await db(
      `SELECT 1 FROM user_blocks
       WHERE (blocker_id=$1 AND blocked_id = ANY($2::uuid[]))
          OR (blocked_id=$1 AND blocker_id = ANY($2::uuid[])) LIMIT 1`,
      [req.user.sub, invited]
    );
    if (blocked.rowCount) {
      return res.status(403).json({ error: 'USER_BLOCKED', message: "You can't add that person." });
    }
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const c = await client.query(
      `INSERT INTO conversations(kind,title,created_by) VALUES('group',$1,$2) RETURNING id`,
      [title, req.user.sub]
    );
    for (const id of unique) {
      await client.query(
        `INSERT INTO conversation_members(conversation_id,user_id,role)
         VALUES($1,$2,$3)`,
        [c.rows[0].id, id, id === req.user.sub ? 'owner' : 'member']
      );
    }
    await client.query('COMMIT');
    res.json({ id: c.rows[0].id });
  } catch (e) {
    await client.query('ROLLBACK');
    res.status(500).json({ error: 'GROUP_CREATE_FAILED' });
  } finally {
    client.release();
  }
});

// Channels (broadcast) + communities (group of groups) reuse conversations/members.
// Channels: only owner/admin may post (enforced in features.js message send).
// Communities: behave like groups; members can post.
async function createCollectionConversation(req, res, kind) {
  const title = String(req.body.title || '').trim();
  const memberIds = Array.isArray(req.body.memberIds) ? req.body.memberIds : [];
  const unique = [...new Set([req.user.sub, ...memberIds.map(String)])];
  if (!title || unique.length < 1 || unique.length > 500) return res.status(400).json({ error: 'INVALID_TITLE' });
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const c = await client.query(
      'INSERT INTO conversations(kind,title,created_by) VALUES($1,$2,$3) RETURNING id',
      [kind, title, req.user.sub]
    );
    for (const id of unique) {
      await client.query(
        'INSERT INTO conversation_members(conversation_id,user_id,role) VALUES($1,$2,$3)',
        [c.rows[0].id, id, id === req.user.sub ? 'owner' : 'member']
      );
    }
    await client.query('COMMIT');
    res.json({ id: c.rows[0].id });
  } catch (e) {
    await client.query('ROLLBACK');
    res.status(500).json({ error: 'CREATE_FAILED' });
  } finally {
    client.release();
  }
}

app.post('/conversations/channel', auth, async (req, res) => createCollectionConversation(req, res, 'channel'));
app.post('/conversations/community', auth, async (req, res) => createCollectionConversation(req, res, 'community'));

// Message history, sending and editing live in features.js so attachment and reaction
// handling stays in one place.

app.get('/notifications', auth, async (req, res) => {
  const r = await db(
    `SELECT id,type,title,body,data,created_at,read_at
     FROM notifications WHERE user_id=$1
     ORDER BY created_at DESC LIMIT 100`,
    [req.user.sub]
  );
  // Api.kt reads a boolean `read`, so the nullable timestamp is normalised here.
  res.json(
    r.rows.map(row => ({
      id: row.id,
      type: row.type,
      title: row.title,
      body: row.body,
      data: row.data,
      read: row.read_at !== null,
      created_at: row.created_at
    }))
  );
});

// Marking notifications read lives in features.js, next to notification settings.

app.get('/store/items', auth, async (_req, res) => {
  const r = await db(
    `SELECT id,title,description,price_minor,currency,image_url
     FROM store_items WHERE active=true ORDER BY created_at DESC`
  );
  res.json(r.rows);
});

app.post('/calls', auth, async (req, res) => {
  const calleeId = String(req.body.calleeId || '');
  const kind = req.body.kind === 'video' ? 'video' : 'audio';
  if (calleeId === req.user.sub) return res.status(400).json({ error: 'INVALID_TARGET' });

  const target = await db('SELECT id FROM users WHERE id=$1', [calleeId]);
  if (!target.rowCount) return res.status(404).json({ error: 'USER_NOT_FOUND' });

  // A block in either direction must stop the call from being placed.
  const blocked = await db(
    `SELECT 1 FROM user_blocks
     WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1`,
    [req.user.sub, calleeId]
  );
  if (blocked.rowCount) {
    return res.status(403).json({
      error: 'USER_BLOCKED',
      message: "You can't call this person."
    });
  }

  // Respect the callee's own "Who can call me" preference.
  const privacy = await db('SELECT calls FROM user_privacy WHERE user_id=$1', [calleeId]);
  const rule = privacy.rowCount ? privacy.rows[0].calls : 'everyone';
  if (rule === 'nobody') {
    return res.status(403).json({ error: 'CALLS_DISABLED', message: "This person isn't accepting calls." });
  }
  if (rule === 'contacts') {
    // "Contacts" is always a direct chat, the same definition privacy uses everywhere else.
    const connected = await db(
      `SELECT 1 FROM conversation_members a
       JOIN conversation_members b ON b.conversation_id=a.conversation_id
       JOIN conversations c ON c.id=a.conversation_id
       WHERE a.user_id=$1 AND b.user_id=$2 AND c.kind='direct' LIMIT 1`,
      [req.user.sub, calleeId]
    );
    if (!connected.rowCount) {
      return res.status(403).json({
        error: 'CALLS_NOT_ALLOWED',
        message: 'You can only call people you have chatted with.'
      });
    }
  }
  const online = await db('SELECT online FROM users WHERE id=$1', [calleeId]);
  const initialStatus = online.rowCount && online.rows[0].online ? 'ringing' : 'calling';
  const r = await db(
    `INSERT INTO calls(caller_id,callee_id,kind,status) VALUES($1,$2,$3,$4)
     RETURNING id,caller_id,callee_id,kind,status,created_at,ended_at`,
    [req.user.sub, calleeId, kind, initialStatus]
  );
  await db(
    `INSERT INTO notifications(user_id,type,title,body,data)
     VALUES($1,'call','Incoming call','You have an incoming call',$2)`,
    [calleeId, JSON.stringify({ callId: r.rows[0].id, kind })]
  );

  // Api.kt parses a nested peer object, so a placed call must be shaped like a history entry.
  const callee = await db('SELECT display_name,username,avatar_url,online FROM users WHERE id=$1', [
    calleeId
  ]);
  const peer = callee.rowCount
    ? {
        id: calleeId,
        display_name: callee.rows[0].display_name,
        username: callee.rows[0].username,
        avatar_url: callee.rows[0].avatar_url,
        online: callee.rows[0].online
      }
    : null;
  const callerInfo = await db('SELECT display_name,username,avatar_url FROM users WHERE id=$1',[req.user.sub]);
  const callerRow = callerInfo.rows[0] || {};
  const callPayload = JSON.stringify({type:'call.incoming',callId:r.rows[0].id,kind,status:initialStatus,caller:{id:req.user.sub,displayName:callerRow.display_name||'HolidayLink user',username:callerRow.username||'',avatarUrl:callerRow.avatar_url||null}});
  for (const ws of sockets.get(calleeId)||[]) if (ws.readyState===1) ws.send(callPayload);
  res.json({ ...r.rows[0], peer, incoming: false, duration_seconds: 0 });
});

app.patch('/calls/:id', auth, async (req, res) => {
  const allowed = ['ringing','calling','accepted','declined','ended','missed'];
  const status = String(req.body.status || '');
  if (!allowed.includes(status)) return res.status(400).json({ error:'INVALID_CALL_STATUS' });
  const r = await db(`UPDATE calls SET status=$1,ended_at=CASE WHEN $1 IN ('ended','declined','missed') THEN NOW() ELSE ended_at END
    WHERE id=$2 AND (caller_id=$3 OR callee_id=$3) RETURNING *`, [status,req.params.id,req.user.sub]);
  if (!r.rowCount) return res.status(404).json({ error:'CALL_NOT_FOUND' });
  const c=r.rows[0]; const peerId=c.caller_id===req.user.sub?c.callee_id:c.caller_id;
  const payload=JSON.stringify({type:'call.state',callId:c.id,status:c.status});
  for (const ws of sockets.get(peerId)||[]) if (ws.readyState===1) ws.send(payload);
  res.json(c);
});

app.get('/calls/:id', auth, async (req, res) => {
  const r = await db(
    `SELECT c.*,cu.display_name AS caller_name,tu.display_name AS callee_name
     FROM calls c
     JOIN users cu ON cu.id=c.caller_id
     JOIN users tu ON tu.id=c.callee_id
     WHERE c.id=$1 AND ($2=c.caller_id OR $2=c.callee_id)`,
    [req.params.id, req.user.sub]
  );
  if (!r.rowCount) return res.status(404).json({ error: 'NOT_FOUND' });
  res.json(r.rows[0]);
});

// Everything added after the first release. Registered before the 404 handler so these routes
// win over the defaults above.
registerFeatures(app, { db, pool, auth, fail, broadcast });

app.use((_req, res) => res.status(404).json({ error: 'NOT_FOUND', message: 'That endpoint does not exist.' }));

// Central error handler: never leak stack traces or credentials to clients.
app.use((err, _req, res, _next) => {
  if (err && err.type === 'entity.parse.failed') {
    return res.status(400).json({ error: 'INVALID_JSON', message: 'The request body could not be read.' });
  }
  console.error('[unhandled]', err);
  if (isDatabaseError(err)) {
    return res.status(503).json({ error: 'DATABASE_UNAVAILABLE', message: 'HolidayLink is temporarily unavailable. Please try again shortly.' });
  }
  res.status(500).json({ error: 'INTERNAL_ERROR', message: 'Something went wrong on our side. Please try again.' });
});

await app.use((err, _req, res, _next) => {
  if (err?.code === 'LIMIT_FILE_SIZE') {
    return res.status(413).json({ error: 'VIDEO_TOO_LARGE', message: 'Videos must be 100 MB or smaller.' });
  }
  console.error('[express]', err?.message || err);
  return res.status(500).json({ error: 'INTERNAL_ERROR', message: 'Something went wrong on our side. Please try again.' });
});

runDatabaseMigrations().catch(err => {
  console.error('[db] startup migration failed:', err.message);
});

const server = app.listen(PORT, '0.0.0.0', () => {
  console.log(`HolidayLink server listening on 0.0.0.0:${PORT}`);
});

const wss = new WebSocketServer({ server, path: '/ws' });
const sockets = new Map(); // userId -> Set<WebSocket>

function broadcast(conversationId, payload) {
  db('SELECT user_id FROM conversation_members WHERE conversation_id=$1', [conversationId])
    .then(r => {
      for (const row of r.rows) {
        const set = sockets.get(row.user_id);
        if (!set) continue;
        for (const ws of set) {
          if (ws.readyState === 1) ws.send(JSON.stringify(payload));
        }
      }
    })
    .catch(console.error);
}

wss.on('connection', (ws, request) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host}`);
    const token = url.searchParams.get('token') || '';
    const user = jwt.verify(token, JWT_SECRET);
    if (!sockets.has(user.sub)) sockets.set(user.sub, new Set());
    sockets.get(user.sub).add(ws);
    db(`UPDATE calls SET status='ringing' WHERE callee_id=$1 AND status='calling' AND created_at>NOW()-INTERVAL '60 seconds'`, [user.sub]).then(()=>db(`SELECT c.id,c.kind,c.status,c.caller_id,u.display_name,u.username,u.avatar_url
        FROM calls c JOIN users u ON u.id=c.caller_id
        WHERE c.callee_id=$1 AND c.status IN ('ringing','calling') AND c.created_at>NOW()-INTERVAL '60 seconds'
        ORDER BY c.created_at DESC`, [user.sub])).then(r=>{
      for (const row of r.rows) ws.send(JSON.stringify({type:'call.incoming',callId:row.id,kind:row.kind,status:'ringing',caller:{id:row.caller_id,displayName:row.display_name,username:row.username,avatarUrl:row.avatar_url}}));
    }).catch(()=>{});

    ws.on('close', () => {
      const set = sockets.get(user.sub);
      set?.delete(ws);
      if (set?.size === 0) sockets.delete(user.sub);
      db('UPDATE users SET online=false,last_seen=NOW() WHERE id=$1', [user.sub]).catch(() => {});
    });

    ws.on('message', async raw => {
      try {
        const data = JSON.parse(raw.toString());
        if (data.type === 'typing' && data.conversationId) {
          const member = await db(
            'SELECT 1 FROM conversation_members WHERE conversation_id=$1 AND user_id=$2',
            [data.conversationId, user.sub]
          );
          if (member.rowCount) {
            // The person typing owns the setting: nothing leaves their device unless they left
            // typing indicators on. A missing row means the default (on). A block in either
            // direction stops the event entirely.
            const sender = await db(
              'SELECT COALESCE(typing_indicators,true) AS typing FROM user_privacy WHERE user_id=$1',
              [user.sub]
            );
            const senderTyping =
              sender.rowCount ? sender.rows[0].typing !== false : true;
            if (senderTyping) {
              await db(
                `SELECT cm.user_id FROM conversation_members cm
                 WHERE cm.conversation_id=$1 AND cm.user_id<>$2
                   AND NOT EXISTS (
                     SELECT 1 FROM user_blocks b
                     WHERE (b.blocker_id=$2 AND b.blocked_id=cm.user_id)
                        OR (b.blocker_id=cm.user_id AND b.blocked_id=$2)
                   )`,
                [data.conversationId, user.sub]
              ).then(r => {
                const payload = JSON.stringify({
                  type: 'typing',
                  userId: user.sub,
                  conversationId: data.conversationId,
                  active: !!data.active
                });
                for (const row of r.rows) {
                  for (const peer of sockets.get(row.user_id) || []) {
                    if (peer.readyState === 1) peer.send(payload);
                  }
                }
              });
            }
          }
        }
        if (data.type === 'call.signal' && data.callId) {
          const c = await db('SELECT caller_id,callee_id FROM calls WHERE id=$1', [data.callId]);
          if (c.rowCount) {
            const peerId = c.rows[0].caller_id === user.sub ? c.rows[0].callee_id : c.rows[0].caller_id;
            const set = sockets.get(peerId);
            for (const peer of set || []) if (peer.readyState === 1) peer.send(JSON.stringify({ type:'call.signal', callId:data.callId, signal:data.signal }));
          }
        }
      } catch (e) {
        console.error('ws message', e.message);
      }
    });
  } catch {
    ws.close(1008, 'Unauthorized');
  }
});
