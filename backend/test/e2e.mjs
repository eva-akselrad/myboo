// End-to-end account flow against `wrangler dev` (codes are read from its log: DEV_LOG_CODES=1).
// usage: node test/e2e.mjs <baseUrl> <logFile>
import fs from "node:fs";
import assert from "node:assert/strict";
const [base, logFile] = process.argv.slice(2);
const call = async (method, path, body, token) => {
  const r = await fetch(base + path, { method, headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { status: r.status, body: await r.json().catch(() => ({})) };
};
const codeFor = async (purpose, email) => {
  const needle = `[dev code] ${purpose} ${email} -> `;
  for (let i = 0; i < 30; i++) {
    const log = fs.readFileSync(logFile, "utf8");
    const at = log.lastIndexOf(needle);
    if (at >= 0 && /^\d{6}/.test(log.slice(at + needle.length, at + needle.length + 6))) return log.slice(at + needle.length, at + needle.length + 6);
    await new Promise((r) => setTimeout(r, 200));
  }
};
const email = `t${Date.now()}@example.com`, pw = "correct horse 1", pw2 = "battery staple 2";
let r;

r = await call("POST", "/v1/auth/signup", { email, name: "Test", password: "short" }); assert.equal(r.status, 400);
r = await call("POST", "/v1/auth/signup", { email, name: "Tess", password: pw }); assert.equal(r.status, 201); const ch = r.body.challenge_id;
const code = await codeFor("verify_email", email); assert.ok(code, "got code");
r = await call("POST", "/v1/auth/login", { email, password: pw }); assert.equal(r.body.step, "verify_email", "unverified login must require email code");
r = await call("POST", "/v1/auth/verify-email", { challenge_id: ch, code: code === "000000" ? "111111" : "000000" }); assert.equal(r.status, 400);
r = await call("POST", "/v1/auth/verify-email", { challenge_id: ch, code }); assert.equal(r.status, 200, JSON.stringify(r.body)); let tok = r.body.token;
r = await call("POST", "/v1/auth/verify-email", { challenge_id: ch, code }); assert.equal(r.status, 400, "code is single use");
r = await call("GET", "/v1/me", null, tok); assert.equal(r.body.user.email, email);
r = await call("POST", "/v1/circles", { name: "Fam" }, tok); assert.equal(r.status, 201);
r = await call("POST", "/v1/auth/signup", { email, name: "X", password: pw }); assert.equal(r.status, 409);

// 2FA toggle needs password
r = await call("PATCH", "/v1/me", { twofa_enabled: true }, tok); assert.equal(r.status, 403);
r = await call("PATCH", "/v1/me", { twofa_enabled: true, password: pw }, tok); assert.equal(r.body.user.twofa_enabled, true);
r = await call("POST", "/v1/auth/login", { email, password: "wrongwrong" }); assert.equal(r.status, 401);
r = await call("POST", "/v1/auth/login", { email, password: pw }); assert.equal(r.body.step, "2fa"); const ch2 = r.body.challenge_id;
r = await call("POST", "/v1/auth/resend", { challenge_id: ch2 }); assert.equal(r.status, 429, "resend cooldown");
r = await call("POST", "/v1/auth/2fa", { challenge_id: ch2, code: "123456" }); assert.equal(r.status, 400);
const c2 = await codeFor("login_2fa", email);
r = await call("POST", "/v1/auth/2fa", { challenge_id: ch2, code: c2 }); assert.equal(r.status, 200, JSON.stringify(r.body)); const tok2 = r.body.token;

r = await call("GET", "/v1/me/sessions", null, tok2); assert.equal(r.body.sessions.length, 2);
const other = r.body.sessions.find((s) => !s.current);
r = await call("DELETE", `/v1/me/sessions/${other.id}`, null, tok2);
r = await call("GET", "/v1/me", null, tok); assert.equal(r.status, 401, "revoked session rejected");

// forgot / reset
r = await call("POST", "/v1/auth/forgot", { email: "nobody@example.com" }); assert.equal(r.status, 200, "no account enumeration");
r = await call("POST", "/v1/auth/forgot", { email }); const ch3 = r.body.challenge_id;
const c3 = await codeFor("reset_password", email);
r = await call("POST", "/v1/auth/reset", { challenge_id: ch3, code: c3, new_password: pw2 }); assert.equal(r.status, 200, JSON.stringify(r.body));
r = await call("GET", "/v1/me", null, tok2); assert.equal(r.status, 401, "reset signs out everywhere");
r = await call("POST", "/v1/auth/login", { email, password: pw }); assert.equal(r.status, 401);
r = await call("POST", "/v1/auth/login", { email, password: pw2 }); assert.equal(r.body.step, "2fa");
const c4 = await codeFor("login_2fa", email);
r = await call("POST", "/v1/auth/2fa", { challenge_id: r.body.challenge_id, code: c4 }); const tok3 = r.body.token; assert.ok(tok3);

// change password keeps current device
r = await call("POST", "/v1/me/password", { current_password: "nope", new_password: pw }, tok3); assert.equal(r.status, 403);
r = await call("POST", "/v1/me/password", { current_password: pw2, new_password: pw }, tok3); assert.equal(r.status, 200);
r = await call("GET", "/v1/me", null, tok3); assert.equal(r.status, 200);

// lockout after 5 bad passwords
for (let i = 0; i < 5; i++) await call("POST", "/v1/auth/login", { email, password: "badbadbad" + i });
r = await call("POST", "/v1/auth/login", { email, password: pw }); assert.equal(r.status, 429, "locked out");

// delete account
r = await call("DELETE", "/v1/me", { password: "wrong" }, tok3); assert.equal(r.status, 403);
r = await call("DELETE", "/v1/me", { password: pw }, tok3); assert.equal(r.status, 200);
r = await call("GET", "/v1/me", null, tok3); assert.equal(r.status, 401);
console.log("ALL AUTH CHECKS PASSED");
