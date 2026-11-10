import "./auth";
import "./circles";
import { LANDING_HTML } from "./landing";
import { dispatch, route, type Env } from "./router";
import { err, json } from "./util";

export type { Env };

route("GET", "/health", false, async () => json({ ok: true }));

const SECURITY_HEADERS = {
  "content-type": "text/html; charset=utf-8",
  "cache-control": "public, max-age=300",
  "x-content-type-options": "nosniff",
  "referrer-policy": "strict-origin-when-cross-origin",
  "content-security-policy":
    "default-src 'none'; style-src 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src data:; base-uri 'none'; form-action 'none'",
};

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    if (req.method === "GET" && (url.pathname === "/" || url.pathname === "/index.html")) {
      return new Response(LANDING_HTML, { headers: SECURITY_HEADERS });
    }
    return (await dispatch(req, env, ctx)) ?? err(404, "not found");
  },
} satisfies ExportedHandler<Env>;
