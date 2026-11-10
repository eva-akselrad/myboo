// Alert queue flow against `wrangler dev`: B arrives at a place, A should receive the alert by polling.
// usage: node test/alerts.mjs <baseUrl> <logFile>
import fs from "node:fs";
import assert from "node:assert/strict";
const [base, logFile] = process.argv.slice(2);
const call = async (method, path, body, token) => {
  const r = await fetch(base + path, { method, headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { status: r.status, body: await r.json().catch(() => ({})) };
};
const code = async (email) => {
  const needle = `[dev code] verify_email ${email} -> `;
  for (let i = 0; i < 30; i++) {
    const log = fs.readFileSync(logFile, "utf8"), at = log.lastIndexOf(needle);
    if (at >= 0 && /^\d{6}/.test(log.slice(at + needle.length))) return log.slice(at + needle.length, at + needle.length + 6);
    await new Promise((r) => setTimeout(r, 200));
  }
};
const user = async (name) => {
  const email = `${name.toLowerCase()}${Date.now()}@example.com`;
  const s = await call("POST", "/v1/auth/signup", { email, name, password: "correct horse 1" });
  const v = await call("POST", "/v1/auth/verify-email", { challenge_id: s.body.challenge_id, code: await code(email) });
  assert.equal(v.status, 200, JSON.stringify(v.body)); return v.body.token;
};
const a = await user("Ann"), b = await user("Bob");
let r = await call("POST", "/v1/circles", { name: "Fam" }, a); const cid = r.body.id;
await call("POST", "/v1/circles/join", { code: r.body.invite_code }, b);
await call("POST", `/v1/circles/${cid}/places`, { name: "Home", lat: 40, lng: -100, radius_m: 150 }, a);

assert.equal((await call("GET", "/v1/alerts", null, a)).body.alerts.length, 0);
await call("POST", "/v1/location", { lat: 40.05, lng: -100, accuracy: 10 }, b);   // baseline, outside
await call("POST", "/v1/location", { lat: 40.0, lng: -100, accuracy: 10 }, b);    // arrives
await new Promise((r) => setTimeout(r, 800));
r = await call("GET", "/v1/alerts", null, a);
assert.equal(r.body.alerts.length, 1, JSON.stringify(r.body)); assert.match(r.body.alerts[0].body, /Bob arrived at Home/);
const id = r.body.alerts[0].id;
assert.equal((await call("GET", "/v1/alerts", null, b)).body.alerts.length, 0, "B doesn't get their own alert");
r = await call("GET", `/v1/alerts?after=${id - 1}`, null, a); assert.equal(r.body.alerts.length, 1, "unacked alert is re-sent");
r = await call("GET", `/v1/alerts?after=${id}`, null, a); assert.equal(r.body.alerts.length, 0, "acked alert is gone");
assert.equal((await call("GET", "/v1/alerts")).status, 401);
console.log("ALERT QUEUE CHECKS PASSED");
