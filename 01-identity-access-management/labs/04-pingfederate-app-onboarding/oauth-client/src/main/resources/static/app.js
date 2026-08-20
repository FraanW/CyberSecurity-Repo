/* The whole frontend: fetch JSON, render tables, POST to the grant endpoints. No build step. */

const $ = (id) => document.getElementById(id);

function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : '';
}

async function getJson(url) {
  const response = await fetch(url, { credentials: 'same-origin' });
  if (response.status === 401) return { __unauthenticated: true };
  return response.json();
}

async function postJson(url) {
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': csrfToken() },
  });
  if (response.status === 401) return { error: 'not_logged_in', error_description: 'Log in first.' };
  return response.json();
}

function rows(target, entries) {
  const body = target.querySelector('tbody');
  body.innerHTML = '';
  for (const [label, value] of entries) {
    if (value === undefined || value === null || value === '') continue;
    const tr = document.createElement('tr');
    const th = document.createElement('th');
    th.textContent = label;
    const td = document.createElement('td');
    const span = document.createElement('span');
    span.className = 'val';
    span.textContent = typeof value === 'object' ? JSON.stringify(value) : String(value);
    td.appendChild(span);
    tr.append(th, td);
    body.appendChild(tr);
  }
  if (!body.children.length) {
    body.innerHTML = '<tr><td class="muted">nothing to show yet</td></tr>';
  }
}

function badge(element, text, kind) {
  element.textContent = text;
  element.className = `badge ${kind}`;
}

function show(result) {
  $('output').textContent = JSON.stringify(result, null, 2);
}

async function loadConfig() {
  const config = await getJson('/api/config');
  const client = config.client;
  const endpoints = config.endpoints;

  badge($('statusBadge'),
    config.configured ? 'client configured' : 'client not configured',
    config.configured ? 'ok' : 'warn');

  rows($('clientTable'), [
    ['Client ID', client.clientId],
    ['Client secret set', client.clientSecretConfigured],
    ['Client authentication', client.clientAuthenticationMethod],
    ['Redirect URI (whitelist this)', client.redirectUri],
    ['Scopes requested', (client.scopes || []).join(' ')],
    ['PKCE', client.pkce ? 'on (S256)' : 'off'],
    ['Start-login URL', client.loginUrl],
    ['Machine-to-machine client', config.machineClient.clientId
      + (config.machineClient.sharesTheInteractiveClient ? '  (same client — set LAB_OAUTH_MACHINE_CLIENT_ID to split them)' : '')],
  ]);

  rows($('endpointTable'), [
    ['Issuer', endpoints.issuer],
    ['Authorization endpoint', endpoints.authorizationEndpoint],
    ['Token endpoint', endpoints.tokenEndpoint],
    ['JWKS URI', endpoints.jwksUri],
    ['UserInfo endpoint', endpoints.userInfoEndpoint],
    ['Introspection endpoint', endpoints.introspectionEndpoint],
    ['End-session endpoint', endpoints.endSessionEndpoint],
    ['Resolved via', endpoints.resolvedVia],
  ]);

  $('loginBtn').href = `/oauth2/authorization/${config.registrationId}`;

  if (config.missing && config.missing.length) {
    $('missingList').innerHTML = config.missing.map((m) => `<li>${m}</li>`).join('');
    $('missingBlock').classList.remove('hidden');
    $('loginBtn').classList.add('hidden');
    $('loginDisabledHint').textContent =
      'PingFederate login is switched off until a client is configured. Use the local login in Step 0 in the meantime.';
  }
}

async function loadSession() {
  const who = await getJson('/api/whoami');
  if (who.__unauthenticated) {
    badge($('statusBadge'), 'not logged in', 'warn');
    return;
  }

  const viaOidc = who.loginType === 'oidc';

  $('loggedOut').classList.add('hidden');
  $('loggedIn').classList.remove('hidden');
  $('localCard').classList.add('hidden');
  $('sessionTitle').textContent = viaOidc
    ? `Logged in as ${who.name || who.email || who.subject} — via PingFederate (OIDC)`
    : `Logged in as ${who.subject} — via this app's own password`;
  badge($('statusBadge'), viaOidc ? 'OIDC session active' : 'local session active', 'ok');

  rows($('whoTable'), [
    ['Login type', viaOidc ? 'OpenID Connect ID token from the authorization server' : 'local username + password'],
    ['Subject (sub)', who.subject],
    ['Name', who.name],
    ['Email', who.email],
    ['Issuer', who.issuer],
    ['Audience', (who.audience || []).join(', ')],
    ['ID token issued at', who.issuedAt],
    ['ID token expires at', who.expiresAt],
    ['Note', who.note],
  ]);

  // There are no tokens behind a local login — only the OIDC path has them.
  if (!viaOidc) {
    $('tokenCard').classList.add('hidden');
    return;
  }
  $('tokenCard').classList.remove('hidden');

  const tokens = await getJson('/api/tokens');
  if (tokens.error) {
    show(tokens);
    return;
  }

  const at = tokens.accessToken;
  $('atNote').innerHTML = at.format === 'jwt'
    ? 'This client\'s <strong>Access Token Manager</strong> mints <strong>JWTs</strong> — self-contained and readable. An API validates it offline against the JWKS.'
    : 'This client\'s <strong>Access Token Manager</strong> mints <strong>reference tokens</strong> — opaque strings that mean nothing on their own. An API must call introspection (Step 5) to validate them. This is PingFederate\'s default and it surprises everyone once.';

  rows($('atTable'), [
    ['Format', at.format],
    ['Token type', at.type],
    ['Scopes granted', (at.scopes || []).join(' ')],
    ['Issued at', at.issuedAt],
    ['Expires at', at.expiresAt],
    ['Seconds remaining', at.secondsRemaining],
    ['Value', at.value || at.preview],
  ]);
  if (at.payload) {
    $('atPayload').textContent = JSON.stringify({ header: at.header, payload: at.payload }, null, 2);
    $('atPayload').classList.remove('hidden');
  } else {
    $('atPayload').classList.add('hidden');
  }

  const it = tokens.idToken;
  rows($('itTable'), [
    ['Format', it.format],
    ['Expires at', it.expiresAt],
    ['Signature present', it.signaturePresent],
    ['Value', it.value || it.preview],
  ]);
  $('itPayload').textContent = JSON.stringify({ header: it.header, payload: it.payload }, null, 2);

  const rt = tokens.refreshToken;
  rows($('rtTable'), [
    ['Present', rt.present],
    ['Issued at', rt.issuedAt],
    ['Expires at', rt.expiresAt],
    ['Value', rt.value || rt.preview],
    ['Note', rt.note],
  ]);
}

$('ccBtn').addEventListener('click', async () => show(await postJson('/api/client-credentials')));
$('introspectBtn').addEventListener('click', async () => show(await postJson('/api/introspect')));
$('userinfoBtn').addEventListener('click', async () => show(await getJson('/api/userinfo')));
$('refreshBtn').addEventListener('click', async () => {
  show(await postJson('/api/refresh'));
  // Re-read the tokens so you can watch whether the refresh token rotated.
  await loadSession();
});

/* The local login form posts a normal HTML form so Spring Security's filter can handle it. */
$('localForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const body = new URLSearchParams({
    username: $('username').value,
    password: $('password').value,
    _csrf: csrfToken(),
  });
  const response = await fetch('/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
    redirect: 'follow',
  });
  if (response.url.includes('error')) {
    $('localError').textContent = 'Wrong username or password.';
    return;
  }
  window.location.href = '/';
});

$('logoutForm').addEventListener('submit', (event) => {
  const field = document.createElement('input');
  field.type = 'hidden';
  field.name = '_csrf';
  field.value = csrfToken();
  event.currentTarget.appendChild(field);
});

(async function start() {
  if (new URLSearchParams(window.location.search).has('error')) {
    $('localError').textContent = 'Wrong username or password.';
  }
  try {
    await loadConfig();
    await loadSession();
  } catch (error) {
    show({ error: error.message });
    badge($('statusBadge'), 'error', 'bad');
  }
})();
