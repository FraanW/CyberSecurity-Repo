# Lab 04 — App onboarding with PingFederate: a SAML app and an OAuth app

> **Lefler built it, Janus specified it.** Two small Java Spring Boot apps you deploy to the
> public internet, then **onboard into your own PingFederate server** — one as a **SAML 2.0 SP
> connection**, one as an **OAuth 2.0 / OIDC client**. This is the single most common ticket in
> an IAM team's queue, and now you can practise it end to end on apps you fully control.
>
> **Authorized-lab-only.** Point these at *your own* PingFederate with *test* clients and *dummy*
> users. Never a production connection, never a real FinCo token on screen.

- **Time:** ~30 min to deploy · ~30 min per onboarding · **Difficulty:** intermediate
- **Platform:** anything with a browser. Local runs are shown for **Windows 11 (PowerShell)** and Bash.
- **Prereqs:** [note 02 — SAML deep dive](../../notes/02-saml/02-saml-deep-dive.md), [note 21 — OAuth 2.0 reference](../../notes/03-oauth-oidc/21-oauth2-complete-reference.md), [note 18 — PingFederate explained](../../notes/06-platforms-and-gateways/18-pingfederate-explained.md). A Render account (free tier is enough). Your PingFederate must be reachable from the internet for the browser redirects to work.
- **You'll be able to:** create an SP connection from a metadata file, create an OAuth client, read an assertion field by field, tell a JWT access token from a reference token, and run all the grants by hand.

---

## TL;DR — the whole lab in one screen

| | **saml-sp** | **oauth-client** |
|---|---|---|
| **Plays the role of** | Service Provider (the app that trusts the login) | Relying Party (the app that receives a token) |
| **In the Ping console** | Applications → Integration → **SP Connections** | Applications → OAuth → **Clients** |
| **The one value Ping needs** | the **ACS URL** | the **redirect URI** |
| **Give Ping this file** | `/api/sp-metadata.xml` | *(nothing — you type the client in)* |
| **What you get back** | a signed **assertion** | an **access token** + **ID token** |
| **See it at** | `/api/assertion` | `/api/tokens` |

**Both apps also ship a plain username/password login.** That means they are *live and usable the
moment they deploy*, before PingFederate knows anything about them. It also gives you the
before/after that makes SSO click: same app, same session cookie, two different ways of proving
who you are.

---

## 0. What these apps actually are

Each app is **one Docker image containing both halves**:

```
┌─────────────────────── one container ───────────────────────┐
│  Spring Boot (Java 21)                                      │
│    ├── the security filter chain  ← the interesting part    │
│    ├── /api/*  JSON endpoints                               │
│    └── serves static/  ── index.html + app.js + style.css   │
└─────────────────────────────────────────────────────────────┘
```

The frontend is deliberately plain HTML and JavaScript with **no build step and no CDN**. Every
button maps to exactly one HTTP call you could make with `curl`. Nothing is hidden behind a
framework, because the point of the lab is to *watch the protocol*.

> **Why one image and not two services?** Because a separate frontend origin would drag CORS,
> cookie `SameSite` rules and a second deploy into a lab about federation. Same-origin keeps the
> session cookie simple so the protocol stays the star. (Real FinCo apps often *are* split —
> that split is its own topic, see [note 20 — reverse proxies in IAM](../../notes/06-platforms-and-gateways/20-reverse-proxies-in-iam.md).)

---

## 1. Deploy both apps to Render

### Option A — the blueprint (both apps at once)

1. Push this repo to your GitHub account.
2. Render dashboard → **New** → **Blueprint** → pick the repo.
3. Render reads [`render.yaml`](../../../render.yaml) at the repo root and offers both services.
4. It prompts for the variables marked `sync: false`. **Leave every PingFederate one blank for now.**
   Set just one thing: **`LAB_LOCAL_PASSWORD`** on each service.
5. **Apply**. First build takes ~5 minutes (Maven downloads the world once).

### Option B — one service at a time

Render dashboard → **New** → **Web Service** → pick the repo, then:

| Field | saml-sp | oauth-client |
|---|---|---|
| Language / Runtime | **Docker** | **Docker** |
| Root Directory | `01-identity-access-management/labs/04-pingfederate-app-onboarding/saml-sp` | `…/oauth-client` |
| Dockerfile Path | `./Dockerfile` | `./Dockerfile` |
| Health Check Path | `/api/health` | `/api/health` |

Then add these environment variables by hand:

```
PUBLIC_BASE_URL=https://<your-service>.onrender.com
LAB_LOCAL_USERNAME=farhaan
LAB_LOCAL_PASSWORD=<pick something>
```

**✅ Checkpoint — both services are up:**

```bash
curl -s https://pingfed-saml-sp.onrender.com/api/health
curl -s https://pingfed-oauth-client.onrender.com/api/health
```

Expected, from each:

```json
{"status":"UP","app":"pingfed-saml-sp","role":"SAML 2.0 Service Provider","startedAt":"..."}
```

> ⚠️ **Render's free tier sleeps after 15 minutes idle.** The first request after a nap takes
> 30–60 seconds while the container wakes. That is not a bug in your SSO — it is the free plan.
> If a SAML login times out on the very first try, wake the app first and retry.

**Now open each app in a browser and log in with the local username and password.** If that works,
your deployment is healthy and every problem from here on is a *federation* problem, not a
*hosting* problem. That separation is worth more than it sounds at 11pm.

> **Did not set `LAB_LOCAL_PASSWORD`?** The app generated one and printed it in the startup logs.
> Render dashboard → your service → **Logs**, look for the boxed `username: / password:` block.

---

## 2. Run them locally instead (optional)

**PowerShell (Windows 11):**

```powershell
cd 01-identity-access-management\labs\04-pingfederate-app-onboarding\saml-sp
docker build -t pingfed-saml-sp .
docker run --rm -p 8081:8080 -e LAB_LOCAL_PASSWORD=lab-pass-123 -e COOKIE_SECURE=false pingfed-saml-sp
```

**Bash:**

```bash
cd 01-identity-access-management/labs/04-pingfederate-app-onboarding/oauth-client
docker build -t pingfed-oauth-client .
docker run --rm -p 8082:8080 -e LAB_LOCAL_PASSWORD=lab-pass-123 pingfed-oauth-client
```

Then open `http://localhost:8081` and `http://localhost:8082`.

> **Gotcha — `COOKIE_SECURE=false` for the SAML app on plain HTTP.** The SAML app marks its
> session cookie `SameSite=None`, because the IdP POSTs the assertion to us *cross-site*.
> Browsers refuse `SameSite=None` unless the cookie is also `Secure`, and `Secure` cookies are
> refused over plain `http://`. On Render (HTTPS) leave the default alone; locally, turn it off.

> **No Docker?** `mvn spring-boot:run` in either app folder works too — Java 21 required.

---

## 3. Onboard the SAML app

Full click-path with screenshots-in-words: **[`PINGFED-SETUP.md`](PINGFED-SETUP.md) §1**. The
short version:

1. Open the app → **Step 1** → **⬇ Download SP metadata XML** (that is `/api/sp-metadata.xml`).
2. PingFederate console → **Applications → Integration → SP Connections → Create Connection**
   → **Browser SSO Profiles** → **SAML 2.0** → **Import Metadata** → upload that file.
3. Fill in the **attribute contract** (start with just `SAML_SUBJECT`, add `email` and `firstName` after).
4. Save, then **enable** the connection.
5. Back in the app → **Step 3** → **Log in with PingFederate**.

**No metadata URL, or want to understand what the import actually did?**
**[`PINGFED-SETUP.md`](PINGFED-SETUP.md) §1.7** walks through entering the entity ID, ACS URL,
bindings and certificate by hand, and **§1.8** covers the piece metadata import never touches at
all: building the **IdP Adapter** and **Authentication Policy** that actually check the user's
credential before the assertion is signed.

**✅ Checkpoint — you are logged in via SAML.** The dashboard now says *"via PingFederate (SAML)"*,
and **Step 4** fills in with the attributes, the parsed highlights, and the raw XML.

### Point the app at your IdP — import the metadata (new)

Steps 1–5 above tell **PingFederate** about the app. This is the other direction: telling **the
app** about PingFederate. It needs exactly three facts — the IdP's **entity ID**, its **SSO URL**,
and its **signing certificate** — and the certificate is the one that decides whether a login
succeeds or comes back `Invalid Signature`.

You can type all three into environment variables. **Don't.** Import the metadata instead:

1. In PingFederate: **System → Protocol Metadata → Metadata Export** → save the XML.
   *(Or fetch the published URL — see the `PartnerSpId` gotcha below.)*
2. In the app: **Step 2 → Import the IdP's metadata → Upload a file → Preview**.
3. **Read the SHA-256 fingerprint** in the preview and check it against the Ping console
   (*SP Connection → Credentials → Digital Signature Settings*).
4. Click **Import and trust this IdP**.

**Live on the next login. No redeploy.** The app swaps the registration in memory.

> ⚠️ **The `PartnerSpId` gotcha.** PingFederate's metadata URL takes a parameter:
> `…/pf/federation_metadata.ping?PartnerSpId=<your SP entity ID>`. **With** it you get the
> certificate *your connection* signs with. **Without** it you get the server default, which can be
> a different certificate entirely — and that mismatch is invisible until every login fails.

> ⚠️ **Importing is a trust decision.** A metadata file is not self-authenticating. Unless it is
> XML-signed by a key you already hold, importing it means trusting whoever gave you the file.
> That is exactly why Preview and Import are two separate buttons: the gap between them is where
> you check the fingerprint. The app requires a login before it will let you import at all —
> anyone who could re-point this SP at an IdP they control could walk in as anybody.

**Why bother, when three environment variables also work?** Because two of the three are URLs you
can typo and the third is a 1,600-character base64 blob that can be mangled on paste or go stale on
rotation. Metadata deletes the transcription step, and with it the most common cause of
`Invalid Signature`. It also trusts **every** certificate the IdP publishes, so a key rotation
mid-flight cannot lock you out.

**Undo it:** *Revert to environment config* drops the imported file and falls back to
`LAB_SAML_IDP_*`. **Make it permanent:** the import screen prints the equivalent environment
variables — an import survives a restart, but not a redeploy (Render rebuilds the filesystem from
the image).

### Before you have real key material

The app mints a **throwaway signing keypair at startup** so it can boot with nothing configured.
It rotates on every restart — which means PingFederate stops trusting your signed AuthnRequests
after any redeploy. Fix it once and forget it:

```bash
cd 01-identity-access-management/labs/04-pingfederate-app-onboarding
./scripts/generate-sp-keypair.sh          # PowerShell: .\scripts\Generate-SpKeypair.ps1
```

Paste the printed `LAB_SAML_SP_PRIVATE_KEY` and `LAB_SAML_SP_CERTIFICATE` into Render, redeploy,
then **re-download the metadata** — it now carries the stable certificate.

> The generated `sp-signing.key` never goes into git. The repo `.gitignore` blocks `*.key` and
> `*.crt` already. Check with `git status` anyway; that habit is free.

---

## 4. Onboard the OAuth app

Full click-path: **[`PINGFED-SETUP.md`](PINGFED-SETUP.md) §2**. The short version:

1. Open the app → **Step 1** → copy the **Redirect URI**. It looks like
   `https://pingfed-oauth-client.onrender.com/login/oauth2/code/pingfed`.
2. PingFederate console → **Applications → OAuth → Clients → Add Client**.
   Paste the redirect URI **exactly** — one trailing slash difference and you get
   `invalid_redirect_uri`, which is the single most common OAuth onboarding failure.
3. Allow the grant types **Authorization Code** and **Refresh Token**
   (and **Client Credentials** if you want Step 5's first button to work).
4. Copy the **client ID** and **client secret**.
5. Set these in Render and redeploy:

```
LAB_OAUTH_ISSUER_URI=https://<your-pingfederate-host>
LAB_OAUTH_CLIENT_ID=<from the console>
LAB_OAUTH_CLIENT_SECRET=<from the console>
LAB_OAUTH_INTROSPECTION_URI=https://<your-pingfederate-host>/as/introspect.oauth2
```

**✅ Checkpoint — `/api/config` shows discovered endpoints.**

```bash
curl -s https://pingfed-oauth-client.onrender.com/api/config | jq .endpoints
```

If `resolvedVia` says `OIDC discovery (issuer URI)`, PingFederate answered on
`/.well-known/openid-configuration` and every endpoint is filled in for you. If it says
`explicit settings`, discovery failed — check the service logs for the reason, and fall back to
typing the four endpoint URLs.

6. Back in the app → **Step 3** → **Log in with PingFederate**.

---

## 5. The five things to actually look at

Once both logins work, this is the part that teaches. Numbers refer to the cards in each app.

| # | Where | What to notice | Why it matters at FinCo |
|---|---|---|---|
| 1 | SAML **Step 4** → *fields that break onboarding* | **Audience** must equal your SP entity ID. **Destination** must equal your ACS URL. | Nine out of ten "SSO is broken" tickets are one of these two not matching. |
| 2 | SAML **Step 4** → *Conditions NotOnOrAfter* | A SAML assertion is valid for **minutes**. | An assertion rejected as expired is usually **clock skew** on the SP host, not a Ping problem. |
| 3 | OAuth **Step 4** → *access token format* | `jwt` or `opaque`? | PingFederate's **Access Token Manager** decides. Reference tokens are the default and they surprise every new API team. A JWT is validated offline against the JWKS; a reference token needs an introspection call on *every* request. |
| 4 | OAuth **Step 5** → *Refresh, twice* | Does the refresh token value change? | If it does, **rotation** is on — replaying an old refresh token then kills the whole chain. That is a detection signal, not just a setting. |
| 5 | OAuth **Step 5** → *Client Credentials* | It needs **no login at all**. | That is the whole point: service-to-service. Notice there is no refresh token either — a machine can just ask again. |

---

## 6. Attacks these apps let you see — and the defence for each

**Law 9: never an attack without its defence.** Each of these is safe to try *on your own lab
connection*, and each maps to a control you will be asked about in an audit.

| Try this | What should happen | The defence you just proved works | Detect it |
|---|---|---|---|
| Edit an attribute inside the assertion XML and replay the POST to the ACS URL | **Rejected** — signature check fails | XML signature validation against the IdP's certificate | PingFederate `audit.log` records the failed SSO; the SP logs a `Saml2AuthenticationException` |
| Replay the same assertion twice | **Rejected** — `NotOnOrAfter` has passed, or the ID was already used | Short assertion lifetime + one-time use | Two SSO events, same assertion ID |
| Change one character of `redirect_uri` in the authorization URL | **Rejected** — `invalid_redirect_uri` | Exact-match redirect URI registration | PingFederate logs the rejected authorization request |
| Strip `code_challenge` from the authorization request, then redeem the code | **Rejected** when PKCE is required | PKCE binds the code to the client that started the flow | A token request with no `code_verifier` |
| Call `/api/introspect` after logging out | `active: false` | Token revocation on session end | Introspection call returning inactive |

> **Do this only against your own lab connection.** Replaying assertions against a production
> connection — even your employer's — is unauthorized testing.

---

## 7. When it does not work

| Symptom | Almost always | Fix |
|---|---|---|
| SAML: `Invalid destination` / `Invalid audience` | Your app is behind Render's TLS proxy and built an `http://` URL | Set `PUBLIC_BASE_URL` to the full `https://…onrender.com` origin, redeploy, re-import the metadata |
| SAML: `Invalid signature` **on the assertion** (login fails at the app) | The app is holding the **wrong public key** for the IdP | **Import the IdP's metadata** (§3). Then read the diagnosis below |
| SAML: PingFederate rejects the **AuthnRequest**'s signature (login fails at Ping) | Ping has an old copy of **your SP** certificate | You redeployed with an ephemeral keypair. Generate a fixed one (§3) and re-import the SP metadata into Ping |
| SAML: assertion rejected as expired | Clock skew | Check the SP host's time. Assertions live for minutes |
| OAuth: `invalid_redirect_uri` | One character off | Compare `/api/config → client.redirectUri` against the console, character by character |
| OAuth: `invalid_client` | Wrong client auth method | Try `LAB_OAUTH_CLIENT_AUTH_METHOD=client_secret_post` — some clients are configured that way |
| OAuth: discovery failed in the logs | Issuer value ≠ the host you reach it on, or a firewall | Fall back to the four explicit endpoint URLs |
| OAuth: no refresh token | Scope or grant missing | Add `offline_access` to `LAB_OAUTH_SCOPES` **and** allow the Refresh Token grant on the client |
| Either app: 401 from `/api/whoami` | Not logged in | That is correct behaviour — the frontend renders it as "not logged in" |

**Turn the logs up** when stuck — both apps log the exact protocol-level failure:

```
LAB_SAML_LOG_LEVEL=DEBUG      # on the SAML app
LAB_OAUTH_LOG_LEVEL=DEBUG     # on the OAuth app
```

### `Invalid Signature` on the assertion — the 60-second RCA

This is the most common onboarding failure and it has one dominant cause, so it gets its own
procedure. Full derivation — what a signature is, why it must exist, and how the maths works — is in
**[note 34 — "Invalid Signature": how SAML signature verification really works](../../notes/02-saml/34-saml-invalid-signature-rca.md)**.

**The one-line version:** the IdP signed with a **private key**; your app checks with the matching
**public key**. `Invalid signature` almost always means those two are not a pair — the app is
holding a *different certificate* from the one PingFederate is signing with.

**Your AuthnRequest going out fine is a strong signal.** It proves the SSO URL, the entity ID and
the ACS URL are all right. The certificate is the one field none of that exercises.

**Let the app diagnose it:**

1. Let the SAML login fail. You land back on the dashboard with a red banner.
2. **Log in with the app's own username and password (Step 0).** The diagnosis contains the
   assertion — somebody's name, email and groups — so it needs a session. The local login is
   deliberately independent of the IdP, which is why it still works when SSO is broken.
3. Click **"Show me why →"**.

You get the fingerprint the IdP signed with, next to the fingerprints you trust:

```
The key the IdP signed with
  SHA-256  5E:EB:5F:E0:01:76:C3:11:C1:0A:7C:7D:D0:49:2E:07:…   ✗ we do NOT trust this key

The keys this app trusts
  SHA-256  9A:F2:9F:70:0B:15:E0:9F:B5:FD:67:80:1B:FE:1A:22:…
```

Two different fingerprints is the root cause, stated in one line. The same panel also cross-checks
**Issuer**, **Audience**, **Destination** and the **Conditions** window, so you find out about the
next failure before you go round again.

> 🔍 **Compare fingerprints, not subject names.** Two certificates can share a subject, an issuer
> and an overlapping validity window and still be different keys — which is exactly what a rotation
> leaves you with. A SHA-256 fingerprint is a hash of the whole certificate, so it cannot lie. It is
> the same value `openssl x509 -fingerprint -sha256 -noout` prints and the same one the Ping console
> shows.

**The four sub-causes, in order of likelihood:**

| Sub-cause | Tell | Fix |
|---|---|---|
| **Key rotation** | worked yesterday, nothing changed on your side | re-import the metadata |
| **Wrong certificate pasted** — Ping's SSL/server cert instead of its **SAML signing** cert | subject looks right (same hostname!), fingerprint does not match | re-import the metadata |
| **Metadata fetched without `?PartnerSpId=…`** | you have the server default, not your connection's cert | re-fetch **with** the parameter |
| **Only one of a rotating pair trusted** | intermittent — some logins work | import metadata (trusts all published certs), or paste **both** PEM blocks into `LAB_SAML_IDP_CERTIFICATE` |

**Diagnose a capture from somewhere else:** grab the `SAMLResponse` with SAML-tracer and POST it to
`/api/saml/diagnose` — same analysis, without needing the failure to happen on this machine.

> ⚠️ **The certificate inside the response is a clue, never a credential.** It came out of the very
> message you are trying to validate, so anyone could have put it there. The app compares it against
> your configured trust anchors and *never* verifies against it. An SP that verified against the
> embedded `KeyInfo` would accept an assertion signed by anybody — see note 34 §9.

---

## 8. Cleanup

1. **PingFederate:** delete the SP connection and the OAuth client. A forgotten test client with
   a known secret is a real finding in a real audit.
2. **Render:** delete both services (free tier, but tidy is a habit).
3. **Local:** `docker rmi pingfed-saml-sp pingfed-oauth-client`, and delete `sp-signing.key`.
4. **Git:** run `git status` and confirm no `.key`, `.crt` or `.env` file is staged.

---

## What you learned

- An app becomes "SSO-enabled" by publishing **three facts** — an identity (entity ID / client ID),
  a **place to receive the answer** (ACS URL / redirect URI), and a **way to verify signatures**
  (certificate / JWKS). Everything else in the console is detail.
- **SAML hands you a signed document; OAuth hands you a token.** The SAML app reads XML and cares
  about audience and time windows. The OAuth app holds a bearer credential and cares about scope,
  expiry and whether it can be read at all.
- **The same login, two trust stories.** Local login means the app checked your password. SSO
  means the app never saw one. Both produce the same session cookie — and that is exactly why
  compromising the IdP compromises every app behind it.
- **A signature is a claim of authorship only one party could make.** The IdP signs the assertion
  with its private key; the app verifies with the public half. `Invalid signature` is nearly always
  "wrong public key", not "someone tampered with your login" — and **importing metadata** removes
  that whole class of failure, because the file carries the certificate the IdP actually uses.
- PingFederate's **Access Token Manager** is the setting that decides whether your APIs can
  validate tokens offline or must call home on every request. That is an architecture decision
  disguised as a dropdown.

## Next

- Wire an actual **resource server** that validates these tokens — start from
  [note 21 — OAuth 2.0 complete reference](../../notes/03-oauth-oidc/21-oauth2-complete-reference.md) §resource servers.
- Compare with [Lab 03](../03-kt-demo-saml-oauth/README.md), which runs the *IdP* side locally in
  Keycloak. Lab 03 teaches you the server; this lab teaches you the app.
- Go deep on the failure you will hit most:
  [note 34 — "Invalid Signature": how SAML signature verification really works](../../notes/02-saml/34-saml-invalid-signature-rca.md).
- Read the assertion and token fields against
  [note 16 — SAML bindings and certificates](../../notes/02-saml/16-saml-bindings-and-certificates.md) and
  [note 22 — OAuth grant types](../../notes/03-oauth-oidc/22-oauth2-grant-types-and-scenarios.md).
