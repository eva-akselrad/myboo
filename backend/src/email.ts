import type { Env } from "./router";
import { escapeHtml } from "./util";

export type CodePurpose = "verify_email" | "login_2fa" | "reset_password";

const COPY: Record<CodePurpose, { subject: string; heading: string; blurb: string }> = {
  verify_email: {
    subject: "Your MyBoo verification code",
    heading: "Welcome to MyBoo!",
    blurb: "Enter this code in the app to verify your email and finish creating your account.",
  },
  login_2fa: {
    subject: "Your MyBoo sign-in code",
    heading: "Sign-in code",
    blurb: "Enter this code in the app to finish signing in. If this wasn't you, change your password.",
  },
  reset_password: {
    subject: "Reset your MyBoo password",
    heading: "Password reset",
    blurb: "Enter this code in the app to choose a new password. If you didn't ask for this, you can ignore it.",
  },
};

/** Pastel notebook styled HTML email (inline CSS only; email clients strip the rest). */
function shell(heading: string, body: string): string {
  return `<!doctype html><html><body style="margin:0;background:#fdf6ec;padding:24px;font-family:'Segoe UI',Helvetica,Arial,sans-serif;color:#3b3a4a">
<div style="max-width:440px;margin:0 auto;background:#fffdf7;border:2px solid #3b3a4a;border-radius:18px;padding:28px 28px 24px;box-shadow:5px 5px 0 #f7c6d4">
<div style="font-size:30px;line-height:1">&#128123;</div>
<h1 style="margin:10px 0 6px;font-size:24px;color:#3b3a4a">${escapeHtml(heading)}</h1>
${body}
<hr style="border:none;border-top:2px dashed #cfe3f7;margin:24px 0 14px">
<div style="font-size:12px;color:#8a8799">MyBoo &middot; keeping your people close</div>
</div></body></html>`;
}

async function send(env: Env, to: string, subject: string, html: string, text: string) {
  const from = env.FROM_EMAIL ?? "myboo@evaakselrad.com";
  if (!env.EMAIL) {
    console.warn(`[email not configured] to=${to} subject=${subject}`);
    return;
  }
  try {
    await env.EMAIL.send({ to, from: { email: from, name: "MyBoo" }, subject, html, text });
  } catch (e) {
    console.error("email send failed", e);
    if (env.DEV_LOG_CODES !== "1") throw e; // in production surface the failure; in local dev the code is logged
  }
}

export async function sendCodeEmail(env: Env, to: string, name: string, purpose: CodePurpose, code: string) {
  if (env.DEV_LOG_CODES === "1") console.log(`[dev code] ${purpose} ${to} -> ${code}`);
  const c = COPY[purpose];
  const html = shell(
    c.heading,
    `<p style="margin:0 0 16px;font-size:15px;line-height:1.5">Hi ${escapeHtml(name)}, ${escapeHtml(c.blurb)}</p>
<div style="background:#fff3b8;border:2px solid #3b3a4a;border-radius:14px;padding:16px;text-align:center;font-size:36px;letter-spacing:10px;font-weight:700;font-family:'Courier New',monospace">${code}</div>
<p style="margin:16px 0 0;font-size:13px;color:#8a8799">This code expires in 10 minutes.</p>`,
  );
  const text = `Hi ${name}, ${c.blurb}\n\nYour code: ${code}\n\nThis code expires in 10 minutes.`;
  await send(env, to, c.subject, html, text);
}

export async function sendNotice(env: Env, to: string, name: string, subject: string, message: string) {
  const html = shell(subject, `<p style="margin:0;font-size:15px;line-height:1.5">Hi ${escapeHtml(name)}, ${escapeHtml(message)}</p>`);
  await send(env, to, subject, html, `Hi ${name}, ${message}`);
}
