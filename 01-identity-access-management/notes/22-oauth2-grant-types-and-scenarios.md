# OAuth 2.0 grant types & scenarios — every flow, behind the screen

> **Janus's playbook.** The other OAuth notes teach the *theory* ([note 03](03-oauth-oidc-deep-dive.md)), one login *wire-by-wire* ([note 19](19-oauth2-in-practice.md)), and a *look-it-up* card ([note 21](21-oauth2-complete-reference.md)). **This note answers a different question:** *"For each grant type, what does the user actually see — and what is really happening behind the screen at every step?"*
>
> We walk **every grant type** as its own complete flow, then map **real-world scenarios** ("mobile banking app", "cron job", "smart TV", "customer connects a budgeting app") to the right grant. Then a proper **OpenID Connect** section, and a **brief** on **OAuth 2.1**.
>
> **Prereq:** read [note 03](03-oauth-oidc-deep-dive.md) once so the four roles and "front vs back channel" already mean something. **Hands-on:** [Lab 01 — Keycloak as your own IdP](../labs/01-keycloak-idp/README.md).

---

## TL;DR

- A **grant type** (a.k.a. **flow**) is just *the recipe an app follows to get a token from the login server.* Different apps live in different situations, so OAuth gives you different recipes.
- **The one question that picks your grant:** *"Is a human sitting there in a browser, and can my app keep a secret?"* Everything else follows.
- **The living grants:** **Authorization Code + PKCE** (any app with a user), **Client Credentials** (machine-to-machine, no user), **Device Code** (no keyboard), **Refresh Token** (renew without re-login). Three niche ones: **JWT Bearer**, **Token Exchange**, **CIBA**.
- **The dead grants:** **Implicit** and **Password (ROPC)** — both removed in OAuth 2.1. Know *why* they died; you'll be asked to migrate them off.
- **What the user sees** is always tiny (a login page, a consent screen, maybe a code to type). **What happens behind the screen** is where the security lives — that's what this note exposes.

---

## 0. The 30-second recap you need before the flows

Four roles, every flow, no exceptions:

| Role | Plain words | In our examples |
|---|---|---|
| **Resource Owner** | the human who owns the data | **Farhaan** |
| **Client** | the app that wants access | web app, SPA, mobile app, backend job, smart TV |
| **Authorization Server (AS)** | the login server that issues tokens | **PingFederate** at FinCo, **Keycloak** in the lab |
| **Resource Server (RS)** | the API holding the data; accepts tokens | `api.finco.example`, a partner bank's API |

**The single idea that shapes every flow — front channel vs back channel:**

- **Front channel** = messages that ride **through the user's browser** (redirects, URLs). *Assume an attacker can see these.* So the front channel only ever carries **one-time, short-lived, useless-on-their-own** artifacts (an authorization `code`).
- **Back channel** = **direct server-to-server HTTPS** calls the browser never sees. *This is where the valuable stuff moves* — the actual tokens, the client secret.

> **Remember this and 80% of "why is the flow shaped like this?" answers itself:** *codes travel out front where it's dangerous; tokens travel out back where it's safe.* Every grant is a variation on that theme.

Two questions each flow has to answer, kept strictly separate:

1. **Who is the user?** → **Authentication** → that's **OpenID Connect's** job (§8).
2. **What may this app do on their behalf?** → **Authorization** → that's plain **OAuth's** job.

---

## 1. How to choose a grant — the decision tree

Start at the top. The **first honest answer** picks your flow.

```
Is a human sitting there, logging in right now?
│
├─ NO  → It's software acting as itself (cron, microservice, backend).
│        └─▶  CLIENT CREDENTIALS                                   (§3)
│
└─ YES → A human is present.
         │
         Does the device have a browser + keyboard?
         │
         ├─ NO (smart TV, CLI on a server, IoT with no screen)
         │        └─▶  DEVICE AUTHORIZATION (Device Code)          (§4)
         │
         └─ YES (web app, SPA, mobile app, desktop app)
                  └─▶  AUTHORIZATION CODE + PKCE                    (§2)   ← the default for ~everything
                          │
                          └─ later: access token expired, don't want to
                             nag the user to log in again?
                                  └─▶  REFRESH TOKEN                (§5)
```

**Everything else is a special case:**

- One service calls another *on behalf of a user* (delegation chains) → **Token Exchange** (§6).
- A service account proves itself with a signed assertion instead of a shared secret → **JWT Bearer** (§6).
- Login is *started on one device but approved on another* (call-centre agent triggers a push to your phone) → **CIBA** (§6).
- You inherited an app using **Implicit** or **Password (ROPC)** → it's on the deprecation list; plan the migration (§7).

Everything below is one branch of this tree, walked in full.

---

## 2. Authorization Code + PKCE — the default for any app with a user

> **The one to know cold.** Web apps, single-page apps, mobile apps, desktop apps — they *all* use this now. If you only ever internalize one flow, make it this one.

### The scenario
Farhaan opens FinCo's **expense dashboard** in his browser and clicks **"Log in with FinCo SSO."** He wants the app to show his expenses.

### What the user *sees* (the whole visible surface)
1. Clicks **Log in**.
2. The page jumps to the FinCo login screen. He types his password + approves MFA.
3. Maybe a **consent screen**: *"Expense Dashboard wants to: read your profile, read your expenses — Allow?"* (Often skipped for trusted first-party apps.)
4. The page jumps **back** to the dashboard, already logged in. His expenses load.

That's it. Four things he notices. Now the part he *doesn't* see.

### What happens *behind the screen*, step by step

```
 Farhaan's       Expense app            Auth Server (AS)          Expenses API
 browser        (the Client)            PingFederate/Keycloak      (the RS)
    |                 |                        |                        |
 0. |-- open app ---->|                        |                        |
    |                 | invents code_verifier (random secret, kept)     |
    |                 | code_challenge = SHA256(verifier)               |
    |                 | invents state + nonce (random)                  |
    |<-- redirect to /authorize ---------------|                        |
    |                 |                        |                        |
 1. |=== GET /authorize?response_type=code&client_id=...               |
    |     &redirect_uri=...&scope=openid%20profile%20read:expenses     |
    |     &state=XYZ&nonce=ABC&code_challenge=...&method=S256 =========>|
    |                 |                        |                        |
 2. |                 |     user logs in (password + MFA) + consent     |
    |                 |     [ this part is NOT OAuth — it's the AS's    |
    |                 |       own business how it checks the human ]    |
    |                 |                        |                        |
 3. |<== 302 redirect: redirect_uri?code=SHORTCODE&state=XYZ ==========|
    |                 |                        |                        |
 4. |-- browser delivers code to app -------->|                        |
    |                 | check: returned state == XYZ ?  (CSRF guard)    |
    |                 |                        |                        |
 5. |                 |-- POST /token (BACK CHANNEL, browser blind) --->|
    |                 |    grant_type=authorization_code                |
    |                 |    code=SHORTCODE & redirect_uri=...            |
    |                 |    client_id (+secret if confidential)          |
    |                 |    code_verifier=ORIGINAL-SECRET  (PKCE proof)  |
    |                 |<-- 200: access_token + id_token (+ refresh) ----|
    |                 |                        |                        |
 6. |                 | validate id_token (sig, iss, aud, exp, nonce)   |
    |                 | → user is logged in; app creates a local session|
    |                 |                        |                        |
 7. |                 |-- GET /expenses  Authorization: Bearer <access> --------->|
    |                 |<------------------ 200 expense data ----------------------|
```

**Now the "why" behind each step the user never saw:**

| Step | What really happened | Why it's built this way |
|---|---|---|
| 0 | App generates a random **`code_verifier`**, sends only its **hash** (`code_challenge`). | So a thief who later steals the `code` still can't use it — they never saw the original secret. This is **PKCE**. |
| 0 | App generates **`state`** (anti-CSRF) and **`nonce`** (anti-replay). | `state` ties the round-trip to *this* browser session; `nonce` ties the ID token to *this* request. |
| 1 | `response_type=code` says *"give me a code first, not a token."* | The two-step. The code goes out front (exposed); the token comes back out back (safe). |
| 2 | The AS authenticates the human however it likes — password, MFA, passkey. | **OAuth deliberately doesn't specify this.** How you prove you're human is the AS's job, not the protocol's. That's why one AS can require MFA, passkeys, or a fingerprint without changing the flow. |
| 3 | The **code** comes back through the browser. It's **one-time-use** and lives ~30–60 seconds. | Even if it leaks, it's near-worthless: single-use, expiring, and locked to the PKCE verifier. |
| 4 | App checks the **`state`** it gets back equals the one it sent. | If they differ, someone is trying to splice their login into your session — abort. |
| 5 | The **real exchange**, over the **back channel**: code + `code_verifier` (+ client secret) → tokens. | The browser can't see this call, so the tokens are never exposed to it. The AS re-hashes the verifier and checks it matches the challenge from step 0. |
| 6 | App validates the **ID token** — signature, `iss`, `aud` = me, `exp`, `nonce` matches. | This is the *authentication* result. Now the app *knows who logged in*. (See §8.) |
| 7 | App calls the API with **`Authorization: Bearer <access_token>`**. | The access token is the *authorization* — the "valet key" for the API. |

> **The wire-level bytes for this exact flow** — real headers, a decoded JWT, refresh, logout — are in [note 19](19-oauth2-in-practice.md). This note stays at the "what/why per step" altitude so we can cover *all* the grants.

### Public vs confidential — the one fork inside this flow
- **Confidential client** (server-side web app): can keep a **`client_secret`**, so step 5 includes it. PKCE is *still* recommended on top (RFC 9700).
- **Public client** (SPA, mobile, desktop): **cannot** keep a secret (the code ships to the user's device). It sends **no secret** — **PKCE is what protects step 5 instead.** This is the whole reason PKCE exists.

### Where it's used
Every user-facing app you'll ever configure at FinCo: the employee portal, customer web banking, the mobile app, internal SPAs. **If a human logs in, this is the flow.**

### The attacks that bite here (each with its defense — Law 9)
| Attack | Where it strikes | Defense |
|---|---|---|
| **redirect_uri manipulation** | step 3 — code delivered to attacker's server | AS enforces **exact-match** registered URIs; no wildcards |
| **CSRF on the callback** | step 4 | generate + verify **`state`** |
| **Authorization-code interception** | step 3→5 | **PKCE** (thief lacks `code_verifier`) + one-time short-lived codes |
| **ID-token replay/injection** | step 6 | validate **`nonce`** + `aud` |

---

## 3. Client Credentials — software acting as itself (no human)

> **The machine-to-machine flow.** No user, no browser, no consent screen — because there's nobody to consent. The app *is* the identity.

### The scenario
Every night at 2 a.m., FinCo's **reconciliation service** wakes up and pulls transaction records from an internal **ledger API**. No human is involved. The service needs to prove *"I am the reconciliation service, and I'm allowed to read the ledger."*

### What the user sees
**Nothing.** There is no user. This runs on a server in the dark.

### What happens behind the screen

```
 Reconciliation service            Auth Server (AS)            Ledger API
   (the Client — confidential)     PingFederate/Keycloak        (the RS)
        |                               |                            |
 1.     |-- POST /token (back channel) ->|                            |
        |   grant_type=client_credentials                            |
        |   scope=read:ledger                                        |
        |   + credentials:                                           |
        |     • client_id + client_secret,  OR                       |
        |     • private-key JWT (RFC 7523), OR                       |
        |     • mTLS client cert (RFC 8705)  ← strongest             |
        |<-- 200 { access_token, expires_in }  (NO refresh token) ---|
        |                               |                            |
 2.     |-- GET /ledger  Authorization: Bearer <access_token> ------------>|
        |<--------------------- 200 ledger data --------------------------|
        |                               |                            |
 3.     |  token expired?  → just do step 1 again (it's a machine,   |
        |                     it has its own credentials)            |
```

**The "why" that makes this flow different from §2:**

- **There is no `/authorize` step and no browser.** `/authorize` exists to *put a login screen in front of a human*. No human → skip it entirely → go straight to `/token`.
- **The client authenticates as *itself*, not on behalf of a user.** The token's subject is the *app*, not a person.
- **No refresh token is issued.** Why would you need one? The service already holds its own credentials — it can just re-run step 1 whenever the token expires. Refresh tokens exist to *avoid re-prompting a human*; there's no human here.
- **The credential is the crown jewel.** A leaked `client_secret` = anyone can *be* your service. This is exactly why **PAM and secret rotation** exist ([note 11](11-pam-deep-dive.md)), and why the strong options — **private-key JWT** or **mTLS** — beat a shared secret.

### Where it's used
- Backend jobs, cron tasks, batch pipelines.
- **Microservice-to-microservice** calls (service A → service B).
- Any daemon, worker, or integration with **no interactive user**.

### FinCo angle
This is *everywhere* in your k8s estate. Note the two coexisting layers:
- **Transport identity** (which pod is this?) = service-mesh **mTLS** ([note 06](06-tls-https-mtls.md)).
- **API-level authorization** (may this service read the ledger?) = **client credentials** access tokens.
They stack; they don't replace each other.

### Attack & defense
| Attack | Defense |
|---|---|
| **Client-secret leakage** (secret in a repo, config map, image layer) | vault the secret + **rotate** it; prefer **private-key JWT / mTLS** so there's no shared secret to steal |
| **Over-scoped service token** (`read:ledger` app also holds `write:payments`) | least-privilege scopes per service; audit which services hold write scopes (IGA for apps, [note 12](12-iga-deep-dive.md)) |

---

## 4. Device Authorization (Device Code) — for things with no keyboard

> **The "go to this URL and type this code" flow.** Built for devices where typing a password is painful or impossible.

### The scenario
Farhaan installs a **FinCo dashboard app on his smart TV** (or logs into a **CLI tool** on a headless server). There's no comfortable keyboard, and you should *never* type a corporate password into a TV remote.

### What the user sees
1. The TV shows: **"To sign in, go to `finco.example/device` on your phone and enter code: `WDJB-MJHT`."**
2. He pulls out his **phone** (which *does* have a browser + password manager + MFA), visits the URL, types the code, logs in normally, approves.
3. The TV screen updates by itself: **"You're signed in."**

Two devices, one login. Notice the clever part: **the sensitive login happens on the trusted device (phone), not the awkward one (TV).**

### What happens behind the screen

```
   Smart TV (the Client)          Auth Server (AS)         Farhaan's phone
        |                              |                         |
 1.     |-- POST /device_authorization ->|                        |
        |<-- { device_code,             |                         |
        |      user_code: "WDJB-MJHT",  |                         |
        |      verification_uri,        |                         |
        |      interval: 5,             |                         |
        |      expires_in: 900 } -------|                         |
        |                              |                         |
 2.  TV shows user_code + URL to the human                        |
        |                              |                         |
 3.     |                              |<-- user opens verification_uri,
        |                              |    types WDJB-MJHT, logs in,
        |                              |    approves consent -----|
        |                              |                         |
 4.     |-- POST /token (polling) ----->|                         |
        |   grant_type=device_code      |  "not yet..."           |
        |   device_code=...             |                         |
        |<-- 400 authorization_pending -|   (TV keeps polling      |
        |        ...every 5s...         |    every `interval` s)   |
        |                              |                         |
 5.     |-- POST /token (polling) ----->|  (after human approves) |
        |<-- 200 { access_token,        |                         |
        |          refresh_token } -----|                         |
```

**The "why" behind the design:**

- **Two separate channels for two devices.** The TV gets a `device_code` (its secret handle); the human gets a short, typeable `user_code`. They're linked at the AS.
- **The TV polls.** It has no way to receive a redirect (it's not a browser), so it repeatedly asks `/token` *"done yet?"* every `interval` seconds until the human finishes. `authorization_pending` means "keep waiting."
- **The password never touches the untrusted device.** The TV never sees Farhaan's credentials — those go into his phone. That's the security win.

### Where it's used
Smart TVs, streaming sticks, game consoles, IoT devices, **CLI tools** (`az login`, `gh auth login`, `kubectl` plugins), printers, ticket kiosks.

### Attack & defense (Law 9)
| Attack | How it works | Defense |
|---|---|---|
| **Device-code phishing** | Attacker starts a device flow, then messages the victim *"enter this code to verify your account."* Victim logs in → the token is minted for the **attacker's** device. | User education (never enter a code someone sent you); **short `expires_in`**; conditional-access / restrict device flow to devices that actually need it; watch for device-flow starts from odd IPs. |

> This one is rising fast in real phishing campaigns precisely *because* MFA doesn't stop it — the victim genuinely completes their own MFA, just for the attacker's session. Pair with §8's `authorization_details` awareness.

---

## 5. Refresh Token — staying logged in without nagging the human

> **Not a login flow — a *renewal* flow.** It rides on top of §2. Its whole job: get a fresh access token when the old one expires, *without* dragging the user back through a login screen.

### The scenario
Farhaan logged into the mobile banking app this morning (via §2). Access tokens live ~15 minutes for safety. It's now 3 p.m. and he opens the app again. He should **not** have to re-authenticate — but the app's access token expired hours ago.

### What the user sees
**Nothing** — the app just works. That seamlessness *is* the refresh token doing its job silently.

### What happens behind the screen

```
   Mobile app (the Client)              Auth Server (AS)
        |                                    |
   ... hours earlier, app was given a refresh_token during §2 ...
        |                                    |
 1.  access token expired; API returned 401  |
        |                                    |
 2.     |-- POST /token (back channel) ------>|
        |   grant_type=refresh_token          |
        |   refresh_token=OLD-RT               |
        |   (+ client auth if confidential)   |
        |<-- 200 { access_token: NEW,         |
        |          refresh_token: NEW-RT } ----|   ← ROTATION: old RT now dead
        |                                    |
 3.  app retries the API call with the NEW access token → 200
```

**The "why," and the one rule that matters:**

- **Refresh tokens are password-equivalent.** They're long-lived and can mint access tokens repeatedly. Treat them like a password: protected storage, never in a URL, never logged.
- **Rotation on every use** is the modern default. Each refresh returns a **new** refresh token and **invalidates the old one**. So a stolen refresh token has a short useful life.
- **Reuse detection is the trap that catches thieves.** If an *already-rotated* (old) refresh token is ever presented again, the AS knows one of two copies is a thief → it **revokes the entire token family**, forcing a real re-login. Legitimate app and attacker can't both hold the "current" token.

### Where it's used
Every long-lived user session: mobile apps, desktop apps, "keep me logged in" web sessions. **Public clients** (mobile/SPA) that get refresh tokens *must* use rotation — for SPAs, many teams now avoid storing refresh tokens in the browser at all (backend-for-frontend pattern instead).

### Attack & defense
| Attack | Defense |
|---|---|
| **Refresh-token theft** → silent long-term access, bypasses MFA (auth already happened), often survives a password reset | **rotation + reuse detection** (revoke the family on reuse); short refresh lifetimes; **sender-constrain** the token (DPoP/mTLS) so a stolen copy is useless off the original device |

---

## 6. The three niche grants (know they exist, recognize them)

You won't wire these up weekly, but naming them correctly marks you as someone who actually understands OAuth.

### 6a. JWT Bearer (RFC 7523) — prove yourself with a signed assertion
- **What it is:** instead of a shared `client_secret`, a service holds a **private key** and signs a short **JWT assertion** ("I am service X, at time T"). It presents that JWT to `/token` and gets an access token.
- **Why it's better than a secret:** the private key never leaves the service; there's no shared secret sitting in the AS's database to leak. This is the "private-key JWT" client-auth option mentioned in §3.
- **Where you'll see it:** Google service accounts, many enterprise service-to-service setups, SAML-to-OAuth bridges (trade a SAML assertion for an access token).

### 6b. Token Exchange (RFC 8693) — delegation down a chain of services
- **The scenario:** Farhaan calls **Service A** (with his user token). Service A needs to call **Service B** *on Farhaan's behalf* — but A's token has the wrong audience for B, and you don't want to hand B a token that can do *everything*.
- **What it does:** A presents its token to the AS and asks to **exchange** it for a *new, narrower* token — scoped down, re-audienced for B, still carrying "acting for Farhaan."
- **Why it matters:** it keeps **least privilege** intact across a call chain instead of passing one god-token everywhere. Also underpins **impersonation** ("act-as") and **delegation** ("on-behalf-of") patterns in microservice meshes.

### 6c. CIBA (Client-Initiated Backchannel Authentication) — login started on a *different* device
- **The scenario:** Farhaan calls FinCo's phone support. The agent needs him to authenticate. Instead of reading codes aloud, the agent's system triggers a **push notification to Farhaan's phone**: *"Approve login?"* He taps approve; the agent's screen unlocks his account.
- **What's different:** the device that *starts* the login (agent's terminal) is **not** the device that *approves* it (customer's phone). There's no browser redirect at all — it's a backchannel request + an out-of-band approval.
- **Where you'll see it:** call centres, high-assurance banking (it's part of the **FAPI** open-banking stack), payment confirmations.

---

## 7. The dead grants — Implicit & Password (ROPC), and *why* they died

You'll still find these in old configs. Your job is to recognize them and migrate off. **Both are removed in OAuth 2.1.**

### 7a. Implicit (`response_type=token`) — ☠️ deprecated
- **What it did:** the AS returned the **access token directly in the URL fragment** (`#access_token=...`) after login — no back-channel exchange. Built for old SPAs that (people wrongly believed) couldn't do a back-channel call.
- **Why it died:**
  - The token lands in the **browser URL** → leaks into **history, referrer headers, server logs, extensions**.
  - **No PKCE possible** (no code to protect).
  - No way to deliver a refresh token safely.
- **What replaced it:** **Authorization Code + PKCE** (§2). SPAs absolutely *can* do the back-channel `/token` call — the original assumption was just wrong.

### 7b. Resource Owner Password Credentials / ROPC (`grant_type=password`) — ☠️ deprecated
- **What it did:** the app **collects the user's actual username + password** and sends them to `/token` directly.
- **Why it died — it resurrects the exact problem OAuth exists to kill:**
  - The app *sees the user's password.* That's the anti-pattern OAuth was invented to eliminate (the valet-key point in [note 03](03-oauth-oidc-deep-dive.md) §1).
  - It **breaks MFA, passkeys, and any modern login** — there's no place in the flow for a second factor or a redirect to the IdP.
  - It trains users to type their corporate password into random app forms — a phisher's dream.
- **What replaced it:** **Authorization Code + PKCE** (§2), or **Client Credentials** (§3) if there was never really a user.

> **Interview-grade one-liner:** *"Implicit put the token in the URL; ROPC put the password in the app. Both violate OAuth's core promise, so 2.1 removes them."*

---

## 8. OpenID Connect — the authentication layer, done properly

Everything above is **authorization** ("what may this app do?"). None of it reliably tells the app **who the user is**. That's a different question, and answering it with a bare access token is a classic security hole (an access token proves *access*, not *identity* — it could be a token minted for a different app and replayed).

**OpenID Connect (OIDC)** is the thin, standard layer that adds real **authentication** on top of OAuth. Same flow (§2), same endpoints — three additions.

### What OIDC adds (and how you switch it on)

1. **The `openid` scope — the switch.** Add `scope=openid` to a normal Authorization Code + PKCE request and the AS returns an **ID token** alongside the access token. That single word turns "OAuth authorization" into "OIDC authentication."

2. **The ID token (always a JWT) — the answer to "who?"** A signed statement, minted **for your client app** (not for an API), saying *who logged in, when, and how.*

3. **Standard plumbing** so you don't hand-wire metadata like the SAML days:
   - **`/.well-known/openid-configuration`** — the **discovery document**: one JSON with every endpoint + the signing-key location. *Your first stop integrating any provider.*
   - **`/userinfo`** — call with the access token to fetch extra user claims.
   - **JWKS URI** — the AS's **public signing keys**, so your app can verify ID-token signatures and auto-pick-up key rotation.

### The vocabulary swap (same machines, OIDC names)
- **Client → Relying Party (RP)** — the app *relying on* the login.
- **Authorization Server → OpenID Provider (OP)** — the thing *providing* identity.
- Same PingFederate/Keycloak boxes; OIDC just renames the roles.

### The ID-token claims you must validate

| Claim | Meaning | The gotcha that bites |
|---|---|---|
| `iss` | who issued it | must **exactly** match the discovery `issuer` string |
| `sub` | stable unique user ID | **key on `sub`, never email** — emails change and get reassigned |
| `aud` | who it's for = your `client_id` | reject if it isn't you → stops **token substitution** |
| `exp` / `iat` | expiry / issued-at | short-lived; the ID token is consumed **once**, at login |
| `nonce` | echo of the random value your app sent in step 0 | must match → blocks **replayed/injected** ID tokens |
| `auth_time` | when the human actually authenticated | enforce re-auth for sensitive actions (**step-up auth**) |
| `acr` / `amr` | assurance level / methods used (e.g. `mfa`, `pwd`) | this is **how you prove MFA happened** — auditors *will* ask |

### The validation checklist (the part that stops attacks)
1. **Signature** verifies against a JWKS key selected by the token's `kid`.
2. **`alg` is on an allowlist** (e.g. RS256/ES256) — **reject `none`**; don't let the token's own header choose the verification path (stops **algorithm-confusion**).
3. **`iss`** = expected issuer. 4. **`aud`** = my `client_id`. 5. **`exp`/`nbf`** valid (allow small clock skew). 6. **`nonce`** matches what you sent.

*(Full JWT internals + the `alg:none` and RS256→HS256 attacks are in [note 03](03-oauth-oidc-deep-dive.md) §9 and [04-cryptography](../../04-cryptography/README.md) §5–6.)*

### The #1 confusion, one more time
- **ID token → your client app.** Answers *"who is the user?"* **Never send it to an API.**
- **Access token → the API.** Answers *"what may the bearer do?"* **Never parse it in your client to identify the user.**

Mixing these up is both the most common OAuth **bug** and a frequent **vulnerability**.

### OIDC logout — the three mechanisms (because "log me out of everything" is hard)
- **RP-initiated:** app redirects to the `end_session_endpoint` → the AS session dies.
- **Front-channel:** AS loads hidden iframes to each app's logout URL (fragile — dies when browsers block third-party cookies).
- **Back-channel:** AS POSTs a signed **logout token** server-to-server to each app (reliable — the modern choice).

---

## 9. Scenario gallery — real situations mapped to flows

The whole point of the grants is matching them to reality. Here's the lookup table you'll actually use, then a couple walked end-to-end.

| Real-world scenario | Client type | Grant | Why |
|---|---|---|---|
| Employee logs into an **internal web dashboard** (server-rendered) | confidential | **Auth Code + PKCE** | human + browser; server can hold a secret |
| Customer uses the **mobile banking app** | public | **Auth Code + PKCE** (+ Refresh) | human + browser; can't keep a secret → PKCE; refresh for "stay logged in" |
| A **single-page app** (React dashboard) | public | **Auth Code + PKCE** | same as mobile; **never Implicit** anymore |
| **Nightly cron** pulls from an internal API | confidential | **Client Credentials** | no human at all |
| **Microservice A → Microservice B** (service's own identity) | confidential | **Client Credentials** | machine acting as itself |
| **Microservice A → B, on behalf of the logged-in user** | confidential | **Token Exchange** | preserve "acting for Farhaan" + least privilege down the chain |
| **Smart TV / CLI / IoT** login | public | **Device Code** | no keyboard; do the real login on a phone |
| **Call-centre agent** needs the customer to approve a login | — | **CIBA** | login started on one device, approved on another |
| Customer **connects a third-party budgeting app** to their bank (open banking) | confidential (FAPI-hardened) | **Auth Code + PKCE + PAR + sender-constrained tokens** | money-moving API → maximum hardening (PSD2/FAPI mandates it) |

### Walked end-to-end: "Customer connects a budgeting app to their FinCo account" (open banking)
This is the scenario that makes OAuth *click* — it's literally the problem OAuth was invented for.

1. **What the user sees:** in the budgeting app, taps *"Connect your FinCo account."* Gets bounced to FinCo's real login, authenticates + MFA, sees consent: *"Budgeteer wants to: view your account balance and transactions (read-only, 90 days) — Allow?"* Approves, bounces back. The budgeting app now shows their balance.
2. **Behind the screen:** it's **Auth Code + PKCE** (§2) with open-banking hardening — **PAR** (the app pushes the request to the AS server-side first, so the browser only carries a `request_uri` handle) and **sender-constrained tokens** (bound to the app's key, so a stolen token is useless).
3. **The magic the user got:** the budgeting app **never saw the bank password.** It holds a **scoped, expiring, revocable** token — read-only, transactions only, no payments. The customer can revoke it anytime from the bank's "connected apps" screen. *That is the entire reason OAuth exists* — the valet key, not the master key ([note 03](03-oauth-oidc-deep-dive.md) §1).

### Walked end-to-end: "Two microservices talk at 2 a.m." (no user)
1. **What anyone sees:** nothing — it's a background job.
2. **Behind the screen:** **Client Credentials** (§3). The reconciliation service authenticates to the AS with a **private-key JWT or mTLS cert**, gets an app-identity access token scoped `read:ledger`, calls the ledger API, done. No `/authorize`, no browser, no refresh token.
3. **The security that matters:** the service's *credential* is the target — vaulted, rotated, ideally keyless (mTLS). This is where OAuth meets **PAM** ([note 11](11-pam-deep-dive.md)).

---

## 10. OAuth 2.1 — briefly (what you actually need to say)

**OAuth 2.1 is not a new protocol.** It's a **consolidation**: it takes OAuth 2.0 (RFC 6749) plus years of hard-won **security best-practice** (the Security BCP, RFC 9700) and folds them into one cleaner, safer document — so new developers land on the secure path by default instead of reading a 2012 spec and repeating old mistakes. *(As of 2026 it's still a finalizing IETF draft, but every major provider already implements its rules.)*

**What OAuth 2.1 bakes in (all things this note already told you to do):**

- ✅ **PKCE is mandatory** for the Authorization Code flow — for **all** clients, public *and* confidential.
- ❌ **Implicit grant removed.**
- ❌ **Password (ROPC) grant removed.**
- ✅ **Exact-match redirect URIs** required — no wildcards, no pattern matching.
- ✅ **Refresh tokens for public clients must be sender-constrained *or* rotated** (one-time-use).
- ❌ **Bearer tokens in query strings forbidden** (they leak via logs/history) — use the `Authorization` header.

> **The one-liner:** *"OAuth 2.1 = OAuth 2.0 minus the dangerous parts (Implicit, ROPC) plus the safe defaults made mandatory (PKCE everywhere, exact redirects, no tokens in URLs)."* If you've been building to this note, **you're already writing OAuth 2.1.**

---

## 11. Why you care at FinCo (Law 8)

- **Every grant maps to a real ticket.** `invalid_redirect_uri` after a deploy (§2 exact-match), "SSO works but the API 401s" (wrong `aud` / stale JWKS — §8), "the app keeps logging me out" (refresh rotation misconfig — §5), "which apps hold write scopes?" (auditor question — §3/§9).
- **PingFederate is the AS** in every diagram here — `/authorize`, `/token`, `/device_authorization`, JWKS, introspection are endpoints your team operates ([note 18](18-pingfederate-explained.md)).
- **Client Credentials + secret custody is a PAM problem.** Service-account secrets and signing keys are crown jewels ([note 11](11-pam-deep-dive.md)); a forged-issuer scenario (stolen AS signing key) is the nightmare tier.
- **Open banking (PSD2/FAPI)** is §9's third-party-app scenario with the hardening dial turned to max — PKCE + PAR + sender-constrained tokens — *because the API behind the token moves money.*
- **See it for yourself (Law 12):** you can run §2 and §3 for real with the copy-paste curl in [note 19](19-oauth2-in-practice.md), against your own Keycloak from [Lab 01](../labs/01-keycloak-idp/README.md).

---

## What you learned

- A **grant type is a recipe**, and one question picks it: *is a human present, and can the app keep a secret?* — the decision tree in §1.
- **Every living grant, walked behind the screen:** Auth Code + PKCE (§2), Client Credentials (§3), Device Code (§4), Refresh (§5), plus the niche three (§6) — and **why** each is shaped the way it is (front vs back channel, human-or-not).
- **Why Implicit and ROPC are dead** (§7) — token-in-URL and password-in-app both break OAuth's core promise.
- **OIDC** (§8) is the authentication layer: the `openid` scope, the ID token and its claims, and the validation checklist that stops the attacks.
- **OAuth 2.1** (§10) just makes the safe path the default — if you built to this note, you're already there.

## Next

- **See the bytes:** [note 19 — OAuth 2.0 in practice](19-oauth2-in-practice.md) traces §2 wire-by-wire.
- **Look it up fast:** [note 21 — the complete reference card](21-oauth2-complete-reference.md) (endpoints, 15 attacks+defenses, RFC 9700 checklist).
- **Do it:** [Lab 01 — Keycloak as your own IdP](../labs/01-keycloak-idp/README.md) — mint real tokens with these grants.
- **Then contrast with the old world:** [note 02 — SAML 2.0 deep dive](02-saml-deep-dive.md) (same SSO job, XML/2005-era).

*— Janus 🔐*
</content>
</invoke>
