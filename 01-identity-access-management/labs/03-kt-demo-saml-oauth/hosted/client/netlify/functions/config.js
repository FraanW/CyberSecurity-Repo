// Injects frontend config from the KEYCLOAK_URL env var (set in Netlify site settings).
exports.handler = async () => {
  const KEYCLOAK_URL = (process.env.KEYCLOAK_URL || '').replace(/\/+$/, '');
  const REALM = process.env.REALM || 'KT-idp';
  const OIDC = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect`;
  return {
    statusCode: 200,
    headers: { 'Content-Type': 'application/javascript' },
    body: `window.APP_CONFIG=${JSON.stringify({ KEYCLOAK_URL, REALM, OIDC })};`
  };
};
