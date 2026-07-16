// A real Resource Server: validates the presented access token via Keycloak introspection.
function oidc() {
  const k = (process.env.KEYCLOAK_URL || '').replace(/\/+$/, '');
  return k ? `${k}/realms/${process.env.REALM || 'KT-idp'}/protocol/openid-connect` : null;
}
function json(status, obj) { return { statusCode: status, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(obj) }; }
async function postForm(url, params) {
  const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(params) });
  const t = await r.text(); let j; try { j = JSON.parse(t); } catch { j = { raw: t }; }
  return { status: r.status, json: j };
}
exports.handler = async (event) => {
  const OIDC = oidc();
  if (!OIDC) return json(500, { error: 'KEYCLOAK_URL not set on the Netlify site' });
  const auth = event.headers.authorization || event.headers.Authorization || '';
  const token = auth.replace(/^Bearer\s+/i, '');
  if (!token) return json(401, { ok: false, message: 'No bearer token presented.' });
  const r = await postForm(`${OIDC}/token/introspect`, { client_id: 'kt-web', client_secret: 'kt-web-secret', token });
  if (r.json && r.json.active) return json(200, { ok: true, message: 'Resource Server: token is valid — access granted (HTTP 200).', token_info: r.json });
  return json(401, { ok: false, message: 'Resource Server: token invalid or expired (HTTP 401).', token_info: r.json });
};
