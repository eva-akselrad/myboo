import { computeInside, nextDriveState, type Place } from "./geo";
import { route, type Env, type User } from "./router";
import { record } from "./status";
import { cleanName, err, inviteCode, isNum, json, newId, readBody } from "./util";

async function isMember(env: Env, circleId: string, userId: string) {
  return !!(await env.DB.prepare("SELECT 1 FROM members WHERE circle_id = ? AND user_id = ?")
    .bind(circleId, userId)
    .first());
}

// ---------- push helpers ----------

/** Queue an alert for each user; their phone picks it up on its next poll (see GET /v1/alerts). */
async function notifyUsers(env: Env, userIds: string[], title: string, body: string) {
  if (!userIds.length) return;
  try {
    const ts = Date.now();
    await env.DB.batch(
      userIds.map((id) => env.DB.prepare("INSERT INTO alerts (user_id, title, body, ts) VALUES (?, ?, ?, ?)").bind(id, title, body, ts)),
    );
    await record(env, "push", true);
  } catch (e) {
    console.error("queue alert failed", e);
    await record(env, "push", false);
  }
}

async function circleMatesOf(env: Env, userId: string, circleId?: string): Promise<string[]> {
  const rows = circleId
    ? await env.DB.prepare("SELECT user_id FROM members WHERE circle_id = ? AND user_id != ?").bind(circleId, userId).all<{ user_id: string }>()
    : await env.DB.prepare(
        `SELECT DISTINCT m2.user_id FROM members m1 JOIN members m2 ON m1.circle_id = m2.circle_id
         WHERE m1.user_id = ? AND m2.user_id != ?`,
      ).bind(userId, userId).all<{ user_id: string }>();
  return rows.results.map((r) => r.user_id);
}

// ---------- core: location ingest + event detection ----------

async function ingestLocation(env: Env, user: User, b: Record<string, unknown>, ctx: ExecutionContext) {
  const lat = b.lat, lng = b.lng;
  if (!isNum(lat) || !isNum(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return err(400, "invalid lat/lng");
  const speed = isNum(b.speed) ? b.speed : null; // m/s
  const accuracy = isNum(b.accuracy) ? b.accuracy : null;
  const ts = isNum(b.ts) ? b.ts : Date.now();

  const prev = await env.DB.prepare("SELECT inside, driving, fast_count FROM user_state WHERE user_id = ?")
    .bind(user.id)
    .first<{ inside: string; driving: number; fast_count: number }>();

  const places = (
    await env.DB.prepare(
      `SELECT p.id, p.circle_id, p.name, p.lat, p.lng, p.radius_m FROM places p
       JOIN members m ON m.circle_id = p.circle_id WHERE m.user_id = ?`,
    ).bind(user.id).all<Place & { circle_id: string }>()
  ).results;

  // Ignore very coarse fixes for place transitions (they cause false arrivals/departures).
  const accurate = accuracy == null || accuracy <= 100;
  const prevInside: string[] = prev ? JSON.parse(prev.inside) : [];
  const inside = accurate ? computeInside(lat, lng, places, prevInside) : prevInside;
  const entered = inside.filter((id) => !prevInside.includes(id));
  const left = prevInside.filter((id) => !inside.includes(id));

  const drive = nextDriveState({ driving: !!prev?.driving, fastCount: prev?.fast_count ?? 0 }, speed);

  await env.DB.prepare(
    `INSERT INTO user_state (user_id, lat, lng, speed, accuracy, ts, inside, driving, fast_count)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(user_id) DO UPDATE SET lat=excluded.lat, lng=excluded.lng, speed=excluded.speed,
       accuracy=excluded.accuracy, ts=excluded.ts, inside=excluded.inside,
       driving=excluded.driving, fast_count=excluded.fast_count`,
  ).bind(user.id, lat, lng, speed, accuracy, ts, JSON.stringify(inside), drive.driving ? 1 : 0, drive.fastCount).run();

  // First-ever fix: record the baseline but don't announce "arrived" for places already inside.
  const events: Promise<void>[] = [];
  if (prev) {
    const placeById = new Map(places.map((p) => [p.id, p]));
    for (const [ids, verb] of [[entered, "arrived at"], [left, "left"]] as const) {
      for (const id of ids) {
        const p = placeById.get(id);
        if (!p) continue;
        events.push(
          circleMatesOf(env, user.id, p.circle_id).then((to) => notifyUsers(env, to, "MyBoo", `${user.name} ${verb} ${p.name}`)),
        );
      }
    }
    if (drive.started) {
      events.push(circleMatesOf(env, user.id).then((to) => notifyUsers(env, to, "MyBoo", `${user.name} started driving`)));
    }
  }
  ctx.waitUntil(Promise.all(events));
  return json({ ok: true, inside, driving: drive.driving });
}

// ---------- routes ----------

// Legacy (v1.0.0 clients registered an FCM token). Alerts no longer use Firebase.
route("PUT", "/v1/me/fcm-token", true, async () => json({ ok: true }));

// Phones poll this: `after` is the last alert id they've shown, which also acknowledges (deletes) it.
route("GET", "/v1/alerts", true, async ({ req, env, user }) => {
  const after = Math.max(0, Number(new URL(req.url).searchParams.get("after")) || 0);
  const [, rows] = await env.DB.batch([
    env.DB.prepare("DELETE FROM alerts WHERE user_id = ? AND (id <= ? OR ts < ?)").bind(user.id, after, Date.now() - 6 * 3_600_000),
    env.DB.prepare("SELECT id, title, body, ts FROM alerts WHERE user_id = ? AND id > ? ORDER BY id LIMIT 20").bind(user.id, after),
  ]);
  return json({ alerts: rows.results });
});

route("GET", "/v1/me/circles", true, async ({ env, user }) => {
  const rows = await env.DB.prepare(
    `SELECT c.id, c.name, c.invite_code FROM circles c JOIN members m ON m.circle_id = c.id WHERE m.user_id = ?`,
  ).bind(user.id).all();
  return json({ circles: rows.results });
});

route("POST", "/v1/circles", true, async ({ req, env, user }) => {
  const name = cleanName((await readBody(req)).name);
  if (!name) return err(400, "name required");
  const id = newId(), code = inviteCode();
  await env.DB.batch([
    env.DB.prepare("INSERT INTO circles (id, name, invite_code, created_at) VALUES (?, ?, ?, ?)").bind(id, name, code, Date.now()),
    env.DB.prepare("INSERT INTO members (circle_id, user_id) VALUES (?, ?)").bind(id, user.id),
  ]);
  return json({ id, name, invite_code: code }, 201);
});

route("POST", "/v1/circles/join", true, async ({ req, env, user }) => {
  const code = String((await readBody(req)).code ?? "").trim().toUpperCase();
  const circle = await env.DB.prepare("SELECT id, name, invite_code FROM circles WHERE invite_code = ?").bind(code).first<{ id: string; name: string; invite_code: string }>();
  if (!circle) return err(404, "invalid invite code");
  await env.DB.prepare("INSERT OR IGNORE INTO members (circle_id, user_id) VALUES (?, ?)").bind(circle.id, user.id).run();
  return json(circle);
});

route("GET", "/v1/circles/:id", true, async ({ env, user, params }) => {
  const [circleId] = params;
  if (!(await isMember(env, circleId, user.id))) return err(404, "not found");
  const circle = await env.DB.prepare("SELECT id, name, invite_code FROM circles WHERE id = ?").bind(circleId).first();
  const members = await env.DB.prepare(
    `SELECT u.id, u.name, s.lat, s.lng, s.speed, s.accuracy, s.ts, COALESCE(s.driving, 0) AS driving, COALESCE(s.inside, '[]') AS inside
     FROM members m JOIN users u ON u.id = m.user_id LEFT JOIN user_state s ON s.user_id = u.id
     WHERE m.circle_id = ?`,
  ).bind(circleId).all<{ inside: string; driving: number } & Record<string, unknown>>();
  const places = await env.DB.prepare("SELECT id, name, lat, lng, radius_m FROM places WHERE circle_id = ?").bind(circleId).all();
  return json({
    circle,
    members: members.results.map((m) => ({ ...m, driving: !!m.driving, inside: JSON.parse(m.inside) })),
    places: places.results,
  });
});

route("POST", "/v1/circles/:id/places", true, async ({ req, env, user, params }) => {
  const [circleId] = params;
  if (!(await isMember(env, circleId, user.id))) return err(404, "not found");
  const b = await readBody(req);
  const name = cleanName(b.name);
  if (!name || !isNum(b.lat) || !isNum(b.lng) || Math.abs(b.lat) > 90 || Math.abs(b.lng) > 180) return err(400, "name, lat, lng required");
  const radius = isNum(b.radius_m) ? Math.min(Math.max(b.radius_m, 50), 5000) : 150;
  const id = newId();
  await env.DB.prepare("INSERT INTO places (id, circle_id, name, lat, lng, radius_m) VALUES (?, ?, ?, ?, ?, ?)")
    .bind(id, circleId, name, b.lat, b.lng, radius).run();
  return json({ id, name, lat: b.lat, lng: b.lng, radius_m: radius }, 201);
});

route("DELETE", "/v1/circles/:id/places/:pid", true, async ({ env, user, params }) => {
  const [circleId, placeId] = params;
  if (!(await isMember(env, circleId, user.id))) return err(404, "not found");
  await env.DB.prepare("DELETE FROM places WHERE id = ? AND circle_id = ?").bind(placeId, circleId).run();
  return json({ ok: true });
});

route("POST", "/v1/location", true, async ({ req, env, ctx, user }) => ingestLocation(env, user, await readBody(req), ctx));
