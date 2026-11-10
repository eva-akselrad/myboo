// Minimal FCM HTTP v1 sender using WebCrypto (no dependencies).

interface ServiceAccount { project_id: string; client_email: string; private_key: string }

let cached: { token: string; exp: number } | null = null;

const b64url = (data: ArrayBuffer | string) => {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 60 > now) return cached.token;

  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );
  const pem = sa.private_key.replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claim}`));
  const jwt = `${header}.${claim}.${b64url(sig)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!res.ok) throw new Error(`FCM auth failed: ${res.status} ${await res.text()}`);
  const { access_token, expires_in } = (await res.json()) as { access_token: string; expires_in: number };
  cached = { token: access_token, exp: now + expires_in };
  return access_token;
}

export type PushResult = "sent" | "invalid-token" | "skipped" | "error";

export async function sendPush(
  serviceAccountJson: string | undefined,
  fcmToken: string,
  title: string,
  body: string,
): Promise<PushResult> {
  if (!serviceAccountJson) {
    console.log(`[push skipped: FCM_SERVICE_ACCOUNT not set] ${title} — ${body}`);
    return "skipped";
  }
  try {
    const sa = JSON.parse(serviceAccountJson) as ServiceAccount;
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: { authorization: `Bearer ${await accessToken(sa)}`, "content-type": "application/json" },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          notification: { title, body },
          android: { priority: "HIGH", notification: { channel_id: "myboo_events" } },
        },
      }),
    });
    if (res.ok) return "sent";
    if (res.status === 404 || res.status === 400) {
      const text = await res.text();
      if (text.includes("UNREGISTERED") || text.includes("INVALID_ARGUMENT")) return "invalid-token";
      console.error("FCM error", res.status, text);
      return "error";
    }
    console.error("FCM error", res.status, await res.text());
    return "error";
  } catch (e) {
    console.error("FCM exception", e);
    return "error";
  }
}
