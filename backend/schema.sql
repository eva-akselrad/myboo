-- MyBoo schema v2: email/password accounts, email-code verification & 2FA, sessions.

CREATE TABLE IF NOT EXISTS users (
  id             TEXT PRIMARY KEY,
  email          TEXT NOT NULL UNIQUE,         -- lowercased
  name           TEXT NOT NULL,
  pass_hash      TEXT NOT NULL,                -- pbkdf2$iter$salt$hash
  email_verified INTEGER NOT NULL DEFAULT 0,
  twofa_enabled  INTEGER NOT NULL DEFAULT 0,   -- email-code 2FA at sign-in
  failed_logins  INTEGER NOT NULL DEFAULT 0,
  locked_until   INTEGER NOT NULL DEFAULT 0,
  fcm_token      TEXT,
  created_at     INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash   TEXT NOT NULL UNIQUE,
  device       TEXT NOT NULL DEFAULT 'Unknown device',
  created_at   INTEGER NOT NULL,
  last_used_at INTEGER NOT NULL,
  expires_at   INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);

-- One-time email codes (verify email, sign-in 2FA, password reset).
CREATE TABLE IF NOT EXISTS challenges (
  id           TEXT PRIMARY KEY,
  user_id      TEXT REFERENCES users(id) ON DELETE CASCADE, -- NULL for decoy (unknown email)
  email        TEXT NOT NULL,
  purpose      TEXT NOT NULL,                  -- verify_email | login_2fa | reset_password
  code_hash    TEXT NOT NULL,
  attempts     INTEGER NOT NULL DEFAULT 0,
  created_at   INTEGER NOT NULL,
  last_sent_at INTEGER NOT NULL,
  expires_at   INTEGER NOT NULL,
  device       TEXT
);
CREATE INDEX IF NOT EXISTS idx_challenges_email ON challenges(email, created_at);

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

CREATE TABLE IF NOT EXISTS user_state (
  user_id    TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  lat        REAL NOT NULL,
  lng        REAL NOT NULL,
  speed      REAL,
  accuracy   REAL,
  ts         INTEGER NOT NULL,
  inside     TEXT NOT NULL DEFAULT '[]',
  driving    INTEGER NOT NULL DEFAULT 0,
  fast_count INTEGER NOT NULL DEFAULT 0
);
