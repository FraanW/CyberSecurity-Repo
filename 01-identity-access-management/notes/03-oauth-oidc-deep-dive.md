# OAuth 2.0 & OpenID Connect — the modern stack, demystified

> **Janus's deep dive.** This is the note that fixes the single most common misunderstanding in IAM: **OAuth is not login.** Once you internalize *authorization vs authentication* here, you'll understand more than most engineers who use these protocols daily. Prereq: [the landscape note](01-iam-protocol-landscape.md). Hands-on: [Lab 01 — Keycloak as your own IdP](../labs/01-keycloak-idp/README.md).

---

## 1. The problem OAuth 2.0 was invented to solve

Before OAuth, if a photo-printing site wanted your Google Photos, you'd give it your **Google password**. Insane: the site now has full access to your entire account forever, and you can't revoke just that site.

**OAuth 2.0 (RFC 6749) is delegated authorization:** it lets you grant an app **limited, revocable access** to *some* of your resources on *another* service **without sharing your password**.

**The valet-key analogy:** your car has two keys. The **master key** opens everything (glovebox, trunk, drives 200 km/h). The **valet key** only starts the engine and drives short distances — it can't open the glovebox. OAuth hands apps a *valet key* (a scoped **access token**), never your master key (your password).

Keywords that map to the analogy:
- **Scope** = what the valet key is allowed to do (`read:photos`, not `delete:account`).
- **Access token** = the valet key itself (time-limited, revocable).
- **Consent screen** = you deciding which valet key to hand over ("App X wants to read your photos — Allow?").

---

## 2. The distinction that makes you sound competent: authZ ≠ authN

- **OAuth 2.0 answers: "is this app *allowed to do X* on the user's behalf?"** → **authorization**.
- It does **not** reliably answer "*who* is the user?" That's **authentication**.

People bolted "login with OAuth" onto raw OAuth for years, and it caused real security holes (an access token proves *access*, not *identity* — it can be a token minted for a different app and replayed). **OpenID Connect (OIDC)** was created in 2014 to add a proper, standardized **authentication** layer on top of OAuth. So:

> **OAuth = authorization. OIDC = authentication built on OAuth.** When a colleague says "we log in with OAuth," the precise thing they mean (or should mean) is **OIDC**.

---

## 3. The four roles (learn these names — they're all over Entra/Okta docs)

| Role | Who it is | Example |
|---|---|---|
| **Resource Owner** | The user who owns the data | Farhaan |
| **Client** | The app that wants access | A web/mobile app |
| **Authorization Server (AS)** | Issues tokens after authenticating the user & getting consent | **Okta, Entra ID, Keycloak** |
| **Resource Server (RS)** | The API holding the protected data; accepts access tokens | Microsoft Graph, your bank's account API |

> In Entra/Okta land, the **Authorization Server = your IdP**. The same product that does SAML also acts as the OAuth/OIDC Authorization Server. That's why one IdP can serve legacy (SAML) and modern (OIDC) apps at once.

---

## 4. The tokens

| Token | Format | Audience (who consumes it) | Purpose |
|---|---|---|---|
| **Access token** | opaque string **or** JWT | the **Resource Server / API** | "bearer of this may call the API within these scopes" |
| **Refresh token** | opaque, long-lived | the **Authorization Server** | get a new access token without re-login |
| **ID token** (OIDC only) | **always a JWT** | the **Client app** | "here's *who* logged in" — the authentication result |

**Three rules that prevent 80% of token bugs:**
1. **Access token → send to APIs.** Never inspect it in your client to figure out who the user is (its format/audience isn't for you).
2. **ID token → for the client only.** It tells *your app* who logged in. **Never send an ID token to an API** as if it were an access token.
3. **Refresh token = high value.** Treat like a password; store securely; rotate; revoke on logout/compromise.

Mixing these up ("we used the ID token to call the API," "we used the access token to identify the user") is a top source of both bugs and vulnerabilities. Cross-reference [../../03-application-security/README.md](../../03-application-security/README.md) §4 (token/session handling) and [../../04-cryptography/README.md](../../04-cryptography/README.md) §6 (how JWTs are signed).

---

## 5. The flow that matters: Authorization Code + PKCE

This is the **modern default** for web, mobile, and SPA apps. Learn it cold — it's what you'll trace in [Lab 01](../labs/01-keycloak-idp/README.md).

```
 Resource Owner        Client app            Authorization Server        Resource Server
   (Farhaan)           (the app)             (Keycloak/Okta/Entra)        (the API)
       |                   |                          |                         |
   1.  |-- click login --->|                          |                         |
       |                   | 2. build /authorize URL w/ code_challenge          |
       |<-- redirect to AS's /authorize --------------|                         |
       |                                              |                         |
   3.  |--- GET /authorize?response_type=code         |                         |
       |    &client_id=...&redirect_uri=...           |                         |
       |    &scope=openid%20profile                   |                         |
       |    &state=xyz&code_challenge=...&S256 ------->|                         |
       |                                              | 4. authenticate user     |
       |                                              |    (password + MFA),     |
       |                                              |    show consent          |
       |<-- redirect to redirect_uri?code=AUTHCODE&state=xyz --|                 |
       |                   |                          |                         |
   5.  |-- browser hands code to client ------------->|                         |
       |                   | 6. POST /token           |                         |
       |                   |    grant_type=authorization_code                   |
       |                   |    code=AUTHCODE                                   |
       |                   |    code_verifier=ORIGINAL  (PKCE proof)            |
       |                   |    client_id / client_secret --------------------->|
       |                   |<-- access_token + id_token (+ refresh_token) ------|
       |                   |                          |                         |
   7.  |                   |--- GET /api  Authorization: Bearer <access_token> ------------>|
       |                   |<-------------------- protected data --------------------------|
```

**Every parameter, explained (you'll configure these in real apps):**
- `response_type=code` → "give me an authorization **code** first, not a token directly" (the secure two-step).
- `client_id` → which registered app this is.
- `redirect_uri` → where to send the code back. **Must exactly match a pre-registered value** (see §7 attacks).
- `scope` → what's requested. `openid` **turns OAuth into OIDC** (see §8). `profile`, `email`, plus API scopes.
- `state` → random anti-**CSRF** value the client generates and later verifies. Prevents login-CSRF.
- `code_challenge` / `code_challenge_method=S256` → the **PKCE** public half (see §6).
- **Authorization code** → short-lived, single-use; exchanged at `/token`. It's useless to a thief without the PKCE `code_verifier` / client secret.

**Why two steps (code, then token)?** The code travels through the **browser** (front channel, more exposed). The valuable tokens are fetched over a **direct back-channel** call the attacker can't see. The code alone is worthless without the second-step secret — that's the whole point.

---

## 6. PKCE — the piece everyone asks about

**PKCE** (Proof Key for Code Exchange, "pixie", RFC 7636) protects the authorization code from being stolen and used by an attacker — critical for **public clients** (mobile apps, SPAs) that can't safely keep a `client_secret`.

How it works:
1. Client invents a random secret **`code_verifier`**.
2. It sends only the **hash**: `code_challenge = SHA256(code_verifier)` in the `/authorize` request.
3. When exchanging the code at `/token`, the client sends the **original `code_verifier`**.
4. The AS hashes it and checks it matches the earlier `code_challenge`. **A thief who intercepted only the code can't complete the exchange** because they never saw the `code_verifier`.

> PKCE is now recommended for **all** OAuth clients (even confidential ones) per the OAuth 2.0 Security BCP (RFC 9700). If you remember one modern-OAuth fact, remember: **"Authorization Code + PKCE is the default; implicit flow is dead."**

---

## 7. The other grant types (know when each applies)

| Grant | Use case | Status |
|---|---|---|
| **Authorization Code + PKCE** | Web, mobile, SPA — a **user** logging in | ✅ Default |
| **Client Credentials** | **Machine-to-machine** (no user); service authenticates as itself | ✅ Standard for backend/API-to-API |
| **Device Authorization** | Input-constrained devices (smart TV, CLI): "go to url, enter code ABCD" | ✅ For devices |
| **Implicit** | Old SPA flow, returned token directly in URL | ❌ Deprecated (token leaks in browser history/referrer) |
| **Resource Owner Password Credentials (ROPC)** | App collects the user's password directly | ❌ Avoid (defeats the point of OAuth) |

In fintech you'll see **client credentials** everywhere for service accounts calling internal APIs — and those client secrets are exactly what **PAM** and secret-rotation exist to protect.

---

## 8. OpenID Connect — the authentication layer

OIDC adds three things on top of OAuth to make it a real *login* protocol:

1. **The `openid` scope.** Include `scope=openid` and the Authorization Server returns an **ID token** alongside the access token. That single scope is the switch that turns "OAuth authorization" into "OIDC authentication."
2. **The ID token (a JWT).** A signed statement of *who authenticated*. Standard claims:
   - `iss` — issuer (the IdP). **Validate it.**
   - `sub` — the stable, unique user ID (the "subject"). *Use `sub`, not email, as the primary key — emails change.*
   - `aud` — audience = your `client_id`. **Validate it's you.**
   - `exp`, `iat`, `nbf` — expiry / issued-at / not-before.
   - `nonce` — ties the token to your specific auth request (replay protection). **Validate it matches what you sent.**
   - plus `email`, `name`, `preferred_username`, etc.
3. **Standard endpoints** (auto-discovered — no more manual metadata wrangling like SAML):
   - **`/.well-known/openid-configuration`** — the **discovery document**: lists every endpoint and the signing-key location. *Your first stop when integrating any OIDC provider.*
   - **`/userinfo`** — call with the access token to get user claims.
   - **JWKS URI** (`/.well-known/jwks.json`) — the IdP's **public signing keys**, so your app can verify ID-token signatures and automatically pick up key rotation.

**ID token vs access token, one more time (because it's the #1 confusion):**
- **ID token** answers *"who is the user?"* → for your **client app**.
- **Access token** answers *"what may the bearer do to the API?"* → for the **Resource Server**.
- Sending the wrong one to the wrong place is both a bug and, often, a vulnerability.

---

## 9. JWT internals (the format under ID tokens and many access tokens)

A JWT is three base64url parts joined by dots: **`header.payload.signature`**.

```
eyJhbGciOiJSUzI1NiIsImtpZCI6ImFiYyJ9      ← header  {"alg":"RS256","kid":"abc"}
.eyJzdWIiOiIxMjMiLCJhdWQiOiJteS1hcHAi...  ← payload {"sub":"123","aud":"my-app","exp":...}
.Rje0Tb...signature...                    ← signature over header.payload
```

**How to validate a JWT (the checklist that stops attacks):**
1. **Signature verifies** against the IdP's published key (from JWKS, selected by `kid`).
2. **`alg` is the expected algorithm** — reject `none`; pin RS256 (or your chosen alg).
3. **`iss`** matches the expected issuer.
4. **`aud`** matches your client_id / API identifier.
5. **`exp` / `nbf`** are within the valid window (and mind clock skew).
6. **`nonce`** matches (OIDC login) — replay protection.

**Two classic JWT attacks (pair with defenses — CLAUDE.md rule):**
- **`alg: none`** — attacker strips the signature and sets `alg` to `none`; a naive library "verifies" a signatureless token. **Defense:** never accept `none`; explicitly allow-list algorithms.
- **Algorithm confusion (RS256 → HS256)** — the app expects RS256 (asymmetric). Attacker changes `alg` to HS256 (symmetric HMAC) and signs with the *public* RSA key as the HMAC secret; a buggy library uses the public key to "verify" and passes it. **Defense:** bind the verification algorithm to the key type; don't let the token's own `alg` header pick the verification path.

Deep crypto for both lives in [../../04-cryptography/README.md](../../04-cryptography/README.md) §5–6 (HMAC vs digital signatures), and its Lab 9 has you exploit `alg:none` hands-on. Web-side token/session pitfalls: [../../03-application-security/README.md](../../03-application-security/README.md) §4.

---

## 10. Attacks & defenses table

| Attack | Mechanism | Defense |
|---|---|---|
| **redirect_uri manipulation** | Attacker registers/abuses a loose redirect to steal the code | **Exact-match** allow-list redirect URIs; no wildcards |
| **CSRF on the callback** | Attacker injects their own code into your session | Generate + verify **`state`** |
| **Authorization code interception** | Code stolen from the browser/mobile handoff | **PKCE** |
| **`alg:none` / algorithm confusion** | Forged/undermined JWT signature | Pin algorithms; verify against JWKS by `kid` |
| **Token leakage** | Tokens in URLs, logs, referrer headers, browser storage | Use auth-code flow (not implicit); short-lived tokens; careful storage; never log tokens |
| **Refresh-token theft** | Long-lived token stolen → persistent access | Rotate refresh tokens; bind to client; revoke on reuse detection |
| **Mixing ID/access tokens** | Using ID token as API cred or access token as identity | Enforce audience checks on both sides |
| **Scope creep / over-permissioned client** | App requests far more than it needs | Least-privilege scopes; review consents |

---

## 11. How this shows up at FinCo

- **Entra ID and Okta are your Authorization Servers.** "App registration," "enterprise application," "API permissions/scopes," "consent," "conditional access" — that's all this note's machinery.
- **Service accounts / client credentials** power internal API-to-API calls. Their **client secrets** are prime targets → vaulted and rotated via **PAM**.
- **Token lifetime & conditional access** are Zero-Trust levers: short tokens + continuous policy re-evaluation.
- Most "app integration" tickets are §10 problems: a wrong `redirect_uri`, a missing scope, an expired signing key, or an audience mismatch.

---

## 12. Tools

- **jwt.io** — paste a JWT to decode header/payload and check the signature (use a dummy token, **never a real production token** — it's a fintech data-handling no-no; decode sensitive ones offline).
- **Browser DevTools → Network** — watch the `/authorize` and `/token` calls live.
- **`/.well-known/openid-configuration`** — read any provider's discovery doc directly.
- **Keycloak** — be your own Authorization Server and run the whole flow in [Lab 01](../labs/01-keycloak-idp/README.md).

---

## 13. The 30-second self-test

Can you answer these? If yes, you're ahead of most:
1. Why is OAuth *not* an authentication protocol? *(It proves delegated access, not identity.)*
2. What single scope turns OAuth into OIDC? *(`openid`.)*
3. What does PKCE protect against, and who needs it most? *(Auth-code interception; public clients — mobile/SPA.)*
4. Which token do you send to an API, and which tells your app who logged in? *(Access token → API; ID token → client.)*
5. Why two steps (code then token) instead of returning a token directly? *(Keep the valuable token off the exposed front channel.)*

Now go **run the whole flow yourself** in [Lab 01](../labs/01-keycloak-idp/README.md), then compare it to SAML in [note 02](02-saml-deep-dive.md).

*— Janus 🔐*
