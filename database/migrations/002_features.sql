-- HolidayLink feature set 002
-- Additive only: safe to run against a database that already matches schema.sql.
-- Nothing here drops or rewrites existing columns, so an older server keeps working.

-- ------------------------------------------------------------------ messages
ALTER TABLE messages ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

-- messages.reply_to already exists in schema.sql, so replies only need an index.
CREATE INDEX IF NOT EXISTS messages_expiry_idx ON messages(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS messages_reply_idx ON messages(reply_to) WHERE reply_to IS NOT NULL;

CREATE TABLE IF NOT EXISTS message_attachments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  kind VARCHAR(24) NOT NULL DEFAULT 'file',
  url TEXT,
  name TEXT,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  width INT,
  height INT,
  duration_ms BIGINT,
  waveform JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS message_attachments_message_idx ON message_attachments(message_id);

CREATE TABLE IF NOT EXISTS message_reactions (
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  emoji VARCHAR(16) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (message_id, user_id)
);

-- ------------------------------------------------------------------ conversations
CREATE TABLE IF NOT EXISTS conversation_settings (
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pinned BOOLEAN NOT NULL DEFAULT FALSE,
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  muted BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (conversation_id, user_id)
);

-- ------------------------------------------------------------------ privacy
CREATE TABLE IF NOT EXISTS user_privacy (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  last_seen VARCHAR(16) NOT NULL DEFAULT 'everyone',
  profile_photo VARCHAR(16) NOT NULL DEFAULT 'everyone',
  about VARCHAR(16) NOT NULL DEFAULT 'everyone',
  status VARCHAR(24) NOT NULL DEFAULT 'contacts',
  read_receipts BOOLEAN NOT NULL DEFAULT TRUE,
  typing_indicators BOOLEAN NOT NULL DEFAULT TRUE,
  group_invites VARCHAR(16) NOT NULL DEFAULT 'everyone',
  calls VARCHAR(16) NOT NULL DEFAULT 'everyone',
  disappearing_seconds BIGINT NOT NULL DEFAULT 0,
  status_exclude UUID[] NOT NULL DEFAULT '{}'::uuid[],
  status_only UUID[] NOT NULL DEFAULT '{}'::uuid[],
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK (last_seen IN ('everyone','contacts','nobody')),
  CHECK (profile_photo IN ('everyone','contacts','nobody')),
  CHECK (about IN ('everyone','contacts','nobody')),
  CHECK (calls IN ('everyone','contacts','nobody')),
  CHECK (group_invites IN ('everyone','contacts','nobody')),
  CHECK (status IN ('everyone','contacts','contacts_except','only_share_with'))
);

CREATE TABLE IF NOT EXISTS user_notification_settings (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  messages BOOLEAN NOT NULL DEFAULT TRUE,
  calls BOOLEAN NOT NULL DEFAULT TRUE,
  status BOOLEAN NOT NULL DEFAULT TRUE,
  ai BOOLEAN NOT NULL DEFAULT TRUE,
  sound BOOLEAN NOT NULL DEFAULT TRUE,
  vibration BOOLEAN NOT NULL DEFAULT TRUE,
  preview BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------------ safety
CREATE TABLE IF NOT EXISTS user_blocks (
  blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (blocker_id, blocked_id),
  CHECK (blocker_id <> blocked_id)
);

CREATE INDEX IF NOT EXISTS user_blocks_blocked_idx ON user_blocks(blocked_id);

CREATE TABLE IF NOT EXISTS reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_id UUID REFERENCES users(id) ON DELETE SET NULL,
  reported_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
  reason VARCHAR(64) NOT NULL,
  details TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS reports_created_idx ON reports(created_at DESC);

-- ------------------------------------------------------------------ status
CREATE TABLE IF NOT EXISTS statuses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind VARCHAR(16) NOT NULL DEFAULT 'text' CHECK (kind IN ('text','image')),
  body TEXT,
  media_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours')
);

CREATE INDEX IF NOT EXISTS statuses_user_created_idx ON statuses(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS statuses_expiry_idx ON statuses(expires_at);

CREATE TABLE IF NOT EXISTS status_views (
  status_id UUID NOT NULL REFERENCES statuses(id) ON DELETE CASCADE,
  viewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (status_id, viewer_id)
);

-- ------------------------------------------------------------------ devices
CREATE TABLE IF NOT EXISTS user_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_key VARCHAR(80) NOT NULL UNIQUE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  label TEXT NOT NULL DEFAULT 'Unknown device',
  platform VARCHAR(24) NOT NULL DEFAULT 'android',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at TIMESTAMPTZ,
  revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS user_sessions_user_idx ON user_sessions(user_id) WHERE NOT revoked;

-- ------------------------------------------------------------------ HolidayLink AI
CREATE TABLE IF NOT EXISTS ai_conversations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(160) NOT NULL DEFAULT 'New chat',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ai_conversations_user_idx ON ai_conversations(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
  role VARCHAR(16) NOT NULL CHECK (role IN ('user','assistant')),
  text TEXT NOT NULL DEFAULT '',
  failed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ai_messages_conversation_idx ON ai_messages(conversation_id, created_at);