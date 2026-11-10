// Password hashing with PBKDF2-SHA256 via WebCrypto (Workers caps iterations at 100,000).
const ITER = 100_000;
const enc = new TextEncoder();

const b64 = (buf: ArrayBuffer | Uint8Array) => {
  let s = "";
  for (const b of new Uint8Array(buf as ArrayBuffer)) s += String.fromCharCode(b);
  return btoa(s);
};
const unb64 = (s: string) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));

async function derive(password: string, salt: Uint8Array, iter: number): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", enc.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: iter }, key, 256);
  return new Uint8Array(bits);
}

export async function hashPassword(password: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  return `pbkdf2$${ITER}$${b64(salt)}$${b64(await derive(password, salt, ITER))}`;
}

export function timingSafeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

export async function verifyPassword(password: string, stored: string): Promise<boolean> {
  const [scheme, iter, salt, hash] = stored.split("$");
  if (scheme !== "pbkdf2" || !iter || !salt || !hash) return false;
  return timingSafeEqual(await derive(password, unb64(salt), Number(iter)), unb64(hash));
}

/** Burn comparable CPU when the account doesn't exist, so timing doesn't reveal it. */
export async function dummyVerify(password: string) {
  await derive(password, new Uint8Array(16), ITER);
}
