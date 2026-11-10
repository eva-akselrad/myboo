// Pastel-notebook landing page served at the domain root.
export const LANDING_HTML = `<!doctype html>
<html lang="en"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>MyBoo — keep your people close</title>
<meta name="description" content="Private location sharing for the people you love. Live map, arrival and departure alerts, and driving notifications.">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Patrick+Hand&family=Caveat:wght@600;700&display=swap" rel="stylesheet">
<style>
:root{--paper:#fffbf2;--ink:#3b3a4a;--pink:#ffc2d1;--mint:#c4efd9;--sky:#bfe0ff;--butter:#fff1a8;--lav:#dccfff;--line:#cfe3f7;--margin:#f7a8b8}
@media (prefers-color-scheme:dark){:root{--paper:#26232e;--ink:#f1ecf7;--pink:#7a4558;--mint:#2f5a49;--sky:#2f4c6b;--butter:#6b5f2a;--lav:#4a3f6e;--line:#3a3648;--margin:#8a4b5c}}
*{box-sizing:border-box}
html{scroll-behavior:smooth}
body{margin:0;background:var(--paper);color:var(--ink);font:20px/1.55 "Patrick Hand",system-ui,sans-serif;
 background-image:linear-gradient(var(--line) 1px,transparent 1px);background-size:100% 34px;background-position:0 12px}
body::before{content:"";position:fixed;left:clamp(14px,6vw,72px);top:0;bottom:0;width:2px;background:var(--margin);opacity:.6}
main{max-width:960px;margin:0 auto;padding:28px clamp(28px,9vw,96px) 80px}
nav{display:flex;align-items:center;justify-content:space-between;padding:8px 0 40px}
.logo{display:flex;align-items:center;gap:10px;font:700 34px Caveat,cursive}
.logo svg{width:38px;height:38px}
.btn{display:inline-block;background:var(--pink);color:var(--ink);text-decoration:none;border:2.5px solid var(--ink);border-radius:999px;
 padding:10px 24px;box-shadow:4px 4px 0 var(--ink);transition:transform .12s,box-shadow .12s}
.btn:hover{transform:translate(2px,2px);box-shadow:2px 2px 0 var(--ink)}
h1{font:700 clamp(44px,9vw,84px)/.98 Caveat,cursive;margin:0 0 18px;letter-spacing:-1px}
h1 mark{background:linear-gradient(transparent 55%,var(--butter) 55%);color:inherit;padding:0 4px}
.lead{max-width:520px;margin:0 0 28px;font-size:23px}
.hero{display:grid;gap:40px;grid-template-columns:1.2fr 1fr;align-items:center}
@media(max-width:760px){.hero{grid-template-columns:1fr}}
.map{position:relative;aspect-ratio:1/1;border:3px solid var(--ink);border-radius:26px;background:var(--mint);box-shadow:8px 8px 0 var(--lav);transform:rotate(1.6deg);overflow:hidden}
.map::before{content:"";position:absolute;inset:0;background:
 radial-gradient(circle at 30% 30%,var(--sky) 0 22%,transparent 23%),
 repeating-linear-gradient(35deg,transparent 0 46px,rgba(255,255,255,.55) 46px 52px),
 repeating-linear-gradient(-55deg,transparent 0 60px,rgba(255,255,255,.4) 60px 65px)}
.pin{position:absolute;width:46px;height:46px;border-radius:50%;border:3px solid var(--ink);display:grid;place-items:center;font:700 24px Caveat,cursive;color:#3b3a4a}
.pin.a{left:22%;top:28%;background:var(--pink)}.pin.b{left:58%;top:46%;background:var(--butter)}.pin.c{left:36%;top:68%;background:var(--sky)}
.pin.b::after{content:"";position:absolute;inset:-12px;border-radius:50%;border:3px solid var(--ink);opacity:.35;animation:p 1.8s infinite}
@keyframes p{from{transform:scale(.6);opacity:.5}to{transform:scale(1.5);opacity:0}}
.zone{position:absolute;left:46%;top:14%;width:34%;aspect-ratio:1;border-radius:50%;border:3px dashed var(--ink);background:rgba(255,255,255,.35)}
.tape{position:absolute;top:-12px;left:50%;width:110px;height:30px;background:var(--butter);opacity:.9;transform:translateX(-50%) rotate(-3deg);border:2px solid var(--ink)}
.cards{display:grid;gap:26px;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));margin-top:90px}
.card{border:2.5px solid var(--ink);border-radius:20px;padding:22px 22px 20px;box-shadow:5px 5px 0 var(--ink)}
.card:nth-child(1){background:var(--pink);transform:rotate(-1.2deg)}.card:nth-child(2){background:var(--sky);transform:rotate(.9deg)}.card:nth-child(3){background:var(--mint);transform:rotate(-.6deg)}.card:nth-child(4){background:var(--lav);transform:rotate(1.1deg)}
.card h3{font:700 30px Caveat,cursive;margin:0 0 4px}.card p{margin:0}
footer{margin-top:90px;font-size:16px;opacity:.75;display:flex;gap:18px;flex-wrap:wrap}
</style></head>
<body><main>
<nav><div class="logo"><svg viewBox="0 0 40 40"><path d="M5 35V19C5 7 35 7 35 19v16l-5-3-5 3-5-3-5 3-5-3z" fill="#ffc2d1" stroke="#3b3a4a" stroke-width="2.5" stroke-linejoin="round"/><circle cx="15" cy="19" r="2.4" fill="#3b3a4a"/><circle cx="25" cy="19" r="2.4" fill="#3b3a4a"/></svg>MyBoo</div>
<a class="btn" href="#get">Get the app</a></nav>
<section class="hero">
<div><h1>Keep your <mark>people</mark> close.</h1>
<p class="lead">A cozy, private way to see that everyone got where they were going. Live map, arrival and departure alerts, and a nudge when someone starts driving.</p>
<a id="get" class="btn" href="https://github.com/eva-akselrad/myboo">Get started</a></div>
<div class="map" aria-hidden="true"><span class="tape"></span><div class="zone"></div><div class="pin a">M</div><div class="pin b">L</div><div class="pin c">S</div></div>
</section>
<section class="cards">
<div class="card"><h3>Live map</h3><p>See your circle on a real map, updated as they move.</p></div>
<div class="card"><h3>Arrive &amp; leave</h3><p>Get a note when someone reaches Home, School or Work.</p></div>
<div class="card"><h3>Driving alerts</h3><p>Know when someone's on the road, and how fast.</p></div>
<div class="card"><h3>Private by design</h3><p>Email sign-in with two-step codes. You choose who's in your circle.</p></div>
</section>
<footer><span>Made with care.</span><span>Map data &copy; OpenStreetMap contributors</span></footer>
</main></body></html>`;
