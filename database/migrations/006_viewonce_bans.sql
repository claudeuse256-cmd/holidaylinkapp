-- HolidayLink V2.4: view-once media + account bans.
-- Run on startup via runDatabaseMigrations (guarded, startup must not fail).

-- View-once attachments: message is deleted after the recipient opens it.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS view_once BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS messages_view_once_idx ON messages(view_once) WHERE view_once = TRUE;

-- Admin bans (website admin panel controls these; app enforces on auth).
ALTER TABLE users ADD COLUMN IF NOT EXISTS banned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS ban_reason TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS banned_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS users_banned_idx ON users(banned) WHERE banned = TRUE;
