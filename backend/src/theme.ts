// Shared pastel-notebook styling for the public pages (landing, status).

export const SECURITY_HEADERS = {
  "content-type": "text/html; charset=utf-8",
  "x-content-type-options": "nosniff",
  "referrer-policy": "strict-origin-when-cross-origin",
  "content-security-policy":
    "default-src 'none'; style-src 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; " +
    "img-src 'self' data:; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
};

export const LOGO_SVG = `<svg viewBox="0 0 40 40" aria-hidden="true"><path d="M5 35V19C5 7 35 7 35 19v16l-4-3.5-4 3.5-3.5-3.5L20 35l-3.5-3.5L13 35l-4-3.5z" fill="#ffb3c6" stroke="#3b3a4a" stroke-width="2.4" stroke-linejoin="round"/><circle cx="15.5" cy="19" r="2.2" fill="#3b3a4a"/><circle cx="24.5" cy="19" r="2.2" fill="#3b3a4a"/><circle cx="12" cy="23" r="2" fill="#ff8fa9" opacity=".6"/><circle cx="28" cy="23" r="2" fill="#ff8fa9" opacity=".6"/><path d="M17.5 23.5q2.5 2.6 5 0" fill="none" stroke="#3b3a4a" stroke-width="1.8" stroke-linecap="round"/></svg>`;

const BASE_CSS = `
:root{--paper:#fffaf0;--card:#fffefa;--ink:#3b3a4a;--muted:#6f6c80;--pink:#ffd1dc;--peach:#ffe0c7;--butter:#fff3b0;--mint:#cdf0de;--sky:#cfe6ff;--lav:#e2d8ff;--line:#d3e6f8;--margin:#f7a8b8;--shadow:#3b3a4a}
@media (prefers-color-scheme:dark){:root{--paper:#25222d;--card:#2e2b38;--ink:#f1ecf7;--muted:#a9a4b8;--pink:#6b4455;--peach:#6a5144;--butter:#6a5f2e;--mint:#3b6454;--sky:#38567a;--lav:#51467a;--line:#353246;--margin:#8c4c5e;--shadow:rgba(241,236,247,.28)}}
*{box-sizing:border-box}
html{scroll-behavior:smooth}
body{margin:0;background:var(--paper);color:var(--ink);font:20px/1.55 "Patrick Hand",system-ui,sans-serif;
 background-image:linear-gradient(var(--line) 1.5px,transparent 1.5px);background-size:100% 34px;background-position:0 14px;overflow-x:hidden}
body::before{content:"";position:fixed;left:clamp(14px,5vw,60px);top:0;bottom:0;width:2px;background:var(--margin);opacity:.7;z-index:0}
a{color:inherit}
main{position:relative;z-index:1;max-width:1040px;margin:0 auto;padding:0 clamp(26px,8vw,90px) 70px}
h1,h2,h3{font-family:Caveat,cursive;line-height:1;margin:0}
h2{font-size:clamp(38px,6vw,54px);margin-bottom:10px}
h3{font-size:30px;margin-bottom:4px}
p{margin:0 0 12px}
nav{position:relative;z-index:2;max-width:1040px;margin:0 auto;display:flex;align-items:center;gap:26px;padding:22px clamp(26px,8vw,90px) 8px}
.logo{display:flex;align-items:center;gap:10px;font:700 36px Caveat,cursive;text-decoration:none}
.logo svg{width:40px;height:40px}
nav .links{margin-left:auto;display:flex;gap:22px;align-items:center}
nav .links a{text-decoration:none;opacity:.85}
nav .links a:hover{opacity:1;text-decoration:underline wavy;text-underline-offset:5px}
@media(max-width:640px){nav{gap:12px}nav .links a.hide-sm{display:none}nav .links{gap:14px}.logo{font-size:30px}.logo svg{width:34px;height:34px}nav .btn{padding:5px 16px;font-size:18px}}
.btn{display:inline-block;white-space:nowrap;background:#ffb3c6;color:#3b3a4a;text-decoration:none;border:2.5px solid var(--ink);border-radius:999px;padding:10px 26px;box-shadow:4px 4px 0 var(--shadow);transition:transform .12s,box-shadow .12s;cursor:pointer}
.btn:hover{transform:translate(2px,2px);box-shadow:2px 2px 0 var(--shadow)}
.btn.alt{background:var(--sky);color:var(--ink)}
.tag{display:inline-block;background:var(--butter);border:2px solid var(--ink);border-radius:9px;padding:1px 12px;font-size:17px;transform:rotate(-1.5deg)}
.card{border:2.5px solid var(--ink);border-radius:22px;padding:22px 24px;box-shadow:5px 5px 0 var(--shadow);background:var(--card)}
.tape{position:absolute;top:-12px;left:50%;width:84px;height:24px;background:var(--butter);opacity:.92;transform:translateX(-50%) rotate(-3deg);border:2px solid rgba(59,58,74,.5)}
.hl{background:linear-gradient(transparent 56%,var(--butter) 56%);padding:0 5px}
footer{position:relative;z-index:1;max-width:1040px;margin:0 auto;padding:30px clamp(26px,8vw,90px) 50px;font-size:17px;color:var(--muted);display:flex;gap:10px 26px;flex-wrap:wrap;align-items:center}
footer a{text-decoration:none;border-bottom:2px dotted var(--muted)}
.dot{display:inline-block;width:11px;height:11px;border-radius:50%;background:#bbb;border:2px solid var(--ink);vertical-align:-1px;margin-right:6px}
.dot.ok{background:#7be0b0}.dot.warn{background:#ffd966}.dot.bad{background:#ff8fa9}
`;

export function page(opts: { title: string; description: string; css?: string; body: string; script?: string; refresh?: number }): string {
  return `<!doctype html>
<html lang="en"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${opts.title}</title>
<meta name="description" content="${opts.description}">
<meta name="theme-color" content="#fffaf0">
<link rel="icon" href="data:image/svg+xml,${encodeURIComponent(LOGO_SVG.replace("<svg ", '<svg xmlns="http://www.w3.org/2000/svg" '))}">
${opts.refresh ? `<meta http-equiv="refresh" content="${opts.refresh}">` : ""}
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Caveat:wght@600;700&family=Patrick+Hand&display=swap" rel="stylesheet">
<style>${BASE_CSS}${opts.css ?? ""}</style></head>
<body>
<nav><a class="logo" href="/">${LOGO_SVG}MyBoo</a>
<div class="links"><a class="hide-sm" href="/#features">Features</a><a class="hide-sm" href="/#how">How it works</a><a href="/status">Status</a><a class="btn" href="/#get">Get the app</a></div></nav>
${opts.body}
${opts.script ? `<script>${opts.script}</script>` : ""}
</body></html>`;
}
