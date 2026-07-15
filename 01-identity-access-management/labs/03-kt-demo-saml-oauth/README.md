# Lab 03 — Reverse-KT demo stack: SAML + OAuth 2.0 (all 4 grant types)

> **Lefler's build, Janus's curriculum.** This is your **presentation lab** for the reverse KT. One command brings up a Keycloak that plays **both** roles your team's PingFederate plays — a **SAML Identity Provider** *and* an **OAuth 2.0 / OIDC Authorization Server** — plus a browser app so you can demo, capture, and *read* every message live with **SAML-tracer** and browser DevTools.
>
> Pair it with the slide guide: [note 23 — Reverse-KT presentation guide](../../notes/23-reverse-kt-presentation-guide.md). The demos here are exactly its Demo A–E cue cards.
>
> **Authorized-lab-only.** Everything runs on your machine with dummy users. Never put a real FinCo token or assertion on screen.

- **Time:** 60–90 min to rehearse all five demos · **Difficulty:** intermediate (beginner-safe steps) · **Platform:** Windows 11 (Docker Desktop + PowerShell); Bash variants given too.
- **Prereqs:** [note 22](../../notes/22-oauth2-grant-types-and-scenarios.md) (grants), [note 02](../../notes/02-saml-deep-dive.md) (SAML). Having done [Lab 01](../01-keycloak-idp/README.md) helps but isn't required.
- **You'll be able to demo:** SAML SSO round trip · OAuth Authorization Code + PKCE · Client Credentials · Device Code · Refresh Token — and read the assertion, code, and tokens on the wire.

---

## TL;DR — the whole event in one screen

| # | Demo | What you run | What the room watches |
|---|---|---|---|
| **A** | **SAML SSO** | log into the `finco-app` portal | SAML-tracer: AuthnRequest → signed Assertion → read the fields |
| **B** | **OAuth Auth Code + PKCE** | the SPA at `http://localhost:9000` | DevTools: `code_challenge` → `?code=` → `/token` → decode tokens |
| **C** | **Client Credentials** | `Show-ClientCredentials` (script) | one `/token` call, no user, no refresh token |
| **D** | **Device Code** | `Show-DeviceFlow` (script) | "go to URL, enter code" + polling |
| **E** | **Refresh Token** | SPA "Refresh" button *or* `Show-Refresh` | new access token, no login; refresh token **rotates** |

Start it: `docker compose up -d` → open `http://localhost:8080` (admin/admin). Details below.

---

## 0. Prerequisites (do this the DAY BEFORE, not at the podium)

1. **Docker Desktop for Windows** — installed and running. Verify in PowerShell:
   ```powershell
   docker --version
   docker compose version
   ```
2. **A browser with DevTools** (Chrome / Edge / Firefox).
3. **SAML-tracer** browser extension — install from your browser's add-on store and **pin it** to the toolbar. *(It only records while its panel is open.)*
4. This lab folder (contains `docker-compose.yml`, `import/`, `spa/`, `scripts/`).

> ⚠️ **Rehearse the whole thing at least once before the event.** This lab wires several moving parts (two SAML realms, a SPA, four grant scripts). The one place that occasionally needs a nudge is the SAML link in **Demo A** — §Demo A includes a 60-second verification and a fallback so you're never surprised on stage.

---

## 1. Start the stack (one command)

From this folder:
```powershell
docker compose up -d
docker compose logs -f keycloak     # watch until you see "Running the server ... started in ..." then Ctrl+C
```

First boot takes ~30–60s (Keycloak downloads once, then imports both realms).

**✅ Checkpoint — confirm both realms imported:**
```powershell
# should return HTTP 200 and JSON for each realm's OIDC discovery doc
curl.exe -s http://localhost:8080/realms/finco-idp/.well-known/openid-configuration | Select-String issuer
curl.exe -s http://localhost:8080/realms/finco-app/.well-known/openid-configuration | Select-String issuer
```
Then open **http://localhost:8080/** → **Administration Console** → **admin / admin**. Use the realm dropdown (top-left) to see **finco-idp** and **finco-app**.

> **What you're looking at:** `finco-idp` is your corporate IdP + Authorization Server (the PingFederate stand-in). `finco-app` is a second app that *trusts* it over SAML. The two OAuth clients, the device client, and the service account all live in `finco-idp`.

**Bookmark these endpoints (finco-idp) — you'll point at them all session:**

| Purpose | URL |
|---|---|
| OIDC discovery | `http://localhost:8080/realms/finco-idp/.well-known/openid-configuration` |
| Authorize (front channel) | `…/realms/finco-idp/protocol/openid-connect/auth` |
| Token (back channel) | `…/realms/finco-idp/protocol/openid-connect/token` |
| UserInfo | `…/realms/finco-idp/protocol/openid-connect/userinfo` |
| Device authorization | `…/realms/finco-idp/protocol/openid-connect/auth/device` |
| JWKS (public keys) | `…/realms/finco-idp/protocol/openid-connect/certs` |
| SAML IdP (metadata) | `…/realms/finco-idp/protocol/saml/descriptor` |

**Test users** (both realms trust the same person): `farhaan / Passw0rd!` and `priya / Passw0rd!`.

---

## Demo A — SAML SSO round trip (capture the assertion)

**The story for the room:** *"App B (`finco-app`) doesn't manage passwords. When I log in, it federates me to the corporate IdP (`finco-idp`) over SAML, gets a signed assertion back, and trusts it."* That's textbook **SP-initiated SSO** (slide 7).

### A.1 — Verify SAML is wired (60-second pre-flight, do it before the event)

1. Admin console → realm **finco-app** → **Identity providers** → you should see **`kc-idp`** (SAML).
2. Realm **finco-idp** → **Clients** → you should see **`kt-saml-broker`** (a SAML client).

If both are present, you're ready. Now smoke-test the actual login (A.2). **If the login fails**, jump to **A.4 (fallback)** — it re-wires the SAML link in 5 deterministic clicks.

### A.2 — Run the demo

1. **Open SAML-tracer first** (click its toolbar icon so the panel is recording).
2. In the same browser, go to the **finco-app account portal**:
   ```
   http://localhost:8080/realms/finco-app/account
   ```
3. Keycloak shows `finco-app`'s login page. Click the button **"Log in with FinCo Corporate IdP (SAML)"**.
4. You're redirected to **finco-idp** — log in as **farhaan / Passw0rd!**.
5. *(First time only)* you may see a **"review profile / update account"** page — that's **just-in-time provisioning** on first federated login (a nice thing to narrate). Confirm it once; it won't appear again.
6. You land back in the `finco-app` account console — **logged in via SAML**. ✅

### A.3 — Read the capture (this is the money moment)

In SAML-tracer, find the two rows tagged **SAML** and click the **"SAML" tab** (it auto-decodes the XML):

- **The `SAMLRequest`** (AuthnRequest, `finco-app` → `finco-idp`): note it's small and, on the wire, deflated+base64 — *"this is why you can't read it by eye."*
- **The `SAMLResponse`** (the signed assertion, `finco-idp` → `finco-app`): walk these fields **out loud** (slides 9–10):

| Find in the assertion | What to say |
|---|---|
| `<saml:Issuer>` | *"who vouched — our IdP, `http://localhost:8080/realms/finco-idp`"* |
| `<ds:Signature>` | *"the hologram — the SP verifies this"* |
| `<NameID>` | *"who this is — `farhaan@example.com`"* |
| `<Conditions NotBefore/NotOnOrAfter>` | *"~5-min validity window — where clock skew bites"* |
| `<Audience>` | *"this assertion is only for `kt-saml-broker`"* |
| `<AttributeStatement>` | *"the claims — email, givenName, surname, Role — what the app maps to permissions"* |

> **Map it back to the slides:** you've now shown slides 7, 9, and 10 on a *real* assertion your own IdP signed.

### A.4 — Fallback: re-wire the SAML link by hand (only if A.2 failed)

Keycloak makes this deterministic — it fetches the IdP's metadata for you:

1. Realm **finco-app** → **Identity providers** → delete `kc-idp` if present → **Add provider → SAML v2.0**.
2. **Alias:** `kc-idp` · **Use entity descriptor / Import from URL:**
   `http://localhost:8080/realms/finco-idp/protocol/saml/descriptor` → **Import** (this auto-fills the SSO URL and cert).
3. Turn **"Validate signatures" Off** (lab simplicity) → **Add**. Copy the **"Redirect URI"** shown on the provider page (it ends in `/broker/kc-idp/endpoint`).
4. Realm **finco-idp** → **Clients** → `kt-saml-broker` → **Settings** → ensure that same **`/broker/kc-idp/endpoint`** URL is in **Valid redirect URIs** → **Save**.
5. Retry A.2.

> **Second fallback (needs internet):** the external-SP path from [Lab 02 Exercise B](../02-saml-assertion-anatomy/README.md#exercise-b--capture-a-live-assertion-with-saml-tracer) using mocksaml/samltest — good backup if the venue Wi-Fi is fine but you're short on time.

---

## Demo B — OAuth 2.0 Authorization Code + PKCE (the default flow)

**The story:** *"Any app with a human uses this. Watch the code travel the front channel and the tokens come back on the back channel."* (Slides 16, 22, 23.)

### B.1 — Run it with the SPA (the clean visual)

1. **Open DevTools → Network** (and/or SAML-tracer is irrelevant here — use DevTools).
2. Go to **http://localhost:9000** — the demo SPA (public client `kt-spa`).
3. Click **"1 · Log in (start the flow)"** → log in as **farhaan / Passw0rd!**.
4. The page comes back **logged in** and shows, top to bottom:
   - the **PKCE** `code_verifier` (kept) vs `code_challenge` (sent) — *"only the hash went out"*
   - the **authorization code** it received (front channel)
   - the **decoded access token** and **decoded ID token** side by side
5. In **DevTools → Network**, point out the two calls:
   - **`/authorize?...`** — carries `code_challenge`, `state`, `scope=openid...`
   - **`/token`** (POST) — the back-channel exchange (code + `code_verifier` → tokens)
6. Click **"Call /userinfo"** to show the access token being used as an API would; click **"Refresh"** for a preview of Demo E.

**Talk track cue:** compare the two tokens — **ID token** `aud` = `kt-spa` (*for the app, says who logged in*), **access token** has `scope`/roles (*for the API*). Never mix them.

### B.2 — Fallback: do it by hand (if the SPA/CORS misbehaves)

This always works — it's the manual flow from Lab 01, and it's arguably *more* impressive because you type every parameter. Paste into the browser (one line), log in, then copy the `code` from the address bar:
```
http://localhost:8080/realms/finco-idp/protocol/openid-connect/auth?response_type=code&client_id=kt-web&redirect_uri=http://localhost:9999/callback&scope=openid%20profile%20email&state=xyz
```
The browser lands on `http://localhost:9999/callback?code=...` ("can't reach this page" is expected). Copy the `code`, then exchange it (be quick — codes die in ~60s):
```powershell
$body = @{ grant_type='authorization_code'; code='<PASTE_CODE>'
           redirect_uri='http://localhost:9999/callback'; client_id='kt-web'; client_secret='kt-web-secret' }
$t = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/realms/finco-idp/protocol/openid-connect/token' -Body $body
$t | Format-List
# decode (see scripts/oauth-demos.ps1 for Decode-Jwt), or paste tokens into an OFFLINE decoder
```

> **Why two clients?** `kt-spa` is a **public** client (no secret, PKCE-protected) for the SPA; `kt-web` is a **confidential** client (has a secret) for the manual/back-channel demo. Showing both is a great "public vs confidential" teaching beat (slide 16).

---

## Demo C — Client Credentials (machine-to-machine, no user)

**The story:** *"No human, no browser — the service authenticates as itself."* (Slide 17.)

**PowerShell:**
```powershell
cd scripts
. .\oauth-demos.ps1        # loads the functions
Show-ClientCredentials
```
**Bash:**
```bash
cd scripts && ./oauth-demos.sh client-credentials
```

**What to point out:**
- A single `POST /token` with `grant_type=client_credentials` and the service's secret.
- The decoded access token's `azp`/`clientId` is **`kt-service`** — an *app*, not a person.
- **No refresh token, no ID token** — there's no user session to maintain.

> **Security aside for the room:** the lab uses a client secret for simplicity; in production you'd prefer **mTLS or a private-key JWT** so there's no shared secret to leak (slide 17 presenter note).

---

## Demo D — Device Authorization (the "go to URL, enter code" flow)

**The story:** *"Smart TVs and CLIs have no keyboard — do the real login on your phone."* (Slide 18.)

**PowerShell:**
```powershell
. .\oauth-demos.ps1
Show-DeviceFlow
```
**Bash:**
```bash
./oauth-demos.sh device
```

**What happens:**
1. The script prints a **`user_code`** and a **verification URL** (`http://localhost:8080/realms/finco-idp/device`).
2. Open that URL in a **second tab** (pretend it's your phone), enter the code, log in as **farhaan**, approve.
3. Watch the script's **polling** flip from `authorization_pending` (the dots) to a real **access + ID token**.

> **Pair the attack (Law 9):** this exact flow is abused in **device-code phishing** — an attacker sends you a code "to fix your account," you complete your own MFA, and the token is minted for *their* device. Defense: never enter a code someone sent you; short `expires_in`; disable device flow where it's not needed.

---

## Demo E — Refresh Token (silent renewal + rotation)

**The story:** *"Access tokens are short; refresh tokens quietly renew them without nagging the user."* (Slide 19.)

**Easiest live:** in the **SPA (Demo B)**, click **"Refresh the access token"** — a new access token appears with no login, and the status line notes a **new refresh token** came back (rotation).

**Script version (shows the rotation explicitly):**
```powershell
. .\oauth-demos.ps1
Show-Refresh          # prints old vs new refresh token — they differ (rotated)
```
```bash
./oauth-demos.sh refresh
```

> **The rule to say:** rotation + reuse detection. If an old, already-used refresh token reappears, the AS assumes a thief has a copy and **revokes the whole family**. That's what keeps you logged into the mobile banking app all day, safely.

---

## Bonus — ROPC (the DEPRECATED password grant), for contrast

Only to *show why we don't use it* (slide 20). `kt-web` has it enabled for the demo:
```powershell
. .\oauth-demos.ps1 ; Show-Ropc
```
```bash
./oauth-demos.sh ropc
```
It works — the app sends the user's **actual password** to `/token` — *and that's exactly the anti-pattern OAuth exists to kill.* Removed in OAuth 2.1. Great mic-drop before you say "…so we use Authorization Code + PKCE instead."

---

## Attack / defense sidebar (repo rule: always pair them)

Try these **only** on this lab, and narrate the defense:

- **Break redirect-URI security on purpose (OAuth):** repeat Demo B.2 with `redirect_uri=http://evil.example/callback`. Keycloak **refuses** — feel the **exact-match allow-list** defense (slide 24, row 1).
- **Break `state` (OAuth CSRF):** in the SPA, tamper with the returned `state` — the page aborts with "state mismatch." That's the CSRF guard.
- **Unsigned-assertion / XSW (SAML):** study only — ask **Loki** to walk XML Signature Wrapping against a captured lab assertion, and **Heimdall** what a SIEM would flag (multiple assertions, signature-validation failures). Full table: [note 02 §9](../../notes/02-saml-deep-dive.md#9-attacks--defenses-always-pair-them--claudemd-rule).
- **JWT `alg:none` / RS256→HS256 (OAuth):** study only — the crypto and the hands-on exploit live in [`../../../04-cryptography/`](../../../04-cryptography/) Lab 9; the fix is "allowlist algorithms, verify via JWKS by `kid`."

---

## How this maps to your day job (PingFederate)

| In this lab (Keycloak) | At FinCo (PingFederate) |
|---|---|
| realm `finco-idp` as SAML IdP | an **SP connection** (we log people into an app) |
| realm `finco-app` trusting it | an **IdP connection** (we trust a partner IdP) |
| the login page / MFA step | **adapters** (HTML Form, Kerberos, PingID) |
| `kt-service` client credentials | service accounts calling internal APIs |
| JWT vs opaque access token | the **Access Token Manager** decision |
| Keycloak **Events** log | PingFederate **`audit.log`** |

Deep dive: [note 18 — PingFederate field guide](../../notes/18-pingfederate-explained.md). Slide-by-slide Ping mapping: [note 23 slide 26](../../notes/23-reverse-kt-presentation-guide.md).

---

## Troubleshooting (check before you panic on stage)

| Symptom | Likely cause & fix |
|---|---|
| `docker compose up` fails on port | 8080 or 9000 already in use → stop the other app, or edit the ports in `docker-compose.yml` |
| Discovery URLs 404 | Keycloak still starting → wait for "started in" in the logs; re-run the §1 checkpoint |
| Realms not present | import ran on an old volume → `docker compose down -v` then `up -d` for a clean import |
| **Demo A** login fails | the SAML link needs a nudge → **Demo A.4 fallback** (Import from URL) |
| **Demo B** SPA can't reach `/token` (CORS) | confirm `kt-spa` **Web origins** include `http://localhost:9000` (admin → Clients → kt-spa) → or use **Demo B.2** manual fallback |
| "Account is not fully set up" on first SAML login | the one-time **review-profile** page — confirm it once (§A.2 step 5) |
| Access token isn't a JWT | it is by default in Keycloak; if you changed it, revert — the SPA decoder expects a JWT |
| Clock skew errors after your laptop slept | restart Docker Desktop so the container clock resyncs |

---

## Data-handling note (fintech habit)

- All users/tokens here are **dummy** — safe to demo and screenshot.
- **Never** put a **real** FinCo assertion or token on screen, in a slide, or into an online decoder — decode sensitive ones **offline** (same rule as [note 05 §D](../../notes/05-first-week-questions.md)). The repo `.gitignore` blocks keys/certs; captures aren't auto-ignored, so don't save real ones.

---

## Cleanup

```powershell
docker compose down        # stop, KEEP your config (realms/UI changes survive)
docker compose down -v     # stop and WIPE everything (fresh import next time)
```

> Between rehearsals use `down` (keeps any Demo-A UI fix you made). For a guaranteed-clean run, use `down -v` then `up -d`.

---

## Day-before rehearsal checklist (Law 6)

- [ ] `docker compose up -d`; §1 checkpoint passes for **both** realms.
- [ ] **Demo A** completes end to end; SAML-tracer shows the AuthnRequest + signed Response.
- [ ] **Demo B** SPA logs in and decodes both tokens (or B.2 manual path ready).
- [ ] **Demos C, D, E** each run once from `scripts/` (PowerShell dot-sourced).
- [ ] SAML-tracer pinned; DevTools Network tested; screenshots of each demo saved as a backup.
- [ ] Mermaid diagrams in [note 23](../../notes/23-reverse-kt-presentation-guide.md) render in your slide tool (export PNGs as fallback).
- [ ] `docker compose down` (not `-v`) so your setup is warm for the event.

---

## What you learned & next

- You stood up, in one command, a single server playing **SAML IdP + OAuth/OIDC Authorization Server** — exactly the dual role PingFederate plays — and drove **five flows** end to end.
- You captured and *read* a real **SAML assertion**, an **authorization code**, and decoded **access/ID tokens** — the artifacts you'll actually touch in tickets.
- You can now demonstrate, not just describe, that you understand these protocols — the whole point of the reverse KT.

**Next:** rehearse against [note 23 — the slide guide](../../notes/23-reverse-kt-presentation-guide.md) until the demos are muscle memory, then skim [note 21 §9](../../notes/21-oauth2-complete-reference.md) (15 attacks) and [note 02 §13](../../notes/02-saml-deep-dive.md) (60-second SAML checklist) as Q&A insurance.

*Built for Farhaan's reverse KT · authorized-lab-only 🔐*
