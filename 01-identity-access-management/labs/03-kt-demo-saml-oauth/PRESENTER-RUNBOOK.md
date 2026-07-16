# 🎤 Presenter runbook — the day-of script (keep this open on a second screen)

> Your single-screen driver for the reverse KT. Slides live in [note 23](../../notes/23-reverse-kt-presentation-guide.md); the full lab reference is [README.md](README.md). **This file is the "what do I click / what do I say" script for the live portion.** Authorized-lab-only; **no real FinCo tokens on screen.**

---

## PART 0 — Deploy (do this ~10 min before you walk in)

1. **Start Docker Desktop**, wait until it's fully up.
2. From this folder:
   ```powershell
   docker compose up -d
   docker compose logs -f keycloak     # wait for "Running the server ... started", Ctrl+C
   ```
3. **Verify both realms** (must both print an `issuer`):
   ```powershell
   curl.exe -s http://localhost:8080/realms/KT-idp/.well-known/openid-configuration | Select-String issuer
   curl.exe -s http://localhost:8080/realms/finco-app/.well-known/openid-configuration | Select-String issuer
   ```
4. **Pre-open browser tabs** (don't type URLs on stage):
   | Tab | URL | For |
   |---|---|---|
   | 1 | `http://localhost:8080` (admin/admin) | Admin console (backup/visuals) |
   | 2 | `http://localhost:9000` | OAuth SPA — Demo B |
   | 3 | `http://localhost:8080/realms/finco-app/account` | SAML app — Demo A |
   | 4 | `http://localhost:8080/realms/KT-idp/device` | Device page — Demo D |
5. **Pin SAML-tracer**; open **DevTools → Network**.
6. **Prime PowerShell** in `scripts/`:
   ```powershell
   . .\oauth-demos.ps1
   ```
7. **Warm up Demo A once** (so the one-time "review profile" page is already dismissed).
8. **Backup:** rehearsal screenshots of every demo open in a folder.

> **Login for everything:** `farhaan` / `Passw0rd!`

---

## PART 1 — Slides first (~30 min)

Run [note 23](../../notes/23-reverse-kt-presentation-guide.md) Sections 1–4 (slides 1–26): IAM foundations → SAML → OAuth/OIDC → PingFederate mapping. Then switch to live demos.

---

## PART 2 — SAML live (Demo A) · slides 7, 9, 10

**Frame:** *"`finco-app` is an app that doesn't store passwords. Watch it federate me to our corporate IdP over SAML."*

1. Click **SAML-tracer** icon (recording).
2. **Tab 3** → app bounces to login → click **"Log in with FinCo Corporate IdP (SAML)."**
   - *Say:* "That redirect just carried a SAML **AuthnRequest** — SP-initiated SSO."
3. Log in as **farhaan** → land back in the app, logged in.
4. **SAML-tracer → "SAML" tab**, walk the two messages:
   - **`SAMLRequest`** — "small, deflated+base64, unreadable by eye."
   - **`SAMLResponse`** — read aloud: **Issuer → Signature → NameID → Conditions (NotBefore/NotOnOrAfter) → Audience → AttributeStatement.**
5. **Punchline:** *"The app never saw my password — it trusted a signed assertion. That signature is the whole security model."*

> **If login fails:** README **Demo A.4** (re-wire via Import-from-URL, 5 clicks). Or fall back to screenshots.

---

## PART 3 — OAuth 2.0 / OIDC live · slides 16–23

### Demo B — Authorization Code + PKCE **(this is OIDC — `scope=openid`)**
1. **Tab 2** (SPA) with **DevTools → Network** open → **"1 · Log in."**
2. Log in as **farhaan** → page returns logged in.
3. In **Network**, point at: **`/authorize`** (has `code_challenge`, `state`, `scope=openid`) → redirect with **`?code=`** (front channel) → **`/token`** POST (back channel).
4. On the page: **PKCE verifier vs challenge**, the **code**, and the **two decoded tokens**.
   - *Say:* "**ID token** — `aud=kt-spa`, `sub`, `nonce`, `amr` — tells my app **who** logged in. **Access token** — scopes/roles — is for the **API**. Two tokens, two jobs."
5. Click **"Call /userinfo"** (access token used as an API would), then **"Refresh"** (previews Demo E).

### Demo B-2 — Implicit **(DEPRECATED — show it to bury it)** · slide 20
Paste in the address bar, log in as **farhaan**:
```
http://localhost:8080/realms/KT-idp/protocol/openid-connect/auth?response_type=token&client_id=kt-implicit&redirect_uri=http://localhost:9999/callback&scope=profile&state=xyz
```
- Lands on `…/callback#access_token=…` ("can't reach this page" is fine).
- *Say:* "The **token is in the URL** — history, referer, logs. No code, so no PKCE. That's why Implicit is dead. `response_type=token` instead of `code` is the whole difference."

### Demo C — Client Credentials (machine-to-machine) · slide 17
```powershell
Show-ClientCredentials
```
- *Say:* "No user, no browser. **No refresh token, no ID token.** `azp=kt-service` — an app, not a person."

### Demo D — Device Code (no keyboard) · slide 18
```powershell
Show-DeviceFlow
```
- Prints a `user_code` + URL → **Tab 4**, enter code, log in, approve.
- *Say:* "Watch it **poll** — `authorization_pending` until I approve. The password only ever touched the trusted device."

### Demo E — Refresh Token (silent renewal + rotation) · slide 19
Either the SPA **"Refresh"** button, or:
```powershell
Show-Refresh
```
- *Say:* "New access token, no login. The **refresh token rotated** — old one's now dead; reuse would revoke the whole family."

### Bonus — ROPC (DEPRECATED) · slide 20
```powershell
Show-Ropc
```
- *Say:* "The app sends the user's **actual password**. It works — and **that's** the problem. Removed in 2.1. So we use Auth Code + PKCE."

---

## PART 4 — Close · slides 32–33
Q&A (slide 32 answers), then the five takeaways (slide 33). *"AuthN ≠ authZ · codes out front, tokens out back · sign/validate everything · every attack has a defense · when in doubt, `audit.log` then a capture."*

---

## Quick-reference card

| Thing | Value |
|---|---|
| Admin console | `http://localhost:8080` · admin/admin |
| SAML app (Demo A) | `http://localhost:8080/realms/finco-app/account` |
| OAuth SPA (Demo B/E) | `http://localhost:9000` |
| Device page (Demo D) | `http://localhost:8080/realms/KT-idp/device` |
| Login | `farhaan` / `Passw0rd!` |
| Scripts | `. .\oauth-demos.ps1` → `Show-ClientCredentials` / `Show-DeviceFlow` / `Show-Refresh` / `Show-Ropc` |
| Grant clients | `kt-spa` (PKCE) · `kt-web` (confidential/ROPC) · `kt-service` (client-creds) · `kt-device` (device) · `kt-implicit` (implicit) |
| Teardown | `docker compose down` (keep) · `down -v` (wipe) |

## If something breaks on stage
- **Stay calm, narrate, switch to screenshots.** The audience learns from the explanation, not the live packets.
- SAML login fails → README **Demo A.4**. · SPA `/token` CORS → README **Demo B.2** (manual curl). · Nothing starts → `docker compose down -v && docker compose up -d`.

*Built for Farhaan's reverse KT · authorized-lab-only 🔐*
