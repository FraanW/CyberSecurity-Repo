// Shared helpers, loaded after /config.js. No external dependencies.
const CFG = window.APP_CONFIG || {};
const OIDC = CFG.OIDC;                       // https://<kc>/realms/KT-idp/protocol/openid-connect

function b64url(bytes) {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function randB64() { const a = new Uint8Array(32); crypto.getRandomValues(a); return b64url(a); }
async function sha256b64(s) { return b64url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))); }
function decodeJwt(t) {
  try {
    const p = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.stringify(JSON.parse(decodeURIComponent(escape(atob(p)))), null, 2);
  } catch (e) { return '(could not decode)'; }
}
function $(id) { return document.getElementById(id); }
function reveal(panelId) { const e = $(panelId); if (e) e.style.display = 'block'; }
function setText(id, txt) { const e = $(id); if (e) e.textContent = txt; }
