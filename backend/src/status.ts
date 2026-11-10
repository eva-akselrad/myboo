import { dispatch, type Env } from "./router";
import { page } from "./theme";
import { escapeHtml } from "./util";

type Level = "operational" | "degraded" | "outage" | "unknown";

interface Component {
  id: "api" | "database" | "email" | "push" | "tiles";
  name: string;
  desc: string;
  kind: "probe" | "events";
}

const COMPONENTS: Component[] = [
  { id: "api", name: "API", desc: "Sign-in, circles and location updates", kind: "probe" },
  { id: "database", name: "Database", desc: "Accounts, circles and places", kind: "probe" },
  { id: "email", name: "Email delivery", desc: "Verification, sign-in and reset codes", kind: "events" },
  { id: "push", name: "Push notifications", desc: "Arrival, departure and driving alerts", kind: "events" },
  { id: "tiles", name: "Map tiles", desc: "OpenStreetMap basemap", kind: "probe" },
];

const DAY = 86_400_000;
const STALE_MS = 15 * 60_000;
const DAYS = 30;

// ---------- recording ----------

/** Record one probe result or runtime event. Never throws: status bookkeeping must not break the app. */
export async function record(env: Env, component: Component["id"], ok: boolean, ms = 0) {
  try {
    await env.DB.prepare("INSERT INTO checks (ts, component, ok, ms) VALUES (?, ?, ?, ?)")
      .bind(Date.now(), component, ok ? 1 : 0, Math.round(ms)).run();
  } catch (e) {
    console.error("status record failed", e);
  }
}

/** Cron: probe the things we can actively test, and trim old rows. */
export async function runProbes(env: Env) {
  const timed = async (fn: () => Promise<boolean>): Promise<[boolean, number]> => {
    const t = Date.now();
    try { return [await fn(), Date.now() - t]; } catch { return [false, Date.now() - t]; }
  };

  const [dbOk, dbMs] = await timed(async () => !!(await env.DB.prepare("SELECT 1 AS x").first()));
  const [apiOk, apiMs] = await timed(async () => {
    const res = await dispatch(new Request("https://internal/health"), env, { waitUntil() {}, passThroughOnException() {} } as unknown as ExecutionContext);
    return res?.status === 200;
  });
  const [tilesOk, tilesMs] = await timed(async () => {
    const r = await fetch("https://tile.openstreetmap.org/0/0/0.png", {
      method: "HEAD",
      headers: { "user-agent": "MyBoo-status/1.0 (+https://myboo.evaakselrad.com/status)" },
      signal: AbortSignal.timeout(6000),
    });
    return r.ok;
  });

  await record(env, "database", dbOk, dbMs);
  await record(env, "api", apiOk && dbOk, apiMs);
  await record(env, "tiles", tilesOk, tilesMs);
  await env.DB.prepare("DELETE FROM checks WHERE ts < ?").bind(Date.now() - (DAYS + 5) * DAY).run().catch(() => {});
}

// ---------- reading ----------

interface View {
  c: Component;
  level: Level;
  note: string;
  ms: number | null;
  uptime: number | null;
  days: ({ date: string; pct: number | null })[];
}

const utcDay = (t: number) => new Date(t).toISOString().slice(0, 10);

async function load(env: Env): Promise<{ views: View[]; overall: Level; updated: number }> {
  const now = Date.now();
  const latest = await env.DB.prepare(
    `SELECT c.component, c.ok, c.ms, c.ts FROM checks c
     JOIN (SELECT component, MAX(ts) m FROM checks GROUP BY component) x ON x.component = c.component AND x.m = c.ts`,
  ).all<{ component: string; ok: number; ms: number; ts: number }>();
  const byComp = new Map(latest.results.map((r) => [r.component, r]));

  const daily = await env.DB.prepare(
    `SELECT component, date(ts / 1000, 'unixepoch') AS d, COUNT(*) AS n, SUM(ok) AS good
     FROM checks WHERE ts > ? GROUP BY component, d`,
  ).bind(now - DAYS * DAY).all<{ component: string; d: string; n: number; good: number }>();

  const recentEvents = await env.DB.prepare("SELECT component, ok FROM checks WHERE component IN ('email','push') AND ts > ? ORDER BY ts DESC")
    .bind(now - DAY).all<{ component: string; ok: number }>();

  const dayList = Array.from({ length: DAYS }, (_, i) => utcDay(now - (DAYS - 1 - i) * DAY));

  const views: View[] = COMPONENTS.map((c) => {
    const rows = daily.results.filter((r) => r.component === c.id);
    const days = dayList.map((date) => {
      const r = rows.find((x) => x.d === date);
      return { date, pct: r ? (r.good / r.n) * 100 : null };
    });
    const tn = rows.reduce((a, r) => a + r.n, 0), tg = rows.reduce((a, r) => a + r.good, 0);
    const uptime = tn ? (tg / tn) * 100 : null;
    const last = byComp.get(c.id);

    let level: Level = "unknown", note = "Waiting for the first check";
    if (c.kind === "probe") {
      if (last && now - last.ts <= STALE_MS) {
        level = last.ok ? "operational" : "outage";
        note = last.ok ? "Responding normally" : "Not responding";
      } else if (last) {
        note = "No recent check";
      }
    } else {
      const ev = recentEvents.results.filter((e) => e.component === c.id);
      if (!ev.length) { level = "operational"; note = "No recent activity, no problems reported"; }
      else if (ev[0].ok) { level = "operational"; note = "Delivering normally"; }
      else {
        const run = ev.findIndex((e) => e.ok);
        const consecutive = run === -1 ? ev.length : run;
        level = consecutive >= 3 ? "outage" : "degraded";
        note = consecutive >= 3 ? "Recent attempts are failing" : "A recent attempt failed";
      }
    }
    return { c, level, note, ms: c.kind === "probe" && last ? last.ms : null, uptime, days };
  });

  const levels = views.map((v) => v.level);
  const overall: Level = levels.includes("outage") ? "outage" : levels.includes("degraded") ? "degraded" : levels.every((l) => l === "unknown") ? "unknown" : "operational";
  const updated = Math.max(0, ...latest.results.map((r) => r.ts));
  return { views, overall, updated };
}

async function ensureData(env: Env) {
  const any = await env.DB.prepare("SELECT 1 AS x FROM checks WHERE component = 'database' LIMIT 1").first();
  if (!any) await runProbes(env); // first ever view: don't show an empty page while waiting for the cron
}

export async function statusJson(env: Env): Promise<Response> {
  await ensureData(env);
  const { views, overall, updated } = await load(env);
  return new Response(
    JSON.stringify({
      status: overall,
      updated_at: updated ? new Date(updated).toISOString() : null,
      components: views.map((v) => ({
        id: v.c.id, name: v.c.name, status: v.level,
        latency_ms: v.ms, uptime_30d: v.uptime == null ? null : Math.round(v.uptime * 100) / 100,
      })),
    }),
    { headers: { "content-type": "application/json", "cache-control": "public, max-age=30", "access-control-allow-origin": "*" } },
  );
}

// ---------- page ----------

const ago = (t: number) => {
  const s = Math.max(0, Math.round((Date.now() - t) / 1000));
  return s < 60 ? "just now" : s < 3600 ? `${Math.floor(s / 60)} min ago` : s < 86400 ? `${Math.floor(s / 3600)} h ago` : `${Math.floor(s / 86400)} d ago`;
};

const LABEL: Record<Level, string> = { operational: "Operational", degraded: "Degraded", outage: "Outage", unknown: "No data yet" };
const DOT: Record<Level, string> = { operational: "ok", degraded: "warn", outage: "bad", unknown: "" };
const BANNER: Record<Level, [string, string, string]> = {
  operational: ["All systems operational", "var(--mint)", "Everything is running smoothly."],
  degraded: ["Some systems are slow", "var(--butter)", "We're seeing a few hiccups and are keeping an eye on it."],
  outage: ["Something's down", "var(--pink)", "Part of MyBoo isn't working right now. We're on it."],
  unknown: ["Checking in…", "var(--sky)", "Collecting the first health checks."],
};

const CSS = `
.hero{padding:34px 0 6px}
h1{font-size:clamp(46px,8vw,72px);margin-bottom:6px}
.sub{color:var(--muted);margin-bottom:26px}
.banner{position:relative;border:3px solid var(--ink);border-radius:26px;padding:26px 28px;box-shadow:7px 7px 0 var(--shadow);transform:rotate(-.5deg);margin:24px 0 38px}
.banner h2{font-size:clamp(34px,6vw,50px);margin:0 0 4px}
.banner p{margin:0}
.comp{position:relative;margin:0 0 22px;padding:18px 22px 16px}
.comp:nth-child(odd){transform:rotate(.25deg)}.comp:nth-child(even){transform:rotate(-.25deg)}
.row{display:flex;align-items:flex-start;gap:14px;flex-wrap:wrap}
.row .name{flex:1 1 220px}
.row .name b{font:700 29px Caveat,cursive}
.row .name span{display:block;color:var(--muted);font-size:17px;line-height:1.3}
.state{display:flex;align-items:center;gap:6px;font-size:19px}
.meta{color:var(--muted);font-size:16px;text-align:right}
@media(max-width:600px){.meta{text-align:left}}
.bars{display:flex;gap:3px;margin-top:12px;height:30px}
.bars i{flex:1;border-radius:4px;border:1.5px solid var(--ink);background:var(--line);min-width:3px}
.bars i.g{background:#7be0b0}.bars i.y{background:#ffd966}.bars i.r{background:#ff8fa9}
.scale{display:flex;justify-content:space-between;color:var(--muted);font-size:15px;margin-top:4px}
.legend{display:flex;gap:18px;flex-wrap:wrap;color:var(--muted);font-size:16px;margin:8px 0 0}
.legend i{display:inline-block;width:13px;height:13px;border:1.5px solid var(--ink);border-radius:3px;vertical-align:-2px;margin-right:6px}
`;

export async function statusPage(env: Env): Promise<string> {
  await ensureData(env);
  const { views, overall, updated } = await load(env);
  const [headline, color, blurb] = BANNER[overall];

  const rows = views.map((v) => {
    const bars = v.days.map((d) => {
      const cls = d.pct == null ? "" : d.pct >= 99.5 ? "g" : d.pct >= 95 ? "y" : "r";
      const tip = d.pct == null ? `${d.date}: no data` : `${d.date}: ${d.pct.toFixed(d.pct === 100 ? 0 : 1)}% up`;
      return `<i class="${cls}" title="${tip}"></i>`;
    }).join("");
    const meta = [
      v.ms != null ? `${v.ms} ms` : null,
      v.uptime != null ? `${v.uptime.toFixed(v.uptime >= 99.995 ? 0 : 2)}% over ${DAYS} days` : null,
    ].filter(Boolean).join(" · ");
    return `<section class="card comp">
<div class="row"><div class="name"><b>${escapeHtml(v.c.name)}</b><span>${escapeHtml(v.c.desc)}</span></div>
<div><div class="state"><span class="dot ${DOT[v.level]}"></span>${LABEL[v.level]}</div><div class="meta">${escapeHtml(v.note)}${meta ? `<br>${meta}` : ""}</div></div></div>
<div class="bars" role="img" aria-label="${DAYS}-day history for ${escapeHtml(v.c.name)}">${bars}</div>
<div class="scale"><span>${DAYS} days ago</span><span>today</span></div></section>`;
  }).join("\n");

  const body = `<main>
<div class="hero"><h1>MyBoo <span class="hl">status</span></h1>
<p class="sub">Live health of everything that keeps your circle connected.${updated ? ` Last checked ${ago(updated)}.` : ""}</p></div>
<div class="banner" style="background:${color}"><span class="tape"></span><h2>${headline}</h2><p>${blurb}</p></div>
${rows}
<p class="legend"><span><i style="background:#7be0b0"></i>100% up</span><span><i style="background:#ffd966"></i>some downtime</span><span><i style="background:#ff8fa9"></i>major downtime</span><span><i style="background:var(--line)"></i>no data</span></p>
</main>
<footer><a href="/">← Back to MyBoo</a><a href="/status.json">status.json</a><span>Updates every 5 minutes. This page refreshes itself.</span></footer>`;

  return page({
    title: "MyBoo status",
    description: "Live system status for MyBoo: API, database, email, push notifications and map tiles.",
    css: CSS,
    body,
    refresh: 60,
  });
}
