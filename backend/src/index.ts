import "./auth";
import "./circles";
import { landingHtml } from "./landing";
import { dispatch, route, type Env } from "./router";
import { runProbes, statusJson, statusPage } from "./status";
import { SECURITY_HEADERS } from "./theme";
import { err, json } from "./util";

export type { Env };

route("GET", "/health", false, async () => json({ ok: true }));

const html = (body: string, cache: string) => new Response(body, { headers: { ...SECURITY_HEADERS, "cache-control": cache } });

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    if (req.method === "GET" || req.method === "HEAD") {
      switch (url.pathname) {
        case "/":
        case "/index.html":
          return html(landingHtml(), "public, max-age=300");
        case "/status":
        case "/status/":
          return html(await statusPage(env), "public, max-age=30");
        case "/status.json":
          return statusJson(env);
      }
    }
    return (await dispatch(req, env, ctx)) ?? err(404, "not found");
  },

  // Cron (every 5 minutes): active health probes for the status page.
  async scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext) {
    ctx.waitUntil(runProbes(env));
  },
} satisfies ExportedHandler<Env>;
