-- HolidayLink V2.3: channels/communities (reuse conversations), video saves (favorites).
-- Channels + communities reuse conversations/members/messages so no new chat tables.
-- Run on startup via runDatabaseMigrations (IF NOT EXISTS / guarded).

-- Allow channel + community kinds alongside direct/group.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'conversations_kind_check'
  ) THEN
    ALTER TABLE conversations DROP CONSTRAINT conversations_kind_check;
  END IF;
  ALTER TABLE conversations ADD CONSTRAINT conversations_kind_check
    CHECK (kind IN ('direct','group','channel','community'));
EXCEPTION WHEN OTHERS THEN
  -- Constraint already migrated or table missing; startup must not fail.
  NULL;
END $$;

-- Favorites: bookmark videos separately from likes.
CREATE TABLE IF NOT EXISTS video_saves (
  video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(video_id,user_id)
);
