/* The whole frontend: fetch three JSON endpoints, render three tables. No framework, no build. */

const $ = (id) => document.getElementById(id);
const clipboard = { cert: '', xml: '', env: '' };

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
    ['Configuration source', idp.configuredFromDetail],
    ['Applied at', idp.configuredAt],
  ]);

  badge($('trustBadge'),
    idp.trustAnchorIsPlaceholder ? 'no real IdP' : `trusting ${(idp.trustedSigningCertificates || []).length} certificate(s)`,
    idp.trustAnchorIsPlaceholder ? 'bad' : 'ok');

  renderCertificates($('trustedCerts'), idp.trustedSigningCertificates, null,
    idp.trustAnchorIsPlaceholder
      ? 'None — the certificate below is a placeholder this app generated for itself so it could boot. Nobody holds its private key, so every assertion will fail verification until you import the real one.'
      : null);

  renderList($('trustWarnings'), config.warnings);

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




/* ============================================================================
 * IdP metadata import, and the diagnosis of a rejected assertion.
 *
 * Everything below builds DOM nodes with textContent rather than innerHTML.
 * The values are certificate subjects and entity IDs that came out of a file
 * somebody uploaded — treating them as markup would turn a metadata import
 * into stored XSS on the one page an operator is guaranteed to visit.
 * ========================================================================== */

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

function renderList(target, items) {
  target.innerHTML = '';
  if (!items || !items.length) {
    target.classList.add('hidden');
    return;
  }
  items.forEach((item) => target.appendChild(el('li', null, item)));
  target.classList.remove('hidden');
}

/**
 * One certificate card. `trustedFingerprints` (when given) turns the left border
 * green or red — the whole point being that you can see a key mismatch without
 * reading a single character of hex.
 */
function certificatesBlock(certificates, trustedFingerprints, emptyNote) {
  const target = el('div');
  if (!certificates || !certificates.length) {
    target.appendChild(el('p', 'muted', emptyNote || 'None.'));
    return target;
  }
  certificates.forEach((certificate) => {
    let state = '';
    if (trustedFingerprints) {
      state = trustedFingerprints.has(certificate.sha256Fingerprint) ? ' match' : ' mismatch';
    }
    const card = el('div', `cert${state}`);
    const label = el('div', 'fp');
    label.appendChild(el('strong', null, 'SHA-256  '));
    label.appendChild(document.createTextNode(certificate.sha256Fingerprint));
    card.appendChild(label);

    const bits = [
      `subject ${certificate.subject}`,
      `issuer ${certificate.issuer}`,
      `${certificate.keyAlgorithm}${certificate.keySizeBits ? ' ' + certificate.keySizeBits + '-bit' : ''}`,
      `valid ${certificate.notBefore} → ${certificate.notAfter}`,
    ];
    if (certificate.expired) bits.push('⚠️ EXPIRED');
    if (certificate.notYetValid) bits.push('⚠️ NOT YET VALID');
    if (certificate.selfSigned) bits.push('self-signed');
    if (trustedFingerprints) bits.push(state === ' match' ? '✓ we trust this key' : '✗ we do NOT trust this key');
    card.appendChild(el('div', 'meta', bits.join('  ·  ')));
    target.appendChild(card);
  });
  return target;
}

/** Replaces a container's contents with a certificate block. */
function renderCertificates(target, certificates, trustedFingerprints, emptyNote) {
  target.innerHTML = '';
  target.appendChild(certificatesBlock(certificates, trustedFingerprints, emptyNote));
}

/* ---------------------------------------------------------------- the failure banner */

async function loadFailure() {
  const result = await getJson('/api/saml/last-failure');
  const banner = $('failureBanner');
  if (!result.hasFailure) {
    banner.classList.add('hidden');
    return;
  }
  const failure = result.failure;
  $('failureTitle').textContent = `SAML login rejected — ${failure.errorCode}`;
  $('failureText').textContent = failure.headline || failure.description || '';
  $('failureHint').textContent = failure.verdict === 'KEY_MISMATCH'
    ? 'Import the IdP metadata in Step 2 and the fingerprints line up by construction.'
    : 'The full diagnosis includes the assertion, so it needs a session — log in at Step 0 first.';
  banner.classList.remove('hidden');
}

$('dismissFailureBtn').addEventListener('click', async () => {
  await fetch('/api/saml/last-failure', {
    method: 'DELETE',
    credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': csrfToken() },
  });
  $('failureBanner').classList.add('hidden');
  $('diagnosisCard').classList.add('hidden');
});

$('diagnoseBtn').addEventListener('click', loadDiagnosis);

async function loadDiagnosis() {
  const card = $('diagnosisCard');
  const body = $('diagnosisBody');
  card.classList.remove('hidden');
  card.scrollIntoView({ behavior: 'smooth', block: 'start' });
  body.innerHTML = '';
  body.appendChild(el('p', 'muted', 'loading…'));

  const detail = await getJson('/api/saml/last-failure/detail');
  body.innerHTML = '';

  if (detail.__unauthenticated) {
    body.appendChild(el('p', 'hint',
      'The diagnosis contains the rejected assertion — somebody’s name, email and groups — so it '
      + 'needs a session. Log in with the app’s own username and password in Step 0. That login '
      + 'does not depend on the IdP, which is exactly why it is still there when SSO is broken.'));
    return;
  }
  if (!detail.hasFailure) {
    body.appendChild(el('p', 'muted', detail.note || 'Nothing to diagnose.'));
    return;
  }
  renderDiagnosis(body, detail.diagnosis, detail);
}

const VERDICT_STYLE = {
  KEY_MISMATCH: ['bad', 'Wrong key — the IdP signed with a certificate we do not trust'],
  KEY_MATCHES: ['warn', 'Right key, altered bytes'],
  NOT_SIGNED: ['bad', 'Nothing in this response is signed'],
  NO_KEY_IN_RESPONSE: ['warn', 'The IdP did not include its certificate'],
  NOTHING_TRUSTED: ['bad', 'This app trusts no IdP certificate at all'],
  UNREADABLE: ['bad', 'The response would not parse'],
};

function renderDiagnosis(target, diagnosis, detail) {
  if (!diagnosis) {
    target.appendChild(el('p', 'muted',
      'No diagnosis available — the failure happened before a SAML response reached this app.'));
    if (detail && detail.description) target.appendChild(el('p', 'hint', detail.description));
    return;
  }

  const [tone, title] = VERDICT_STYLE[diagnosis.verdict] || ['warn', diagnosis.verdict];
  const verdict = el('div', `verdict ${tone}`);
  verdict.appendChild(el('div', 'title', title));
  verdict.appendChild(el('div', null, diagnosis.summary));
  target.appendChild(verdict);

  if (detail && detail.errorCode) {
    target.appendChild(el('p', 'hint',
      `Spring Security reported: ${detail.errorCode} — ${detail.description || ''}`));
  }

  if (diagnosis.nextSteps && diagnosis.nextSteps.length) {
    target.appendChild(el('h2', null, 'What to do'));
    const list = el('ul', 'steps');
    diagnosis.nextSteps.forEach((step) => list.appendChild(el('li', null, step)));
    target.appendChild(list);
  }

  const trusted = new Set((diagnosis.certificatesWeTrust || []).map((c) => c.sha256Fingerprint));

  target.appendChild(el('h2', null, 'The key the IdP signed with'));
  target.appendChild(el('p', 'hint',
    'Taken from the response’s own <ds:KeyInfo>. Informational only — the actual verification is '
    + 'done against the certificates below, never against this one. Anyone can put any certificate '
    + 'in KeyInfo; that is why it is a clue, not a credential.'));
  target.appendChild(certificatesBlock(diagnosis.certificatesInResponse, trusted,
    'The response carried no certificate, so there is nothing to compare automatically.'));

  target.appendChild(el('h2', null, 'The keys this app trusts'));
  target.appendChild(certificatesBlock(diagnosis.certificatesWeTrust, null,
    'None. Nothing could ever verify.'));

  if (diagnosis.signatures && diagnosis.signatures.length) {
    target.appendChild(el('h2', null, 'The signatures themselves'));
    const table = el('table');
    const tbody = el('tbody');
    table.appendChild(tbody);
    target.appendChild(table);
    rows(table, diagnosis.signatures.flatMap((signature, index) => [
      [`#${index + 1} signs`, `<${signature.overElement}>`],
      [`#${index + 1} signature algorithm`, signature.signatureAlgorithm],
      [`#${index + 1} digest algorithm`, signature.digestAlgorithm],
      [`#${index + 1} canonicalization`, signature.canonicalizationMethod],
      [`#${index + 1} includes certificate`, signature.includesCertificate],
    ]));
  }

  if (diagnosis.otherChecks && diagnosis.otherChecks.length) {
    target.appendChild(el('h2', null, 'The other things that reject an assertion'));
    target.appendChild(el('p', 'hint',
      'Checked at the same time, so you find out about the next failure before you go round again.'));
    const list = el('ul', 'steps');
    diagnosis.otherChecks.forEach((check) => list.appendChild(el('li', null, check)));
    target.appendChild(list);
  }

  if (detail && detail.rawResponseXml) {
    target.appendChild(el('h2', null, 'The rejected response, raw'));
    target.appendChild(el('p', 'hint',
      'The same bytes SAML-tracer would show you. Treat it as sensitive — it is somebody’s identity.'));
    const pre = el('pre', null, detail.rawResponseXml);
    target.appendChild(pre);
  }
}

/* ---------------------------------------------------------------- metadata import */

let pendingMetadata = null;   // { kind: 'file'|'xml'|'url', value }
let previewedEntityId = null;
let activeTab = 'file';

function setTab(name) {
  activeTab = name;
  ['file', 'paste', 'url'].forEach((tab) => {
    $(`tab-${tab}`).classList.toggle('hidden', tab !== name);
  });
  document.querySelectorAll('.tabs button').forEach((button) => {
    button.classList.toggle('active', button.dataset.tab === name);
  });
}

document.querySelectorAll('.tabs button').forEach((button) => {
  button.addEventListener('click', () => setTab(button.dataset.tab));
});

$('dropZone').addEventListener('click', () => $('fileInput').click());
$('dropZone').addEventListener('dragover', (event) => {
  event.preventDefault();
  $('dropZone').classList.add('over');
});
$('dropZone').addEventListener('dragleave', () => $('dropZone').classList.remove('over'));
$('dropZone').addEventListener('drop', (event) => {
  event.preventDefault();
  $('dropZone').classList.remove('over');
  if (event.dataTransfer.files.length) {
    selectFile(event.dataTransfer.files[0]);
  }
});
$('fileInput').addEventListener('change', (event) => {
  if (event.target.files.length) selectFile(event.target.files[0]);
});

function selectFile(file) {
  pendingMetadata = { kind: 'file', value: file };
  $('dropZone').innerHTML = '';
  $('dropZone').appendChild(el('strong', null, file.name));
  $('dropZone').appendChild(document.createTextNode(`${Math.max(1, Math.round(file.size / 1024))} KB — click Preview`));
}

/** Builds the multipart body for whichever tab is active. */
function importBody() {
  const form = new FormData();
  if (activeTab === 'file') {
    if (!pendingMetadata || pendingMetadata.kind !== 'file') {
      throw new Error('Choose a metadata file first.');
    }
    form.append('file', pendingMetadata.value);
  } else if (activeTab === 'paste') {
    const xml = $('xmlInput').value.trim();
    if (!xml) throw new Error('Paste the metadata XML first.');
    form.append('xml', xml);
  } else {
    const url = $('urlInput').value.trim();
    if (!url) throw new Error('Enter the metadata URL first.');
    form.append('url', url);
  }
  return form;
}

async function postForm(url, form) {
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': csrfToken() },
    body: form,
  });
  if (response.status === 401) {
    throw new Error('Log in first (Step 0). Changing which IdP this app trusts is an admin action — '
      + 'an anonymous caller who could do it could point this SP at an IdP they control.');
  }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body.message || `HTTP ${response.status}`);
  }
  return body;
}

function busy(on) {
  $('importSpinner').classList.toggle('hidden', !on);
  $('previewBtn').disabled = on;
  $('applyBtn').disabled = on;
  $('resetImportBtn').disabled = on;
}

$('previewBtn').addEventListener('click', async () => {
  $('importError').textContent = '';
  try {
    busy(true);
    const result = await postForm('/api/idp/metadata/preview', importBody());
    renderPreview(result);
  } catch (error) {
    $('importError').textContent = error.message;
    $('previewBlock').classList.add('hidden');
    $('applyBtn').classList.add('hidden');
  } finally {
    busy(false);
  }
});

function renderPreview(result) {
  const target = $('previewBody');
  target.innerHTML = '';
  previewedEntityId = result.count === 1 ? result.identityProviders[0].entityId : null;

  result.identityProviders.forEach((idp) => {
    const table = el('table');
    table.appendChild(el('tbody'));
    target.appendChild(el('h2', null, idp.entityId));
    target.appendChild(table);
    rows(table, [
      ['SSO URL', idp.singleSignOnServiceLocation],
      ['SSO binding', idp.singleSignOnServiceBinding],
      ['Single Logout URL', idp.singleLogoutServiceLocation],
      ['SLO binding', idp.singleLogoutServiceBinding],
      ['Wants signed AuthnRequests', idp.wantAuthnRequestsSigned],
      ['Metadata document is XML-signed', idp.documentSigned],
      ['Metadata valid until', idp.validUntil],
    ]);
    target.appendChild(el('h2', null, 'Signing certificates in this document'));
    target.appendChild(certificatesBlock(idp.signingCertificates, null));
    if (idp.encryptionCertificates && idp.encryptionCertificates.length) {
      target.appendChild(el('h2', null, 'Encryption certificates'));
      target.appendChild(certificatesBlock(idp.encryptionCertificates, null));
    }
    const warnings = el('ul', 'warnings');
    renderList(warnings, idp.warnings);
    target.appendChild(warnings);
  });

  if (result.count > 1) {
    target.appendChild(el('p', 'hint',
      `This document describes ${result.count} IdPs. Importing needs one entity ID — paste the one `
      + 'you want into the box below.'));
    const picker = el('input');
    picker.type = 'text';
    picker.id = 'entityIdPicker';
    picker.placeholder = 'entity ID to import';
    target.appendChild(picker);
  }

  $('previewBlock').classList.remove('hidden');
  $('applyBtn').classList.remove('hidden');
}

$('applyBtn').addEventListener('click', async () => {
  $('importError').textContent = '';
  try {
    busy(true);
    const form = importBody();
    const picker = document.getElementById('entityIdPicker');
    const entityId = picker ? picker.value.trim() : previewedEntityId;
    if (entityId) form.append('entityId', entityId);
    const result = await postForm('/api/idp', form);
    showEnvironment(result.environmentVariables);
    $('previewBlock').classList.add('hidden');
    $('applyBtn').classList.add('hidden');
    await refresh();
  } catch (error) {
    $('importError').textContent = error.message;
  } finally {
    busy(false);
  }
});

$('resetImportBtn').addEventListener('click', async () => {
  $('importError').textContent = '';
  try {
    busy(true);
    const response = await fetch('/api/idp/metadata', {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: { 'X-XSRF-TOKEN': csrfToken() },
    });
    if (response.status === 401) throw new Error('Log in first (Step 0).');
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    $('envBlock').classList.add('hidden');
    $('previewBlock').classList.add('hidden');
    await refresh();
  } catch (error) {
    $('importError').textContent = error.message;
  } finally {
    busy(false);
  }
});

function showEnvironment(variables) {
  if (!variables || !Object.keys(variables).length) return;
  const text = Object.entries(variables)
    .map(([key, value]) => `${key}=${value}`)
    .join('\n');
  clipboard.env = text;
  $('envPem').textContent = text;
  $('envBlock').classList.remove('hidden');
}

/* ---------------------------------------------------------------- bootstrap */

async function refresh() {
  await loadConfig();
  await loadSession();
  await loadFailure();
}

(async function start() {
  const params = new URLSearchParams(window.location.search);
  if (params.has('error')) {
    $('localError').textContent = 'Wrong username or password.';
  }
  try {
    await refresh();
    // Arriving straight from a rejected assertion? Open the diagnosis without being asked.
    if (params.has('samlError')) {
      await loadDiagnosis();
    }
  } catch (error) {
    $('flowError').textContent = error.message;
    badge($('statusBadge'), 'error', 'bad');
  }
})();
