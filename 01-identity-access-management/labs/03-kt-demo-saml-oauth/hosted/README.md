# Hosted KT demo on Render (fully free) — Part 1: the Keycloak IdP

> The browser-only, no-clone version of the KT demo, for presenting from a locked-down office laptop. **Part 1 (this doc): stand up the real Keycloak** (SAML IdP + OAuth/OIDC Authorization Server) on Render's free tier and keep it alive. **Part 2 (next): the client** — landing page (2 cards) + the 4 interactive OAuth flows — is built once Keycloak is confirmed healthy.
>
> Deploy from GitHub via the Render web UI — **you never clone anything.**

---

## Reality check on "fully free"
- **Free = 512 MB RAM / 0.1 CPU.** Keycloak is tuned here to fit (JVM capped at 400 MB). Fine for a **single-presenter** demo; watch for restarts during rehearsal. *(If it's unstable, the only real fix is more RAM — Render **Standard**, 2 GB — because $7 Starter is also just 512 MB.)*
- **Free spins down after 15 min idle**, and cold-start on 0.1 CPU takes **1–3 min**. → You **must keep it warm** (see §3). This is the single most important thing for a smooth live demo.

---

## 1. Deploy Keycloak on Render (~5 min + first boot)

1. **dashboard.render.com** → **New +** → **Web Service** → **Build and deploy from a Git repository** → connect **`FraanW/CyberSecurity-Repo`**.
2. Configure:
   - **Name:** `finco-idp` → your URL becomes `https://finco-idp.onrender.com` (Render may add a suffix; whatever it is, that's your IdP URL).
   - **Branch:** `claude/oauth-openid-connect-study-neesah` *(or `master` once merged)*
   - **Root Directory:** `01-identity-access-management/labs/03-kt-demo-saml-oauth/hosted/keycloak`
   - **Runtime / Language:** **Docker** (it auto-detects the `Dockerfile`)
   - **Instance Type:** **Free**
3. **Environment variables:** none required — the image defaults the admin login to **`admin` / `admin`** (lab-only) and auto-detects its public URL from Render's `RENDER_EXTERNAL_URL`.
   - *Optional override:* set **both** `KC_BOOTSTRAP_ADMIN_USERNAME` **and** `KC_BOOTSTRAP_ADMIN_PASSWORD` together (Keycloak refuses to start if only one is set — with the exact name `KC_BOOTSTRAP_ADMIN_PASSWORD`, not `PASSWORD`).
   - *(You'll add one var, `CLIENT_ORIGIN`, in Part 2 once the client exists.)*
4. **Create Web Service.** First boot is slow on free (watch the **Logs** tab for `Running the server ... started`). If it restarts once or twice while booting cold, give it a minute.

**✅ Checkpoint:**
- Open `https://<your-idp>.onrender.com/realms/finco-idp/.well-known/openid-configuration` → JSON with an `issuer` that is your **https onrender URL** (not localhost). 
- Open `https://<your-idp>.onrender.com/admin/` → log in `admin`/`admin` → realm dropdown shows **finco-idp** and **finco-app**.

> If the OIDC `issuer` shows `http://` or `localhost`, the proxy/hostname env didn't take — check the service logs; the entrypoint prints the public origin it used.

---

## 2. What just deployed (and how it maps to your job)
One Keycloak = **PingFederate's dual role**: a **SAML IdP** *and* an **OAuth/OIDC Authorization Server**. Realms auto-import on every boot (clean + reproducible). Clients ready to use:

| Client | Grant / role |
|---|---|
| `kt-spa` | Authorization Code + PKCE (**OIDC** — `openid` scope) |
| `kt-web` | confidential Auth Code + Refresh (+ ROPC for contrast) |
| `kt-service` | Client Credentials (machine-to-machine) |
| `kt-device` | Device Authorization |
| `kt-implicit` | Implicit (deprecated — shown to bury it) |
| `kt-saml-broker` | the SAML SP (realm `finco-app` federates to `finco-idp`) |

*(Keycloak↔Entra ID↔PingFederate concept map lives in note 23 / the chat.)*

---

## 3. Keep it alive (do NOT skip — free tier sleeps)
Ping a cheap Keycloak URL every ~10 min so it never idles out.

**UptimeRobot (easiest, free):**
1. uptimerobot.com → sign up → **Add New Monitor**.
2. Type **HTTP(s)** · Friendly name `keep keycloak warm` · **URL** `https://<your-idp>.onrender.com/realms/master` · **Monitoring interval 5 minutes** → **Create**.

That's it — it pings forever, keeps the service warm, and gives you an uptime graph. (cron-job.org works the same way if you prefer.)

**Notes:**
- Ping `…/realms/master` — always 200, lightweight.
- Free tier allows **750 instance-hours/month** ≈ one always-on service. Keep-alive **only this one** service; the Part-2 client will be a **free Static Site** (never sleeps, doesn't burn hours).
- **Day-of insurance:** enable the pinger the night before **and** manually open the IdP URL ~5 min before you present.

---

## 4. Stress-test before you trust it (rehearsal)
On free 512 MB / 0.1 CPU, do a full dry run and watch the Render **Logs / Metrics**:
- [ ] Admin console loads; both realms present.
- [ ] Log in as `farhaan` / `Passw0rd!` somewhere (e.g. `…/realms/finco-idp/account`).
- [ ] Memory stays under 512 MB (Metrics tab) — no `OOMKilled` / restart loop.
- [ ] After 20 min idle **with the pinger on**, the service is still instantly responsive.

If you see OOM/restart loops under light use, that's the free-RAM ceiling — tell me and we'll either trim further or you bump that one service to Standard for the event.

---

## Next (Part 2)
Once your IdP passes §1 and §4, tell me your **IdP URL** and I'll build the client — the **landing page (SAML · OAuth cards)** and the **4 interactive grant flows** — as a free Render **Static Site**, wire it to your IdP, and add the `CLIENT_ORIGIN` env var so redirect URIs line up. Then a hosted presenter runbook.

*Authorized-lab-only · demo users/keys, never real FinCo data 🔐*
