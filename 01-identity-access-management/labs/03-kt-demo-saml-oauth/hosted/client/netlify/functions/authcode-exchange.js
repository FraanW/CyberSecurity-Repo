// Auth Code WITHOUT PKCE — confidential client exchanges the code with its secret, server-side.
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
  const body = new URLSearchParams(event.body || '');
  const r = await postForm(`${OIDC}/token`, {
    grant_type: 'authorization_code',
    code: body.get('code'),
    redirect_uri: body.get('redirect_uri'),
    client_id: 'kt-web',
    client_secret: 'kt-web-secret'
  });
  return json(r.status, r.json);
};
