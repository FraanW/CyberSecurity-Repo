/* The whole frontend: fetch three JSON endpoints, render three tables. No framework, no build. */

const $ = (id) => document.getElementById(id);
const clipboard = { cert: '', xml: '' };

/** Spring Security hands us a CSRF token in a cookie; POSTs must echo it back in a header. */
function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : '';
}

async function getJson(url) {
  const response = await fetch(url, { credentials: 'same-origin' });
  if (response.status === 401) return { __unauthenticated: true };
  if (!response.ok) throw new Error(`${url} → HTTP ${response.status}`);
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

async function loadConfig() {
  const config = await getJson('/api/config');
  const sp = config.serviceProvider;
  const idp = config.identityProvider;

  badge($('statusBadge'),
    config.configured ? 'IdP configured' : 'IdP not configured',
    config.configured ? 'ok' : 'warn');

  rows($('spTable'), [
    ['SP entity ID', sp.entityId],
    ['ACS URL (assertion goes here)', sp.assertionConsumerServiceUrl],
    ['ACS binding', sp.assertionConsumerServiceBinding],
    ['Single Logout URL', sp.singleLogoutServiceUrl],
    ['SP metadata URL', sp.metadataUrl],
    ['Start-SSO URL', sp.loginUrl],
  ]);

  rows($('idpTable'), [
    ['IdP entity ID', idp.entityId],
    ['SSO URL', idp.singleSignOnServiceUrl],
    ['SSO binding', idp.singleSignOnServiceBinding],
    ['Single Logout URL', idp.singleLogoutServiceUrl],
    ['Wants signed AuthnRequests', idp.wantAuthnRequestsSigned],
    ['Configured from', idp.configuredFrom],
  ]);

  if (sp.signingCertificate) {
    clipboard.cert = sp.signingCertificate;
    $('certPem').textContent = sp.signingCertificate;
    $('certHint').innerHTML = sp.signingCertificateIsEphemeral
      ? '⚠️ <strong>Throwaway key.</strong> It is regenerated on every restart, so PingFederate will stop trusting it after a redeploy. Fine for a first walkthrough; set <code>LAB_SAML_SP_PRIVATE_KEY</code> and <code>LAB_SAML_SP_CERTIFICATE</code> before you rely on it.'
      : 'Loaded from your environment variables — stable across restarts. Upload this to the SP connection\'s signature verification settings.';
    $('certBlock').classList.remove('hidden');
  }

  $('loginBtn').href = `/saml2/authenticate/${config.registrationId}`;

  if (config.missing && config.missing.length) {
    $('missingList').innerHTML = config.missing.map((m) => `<li>${m}</li>`).join('');
    $('missingBlock').classList.remove('hidden');
    $('loginBtn').classList.add('hidden');
    $('loginDisabledHint').textContent =
      'SAML login is switched off until an IdP is configured. Use the local login in Step 0 in the meantime.';
  }
}

async function loadSession() {
  const who = await getJson('/api/whoami');
  if (who.__unauthenticated) {
    badge($('statusBadge'), 'not logged in', 'warn');
    return;
  }

  const viaSaml = who.loginType === 'saml';

  $('loggedOut').classList.add('hidden');
  $('loggedIn').classList.remove('hidden');
  $('localCard').classList.add('hidden');
  $('sessionTitle').textContent = viaSaml
    ? `Logged in as ${who.nameId} — via PingFederate (SAML)`
    : `Logged in as ${who.nameId} — via this app's own password`;
  badge($('statusBadge'), viaSaml ? 'SAML session active' : 'local session active', 'ok');

  rows($('whoTable'), [
    ['Login type', viaSaml ? 'SAML 2.0 assertion from the IdP' : 'local username + password'],
    [viaSaml ? 'NameID' : 'Username', who.nameId],
    ['Session index (needed for SLO)', (who.sessionIndexes || []).join(', ')],
    ['Granted authorities', (who.authorities || []).join(', ')],
    ['Registration ID', who.relyingPartyRegistrationId],
    ['Local session created', who.localSessionCreatedAt],
    ['Note', who.note],
  ]);

  // Assertion details only exist for a SAML session.
  if (!viaSaml) {
    $('assertionCard').classList.add('hidden');
    return;
  }
  $('assertionCard').classList.remove('hidden');

  const attributes = who.attributes || {};
  rows($('attrTable'), Object.entries(attributes));

  const highlights = await getJson('/api/assertion/highlights');
  rows($('highlightTable'), [
    ['Issuer (must match IdP entity ID)', highlights.issuer],
    ['Audience (must match SP entity ID)', highlights.audience],
    ['Destination (must match ACS URL)', highlights.destination],
    ['Status code', highlights.statusCode],
    ['NameID format', highlights.nameIdFormat],
    ['Conditions NotBefore', highlights.conditionsNotBefore],
    ['Conditions NotOnOrAfter', highlights.conditionsNotOnOrAfter],
    ['Authn instant', highlights.authnInstant],
    ['Authn context', highlights.authnContextClassRef],
    ['Session index', highlights.sessionIndex],
    ['XML signatures found', highlights.signatures],
    ['Encrypted assertions', highlights.encryptedAssertions],
    ['Attribute names in assertion', (highlights.attributeNames || []).join(', ')],
  ]);

  const xml = await fetch('/api/assertion', { credentials: 'same-origin' }).then((r) => r.text());
  clipboard.xml = xml;
  $('rawXml').textContent = xml;
}

document.addEventListener('click', async (event) => {
  const key = event.target.dataset?.copy;
  if (key) {
    await navigator.clipboard.writeText(clipboard[key] || '');
    const original = event.target.textContent;
    event.target.textContent = 'copied ✓';
    setTimeout(() => { event.target.textContent = original; }, 1200);
  }
});

$('pingBtn').addEventListener('click', async () => {
  const result = await getJson('/api/public/ping');
  $('flowError').style.color = 'var(--good)';
  $('flowError').textContent = `/api/public/ping → ${JSON.stringify(result)}`;
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
  // Spring redirects to /?error=bad-credentials when the password is wrong.
  if (response.url.includes('error')) {
    $('localError').textContent = 'Wrong username or password.';
    return;
  }
  window.location.href = '/';
});

$('logoutForm').addEventListener('submit', (event) => {
  // Add the CSRF token as a hidden field so the POST survives Spring Security's check.
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
    $('flowError').textContent = error.message;
    badge($('statusBadge'), 'error', 'bad');
  }
})();
