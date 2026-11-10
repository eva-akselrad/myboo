import { dummyVerify, hashPassword, verifyPassword } from "./crypto";
import { sendCodeEmail, sendNotice, type CodePurpose } from "./email";
import { route, SESSION_TTL_MS, type Env } from "./router";
import { cleanName, err, json, newId, normEmail, randomHex, readBody, sha256, sixDigits } from "./util";

const CODE_TTL_MS = 10 * 60_000;
const RESEND_COOLDOWN_MS = 45_000;
const MAX_ATTEMPTS = 5;
const MAX_CODES_PER_HOUR = 10;
const MAX_FAILED_LOGINS = 5;
const LOCK_MS = 15 * 60_000;

interface UserRow {
  id: string; email: string; name: string; pass_hash: string;
  email_verified: number; twofa_enabled: number; failed_logins: number; locked_until: number;
}

const publicUser = (u: Pick<UserRow, "id" | "email" | "name" | "twofa_enabled">) => ({
  id: u.id, email: u.email, name: u.name, twofa_enabled: !!u.twofa_enabled,
});

const validPassword = (p: unknown): p is string => typeof p === "string" && p.length >= 8 && p.length <= 200;
const deviceOf = (req: Request, b: Record<string, unknown>) =>
  (typeof b.device === "string" && b.device.trim() ? b.device : req.headers.get("user-agent") ?? "Unknown device").slice(0, 80);

const codeHash = (challengeId: string, code: string) => sha256(`${challengeId}:${code}`);

async function userByEmail(env: Env, email: string) {
  return env.DB.prepare("SELECT * FROM users WHERE email = ?").bind(email).first<UserRow>();
}

/** Create a code challenge and email it. A null user makes a decoy (so unknown emails look identical). */
async function startChallenge(
  env: Env, user: UserRow | null, email: string, purpose: CodePurpose, device?: string,
): Promise<{ error: Response } | { id: string }> {
  const now = Date.now();
  await env.DB.prepare("DELETE FROM challenges WHERE expires_at < ?").bind(now - 3600_000).run();
  const recent = await env.DB.prepare("SELECT COUNT(*) AS n FROM challenges WHERE email = ? AND created_at > ?")
    .bind(email, now - 3600_000).first<{ n: number }>();
  if ((recent?.n ?? 0) >= MAX_CODES_PER_HOUR) return { error: err(429, "Too many codes requested. Try again later.") };

  const id = newId();
  const code = sixDigits();
  await env.DB.prepare(
    `INSERT INTO challenges (id, user_id, email, purpose, code_hash, created_at, last_sent_at, expires_at, device)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).bind(id, user?.id ?? null, email, purpose, await codeHash(id, code), now, now, now + CODE_TTL_MS, device ?? null).run();
  if (user) await sendCodeEmail(env, email, user.name, purpose, code);
  return { id };
}

async function createSession(env: Env, user: UserRow, device: string) {
  const token = randomHex(32);
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO sessions (id, user_id, token_hash, device, created_at, last_used_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)`,
  ).bind(newId(), user.id, await sha256(token), device, now, now, now + SESSION_TTL_MS).run();
  return json({ token, user: publicUser(user) });
}

type Checked = { ok: true; userId: string | null; email: string; device: string | null } | { ok: false; res: Response };

async function consumeChallenge(env: Env, id: unknown, code: unknown, purpose: CodePurpose): Promise<Checked> {
  const bad = (m = "That code is invalid or has expired.") => ({ ok: false as const, res: err(400, m) });
  if (typeof id !== "string" || typeof code !== "string" || !/^\d{6}$/.test(code)) return bad();
  const row = await env.DB.prepare("SELECT * FROM challenges WHERE id = ? AND purpose = ?")
    .bind(id, purpose).first<{ user_id: string | null; email: string; code_hash: string; attempts: number; expires_at: number; device: string | null }>();
  if (!row || row.expires_at < Date.now() || row.attempts >= MAX_ATTEMPTS) {
    if (row) await env.DB.prepare("DELETE FROM challenges WHERE id = ?").bind(id).run();
    return bad();
  }
  if (row.code_hash !== (await codeHash(id, code))) {
    await env.DB.prepare("UPDATE challenges SET attempts = attempts + 1 WHERE id = ?").bind(id).run();
    return bad(row.attempts + 1 >= MAX_ATTEMPTS ? "Too many wrong codes. Request a new one." : "That code isn't right.");
  }
  await env.DB.prepare("DELETE FROM challenges WHERE id = ?").bind(id).run(); // single use
  return { ok: true, userId: row.user_id, email: row.email, device: row.device };
}

// ---------- sign up / verify ----------

route("POST", "/v1/auth/signup", false, async ({ req, env }) => {
  const b = await readBody(req);
  const email = normEmail(b.email), name = cleanName(b.name);
  if (!email) return err(400, "Enter a valid email address.");
  if (!name) return err(400, "Enter your name.");
  if (!validPassword(b.password)) return err(400, "Password must be at least 8 characters.");

  let user = await userByEmail(env, email);
  if (user?.email_verified) return err(409, "An account with this email already exists. Try signing in.");

  const hash = await hashPassword(b.password);
  if (user) {
    // unverified leftovers: let the new signup take over the address
    await env.DB.prepare("UPDATE users SET name = ?, pass_hash = ? WHERE id = ?").bind(name, hash, user.id).run();
    user = { ...user, name, pass_hash: hash };
  } else {
    const id = newId();
    await env.DB.prepare("INSERT INTO users (id, email, name, pass_hash, created_at) VALUES (?, ?, ?, ?, ?)")
      .bind(id, email, name, hash, Date.now()).run();
    user = (await userByEmail(env, email))!;
  }
  const c = await startChallenge(env, user, email, "verify_email", deviceOf(req, b));
  if ("error" in c) return c.error;
  return json({ challenge_id: c.id, step: "verify_email", email }, 201);
});

route("POST", "/v1/auth/verify-email", false, async ({ req, env }) => {
  const b = await readBody(req);
  const r = await consumeChallenge(env, b.challenge_id, b.code, "verify_email");
  if (!r.ok) return r.res;
  const user = r.userId ? await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(r.userId).first<UserRow>() : null;
  if (!user) return err(400, "That code is invalid or has expired.");
  await env.DB.prepare("UPDATE users SET email_verified = 1 WHERE id = ?").bind(user.id).run();
  return createSession(env, { ...user, email_verified: 1 }, r.device ?? deviceOf(req, b));
});

// ---------- sign in (+ email 2FA) ----------

route("POST", "/v1/auth/login", false, async ({ req, env }) => {
  const b = await readBody(req);
  const email = normEmail(b.email);
  const generic = () => err(401, "Invalid email or password.");
  if (!email || typeof b.password !== "string") return generic();

  const user = await userByEmail(env, email);
  if (!user) { await dummyVerify(b.password); return generic(); }
  if (user.locked_until > Date.now()) return err(429, "Too many attempts. Try again in a few minutes.");

  if (!(await verifyPassword(b.password, user.pass_hash))) {
    const fails = user.failed_logins + 1;
    await env.DB.prepare("UPDATE users SET failed_logins = ?, locked_until = ? WHERE id = ?")
      .bind(fails >= MAX_FAILED_LOGINS ? 0 : fails, fails >= MAX_FAILED_LOGINS ? Date.now() + LOCK_MS : 0, user.id).run();
    return generic();
  }
  if (user.failed_logins) await env.DB.prepare("UPDATE users SET failed_logins = 0, locked_until = 0 WHERE id = ?").bind(user.id).run();

  const device = deviceOf(req, b);
  const purpose: CodePurpose | null = !user.email_verified ? "verify_email" : user.twofa_enabled ? "login_2fa" : null;
  if (!purpose) return createSession(env, user, device);

  const c = await startChallenge(env, user, email, purpose, device);
  if ("error" in c) return c.error;
  return json({ challenge_id: c.id, step: purpose === "login_2fa" ? "2fa" : "verify_email", email });
});

route("POST", "/v1/auth/2fa", false, async ({ req, env }) => {
  const b = await readBody(req);
  const r = await consumeChallenge(env, b.challenge_id, b.code, "login_2fa");
  if (!r.ok) return r.res;
  const user = r.userId ? await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(r.userId).first<UserRow>() : null;
  if (!user) return err(400, "That code is invalid or has expired.");
  return createSession(env, user, r.device ?? deviceOf(req, b));
});

route("POST", "/v1/auth/resend", false, async ({ req, env }) => {
  const b = await readBody(req);
  if (typeof b.challenge_id !== "string") return err(400, "challenge_id required");
  const row = await env.DB.prepare("SELECT * FROM challenges WHERE id = ?").bind(b.challenge_id)
    .first<{ user_id: string | null; email: string; purpose: CodePurpose; last_sent_at: number }>();
  if (!row) return err(400, "That request has expired. Start again.");
  const wait = row.last_sent_at + RESEND_COOLDOWN_MS - Date.now();
  if (wait > 0) return err(429, `Please wait ${Math.ceil(wait / 1000)}s before requesting another code.`);
  const hourly = await env.DB.prepare("SELECT COUNT(*) AS n FROM challenges WHERE email = ? AND created_at > ?")
    .bind(row.email, Date.now() - 3600_000).first<{ n: number }>();
  if ((hourly?.n ?? 0) >= MAX_CODES_PER_HOUR) return err(429, "Too many codes requested. Try again later.");

  const code = sixDigits(), now = Date.now();
  await env.DB.prepare("UPDATE challenges SET code_hash = ?, attempts = 0, last_sent_at = ?, expires_at = ? WHERE id = ?")
    .bind(await codeHash(b.challenge_id, code), now, now + CODE_TTL_MS, b.challenge_id).run();
  const user = row.user_id ? await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(row.user_id).first<UserRow>() : null;
  if (user) await sendCodeEmail(env, row.email, user.name, row.purpose, code);
  return json({ ok: true });
});

// ---------- password reset ----------

route("POST", "/v1/auth/forgot", false, async ({ req, env }) => {
  const email = normEmail((await readBody(req)).email);
  if (!email) return err(400, "Enter a valid email address.");
  const user = await userByEmail(env, email);
  // Same response whether or not the account exists; unknown emails get a decoy challenge.
  const c = await startChallenge(env, user?.email_verified ? user : null, email, "reset_password");
  if ("error" in c) return c.error;
  return json({ challenge_id: c.id, step: "reset_password", email });
});

route("POST", "/v1/auth/reset", false, async ({ req, env }) => {
  const b = await readBody(req);
  if (!validPassword(b.new_password)) return err(400, "Password must be at least 8 characters.");
  const r = await consumeChallenge(env, b.challenge_id, b.code, "reset_password");
  if (!r.ok) return r.res;
  const user = r.userId ? await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(r.userId).first<UserRow>() : null;
  if (!user) return err(400, "That code is invalid or has expired.");
  await env.DB.batch([
    env.DB.prepare("UPDATE users SET pass_hash = ?, failed_logins = 0, locked_until = 0 WHERE id = ?")
      .bind(await hashPassword(b.new_password), user.id),
    env.DB.prepare("DELETE FROM sessions WHERE user_id = ?").bind(user.id), // sign out everywhere
  ]);
  await sendNotice(env, user.email, user.name, "Your MyBoo password was changed",
    "your password was just reset and all devices were signed out. If this wasn't you, reset it again right away.");
  return json({ ok: true });
});

// ---------- signed-in account management ----------

route("POST", "/v1/auth/logout", true, async ({ env, user }) => {
  await env.DB.prepare("DELETE FROM sessions WHERE id = ?").bind(user.sessionId).run();
  return json({ ok: true });
});

route("GET", "/v1/me", true, async ({ env, user }) => {
  const u = await env.DB.prepare("SELECT id, email, name, twofa_enabled FROM users WHERE id = ?").bind(user.id)
    .first<UserRow>();
  return json({ user: publicUser(u!) });
});

route("PATCH", "/v1/me", true, async ({ req, env, user }) => {
  const b = await readBody(req);
  const u = (await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(user.id).first<UserRow>())!;
  const name = b.name === undefined ? u.name : cleanName(b.name);
  if (!name) return err(400, "Enter a name.");
  let twofa = u.twofa_enabled;
  if (b.twofa_enabled !== undefined) {
    if (typeof b.twofa_enabled !== "boolean") return err(400, "twofa_enabled must be a boolean");
    // Changing a security setting needs the password again.
    if (typeof b.password !== "string" || !(await verifyPassword(b.password, u.pass_hash))) return err(403, "Password is incorrect.");
    twofa = b.twofa_enabled ? 1 : 0;
  }
  await env.DB.prepare("UPDATE users SET name = ?, twofa_enabled = ? WHERE id = ?").bind(name, twofa, user.id).run();
  return json({ user: publicUser({ ...u, name, twofa_enabled: twofa }) });
});

route("POST", "/v1/me/password", true, async ({ req, env, user }) => {
  const b = await readBody(req);
  const u = (await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(user.id).first<UserRow>())!;
  if (typeof b.current_password !== "string" || !(await verifyPassword(b.current_password, u.pass_hash))) return err(403, "Current password is incorrect.");
  if (!validPassword(b.new_password)) return err(400, "New password must be at least 8 characters.");
  await env.DB.batch([
    env.DB.prepare("UPDATE users SET pass_hash = ? WHERE id = ?").bind(await hashPassword(b.new_password), user.id),
    env.DB.prepare("DELETE FROM sessions WHERE user_id = ? AND id != ?").bind(user.id, user.sessionId), // keep this device
  ]);
  await sendNotice(env, u.email, u.name, "Your MyBoo password was changed",
    "your password was just changed and other devices were signed out. If this wasn't you, reset your password right away.");
  return json({ ok: true });
});

route("GET", "/v1/me/sessions", true, async ({ env, user }) => {
  const rows = await env.DB.prepare(
    "SELECT id, device, created_at, last_used_at FROM sessions WHERE user_id = ? ORDER BY last_used_at DESC",
  ).bind(user.id).all<{ id: string; device: string; created_at: number; last_used_at: number }>();
  return json({ sessions: rows.results.map((s) => ({ ...s, current: s.id === user.sessionId })) });
});

route("DELETE", "/v1/me/sessions/:id", true, async ({ env, user, params }) => {
  await env.DB.prepare("DELETE FROM sessions WHERE id = ? AND user_id = ?").bind(params[0], user.id).run();
  return json({ ok: true });
});

route("POST", "/v1/me/logout-all", true, async ({ env, user }) => {
  await env.DB.prepare("DELETE FROM sessions WHERE user_id = ?").bind(user.id).run();
  return json({ ok: true });
});

route("DELETE", "/v1/me", true, async ({ req, env, user }) => {
  const b = await readBody(req);
  const u = (await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(user.id).first<UserRow>())!;
  if (typeof b.password !== "string" || !(await verifyPassword(b.password, u.pass_hash))) return err(403, "Password is incorrect.");
  await env.DB.batch([
    env.DB.prepare("DELETE FROM users WHERE id = ?").bind(user.id), // cascades sessions, members, state, challenges
    env.DB.prepare("DELETE FROM circles WHERE id NOT IN (SELECT circle_id FROM members)"), // drop orphaned circles
  ]);
  return json({ ok: true });
});
