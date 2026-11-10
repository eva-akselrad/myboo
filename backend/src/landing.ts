import { page } from "./theme";

const CSS = `
section{position:relative;margin-top:104px}
.hero{display:grid;gap:30px 50px;grid-template-columns:1.05fr .95fr;align-items:center;padding-top:34px}
@media(max-width:820px){.hero{grid-template-columns:1fr}}
h1{font-size:clamp(54px,10vw,96px);margin:14px 0 18px;letter-spacing:-1px}
.lead{font-size:clamp(21px,3vw,25px);max-width:520px;margin-bottom:26px}
.cta{display:flex;gap:16px;flex-wrap:wrap;align-items:center}
.phones{position:relative;height:clamp(430px,62vw,600px)}
.phone{position:absolute;width:min(46%,250px);aspect-ratio:9/20;border:3px solid var(--ink);border-radius:30px;background:var(--card);box-shadow:7px 7px 0 var(--shadow);overflow:hidden}
.phone img{display:block;width:100%;height:100%;object-fit:cover;object-position:top}
.phones .p1{left:2%;top:6%;transform:rotate(-5deg)}
.phones .p2{right:2%;top:0;transform:rotate(4.5deg);z-index:2}
.phones .scribble{position:absolute;left:3%;bottom:-3%;font:700 28px Caveat,cursive;transform:rotate(-4deg);white-space:nowrap;max-width:96%}
.grid{display:grid;gap:30px;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));margin-top:34px}
.note{position:relative;padding:26px 24px 22px}
.note:nth-child(6n+1){background:var(--pink);transform:rotate(-1.1deg)}.note:nth-child(6n+2){background:var(--sky);transform:rotate(.9deg)}
.note:nth-child(6n+3){background:var(--mint);transform:rotate(-.6deg)}.note:nth-child(6n+4){background:var(--lav);transform:rotate(1.1deg)}
.note:nth-child(6n+5){background:var(--peach);transform:rotate(-.9deg)}.note:nth-child(6n){background:var(--butter);transform:rotate(.7deg)}
.note p{margin:0}
.steps{display:grid;gap:34px;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));margin-top:34px;counter-reset:s}
.step{position:relative;padding-left:78px}
.step::before{counter-increment:s;content:counter(s);position:absolute;left:0;top:-4px;width:58px;height:58px;border-radius:50%;border:3px solid var(--ink);display:grid;place-items:center;font:700 38px Caveat,cursive;box-shadow:3px 3px 0 var(--shadow)}
.step:nth-child(1)::before{background:var(--pink)}.step:nth-child(2)::before{background:var(--sky)}.step:nth-child(3)::before{background:var(--mint)}
.step p{margin:0}
.show{display:grid;gap:40px;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));margin-top:40px;justify-items:center}
.show figure{margin:0;text-align:center}
.show .phone{position:relative;width:min(100%,230px)}
.show figure:nth-child(1) .phone{transform:rotate(-2deg)}.show figure:nth-child(2) .phone{transform:rotate(1.5deg)}.show figure:nth-child(3) .phone{transform:rotate(-1deg)}
.show figcaption{font:700 28px Caveat,cursive;margin-top:20px}
.safe{display:grid;gap:14px 40px;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));margin:26px 0 0;padding:0;list-style:none}
.safe li{padding-left:34px;position:relative}
.safe li::before{content:"";position:absolute;left:2px;top:7px;width:20px;height:20px;border-radius:6px;background:var(--mint);border:2px solid var(--ink)}
.safe li::after{content:"";position:absolute;left:9px;top:10px;width:5px;height:10px;border:solid var(--ink);border-width:0 2.5px 2.5px 0;transform:rotate(40deg)}
details{border:2.5px solid var(--ink);border-radius:18px;padding:14px 20px;margin-bottom:16px;background:var(--card);box-shadow:4px 4px 0 var(--shadow)}
summary{cursor:pointer;font:700 28px Caveat,cursive;list-style:none;display:flex;justify-content:space-between;gap:12px}
summary::-webkit-details-marker{display:none}
summary::after{content:"+";font-size:34px;line-height:.9}
details[open] summary::after{content:"–"}
details p{margin:10px 0 2px;color:var(--muted)}
.band{position:relative;margin-top:110px;border:3px solid var(--ink);border-radius:30px;padding:48px 32px;text-align:center;background:var(--pink);box-shadow:8px 8px 0 var(--shadow);transform:rotate(-.5deg)}
.band h2{font-size:clamp(42px,7vw,64px)}
.band p{max-width:520px;margin:6px auto 22px}
`;

const FEATURES: [string, string][] = [
  ["Live map", "See everyone in your circle on a real map that updates as they move, with a tidy pin for each person."],
  ["Arrive &amp; leave notes", "Pick the places that matter. Get a little note when someone gets to Home, or leaves School."],
  ["Driving heads-up", "Know when someone's hit the road and how fast they're going. No more &ldquo;are you driving?&rdquo; texts."],
  ["Circles by invite", "Family, roommates, best friends. Share a six-letter code and only the people you invite can see you."],
  ["Two-step sign-in", "Turn on emailed sign-in codes so a stolen password isn't enough to get into your account."],
  ["Light or dark", "Pastel paper by day, a cozy night-notebook after dark. It follows your phone's setting."],
];

const FAQ: [string, string][] = [
  ["Who can see where I am?", "Only people in a circle you've joined, and only while sharing is switched on. You can turn it off with one tap, any time."],
  ["Which phones does it work on?", "MyBoo is an Android app today (Android 8 or newer). It needs location permission, and for alerts to work with the screen off it asks for &ldquo;always&rdquo; location access."],
  ["Will it drain my battery?", "It uses balanced-power location, roughly one update every 20 seconds while sharing is on, rather than constant high-accuracy GPS."],
  ["How do the alerts work?", "Your phone sends position updates to our server. The server notices when you cross into or out of a place, or start moving at driving speed, and pushes a note to the rest of your circle."],
  ["How do I delete my data?", "Open Settings in the app and tap &ldquo;Delete my account&rdquo;. Your profile, location and circle memberships are removed right away."],
  ["Is something broken?", "Check the <a href=\"/status\"><b>status page</b></a> for live health and 30-day history."],
];

const SCRIPT = `
fetch('/status.json').then(function(r){return r.json()}).then(function(s){
  var d=document.getElementById('sdot'),t=document.getElementById('stext');
  var m={operational:['ok','All systems operational'],degraded:['warn','Some systems are slow'],outage:['bad','Something is down']}[s.status];
  if(m){d.className='dot '+m[0];t.textContent=m[1];}
}).catch(function(){});
`;

export function landingHtml(): string {
  const features = FEATURES.map(([h, p]) => `<div class="card note"><span class="tape"></span><h3>${h}</h3><p>${p}</p></div>`).join("");
  const faq = FAQ.map(([q, a]) => `<details><summary>${q}</summary><p>${a}</p></details>`).join("");

  const body = `<main>
<div class="hero">
  <div>
    <span class="tag">Android &middot; early access</span>
    <h1>Keep your <span class="hl">people</span> close.</h1>
    <p class="lead">A cozy, private way to see that everyone got where they were going. A live map, a note when someone arrives or leaves, and a heads-up when they start driving.</p>
    <div class="cta"><a class="btn" href="#how">See how it works</a><a class="btn alt" href="/status"><span class="dot ok"></span>Status</a></div>
  </div>
  <div class="phones" aria-hidden="false">
    <div class="phone p1"><img src="/img/welcome.webp" alt="MyBoo welcome screen on notebook paper" width="540" height="1200"></div>
    <div class="phone p2"><img src="/img/map-light.webp" alt="MyBoo live map with the family circle" width="540" height="1200"></div>
    <span class="scribble">your whole circle, one glance &#8599;</span>
  </div>
</div>

<section id="features"><h2>Everything you need, <span class="hl">nothing you don't</span></h2>
<p>Simple on purpose, so it's easy for the whole family to use.</p>
<div class="grid">${features}</div></section>

<section id="how"><h2>How it works</h2>
<div class="steps">
  <div class="step"><h3>Make an account</h3><p>Sign up with your email and confirm it with a quick code. That's it.</p></div>
  <div class="step"><h3>Start a circle</h3><p>Name it, add Home, School or Work, and send the invite code to your people.</p></div>
  <div class="step"><h3>Share &amp; relax</h3><p>Flip sharing on. MyBoo pings the circle when someone arrives, leaves or hits the road.</p></div>
</div></section>

<section><h2>A map that feels like <span class="hl">your notebook</span></h2>
<div class="show">
  <figure><div class="phone"><img loading="lazy" src="/img/map-light.webp" alt="Live map, light theme" width="540" height="1200"></div><figcaption>Live map</figcaption></figure>
  <figure><div class="phone"><img loading="lazy" src="/img/map-dark.webp" alt="Live map, dark theme" width="540" height="1200"></div><figcaption>Night mode</figcaption></figure>
  <figure><div class="phone"><img loading="lazy" src="/img/settings.webp" alt="Account settings with two-step sign-in" width="540" height="1200"></div><figcaption>You're in control</figcaption></figure>
</div></section>

<section><h2>Private <span class="hl">by design</span></h2>
<p>Location is personal. Here's how we treat it.</p>
<ul class="safe">
  <li>Accounts are verified by email, with optional two-step sign-in codes.</li>
  <li>Passwords are hashed, never stored. Sign-in codes expire in 10 minutes and work once.</li>
  <li>Only people you invite into a circle can see you, and sharing is off until you turn it on.</li>
  <li>See every device that's signed in, and sign any of them out, or all of them.</li>
  <li>Delete your account any time and your data goes with it.</li>
  <li>Repeated wrong passwords temporarily lock sign-in to protect your account.</li>
</ul></section>

<section><h2>Good <span class="hl">questions</span></h2>${faq}</section>

<section id="get" class="band"><span class="tape"></span>
<h2>Ready to keep your people close?</h2>
<p>MyBoo for Android is in early access. We'll add a download link here as soon as it's ready.</p>
<a class="btn alt" href="/status"><span class="dot ok"></span>Check status</a></section>
</main>
<footer><span><span class="dot" id="sdot"></span><a href="/status" id="stext">Status</a></span><span>Map data &copy; OpenStreetMap contributors</span><span>Made with care.</span></footer>`;

  return page({
    title: "MyBoo | keep your people close",
    description: "Private location sharing for the people you love. A live map, arrival and departure alerts, and driving heads-ups, wrapped in a cozy pastel notebook.",
    css: CSS,
    body,
    script: SCRIPT,
  });
}
