'use strict';
// Commerce Control — the lab's stand-in for the app team's application.
//
// One app, many banks. It has NO login page. The only way in is the SSO landing endpoint
//     /sso/launch/<org-id>
// which PingFederate's OpenToken SP adapter sends the browser to, carrying an OpenToken.
//
// Zero npm dependencies on purpose: every step is in this file or in opentoken.js.

const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const opentoken = require('./opentoken');

const PORT = Number(process.env.PORT || 3000);
const PUBLIC_ORIGIN = (process.env.PUBLIC_ORIGIN || process.env.RENDER_EXTERNAL_URL || `http://localhost:${PORT}`).replace(/\/+$/, '');
const SECURE_COOKIES = PUBLIC_ORIGIN.startsWith('https://');
// The password typed into the OpenToken SP adapter instance in PingFederate. A SECRET.
const OPENTOKEN_PASSWORD = process.env.OPENTOKEN_PASSWORD || '';
// The adapter's "Token Name" (the query parameter / form field / cookie name).
const TOKEN_NAME = process.env.OPENTOKEN_TOKEN_NAME || 'opentoken';
// Lab convenience only: where the two pretend banks live, so the "no session" page can show
// what a bank's intranet tile would link to.
const BANKS_ORIGIN = (process.env.BANKS_ORIGIN || '').replace(/\/+$/, '');
const SESSION_MINUTES = Number(process.env.SESSION_MINUTES || 15);

const ORGS = JSON.parse(fs.readFileSync(path.join(__dirname, 'users.json'), 'utf8'));

const sessions = new Map();   // session id -> { orgId, subject, profile, attributes, via, expires }
const seenTokens = new Map(); // sha256(token) -> expiry ms. Makes each token single-use.
const attempts = [];          // last 25 SSO attempts, for the demo log page. Never stores tokens.

// ---------------------------------------------------------------- helpers
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function page(title, body) {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)} · Commerce Control (lab)</title>
<style>
  :root { color-scheme: light dark; --bg:#f6f7f9; --card:#fff; --ink:#1b2430; --mute:#5b6675; --line:#d9dee5; --ok:#0b7a43; --bad:#b3261e; --accent:#1f4fbf; }
  @media (prefers-color-scheme: dark) { :root { --bg:#12161c; --card:#1b212a; --ink:#e7ebf0; --mute:#9aa6b4; --line:#2e3742; --ok:#5fd39a; --bad:#ff8a80; --accent:#8fb0ff; } }
  body { margin:0; font:16px/1.5 system-ui, sans-serif; background:var(--bg); color:var(--ink); }
  main { max-width:820px; margin:0 auto; padding:24px 16px 48px; }
  h1 { font-size:1.5rem; margin:0 0 4px; } h2 { font-size:1.1rem; margin:24px 0 8px; }
  .lab { color:var(--mute); font-size:.9rem; margin-bottom:20px; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:16px; margin:12px 0; overflow-x:auto; }
  table { border-collapse:collapse; width:100%; } th, td { text-align:left; padding:6px 10px 6px 0; border-bottom:1px solid var(--line); vertical-align:top; }
  th { color:var(--mute); font-weight:600; white-space:nowrap; } code { font-size:.9em; word-break:break-all; }
  .ok { color:var(--ok); font-weight:600; } .bad { color:var(--bad); font-weight:600; }
  a { color:var(--accent); } .btn { display:inline-block; padding:8px 14px; border:1px solid var(--accent); border-radius:8px; text-decoration:none; margin:4px 8px 4px 0; }
</style></head><body><main>
<h1>Commerce Control</h1><div class="lab">Lab stand-in for the app team's application · dummy users only</div>
${body}
<p class="lab"><a href="/">Home</a> · <a href="/sso/log">SSO attempt log</a> · <a href="/logout">Sign out</a></p>
</main></body></html>`;
}

function send(res, status, html, headers = {}) {
  res.writeHead(status, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', ...headers });
  res.end(html);
}

function cookieHeader(value, maxAgeSeconds) {
  return `cc_session=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAgeSeconds}${SECURE_COOKIES ? '; Secure' : ''}`;
}

function currentSession(req) {
  const m = /(?:^|;\s*)cc_session=([a-f0-9]{64})/.exec(req.headers.cookie || '');
  if (!m) return null;
  const s = sessions.get(m[1]);
  if (!s || s.expires < Date.now()) { sessions.delete(m[1]); return null; }
  return s;
}

function readForm(req) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > 64 * 1024) { reject(new Error('body too large')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => resolve(new URLSearchParams(Buffer.concat(chunks).toString('utf8'))));
    req.on('error', reject);
  });
}

function record(orgId, ok, detail, subject) {
  attempts.unshift({ at: new Date().toISOString(), orgId, ok, detail, subject: subject || '—' });
  attempts.length = Math.min(attempts.length, 25);
  console.log(`[sso] org=${orgId} ok=${ok} subject=${subject || '-'} detail=${detail}`);
}

// ---------------------------------------------------------------- the SSO landing endpoint
// This is the ONE thing the app team builds for SSO. Each numbered step is a decision the
// real app has to make too.
async function ssoLanding(req, res, url, orgId) {
  const fail = (status, why, subject) => {
    record(orgId, false, why, subject);
    send(res, status, page('Sign-in failed', `<div class="card"><p class="bad">Sign-in failed</p><p>${esc(why)}</p>
      <p>Please start again from your organisation's portal.</p></div>`));
  };

  // 1. Is this an organisation we know? The org id comes from the URL path that PingFederate
  //    was configured to send this bank's users to.
  const org = Object.hasOwn(ORGS, orgId) ? ORGS[orgId] : null;
  if (!org) return fail(404, `Unknown organisation "${orgId}".`);

  // 2. Find the token. The adapter's transport mode decides where it is:
  //    query parameter (GET) or form POST. Cookie transport is not used in this lab because
  //    PingFederate and the app are on different domains.
  let token = url.searchParams.get(TOKEN_NAME);
  let via = 'query parameter';
  if (!token && req.method === 'POST') {
    try { token = (await readForm(req)).get(TOKEN_NAME); via = 'form POST'; } catch { return fail(400, 'Could not read the request body.'); }
  }
  if (!token) return fail(400, `No "${TOKEN_NAME}" was sent. This URL only works when you arrive from your bank through FinCo's PingFederate.`);

  // 3. Decrypt and integrity-check it with the shared password.
  let attributes;
  try { attributes = opentoken.decode(token, OPENTOKEN_PASSWORD).attributes; } catch (e) { return fail(401, `The token was rejected: ${e.message}.`); }
  const subject = String(attributes.subject || '');

  // 4. Is it inside its validity window? (PingFederate's default lifetime is a few minutes.)
  try { opentoken.checkTimes(attributes); } catch (e) { return fail(401, `The token was rejected: ${e.message}.`, subject); }

  // 5. Has this exact token been used before? Each token is accepted once.
  const fingerprint = crypto.createHash('sha256').update(token).digest('hex');
  for (const [k, exp] of seenTokens) if (exp < Date.now()) seenTokens.delete(k);
  if (seenTokens.has(fingerprint)) return fail(401, 'This token was already used once (replay).', subject);
  seenTokens.set(fingerprint, Date.parse(attributes['not-on-or-after']) + 120000);

  // 6. Optional cross-check. If PingFederate also puts the org id INSIDE the token (an
  //    attribute named org_id), it must agree with the URL. Inactive unless that attribute is mapped.
  if (attributes.org_id && attributes.org_id !== orgId) return fail(403, 'The token was issued for a different organisation than this URL.', subject);

  // 7. Find the EXISTING user by organisation + subject together. No auto-create.
  if (!subject) return fail(401, 'The token has no subject.');
  const profile = Object.hasOwn(org.users, subject.toLowerCase()) ? org.users[subject.toLowerCase()] : null;
  if (!profile) return fail(403, `${subject} is not provisioned in Commerce Control for ${org.name}.`, subject);

  // 8. Swap the token for the app's own session, then redirect so the token leaves the address bar.
  const sid = crypto.randomBytes(32).toString('hex');
  sessions.set(sid, { orgId, orgName: org.name, subject, profile, attributes, via, at: new Date().toISOString(), expires: Date.now() + SESSION_MINUTES * 60000 });
  record(orgId, true, `signed in (token arrived by ${via})`, subject);
  res.writeHead(303, { Location: '/', 'Set-Cookie': cookieHeader(sid, SESSION_MINUTES * 60), 'Cache-Control': 'no-store' });
  res.end();
}

// ---------------------------------------------------------------- pages
function home(req, res) {
  const s = currentSession(req);
  if (!s) {
    const tiles = BANKS_ORIGIN
      ? `<h2>Lab shortcut: what each bank's intranet tile links to</h2><div class="card">
          <a class="btn" href="${esc(BANKS_ORIGIN)}/realms/bank-a/protocol/saml/clients/commerce-control">Bank A portal tile</a>
          <a class="btn" href="${esc(BANKS_ORIGIN)}/realms/bank-b/protocol/saml/clients/commerce-control">Bank B portal tile</a>
          <p class="lab">In real life these links sit on each bank's own intranet, not here.</p></div>`
      : '';
    return send(res, 200, page('Not signed in', `<div class="card"><p><strong>You are not signed in.</strong></p>
      <p>Commerce Control has no login page. Open it from your organisation's portal, and your organisation will sign you in.</p></div>${tiles}`));
  }
  const rows = Object.entries(s.attributes).map(([k, v]) => `<tr><th>${esc(k)}</th><td><code>${esc([].concat(v).join(', '))}</code></td></tr>`).join('');
  send(res, 200, page('Welcome', `<div class="card"><p class="ok">Signed in through single sign-on</p>
    <table><tr><th>User</th><td>${esc(s.profile.displayName)} (${esc(s.subject)})</td></tr>
    <tr><th>Organisation</th><td>${esc(s.orgName)} · <code>${esc(s.orgId)}</code> (from the landing URL)</td></tr>
    <tr><th>Role in this app</th><td>${esc(s.profile.role)} (stored in the app, not sent by the bank)</td></tr>
    <tr><th>Token arrived by</th><td>${esc(s.via)}</td></tr>
    <tr><th>App session</th><td>started ${esc(s.at)}, lasts ${SESSION_MINUTES} minutes</td></tr></table></div>
    <h2>What was inside the OpenToken</h2><div class="card"><table>${rows}</table></div>`));
}

function log(req, res) {
  const rows = attempts.map((a) => `<tr><td>${esc(a.at)}</td><td><code>${esc(a.orgId)}</code></td><td class="${a.ok ? 'ok' : 'bad'}">${a.ok ? 'accepted' : 'rejected'}</td><td>${esc(a.subject)}</td><td>${esc(a.detail)}</td></tr>`).join('');
  send(res, 200, page('SSO attempt log', `<h2>Last ${attempts.length} SSO attempts</h2><div class="card"><table>
    <tr><th>Time (UTC)</th><th>Org</th><th>Result</th><th>Subject</th><th>Detail</th></tr>${rows || '<tr><td colspan="5">None yet.</td></tr>'}</table></div>
    <p class="lab">Lab aid. A real app would send these to its security log, not show them on a page.</p>`));
}

// ---------------------------------------------------------------- router
const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, PUBLIC_ORIGIN);
    const launch = /^\/sso\/launch\/([A-Za-z0-9._-]{1,64})\/?$/.exec(url.pathname);
    if (launch && (req.method === 'GET' || req.method === 'POST')) return await ssoLanding(req, res, url, launch[1]);
    if (req.method !== 'GET') return send(res, 405, page('Not allowed', '<div class="card">Method not allowed.</div>'));
    if (url.pathname === '/api/health') { res.writeHead(200, { 'Content-Type': 'application/json' }); return res.end(JSON.stringify({ status: 'ok', openTokenConfigured: Boolean(OPENTOKEN_PASSWORD) })); }
    if (url.pathname === '/') return home(req, res);
    if (url.pathname === '/sso/log') return log(req, res);
    if (url.pathname === '/logout') {
      const m = /(?:^|;\s*)cc_session=([a-f0-9]{64})/.exec(req.headers.cookie || '');
      if (m) sessions.delete(m[1]);
      res.writeHead(303, { Location: '/', 'Set-Cookie': cookieHeader('', 0) });
      return res.end();
    }
    send(res, 404, page('Not found', '<div class="card">Nothing here.</div>'));
  } catch (e) {
    console.error(e);
    send(res, 500, page('Error', '<div class="card">Something went wrong.</div>'));
  }
});

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Commerce Control listening on ${PORT} (public origin ${PUBLIC_ORIGIN})`);
    if (!OPENTOKEN_PASSWORD) console.log('WARNING: OPENTOKEN_PASSWORD is not set, so every token will be rejected.');
  });
}
module.exports = { server };
