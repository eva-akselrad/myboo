CREATE TABLE IF NOT EXISTS users (
  id         TEXT PRIMARY KEY,
  name       TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  fcm_token  TEXT,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS circles (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  invite_code TEXT NOT NULL UNIQUE,
  created_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS members (
  circle_id TEXT NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
  user_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (circle_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_members_user ON members(user_id);

CREATE TABLE IF NOT EXISTS places (
  id        TEXT PRIMARY KEY,
  circle_id TEXT NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
  name      TEXT NOT NULL,
  lat       REAL NOT NULL,
  lng       REAL NOT NULL,
  radius_m  REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_places_circle ON places(circle_id);

-- Latest known location + derived state per user.
CREATE TABLE IF NOT EXISTS user_state (
  user_id    TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  lat        REAL NOT NULL,
  lng        REAL NOT NULL,
  speed      REAL,
  accuracy   REAL,
  ts         INTEGER NOT NULL,
  inside     TEXT NOT NULL DEFAULT '[]', -- JSON array of place ids currently inside
  driving    INTEGER NOT NULL DEFAULT 0,
  fast_count INTEGER NOT NULL DEFAULT 0   -- consecutive fast samples (debounce)
);
