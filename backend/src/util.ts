export const json = (data: unknown, status = 200, headers: HeadersInit = {}) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store", ...headers },
  });
export const err = (status: number, message: string) => json({ error: message }, status);

export const hex = (buf: ArrayBuffer | Uint8Array) =>
  [...new Uint8Array(buf as ArrayBuffer)].map((b) => b.toString(16).padStart(2, "0")).join("");
export const sha256 = async (s: string) =>
  hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
export const randomHex = (bytes: number) => hex(crypto.getRandomValues(new Uint8Array(bytes)));
export const newId = () => crypto.randomUUID();

export const isNum = (v: unknown): v is number => typeof v === "number" && Number.isFinite(v);
export const cleanName = (v: unknown, max = 40) =>
  typeof v === "string" && v.trim().length > 0 ? v.trim().slice(0, max) : null;

const INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
export const inviteCode = () =>
  Array.from(crypto.getRandomValues(new Uint8Array(6)), (b) => INVITE_ALPHABET[b % INVITE_ALPHABET.length]).join("");

/** Uniform random 6-digit code. */
export const sixDigits = () => {
  const n = new Uint32Array(1);
  do crypto.getRandomValues(n); while (n[0] >= 4_294_000_000); // avoid modulo bias
  return String(n[0] % 1_000_000).padStart(6, "0");
};

export const readBody = async (req: Request): Promise<Record<string, unknown>> => {
  try {
    const j = await req.json();
    return j && typeof j === "object" ? (j as Record<string, unknown>) : {};
  } catch {
    return {};
  }
};

export const normEmail = (v: unknown): string | null => {
  if (typeof v !== "string") return null;
  const e = v.trim().toLowerCase();
  return e.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(e) ? e : null;
};

export const escapeHtml = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
