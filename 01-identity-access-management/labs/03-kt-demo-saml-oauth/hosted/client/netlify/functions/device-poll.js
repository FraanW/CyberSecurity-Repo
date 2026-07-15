// Demo D — poll the token endpoint with the device_code (server-side proxy).
function oidc() {
  const k = (process.env.KEYCLOAK_URL || '').replace(/\/+$/, '');
  return k ? `${k}/realms/${process.env.REALM || 'finco-idp'}/protocol/openid-connect` : null;
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
    grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
    device_code: body.get('device_code'),
    client_id: 'kt-device'
  });
  return json(r.status, r.json);
};
