import { err, sha256 } from "./util";

export interface Env {
  DB: D1Database;
  EMAIL?: SendEmail;
  FCM_SERVICE_ACCOUNT?: string;
  /** Sender address; must be on a domain onboarded to Cloudflare Email Sending. */
  FROM_EMAIL?: string;
  /** If "1", log one-time codes to the console (local dev only; never set in production). */
  DEV_LOG_CODES?: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  sessionId: string;
}

export interface Ctx {
  req: Request;
  env: Env;
  ctx: ExecutionContext;
  user: User;
  params: string[];
}

type Handler = (c: Ctx) => Promise<Response>;
const routes: { method: string; re: RegExp; auth: boolean; fn: Handler }[] = [];

export const route = (method: string, path: string, auth: boolean, fn: Handler) =>
  routes.push({ method, re: new RegExp(`^${path.replace(/:[a-z]+/g, "([^/]+)")}$`), auth, fn });

const SESSION_TTL_MS = 90 * 24 * 3600 * 1000;
export { SESSION_TTL_MS };

async function authenticate(req: Request, env: Env): Promise<User | null> {
  const m = /^Bearer (.+)$/.exec(req.headers.get("authorization") ?? "");
  if (!m) return null;
  const now = Date.now();
  const row = await env.DB.prepare(
    `SELECT u.id, u.name, u.email, s.id AS session_id, s.last_used_at
     FROM sessions s JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = ? AND s.expires_at > ? AND u.email_verified = 1`,
  )
    .bind(await sha256(m[1]), now)
    .first<{ id: string; name: string; email: string; session_id: string; last_used_at: number }>();
  if (!row) return null;
  if (now - row.last_used_at > 3600_000) {
    // sliding expiry, written at most hourly
    await env.DB.prepare("UPDATE sessions SET last_used_at = ?, expires_at = ? WHERE id = ?")
      .bind(now, now + SESSION_TTL_MS, row.session_id).run();
  }
  return { id: row.id, name: row.name, email: row.email, sessionId: row.session_id };
}

export async function dispatch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response | null> {
  const path = new URL(req.url).pathname;
  for (const r of routes) {
    if (r.method !== req.method) continue;
    const m = r.re.exec(path);
    if (!m) continue;
    let user = null as User | null;
    if (r.auth) {
      user = await authenticate(req, env);
      if (!user) return err(401, "unauthorized");
    }
    try {
      return await r.fn({ req, env, ctx, user: user as User, params: m.slice(1) });
    } catch (e) {
      console.error(e);
      return err(500, "internal error");
    }
  }
  return null;
}
