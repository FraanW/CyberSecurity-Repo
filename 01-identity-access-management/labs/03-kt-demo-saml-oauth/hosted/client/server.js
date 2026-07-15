// Reverse-KT demo client (Node).
//  - Serves the landing page + OAuth flow pages.
//  - Runs the two OAuth grants that can't happen in a browser (Client
//    Credentials = needs a secret; Device Code = CORS) server-side.
//  - Acts as a real SAML Service Provider (SP-initiated + IdP-initiated) using
//    @node-saml/node-saml, so the SAML section shows genuine federation + SSO.
//
// Required env: KEYCLOAK_URL = https://<your-keycloak>.onrender.com
// Optional env: REALM (default finco-idp)
//               APP_ORIGIN (this app's public https URL; Render sets
//               RENDER_EXTERNAL_URL automatically, used as the fallback).

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;
const KEYCLOAK_URL = (process.env.KEYCLOAK_URL || '').replace(/\/+$/, '');
const REALM = process.env.REALM || 'finco-idp';
const OIDC = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect`;
const SAML_IDP = `${KEYCLOAK_URL}/realms/${REALM}/protocol/saml`;
const APP_ORIGIN = (process.env.APP_ORIGIN || process.env.RENDER_EXTERNAL_URL || '').replace(/\/+$/, '');

// Demo client credentials (LAB ONLY — these live in the imported realm).
const SERVICE_CLIENT = 'kt-service', SERVICE_SECRET = 'kt-service-secret';
const INTROSPECT_CLIENT = 'kt-web',  INTROSPECT_SECRET = 'kt-web-secret';
const DEVICE_CLIENT = 'kt-device';
const SAML_SP_ENTITY = 'kt-saml-app';                 // SAML SP entityId (matches the realm client)

let SAML = null;
try { SAML = require('@node-saml/node-saml').SAML; } catch (e) { console.warn('node-saml not installed; SAML routes disabled:', e.message); }

const PUBLIC = path.join(__dirname, 'public');
const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.json': 'application/json' };

function send(res, code, body, type = 'application/json', extraHeaders = {}) {
  res.writeHead(code, { 'Content-Type': type, ...extraHeaders });
  if (Buffer.isBuffer(body)) return res.end(body);
  res.end(typeof body === 'string' ? body : JSON.stringify(body));
}
function redirect(res, location, extraHeaders = {}) { res.writeHead(302, { Location: location, ...extraHeaders }); res.end(); }
function readBody(req) { return new Promise(r => { let d = ''; req.on('data', c => d += c); req.on('end', () => r(d)); }); }
function cookies(req) { const h = req.headers.cookie || ''; const o = {}; h.split(';').forEach(c => { const i = c.indexOf('='); if (i > 0) o[c.slice(0, i).trim()] = decodeURIComponent(c.slice(i + 1).trim()); }); return o; }
async function postForm(url, params) {
  const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(params) });
  const t = await r.text(); let j; try { j = JSON.parse(t); } catch { j = { raw: t }; }
  return { status: r.status, json: j };
}

// --- SAML: fetch the IdP signing cert from its metadata (lazy + cached) -------
let _idpCert = null;
async function idpCert() {
  if (_idpCert) return _idpCert;
  const res = await fetch(`${SAML_IDP}/descriptor`);
  if (!res.ok) throw new Error(`IdP metadata fetch failed: HTTP ${res.status}`);
  const xml = await res.text();
  const m = xml.match(/<[^>]*X509Certificate>([\s\S]*?)<\/[^>]*X509Certificate>/);
  if (!m) throw new Error('No X509Certificate in IdP metadata');
  const b64 = m[1].replace(/\s+/g, '');
  _idpCert = `-----BEGIN CERTIFICATE-----\n${b64.match(/.{1,64}/g).join('\n')}\n-----END CERTIFICATE-----`;
  return _idpCert;
}
async function samlSP() {
  return new SAML({
    callbackUrl: `${APP_ORIGIN}/saml/acs`,
    entryPoint: `${SAML_IDP}`,
    issuer: SAML_SP_ENTITY,
    idpCert: await idpCert(),
    wantAssertionsSigned: true,
    wantAuthnResponseSigned: false,     // Keycloak signs the assertion; be lenient on the envelope
    validateInResponseTo: 'never',      // allow BOTH SP-init and IdP-init (unsolicited) responses
    disableRequestedAuthnContext: true,
    identifierFormat: 'urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress'
  });
}
function setSession(res, profile, location) {
  const summary = { name: profile.nameID, attributes: profile.attributes || {}, sessionIndex: profile.sessionIndex || null };
  const cookie = 'kt_saml=' + encodeURIComponent(Buffer.from(JSON.stringify(summary)).toString('base64')) + '; Path=/; HttpOnly; SameSite=Lax; Max-Age=3600';
  redirect(res, location, { 'Set-Cookie': cookie });
}
function clearSession(res, location) {
  redirect(res, location, { 'Set-Cookie': 'kt_saml=; Path=/; HttpOnly; Max-Age=0' });
}

const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://localhost');
  const p = u.pathname;
  try {
    // ---- Frontend config ----
    if (p === '/config.js') {
      return send(res, 200, `window.APP_CONFIG=${JSON.stringify({ KEYCLOAK_URL, REALM, OIDC, SAML_IDP, SAML_SP_ENTITY })};`, 'application/javascript');
    }

    // ---- OAuth: server-side grants ----
    if (p === '/api/client-credentials' && req.method === 'POST') {
      if (!KEYCLOAK_URL) return send(res, 500, { error: 'KEYCLOAK_URL not configured' });
      const r = await postForm(`${OIDC}/token`, { grant_type: 'client_credentials', client_id: SERVICE_CLIENT, client_secret: SERVICE_SECRET });
      return send(res, r.status, r.json);
    }
    if (p === '/api/device/start' && req.method === 'POST') {
      const r = await postForm(`${OIDC}/auth/device`, { client_id: DEVICE_CLIENT, scope: 'openid profile' });
      return send(res, r.status, r.json);
    }
    if (p === '/api/device/poll' && req.method === 'POST') {
      const body = new URLSearchParams(await readBody(req));
      const r = await postForm(`${OIDC}/token`, { grant_type: 'urn:ietf:params:oauth:grant-type:device_code', device_code: body.get('device_code'), client_id: DEVICE_CLIENT });
      return send(res, r.status, r.json);
    }
    if (p === '/api/resource' && req.method === 'GET') {
      const token = (req.headers['authorization'] || '').replace(/^Bearer\s+/i, '');
      if (!token) return send(res, 401, { ok: false, message: 'No bearer token presented.' });
      const r = await postForm(`${OIDC}/token/introspect`, { client_id: INTROSPECT_CLIENT, client_secret: INTROSPECT_SECRET, token });
      if (r.json && r.json.active) return send(res, 200, { ok: true, message: 'Resource Server: token is valid — access granted (HTTP 200).', token_info: r.json });
      return send(res, 401, { ok: false, message: 'Resource Server: token invalid or expired (HTTP 401).', token_info: r.json });
    }

    // ---- SAML: real Service Provider ----
    if (p === '/saml/login' && req.method === 'GET') {           // SP-initiated: build AuthnRequest -> IdP
      if (!SAML) return send(res, 500, { error: 'node-saml not installed' });
      const sp = await samlSP();
      const url = await sp.getAuthorizeUrlAsync('', null, {});
      return redirect(res, url);
    }
    if (p === '/saml/acs' && req.method === 'POST') {            // ACS: both SP-init and IdP-init land here
      if (!SAML) return send(res, 500, { error: 'node-saml not installed' });
      const sp = await samlSP();
      const body = Object.fromEntries(new URLSearchParams(await readBody(req)));
      const { profile } = await sp.validatePostResponseAsync(body);
      return setSession(res, profile, '/saml.html?loggedin=1');
    }
    if (p === '/saml/status' && req.method === 'GET') {
      const c = cookies(req).kt_saml;
      if (!c) return send(res, 200, { loggedIn: false });
      try { return send(res, 200, { loggedIn: true, ...JSON.parse(Buffer.from(decodeURIComponent(c), 'base64').toString('utf8')) }); }
      catch { return send(res, 200, { loggedIn: false }); }
    }
    if (p === '/saml/slogout') {                                 // app-only logout (IdP session stays alive -> SSO)
      return clearSession(res, '/saml.html?loggedout=app');
    }
    if (p === '/saml/logout') {                                  // full reset: clear app + end the IdP session
      return clearSession(res, `${OIDC}/logout`);
    }

    // ---- Static files ----
    let file = p === '/' ? '/index.html' : p;
    const full = path.join(PUBLIC, path.normalize(file).replace(/^(\.\.[/\\])+/, ''));
    if (full.startsWith(PUBLIC) && fs.existsSync(full) && fs.statSync(full).isFile()) {
      return send(res, 200, fs.readFileSync(full), TYPES[path.extname(full)] || 'text/plain');
    }
    send(res, 404, 'Not found', 'text/plain');
  } catch (e) {
    send(res, 500, { error: String(e && e.message || e) });
  }
});
server.listen(PORT, () => console.log(`KT client on :${PORT} · KEYCLOAK_URL=${KEYCLOAK_URL || '(NOT SET!)'} · APP_ORIGIN=${APP_ORIGIN || '(auto)'}`));
