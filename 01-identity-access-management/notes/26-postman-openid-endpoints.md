# Hitting the OpenID endpoints with Postman — a request-by-request guide

> **Janus + Lefler, hands-on.** You found the endpoint list in the discovery document (`/.well-known/openid-configuration`, see [note 23, Slide 21](23-reverse-kt-presentation-guide.md) and the [reference card, note 21](21-oauth2-complete-reference.md)). This note is the **do-it-yourself companion**: exactly how to structure a Postman request to *each* of those endpoints — the method, the URL, the headers, the body, and what you get back — so you can poke a real AS by hand and *see* OAuth happen.
>
> **Prereq:** skim [note 19 (OAuth wire-level)](19-oauth2-in-practice.md) once so the flow order makes sense. **Hands-on target:** your own **Keycloak** from [Lab 01](../labs/01-keycloak-idp/README.md) — *never* a FinCo production AS, and never with real credentials or real tokens on screen.

---

## TL;DR

- **Discovery is your map.** `GET /.well-known/openid-configuration` returns a JSON object; every field ending in `_endpoint` (plus `jwks_uri`) is a URL you can call. Pull it first, save each URL into a Postman **variable**, and every other request just references `{{token_endpoint}}` etc.
- **The single most common mistake:** the **token endpoint wants `application/x-www-form-urlencoded`, not JSON.** Pick the wrong body type and you get `400 invalid_request` every time.
- **The `/authorize` endpoint is a browser redirect, not an API call.** You can't meaningfully `GET` it in a raw Postman request — use Postman's **Authorization → OAuth 2.0 → Get New Access Token** helper (it pops a real browser window and does PKCE for you), or copy the URL into a browser and catch the `?code=` yourself.
- **Everything else is a plain HTTP call** you *can* build by hand: `/token`, `/userinfo`, `/introspect`, `/revoke`, `/device_authorization`, `jwks_uri`, `end_session`.
- **Pair it with defenses:** hitting these by hand teaches you the abuse *and* the fix — form-encoding, exact `redirect_uri`, PKCE, `Authorization: Basic` for client secrets, and never logging a live token.

---

## The cast (one lab scenario, reused everywhere below)

Same convention as [note 19](19-oauth2-in-practice.md) — in the lab it's **Keycloak**; at FinCo the same role is **PingFederate**.

| Postman variable | Example value (lab) | What it is |
|---|---|---|
| `{{base_url}}` | `https://sso.finco.example/realms/finco` | The realm/issuer base (Keycloak: `.../realms/<realm>`) |
| `{{client_id}}` | `expense-dashboard` | Your registered app |
| `{{client_secret}}` | *(confidential clients only)* | Secret for server-side apps — **never** for a SPA/mobile |
| `{{redirect_uri}}` | `https://oauth.pstmn.io/v1/callback` | Postman's hosted callback (or your app's exact registered URI) |
| `{{username}}` / `{{password}}` | a **lab** test user | Only ever a throwaway lab account |

> Store these in a Postman **Environment** (top-right dropdown → "Manage Environments"), not hard-coded in requests. Mark `client_secret`/`password` as **secret** type so they don't render in the UI.

---

## Step 0 — Pull the discovery document and auto-save every endpoint

This one request bootstraps everything else.

**Request**

| Field | Value |
|---|---|
| Method | `GET` |
| URL | `{{base_url}}/.well-known/openid-configuration` |
| Auth | None |
| Headers | `Accept: application/json` |
| Body | none |

**Tests tab** (Postman runs this after the response — it scrapes the URLs into variables so you never copy-paste them):

```js
const d = pm.response.json();
pm.collectionVariables.set("issuer",                 d.issuer);
pm.collectionVariables.set("authorization_endpoint", d.authorization_endpoint);
pm.collectionVariables.set("token_endpoint",         d.token_endpoint);
pm.collectionVariables.set("userinfo_endpoint",      d.userinfo_endpoint);
pm.collectionVariables.set("jwks_uri",               d.jwks_uri);
pm.collectionVariables.set("introspection_endpoint", d.introspection_endpoint || "");
pm.collectionVariables.set("revocation_endpoint",    d.revocation_endpoint || "");
pm.collectionVariables.set("device_endpoint",        d.device_authorization_endpoint || "");
pm.collectionVariables.set("end_session_endpoint",   d.end_session_endpoint || "");
pm.test("discovery ok", () => pm.response.to.have.status(200));
```

**What you get back** — a JSON object. The fields you'll reuse:

| Field in the response | You'll use it for |
|---|---|
| `issuer` | Must exactly match the `iss` claim in every token — your first validation check |
| `authorization_endpoint` | The `/authorize` browser redirect (login + consent) |
| `token_endpoint` | The `/token` back-channel exchange (where the real tokens come from) |
| `userinfo_endpoint` | Extra identity claims, called with the access token |
| `jwks_uri` | Public keys to verify JWT signatures |
| `introspection_endpoint` | Ask "is this token still valid?" (opaque tokens) |
| `revocation_endpoint` | Kill a token now |
| `device_authorization_endpoint` | Start the device-code flow |
| `end_session_endpoint` | Logout / end the session |
| `scopes_supported`, `grant_types_supported`, `*_signing_alg_values_supported` | What this AS will actually accept — read before you guess |

> **Do this first, every time you point Postman at a new AS.** If a field is missing from *your* discovery doc, that AS doesn't offer that endpoint — don't invent the URL.

---

## Endpoint 1 — `jwks_uri` (the public keys)

The simplest call after discovery, and the one you need to *verify* any JWT you get later.

| Field | Value |
|---|---|
| Method | `GET` |
| URL | `{{jwks_uri}}` |
| Auth | None |
| Body | none |

**Back:** a JSON object with a `keys` array (each key has `kid`, `kty`, `n`, `e`, `alg`, `use`). To verify a token's signature, match the token header's **`kid`** to the key with the same `kid` here.

> **Attack ↔ defense:** the classic **algorithm-confusion** attack swaps `RS256` for `HS256` and signs with this *public* key as an HMAC secret. Defense: your verifier allowlists algorithms and only accepts keys fetched from this JWKS — never lets the token's own header pick the algorithm. (Full attack table: [note 21 §9](21-oauth2-complete-reference.md).)

---

## Endpoint 2 — `authorization_endpoint` (`/authorize`): the front-channel one

**This is the one you cannot fully do as a raw Postman request.** `/authorize` is a **browser redirect** — the AS needs to render a login page, take the password + MFA, show a consent screen, then redirect the user back to `redirect_uri?code=...`. A raw `GET` in Postman just returns the login HTML; there's no browser to drive it.

You have two clean options.

### Option A (recommended) — let Postman's OAuth 2.0 helper drive it

This is what Postman is *built* for, and it does PKCE automatically.

1. On any request, open the **Authorization** tab → Type = **OAuth 2.0** → **Get New Access Token**.
2. Fill the form:

| Field | Value |
|---|---|
| Grant Type | **Authorization Code (With PKCE)** |
| Callback URL | `{{redirect_uri}}` (use `https://oauth.pstmn.io/v1/callback` and register it on the client) |
| Auth URL | `{{authorization_endpoint}}` |
| Access Token URL | `{{token_endpoint}}` |
| Client ID | `{{client_id}}` |
| Client Secret | `{{client_secret}}` *(leave blank for a public/SPA client)* |
| Code Challenge Method | **SHA-256** |
| Scope | `openid profile email` |
| State | any random string (Postman fills one) |
| Client Authentication | "Send as Basic Auth header" (confidential) or "Send client credentials in body" (public) |

3. Click **Get New Access Token** → a real browser window opens → log in as your **lab** user → consent. Postman catches the redirect, silently calls `/token`, and hands you the tokens.

> Behind the scenes Postman just did the whole Slide 16 flow: built a `code_challenge`, sent you to `/authorize`, caught the `?code=`, and POSTed it to `/token` with the matching `code_verifier`. Watch it happen in **Console** (View → Show Postman Console).

### Option B — do the redirect by hand (to *see* the front channel)

Paste this URL into a **browser** (not Postman), then read the address bar after login:

```
{{authorization_endpoint}}?response_type=code
  &client_id={{client_id}}
  &redirect_uri={{redirect_uri}}
  &scope=openid%20profile%20email
  &state=xyz123
  &code_challenge=<challenge>
  &code_challenge_method=S256
```

The browser lands on `...{{redirect_uri}}?code=SHORTCODE&state=xyz123`. Copy that `code` — you'll feed it to `/token` in Endpoint 3. To generate the `code_challenge`, use this **Pre-request Script** on a throwaway request (Postman's sandbox ships CryptoJS):

```js
function b64url(words) {
  return CryptoJS.enc.Base64.stringify(words)
    .replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
const verifier  = b64url(CryptoJS.lib.WordArray.random(32));   // the secret
const challenge = b64url(CryptoJS.SHA256(verifier));           // its SHA-256 hash
pm.collectionVariables.set("code_verifier",  verifier);
pm.collectionVariables.set("code_challenge", challenge);
console.log("verifier:", verifier, "challenge:", challenge);
```

> **Attack ↔ defense:** `state` blocks CSRF (the value must come back unchanged); the exact registered `redirect_uri` blocks redirect manipulation; PKCE means a stolen `code` is useless without the `code_verifier`. Front channel = exposed, so everything here is bound to a secret.

---

## Endpoint 3 — `token_endpoint` (`/token`): the back-channel workhorse

**The heart of it.** Every grant hits this same URL — only the body changes. **Body type must be `x-www-form-urlencoded`** (in Postman: Body tab → "x-www-form-urlencoded"). This is the #1 gotcha.

Common headers/auth for all variants:

- **Confidential client** (has a secret): Authorization tab → **Basic Auth**, username = `{{client_id}}`, password = `{{client_secret}}`. (Sends the `Authorization: Basic base64(id:secret)` header.)
- **Public client** (SPA/mobile, no secret): no auth header — put `client_id` in the body instead.
- Header `Content-Type: application/x-www-form-urlencoded` (Postman sets this for you when you choose that body type).

### 3a — Authorization Code + PKCE (the default)

| Body key | Value |
|---|---|
| `grant_type` | `authorization_code` |
| `code` | the `SHORTCODE` you caught from `/authorize` |
| `redirect_uri` | `{{redirect_uri}}` (must match exactly what you sent) |
| `code_verifier` | `{{code_verifier}}` (the secret from the pre-request script) |
| `client_id` | `{{client_id}}` *(public clients only; confidential send it via Basic auth)* |

### 3b — Client Credentials (machine-to-machine, no user)

| Body key | Value |
|---|---|
| `grant_type` | `client_credentials` |
| `scope` | e.g. `expenses.read` |

Confidential-only (there's no user; the secret *is* the identity). No `openid` scope — there's no one to identify, so you get an access token but no ID token.

### 3c — Refresh Token (stay logged in)

| Body key | Value |
|---|---|
| `grant_type` | `refresh_token` |
| `refresh_token` | `{{refresh_token}}` |
| `client_id` | `{{client_id}}` *(public clients)* |

### 3d — ROPC / Password — **for recognizing a legacy config only**

| Body key | Value |
|---|---|
| `grant_type` | `password` |
| `username` | `{{username}}` (lab test user) |
| `password` | `{{password}}` |
| `scope` | `openid` |

> **Do NOT build this out of curiosity against anything real.** This grant makes *your app* handle the user's raw password — the exact anti-pattern OAuth exists to kill, and it's removed in OAuth 2.1 (see Slide 20). It's in this note so you can recognize and migrate it, not adopt it.

### Save the tokens automatically (Tests tab on the `/token` request)

```js
const t = pm.response.json();
pm.collectionVariables.set("access_token",  t.access_token);
if (t.refresh_token) pm.collectionVariables.set("refresh_token", t.refresh_token);
if (t.id_token)      pm.collectionVariables.set("id_token",      t.id_token);
pm.test("got a token", () => pm.expect(t.access_token).to.be.a("string"));
```

Now `{{access_token}}` is live for every call below.

> **Attack ↔ defense:** this whole exchange is **back channel** — the browser never sees it, which is *why* the valuable tokens travel here and only the throwaway `code` travels out front. Keep `client_secret` in Postman's secret vars, never in a committed collection.

---

## Endpoint 4 — `userinfo_endpoint` (`/userinfo`): claims for the logged-in user

| Field | Value |
|---|---|
| Method | `GET` |
| URL | `{{userinfo_endpoint}}` |
| Auth | **Bearer Token** = `{{access_token}}` (Authorization tab → Bearer Token) |
| Body | none |

**Back:** a JSON object of identity claims (`sub`, `name`, `email`, …). The `sub` here must match the `sub` in your ID token — that's your stable user key.

> This is the first place you *use* an access token as a bearer credential. Anyone holding the token gets this data — which is exactly why access tokens are short-lived.

---

## Endpoint 5 — `introspection_endpoint` (`/introspect`): "is this token still good?"

For **opaque** tokens (reference strings, not JWTs) a resource server can't decode the token itself, so it asks the AS. **Form-encoded**, and the *caller* (a resource server / confidential client) authenticates.

| Field | Value |
|---|---|
| Method | `POST` |
| URL | `{{introspection_endpoint}}` |
| Auth | **Basic Auth** = `{{client_id}}` / `{{client_secret}}` |
| Body type | `x-www-form-urlencoded` |

| Body key | Value |
|---|---|
| `token` | `{{access_token}}` (or a refresh token) |
| `token_type_hint` | `access_token` *(optional, speeds lookup)* |

**Back:** `{ "active": true, "scope": "...", "sub": "...", "exp": ..., ... }` — or just `{ "active": false }` if it's expired/revoked/bogus.

> **Attack ↔ defense:** introspection is how a revoked token dies *immediately* for opaque tokens (JWTs, being self-contained, stay valid until `exp` unless you also introspect). The endpoint itself is protected — you must authenticate to ask, so an attacker can't fish token status anonymously.

---

## Endpoint 6 — `revocation_endpoint` (`/revoke`): kill a token now

| Field | Value |
|---|---|
| Method | `POST` |
| URL | `{{revocation_endpoint}}` |
| Auth | **Basic Auth** = `{{client_id}}` / `{{client_secret}}` (or `client_id` in body for public) |
| Body type | `x-www-form-urlencoded` |

| Body key | Value |
|---|---|
| `token` | `{{refresh_token}}` (revoke the refresh token to end the family) |
| `token_type_hint` | `refresh_token` |

**Back:** `200 OK` with an empty body on success (revocation is deliberately quiet — you get 200 even for an unknown token, so attackers can't probe validity here).

> This is your **Leaver-event kill switch** for tokens — the thing you reach for when offboarding or after a breach. Revoke the refresh token and the whole session family dies.

---

## Endpoint 7 — `device_authorization_endpoint` (`/device_authorization`): the device flow

For inputs-constrained devices (TV, CLI). Two steps.

**Step 1 — start it:**

| Field | Value |
|---|---|
| Method | `POST` |
| URL | `{{device_endpoint}}` |
| Auth | Basic (confidential) or `client_id` in body (public) |
| Body type | `x-www-form-urlencoded` |
| Body | `scope = openid profile` |

**Back:** `{ "device_code": "...", "user_code": "ABCD-1234", "verification_uri": "...", "interval": 5, "expires_in": 600 }`. You (playing the user) open `verification_uri` in a browser and type the `user_code`.

**Step 2 — poll `/token`** until the user finishes (reuses Endpoint 3, new grant type):

| Body key | Value |
|---|---|
| `grant_type` | `urn:ietf:params:oauth:grant-type:device_code` |
| `device_code` | the `device_code` from step 1 |
| `client_id` | `{{client_id}}` |

Poll every `interval` seconds: you'll get `authorization_pending` until the user approves, then the tokens.

> **Attack ↔ defense:** device-code **phishing** is live right now — an attacker starts the flow and tricks a victim into entering the `user_code`. Defense: only enable this grant on clients that truly need it, and short `expires_in`. (See Slide 18.)

---

## Endpoint 8 — `end_session_endpoint` (logout): back to the browser

Like `/authorize`, this is a **front-channel browser redirect**, not an API call. Open in a browser:

```
{{end_session_endpoint}}?id_token_hint={{id_token}}
  &post_logout_redirect_uri={{redirect_uri}}
```

The AS ends its session and redirects back. There's nothing useful to see in a raw Postman `GET`.

---

## The whole thing at a glance

| # | Endpoint | Method | Postman does it as | Body type | Auth |
|---|---|---|---|---|---|
| 0 | `/.well-known/openid-configuration` | GET | Raw request | — | none |
| 1 | `jwks_uri` | GET | Raw request | — | none |
| 2 | `authorization_endpoint` | GET (redirect) | **OAuth 2.0 helper** / browser | — | user login |
| 3 | `token_endpoint` | POST | Raw request | **form-urlencoded** | Basic (conf.) / body (public) |
| 4 | `userinfo_endpoint` | GET | Raw request | — | Bearer token |
| 5 | `introspection_endpoint` | POST | Raw request | form-urlencoded | Basic |
| 6 | `revocation_endpoint` | POST | Raw request | form-urlencoded | Basic / body |
| 7 | `device_authorization_endpoint` | POST | Raw request | form-urlencoded | Basic / body |
| 8 | `end_session_endpoint` | GET (redirect) | Browser | — | `id_token_hint` |

---

## Five things that will bite you (and the fix)

1. **JSON body on `/token`** → `400 invalid_request`. **Fix:** always `x-www-form-urlencoded` for `/token`, `/introspect`, `/revoke`.
2. **`redirect_uri` mismatch** → `invalid_grant` / `redirect_uri_mismatch`. **Fix:** the value at `/token` must be byte-identical to the one at `/authorize`, and both must be registered exactly on the client.
3. **Trying to `GET` `/authorize` in Postman and expecting tokens.** **Fix:** it's a browser flow — use the OAuth 2.0 helper.
4. **PKCE verifier/challenge mismatch** → `invalid_grant`. **Fix:** generate the pair once in a pre-request script, send the *challenge* to `/authorize` and the *verifier* to `/token`; don't regenerate between the two.
5. **Confidential-client auth in the wrong place** → `invalid_client`. **Fix:** pick one — Basic auth header *or* `client_secret` in the body, matching what the client is configured for (`token_endpoint_auth_method`).

---

## Safety rules for this note (non-negotiable at FinCo)

- **Lab only.** Point Postman at your Keycloak lab, or a sandbox tenant you're authorized to use — **never a FinCo production AS**, and never a third party's.
- **No real credentials, no real tokens.** Use throwaway lab users. A live access/refresh token is a password-equivalent — don't paste it into a shared collection, a screenshot, or a commit.
- **Don't commit the collection with secrets.** Keep `client_secret`/`password` in Postman **secret** environment vars; the repo `.gitignore` already blocks `.env`, keys, and captures — keep it that way.
- **Decode, don't trust.** When you inspect a JWT (paste into jwt.io's *offline* mode or decode locally), remember it's only base64 — the signature check against `jwks_uri` is what makes it trustworthy.

---

## Next

- Run the real flow end-to-end against your own IdP: [Lab 01 — Keycloak as your own IdP](../labs/01-keycloak-idp/README.md).
- Cross-reference the wire-level trace: [note 19](19-oauth2-in-practice.md) shows the same requests as raw HTTP.
- Interview/revision pointers for every endpoint and attack: [note 21](21-oauth2-complete-reference.md).
