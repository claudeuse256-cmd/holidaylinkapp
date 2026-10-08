-- HolidayLink V2.1: Firebase retirement, 2FA passwords and social videos.
-- Firebase is no longer the source of truth. New admin uploads are stored in the new backend.

ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_password_hash TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_updated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS users_two_factor_idx ON users(two_factor_enabled);

CREATE TABLE IF NOT EXISTS videos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  video_url TEXT NOT NULL,
  thumbnail_url TEXT,
  caption VARCHAR(1000) NOT NULL DEFAULT '',
  views BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS videos_created_idx ON videos(created_at DESC);
CREATE INDEX IF NOT EXISTS videos_user_created_idx ON videos(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS video_likes (
  video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(video_id,user_id)
);

CREATE TABLE IF NOT EXISTS video_comments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body VARCHAR(1000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS video_comments_video_idx ON video_comments(video_id, created_at DESC);

CREATE TABLE IF NOT EXISTS video_follows (
  follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(follower_id,creator_id),
  CHECK(follower_id <> creator_id)
);
