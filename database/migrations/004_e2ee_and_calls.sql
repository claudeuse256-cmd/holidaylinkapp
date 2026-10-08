CREATE TABLE IF NOT EXISTS device_keys (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  public_key TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS device_keys_updated_idx ON device_keys(updated_at);

ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_data BYTEA;
