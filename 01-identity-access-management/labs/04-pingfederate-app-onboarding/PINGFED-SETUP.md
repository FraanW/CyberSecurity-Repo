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

### 1.7 Configuring without the metadata file — by hand

**Why you'd do this.** Importing metadata is the fast path, but a real FinCo ticket often does not
come with a metadata URL — a partner sends you three values in an email instead, or your lab
firewall blocks the import fetch. Knowing the manual path is what separates "I clicked a wizard"
from "I understand what the wizard was doing." Every field below is something the imported file
would have filled in for you — here you type it yourself, from the app's **Step 1 table**.

**Applications → Integration → SP Connections → Create Connection**

| Screen | What to do |
|---|---|
| Connection Template | **Do not use a template** |
| Connection Type | tick **Browser SSO Profiles**, protocol **SAML 2.0** |
| Connection Options | tick **Browser SSO** |
| Import Metadata | choose **None** — this is the whole difference from §1.2 |
| General Info | **Partner's Entity ID** = the app's **SP entity ID** from Step 1 (looks like `https://pingfed-saml-sp.onrender.com/saml2/service-provider-metadata/pingfed`). **Connection Name** = a human label, e.g. `IAM Lab SAML SP (manual)` |
| Browser SSO | continue as in §1.3 |

Then, inside **Browser SSO → Protocol Settings**, add the two endpoints by hand:

| Field | Value | Where you got it |
|---|---|---|
| Assertion Consumer Service URL | the app's **ACS URL** | Step 1 table, `assertionConsumerServiceUrl` |
| Binding | **POST** | Step 1 table, `assertionConsumerServiceBinding` |
| Default | tick it — there is only one ACS URL, make it the default | — |
| Single Logout Service URL | the app's **Single Logout URL** | Step 1 table, `singleLogoutServiceUrl` |
| Single Logout Response URL | same value | same |

> ⚠️ **Gotcha — copy, do not retype.** The ACS URL is matched exactly. A trailing slash or a typo
> in the hostname and PingFederate rejects the assertion with a destination mismatch. Copy it
> from the app, do not read it off a screenshot and type it.

Finally, in **Credentials → Signature Verification Settings**, upload the SP's **public**
certificate by hand:

1. **Manage Signature Verification Settings** → **Unanchored** (or **Anchored**, if your lab
   already has a trust chain set up — Unanchored is simplest for a lab).
2. **Import Certificate** → paste the block from the app's **Step 1 → SP signing certificate**
   panel, or upload the `.crt` file next to `sp-signing.key` if you generated one with
   `scripts/generate-sp-keypair.sh`.
3. This is the **same certificate** the imported-metadata path would have picked up automatically
   — you are just telling Ping about it one field at a time instead of one file at a time.

Everything else — Attribute Contract, Authentication Source Mapping, signing settings for
*outgoing* assertions — is identical to §1.3 and §1.4. Metadata import only ever fills in the
*protocol* fields (entity ID, ACS URL, certificate); it never touches authentication, which is
§1.8 below regardless of which path you took to get here.

### 1.8 Authentication Policies and IdP Adapters — what actually checks the password

**The gap metadata import never fills.** SP metadata tells PingFederate *where to send the
assertion*. It says nothing about *how to authenticate the user* — that is a separate piece,
configured once and then reused by every connection. This is the part of the console new IAM
hires find most confusing, because it is three screens that all sound like the same thing.

**TL;DR — three pieces, in the order data flows through them:**

```
user's browser
     │  (username + password, or Windows SSO, or whatever)
     ▼
IdP Adapter instance            ← "the thing that actually checks the credential"
     │  (calls out to a Credential Validator to check it)
     ▼
Authentication Policy            ← "the if-this-then-that routing between adapters"
     │  (produces a Policy Contract — a fixed set of attribute names)
     ▼
SP Connection's Authentication Source Mapping   ← "which policy/adapter feeds this connection"
     │  (maps policy contract attributes → the SAML attribute contract)
     ▼
signed SAML assertion, sent to the SP
```

**1. Create a Password Credential Validator** — the thing that actually checks a password against
something. For a lab, the simplest is a **Simple Username Password Credential Validator**, which
stores users right inside PingFederate — no LDAP needed.

**Authentication → Integration → Password Credential Validators → Create New Instance**

| Field | Value |
|---|---|
| Type | **Simple Username Password Credential Validator** |
| Instance Name | `Lab-Local-Users` |
| Users | **Add a Row** → username `labuser`, password whatever you like, confirm |

> This is a lab-only stand-in for what FinCo really has here: an **LDAP Datastore** or Active
> Directory bind. The rest of the pipeline below does not care which one feeds it — swap this one
> instance for a real directory later and nothing downstream changes. That decoupling is the
> whole design.

**2. Create an IdP Adapter instance** — the thing the user actually interacts with in the browser.

**Authentication → Integration → IdP Adapters → Create New Instance**

| Screen | What to set |
|---|---|
| Type | **HTML Form Adapter** |
| Instance Name | `Lab-HTML-Form` |
| IdP Adapter | **Credential Validators** → add `Lab-Local-Users` from step 1 |
| Adapter Attributes | tick **username** as the **pseudonym** (the value that becomes the adapter's output attribute) |
| Adapter Contract Mapping | leave default — `username` flows straight through |

**✅ Checkpoint.** Back on the IdP Adapters list, your new instance shows **Lab-HTML-Form**, type
**HTML Form Adapter**. That is now a reusable building block — any connection or policy can use it.

**3. Build the Authentication Policy** — the routing logic that decides which adapter runs, and
shapes its output into a fixed contract every downstream connection can consume.

**Authentication → Policies → Policy Contracts → Create New Policy Contract** (if you do not
already have one)

| Field | Value |
|---|---|
| Contract Name | `Lab-Policy-Contract` |
| Extended Attributes | add `email`, `firstName` — these are the attributes your SP connection will consume in §1.3 |

**Authentication → Policies → Sign-On Policies → Create Policy**

This is a small tree, not a form — click through it like a flowchart:

1. **Start** node → **Add Authentication Source** → pick your adapter, `Lab-HTML-Form`.
2. On the **Success** branch → **Restart, Fail, or Done** → choose **Done**, and select
   **Lab-Policy-Contract** as the contract to fulfil. Map `subject` ← the adapter's `username`
   output, plus `email` / `firstName` if your credential validator supplies them (the Simple
   Username Password Credential Validator does not — leave them blank or hardcode a lab value;
   a real LDAP validator would supply them here).
3. Save. Name the whole policy `Lab-Sign-On-Policy`.

**4. Point the SP connection at the policy** — this is the one line in §1.3 that ties it all
together:

**Browser SSO → Configure Browser SSO → Authentication Source Mapping**

| Field | Value |
|---|---|
| Authentication Source | **Adapter Instance** (direct) if you skipped policies, or **Authentication Policy Contract** if you built one — pick **Authentication Policy Contract** → `Lab-Policy-Contract` |
| Attribute Contract Fulfilment | `SAML_SUBJECT` ← `Lab-Policy-Contract.subject`; `email` ← `Lab-Policy-Contract.email`; `firstName` ← `Lab-Policy-Contract.firstName` |

**✅ Checkpoint — the whole chain, end to end.** Log in via the app's **Step 3**. You land on
PingFederate's **HTML Form Adapter** login page (not a redirect straight through), enter
`labuser` and its password, and land back on the app with a SAML session. If you skip straight to
success without ever seeing a login form, the adapter did not run — check that the policy's Start
node actually points at `Lab-HTML-Form`.

**Job tie-in — why FinCo splits it this way.** A Credential Validator, an Adapter, and a Policy
Contract are three separate objects on purpose: one directory can back many adapters (password,
Kerberos, certificate), one adapter can be reused by every policy, and one policy contract can
feed every SP connection without each connection needing to know *how* the user was authenticated
— only *that* they were, and what came out. When someone at FinCo says "add step-up MFA before
the payments app," this is the exact tree they are editing: a new branch in the Sign-On Policy
that routes through an MFA adapter before reaching **Done**.

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
