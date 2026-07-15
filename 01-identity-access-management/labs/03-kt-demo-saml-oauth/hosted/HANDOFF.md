# 🚀 Hosted KT demo — handoff & hotfix guide

> The operator's guide for the **live, hosted** demo (Render, free tier). What's running, how to demo every flow, and — crucially — **where to hot-fix** each thing that can break, since it's all live during the KT.

---

## 1. What's running (two free Render services)

| Service | Role | URL |
|---|---|---|
| **Keycloak** (`cybersecurity-repo`) | SAML IdP **+** OAuth/OIDC Authorization Server | `https://cybersecurity-repo.onrender.com` |
| **Client** (`cybersecurity-repo-client`) | Landing page, 6 OAuth flows, SAML SP, QR, Resource Server | `https://cybersecurity-repo-client.onrender.com` |

Repo source: `01-identity-access-management/labs/03-kt-demo-saml-oauth/hosted/` → `keycloak/` (Docker) and `client/` (Node).
Slides: [note 23](../../notes/23-reverse-kt-presentation-guide.md) · Users: [USER-MANAGEMENT.md](USER-MANAGEMENT.md) · Local rehearsal rig: [../PRESENTER-RUNBOOK.md](../PRESENTER-RUNBOOK.md).

**Login for everything:** `farhaan / Passw0rd!` (also `priya / Passw0rd!`).

---

## 2. Deploy / redeploy — which service, when

| You changed… | Redeploy | Why |
|---|---|---|
| anything under `client/` (pages, server.js, functions) | **Client** | Node app; fast (~1 min) |
| anything under `keycloak/` (realm JSON, entrypoint, Dockerfile) | **Keycloak** | re-imports realms; slow (~4–5 min boot on 0.1 CPU) |
| the client's **URL** changed | **Keycloak** — set `CLIENT_ORIGIN` to the new URL | it bakes redirect URIs + SAML ACS |

**Redeploy:** Render → service → **Manual Deploy → Deploy latest commit** (or **Clear build cache & deploy**).

---

## 3. Environment variables (the whole set)

| Service | Key | Value | Notes |
|---|---|---|---|
| Keycloak | *(none required)* | — | admin defaults to `admin`/`admin`; own URL auto-detected via `RENDER_EXTERNAL_URL` |
| Keycloak | `CLIENT_ORIGIN` | the client's URL, e.g. `https://cybersecurity-repo-client.onrender.com` | **must match the client** or OAuth/SAML redirects fail |
| Client | `KEYCLOAK_URL` | `https://cybersecurity-repo.onrender.com` | how the client finds the IdP + its metadata |

---

## 4. Keep it warm (free tier sleeps after 15 min)
UptimeRobot (or cron-job.org) → **HTTP(s)** monitor (not "Ping"/ICMP — Render blocks ICMP) → `https://cybersecurity-repo.onrender.com/realms/master` → every 5 min. Turn it on before rehearsals + the event. First cold boot is ~4–5 min; keep-alive avoids that mid-demo.

---

## 5. The demo, flow by flow

**SAML** (`/saml.html`) — arm **SAML-tracer** first:
- **SP-initiated:** start at the app → AuthnRequest → login → signed assertion; tracer shows `SAMLRequest` then `SAMLResponse` (with `InResponseTo`).
- **IdP-initiated:** unsolicited `SAMLResponse`, no request.
- **SSO:** log out of the **app only** → log in again passwordless; **full reset** → password again.

**OAuth** (`/oauth.html`) — 6 cards, DevTools → Network open:
1. **Auth Code + PKCE** (public `kt-spa`) — `prompt=login` forces the login screen; shows PKCE verifier/challenge, `?code=`, token swap, decoded access + ID tokens; Refresh + /userinfo.
1b. **Auth Code (no PKCE)** (confidential `kt-web`) — code exchanged server-side with the secret.
2. **Client Credentials** — no user; token → calls the Resource Server (`/api/resource`) → 200.
3. **Device Code** — shows a **QR** + code; scan on phone, approve, polling flips to tokens.
4. **Implicit** (deprecated) — token in the URL fragment.
5. **ROPC** — in-app username/password form; deprecated publicly, still used for trusted first-party apps.

---

## 6. 🔧 Hotfix runbook (symptom → cause → fix)

> Most fixes are a **Render env change + redeploy** or a **realm-file edit + Keycloak redeploy**. All realm edits live in `keycloak/realms/finco-idp-realm.json` (and are applied on the next Keycloak deploy).

| Symptom | Cause | Fix |
|---|---|---|
| OAuth **`Invalid redirect_uri`** | `CLIENT_ORIGIN` on Keycloak ≠ the client's real URL | set `CLIENT_ORIGIN` = client URL → redeploy Keycloak |
| SAML **"assertion consumer url not set up"** / invalid redirect | SAML client missing an ACS location | `kt-saml-app` needs `adminUrl` + `saml_assertion_consumer_url_post` = `<client>/saml/acs` *(already set)* |
| node-saml **`Invalid signature`** | client cached a stale IdP cert after a Keycloak key change | client fetches certs **fresh** now *(fixed)*; if it ever recurs, **redeploy the client** |
| Logout → `/saml/acs` **Buffer/"Received undefined"** | SAML LogoutRequest hit the login ACS | `/saml/acs` now clears the session on non-login messages *(fixed)* |
| Keycloak **"Multiple garbage collectors selected"** | a GC flag added on top of the image's default | don't set a GC in `entrypoint.sh` — heap-only tuning *(fixed)* |
| Keycloak **OOM > 512 MB** | dev mode / big heap | optimized prod build, `-Xmx256m`, `KC_CACHE=local` *(fixed)*; only more RAM (Standard) helps beyond this |
| Keycloak **~5-min boot + JGroups "Socket is closed" spam** | clustered cache | `KC_CACHE=local` at **runtime** *(fixed)* — cache is a runtime option |
| Keycloak **"bootstrap-admin-username … password is set"** | admin username set without password | admin creds default to `admin`/`admin` now *(fixed)*; if overriding, set **both** `KC_BOOTSTRAP_ADMIN_USERNAME` and `KC_BOOTSTRAP_ADMIN_PASSWORD` |
| Realm import **"Unrecognized field …"** | a field on the wrong object | keep realm JSON to known fields; validate with `python3 -m json.tool` before deploy |
| Auth Code **no login screen** | existing Keycloak SSO session | `prompt=login` on the redirect flows *(set)* — or use a fresh/incognito tab |
| **Auth Code (no PKCE)** → `invalid_request` (code_challenge required) | `kt-web` still enforces PKCE | remove `pkce.code.challenge.method` from `kt-web` *(done)* → redeploy Keycloak |
| Build fails / hangs on Render | **Build Command** set to `node server.js` | Build = `npm install`; **Start** = `node server.js` |
| UptimeRobot shows "down" but site works | ICMP "Ping" monitor | use **HTTP(s)** monitor with the full `https://…/realms/master` URL |
| A new flow "doesn't exist" | client not redeployed | Deploy latest commit on the **client** (it `npm install`s new deps) |

**Fast live inspection (no redeploy):** get an admin token and query/patch via the API —
```bash
KC=https://cybersecurity-repo.onrender.com
TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin | jq -r .access_token)
curl -s "$KC/admin/realms/finco-idp/clients?clientId=kt-saml-app" -H "Authorization: Bearer $TOKEN"
```
*(API patches are lost on the next Keycloak restart — mirror any real fix into the realm JSON.)*

---

## 7. Key files
```
hosted/
  keycloak/
    Dockerfile          optimized prod build (kc.sh build)
    entrypoint.sh       URL substitution, JVM tuning, KC_CACHE=local, start --optimized
    realms/
      finco-idp-realm.json   ← users, all clients, SAML SP (edit here for realm changes)
      finco-app-realm.json   (legacy brokering realm; unused by the current SAML demo)
  client/
    server.js           OAuth server-side grants, SAML SP, /api/qr, Resource Server
    package.json        deps: @node-saml/node-saml, qrcode
    public/             index, oauth, saml, authcode(+nopkce), client-credentials, device, implicit, ropc
    netlify/functions/  Netlify equivalents (if ever hosted there)
  USER-MANAGEMENT.md    who owns users, where to add them
  HANDOFF.md            (this file)
```

---

## 8. Day-before checklist
- [ ] Keep-alive pinger on; both services **warm** (open both URLs).
- [ ] SAML-tracer pinned; DevTools Network tested.
- [ ] Rehearse: SAML (SP/IdP/SSO) + all 6 OAuth flows once each.
- [ ] Screenshots of each flow saved as a fallback.
- [ ] `CLIENT_ORIGIN` (Keycloak) == the client URL you'll present from.
- [ ] Know this file's §6 — if something breaks live, the fix is here.

*Authorized-lab-only · demo credentials/keys, never real data 🔐*
