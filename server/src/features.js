import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import express from 'express';

/**
 * Everything added after the first release: profiles, blocking, message actions, statuses,
 * privacy, devices, global search, account deletion and the HolidayLink AI proxy.
 *
 * Privacy rules live here, on the server, and nowhere else. The Android client only renders what
 * these routes choose to send, so a field set to "Nobody" is simply absent from the response
 * instead of being hidden behind a client toggle.
 */

const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
const AUDIENCES = ['everyone', 'contacts', 'nobody'];
const STATUS_AUDIENCES = ['everyone', 'contacts', 'contacts_except', 'only_share_with'];
const AI_SYSTEM_PROMPT = [
  'You are HolidayLink AI, the assistant built into the HolidayLink messaging app.',
  'Help the person with their own messages, plans and drafts.',
  'Never invent personal details about other people and never claim to have contacted anyone.',
  'If something is unclear or personal, say so plainly.'
].join(' ');

// The "Response style" preference from Settings > HolidayLink AI. Unknown values fall back to
// balanced, so a client can never talk the assistant into ignoring the base instructions.
const AI_RESPONSE_STYLES = {
  concise: 'Answer as briefly as the question allows: short sentences, no preamble.',
  balanced: 'Answer with a short paragraph or a few bullets, no preamble.',
  detailed: 'Answer thoroughly with steps or examples, but stay on the question asked.'
};

export const UPLOAD_DIR = process.env.UPLOAD_DIR
  ? path.resolve(process.env.UPLOAD_DIR)
  : path.join(process.cwd(), 'uploads');

const GEMINI_KEY = String(process.env.GEMINI_API_KEY || '').trim();
// Several models, tried in order. If one is unavailable, rate limited or times out the next one
// answers, so AI does not go dark because a single model id was retired. GEMINI_MODEL is still
// honoured on its own for older deployments.
const GEMINI_MODEL_CHAIN = String(
  process.env.GEMINI_MODELS || process.env.GEMINI_MODEL || ''
)
  .split(',')
  .map(id => id.trim())
  .filter(Boolean);
const GEMINI_MODELS =
  GEMINI_MODEL_CHAIN.length > 0
    ? GEMINI_MODEL_CHAIN
    : ['gemini-3.8-flash'];
const GEMINI_ENDPOINT = 'https://generativelanguage.googleapis.com/v1beta/models';
const AI_MIN_GAP_MS = 1500;
const AI_MODEL_TIMEOUT_MS = 25_000;

const EXTENSION_BY_MIME = {
  'image/jpeg': '.jpg',
  'image/jpg': '.jpg',
  'image/png': '.png',
  'image/webp': '.webp',
  'image/gif': '.gif',
  'video/mp4': '.mp4',
  'video/3gpp': '.3gp',
  'audio/mp4': '.m4a',
  'audio/mpeg': '.mp3',
  'audio/ogg': '.ogg',
  'application/pdf': '.pdf',
  'text/plain': '.txt'
};

const aiHits = new Map();

const AI_CLEANUP = setInterval(() => {
  const now = Date.now();
  for (const [key, entry] of aiHits) {
    if (now - entry.first > 60_000) aiHits.delete(key);
  }
}, 60_000);
if (typeof AI_CLEANUP.unref === 'function') AI_CLEANUP.unref();

export function registerFeatures(app, ctx) {
  const { db, auth, fail, broadcast } = ctx;

  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
  app.use('/uploads', express.static(UPLOAD_DIR, { maxAge: '7d', fallthrough: true }));


  // Videos feed removed. Chat video attachments still use /uploads + message_attachments.

  // ---------------------------------------------------------------- shared helpers

  /** Blocking is symmetric for every action that opens a channel: neither side may reach the other. */
  const isBlockedEitherWay = async (a, b) => {
    const r = await db(
      `SELECT 1 FROM user_blocks
       WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1)
       LIMIT 1`,
      [a, b]
    );
    return r.rowCount > 0;
  };

  /**
   * "Contacts" means two people who have a direct chat with each other, never a raw address book
   * and never merely sharing a group. Kept in one place so every rule that says "contacts"
   * (last seen, photo, about, group invites, search) means exactly the same thing.
   */
  const areConnected = async (viewerId, otherId) => {
    if (!viewerId || !otherId || viewerId === otherId) return false;
    const r = await db(
      `SELECT 1 FROM conversation_members a
       JOIN conversation_members b ON b.conversation_id=a.conversation_id
       JOIN conversations c ON c.id=a.conversation_id
       WHERE a.user_id=$1 AND b.user_id=$2 AND c.kind='direct' LIMIT 1`,
      [viewerId, otherId]
    );
    return r.rowCount > 0;
  };

  /**
   * Batched form of [areConnected]: given a viewer and a set of people, returns the subset the
   * viewer already has a direct chat with. One query instead of one per person.
   */
  const connectedTo = async (viewerId, ids) => {
    const out = new Set();
    const candidates = [...new Set((ids || []).map(String))].filter(id => id && id !== viewerId);
    if (!viewerId || !candidates.length) return out;
    const r = await db(
      `SELECT DISTINCT b.user_id FROM conversation_members a
       JOIN conversation_members b ON b.conversation_id=a.conversation_id
       JOIN conversations c ON c.id=a.conversation_id
       WHERE a.user_id=$1 AND b.user_id = ANY($2::uuid[]) AND c.kind='direct'`,
      [viewerId, candidates]
    );
    for (const row of r.rows) out.add(row.user_id);
    return out;
  };

  const privacyOf = async userId => {
    const r = await db('SELECT * FROM user_privacy WHERE user_id=$1', [userId]);
    return r.rowCount ? r.rows[0] : null;
  };

  const allows = async (viewerId, targetId, audience) => {
    if (audience === 'everyone') return true;
    if (audience === 'nobody') return false;
    if (viewerId === targetId) return true;
    return areConnected(viewerId, targetId);
  };

  const statusVisible = async (row, viewerId) => {
    const exclude = Array.isArray(row.status_exclude) ? row.status_exclude : [];
    const only = Array.isArray(row.status_only) ? row.status_only : [];
    if (exclude.includes(viewerId)) return false;
    if (only.length > 0) return only.includes(viewerId);
    if (viewerId === row.user_id) return true;
    if (row.status_rule === 'everyone') return true;
    return areConnected(viewerId, row.user_id);
  };

  const roleIn = async (conversationId, userId) => {
    const r = await db(
      'SELECT role FROM conversation_members WHERE conversation_id=$1 AND user_id=$2',
      [conversationId, userId]
    );
    return r.rowCount ? r.rows[0].role : null;
  };

  const baseUrl = req => {
    const proto = req.get('x-forwarded-proto')?.split(',')[0]?.trim() || req.protocol;
    return `${proto}://${req.get('host')}`;
  };

  // Same key the website uses for every upload, overridable per deployment.
  const IMGBB_KEY = String(process.env.IMGBB_API_KEY || '004a77cb11087502ad96cdf0555781e7').trim();

  const uploadToImgbb = async (buffer, mime) => {
    if (!IMGBB_KEY) return null;
    try {
      const form = new FormData();
      form.append('image', buffer.toString('base64'));
      const res = await fetch(`https://api.imgbb.com/1/upload?key=${encodeURIComponent(IMGBB_KEY)}`, {
        method: 'POST',
        body: form
      });
      const body = await res.json().catch(() => null);
      const url = body?.data?.url;
      return typeof url === 'string' && url ? url : null;
    } catch (err) {
      console.warn('[imgbb] upload failed, falling back to local disk:', err.message);
      return null;
    }
  };

  /**
   * Stores an uploaded data URL and returns its public URL. Images go to imgbb
   * exactly like the website; anything else (or imgbb failure) stays on local
   * disk under /uploads. Image bytes never go into PostgreSQL.
   */
  const storeDataUrl = async (req, dataUrl, kind) => {
    const match = /^data:([^;,]*)((?:;[^;,]+)*),(.*)$/s.exec(String(dataUrl || ''));
    if (!match) return null;
    const mime = (match[1] || 'application/octet-stream').toLowerCase();
    const isBase64 = match[2].includes(';base64');
    let buffer;
    try {
      buffer = isBase64
        ? Buffer.from(match[3], 'base64')
        : Buffer.from(decodeURIComponent(match[3]), 'utf8');
    } catch {
      return null;
    }
    if (buffer.length === 0 || buffer.length > MAX_UPLOAD_BYTES) return null;

    const name = `${kind}-${Date.now()}-${crypto.randomBytes(6).toString('hex')}${EXTENSION_BY_MIME[mime] || '.bin'}`;
    if (mime.startsWith('image/')) {
      const hosted = await uploadToImgbb(buffer, mime);
      if (hosted) return { url: hosted, name, sizeBytes: buffer.length, buffer, mime };
    }
    fs.writeFileSync(path.join(UPLOAD_DIR, name), buffer);
    return { url: `${baseUrl(req)}/uploads/${name}`, name, sizeBytes: buffer.length, buffer, mime };
  };

  /**
   * Expands raw message rows into the payload the app renders: sender details, attachments,
   * reaction totals and who has read the message.
   */
  const hydrateMessages = async (rows, viewerId) => {
    if (!rows.length) return [];
    const ids = rows.map(row => row.id);

    // Sender photos follow the same "Who can see my profile photo" rule as a profile page,
    // so a message list can never be used to bypass it.
    const senderIds = [...new Set(rows.map(row => row.sender_id).filter(Boolean))];
    const photoAllowed = new Map();
    if (senderIds.length) {
      const privacy = await db(
        'SELECT user_id,profile_photo FROM user_privacy WHERE user_id = ANY($1::uuid[])',
        [senderIds]
      );
      const rules = new Map(privacy.rows.map(row => [row.user_id, row.profile_photo || 'everyone']));
      const known = await connectedTo(viewerId, senderIds);
      for (const id of senderIds) {
        const rule = rules.get(id) || 'everyone';
        photoAllowed.set(
          id,
          rule === 'nobody' ? false : rule === 'contacts' ? known.has(id) : true
        );
      }
    }

    const attachments = await db(
      'SELECT * FROM message_attachments WHERE message_id = ANY($1::uuid[]) ORDER BY created_at',
      [ids]
    );
    const reactions = await db(
      `SELECT message_id, emoji, COUNT(*)::int AS total,
              BOOL_OR(user_id=$2::uuid) AS mine
       FROM message_reactions
       WHERE message_id = ANY($1::uuid[])
       GROUP BY message_id, emoji`,
      [ids, viewerId]
    );
    const receipts = await db(
      'SELECT message_id, user_id, delivered_at, read_at FROM message_receipts WHERE message_id = ANY($1::uuid[])',
      [ids]
    );

    const attachmentsBy = new Map();
    for (const row of attachments.rows) {
      const list = attachmentsBy.get(row.message_id) || [];
      list.push({
        id: row.id,
        kind: row.kind,
        url: row.url,
        name: row.name,
        size_bytes: Number(row.size_bytes || 0),
        width: row.width,
        height: row.height,
        duration_ms: row.duration_ms === null ? null : Number(row.duration_ms),
        waveform: row.waveform
      });
      attachmentsBy.set(row.message_id, list);
    }

    const reactionsBy = new Map();
    for (const row of reactions.rows) {
      const list = reactionsBy.get(row.message_id) || [];
      list.push({ emoji: row.emoji, count: Number(row.total), mine: Boolean(row.mine) });
      reactionsBy.set(row.message_id, list);
    }

    const readersBy = new Map();
    const deliveredBy = new Map();
    for (const row of receipts.rows) {
      if (row.read_at) { const list = readersBy.get(row.message_id) || []; list.push(row.user_id); readersBy.set(row.message_id, list); }
      if (row.delivered_at) { const list = deliveredBy.get(row.message_id) || []; list.push(row.user_id); deliveredBy.set(row.message_id, list); }
    }

    return rows.map(row => {
      const expired = Boolean(row.expires_at) && new Date(row.expires_at).getTime() <= Date.now();
      const attachments = attachmentsBy.get(row.id) || [];
      return {
        id: row.id,
        conversation_id: row.conversation_id,
        sender_id: row.sender_id,
        sender_name: row.sender_name || null,
        sender_username: row.sender_username || null,
        sender_avatar_url:
          photoAllowed.get(row.sender_id) === false ? null : row.sender_avatar_url || null,
        body: row.deleted_at ? '' : row.body,
        message_type: row.message_type,
        created_at: row.created_at,
        edited_at: row.edited_at,
        expires_at: row.expires_at,
        view_once: Boolean(row.view_once),
        pinned: Boolean(row.pinned),
        reply_to_id: row.reply_to || null,
        reply_to_body: row.reply_to_body || null,
        reply_to_sender_name: row.reply_to_sender_name || null,
        my_reaction: row.my_reaction || null,
        status: row.deleted_at ? 'deleted' : expired ? 'expired' : row.sender_id === viewerId ? ((deliveredBy.get(row.id)||[]).length ? ((readersBy.get(row.id)||[]).length ? 'read' : 'delivered') : 'sent') : 'delivered',
        attachment: attachments[0] || null,
        attachments,
        reactions: reactionsBy.get(row.id) || [],
        read_by: row.sender_id === viewerId ? readersBy.get(row.id) || [] : []
      };
    });
  };

  /** Hides messages whose disappearing timer has run out (self-destruct). */
  const purgeExpired = async conversationId => {
    await db(
      `UPDATE messages SET body='', message_type='text'
       WHERE conversation_id=$1 AND expires_at IS NOT NULL AND expires_at<=NOW()
         AND deleted_at IS NULL`,
      [conversationId]
    );
    // Self-destruct removes media too, Telegram-style.
    await db(
      `DELETE FROM message_attachments WHERE message_id IN
        (SELECT id FROM messages WHERE conversation_id=$1 AND expires_at IS NOT NULL
          AND expires_at<=NOW() AND deleted_at IS NULL)`,
      [conversationId]
    );
  };

  app.get('/users/:id/avatar', async (req,res)=>{
    try { const r=await db('SELECT avatar_data FROM users WHERE id=$1',[req.params.id]); if(!r.rowCount || !r.rows[0].avatar_data) return res.status(404).end(); res.setHeader('Content-Type','image/jpeg'); res.setHeader('Cache-Control','public,max-age=86400'); res.send(r.rows[0].avatar_data); } catch(err){ return fail(res,err,'avatar'); }
  });

  // ---------------------------------------------------------------- profile

  app.delete('/account', auth, async (req, res) => {
    try {
      // Every related table cascades from users, so one delete leaves nothing behind.
      await db('DELETE FROM users WHERE id=$1', [req.user.sub]);
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'delete-account');
    }
    });

  // Website admin panel control: ban/unban/kick by user id. Guarded by a shared
  // ADMIN_API_KEY env secret (set the same value in the website admin config).
  const adminAuth = (req, res) => {
    const key = String(process.env.ADMIN_API_KEY || '');
    if (!key) return res.status(503).json({ error: 'ADMIN_DISABLED', message: 'Admin API is not configured.' }), false;
    if (req.get('x-admin-key') !== key) return res.status(403).json({ error: 'FORBIDDEN' }), false;
    return true;
  };

  app.post('/admin/ban', async (req, res) => {
    try {
      if (!adminAuth(req, res)) return;
      const userId = String(req.body.userId || '');
      const reason = String(req.body.reason || '').slice(0, 300);
      if (!userId) return res.status(400).json({ error: 'INVALID_USER' });
      const r = await db(
        'UPDATE users SET banned=true, ban_reason=NULLIF($2,\'\'), banned_at=NOW(), updated_at=NOW() WHERE id=$1',
        [userId, reason]
      );
      if (!r.rowCount) return res.status(404).json({ error: 'USER_NOT_FOUND' });
      res.json({ ok: true, banned: true });
    } catch (err) {
      return fail(res, err, 'admin-ban');
    }
  });

  app.post('/admin/unban', async (req, res) => {
    try {
      if (!adminAuth(req, res)) return;
      const userId = String(req.body.userId || '');
      if (!userId) return res.status(400).json({ error: 'INVALID_USER' });
      const r = await db(
        'UPDATE users SET banned=false, ban_reason=NULL, banned_at=NULL, updated_at=NOW() WHERE id=$1',
        [userId]
      );
      if (!r.rowCount) return res.status(404).json({ error: 'USER_NOT_FOUND' });
      res.json({ ok: true, banned: false });
    } catch (err) {
      return fail(res, err, 'admin-unban');
    }
  });

  app.patch('/me/profile', auth, async (req, res) => {
    try {
      const updates = [];
      const params = [req.user.sub];
      const set = (column, value) => {
        params.push(value);
        updates.push(`${column}=$${params.length}`);
      };

      if (req.body.displayName !== undefined) {
        const displayName = String(req.body.displayName || '').trim();
        if (!displayName || displayName.length > 120) {
          return res.status(400).json({
            error: 'INVALID_DISPLAY_NAME',
            message: 'Enter a display name of up to 120 characters.'
          });
        }
        set('display_name', displayName);
      }

      if (req.body.username !== undefined) {
        const username = String(req.body.username || '').trim().replace(/^@/, '').toLowerCase();
        if (!/^[a-z0-9_]{3,32}$/.test(username)) {
          return res.status(400).json({
            error: 'INVALID_USERNAME',
            message: 'Username must be 3-32 characters using letters, numbers or underscore.'
          });
        }
        const taken = await db('SELECT 1 FROM users WHERE LOWER(username)=LOWER($1) AND id<>$2', [
          username,
          req.user.sub
        ]);
        if (taken.rowCount) {
          return res.status(409).json({
            error: 'PHONE_OR_USERNAME_ALREADY_REGISTERED',
            message: 'That username is already taken.'
          });
        }
        set('username', username);
      }

      if (req.body.about !== undefined) {
        const about = String(req.body.about || '').trim();
        if (about.length > 140) {
          return res.status(400).json({
            error: 'INVALID_ABOUT',
            message: 'Your About text must be 140 characters or fewer.'
          });
        }
        set('bio', about);
      }

      if (req.body.removeAvatar === true) {
        set('avatar_url', null);
        params.push(null); updates.push(`avatar_data=$${params.length}`);
      } else if (req.body.avatarDataUrl) {
        const stored = await storeDataUrl(req, req.body.avatarDataUrl, 'avatar');
        if (!stored) {
          return res.status(400).json({
            error: 'INVALID_IMAGE',
            message: 'That photo could not be read. Try a different one.'
          });
        }
        set('avatar_url', stored.url);
        // Keep a DB copy so /users/:id/avatar still works when Render's
        // ephemeral /uploads disk is wiped (redeploy/restart).
        try {
          params.push(stored.buffer); updates.push(`avatar_data=$${params.length}`);
        } catch {}
      }

      if (updates.length === 0) {
        const current = await db(
          'SELECT id,phone,email,display_name,username,avatar_url,bio,online,last_seen FROM users WHERE id=$1',
          [req.user.sub]
        );
        return res.json(current.rows[0]);
      }

      updates.push('updated_at=NOW()');
      const r = await db(`UPDATE users SET ${updates.join(',')} WHERE id=$1 RETURNING *`, params);
      res.json(r.rows[0]);
    } catch (err) {
      return fail(res, err, 'update-profile');
    }
  });

  /**
   * Another person's profile. Fields the viewer is not entitled to come back as null, so
   * "Nobody" cannot be bypassed by reading the raw response.
   */
  app.get('/users/:id', auth, async (req, res) => {
    try {
      const targetId = String(req.params.id || '');
      const r = await db(
        `SELECT id,display_name,username,avatar_url,bio,phone,online,last_seen
         FROM users WHERE id=$1`,
        [targetId]
      );
      if (!r.rowCount) return res.status(404).json({ error: 'USER_NOT_FOUND' });

      const user = r.rows[0];
      const iBlockedThem = await db(
        'SELECT 1 FROM user_blocks WHERE blocker_id=$1 AND blocked_id=$2',
        [req.user.sub, targetId]
      );
      if (iBlockedThem.rowCount) {
        return res.status(403).json({
          error: 'USER_BLOCKED',
          message: "You blocked this person, so their profile isn't shown."
        });
      }
      const theyBlockedMe = await db(
        'SELECT 1 FROM user_blocks WHERE blocker_id=$1 AND blocked_id=$2',
        [targetId, req.user.sub]
      );

      const privacy = await privacyOf(targetId);
      const photoRule = privacy ? privacy.profile_photo : 'everyone';
      const aboutRule = privacy ? privacy.about : 'everyone';
      const lastSeenRule = privacy ? privacy.last_seen : 'everyone';
      const connected = await areConnected(req.user.sub, targetId);

      res.json({
        id: user.id,
        display_name: user.display_name,
        username: user.username,
        avatar_url: (await allows(req.user.sub, targetId, photoRule)) ? user.avatar_url : null,
        about: (await allows(req.user.sub, targetId, aboutRule)) ? user.bio : null,
        phone: connected ? user.phone : null,
        online: (await allows(req.user.sub, targetId, lastSeenRule)) ? user.online : null,
        last_seen: (await allows(req.user.sub, targetId, lastSeenRule)) ? user.last_seen : null,
        is_blocked: false,
        blocked_by_them: theyBlockedMe.rowCount > 0
      });
    } catch (err) {
      return fail(res, err, 'user-profile');
    }
  });

  // ---------------------------------------------------------------- contacts

  /**
   * Matches SHA-256 hashes of device numbers. Only hashes arrive here: the server cannot turn a
   * hash back into a phone number, and no name or photo is ever uploaded.
   */
  const matchByHashes = async (req, res) => {
    try {
      const hashes = Array.isArray(req.body.phoneHashes)
        ? req.body.phoneHashes.map(value => String(value).toLowerCase()).slice(0, 5000)
        : [];
      if (hashes.length === 0) return res.json([]);

      const matches = await db(
        `SELECT u.id,u.display_name,u.username,u.avatar_url,u.online,u.last_seen,u.phone_hash,
                p.profile_photo,p.about,p.last_seen AS last_seen_rule
         FROM users u
         LEFT JOIN user_privacy p ON p.user_id=u.id
         WHERE LOWER(u.phone_hash) = ANY($1::text[]) AND u.id<>$2
         ORDER BY u.display_name`,
        [hashes, req.user.sub]
      );

      const out = [];
      for (const row of matches.rows) {
        if (await isBlockedEitherWay(req.user.sub, row.id)) continue;
        out.push({
          id: row.id,
          display_name: row.display_name,
          username: row.username,
          avatar_url: (await allows(req.user.sub, row.id, row.profile_photo || 'everyone'))
            ? row.avatar_url
            : null,
          bio: (await allows(req.user.sub, row.id, row.about || 'everyone')) ? row.bio : null,
          online: (await allows(req.user.sub, row.id, row.last_seen_rule || 'everyone'))
            ? row.online
            : null,
          last_seen: (await allows(req.user.sub, row.id, row.last_seen_rule || 'everyone'))
            ? row.last_seen
            : null,
          matchedHash: row.phone_hash
        });
      }
      res.json(out);
    } catch (err) {
      return fail(res, err, 'contact-match');
    }
  };

  app.post('/contacts/match', auth, matchByHashes);
  app.post('/users/contact-matches', auth, matchByHashes);

  // ---------------------------------------------------------------- blocking

  app.post('/users/:id/block', auth, async (req, res) => {
    try {
      const targetId = String(req.params.id || '');
      if (!targetId || targetId === req.user.sub) {
        return res.status(400).json({ error: 'INVALID_TARGET' });
      }
      const target = await db('SELECT 1 FROM users WHERE id=$1', [targetId]);
      if (!target.rowCount) return res.status(404).json({ error: 'USER_NOT_FOUND' });

      await db(
        `INSERT INTO user_blocks(blocker_id,blocked_id) VALUES($1,$2) ON CONFLICT DO NOTHING`,
        [req.user.sub, targetId]
      );
      res.json({ ok: true, blocked: true });
    } catch (err) {
      return fail(res, err, 'block-user');
    }
  });

  app.delete('/users/:id/block', auth, async (req, res) => {
    try {
      await db('DELETE FROM user_blocks WHERE blocker_id=$1 AND blocked_id=$2', [
        req.user.sub,
        String(req.params.id || '')
      ]);
      res.json({ ok: true, blocked: false });
    } catch (err) {
      return fail(res, err, 'unblock-user');
    }
  });

  app.get('/blocked-users', auth, async (req, res) => {
    try {
      const r = await db(
        `SELECT u.id,u.display_name,u.username,u.avatar_url,u.bio,u.online,u.last_seen,
                p.profile_photo,p.about,p.last_seen AS last_seen_rule
         FROM user_blocks b
         JOIN users u ON u.id=b.blocked_id
         LEFT JOIN user_privacy p ON p.user_id=u.id
         WHERE b.blocker_id=$1 ORDER BY b.created_at DESC`,
        [req.user.sub]
      );
      // Blocking is not a licence to see fields someone hid from everyone, so the same
      // photo/About/presence rules still apply to this list. Rules are resolved in an
      // async loop because each one needs its own "are you connected" check.
      const out = [];
      for (const row of r.rows) {
        const photoOk = await allows(req.user.sub, row.id, row.profile_photo || 'everyone');
        const aboutOk = await allows(req.user.sub, row.id, row.about || 'everyone');
        const presenceOk = await allows(req.user.sub, row.id, row.last_seen_rule || 'everyone');
        out.push({
          id: row.id,
          display_name: row.display_name,
          username: row.username,
          avatar_url: photoOk ? row.avatar_url : null,
          bio: aboutOk ? row.bio : null,
          online: presenceOk ? row.online : null,
          last_seen: presenceOk ? row.last_seen : null
        });
      }
      res.json(out);
    } catch (err) {
      return fail(res, err, 'blocked-users');
    }
  });

  // ---------------------------------------------------------------- reporting

  const REASONS = [
    'spam',
    'harassment',
    'hate',
    'violence',
    'scam',
    'impersonation',
    'sexual_content',
    'other'
  ];

  app.post('/users/:id/report', auth, async (req, res) => {
    try {
      const reason = REASONS.includes(String(req.body.reason))
        ? String(req.body.reason)
        : 'other';
      await db(
        `INSERT INTO reports(reporter_id,reported_user_id,reason,details) VALUES($1,$2,$3,$4)`,
        [
          req.user.sub,
          String(req.params.id || ''),
          reason,
          String(req.body.details || '').slice(0, 2000) || null
        ]
      );
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'report-user');
    }
  });

  app.post('/reports', auth, async (req, res) => {
    try {
      const messageId = String(req.body.messageId || '');
      const owner = await db('SELECT sender_id FROM messages WHERE id=$1 AND deleted_at IS NULL', [
        messageId
      ]);
      if (!owner.rowCount) return res.status(404).json({ error: 'MESSAGE_NOT_FOUND' });
      const reason = REASONS.includes(String(req.body.reason))
        ? String(req.body.reason)
        : 'other';
      await db(
        `INSERT INTO reports(reporter_id,reported_user_id,message_id,reason,details)
         VALUES($1,$2,$3,$4,$5)`,
        [
          req.user.sub,
          owner.rows[0].sender_id,
          messageId,
          reason,
          String(req.body.details || '').slice(0, 2000) || null
        ]
      );
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'report-message');
    }
  });

  // ---------------------------------------------------------------- end-to-end encryption keys

  app.put('/me/device-key', auth, async (req, res) => {
    const publicKey = String(req.body.publicKey || '').trim();
    if (!publicKey || publicKey.length > 4096) return res.status(400).json({ error: 'INVALID_DEVICE_KEY' });
    await db(
      `INSERT INTO device_keys(user_id,public_key) VALUES($1,$2)
       ON CONFLICT(user_id) DO UPDATE SET public_key=EXCLUDED.public_key,updated_at=NOW()`,
      [req.user.sub, publicKey]
    );
    res.json({ ok: true });
  });

  app.get('/conversations/:id/keys', auth, async (req, res) => {
    if (!(await roleIn(req.params.id, req.user.sub))) return res.status(403).json({ error: 'NOT_MEMBER' });
    const r = await db(
      `SELECT cm.user_id,dk.public_key
       FROM conversation_members cm
       LEFT JOIN device_keys dk ON dk.user_id=cm.user_id
       WHERE cm.conversation_id=$1`, [req.params.id]
    );
    res.json(r.rows.map(x => ({ user_id:x.user_id, public_key:x.public_key || null })));
  });

  // Member ids for ranking common groups (caller must be a member).
  app.get('/conversations/:id/members', auth, async (req, res) => {
    try {
      if (!(await roleIn(req.params.id, req.user.sub))) return res.status(403).json({ error: 'NOT_MEMBER' });
      const r = await db('SELECT user_id FROM conversation_members WHERE conversation_id=$1', [req.params.id]);
      res.json(r.rows.map(x => x.user_id));
    } catch (err) { return fail(res, err, 'members'); }
  });

  // ---------------------------------------------------------------- messages

  app.get('/conversations/:id/messages', auth, async (req, res) => {
    try {
      if (!(await roleIn(req.params.id, req.user.sub))) {
        return res.status(403).json({
          error: 'NOT_MEMBER',
          message: "You're no longer part of this chat."
        });
      }
      await purgeExpired(req.params.id);

      const search = String(req.query.search || '').trim();
      const limit = Math.max(1, Math.min(500, Number(req.query.limit) || 200));
      const before = String(req.query.before || '').trim();

      const params = [req.params.id, req.user.sub];
      let where = 'm.conversation_id=$1 AND m.deleted_at IS NULL';
      if (search) { /* E2EE messages are intentionally not server-searchable. */ } 
      if (before) {
        params.push(before);
        where += ` AND m.created_at < $${params.length}::timestamptz`;
      }
      params.push(limit);

      const r = await db(
        `SELECT m.*,u.display_name AS sender_name,u.username AS sender_username,
                u.avatar_url AS sender_avatar_url,
                rp.body AS reply_to_body,ru.display_name AS reply_to_sender_name,
                (SELECT r2.emoji FROM message_reactions r2
                 WHERE r2.message_id=m.id AND r2.user_id=$2 LIMIT 1) AS my_reaction
         FROM messages m
         JOIN users u ON u.id=m.sender_id
         LEFT JOIN messages rp ON rp.id=m.reply_to
         LEFT JOIN users ru ON ru.id=rp.sender_id
         WHERE ${where}
         ORDER BY m.created_at DESC
         LIMIT $${params.length}`,
        params
      );

      const resultRows = r.rows.reverse();
      const incomingIds = resultRows.filter(x => x.sender_id !== req.user.sub).map(x => x.id);
      if (incomingIds.length) {
        await db(`INSERT INTO message_receipts(message_id,user_id,delivered_at)
                  SELECT id,$2,NOW() FROM messages WHERE id=ANY($1::uuid[])
                  ON CONFLICT(message_id,user_id) DO UPDATE SET delivered_at=COALESCE(message_receipts.delivered_at,NOW())`, [incomingIds, req.user.sub]);
        // Keep the encrypted envelope until every recipient actually opens the conversation.
        // The read endpoint removes it after recording read_at; this gives the sender/recipient
        // WhatsApp-like local-copy behaviour while preventing premature server deletion.
      }
      res.json(await hydrateMessages(resultRows, req.user.sub));
    } catch (err) {
      return fail(res, err, 'list-messages');
    }
  });

  app.post('/conversations/:id/messages', auth, async (req, res) => {
    try {
      const conversationId = req.params.id;
      const role = await roleIn(conversationId, req.user.sub);
      if (!role) {
        return res.status(403).json({
          error: 'NOT_MEMBER',
          message: "You're no longer part of this chat."
        });
      }
      // Channels are broadcast: only owner/admin may post, members read.
      const conv = await db('SELECT kind FROM conversations WHERE id=$1', [conversationId]);
      if (conv.rowCount && conv.rows[0].kind === 'channel' && !['owner', 'admin'].includes(role)) {
        return res.status(403).json({ error: 'CHANNEL_READ_ONLY', message: 'Only channel admins can post.' });
      }

      // A block stops messages in both directions, including in a chat that already exists.
      const peers = await db(
        `SELECT cm2.user_id FROM conversation_members cm1
         JOIN conversation_members cm2 ON cm2.conversation_id=cm1.conversation_id
         WHERE cm1.user_id=$1 AND cm2.user_id<>$1`,
        [req.user.sub]
      );
      for (const peer of peers.rows) {
        if (await isBlockedEitherWay(req.user.sub, peer.user_id)) {
          return res.status(403).json({
            error: 'USER_BLOCKED',
            message: "You can't send messages in this chat."
          });
        }
      }

      const body = String(req.body.body || '').trim();
      const encrypted = Boolean(req.body.e2ee);
      const nested =
        req.body.attachment && typeof req.body.attachment === 'object' ? req.body.attachment : null;
      // Api.kt sends the attachment fields flat, so both shapes are accepted.
      const dataUrl = (nested && nested.dataUrl) || req.body.attachmentDataUrl || null;
      const kindHint = (nested && nested.kind) || req.body.attachmentKind || null;
      const nameHint = (nested && nested.name) || req.body.attachmentName || null;
      const durationHint = (nested && nested.durationMs) || req.body.durationMs || null;
      const waveformHint = (nested && nested.waveform) || req.body.waveform || null;
      let replyToId = String(req.body.replyTo || req.body.replyToId || '');

      if (!body && !dataUrl) {
        return res.status(400).json({ error: 'INVALID_MESSAGE', message: 'Type a message first.' });
      }
      if (!encrypted && body.length > 4000) {
        return res.status(400).json({
          error: 'INVALID_MESSAGE',
          message: 'Messages are limited to 4000 characters.'
        });
      }

      if (replyToId) {
        const parent = await db(
          'SELECT 1 FROM messages WHERE id=$1 AND conversation_id=$2',
          [replyToId, conversationId]
        );
        if (!parent.rowCount) replyToId = '';
      }

      const kind = !dataUrl ? 'text' : ['image', 'video', 'audio', 'file'].includes(String(kindHint))
        ? String(kindHint)
        : 'file';

      let stored = null;
      if (dataUrl) {
        stored = await storeDataUrl(req, dataUrl, 'attachment');
        if (!stored) {
          return res.status(400).json({
            error: 'INVALID_ATTACHMENT',
            message: 'That file could not be read or is too large to send.'
          });
        }
      }

      const privacy = await privacyOf(req.user.sub);
      // Telegram-style self-destruct: per-message timer overrides the chat default.
      // Allowed: 0 (off/chat default), 5s..7d. View-once needs an attachment.
      const viewOnce = Boolean(req.body.viewOnce) && Boolean(dataUrl);
      let ttl = Number(privacy?.disappearing_seconds || 0);
      const askedTtl = Number(req.body.ttlSeconds);
      if (Number.isFinite(askedTtl) && askedTtl > 0) {
        ttl = Math.min(7 * 24 * 3600, Math.max(5, Math.floor(askedTtl)));
      }
      const message = await db(
        `INSERT INTO messages(conversation_id,sender_id,body,message_type,reply_to,expires_at,view_once)
         VALUES($1,$2,$3,$4,$5::uuid,
                CASE WHEN $6::bigint>0 THEN NOW()+($6::bigint * INTERVAL '1 second') ELSE NULL END,$7)
         RETURNING id,conversation_id,sender_id,body,message_type,reply_to,created_at,expires_at,view_once`,
        [
          conversationId,
          req.user.sub,
          body || (stored ? stored.name : ''),
          kind,
          replyToId || null,
          String(ttl),
          viewOnce
        ]
      );

      if (stored) {
        await db(
          `INSERT INTO message_attachments(message_id,kind,url,name,size_bytes,width,height,duration_ms,waveform)
           VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
          [
            message.rows[0].id,
            kind,
            stored.url,
            typeof nameHint === 'string' && nameHint ? nameHint.slice(0, 200) : stored.name,
            stored.sizeBytes,
            null,
            null,
            Number.isFinite(Number(durationHint)) ? Number(durationHint) : null,
            waveformHint ? String(waveformHint).slice(0, 20000) : null
          ]
        );
      }

      const [hydrated] = await hydrateMessages(
        [
          {
            ...message.rows[0],
            sender_name: null,
            sender_username: null,
            sender_avatar_url: null
          }
        ],
        req.user.sub
      );

      broadcast(conversationId, { type: 'message', message: hydrated });

      const recipients = await db(
        'SELECT user_id FROM conversation_members WHERE conversation_id=$1 AND user_id<>$2',
        [conversationId, req.user.sub]
      );
      // Every recipient decides for themselves whether a message notifies them.
      if (recipients.rows.length) {
        const recipientIds = recipients.rows.map(row => row.user_id);
        const prefs = await db(
          'SELECT user_id,messages FROM user_notification_settings WHERE user_id = ANY($1::uuid[])',
          [recipientIds]
        );
        const muted = new Set(
          prefs.rows.filter(row => row.messages === false).map(row => row.user_id)
        );
        for (const row of recipients.rows) {
          if (muted.has(row.user_id)) continue;
          await db(
            `INSERT INTO notifications(user_id,type,title,body,data) VALUES($1,'message',$2,$3,$4)`,
            [
              row.user_id,
              kind === 'text' ? 'New message' : 'New attachment',
              (body || 'Sent you a file').slice(0, 160),
              JSON.stringify({
                conversationId,
                messageId: message.rows[0].id,
                senderId: req.user.sub
              })
            ]
          );
        }
      }

      res.json(hydrated);
    } catch (err) {
      return fail(res, err, 'send-message');
    }
  });

  const loadOwnMessage = async (req, res) => {
    if (!(await roleIn(req.params.id, req.user.sub))) {
      res.status(403).json({ error: 'NOT_MEMBER', message: "You're no longer part of this chat." });
      return null;
    }
    const r = await db(
      `SELECT m.*,u.display_name AS sender_name,u.username AS sender_username,
              u.avatar_url AS sender_avatar_url
       FROM messages m JOIN users u ON u.id=m.sender_id
       WHERE m.id=$1 AND m.conversation_id=$2`,
      [req.params.messageId, req.params.id]
    );
    if (!r.rowCount || r.rows[0].deleted_at) {
      res.status(404).json({ error: 'MESSAGE_NOT_FOUND', message: 'That message no longer exists.' });
      return null;
    }
    if (r.rows[0].sender_id !== req.user.sub) {
      res.status(403).json({
        error: 'NOT_MESSAGE_OWNER',
        message: 'You can only change messages you sent.'
      });
      return null;
    }
    return r.rows[0];
  };

  app.patch('/conversations/:id/messages/:messageId', auth, async (req, res) => {
    try {
      const message = await loadOwnMessage(req, res);
      if (!message) return;
      const body = String(req.body.body || '').trim();
      const encrypted = Boolean(req.body.e2ee);
      if (!body || body.length > 4000) {
        return res.status(400).json({
          error: 'INVALID_MESSAGE',
          message: 'Messages are limited to 4000 characters.'
        });
      }
      await db('UPDATE messages SET body=$1, edited_at=NOW() WHERE id=$2', [
        body,
        req.params.messageId
      ]);
      const [hydrated] = await hydrateMessages([{ ...message, body, edited_at: new Date() }], req.user.sub);
      broadcast(req.params.id, { type: 'message.updated', message: hydrated });
      res.json(hydrated);
    } catch (err) {
      return fail(res, err, 'edit-message');
    }
  });

  app.delete('/conversations/:id/messages/:messageId', auth, async (req, res) => {
    try {
      const message = await loadOwnMessage(req, res);
      if (!message) return;
      await db('UPDATE messages SET deleted_at=NOW() WHERE id=$1', [req.params.messageId]);
      broadcast(req.params.id, { type: 'message.deleted', messageId: req.params.messageId });
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'delete-message');
    }
  });

  // View-once: recipient opens the media once, then it is destroyed server-side.
  app.post('/conversations/:id/messages/:messageId/viewed', auth, async (req, res) => {
    try {
      if (!(await roleIn(req.params.id, req.user.sub))) {
        return res.status(403).json({ error: 'NOT_MEMBER' });
      }
      const r = await db(
        'SELECT id,sender_id,view_once,deleted_at FROM messages WHERE id=$1 AND conversation_id=$2',
        [req.params.messageId, req.params.id]
      );
      if (!r.rowCount || r.rows[0].deleted_at) return res.status(404).json({ error: 'MESSAGE_NOT_FOUND' });
      const message = r.rows[0];
      if (!message.view_once) return res.json({ ok: true, destroyed: false });
      if (message.sender_id === req.user.sub) return res.json({ ok: true, destroyed: false });
      await db('DELETE FROM message_attachments WHERE message_id=$1', [req.params.messageId]);
      await db('UPDATE messages SET body=\'\',message_type=\'text\',deleted_at=NOW() WHERE id=$1', [req.params.messageId]);
      broadcast(req.params.id, { type: 'message.deleted', messageId: req.params.messageId });
      res.json({ ok: true, destroyed: true });
    } catch (err) {
      return fail(res, err, 'view-once');
    }
  });

  app.post('/conversations/:id/messages/:messageId/reactions', auth, async (req, res) => {
    try {
      if (!(await roleIn(req.params.id, req.user.sub))) {
        return res.status(403).json({ error: 'NOT_MEMBER' });
      }
      const r = await db(
        `SELECT m.*,u.display_name AS sender_name,u.username AS sender_username,
                u.avatar_url AS sender_avatar_url
         FROM messages m JOIN users u ON u.id=m.sender_id
         WHERE m.id=$1 AND m.conversation_id=$2`,
        [req.params.messageId, req.params.id]
      );
      if (!r.rowCount || r.rows[0].deleted_at) {
        return res.status(404).json({ error: 'MESSAGE_NOT_FOUND' });
      }
      const message = r.rows[0];

      const emoji = String(req.body.emoji || '').trim().slice(0, 16);
      if (!emoji) {
        return res.status(400).json({ error: 'INVALID_EMOJI', message: 'Pick a reaction first.' });
      }

      const existing = await db(
        'SELECT emoji FROM message_reactions WHERE message_id=$1 AND user_id=$2',
        [req.params.messageId, req.user.sub]
      );
      if (existing.rowCount && existing.rows[0].emoji === emoji) {
        await db('DELETE FROM message_reactions WHERE message_id=$1 AND user_id=$2', [
          req.params.messageId,
          req.user.sub
        ]);
      } else {
        await db(
          `INSERT INTO message_reactions(message_id,user_id,emoji) VALUES($1,$2,$3)
           ON CONFLICT (message_id,user_id) DO UPDATE SET emoji=EXCLUDED.emoji`,
          [req.params.messageId, req.user.sub, emoji]
        );
      }

      const [hydrated] = await hydrateMessages([message], req.user.sub);
      broadcast(req.params.id, { type: 'message.reacted', message: hydrated });
      res.json(hydrated);
    } catch (err) {
      return fail(res, err, 'react-message');
    }
  });

  app.post('/conversations/:id/messages/:messageId/pin', auth, async (req, res) => {
    try {
      if (!(await roleIn(req.params.id, req.user.sub))) {
        return res.status(403).json({ error: 'NOT_MEMBER' });
      }
      const pinned = req.body.pinned === true;
      const r = await db(
        `UPDATE messages SET pinned=$1
         WHERE id=$2 AND conversation_id=$3 AND deleted_at IS NULL
         RETURNING *`,
        [pinned, req.params.messageId, req.params.id]
      );
      if (!r.rowCount) return res.status(404).json({ error: 'MESSAGE_NOT_FOUND' });

      const [hydrated] = await hydrateMessages([r.rows[0]], req.user.sub);
      broadcast(req.params.id, {
        type: 'message.pinned',
        message: hydrated
      });
      res.json(hydrated);
    } catch (err) {
      return fail(res, err, 'pin-message');
    }
  });

  app.get('/conversations/:id/pinned', auth, async (req, res) => {
    try {
      if (!(await roleIn(req.params.id, req.user.sub))) {
        return res.status(403).json({ error: 'NOT_MEMBER' });
      }
      const r = await db(
        `SELECT m.*,u.display_name AS sender_name,u.username AS sender_username,
                u.avatar_url AS sender_avatar_url
         FROM messages m JOIN users u ON u.id=m.sender_id
         WHERE m.conversation_id=$1 AND m.pinned=true AND m.deleted_at IS NULL
         ORDER BY m.created_at DESC`,
        [req.params.id]
      );
      res.json(await hydrateMessages(r.rows, req.user.sub));
    } catch (err) {
      return fail(res, err, 'pinned-messages');
    }
  });

  app.post('/conversations/:id/read', auth, async (req, res) => {
    try {
      if (!(await roleIn(req.params.id, req.user.sub))) {
        return res.status(403).json({ error: 'NOT_MEMBER' });
      }
      // Record the local read event for server cleanup even when the sender has disabled
      // read-receipt visibility. The timestamp is not broadcast in that case.
      const messagesToRead = await db(
        `SELECT DISTINCT m.sender_id
         FROM messages m
         WHERE m.conversation_id=$1 AND m.sender_id<>$2 AND m.deleted_at IS NULL`,
        [req.params.id, req.user.sub]
      );
      for (const row of messagesToRead.rows) {
        await db(
          `INSERT INTO message_receipts(message_id,user_id,read_at)
           SELECT id,$1,NOW() FROM messages
           WHERE conversation_id=$2 AND sender_id=$3 AND deleted_at IS NULL
           ON CONFLICT (message_id,user_id) DO UPDATE SET read_at=NOW()`,
          [req.user.sub, req.params.id, row.sender_id]
        );
      }
      // Only expose a seen event to senders who allow read receipts.
      const visible = await db(
        `SELECT 1 FROM messages m
         LEFT JOIN user_privacy p ON p.user_id=m.sender_id
         WHERE m.conversation_id=$1 AND m.sender_id<>$2 AND m.deleted_at IS NULL
           AND COALESCE(p.read_receipts,true)=true LIMIT 1`,
        [req.params.id, req.user.sub]
      );
      if (messagesToRead.rows.length && visible.rowCount) {
        broadcast(req.params.id, { type: 'conversation.read', userId: req.user.sub });
      }
      // Delete only after every non-sender member has read the encrypted envelope.
      await db(`DELETE FROM messages m WHERE m.conversation_id=$1 AND m.deleted_at IS NULL
                AND NOT EXISTS (SELECT 1 FROM conversation_members cm WHERE cm.conversation_id=m.conversation_id AND cm.user_id<>m.sender_id
                  AND NOT EXISTS (SELECT 1 FROM message_receipts mr WHERE mr.message_id=m.id AND mr.user_id=cm.user_id AND mr.read_at IS NOT NULL))`, [req.params.id]);
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'mark-read');
    }
  });

  app.get('/conversations/:id/settings', auth, async (req, res) => {
    try {
      if (!(await roleIn(req.params.id, req.user.sub))) {
        return res.status(403).json({ error: 'NOT_MEMBER' });
      }
      const r = await db(
        'SELECT pinned,archived,muted FROM conversation_settings WHERE conversation_id=$1 AND user_id=$2',
        [req.params.id, req.user.sub]
      );
      res.json(r.rowCount ? r.rows[0] : { pinned: false, archived: false, muted: false });
    } catch (err) {
      return fail(res, err, 'conversation-settings');
    }
  });

  app.patch('/conversations/:id/settings', auth, async (req, res) => {
    try {
      if (!(await roleIn(req.params.id, req.user.sub))) {
        return res.status(403).json({ error: 'NOT_MEMBER' });
      }
      const current = await db(
        'SELECT pinned,archived,muted FROM conversation_settings WHERE conversation_id=$1 AND user_id=$2',
        [req.params.id, req.user.sub]
      );
      const previous = current.rowCount
        ? current.rows[0]
        : { pinned: false, archived: false, muted: false };
      const pick = (key) =>
        req.body[key] === undefined ? Boolean(previous[key]) : req.body[key] === true;
      const next = { pinned: pick('pinned'), archived: pick('archived'), muted: pick('muted') };

      await db(
        `INSERT INTO conversation_settings(conversation_id,user_id,pinned,archived,muted)
         VALUES($1,$2,$3,$4,$5)
         ON CONFLICT (conversation_id,user_id) DO UPDATE
           SET pinned=EXCLUDED.pinned, archived=EXCLUDED.archived, muted=EXCLUDED.muted`,
        [req.params.id, req.user.sub, next.pinned, next.archived, next.muted]
      );
      res.json(next);
    } catch (err) {
      return fail(res, err, 'update-conversation-settings');
    }
  });

  // ---------------------------------------------------------------- statuses

  app.get('/statuses', auth, async (req, res) => {
    try {
      const r = await db(
        `SELECT s.id,s.user_id,s.kind,s.body,s.media_url,s.created_at,
                u.display_name,u.username,u.avatar_url,
                p.status AS status_rule,p.status_exclude,p.status_only,
                (SELECT COUNT(*)::int FROM status_views v WHERE v.status_id=s.id) AS viewer_count,
                EXISTS(SELECT 1 FROM status_views v2 WHERE v2.status_id=s.id AND v2.viewer_id=$1)
                  AS seen_by_me
         FROM statuses s
         JOIN users u ON u.id=s.user_id
         LEFT JOIN user_privacy p ON p.user_id=s.user_id
         WHERE s.expires_at>NOW()
         ORDER BY s.created_at DESC
         LIMIT 200`,
        [req.user.sub]
      );
      const visibleRows = [];
      for (const row of r.rows) {
        if (await statusVisible(row, req.user.sub)) visibleRows.push(row);
      }
      const out = [];

      // Seeing a status update says nothing about a profile photo set to "My contacts" or
      // "Nobody", so the photo rule is applied separately from the status rule.
      const authorIds = [...new Set(visibleRows.map(row => row.user_id))];
      const rules = new Map();
      if (authorIds.length) {
        const found = await db(
          'SELECT user_id,profile_photo FROM user_privacy WHERE user_id = ANY($1::uuid[])',
          [authorIds]
        );
        for (const row of found.rows) rules.set(row.user_id, row.profile_photo || 'everyone');
      }
      const connected = await connectedTo(req.user.sub, authorIds);
      const canSeePhoto = userId => {
        const rule = rules.get(userId) || 'everyone';
        if (rule === 'nobody') return false;
        if (rule === 'contacts') return connected.has(userId);
        return true;
      };

      for (const row of visibleRows) {
        out.push({
          id: row.id,
          user_id: row.user_id,
          user_name: row.display_name,
          avatar_url: canSeePhoto(row.user_id) ? row.avatar_url : null,
          kind: row.kind,
          body: row.body,
          media_url: row.media_url,
          created_at: row.created_at,
          viewer_count: row.viewer_count,
          seen_by_me: row.seen_by_me
        });
      }
      res.json(out);
    } catch (err) {
      return fail(res, err, 'statuses');
    }
  });

  app.post('/statuses', auth, async (req, res) => {
    try {
      const body = String(req.body.body || '').trim();
      const encrypted = Boolean(req.body.e2ee);
      const mediaDataUrl = req.body.mediaDataUrl || null;
      if (!body && !mediaDataUrl) {
        return res.status(400).json({
          error: 'INVALID_STATUS',
          message: 'Write something or add a photo.'
        });
      }
      if (body.length > 280) {
        return res.status(400).json({
          error: 'INVALID_STATUS',
          message: 'Status updates are limited to 280 characters.'
        });
      }
      let mediaUrl = null;
      if (mediaDataUrl) {
        const stored = await storeDataUrl(req, mediaDataUrl, 'status');
        if (!stored) {
          return res.status(400).json({
            error: 'INVALID_IMAGE',
            message: 'That photo could not be read. Try a different one.'
          });
        }
        mediaUrl = stored.url;
      }
      const r = await db(
        `INSERT INTO statuses(user_id,kind,body,media_url) VALUES($1,$2,$3,$4)
         RETURNING id,user_id,kind,body,media_url,created_at,expires_at`,
        [req.user.sub, mediaUrl ? 'image' : 'text', body || null, mediaUrl]
      );
      const me = await db('SELECT display_name,avatar_url FROM users WHERE id=$1', [req.user.sub]);
      res.json({
        ...r.rows[0],
        user_name: me.rowCount ? me.rows[0].display_name : null,
        avatar_url: me.rowCount ? me.rows[0].avatar_url : null,
        viewer_count: 0,
        seen_by_me: true
      });
    } catch (err) {
      return fail(res, err, 'create-status');
    }
  });

  app.post('/statuses/:id/view', auth, async (req, res) => {
    try {
      const r = await db(
        `SELECT s.*,p.status AS status_rule,p.status_exclude,p.status_only
         FROM statuses s LEFT JOIN user_privacy p ON p.user_id=s.user_id
         WHERE s.id=$1 AND s.expires_at>NOW()`,
        [req.params.id]
      );
      if (!r.rowCount) return res.status(404).json({ error: 'NOT_FOUND' });
      if (!(await statusVisible(r.rows[0], req.user.sub))) {
        return res.status(403).json({ error: 'NOT_VISIBLE' });
      }
      await db(
        'INSERT INTO status_views(status_id,viewer_id) VALUES($1,$2) ON CONFLICT DO NOTHING',
        [req.params.id, req.user.sub]
      );
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'view-status');
    }
  });

  // ---------------------------------------------------------------- call history

  app.get('/calls', auth, async (req, res) => {
    try {
      const r = await db(
        `SELECT c.id,c.caller_id,c.callee_id,c.kind,c.status,c.created_at,c.ended_at,
                cu.display_name AS caller_name,cu.username AS caller_username,
                cu.avatar_url AS caller_avatar_url,cu.online AS caller_online,
                tu.display_name AS callee_name,tu.username AS callee_username,
                tu.avatar_url AS callee_avatar_url,tu.online AS callee_online
         FROM calls c
         JOIN users cu ON cu.id=c.caller_id
         JOIN users tu ON tu.id=c.callee_id
         WHERE c.caller_id=$1 OR c.callee_id=$1
         ORDER BY c.created_at DESC LIMIT 200`,
        [req.user.sub]
      );
      // Call history is just another surface, so the other person's privacy rules apply here too:
      // a photo or presence set to "Nobody" is stripped rather than shown in the call log.
      const peerIds = [...new Set(r.rows.flatMap(row => [row.caller_id, row.callee_id]))];
      const rules = await db(
        'SELECT user_id,profile_photo,last_seen FROM user_privacy WHERE user_id = ANY($1::uuid[])',
        [peerIds]
      );
      const ruleOf = new Map(rules.rows.map(row => [row.user_id, row]));
      const known = await connectedTo(req.user.sub, peerIds);
      const canSee = (userId, column) => {
        const rule = ruleOf.get(userId);
        if (!rule) return true;
        const value = column === 'profile_photo' ? rule.profile_photo : rule.last_seen;
        if (value === 'nobody') return false;
        if (value === 'contacts') return known.has(userId);
        return true;
      };

      res.json(
        r.rows.map(row => {
          const outgoing = row.caller_id === req.user.sub;
          const peerId = outgoing ? row.callee_id : row.caller_id;
          const photoVisible = canSee(peerId, 'profile_photo');
          const presenceVisible = canSee(peerId, 'last_seen');
          return {
            id: row.id,
            caller_id: row.caller_id,
            callee_id: row.callee_id,
            peer: outgoing
              ? {
                  id: row.callee_id,
                  display_name: row.callee_name,
                  username: row.callee_username,
                  avatar_url: photoVisible ? row.callee_avatar_url : null,
                  online: presenceVisible ? row.callee_online : null
                }
              : {
                  id: row.caller_id,
                  display_name: row.caller_name,
                  username: row.caller_username,
                  avatar_url: photoVisible ? row.caller_avatar_url : null,
                  online: presenceVisible ? row.caller_online : null
                },
            kind: row.kind,
            status: row.status,
            incoming: !outgoing,
            duration_seconds: row.ended_at && row.created_at
              ? Math.max(
                  0,
                  Math.round(
                    (new Date(row.ended_at).getTime() - new Date(row.created_at).getTime()) / 1000
                  )
                )
              : 0,
            created_at: row.created_at
          };
        })
      );
    } catch (err) {
      return fail(res, err, 'call-history');
    }
  });

  // ---------------------------------------------------------------- notification settings

  const NOTIFICATION_KEYS = ['messages', 'calls', 'status', 'ai', 'sound', 'vibration', 'preview'];

  app.get('/notifications/settings', auth, async (req, res) => {
    try {
      const r = await db('SELECT * FROM user_notification_settings WHERE user_id=$1', [
        req.user.sub
      ]);
      res.json(r.rowCount ? r.rows[0] : {});
    } catch (err) {
      return fail(res, err, 'notification-settings');
    }
  });

  app.post('/notifications/:id/read', auth, async (req, res) => {
    try {
      // The column is read_at (see schema.sql); the boolean only exists in the API response.
      const r = await db(
        'UPDATE notifications SET read_at=COALESCE(read_at,NOW()) WHERE id=$1 AND user_id=$2 AND read_at IS NULL',
        [String(req.params.id || ''), req.user.sub]
      );
      if (!r.rowCount) {
        const exists = await db('SELECT 1 FROM notifications WHERE id=$1 AND user_id=$2', [
          String(req.params.id || ''),
          req.user.sub
        ]);
        if (!exists.rowCount) return res.status(404).json({ error: 'NOT_FOUND' });
      }
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'read-notification');
    }
  });

  app.post('/notifications/read-all', auth, async (req, res) => {
    try {
      await db(
        'UPDATE notifications SET read_at=COALESCE(read_at,NOW()) WHERE user_id=$1 AND read_at IS NULL',
        [req.user.sub]
      );
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'read-all-notifications');
    }
  });

  app.patch('/notifications/settings', auth, async (req, res) => {
    try {
      const current = await db('SELECT * FROM user_notification_settings WHERE user_id=$1', [
        req.user.sub
      ]);
      const previous = current.rowCount ? current.rows[0] : {};
      const values = NOTIFICATION_KEYS.map(key =>
        req.body[key] === undefined
          ? previous[key] === undefined
            ? true
            : Boolean(previous[key])
          : Boolean(req.body[key])
      );

      const params = [req.user.sub, ...values];
      const placeholders = NOTIFICATION_KEYS.map((_, index) => `$${index + 2}`);
      const assignments = NOTIFICATION_KEYS.map(
        (key, index) => `${key}=$${index + 2}`
      );
      await db(
        `INSERT INTO user_notification_settings(user_id,${NOTIFICATION_KEYS.join(',')})
         VALUES($1,${placeholders.join(',')})
         ON CONFLICT (user_id) DO UPDATE SET ${assignments.join(',')}, updated_at=NOW()`,
        params
      );
      const r = await db('SELECT * FROM user_notification_settings WHERE user_id=$1', [
        req.user.sub
      ]);
      res.json(r.rows[0]);
    } catch (err) {
      return fail(res, err, 'update-notification-settings');
    }
  });

  // ---------------------------------------------------------------- privacy

  const DEFAULT_PRIVACY = {
    last_seen: 'everyone',
    profile_photo: 'everyone',
    about: 'everyone',
    status: 'contacts',
    read_receipts: true,
    typing_indicators: true,
    group_invites: 'everyone',
    calls: 'everyone',
    disappearing_seconds: 0,
    status_exclude: [],
    status_only: []
  };

  const privacyPayload = row => ({
    last_seen: row.last_seen,
    profile_photo: row.profile_photo,
    about: row.about,
    status: row.status,
    read_receipts: Boolean(row.read_receipts),
    typing_indicators: Boolean(row.typing_indicators),
    group_invites: row.group_invites,
    calls: row.calls,
    disappearing_seconds: Number(row.disappearing_seconds || 0),
    status_exclude: Array.isArray(row.status_exclude) ? row.status_exclude : [],
    status_only: Array.isArray(row.status_only) ? row.status_only : []
  });

  app.get('/privacy', auth, async (req, res) => {
    try {
      const r = await db('SELECT * FROM user_privacy WHERE user_id=$1', [req.user.sub]);
      res.json(privacyPayload(r.rowCount ? r.rows[0] : DEFAULT_PRIVACY));
    } catch (err) {
      return fail(res, err, 'privacy');
    }
  });

  app.patch('/privacy', auth, async (req, res) => {
    try {
      const body = req.body || {};
      const current = await db('SELECT * FROM user_privacy WHERE user_id=$1', [req.user.sub]);
      const previous = current.rowCount ? current.rows[0] : DEFAULT_PRIVACY;
      const audience = (value, allowed, fallback) => {
        const candidate = value === undefined || value === null ? '' : String(value);
        return allowed.includes(candidate) ? candidate : fallback;
      };
      const next = {
        last_seen: audience(body.last_seen, AUDIENCES, previous.last_seen),
        profile_photo: audience(body.profile_photo, AUDIENCES, previous.profile_photo),
        about: audience(body.about, AUDIENCES, previous.about),
        status: audience(body.status, STATUS_AUDIENCES, previous.status),
        group_invites: audience(body.group_invites, AUDIENCES, previous.group_invites),
        calls: audience(body.calls, AUDIENCES, previous.calls),
        read_receipts:
          body.read_receipts === undefined ? Boolean(previous.read_receipts) : Boolean(body.read_receipts),
        typing_indicators:
          body.typing_indicators === undefined
            ? Boolean(previous.typing_indicators)
            : Boolean(body.typing_indicators),
        disappearing_seconds: Math.max(
          0,
          Math.min(
            604_800,
            Number(
              body.disappearing_seconds === undefined
                ? previous.disappearing_seconds
                : body.disappearing_seconds
            ) || 0
          )
        ),
        status_exclude: Array.isArray(body.status_exclude)
          ? body.status_exclude.map(String).slice(0, 500)
          : Array.isArray(previous.status_exclude)
            ? previous.status_exclude
            : [],
        status_only: Array.isArray(body.status_only)
          ? body.status_only.map(String).slice(0, 500)
          : Array.isArray(previous.status_only)
            ? previous.status_only
            : []
      };

      const r = await db(
        `INSERT INTO user_privacy(user_id,last_seen,profile_photo,about,status,read_receipts,
                                  typing_indicators,group_invites,calls,disappearing_seconds,
                                  status_exclude,status_only)
         VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::uuid[],$12::uuid[])
         ON CONFLICT (user_id) DO UPDATE SET
           last_seen=EXCLUDED.last_seen,
           profile_photo=EXCLUDED.profile_photo,
           about=EXCLUDED.about,
           status=EXCLUDED.status,
           read_receipts=EXCLUDED.read_receipts,
           typing_indicators=EXCLUDED.typing_indicators,
           group_invites=EXCLUDED.group_invites,
           calls=EXCLUDED.calls,
           disappearing_seconds=EXCLUDED.disappearing_seconds,
           status_exclude=EXCLUDED.status_exclude,
           status_only=EXCLUDED.status_only,
           updated_at=NOW()
         RETURNING *`,
        [
          req.user.sub,
          next.last_seen,
          next.profile_photo,
          next.about,
          next.status,
          next.read_receipts,
          next.typing_indicators,
          next.group_invites,
          next.calls,
          next.disappearing_seconds,
          next.status_exclude,
          next.status_only
        ]
      );
      res.json(privacyPayload(r.rows[0]));
    } catch (err) {
      return fail(res, err, 'update-privacy');
    }
  });

  // ---------------------------------------------------------------- devices

  app.get('/devices', auth, async (req, res) => {
    try {
      const r = await db(
        `SELECT session_key,label,platform,created_at,last_seen_at
         FROM user_sessions WHERE user_id=$1 AND NOT revoked
         ORDER BY last_seen_at DESC NULLS LAST, created_at DESC`,
        [req.user.sub]
      );
      res.json(
        r.rows.map(row => ({
          id: row.session_key,
          label: row.label,
          platform: row.platform,
          created_at: row.created_at,
          last_seen_at: row.last_seen_at,
          current: row.session_key === (req.user.sid || '')
        }))
      );
    } catch (err) {
      return fail(res, err, 'devices');
    }
  });

  app.delete('/devices/:id', auth, async (req, res) => {
    try {
      const r = await db(
        `UPDATE user_sessions SET revoked=true
         WHERE user_id=$1 AND session_key=$2 AND NOT revoked`,
        [req.user.sub, String(req.params.id || '')]
      );
      if (!r.rowCount) return res.status(404).json({ error: 'NOT_FOUND' });
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'revoke-session');
    }
  });

  app.post('/devices/logout-others', auth, async (req, res) => {
    try {
      await db(
        `UPDATE user_sessions SET revoked=true
         WHERE user_id=$1 AND session_key<>$2 AND NOT revoked`,
        [req.user.sub, req.user.sid || '']
      );
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'logout-others');
    }
  });

  // ---------------------------------------------------------------- global search

  app.get('/search', auth, async (req, res) => {
    try {
      const q = String(req.query.q || '').trim();
      if (q.length < 2) {
        return res.json({ users: [], contacts: [], chats: [], messages: [], ai: [], total: 0 });
      }
      const like = `%${q}%`;
      const me = req.user.sub;

      const people = await db(
        `SELECT u.id,u.display_name,u.username,u.avatar_url,u.online,u.last_seen,
                p.profile_photo,p.last_seen AS last_seen_rule
         FROM users u
         LEFT JOIN user_privacy p ON p.user_id=u.id
         WHERE u.id<>$1
           AND (u.display_name ILIKE $2 OR u.username ILIKE $2)
           AND NOT EXISTS (
             SELECT 1 FROM user_blocks b
             WHERE (b.blocker_id=$1 AND b.blocked_id=u.id)
                OR (b.blocker_id=u.id AND b.blocked_id=$1)
           )
         ORDER BY u.online DESC NULLS LAST, u.display_name
         LIMIT 20`,
        [me, like]
      );

      // Search results honour the same rules as a profile: a photo, presence or
      // last-seen set to "Nobody" never comes back through search.
      const publicPeople = [];
      for (const row of people.rows) {
        const photoVisible = await allows(me, row.id, row.profile_photo || 'everyone');
        const presenceVisible = await allows(me, row.id, row.last_seen_rule || 'everyone');
        const sanitised = {
          ...row,
          avatar_url: photoVisible ? row.avatar_url : null,
          online: presenceVisible ? row.online : null,
          last_seen: presenceVisible ? row.last_seen : null,
          profile_photo: undefined,
          last_seen_rule: undefined
        };
        delete sanitised.profile_photo;
        delete sanitised.last_seen_rule;
        publicPeople.push(sanitised);
      }

      const knownContacts = await connectedTo(me, publicPeople.map(row => row.id));
      const contacts = publicPeople.filter(row => knownContacts.has(row.id));

      const chats = await db(
        `SELECT c.id,c.kind,c.title,c.created_at,
                (
                  SELECT json_build_object('id',m.id,'body',m.body,'senderId',m.sender_id,
                                           'createdAt',m.created_at,'type',m.message_type)
                  FROM messages m
                  WHERE m.conversation_id=c.id AND m.deleted_at IS NULL
                  ORDER BY m.created_at DESC LIMIT 1
                ) AS last_message,
CASE WHEN c.kind='direct' THEN (
                   SELECT json_build_object(
                     'id',u.id,'displayName',u.display_name,'username',u.username,
                     'avatarUrl',CASE WHEN COALESCE(pp.profile_photo,'everyone')<>'nobody'
                                      OR $1=u.id THEN u.avatar_url ELSE NULL END,
                     'online',CASE WHEN COALESCE(pp.last_seen,'everyone')<>'nobody'
                                      OR $1=u.id THEN u.online ELSE NULL END)
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
         JOIN conversation_members cm ON cm.conversation_id=c.id AND cm.user_id=$1
         LEFT JOIN conversation_settings s ON s.conversation_id=c.id AND s.user_id=$1
         WHERE COALESCE(c.title,'') ILIKE $2
            OR EXISTS (
                SELECT 1 FROM conversation_members x
                JOIN users u2 ON u2.id=x.user_id
                WHERE x.conversation_id=c.id AND x.user_id<>$1
                  AND (u2.display_name ILIKE $2 OR u2.username ILIKE $2)
              )
         ORDER BY COALESCE(
                  (SELECT MAX(m3.created_at) FROM messages m3 WHERE m3.conversation_id=c.id),
                  c.created_at) DESC
         LIMIT 20`,
        [me, like]
      );

      // A message hit also carries just enough of its conversation for the client to open the
      // right chat: a group needs its title, a direct chat needs the other person's name.
      const messages = await db(
        `SELECT m.id,m.conversation_id,m.sender_id,m.body,m.message_type,m.created_at,
                u.display_name AS sender_name,
                c.kind AS conversation_kind,c.title AS conversation_title,
                CASE WHEN c.kind='direct' THEN (
                  SELECT u2.id FROM conversation_members cm2
                  JOIN users u2 ON u2.id=cm2.user_id
                  WHERE cm2.conversation_id=c.id AND cm2.user_id<>$1 LIMIT 1
                ) ELSE NULL END AS conversation_peer_id,
                CASE WHEN c.kind='direct' THEN (
                  SELECT u2.display_name FROM conversation_members cm2
                  JOIN users u2 ON u2.id=cm2.user_id
                  WHERE cm2.conversation_id=c.id AND cm2.user_id<>$1 LIMIT 1
                ) ELSE NULL END AS conversation_peer_name,
                CASE WHEN COALESCE(p.profile_photo,'everyone')<>'nobody' OR $1=m.sender_id
                     THEN u.avatar_url ELSE NULL END AS sender_avatar_url
         FROM messages m
         JOIN conversation_members cm ON cm.conversation_id=m.conversation_id AND cm.user_id=$1
         JOIN conversations c ON c.id=m.conversation_id
         JOIN users u ON u.id=m.sender_id
         LEFT JOIN user_privacy p ON p.user_id=m.sender_id
         WHERE m.deleted_at IS NULL AND m.body ILIKE $2
         ORDER BY m.created_at DESC LIMIT 20`,
        [me, like]
      );

      const ai = await db(
        `SELECT id,title,created_at,updated_at FROM ai_conversations
         WHERE user_id=$1 AND title ILIKE $2
         ORDER BY updated_at DESC LIMIT 10`,
        [me, like]
      );

      res.json({
        users: publicPeople,
        contacts,
        chats: chats.rows,
        messages: messages.rows,
        ai: ai.rows,
        total: people.rowCount + chats.rowCount + messages.rowCount + ai.rowCount
      });
    } catch (err) {
      return fail(res, err, 'search');
    }
  });

  // ---------------------------------------------------------------- HolidayLink AI

  /** Small in-memory throttle: stops one client from burning the Gemini quota. */
  const aiRateOk = userId => {
    const now = Date.now();
    const entry = aiHits.get(userId) || { first: now, last: 0 };
    if (entry.last && now - entry.last < AI_MIN_GAP_MS) return false;
    if (now - entry.first > 60_000) entry.first = now;
    entry.last = now;
    aiHits.set(userId, entry);
    return true;
  };

  app.get('/ai/conversations', auth, async (req, res) => {
    try {
      const r = await db(
        'SELECT id,title,created_at,updated_at FROM ai_conversations WHERE user_id=$1 ORDER BY updated_at DESC LIMIT 50',
        [req.user.sub]
      );
      res.json(r.rows);
    } catch (err) {
      return fail(res, err, 'ai-conversations');
    }
  });

  app.get('/ai/conversations/:id/messages', auth, async (req, res) => {
    try {
      const owner = await db('SELECT user_id FROM ai_conversations WHERE id=$1', [req.params.id]);
      if (!owner.rowCount || owner.rows[0].user_id !== req.user.sub) {
        return res.status(404).json({ error: 'NOT_FOUND' });
      }
      const r = await db(
        'SELECT id,conversation_id,role,text,failed,created_at FROM ai_messages WHERE conversation_id=$1 ORDER BY created_at LIMIT 200',
        [req.params.id]
      );
      res.json(r.rows);
    } catch (err) {
      return fail(res, err, 'ai-messages');
    }
  });

  app.delete('/ai/conversations/:id', auth, async (req, res) => {
    try {
      await db('DELETE FROM ai_conversations WHERE id=$1 AND user_id=$2', [
        req.params.id,
        req.user.sub
      ]);
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'delete-ai-conversation');
    }
  });

  app.delete('/ai/conversations', auth, async (req, res) => {
    try {
      await db('DELETE FROM ai_conversations WHERE user_id=$1', [req.user.sub]);
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'clear-ai-history');
    }
  });

  /**
   * Shared Gemini call used by chat + send-on-behalf. Returns {ok, text} or
   * {ok:false, timeout, status}. The key never leaves the server.
   */
  const askGemini = async (systemText, contents) => {
    let text = '';
    let answered = false;
    let timedOut = false;
    let lastStatus = 0;
    for (const model of GEMINI_MODELS) {
      const attempt = new AbortController();
      const timer = setTimeout(() => attempt.abort(), AI_MODEL_TIMEOUT_MS);
      let upstream;
      try {
        upstream = await fetch(`${GEMINI_ENDPOINT}/${model}:generateContent`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'x-goog-api-key': GEMINI_KEY },
          signal: attempt.signal,
          body: JSON.stringify({ systemInstruction: { parts: [{ text: systemText }] }, contents })
        });
      } catch (err) {
        clearTimeout(timer);
        timedOut = Boolean(err) && (err.name === 'AbortError' || err.name === 'TimeoutError');
        console.error('[ai] model', model, timedOut ? 'timed out' : String((err && err.message) || err));
        continue;
      }
      clearTimeout(timer);
      if (upstream.ok) {
        const payload = await upstream.json().catch(() => null);
        text = String(
          payload?.candidates?.[0]?.content?.parts?.map(part => part.text || '').join('') || ''
        ).trim();
        answered = true;
        console.log('[ai] answered by', model);
        break;
      }
      lastStatus = upstream.status;
      const detail = await upstream.text().catch(() => '');
      console.error('[ai] model', model, lastStatus, detail.slice(0, 300));
      if (lastStatus === 400 || lastStatus === 401 || lastStatus === 403) break;
    }
    return { ok: answered, text, timedOut, status: lastStatus };
  };

  /**
   * The only place Gemini is ever called. The key stays in the server environment, so the Android
   * package contains no credential and nothing can be lifted off a device.
   */
  app.post('/ai/chat', auth, async (req, res) => {
    const prompt = String(req.body.message || '').trim();
    if (!prompt || prompt.length > 4000) {
      return res.status(400).json({
        error: 'INVALID_MESSAGE',
        message: 'Write a message of up to 4000 characters.'
      });
    }
    if (!GEMINI_KEY) {
      return res.status(503).json({
        error: 'AI_NOT_CONFIGURED',
        message: 'HolidayLink AI is not available right now. Please try again shortly.'
      });
    }
    if (!aiRateOk(req.user.sub)) {
      return res.status(429).json({
        error: 'AI_RATE_LIMITED',
        message: "You're sending messages to HolidayLink AI too quickly. Take a short break."
      });
    }

    // "Save AI chats" turned off: nothing is written, the conversation never leaves the device,
    // and the client sends the turns it already holds instead of the server reading them back.
    const save = req.body.save !== false;
    const styleInstruction =
      AI_RESPONSE_STYLES[String(req.body.style || '')] || AI_RESPONSE_STYLES.balanced;

    let conversationId = save ? String(req.body.conversationId || '') : '';
    let userMessageId = null;
    try {
      let contents;
      if (save) {
        if (conversationId) {
          const owner = await db('SELECT user_id FROM ai_conversations WHERE id=$1', [conversationId]);
          if (!owner.rowCount || owner.rows[0].user_id !== req.user.sub) conversationId = '';
        }
        if (!conversationId) {
          const created = await db(
            'INSERT INTO ai_conversations(user_id,title) VALUES($1,$2) RETURNING id',
            [req.user.sub, prompt.slice(0, 60)]
          );
          conversationId = created.rows[0].id;
        }

        const saved = await db(
          `INSERT INTO ai_messages(conversation_id,role,text) VALUES($1,'user',$2) RETURNING id,created_at`,
          [conversationId, prompt]
        );
        userMessageId = saved.rows[0].id;

        const history = await db(
          'SELECT role,text FROM ai_messages WHERE conversation_id=$1 ORDER BY created_at LIMIT 60',
          [conversationId]
        );
        contents = history.rows.map(row => ({
          role: row.role === 'assistant' ? 'model' : 'user',
          parts: [{ text: row.text }]
        }));
      } else {
        const prior = Array.isArray(req.body.history) ? req.body.history.slice(-40) : [];
        contents = prior
          .map(row => ({
            role: String(row && row.role) === 'assistant' ? 'model' : 'user',
            parts: [{ text: String((row && row.text) || '').slice(0, 4000) }]
          }))
          .filter(part => part.parts[0].text.trim() !== '');
      }
      contents.push({ role: 'user', parts: [{ text: prompt }] });

      // Each configured model is tried in turn, so one retired model id cannot take AI offline.
      let text = '';
      let answered = false;
      let timedOut = false;
      let lastStatus = 0;
      for (const model of GEMINI_MODELS) {
        const attempt = new AbortController();
        const timer = setTimeout(() => attempt.abort(), AI_MODEL_TIMEOUT_MS);
        let upstream;
        try {
          upstream = await fetch(`${GEMINI_ENDPOINT}/${model}:generateContent`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'x-goog-api-key': GEMINI_KEY },
            signal: attempt.signal,
            body: JSON.stringify({
              systemInstruction: { parts: [{ text: `${AI_SYSTEM_PROMPT} ${styleInstruction}` }] },
              contents
            })
          });
        } catch (err) {
          clearTimeout(timer);
          timedOut = Boolean(err) && (err.name === 'AbortError' || err.name === 'TimeoutError');
          console.error('[ai] model', model, timedOut ? 'timed out' : String((err && err.message) || err));
          continue;
        }
        clearTimeout(timer);

        if (upstream.ok) {
          const payload = await upstream.json().catch(() => null);
          text = String(
            payload?.candidates?.[0]?.content?.parts?.map(part => part.text || '').join('') || ''
          ).trim();
          answered = true;
          console.log('[ai] answered by', model);
          break;
        }

        lastStatus = upstream.status;
        const detail = await upstream.text().catch(() => '');
        console.error('[ai] model', model, lastStatus, detail.slice(0, 300));
        // A bad key or a malformed request fails on every model, so stop instead of retrying.
        if (lastStatus === 400 || lastStatus === 401 || lastStatus === 403) break;
      }

      if (!answered) {
        if (userMessageId) {
          await db('UPDATE ai_messages SET failed=true WHERE id=$1', [userMessageId]);
        }
        if (!lastStatus && timedOut) {
          return res.status(504).json({
            error: 'AI_TIMEOUT',
            message: 'HolidayLink AI took too long to answer. Please try again.'
          });
        }
        return res.status(lastStatus === 429 ? 429 : 502).json({
          error: lastStatus === 429 ? 'AI_RATE_LIMITED' : 'AI_UPSTREAM_ERROR',
          message: 'HolidayLink AI could not answer just now. Please try again.'
        });
      }

      const answer = text || 'HolidayLink AI could not answer that. Try rephrasing your question.';
      let messageId = '';
      if (save) {
        const reply = await db(
          `INSERT INTO ai_messages(conversation_id,role,text)
           VALUES($1,'assistant',$2) RETURNING id,created_at`,
          [conversationId, answer]
        );
        messageId = reply.rows[0].id;
        await db('UPDATE ai_conversations SET updated_at=NOW() WHERE id=$1', [conversationId]);
      }

      res.json({
        ok: true,
        conversationId,
        messageId,
        saved: save,
        text: answer
      });
    } catch (err) {
      if (err && (err.name === 'AbortError' || err.name === 'TimeoutError')) {
        return res.status(504).json({
          error: 'AI_TIMEOUT',
          message: 'HolidayLink AI took too long to answer. Please try again.'
        });
      }
      return fail(res, err, 'ai-chat');
    }
  });

  // ---------------------------------------------------------------- user bots (Telegram-style)

  app.get('/ai/bots', auth, async (req, res) => {
    try {
      const r = await db(
        `SELECT b.id,b.username,b.display_name,b.owner_id,b.created_at,u.display_name AS owner_name
         FROM ai_bots b JOIN users u ON u.id=b.owner_id
         ORDER BY b.created_at DESC LIMIT 200`
      );
      res.json(r.rows.map(row => ({
        id: row.id, username: row.username, display_name: row.display_name,
        owner_id: row.owner_id, owner_name: row.owner_name, created_at: row.created_at,
        mine: row.owner_id === req.user.sub
      })));
    } catch (err) {
      return fail(res, err, 'ai-bots');
    }
  });

  app.post('/ai/bots', auth, async (req, res) => {
    try {
      const username = String(req.body.username || '').trim().replace(/^@/, '').toLowerCase();
      const displayName = String(req.body.displayName || '').trim();
      const instructions = String(req.body.instructions || '').trim().slice(0, 2000);
      if (!/^[a-z0-9_]{3,32}$/.test(username)) {
        return res.status(400).json({ error: 'INVALID_USERNAME', message: 'Bot username must be 3-32 characters: letters, numbers or underscore.' });
      }
      if (!displayName || displayName.length > 120) {
        return res.status(400).json({ error: 'INVALID_DISPLAY_NAME', message: 'Give the bot a display name up to 120 characters.' });
      }
      const clash = await db(
        'SELECT 1 FROM users WHERE LOWER(username)=LOWER($1) UNION SELECT 1 FROM ai_bots WHERE LOWER(username)=LOWER($1)',
        [username]
      );
      if (clash.rowCount) {
        return res.status(409).json({ error: 'USERNAME_TAKEN', message: 'That @name is already taken.' });
      }
      const r = await db(
        'INSERT INTO ai_bots(owner_id,username,display_name,instructions) VALUES($1,$2,$3,$4) RETURNING id,username,display_name,created_at',
        [req.user.sub, username, displayName, instructions]
      );
      res.status(201).json({ ...r.rows[0], owner_id: req.user.sub, mine: true });
    } catch (err) {
      return fail(res, err, 'create-bot');
    }
  });

  app.delete('/ai/bots/:id', auth, async (req, res) => {
    try {
      const r = await db('DELETE FROM ai_bots WHERE id=$1 AND owner_id=$2', [req.params.id, req.user.sub]);
      if (!r.rowCount) return res.status(404).json({ error: 'NOT_FOUND' });
      res.json({ ok: true });
    } catch (err) {
      return fail(res, err, 'delete-bot');
    }
  });

  /**
   * Instruct the AI to send a message on your behalf (website send_message parity).
   * The message is stored as your own message in the conversation, never as a bot.
   */
  app.post('/ai/send', auth, async (req, res) => {
    try {
      const conversationId = String(req.body.conversationId || '');
      const prompt = String(req.body.prompt || '').trim();
      const botId = String(req.body.botId || '');
      if (!conversationId || !prompt || prompt.length > 4000) {
        return res.status(400).json({ error: 'INVALID_MESSAGE', message: 'Write an instruction up to 4000 characters.' });
      }
      if (!(await roleIn(conversationId, req.user.sub))) {
        return res.status(403).json({ error: 'NOT_MEMBER', message: "You're no longer part of this chat." });
      }
      if (!GEMINI_KEY) {
        return res.status(503).json({ error: 'AI_NOT_CONFIGURED', message: 'HolidayLink AI is not available right now. Please try again shortly.' });
      }
      if (!aiRateOk(req.user.sub)) {
        return res.status(429).json({ error: 'AI_RATE_LIMITED', message: "You're asking HolidayLink AI too quickly. Take a short break." });
      }
      let systemText = `${AI_SYSTEM_PROMPT} ${AI_RESPONSE_STYLES.balanced} You are sending this message on the user's behalf into their chat. Write ONLY the message text, no preamble, no quotes.`;
      if (botId) {
        const bot = await db('SELECT display_name,instructions FROM ai_bots WHERE id=$1', [botId]);
        if (bot.rowCount) {
          systemText = `${AI_SYSTEM_PROMPT} You are ${bot.rows[0].display_name}. ${bot.rows[0].instructions || ''} You are sending this message on the user's behalf into their chat. Write ONLY the message text, no preamble, no quotes.`;
        }
      }
      const result = await askGemini(systemText, [{ role: 'user', parts: [{ text: prompt }] }]);
      if (!result.ok) {
        return res.status(result.status === 429 ? 429 : 502).json({
          error: result.status === 429 ? 'AI_RATE_LIMITED' : 'AI_UPSTREAM_ERROR',
          message: 'HolidayLink AI could not answer just now. Please try again.'
        });
      }
      const answer = result.text || 'HolidayLink AI could not answer that. Try rephrasing.';
      const inserted = await db(
        `INSERT INTO messages(conversation_id,sender_id,body,message_type)
         VALUES($1,$2,$3,'text') RETURNING id,conversation_id,sender_id,body,message_type,created_at`,
        [conversationId, req.user.sub, answer]
      );
      const [hydrated] = await hydrateMessages(
        [{ ...inserted.rows[0], sender_name: null, sender_username: null, sender_avatar_url: null }],
        req.user.sub
      );
      broadcast(conversationId, { type: 'message', message: hydrated });
      res.json({ ok: true, message: hydrated, text: answer });
    } catch (err) {
      return fail(res, err, 'ai-send');
    }
  });

  return { storeDataUrl, hydrateMessages, isBlockedEitherWay, areConnected, connectedTo };
}
