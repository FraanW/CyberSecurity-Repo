# OAuth 2.0 in practice — one login, every byte explained

> **Janus's wire-level walkthrough.** Note [03](03-oauth-oidc-deep-dive.md) taught you the *theory* — roles, grant types, PKCE, ID vs access tokens. **This note is the practice companion.** We take ONE realistic login at FinCo and show you **every single HTTP request and response on the wire**, then explain "what just happened and why" in plain words. When you finish, you'll be able to read a real OAuth trace in Burp or browser DevTools and know exactly what each byte is doing.
>
> **Prereq:** skim [note 03](03-oauth-oidc-deep-dive.md) first (this note assumes you know the four roles and that "auth code + PKCE" is the default). **Hands-on:** [Lab 01 — Keycloak as your own IdP](../labs/01-keycloak-idp/README.md).

---

## TL;DR

- We follow **one running scenario**: FinCo's **expense dashboard** (a single-page app) logs Farhaan in and calls the **expenses API** on his behalf.
- You'll see the full **Authorization Code + PKCE** flow wire-by-wire: PKCE setup → `/authorize` → login/consent → the `?code=` redirect → `/token` → decode the JWT → call the API → refresh → logout.
- Every step calls out **the attack that bites there** and **the defense that stops it** (stolen code → PKCE, loose redirect → exact match, missing `state` → CSRF, `alg:none` → allowlist, refresh theft → rotation).
- Two bonus sections: **service-to-service** (`client_credentials`, and how it coexists with your k8s mTLS at FinCo), and **copy-paste curl** to run it for real against Keycloak.

---

## The cast (our one scenario)

| Thing | Value in this note | What it is |
|---|---|---|
| **User** (Resource Owner) | **Farhaan** | The person logging in |
| **The app** (Client) | `https://expenses.finco.example` | Expense dashboard — a **single-page app (SPA)**, so a **public client** (can't keep a secret) |
| **The API** (Resource Server) | `https://api.finco.example` | Holds the expense data the app wants |
| **The login server** (Authorization Server, "AS") | `https://sso.finco.example` | Issues tokens. **At FinCo this role is played by PingFederate; in the lab it's Keycloak.** |

> **Say it once, remember it:** wherever this note says "the AS" or `sso.finco.example`, that's **PingFederate** in your real FinCo world and **Keycloak** in the lab. Same protocol, same wire messages — only the product logo changes.

---

## The whole flow at a glance (sequence diagram)

```
 Farhaan's       Expense SPA           Auth Server (AS)         Expenses API
 browser      expenses.finco.example   sso.finco.example       api.finco.example
    |                 |                        |                       |
 0. |-- open app ---->| (no token yet)         |                       |
    |                 |  make code_verifier                            |
    |                 |  code_challenge=S256(verifier)                 |
    |                 |                        |                       |
 1. |<-- 302 to AS's /authorize (code_challenge, state, nonce, scope) -|
    |----------------- GET /authorize -------->|                       |
    |                 |                    2. login (pwd + MFA)         |
    |                 |                       + consent                 |
    |<-- 302 to redirect_uri?code=AUTH_CODE&state=xyz ------------------|
    |                 |                        |                       |
 3. |-- browser hands code to SPA ----------->|  (front channel done)  |
    |                 |                        |                       |
    |                 |-- POST /token (code + code_verifier) --------->|
    |                 |<-- access_token + id_token + refresh_token ----|
    |                 |    (back channel — attacker can't see this)     |
    |                 |                        |                       |
 4. |                 |-- GET /v1/expenses  Authorization: Bearer ----->|
    |                 |                        |  verify sig via JWKS,  |
    |                 |                        |  check iss/aud/exp/scope|
    |                 |<------------------ 200 OK  [expenses] ----------|
    |                 |                        |                       |
 5. |                 |  ...access token expires (401)...              |
    |                 |-- POST /token grant_type=refresh_token ------->|
    |                 |<-- NEW access_token + NEW refresh_token -------|
    |                 |                        |                       |
 6. |-- log out ----->|-- POST /revoke (refresh_token) -------------->|
```

Front channel = through the browser (exposed). Back channel = SPA-to-AS direct call (attacker can't observe). Keep that split in your head — it's the reason the flow is shaped the way it is.

---

## Step 0 — App loads, no token yet → build PKCE

Farhaan opens `https://expenses.finco.example`. The SPA has no token, so before it sends him anywhere it prepares **PKCE** (Proof Key for Code Exchange, RFC 7636).

It generates two values in the browser:

```
code_verifier  = 3q7Xw9Zt2Lp8Kd1Rf6Yb0Nc4Vs5Mh_aJ2Gk3Qe1Tn0Ux   (random, 43–128 chars, kept secret in memory)
code_challenge = E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM       (= BASE64URL( SHA256(code_verifier) ))
```

**What just happened and why.** The app invented a random secret (`code_verifier`) and computed its **SHA-256 hash**, base64url-encoded (`code_challenge`). The one-sentence math: `code_challenge = base64url(sha256(code_verifier))` — a hash is one-way, so anyone who sees the challenge **cannot** work backward to the verifier.

The app will show the **challenge** to the world (in the URL, step 1) but keep the **verifier** hidden in memory. At the end (step 3b) it proves it knows the verifier. That's the whole trick.

> **Gotcha (attack → defense).** For a **public client** like this SPA there's no client secret to prove "I'm the app that started this login." A thief who steals the authorization code from the browser could try to redeem it. **PKCE is the defense:** the code is useless without the verifier, and the verifier never left Farhaan's browser. Always use `S256` — the older `plain` method sends the verifier as-is and defeats the purpose.

---

## Step 1 — The authorization request (`/authorize`)

The SPA redirects the browser to the AS. This is a plain `GET` (it's a navigation), so everything rides in the query string:

```http
GET /as/authorize?response_type=code
    &client_id=expense-dashboard-spa
    &redirect_uri=https%3A%2F%2Fexpenses.finco.example%2Fcallback
    &scope=openid%20profile%20expenses%3Aread%20expenses%3Awrite
    &state=Kx8f2aQ9pLm3
    &nonce=Vt5rZ0nB7cYw
    &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
    &code_challenge_method=S256 HTTP/1.1
Host: sso.finco.example
```

**Every parameter, explained:**

| Parameter | Value here | What it means / why it matters |
|---|---|---|
| `response_type` | `code` | "Give me an **authorization code** first, not a token." The secure two-step (RFC 6749 §4.1). |
| `client_id` | `expense-dashboard-spa` | Which registered app this is. Public — not a secret. |
| `redirect_uri` | `https://expenses.finco.example/callback` | Where to send the code back. **Must exactly match** a value pre-registered in the AS. |
| `scope` | `openid profile expenses:read expenses:write` | What we're asking for (see the scope design note below). |
| `state` | `Kx8f2aQ9pLm3` | Random anti-**CSRF** value. The app remembers it and checks it comes back unchanged. |
| `nonce` | `Vt5rZ0nB7cYw` | Random value bound into the **ID token** to stop token replay (OIDC Core). |
| `code_challenge` | `E9Mel...w-cM` | The PKCE public half from step 0. |
| `code_challenge_method` | `S256` | Tells the AS the challenge is a SHA-256 hash (not `plain`). |

**Designing the scopes (this is a real FinCo skill).** Scopes are the "valet key" permissions from note 03. We asked for four, each with a job:

- `openid` — the switch that makes this **OIDC** (we want to know *who* logged in, so we get an ID token). Without it, this is plain OAuth.
- `profile` — lets us read Farhaan's name/username for the UI.
- `expenses:read` — lets the app **read** expense data from the API.
- `expenses:write` — lets the app **submit/edit** expenses.

> **Gotcha (attack → defense): overly-broad scopes.** If the dashboard only ever *shows* expenses, asking for `expenses:write` is **scope creep** — extra blast radius if the token leaks. **Defense = least privilege:** request only what the screen needs. In fintech this is audit-relevant (SOX/PCI love "why does this app have write access?"). Design scopes narrow; add more only when a feature needs them.

> **Gotcha (attack → defense): `redirect_uri` manipulation.** An attacker crafts a login link with `redirect_uri=https://evil.example/steal` hoping the code lands on their server. **Defense = exact-match registration:** the AS only accepts redirect URIs registered ahead of time, character-for-character (no wildcards, no "starts-with"). You'll feel this defense work in [Lab 01 §11](../labs/01-keycloak-idp/README.md) when Keycloak refuses a wrong URI.

---

## Step 2 — Login + consent at the AS

The browser is now on `sso.finco.example`. The AS runs its own login: Farhaan types his password and completes **MFA** (at FinCo this is your Strong Customer Authentication / phishing-resistant MFA — think FIDO2/passkey, not SMS). None of this touches the SPA — the app never sees the password. That's the entire point of federated login.

Then the AS may show a **consent screen**: *"Expense Dashboard wants to: read your profile, read expenses, submit expenses — Allow?"*

**When is consent skipped?** For **first-party apps** — apps the company owns, like this internal dashboard — admins usually **pre-consent** the client, so Farhaan is never nagged. Consent screens mostly matter for **third-party** apps ("Login with FinCo" used by an outside vendor), where the user really is granting an external party access. At FinCo your internal PingFederate clients will typically be pre-consented; a partner integration would not be.

---

## Step 3a — The redirect back with the code

Login succeeds. The AS sends the browser back to the registered `redirect_uri` with the code attached:

```http
HTTP/1.1 302 Found
Location: https://expenses.finco.example/callback?code=SplxlOBeZQQYbYS6WxSbIA&state=Kx8f2aQ9pLm3
```

**What just happened and why.** The AS handed back a short **authorization code** (`SplxlOBeZQQYbYS6WxSbIA`) plus the **same `state`** the app sent in step 1.

- **Why the code is short-lived and single-use.** It travels through the browser (front channel), so it's the most exposed thing in the flow. The AS gives it a tiny lifetime (often ~30–60 seconds) and **burns it after one use**. Even if someone grabs it, it's likely already dead — and PKCE kills it anyway.
- **Why the app must check `state`.** The app compares the returned `state` to the one it generated. If they don't match, it **aborts**.

> **Gotcha (attack → defense): CSRF / login-CSRF via the callback.** Without `state`, an attacker can trick your browser into completing a login with *the attacker's* code, silently logging you into *their* account (so your actions land in their session) — or the reverse. **Defense = generate a random `state`, store it, and verify it on return.** No match → reject. This is why `state` is not optional.

---

## Step 3b — Exchange the code for tokens (`/token`, back channel)

Now the SPA makes a **direct** `POST` to the AS — this does **not** go through the browser address bar, it's a background fetch. This is the secure back channel.

```http
POST /as/token HTTP/1.1
Host: sso.finco.example
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https%3A%2F%2Fexpenses.finco.example%2Fcallback
&code_verifier=3q7Xw9Zt2Lp8Kd1Rf6Yb0Nc4Vs5Mh_aJ2Gk3Qe1Tn0Ux
&client_id=expense-dashboard-spa
```

**What just happened and why.** The app trades the code for tokens. It sends:

- `grant_type=authorization_code` — "I'm redeeming a code."
- `code` — the code from step 3a.
- `redirect_uri` — **must match** the one from step 1 (the AS re-checks).
- `code_verifier` — the **PKCE proof**. The AS computes `sha256(verifier)` and checks it equals the `code_challenge` from step 1. Match → proceed. No match → reject.
- `client_id` — which app.

**Public vs confidential client — how the app proves who it is:**

| Client type | Example | How it authenticates at `/token` |
|---|---|---|
| **Public** (our SPA) | Browser SPA, mobile app | **No secret** (a browser can't hide one). PKCE `code_verifier` is what protects the exchange. |
| **Confidential** | A backend web app / service | A real secret. Options, weakest→strongest: `client_secret` (shared password) → **`private_key_jwt`** (app signs a JWT with its private key, RFC 7523) → **mTLS** (client certificate, RFC 8705). |

> At FinCo, **confidential clients should prefer `private_key_jwt` or mTLS over a shared `client_secret`** — no shared password to leak or rotate, and it lines up with the certificate-based trust your k8s mesh already uses. Shared secrets are exactly what PAM exists to vault and rotate.

---

## Step 3c — The token response

The AS answers the back-channel POST with JSON:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store

{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Ij2024-fk"... (JWT, decoded below),
  "id_token":     "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVC...FAKE-SIGNATURE",
  "refresh_token":"rt_8f3aQ.opaque.4b1c9d7e2f...NOT-A-JWT",
  "token_type":   "Bearer",
  "expires_in":   900,
  "scope":        "openid profile expenses:read expenses:write"
}
```

| Field | What it's for |
|---|---|
| `access_token` | The **API key** for this session. Send it to `api.finco.example`. Here it's a JWT (decoded next). |
| `id_token` | **Always a JWT.** Tells *the SPA* who logged in (name, `sub`). **Never send this to the API.** |
| `refresh_token` | Opaque, long-lived. Used to get a **new** access token without re-login. Treat like a password. |
| `token_type` | `Bearer` — "whoever bears this token may use it." (So don't let anyone else bear it.) |
| `expires_in` | Access token lifetime in seconds. `900` = 15 min — short on purpose (see step 5). |

> **Note the differences from note 03 §4:** the **access token** is for the API, the **ID token** is for the app, the **refresh token** is for the AS. Three tokens, three audiences. Mixing them up is the #1 OAuth bug.

### Decode the access token (JWT)

A JWT is three base64url parts: `header.payload.signature`. Decoding the first two parts of our access token:

```json
// HEADER  — how it's signed
{
  "alg": "RS256",              // RSA + SHA-256 signature (asymmetric)
  "typ": "at+jwt",             // it's an access token JWT (RFC 9068)
  "kid": "2024-fk"             // which AS signing key to verify with (look this up in JWKS)
}
```

```json
// PAYLOAD — the claims
{
  "iss": "https://sso.finco.example",         // issuer — who minted this token
  "sub": "u_farhaan_8842",                     // subject — stable user id (NOT the email)
  "aud": "https://api.finco.example",          // audience — who this token is FOR (the expenses API)
  "azp": "expense-dashboard-spa",              // authorized party — which client requested it
  "exp": 1751970900,                           // expiry (unix time) — after this it's dead
  "iat": 1751970000,                           // issued-at
  "scope": "openid profile expenses:read expenses:write",  // what the bearer may do
  "roles": ["employee", "expense-submitter"]   // optional: coarse roles for the API's RBAC
}
// signature: FAKE-SIGNATURE (real one is an RSA signature over header.payload)
```

**What each claim is and what the API does with it:**

| Claim | Meaning | The API's job |
|---|---|---|
| `iss` | Who issued the token | Reject if it isn't exactly `https://sso.finco.example`. |
| `sub` | Stable, unique user id | Use as the primary key for "whose expenses?" — **not** the email (emails change). |
| `aud` | Who the token is *for* | Reject if `aud` isn't the expenses API. Stops a token minted for another API being replayed here. |
| `azp` | Which client asked | Useful for logging / policy ("which app is calling?"). |
| `exp` / `iat` | Expiry / issued-at | Reject if expired (mind small clock skew). |
| `scope` | Permitted actions | `GET` needs `expenses:read`; `POST` needs `expenses:write`. Missing scope → `403`. |
| `roles` | Coarse role labels | Optional RBAC layer on top of scopes. |

> **Scope vs role, quickly:** *scope* = what the **app** was allowed to request on the user's behalf; *role* = what the **user** is (their entitlement). Fintech APIs often check both: "the app has `expenses:write` AND the user has the `expense-submitter` role."

---

## Step 4 — Call the API with the access token

The SPA calls the expenses API, putting the access token in the `Authorization` header:

```http
GET /v1/expenses HTTP/1.1
Host: api.finco.example
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6ImF0K2p3dCIsImtpZCI6IjIwMjQtZmsifQ.eyJpc3MiOiJodHRwczovL3Nzby5maW5jby5leGFtcGxlIiwic3ViIjoidV9mYXJoYWFuXzg4NDIiLCJhdWQiOiJodHRwczovL2FwaS5maW5jby5leGFtcGxlIn0.FAKE-SIGNATURE
```

Success:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{ "expenses": [ { "id": "exp_1029", "amount": 4200, "currency": "INR", "status": "pending" } ] }
```

### How the API validates the token (JWT path — the common case)

The access token here is a **JWT**, so the API can validate it **without calling the AS on every request** — it just checks the math and the claims:

1. **Read `kid` from the header** → look up that public key in the AS's **JWKS** (`https://sso.finco.example/.well-known/jwks.json`), which the API fetches once and caches. JWKS auto-handles key rotation.
2. **Verify the signature** against that public key.
3. **Check `alg`** is `RS256` (from an **allowlist** — see the gotcha).
4. **Check `iss`** = `https://sso.finco.example`.
5. **Check `aud`** = `https://api.finco.example`.
6. **Check `exp`** hasn't passed.
7. **Check `scope`** contains `expenses:read` for this `GET` (and `expenses:write` for a `POST`).

Any check fails → `401` (bad token) or `403` (valid token, missing scope).

> **Gotcha (attack → defense): `alg:none` and algorithm confusion.** An attacker edits the header to `"alg":"none"` and strips the signature, hoping a lazy library accepts an unsigned token; or flips `RS256`→`HS256` and signs with the *public* key as an HMAC secret. **Defense:** use a vetted library, **pin the algorithm to an allowlist** (`RS256` only), never let the token's own header choose the verification path, and never accept `none`. You'll break this hands-on in [`04-cryptography` Lab 9](../../04-cryptography/README.md).

### The opaque-token alternative — introspection

Some setups (including many **PingFederate** deployments) issue **opaque** access tokens — a random string with no readable claims, e.g. `at_9f83bd2c1e...`. The API can't decode those, so it **asks the AS** via the introspection endpoint (RFC 7662):

```http
POST /as/introspect HTTP/1.1
Host: sso.finco.example
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <base64(api-resource-server-id:its-secret)>

token=at_9f83bd2c1e4a7b6d0f2c8e91
```

The AS replies with the claims (and whether the token is still alive):

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "active": true,
  "sub": "u_farhaan_8842",
  "aud": "https://api.finco.example",
  "scope": "openid profile expenses:read expenses:write",
  "exp": 1751970900,
  "client_id": "expense-dashboard-spa"
}
```

**JWT vs opaque — the real trade-off:**

| | JWT access token | Opaque access token |
|---|---|---|
| API validates by | Checking signature + claims **locally** | **Calling** the AS (`/introspect`) |
| Speed | Fast, no round trip | Extra network hop per check (usually cached) |
| Revocation | **Weak** — valid until `exp` | **Strong** — AS can say `active:false` instantly |
| Sees inside | Anyone who decodes it | Only the AS knows the contents |

> For high-value fintech APIs, the instant-revocation of opaque + introspection is attractive; for internal high-throughput APIs, JWTs avoid a round trip. Many teams split the difference: JWTs with **short** `exp`.

---

## Step 5 — Token expires → refresh (with rotation)

Fifteen minutes pass. The access token's `exp` is now in the past. Next API call:

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer error="invalid_token", error_description="token expired"
```

The SPA doesn't make Farhaan log in again. It uses the **refresh token** to get a fresh access token — back channel again:

```http
POST /as/token HTTP/1.1
Host: sso.finco.example
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&refresh_token=rt_8f3aQ.opaque.4b1c9d7e2f...NOT-A-JWT
&client_id=expense-dashboard-spa
&scope=openid profile expenses:read expenses:write
```

Response — note the **new** refresh token:

```json
{
  "access_token":  "eyJhbGci...NEW-JWT...FAKE-SIGNATURE",
  "refresh_token": "rt_2b9cX.opaque.NEW-VALUE-EACH-TIME...",   // ← different from the one we sent!
  "token_type":    "Bearer",
  "expires_in":    900
}
```

**What just happened and why — refresh token rotation.** The AS issued a **brand-new** refresh token and **invalidated the old one**. This is **rotation** (recommended for public clients by the OAuth Security BCP, RFC 9700).

Why bother? It enables **reuse detection**:

- Normally each refresh token is used once, then replaced. The chain moves forward: `rt1 → rt2 → rt3 …`
- Suppose an attacker **steals `rt2`** and redeems it. Now there are two parties holding tokens from the same chain.
- When the *legitimate* app later tries to use `rt2` (which the AS already retired), the AS sees a **retired token being reused** → it concludes the chain is compromised and **kills the entire family** of tokens. Farhaan is forced to log in again; the attacker is locked out too.

> **Gotcha (attack → defense): refresh token theft.** A stolen long-lived refresh token = persistent access. **Defense = rotation + reuse detection** (above), short refresh lifetimes, and binding the token to the client. Because our SPA is a public client, also prefer storing tokens in memory (see next) so there's less to steal.

---

## Step 6 — Logout and revocation

Farhaan clicks "Log out." The app calls the revocation endpoint (RFC 7009) to kill the refresh token server-side:

```http
POST /as/revoke HTTP/1.1
Host: sso.finco.example
Content-Type: application/x-www-form-urlencoded

token=rt_2b9cX.opaque.NEW-VALUE-EACH-TIME...
&token_type_hint=refresh_token
&client_id=expense-dashboard-spa
```

```http
HTTP/1.1 200 OK
```

**What "logging out" really means for JWTs — the uncomfortable truth.** Revoking the **refresh token** works instantly: no more new access tokens can be minted. But the **JWT access token already in the app's memory stays valid until its `exp`.** A JWT is self-contained — the API validates it by math, not by asking the AS — so there's **no true instant revocation** of a JWT.

Mitigations you'll actually use at FinCo:

- **Short access-token TTLs** (like our 15 min) so the "still valid after logout" window is small.
- **Opaque tokens + introspection** where instant revocation genuinely matters (the AS can flip `active:false` now).
- **Clear tokens from the client** on logout so the app itself stops sending them.
- **OIDC front-channel / back-channel logout** to end the AS session so re-entry needs a fresh login.

> **Storage gotcha (attack → defense): tokens in the wrong place.** Tokens in the **URL** leak via browser history, referrer headers, and server logs (this is why the dead *implicit* flow was killed). Tokens in **`localStorage`** are readable by any XSS on the page. **Defense:** never put tokens in URLs or logs; for SPAs prefer **in-memory** storage (or a backend-for-frontend that keeps tokens server-side in an `HttpOnly` cookie). And never, ever log an access or refresh token — that's a fintech audit finding waiting to happen.

---

## Service-to-service: the API calls another API (`client_credentials`)

Now a different shape of problem. Our **expenses API** needs to reimburse Farhaan, so it calls the **payments API** at `https://payments.finco.example`. There's **no user** in this call — it's machine-to-machine. So we use the **client credentials** grant (RFC 6749 §4.4). The expenses API is now acting as a *client* with its own identity.

```http
POST /as/token HTTP/1.1
Host: sso.finco.example
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <base64(expenses-api-svc:ITS-CLIENT-SECRET)>

grant_type=client_credentials
&scope=payments:initiate
```

```json
{
  "access_token": "eyJhbGci...svc...FAKE-SIGNATURE",
  "token_type":   "Bearer",
  "expires_in":   300,
  "scope":        "payments:initiate"
}
```

The decoded token has **no user** — `sub` is the service itself:

```json
{
  "iss": "https://sso.finco.example",
  "sub": "svc_expenses_api",              // the SERVICE is the subject, no human here
  "aud": "https://payments.finco.example",
  "scope": "payments:initiate",
  "exp": 1751970300
}
```

Then the expenses API calls payments with `Authorization: Bearer <that token>`, and payments validates it exactly like step 4 (`iss`/`aud`/`exp`/`scope`).

**This is the machine-identity pattern** you'll see all over FinCo's backend. The client secret here is a high-value credential → **vaulted and rotated by PAM** (or, better, replaced with `private_key_jwt`/mTLS so there's no shared secret at all).

### How this coexists with your k8s service-mesh mTLS

You already have **mTLS between pods** in your FinCo Kubernetes mesh. So do you even need OAuth here? **Yes — they solve different layers, and you want both:**

| | **mTLS (service mesh)** | **OAuth client_credentials** |
|---|---|---|
| Layer | **Transport** (TCP/TLS) | **Application** (HTTP) |
| Question it answers | "Is this *pod* who it claims to be, on an encrypted channel?" | "Is this *service* allowed to do *this action*?" |
| Proves | Machine **identity** + confidentiality | **Authorization** with fine-grained **scopes** |
| Granularity | Pod-to-pod trust | Per-action (`payments:initiate` vs `payments:refund`) |

> **The clean mental model:** mTLS is the *sealed, ID-checked tunnel* between two pods. The OAuth access token is the *scoped permission slip* that rides inside it saying "…and I'm allowed to initiate a payment." mTLS stops an unknown pod from even connecting; the token stops a known-but-unauthorized service from doing something it shouldn't. Defense in depth — Zero Trust wants both machine authN (mTLS) **and** action-level authZ (scopes).

---

## Try it for real (copy-paste against Keycloak)

Everything above runs on your machine with **Lab 01 — Keycloak as your own IdP** ([`../labs/01-keycloak-idp/README.md`](../labs/01-keycloak-idp/README.md)). Do the full lab for the browser steps; here are quick PowerShell-friendly commands (Lefler Law 7 — Windows-aware) to hit the same endpoints. Assumes the lab's `finco-lab` realm is up on `http://localhost:8080`.

**1. Exchange an auth code for tokens** (paste a fresh `code` from the browser step — it's single-use, be quick):

```powershell
$tokens = Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/realms/finco-lab/protocol/openid-connect/token' `
  -Body @{
    grant_type    = 'authorization_code'
    code          = '<PASTE_CODE>'
    redirect_uri  = 'http://localhost:9999/callback'
    client_id     = 'spa-lab-app'
    code_verifier = $verifier      # from the PKCE step in Lab 01 §10
  }
$tokens | Format-List
```

**2. Introspect the access token** (see the RFC 7662 response from step 4, for real):

```powershell
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/realms/finco-lab/protocol/openid-connect/token/introspect' `
  -Body @{
    token         = $tokens.access_token
    client_id     = 'oidc-lab-app'
    client_secret = '<CONFIDENTIAL_CLIENT_SECRET>'
  } | Format-List
```

**3. Machine-to-machine — `client_credentials`** (no user; needs a confidential client):

```powershell
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/realms/finco-lab/protocol/openid-connect/token' `
  -Body @{
    grant_type    = 'client_credentials'
    client_id     = 'oidc-lab-app'
    client_secret = '<CONFIDENTIAL_CLIENT_SECRET>'
  } | Format-List
```

**4. Refresh the access token** (watch the returned `refresh_token` change — that's rotation):

```powershell
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/realms/finco-lab/protocol/openid-connect/token' `
  -Body @{
    grant_type    = 'refresh_token'
    refresh_token = $tokens.refresh_token
    client_id     = 'oidc-lab-app'
    client_secret = '<CONFIDENTIAL_CLIENT_SECRET>'
  } | Format-List
```

> Prefer raw HTTP to feel the bytes? Swap `Invoke-RestMethod` for `curl.exe` (the real curl, not the PowerShell alias): `curl.exe -X POST http://localhost:8080/.../token -d "grant_type=refresh_token" -d "refresh_token=..." -d "client_id=oidc-lab-app" -d "client_secret=..."`.

---

## Attacks & defenses — where each one bit in our flow

| Step | Attack | Defense |
|---|---|---|
| 0, 3b | Stolen authorization code (public client) | **PKCE** — code useless without the `code_verifier` |
| 1 | `redirect_uri` manipulation | **Exact-match** registered redirect URIs, no wildcards |
| 1 | Overly-broad scopes | **Least privilege** — request only what the screen uses |
| 3a | CSRF / login-CSRF on the callback | Generate + verify **`state`** |
| 3c, 6 | Tokens in URL / `localStorage` / logs | In-memory or BFF `HttpOnly` cookie; **never log tokens** |
| 4 | `alg:none` / RS256→HS256 confusion | Vetted library + **algorithm allowlist**; verify via JWKS by `kid` |
| 5 | Refresh token theft | **Rotation + reuse detection**; short lifetimes |
| 6 | Expecting instant JWT logout | **Short TTLs** (or opaque + introspection) — JWTs aren't truly revocable |

---

## What you learned

- You can now read a **real OAuth trace** end to end: PKCE setup, `/authorize`, the `?code=` redirect, `/token`, the decoded JWT, the API's validation, refresh with rotation, and revocation.
- You know **why the flow is shaped this way** — the front-channel/back-channel split, why the code is short-lived and single-use, and why a public client leans on PKCE instead of a secret.
- You can decode an access-token JWT and explain **what the API does with each claim** (`iss`/`sub`/`aud`/`exp`/`scope`), and you know the **JWT-vs-opaque/introspection** trade-off PingFederate teams weigh.
- You can place **machine-to-machine `client_credentials`** correctly next to your **k8s mTLS** — transport authN vs application authZ, two layers, both wanted.
- Every step has an **attack and its defense** wired to the exact moment it bites.

## Next

- **Do it with your hands:** run [Lab 01 — Keycloak as your own IdP](../labs/01-keycloak-idp/README.md) start to finish, then re-read this note — every message above will map to something you saw.
- **Then attack it:** the natural follow-up is **Lab 4 (Burp-intercept)** in the [domain README](../README.md) — put Burp Suite in the middle of this exact flow, watch the `/authorize` and `/token` calls live, and try tampering (drop `state`, swap `redirect_uri`, edit a JWT `alg`) to *feel* each defense hold or break. Hand the offensive angles to **Loki**; ask **Lefler** to stand up the intercept lab.
- **Compare protocols:** revisit [note 02 — SAML deep dive](02-saml-deep-dive.md) and notice how SAML's signed XML assertion is the older cousin of the JWT you just decoded.

*— Janus 🔐 · standards: RFC 6749 (OAuth), 7519 (JWT), 7636 (PKCE), 7009 (revocation), 7662 (introspection), 9068 (JWT access tokens), 9700 (Security BCP), OIDC Core.*
