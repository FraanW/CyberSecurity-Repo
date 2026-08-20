# PingFederate console walk-through — onboarding both lab apps

> **The click-path.** [`README.md`](README.md) is the lab; this is the checklist you keep open on
> the second monitor while you work in the admin console. Menu names are PingFederate 11.x /
> 12.x; older builds nest the same screens slightly differently, but the *fields* are identical.
>
> **Authorized-lab-only.** Your own PingFederate, test connections, dummy users.

**Before you start, have these three things open:**

1. The PingFederate **admin console**, signed in.
2. Your deployed **saml-sp** app (for the metadata file).
3. Your deployed **oauth-client** app (for the redirect URI).

---

## §1 — The SAML app, as an SP Connection

You are telling PingFederate: *"here is one more app I should be the IdP for."*

### 1.1 Get the metadata file

Open the saml-sp app → **Step 1** → **⬇ Download SP metadata XML**.

Or straight from the command line:

```bash
curl -sO https://pingfed-saml-sp.onrender.com/api/sp-metadata.xml
```

That file contains everything Ping needs, so you will not have to type any of it:

| Inside the file | What it is |
|---|---|
| `entityID` | who this app claims to be |
| `AssertionConsumerService Location` | **the ACS URL** — where to POST the assertion |
| `SingleLogoutService Location` | where to send logout messages |
| `KeyDescriptor use="signing"` | our **public** certificate, so Ping can verify our AuthnRequests |

> ⚠️ **Download it *after* you have set a fixed signing keypair** (README §3). With the throwaway
> keypair, the certificate inside this file changes on every restart and your connection breaks
> on the next redeploy.

### 1.2 Create the connection

**Applications → Integration → SP Connections → Create Connection**

| Screen | What to do |
|---|---|
| Connection Template | **Do not use a template** |
| Connection Type | tick **Browser SSO Profiles**, protocol **SAML 2.0** |
| Connection Options | tick **Browser SSO** |
| Import Metadata | **File** → choose `pingfed-sp-metadata.xml` |
| Metadata Summary | just confirm what it read |
| General Info | the **Connection Name** is a label for humans — `IAM Lab SAML SP` is fine. Leave the entity ID as imported |

### 1.3 Browser SSO

**Browser SSO → Configure Browser SSO**

| Screen | What to set |
|---|---|
| SAML Profiles | tick **IdP-Initiated SSO** and **SP-Initiated SSO** (the lab app uses SP-initiated; enabling both lets you try each) |
| Assertion Lifetime | leave the defaults (5 minutes before / after) |
| Assertion Creation → Identity Mapping | **Standard** |
| Attribute Contract | `SAML_SUBJECT` is there already. **Add `email` and `firstName`** — they show up in the app's Step 4 table, which is how you prove the contract works |
| Authentication Source Mapping | map your existing **IdP adapter** (whatever your lab uses to authenticate — HTML Form is typical) |
| Attribute Contract Fulfilment | `SAML_SUBJECT` ← your adapter's username/subject; `email` ← the adapter's or datastore's mail attribute |
| Protocol Settings → Assertion Consumer Service URL | **already imported.** Confirm it matches the app's Step 1 table exactly |
| Allowable SAML Bindings | tick **POST** (and **Redirect** if you want to experiment) |
| Signature Policy | tick **Require AuthN requests to be signed** — the app signs them by default |
| Encryption Policy | **None** to start. Turn it on later; the app can decrypt, it holds the matching private key |

### 1.4 Credentials

**Credentials → Configure Credentials**

| Screen | What to set |
|---|---|
| Digital Signature Settings | pick your IdP **signing certificate**, and **RSA SHA256** |
| Signature Verification Settings | **already imported from the metadata** — this is our SP certificate |

### 1.5 Save and enable

Save the connection, then set its status to **Active** on the SP Connections list.

**✅ Checkpoint.** In the app: **Step 3 → Log in with PingFederate**. You should land back on the
dashboard with *"via PingFederate (SAML)"* and a populated **Step 4**.

### 1.6 If the login fails

Read **`server/default/log/audit.log`** first — one line per SSO transaction, and it names the
connection and the failure. `server.log` has the stack trace when you need it.

| audit.log says | Means | Fix |
|---|---|---|
| `Signature verification failed` | Ping has a stale copy of our certificate | Re-import the metadata after fixing the SP keypair |
| `Unknown connection` / entity ID mismatch | The app's entity ID changed | It defaults to the app's metadata URL — if you set `PUBLIC_BASE_URL` after creating the connection, it moved. Re-import |
| Nothing at all appears | The AuthnRequest never arrived | Check the app's Step 2 table — is the SSO URL right? Is PingFederate reachable from your browser? |

---

## §2 — The OAuth app, as an OAuth Client

You are telling PingFederate: *"here is one more app allowed to ask me for tokens."*

### 2.1 Get the redirect URI

Open the oauth-client app → **Step 1** → copy **Redirect URI**. It looks like:

```
https://pingfed-oauth-client.onrender.com/login/oauth2/code/pingfed
```

> ⚠️ **Copy it, do not retype it.** Redirect URIs are matched **character for character**. A
> trailing slash, `http` instead of `https`, or a typo in the hostname all produce
> `invalid_redirect_uri` — the most common OAuth onboarding failure there is.

### 2.2 Create the client

**Applications → OAuth → Clients → Add Client**

| Field | Value |
|---|---|
| Client ID | `finco-lab-oauth-client` (anything; you will paste it into Render) |
| Client Name | `IAM Lab OAuth Client` |
| Client Authentication | **Client Secret** → **Generate Secret**, and copy it now — some builds only show it once |
| Redirect URIs | paste the URI from 2.1 |
| Allowed Grant Types | **Authorization Code**, **Refresh Token**, and **Client Credentials** if you want Step 5's first button to work |
| Default Access Token Manager | pick one — **this is the setting that decides JWT vs reference token** (see 2.4) |
| OpenID Connect → Policy | pick your OIDC policy, so an **ID token** is issued |
| Persistent Grants Expiration | leave the default |
| Require PKCE | tick it if your build offers it — the app sends PKCE by default |

### 2.3 Wire it into Render

Your service → **Environment** → set, then **Save** (which redeploys):

```
LAB_OAUTH_ISSUER_URI=https://<your-pingfederate-host>
LAB_OAUTH_CLIENT_ID=finco-lab-oauth-client
LAB_OAUTH_CLIENT_SECRET=<the secret you copied>
LAB_OAUTH_INTROSPECTION_URI=https://<your-pingfederate-host>/as/introspect.oauth2
LAB_OAUTH_END_SESSION_URI=https://<your-pingfederate-host>/idp/startSLO.ping
```

Scopes default to `openid,profile,email`. Add `offline_access` if you want a refresh token:

```
LAB_OAUTH_SCOPES=openid,profile,email,offline_access
```

**✅ Checkpoint.**

```bash
curl -s https://pingfed-oauth-client.onrender.com/api/config | jq '.endpoints.resolvedVia'
```

`"OIDC discovery (issuer URI)"` means PingFederate answered on
`/.well-known/openid-configuration` and filled in every endpoint. If it says `"explicit
settings"`, discovery failed — check the service logs, then set the four endpoints by hand:

```
LAB_OAUTH_AUTHORIZATION_URI=https://<host>/as/authorization.oauth2
LAB_OAUTH_TOKEN_URI=https://<host>/as/token.oauth2
LAB_OAUTH_JWKS_URI=https://<host>/pf/JWKS
LAB_OAUTH_USERINFO_URI=https://<host>/idp/userinfo.openid
```

### 2.4 The one setting worth understanding: Access Token Manager

The **Access Token Manager** attached to your client decides what an access token *is*:

| ATM type | The token is | An API validates it by | Trade-off |
|---|---|---|---|
| **JWT** | a signed, self-contained JSON document | checking the signature against the **JWKS** — offline, no network call | Fast. But it stays valid until it expires; you cannot revoke it mid-flight |
| **Reference** (internally managed) | an opaque random string | calling the **introspection endpoint** on every request | Revocable instantly. But every API call now depends on PingFederate being up |

Log in, then open **Step 4** in the app. It tells you which one you got, in plain words. Then
press **Introspect** in Step 5 and watch what comes back. Switch the ATM in the console, log in
again, and compare — that ten-minute experiment explains more than any diagram.

> **Job tie-in:** when an API team at FinCo asks *"why do we need to call Ping on every request?"*,
> this dropdown is the answer, and this lab is where you can show them.

### 2.5 A second client for machine-to-machine

Client Credentials is a *different kind of client* — no user, no browser, no redirect URI. Model
that properly instead of reusing the interactive one:

1. **Add Client** → Client ID `finco-lab-machine`, **Client Credentials** grant only, **no** redirect URIs.
2. Give it its own scope, e.g. `finco.payments.read`.
3. In Render:

```
LAB_OAUTH_MACHINE_CLIENT_ID=finco-lab-machine
LAB_OAUTH_MACHINE_CLIENT_SECRET=<its secret>
LAB_OAUTH_MACHINE_SCOPES=finco.payments.read
```

Then, from anywhere — no login, no cookie, no browser:

```bash
curl -s -X POST https://pingfed-oauth-client.onrender.com/api/client-credentials | jq .
```

### 2.6 If it fails

| PingFederate returns | Means | Fix |
|---|---|---|
| `invalid_redirect_uri` | Not a character-for-character match | Compare `/api/config → client.redirectUri` against the console field |
| `invalid_client` | Wrong ID, wrong secret, or wrong auth method | Try `LAB_OAUTH_CLIENT_AUTH_METHOD=client_secret_post` |
| `unauthorized_client` | The grant is not allowed for this client | Tick the grant type in the console |
| `invalid_scope` | The client cannot request that scope | Add the scope to the client, or drop it from `LAB_OAUTH_SCOPES` |
| No `id_token` in the response | No OIDC policy on the client | Attach one, and keep `openid` in the scopes |
| No `refresh_token` | Scope or grant missing | Add `offline_access` **and** allow the Refresh Token grant |

---

## §3 — Clean up when you are done

1. Delete the **SP connection**.
2. Delete both **OAuth clients**.
3. Delete the **Render services**.
4. Delete your local `sp-signing.key`.

A forgotten test client with a known secret, still enabled, is a genuine audit finding. Deleting
it is part of doing the lab, not an afterthought.
