// Reverse-KT demo client (Node, zero dependencies).
// Serves the landing page + OAuth flow pages, and runs the two grants that
// cannot happen in a browser (Client Credentials = needs a secret; Device Code
// = CORS) server-side against the hosted Keycloak. Auth Code + PKCE, Implicit,
// and Refresh run in the browser directly against Keycloak.
//
// Required env: KEYCLOAK_URL = https://<your-keycloak>.onrender.com
// Optional env: REALM (default finco-idp)

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;
const KEYCLOAK_URL = (process.env.KEYCLOAK_URL || '').replace(/\/+$/, '');
const REALM = process.env.REALM || 'finco-idp';
const OIDC = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect`;

// Demo client credentials (LAB ONLY — these live in the imported realm).
const SERVICE_CLIENT = 'kt-service',   SERVICE_SECRET = 'kt-service-secret';   // client credentials
const INTROSPECT_CLIENT = 'kt-web',    INTROSPECT_SECRET = 'kt-web-secret';     // resource-server introspection
const DEVICE_CLIENT = 'kt-device';                                              // device grant

const PUBLIC = path.join(__dirname, 'public');
const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.json': 'application/json' };

function send(res, code, body, type = 'application/json') {
  res.writeHead(code, { 'Content-Type': type });
  if (Buffer.isBuffer(body)) return res.end(body);
  res.end(typeof body === 'string' ? body : JSON.stringify(body));
}
function readBody(req) { return new Promise(r => { let d = ''; req.on('data', c => d += c); req.on('end', () => r(d)); }); }
async function postForm(url, params) {
  const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(params) });
  const text = await r.text();
  let json; try { json = JSON.parse(text); } catch { json = { raw: text }; }
  return { status: r.status, json };
}

const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://localhost');
  const p = u.pathname;
  try {
    // Frontend config (injected, so the client URL/realm aren't hardcoded)
    if (p === '/config.js') {
      return send(res, 200, `window.APP_CONFIG=${JSON.stringify({ KEYCLOAK_URL, REALM, OIDC })};`, 'application/javascript');
    }
    // Demo C — Client Credentials (server-side; the secret never reaches the browser)
    if (p === '/api/client-credentials' && req.method === 'POST') {
      if (!KEYCLOAK_URL) return send(res, 500, { error: 'KEYCLOAK_URL not configured on the service' });
      const r = await postForm(`${OIDC}/token`, { grant_type: 'client_credentials', client_id: SERVICE_CLIENT, client_secret: SERVICE_SECRET });
      return send(res, r.status, r.json);
    }
    // Demo D — Device Authorization (server-side proxy: start + poll)
    if (p === '/api/device/start' && req.method === 'POST') {
      const r = await postForm(`${OIDC}/auth/device`, { client_id: DEVICE_CLIENT, scope: 'openid profile' });
      return send(res, r.status, r.json);
    }
    if (p === '/api/device/poll' && req.method === 'POST') {
      const body = new URLSearchParams(await readBody(req));
      const r = await postForm(`${OIDC}/token`, { grant_type: 'urn:ietf:params:oauth:grant-type:device_code', device_code: body.get('device_code'), client_id: DEVICE_CLIENT });
      return send(res, r.status, r.json);
    }
    // A real Resource Server endpoint: validates the presented access token via introspection
    if (p === '/api/resource' && req.method === 'GET') {
      const token = (req.headers['authorization'] || '').replace(/^Bearer\s+/i, '');
      if (!token) return send(res, 401, { ok: false, message: 'No bearer token presented.' });
      const r = await postForm(`${OIDC}/token/introspect`, { client_id: INTROSPECT_CLIENT, client_secret: INTROSPECT_SECRET, token });
      if (r.json && r.json.active) return send(res, 200, { ok: true, message: 'Resource Server: token is valid — access granted (HTTP 200).', token_info: r.json });
      return send(res, 401, { ok: false, message: 'Resource Server: token invalid or expired (HTTP 401).', token_info: r.json });
    }
    // Static files
    let file = p === '/' ? '/index.html' : p;
    const full = path.join(PUBLIC, path.normalize(file).replace(/^(\.\.[/\\])+/, ''));
    if (full.startsWith(PUBLIC) && fs.existsSync(full) && fs.statSync(full).isFile()) {
      return send(res, 200, fs.readFileSync(full), TYPES[path.extname(full)] || 'text/plain');
    }
    send(res, 404, 'Not found', 'text/plain');
  } catch (e) {
    send(res, 500, { error: String(e) });
  }
});
server.listen(PORT, () => console.log(`KT client on :${PORT} · KEYCLOAK_URL=${KEYCLOAK_URL || '(NOT SET!)'}`));
