# Hosted KT demo — Part 2: the client (landing page + 4 interactive OAuth flows)

> A tiny Node web app (zero npm dependencies) that serves the **landing page (SAML · OAuth cards)**, the **four interactive OAuth grant flows**, and the **SAML** launcher — wired to your hosted Keycloak from Part 1. Deploy as a free Render **Web Service**. No cloning.

**Prereq:** Part 1 done — your Keycloak is live (e.g. `https://cybersecurity-repo.onrender.com`) and healthy.

---

## 1. Deploy the client (~2 min; Node cold-starts fast)
1. **dashboard.render.com → New + → Web Service** → repo `FraanW/CyberSecurity-Repo`.
2. Configure:
   - **Name:** `finco-client` → URL becomes `https://finco-client.onrender.com`
   - **Branch:** `master`
   - **Root Directory:** `01-identity-access-management/labs/03-kt-demo-saml-oauth/hosted/client`
   - **Runtime:** **Node** (auto-detected from `package.json`) · Build `npm install` · Start `npm start`
   - **Instance Type:** **Free**
3. **Environment variable:**
   | Key | Value |
   |---|---|
   | `KEYCLOAK_URL` | `https://cybersecurity-repo.onrender.com` *(your Part-1 IdP URL, no trailing slash)* |
4. **Create Web Service.** When live, open `https://finco-client.onrender.com` → you should see the two cards.

---

## 2. Point Keycloak at the client (one-time — makes redirects match)
The browser flows (Auth Code + PKCE, Implicit) redirect back to the client, so Keycloak must allow the client's URL as a redirect. Tell it:

1. Render → your **`finco-idp`** (Keycloak) service → **Environment** → add:
   | Key | Value |
   |---|---|
   | `CLIENT_ORIGIN` | `https://finco-client.onrender.com` *(your client URL from step 1)* |
2. **Save** → Keycloak redeploys (~3–4 min) and re-imports the realms with the client's URL baked into the redirect URIs + web origins.

> **Why this matters (and it's a teaching point):** Keycloak enforces **exact-match redirect URIs**. Until `CLIENT_ORIGIN` is set, the browser flows fail with `Invalid redirect_uri` — which is the security control working, not a bug. Client Credentials and Device Code (server-side) work regardless.

---

## 3. Verify each flow (rehearsal checklist)
Open `https://finco-client.onrender.com`, then:
- [ ] **SAML card** → Start SAML login → log in as `farhaan/Passw0rd!` → SAML-tracer shows `SAMLRequest` + signed `SAMLResponse`.
- [ ] **OAuth → Authorization Code + PKCE** → log in → PKCE verifier/challenge, the `?code=`, decoded access + ID tokens. Try **Refresh** and **/userinfo**.
- [ ] **OAuth → Client Credentials** → Get a token (no refresh/id token) → **Call the Resource Server API** → HTTP 200.
- [ ] **OAuth → Device Code** → Start → open the URL on another tab, enter the code, approve → the page's polling flips to tokens.
- [ ] **OAuth → Implicit** → log in → the access token appears **in the URL fragment** (the point).

---

## 4. Keep-alive
- **Keycloak (Part 1)** is the one that must stay warm — keep the UptimeRobot pinger on `…/realms/master`.
- **This client** is a tiny Node app that cold-starts in seconds, so it needs no pinger. (To be safe on the day, just open it once a few minutes before you present.)
- Free-tier reminder: run the Keycloak pinger mainly around your rehearsal + event windows so you stay well under the 750 free hours.

---

## 5. Presenting from here (office laptop)
Everything is browser-only on `*.onrender.com` — nothing to install but **SAML-tracer**. Suggested order (matches note 23's Demo A–E and the local `PRESENTER-RUNBOOK.md`):
1. **SAML** card — arm SAML-tracer, log in, read the assertion.
2. **OAuth → Auth Code + PKCE** — the star; contrast ID vs access token.
3. **Client Credentials** — machine-to-machine + the 200 from the Resource Server.
4. **Device Code** — the two-device flow.
5. **Implicit** — token-in-URL, "so we don't use this."

> Lab-only: demo users/keys, no real FinCo data. Don't put a real token/assertion on screen.

---

## Deploying on Netlify instead of Render (same folder works for both)
The `/api/*` routes and `/config.js` are also provided as **Netlify Functions** (`netlify/functions/`), wired by `netlify.toml`, so the exact same pages run on Netlify with no code change. Keycloak still lives on Render.

1. **app.netlify.com → Add new site → Import an existing project** → GitHub → repo `FraanW/CyberSecurity-Repo`.
2. Configure:
   - **Base directory:** `01-identity-access-management/labs/03-kt-demo-saml-oauth/hosted/client`
   - **Publish directory:** `public` *(relative to base — `netlify.toml` already sets this)*
   - **Functions directory:** `netlify/functions` *(also set in `netlify.toml`)*
   - **Build command:** *(leave empty)*
3. **Environment variables** (Site configuration → Environment variables):
   | Key | Value |
   |---|---|
   | `KEYCLOAK_URL` | `https://cybersecurity-repo.onrender.com` |
4. **Deploy.** Your site is `https://<name>.netlify.app`.
5. **Point Keycloak at it:** on the Render `finco-idp` service, set `CLIENT_ORIGIN` to your **Netlify** URL (`https://<name>.netlify.app`) and save → Keycloak redeploys so the browser flows' redirect URIs match. *(If you'd deployed the Render client too, this env var only points at one origin — set it to whichever client you'll actually present from.)*
6. Verify the five flows (same checklist as §3).

> **Why Netlify needs the functions:** a pure static host can't run Client Credentials (needs the secret) or Device Code (browser CORS), or generate `/config.js`. The functions are the serverless equivalent of `server.js` — Auth Code + PKCE, Implicit and Refresh still run straight in the browser against Keycloak.

## How it's wired (for your own understanding)
- **Browser → Keycloak directly:** Auth Code + PKCE, Implicit, Refresh, `/userinfo` (CORS allowed via `CLIENT_ORIGIN` web origins).
- **Browser → this Node app → Keycloak:** Client Credentials (secret stays server-side) and Device Code (avoids browser CORS). The app also exposes `/api/resource`, a real Resource Server that validates the token via Keycloak introspection.
- **Config injection:** `/config.js` serves `KEYCLOAK_URL`/`REALM` from the env var, so nothing is hardcoded.

*Authorized-lab-only 🔐*
