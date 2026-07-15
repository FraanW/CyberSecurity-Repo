# Hosted KT demo on Render — Step 1: verify the firewall (do this first)

> **Goal:** before we build the real Keycloak + Resource Server + client on Render, prove your **office laptop's browser can reach a `*.onrender.com` app URL**. That's the *only* thing your firewall needs to allow — everything server-to-server happens inside Render.
>
> You do **not** clone anything. Render pulls from GitHub and builds in the cloud; you drive it from the Render web UI.

---

## Deploy the reachability test (~3 minutes, no terminal)

1. Go to **https://dashboard.render.com** → sign up / log in (GitHub sign-in is easiest).
2. **New +** → **Static Site**.
3. **Connect** your GitHub and pick the repo **`FraanW/CyberSecurity-Repo`** (authorize Render to read it if asked).
4. Fill in:
   - **Name:** `kt-reach-test` (your URL becomes `https://kt-reach-test.onrender.com`, or Render adds a suffix)
   - **Branch:** `claude/oauth-openid-connect-study-neesah` *(or `master` once this is merged)*
   - **Root Directory:** *(leave blank)*
   - **Build Command:** *(leave blank)*
   - **Publish Directory:** `01-identity-access-management/labs/03-kt-demo-saml-oauth/hosted/reachability-check`
5. **Create Static Site.** Wait ~1–2 min for "Live", then click the `*.onrender.com` URL.

> This also proves the **deploy-from-GitHub-via-web-UI** workflow works for you — the same workflow the full stack uses. No clone, ever.

---

## Verify at the office (the actual test)

Open that `*.onrender.com` URL **on your office laptop's browser**. The page tells you what to check; in short:

- [ ] **Page loads** → browser can reach onrender apps. ✅
- [ ] **🔒 padlock → Certificate → "Issued by":**
  - Let's Encrypt / Google Trust Services = clean.
  - Your company / Zscaler / Netskope / Palo Alto = **TLS inspection** — still fine for the demo, just **tell Claude**.
- [ ] **Self-fetch pill shows "OK"** → programmatic requests allowed (needed for the SPA's `/token` call).
- [ ] **Reload is instant** (first hit may be slow — free-tier cold start).

**Also try, if you can:** open **DevTools → Network**, reload, confirm the request is **200** and served over **HTTPS (443)**.

---

## Report back one of these

| You saw | Next step |
|---|---|
| ✅ Loads + self-fetch OK + clean cert | Say **"Render is reachable, clean cert"** → Claude builds the full real stack. |
| ✅ Loads + self-fetch OK + **company CA** cert | Say **"reachable but TLS-inspected"** → Claude builds it so nothing depends on the browser trusting Render's real cert. |
| ⚠️ Loads but self-fetch blocked | Say so — likely still workable; Claude adjusts. |
| ⛔ Block page / never loads | Say so — we switch to a fallback (e.g., everything as one same-origin service, or revisit Netlify-only). |

---

## What gets built once it's green (preview)

All on Render, all `*.onrender.com` (one domain for your firewall):

- **`finco-idp`** — a **real Keycloak** (Docker) = SAML IdP **+** OAuth/OIDC Authorization Server, with the realms auto-imported (same `finco-idp`/`finco-app` config as the local Lab 03).
- **`finco-api`** — a tiny **Resource Server** that validates access tokens (so Client Credentials / Bearer calls return real 200/401).
- **`finco-client`** — the **landing page (2 cards: SAML · OAuth)** and the **4 interactive OAuth grant flows** + the SAML SP page, self-contained (no external CDN).
- A **`render.yaml` Blueprint** so you deploy all three in one click (**New → Blueprint → pick the repo**), plus a presenter runbook for the hosted version.

*Authorized-lab-only · demo users and keys, never real FinCo data 🔐*
