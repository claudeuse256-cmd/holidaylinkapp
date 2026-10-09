-- HolidayLink V2.5: user-created AI bots (Telegram-style).
-- Bots are personas with custom instructions, mentionable as @username in chats.

CREATE TABLE IF NOT EXISTS ai_bots (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  username VARCHAR(32) NOT NULL UNIQUE,
  display_name VARCHAR(120) NOT NULL,
  instructions TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ai_bots_owner_idx ON ai_bots(owner_id);
