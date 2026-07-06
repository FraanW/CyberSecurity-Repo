# Lab 01 — Keycloak as your own Identity Provider (OIDC end to end)

> **Lefler's build, Janus's curriculum.** You'll run your **own IdP** (Keycloak — the open-source stand-in for Okta/Entra ID) and drive a **complete OpenID Connect login by hand**, watching every parameter and every token. When you're done, the [OAuth/OIDC note](../../notes/03-oauth-oidc-deep-dive.md) will feel obvious. **Authorized-lab-only:** everything here runs on your machine.

- **Time:** 45–75 min · **Difficulty:** beginner-friendly · **Prereqs:** [note 03](../../notes/03-oauth-oidc-deep-dive.md)
- **You'll learn:** realms/clients/users, the Authorization Code + PKCE flow, access vs ID vs refresh tokens, JWT decoding, the discovery document, redirect-URI security.

---

## 0. Prerequisites

1. **Docker Desktop for Windows** — install from docker.com, launch it, then verify in PowerShell:
   ```powershell
   docker --version
   docker compose version
   ```
   Both should print versions. (If Docker isn't installed and you'd rather not, tell **Lefler** — there's a native-Java fallback, but Docker is by far the smoothest.)
2. A browser with dev tools (Chrome/Firefox/Edge).
3. This lab folder (contains `docker-compose.yml`).

---

## 1. Start your IdP

From this folder:
```powershell
docker compose up -d
docker compose logs -f keycloak      # watch until you see "started in ..." then Ctrl+C
```
Open **http://localhost:8080/** → **Administration Console** → log in with **admin / admin**.

> ✅ **Checkpoint:** you're looking at the Keycloak admin console. This is your personal Okta/Entra.

---

## 2. Create a realm (your isolated identity world)

A **realm** is a self-contained set of users, clients, and config (think: one company/tenant).

1. Top-left realm dropdown (says **Keycloak**) → **Create realm**.
2. Realm name: **`finco-lab`** → **Create**.
3. Make sure the realm dropdown now shows **finco-lab** for everything below.

---

## 3. Create a user (your test identity)

1. **Users** → **Add user**. Username **`farhaan`**, Email `farhaan@example.com`, First/Last name, **Email verified: On** → **Create**.
2. Open the user → **Credentials** tab → **Set password** → password `Passw0rd!`, **Temporary: Off** → **Save**.

> This user lives in Keycloak's built-in directory. In note 04 you'll see how a real IdP instead reads users from **LDAP/AD/Entra** — Keycloak can do that too (User federation), a great follow-up.

---

## 4. Register an OIDC client (the app that will log the user in)

1. **Clients** → **Create client**. Client type **OpenID Connect**, Client ID **`oidc-lab-app`** → **Next**.
2. **Client authentication: On** (this makes it a *confidential* client with a secret) · **Standard flow: On** (that's Authorization Code) → **Next**.
3. **Valid redirect URIs:** `http://localhost:9999/callback` → **Save**.
   - *Why 9999?* Nothing runs there — we'll intercept the redirect from the browser URL bar. This is a classic manual-flow trick.
4. **Credentials** tab → copy the **Client secret** (you'll paste it in step 6).

> 🔒 **Security note you'll actually use:** that redirect URI is an **exact-match allow-list**. Try logging in later with a *different* `redirect_uri` and Keycloak refuses — that's the defense against the redirect-URI attacks from [note 03 §10](../../notes/03-oauth-oidc-deep-dive.md#10-attacks--defenses-table).

---

## 5. Step 1 of the flow — get an authorization code (in the browser)

Paste this into your browser address bar (one line), then log in as **farhaan / Passw0rd!**:

```
http://localhost:8080/realms/finco-lab/protocol/openid-connect/auth?response_type=code&client_id=oidc-lab-app&redirect_uri=http://localhost:9999/callback&scope=openid%20profile%20email&state=xyz123
```

After login the browser redirects to `http://localhost:9999/callback?code=...&state=xyz123` and shows **"can't reach this page"** — that's expected. **Look at the address bar** and copy the long **`code`** value (everything between `code=` and `&state`).

> Notice `scope=openid ...` — that `openid` is the switch that makes this **OIDC** (authentication), not plain OAuth. And `state=xyz123` comes back unchanged: that's your **CSRF** check.

---

## 6. Step 2 of the flow — exchange the code for tokens (back channel)

In PowerShell (be quick — the code is single-use and expires in ~60s):

```powershell
$body = @{
  grant_type    = 'authorization_code'
  code          = '<PASTE_CODE_HERE>'
  redirect_uri  = 'http://localhost:9999/callback'
  client_id     = 'oidc-lab-app'
  client_secret = '<PASTE_CLIENT_SECRET_HERE>'
}
$tokens = Invoke-RestMethod -Method Post -Body $body `
  -Uri 'http://localhost:8080/realms/finco-lab/protocol/openid-connect/token'
$tokens | Format-List
```

You should get back **`access_token`**, **`id_token`**, **`refresh_token`**, `expires_in`, `token_type: Bearer`, `scope`.

> 💡 **This is the whole point of Authorization Code flow:** the *code* travelled through the exposed browser; the valuable *tokens* came over this direct, server-to-server call an attacker can't see.

---

## 7. Decode the tokens (see what's inside)

Paste this helper into the same PowerShell session, then decode:

```powershell
function Decode-Jwt($jwt) {
  $jwt.Split('.')[0..1] | ForEach-Object {
    $s = $_.Replace('-','+').Replace('_','/')
    switch ($s.Length % 4) { 2 { $s += '==' } 3 { $s += '=' } }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s)) | ConvertFrom-Json | ConvertTo-Json
  }
}
"===== ID TOKEN =====";     Decode-Jwt $tokens.id_token
"===== ACCESS TOKEN ====="; Decode-Jwt $tokens.access_token
```

**Look for and compare (this is the money moment):**
- **ID token** — `iss` (issuer), `sub` (stable user id — *not* the email!), `aud` = `oidc-lab-app` (**it's for the client**), `exp`, `nonce`, `email`, `preferred_username`.
- **Access token** — note `aud`/`azp`, `scope`, and `realm_access.roles`. **It's meant for an API**, not for identifying the user.

> That difference — **ID token tells your app who logged in; access token authorizes API calls** — is the #1 confusion from [note 03 §8](../../notes/03-oauth-oidc-deep-dive.md#8-openid-connect--the-authentication-layer). You just saw it firsthand.

---

## 8. Use the access token & read the discovery doc

```powershell
# Call the /userinfo endpoint as the "API" would, presenting the access token:
Invoke-RestMethod -Uri 'http://localhost:8080/realms/finco-lab/protocol/openid-connect/userinfo' `
  -Headers @{ Authorization = "Bearer $($tokens.access_token)" }

# The discovery document — the map of every endpoint + the signing keys (JWKS):
Invoke-RestMethod -Uri 'http://localhost:8080/realms/finco-lab/.well-known/openid-configuration' |
  Select-Object issuer, authorization_endpoint, token_endpoint, userinfo_endpoint, jwks_uri
```

> `jwks_uri` is where an app fetches the IdP's **public keys** to verify your ID token's signature (and auto-handle key rotation). This is OIDC's clean replacement for SAML's manual metadata/cert exchange.

---

## 9. Realm endpoints reference (bookmark this)

| Purpose | URL (realm = `finco-lab`) |
|---|---|
| Authorize (login) | `…/realms/finco-lab/protocol/openid-connect/auth` |
| Token | `…/realms/finco-lab/protocol/openid-connect/token` |
| UserInfo | `…/realms/finco-lab/protocol/openid-connect/userinfo` |
| JWKS (public keys) | `…/realms/finco-lab/protocol/openid-connect/certs` |
| Discovery | `…/realms/finco-lab/.well-known/openid-configuration` |
| Logout | `…/realms/finco-lab/protocol/openid-connect/logout` |

Base = `http://localhost:8080`.

---

## 10. Level up — Authorization Code + **PKCE** (public client)

Mobile/SPA apps can't keep a secret, so they use **PKCE** instead. Do it once by hand and PKCE will never be mysterious again.

1. Create a second client **`spa-lab-app`**: Client authentication **Off** (public), Standard flow **On**, redirect URI `http://localhost:9999/callback`. In **Advanced** → *Proof Key for Code Exchange Code Challenge Method* → **S256** → Save.
2. Generate a PKCE verifier + challenge:
   ```powershell
   $b = New-Object 'byte[]' 32
   [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
   $verifier  = [Convert]::ToBase64String($b).TrimEnd('=').Replace('+','-').Replace('/','_')
   $sha       = [Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::ASCII.GetBytes($verifier))
   $challenge = [Convert]::ToBase64String($sha).TrimEnd('=').Replace('+','-').Replace('/','_')
   "verifier =  $verifier"; "challenge = $challenge"
   ```
3. Authorize in the browser (note `code_challenge` + `code_challenge_method=S256`, and **no client secret anywhere**):
   ```
   http://localhost:8080/realms/finco-lab/protocol/openid-connect/auth?response_type=code&client_id=spa-lab-app&redirect_uri=http://localhost:9999/callback&scope=openid&state=abc&code_challenge=<PASTE_CHALLENGE>&code_challenge_method=S256
   ```
4. Exchange, sending the **verifier** instead of a secret:
   ```powershell
   $body = @{ grant_type='authorization_code'; code='<CODE>'; redirect_uri='http://localhost:9999/callback'
              client_id='spa-lab-app'; code_verifier=$verifier }
   Invoke-RestMethod -Method Post -Body $body `
     -Uri 'http://localhost:8080/realms/finco-lab/protocol/openid-connect/token' | Format-List
   ```

> **What you just proved:** a thief who stole the `code` from the browser **can't exchange it** without your `code_verifier` — which never left your machine. That's PKCE ([note 03 §6](../../notes/03-oauth-oidc-deep-dive.md#6-pkce--the-piece-everyone-asks-about)).

---

## 11. Purple-team & attack/defense (repo rule: pair them)

- **See the audit trail:** Realm settings → **Sessions**/user **Sessions**, and enable **Realm settings → Events** (login events) → then log in and watch events appear. That's the raw material your SOC (**Heimdall**) turns into detections. Ask Heimdall: *"what would a login-anomaly alert look like from these events?"*
- **Break redirect-URI security on purpose:** repeat step 5 with `redirect_uri=http://evil.example/callback`. Keycloak refuses — feel the **exact-match allow-list** defense working.
- **Token attacks (study on this lab only):** ask **Loki** to walk `alg:none` / RS256→HS256 confusion against a token here; the fix (pin algorithms, verify via JWKS) is in [note 03 §9](../../notes/03-oauth-oidc-deep-dive.md#9-jwt-internals-the-format-under-id-tokens-and-many-access-tokens) and hands-on in `../../../04-cryptography/` Lab 9.

---

## 12. What you learned & how it maps to FinCo

- Keycloak realm = an Okta org / Entra **tenant**. Client = an **app registration**. Roles/scopes = **API permissions**.
- You ran the exact flow that logs employees into modern apps — and saw why access/ID tokens differ.
- Every "app integration" ticket you'll get is this machinery: a wrong redirect URI, a missing scope, an expired signing key, an audience mismatch.

**Next:** do [Lab 02 — SAML assertion anatomy](../02-saml-assertion-anatomy/README.md) to see the *older* federation protocol you'll debug most, then compare the two.

---

## 13. Cleanup

```powershell
docker compose down          # stop & remove the container (state is discarded)
```
Run `docker compose up -d` any time to start fresh and practice the setup again — repetition is the point.

*Built for Farhaan's IAM track · authorized-lab-only 🔐*
