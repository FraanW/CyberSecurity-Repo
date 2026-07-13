# OAuth 2.0 + OIDC — the complete reference card

> **Janus's reference card.** Notes [03](03-oauth-oidc-deep-dive.md) (theory) and [19](19-oauth2-in-practice.md) (wire-level practice) *teach* OAuth. **This note is for looking things up** — every role, endpoint, token, grant type, flow, attack, and defense in crisp pointers. Skim it before an interview, keep it open during a debugging session, revise from it before a cert exam.
>
> **Prereq:** none to *use* it, but read note 03 once so the pointers have meaning. **Hands-on:** [Lab 01 — Keycloak as your own IdP](../labs/01-keycloak-idp/README.md).

---

## TL;DR

- **OAuth 2.0 (RFC 6749)** = **delegated authorization**: let an app act on your data *without* your password. It is **not** login.
- **OIDC (OpenID Connect)** = the **authentication** ("who are you?") layer built **on top of** OAuth — adds the **ID token**, **UserInfo**, and **discovery**.
- **Default flow today:** Authorization Code **+ PKCE** for anything with a user; **Client Credentials** for machine-to-machine.
- **Dead flows:** Implicit and Password (ROPC) — deprecated by the OAuth Security BCP (RFC 9700) and removed in OAuth 2.1.
- **One-line security model:** secrets and tokens move on the **back channel**; the **front channel** (browser redirects) carries only one-time, short-lived artifacts (the code) — and every artifact is bound to something (PKCE, `state`, `nonce`, exact `redirect_uri`).

---

## 1. Why OAuth exists (the motivation)

- **The problem:** pre-OAuth, "let app X read my data on service Y" meant **giving X your Y password** — full access, forever, unrevocable, and X can be breached.
- **The fix:** hand the app a **scoped, expiring, revocable token** instead of the password (the *valet key*, not the master key).
- **The constraint that shapes everything:** the browser (front channel) is **hostile territory** — URLs leak via history, logs, referrers, extensions. So the protocol is engineered to never put a long-lived secret in a URL.
- **Why OIDC had to exist:** raw OAuth proves *access*, not *identity* — sites that "logged users in" with a bare access token were vulnerable to token substitution. OIDC standardizes login with a token *meant for the client*: the **ID token**.

---

## 2. The infrastructure (roles + trust setup)

### The four roles

| Role | Plain words | Example |
|---|---|---|
| **Resource Owner** | the user who owns the data | Farhaan |
| **Client** | the app that wants access | web app, SPA, mobile app, backend job |
| **Authorization Server (AS)** | the login server that issues tokens | PingFederate (FinCo), Keycloak (lab), Entra ID, Okta |
| **Resource Server (RS)** | the API holding the data; accepts access tokens | `api.finco.example`, Microsoft Graph |

### Client types (decides which flow + whether you get a secret)

- **Confidential client** — runs on a server, *can* keep a secret → gets `client_id` + `client_secret` (or better: private-key JWT / mTLS auth).
- **Public client** — SPA or mobile app, code is in the user's hands, *cannot* keep a secret → `client_id` only, **must use PKCE**.

### What "registering a client" sets up (the trust prerequisites)

1. `client_id` (public identifier) and, for confidential clients, a **client authentication method** (secret / private key / mTLS cert).
2. **Exact** allowed `redirect_uri`(s) — the AS will refuse anything else.
3. Allowed **grant types** and **scopes** for this client.
4. Token settings: lifetimes, refresh rotation, audience.
- The AS also publishes its own trust anchors: a **discovery document** and a **JWKS** (its public signing keys) — see endpoints below.

---

## 3. The endpoints

| Endpoint | Channel | What it does | Spec |
|---|---|---|---|
| `/authorize` | **Front** (browser redirect) | starts the flow; authenticates user, gets consent, returns the **code** | RFC 6749 |
| `/token` | **Back** (server↔server POST) | swaps code/refresh-token/credentials for **tokens** | RFC 6749 |
| `/.well-known/openid-configuration` | Back | **discovery**: JSON listing every other endpoint + capabilities | OIDC Discovery |
| `jwks_uri` | Back | the AS's **public keys** (JWKS) — how RSs verify token signatures | RFC 7517 |
| `/userinfo` | Back | OIDC: returns claims about the logged-in user (send access token) | OIDC Core |
| `/introspect` | Back | RS asks the AS "is this (opaque) token valid? what's in it?" | RFC 7662 |
| `/revoke` | Back | kill a token (logout, compromise) | RFC 7009 |
| `/device_authorization` | Back | device flow: get a `user_code` for input-constrained devices (TVs, CLIs) | RFC 8628 |
| PAR endpoint (`/par`) | Back | push the authorize request server-side first → browser carries only a `request_uri` | RFC 9126 |
| `/register` | Back | dynamic client registration (self-service client creation) | RFC 7591 |
| `end_session_endpoint` | Front | OIDC RP-initiated logout | OIDC Logout |

**Rule of thumb:** *front channel = redirects the user's browser can see; back channel = direct HTTPS calls an attacker in the browser can't see.* Secrets and tokens belong on the back channel only.

---

## 4. The tokens

| Token | Format | Consumed by | Purpose | Lifetime |
|---|---|---|---|---|
| **Access token** | JWT *or* opaque | **Resource Server** | "bearer may call the API within these scopes" | minutes–1h |
| **Refresh token** | opaque | **Authorization Server** | mint new access tokens without re-login | days–months, **rotate on use** |
| **ID token** (OIDC) | **always JWT** | **Client** | proof of *who* authenticated + how/when | minutes; consumed once at login |

### The three rules that prevent 80% of token bugs

1. **Access token → APIs.** Clients must treat it as opaque — never parse it to learn who the user is.
2. **ID token → the client only.** **Never** send an ID token to an API as if it were an access token.
3. **Refresh token = password-equivalent.** Store server-side or in the most protected storage available; rotate; revoke on logout.

### JWT anatomy (30-second version)

- Three base64url parts: `header.payload.signature`.
- Header: `alg` (signing algorithm), `kid` (which key in the JWKS signed it).
- Payload: the **claims** (see §7 for the standard ones).
- **Signed, not encrypted** — anyone can *read* a JWT; only the AS's private key can *forge* one. Never put secrets in claims.

### RS validation checklist (what the API must check on every request)

1. Signature verifies against a key from the AS's **JWKS** (fetched from `jwks_uri`, matched by `kid`).
2. `alg` is on an **allowlist** (e.g. `RS256`/`ES256`) — reject `none` and unexpected algorithms.
3. `iss` = the expected AS. 4. `aud` = **me** (this API). 5. `exp`/`nbf` valid (small clock skew). 6. required **scopes** present.
- Opaque token instead of JWT? Same checks, but ask the AS via **introspection** (RFC 7662).

### Bearer vs sender-constrained

- **Bearer token** (RFC 6750, the default): *whoever holds it can use it* — like cash. Theft = usable.
- **Sender-constrained token:** bound to the client's key, so a stolen token is useless — via **mTLS** (RFC 8705) or **DPoP** (RFC 9449). Where high-value APIs are heading (open banking mandates this).

---

## 5. Scopes & consent

- **Scope** = a permission label the client *requests* (`read:expenses payments:initiate openid profile`).
- The AS shows the user a **consent screen**; what's granted is stamped into the access token.
- The RS enforces scope per endpoint: no `payments:initiate` scope → 403, even with a valid token.
- **Least privilege applies to apps too:** request the minimum; auditors *will* ask why a reporting app holds a write scope.
- Scopes are **coarse app-level permissions** — fine-grained per-record authorization (is this *your* expense report?) stays in the API. Scope ≠ RBAC.

---

## 6. Grant types (the flows)

| Grant | Use for | Status | One-line mechanics |
|---|---|---|---|
| **Authorization Code + PKCE** | everything with a user (web, SPA, mobile) | ✅ **the default** | browser gets one-time `code` at `/authorize`; client swaps code+`code_verifier` for tokens at `/token` |
| **Client Credentials** | machine-to-machine, no user | ✅ | client authenticates itself at `/token`, gets an app-identity access token (no refresh token) |
| **Refresh Token** | renew access without re-login | ✅ | `grant_type=refresh_token` at `/token`; rotate on every use |
| **Device Code** | no-keyboard devices: TVs, CLIs, kiosks | ✅ | device polls `/token` while user enters `user_code` at a URL on their phone |
| **JWT Bearer** | trade an existing trust (signed JWT) for a token | ✅ niche | RFC 7523 — common for service accounts (e.g. Google) |
| **Token Exchange** | service A calls B *on behalf of* a user (delegation chains) | ✅ niche | RFC 8693 — swap one token for another with narrowed audience |
| **CIBA** | login initiated on a different device (agent triggers push to customer's phone) | ✅ niche | backchannel authentication request + push approval |
| **Implicit** | (was: SPAs) tokens returned straight in the URL fragment | ❌ **deprecated** | token in URL = leaks via history/referrer; no PKCE possible |
| **Password (ROPC)** | (was: trusted first-party apps) app collects the password itself | ❌ **deprecated** | resurrects the password anti-pattern OAuth exists to kill; breaks MFA |

### Which grant, in one breath

- Human in a browser or app → **Code + PKCE**. 
- No human (cron job, microservice) → **Client Credentials**. 
- No keyboard → **Device Code**. 
- Renewing → **Refresh Token**. 
- Anything suggesting Implicit or ROPC → **legacy; migrate**.

---

## 7. The complete flow — Authorization Code + PKCE, pointer by pointer

*(Full wire-level version with real bytes: [note 19](19-oauth2-in-practice.md).)*

```
Browser ──/authorize (front)──▶ AS ──code──▶ Browser ──code──▶ Client ──/token (back)──▶ tokens ──▶ API
```

1. User clicks **Login**. Client generates PKCE pair: random `code_verifier` (kept), `code_challenge = base64url(sha256(verifier))` (sent).
2. Client redirects browser to **`/authorize`** with: `response_type=code`, `client_id`, `redirect_uri`, `scope` (incl. `openid` for OIDC), `state` (anti-CSRF random), `nonce` (OIDC replay guard), `code_challenge` + `method=S256`.
3. AS **authenticates the user** (password + MFA — this part is the AS's business, not OAuth's) and shows **consent**.
4. AS redirects back to the registered `redirect_uri` with **`?code=...&state=...`** — code is one-time, ~60s lifetime.
5. Client **checks `state`** matches what it sent (CSRF defense), else abort.
6. Client POSTs the **back channel** `/token` request: `grant_type=authorization_code`, `code`, `redirect_uri`, `client_id`, **`code_verifier`** (+ client secret if confidential).
7. AS verifies: code valid + unused, `redirect_uri` matches, **`sha256(verifier) == challenge`** (PKCE), client authenticated.
8. AS returns **`access_token` + `id_token` + `refresh_token`**.
9. Client validates the **ID token** (signature, `iss`, `aud` = my `client_id`, `exp`, **`nonce`** matches) → user is logged in; create local session.
10. Client calls the API with **`Authorization: Bearer <access_token>`** → RS runs the §4 validation checklist → data.
11. Access token expires → **refresh grant** → new access + **new (rotated) refresh token**.
12. Logout → **`/revoke`** the refresh token (+ OIDC `end_session_endpoint` to kill the AS session).

### Client Credentials flow (machine-to-machine), in four pointers

1. Service POSTs `/token`: `grant_type=client_credentials`, `scope`, + its credentials (secret / private-key JWT / **mTLS cert** — the strong option, and how it meshes with FinCo's k8s mTLS).
2. AS authenticates the *service* and returns an access token (no refresh token — the client can just re-authenticate).
3. Service calls the API with the bearer token. 4. Token expires → repeat step 1.

---

## 8. OIDC — the identity layer, in pointers

- **What it adds to OAuth:** the **ID token**, the **`/userinfo`** endpoint, **discovery** (`/.well-known/openid-configuration`), standard **claims**, standard **logout**.
- **How it's triggered:** just add scope **`openid`** (plus `profile`, `email` as wanted) to a normal code flow. Same endpoints, same flow — extra token back.
- **Renamed roles:** Client → **Relying Party (RP)**; AS → **OpenID Provider (OP)**. Same machines, OIDC vocabulary.
- **`response_type`:** use `code` (code flow) — the hybrid (`code id_token`) and implicit (`id_token token`) variants are legacy web-app patterns; new builds use code + PKCE only.

### ID token claims you must know

| Claim | Meaning | Gotcha |
|---|---|---|
| `iss` | who issued it (AS URL) | must exactly match the discovery `issuer` |
| `sub` | stable unique user ID | **the** identifier — never key on email (emails change/reassign) |
| `aud` | who it's for = your `client_id` | reject if it's not you — stops token substitution |
| `exp` / `iat` | expiry / issued-at | short; ID tokens are consumed once at login |
| `nonce` | echo of the value the client sent | must match → blocks replayed/injected ID tokens |
| `auth_time` | when the user actually authenticated | enforce re-auth for sensitive actions ("step-up") |
| `acr` / `amr` | how strong / which methods (e.g. `mfa`, `pwd`) | how you *prove* MFA happened — auditors ask |
| `azp` | authorized party | relevant when one token serves multiple audiences |

### OIDC logout — the three mechanisms

- **RP-initiated:** app redirects to `end_session_endpoint` → AS session dies.
- **Front-channel:** AS loads hidden iframes to each app's logout URL (fragile — third-party-cookie blocking).
- **Back-channel:** AS POSTs a signed **logout token** server-to-server to each app (reliable — the modern choice).

### OAuth vs OIDC vs SAML, one line each

- **OAuth 2.0** — an app gets *scoped access* to an API on your behalf (authorization).
- **OIDC** — an app learns *who you are* via a signed ID token (authentication), riding on OAuth.
- **SAML 2.0** — same *job* as OIDC (federated web SSO) but XML/2005-era; enterprises run both side by side ([note 02](02-saml-deep-dive.md)).

---

## 9. Vulnerabilities & attacks (each paired with its defense — Law 9)

| # | Attack | How it works (one line) | Defense |
|---|---|---|---|
| 1 | **Redirect URI manipulation** | attacker changes `redirect_uri` so the code is delivered to *their* server | AS enforces **exact-match** registered URIs (no wildcards, no path prefixes) |
| 2 | **Open-redirect chaining** | `redirect_uri` is valid but that page forwards elsewhere with the code attached | no open redirectors on client domains; exact match incl. path |
| 3 | **CSRF / session fixation on callback** | attacker gets victim's browser to complete *attacker's* flow → victim logged into attacker's account | random **`state`**, verified on callback |
| 4 | **Authorization code interception** | code stolen in transit/from URL and replayed by attacker | **PKCE** — thief lacks `code_verifier`; codes one-time + ~60s |
| 5 | **Token leakage via URL** (implicit) | access token in URL fragment → browser history, logs, referrer | implicit is dead; **code flow only**; tokens travel on the back channel |
| 6 | **Mix-up attack** (multi-AS clients) | malicious AS tricks client into sending the code/verifier to the wrong AS | `iss` parameter on the authorization response (RFC 9207); per-AS redirect URIs |
| 7 | **JWT `alg:none` / algorithm confusion** | forged token claims "no signature needed" or swaps RS256→HS256 using the public key as HMAC secret | **allowlist algorithms**; verify strictly against JWKS keys of expected type |
| 8 | **`kid`/JWKS injection** | attacker-controlled `kid` or `jku` header points verification at attacker's key | resolve keys **only** from the pre-configured `jwks_uri`; ignore `jku`/`x5u` from tokens |
| 9 | **Audience confusion / token substitution** | valid token minted for app A replayed against API B | RS checks **`aud`**; clients check ID-token `aud` + `nonce`; per-API audiences |
| 10 | **Refresh token theft** | long-lived token stolen from storage → silent long-term access | **rotation on every use** + reuse detection (old one reused → revoke the whole family); sender-constraining |
| 11 | **Consent phishing ("illicit grant")** | attacker's *legitimate-looking* app asks users for broad scopes (`Mail.Read`, offline access) — MFA doesn't help, the user *approves* | admin-consent policies for risky scopes, publisher verification, audit app grants (real Microsoft 365 campaigns work this way) |
| 12 | **Device-code phishing** | attacker starts a device flow, sends victim the `user_code` "to fix your account" — victim's login mints *attacker's* token | user education; restrict/disable device flow where not needed; conditional access on the flow |
| 13 | **Client secret leakage** | secret in a git repo, mobile APK, or SPA bundle | public clients get **no secret** (PKCE instead); server secrets in vaults, rotated — a PAM/secrets problem ([note 11](11-pam-deep-dive.md)) |
| 14 | **Scope creep / over-privileged apps** | app requests (and admins approve) far more than needed → huge blast radius on compromise | least-privilege scopes; periodic access reviews of app grants (IGA for apps, [note 12](12-iga-deep-dive.md)) |
| 15 | **Stolen bearer token replay** | any XSS/malware/proxy-logs theft → token works from attacker's machine | short lifetimes; **sender-constrained tokens** (DPoP/mTLS); token binding to session context |

**Cross-reference:** JWT internals in [04-cryptography](../../04-cryptography/README.md), token/session handling in [03-application-security](../../03-application-security/README.md), broader IAM attack surface in [note 10](10-iam-vulnerabilities.md).

---

## 10. Threats & motivations — who attacks OAuth, and why

- **Why tokens beat passwords (attacker's view):** a stolen token **bypasses MFA** (auth already happened), often **survives a password reset**, works quietly via APIs, and refresh tokens grant **long-lived** access. Modern attackers increasingly steal *sessions/tokens*, not credentials.
- **Account takeover (ATO):** tokens = direct access to mail, files, payment APIs. In fintech: `payments:initiate` scope is money.
- **Persistence without malware:** a consent-phished app grant (attack #11) keeps working — no implant to detect, survives credential resets, hides in the "approved applications" list nobody reviews.
- **Supply-chain leverage:** steal one integration's tokens, inherit access to *all its customers* (the 2022 Heroku/Travis-CI → GitHub OAuth-token theft is the canonical case).
- **Forge-the-issuer, the nightmare tier:** compromise or abuse an AS **signing key** and you can mint valid tokens for *anyone* (Storm-0558 vs Microsoft, 2023). Why signing-key custody is a crown-jewel PAM concern.
- **Fintech regulatory angle:** open-banking regimes (PSD2/FAPI) *mandate* hardened OAuth — PKCE, PAR, sender-constrained tokens — because the API behind the token moves money. PCI-DSS Req 7/8 (least privilege, authn) applies to *app* grants too ([note 09](09-pci-dss-and-iam.md)).

---

## 11. Hardening checklist (the Security BCP, RFC 9700 → "OAuth 2.1")

- [ ] **Code + PKCE for every user-facing flow** — even confidential clients.
- [ ] **No implicit, no ROPC** anywhere (grep your AS config for them).
- [ ] **Exact-match redirect URIs**; HTTPS-only; no wildcards.
- [ ] `state` on every request; `nonce` on every OIDC request.
- [ ] **Short-lived access tokens** (≤ 15–60 min); **refresh rotation + reuse detection**.
- [ ] RS validates `iss`, `aud`, `exp`, `alg` allowlist, scopes — every request (§4 checklist).
- [ ] Distinct **audiences per API**; no "one token for everything".
- [ ] Confidential clients authenticate with **private-key JWT or mTLS**, not shared secrets, where supported.
- [ ] High-value APIs: **sender-constrained tokens** (DPoP / mTLS) + **PAR**.
- [ ] Govern app consents: admin approval for risky scopes, periodic review of granted apps.
- [ ] Monitor: token issuance anomalies, refresh-reuse alerts, consent-grant events in AS audit logs (PingFederate `audit.log` — [note 18](18-pingfederate-explained.md)).

---

## 12. Why you care at FinCo (Law 8)

- **PingFederate is the AS** in every row of §3 — `/authorize`, `/token`, JWKS, introspection are all endpoints your team operates; Access Token Managers decide JWT-vs-opaque and the claims inside.
- **Your k8s estate:** pod-to-pod trust is service-mesh **mTLS** (transport identity); *API-level* delegation across services is **client credentials / token exchange** — the two layers coexist ([note 06](06-tls-https-mtls.md)).
- **Tickets you'll see:** `invalid_redirect_uri` after a deploy changed a URL; "SSO works but API returns 401" (wrong `aud` / expired JWKS cache); "app keeps asking to re-login" (refresh rotation misconfig); an auditor asking for the list of apps holding write scopes.
- **See it for yourself (Law 12):** run the flow end-to-end against Keycloak with the copy-paste curl in [note 19](19-oauth2-in-practice.md), and build the IdP in [Lab 01](../labs/01-keycloak-idp/README.md).

---

## What you learned

- OAuth's shape is forced by one constraint: **the browser can't keep secrets** → codes up front, tokens out back, every artifact bound (PKCE, `state`, `nonce`, exact URIs).
- The **infrastructure** (4 roles), **endpoints** (front vs back channel), **3 tokens** and their audiences, **grant types** and when each applies.
- **OIDC** = OAuth + ID token + discovery + UserInfo + standard logout.
- **15 attacks with their paired defenses**, and the attacker economics: tokens bypass MFA and outlive password resets — which is why the BCP checklist in §11 exists.

## Next

- Trace every pointer in §7 on the wire: [note 19 — OAuth 2.0 in practice](19-oauth2-in-practice.md).
- Build the AS yourself: [Lab 01 — Keycloak as your own IdP](../labs/01-keycloak-idp/README.md).
- Then continue the roadmap: a **PAM or IGA hands-on lab** ([note 11](11-pam-deep-dive.md) / [note 12](12-iga-deep-dive.md)).
