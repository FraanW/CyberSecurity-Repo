# Lab 05 — IdP-initiated SSO: many banks, one PingFederate, one app (OpenToken)

> **Lefler built it, Janus specified it.** Two pretend banks sign their own users in, hand them to
> *your* PingFederate, and PingFederate hands them to one app — **Commerce Control** — with an
> **OpenToken**. It mirrors what the service engineers configure by hand today, so you can show the
> team every moving part before anyone automates it.
>
> **Authorized-lab-only.** Your own PingFederate on an evaluation licence, dummy users, made-up
> banks. No FinCo configuration, certificates, passwords or real bank names go anywhere near this.

- **Time:** ~45 min first run · **Difficulty:** intermediate
- **Machine:** an **Ubuntu laptop** with Docker and about 4 GB of free memory (commands are Bash).
  The banks and the app run on **Render's free tier**.
- **You need:** a Render account, and a **Ping DevOps user and key** (free registration from your
  Ping Identity profile page — use a personal email).
- **Read first:** [note 35 — IdP-initiated SSO with PingFederate as the SP](../../notes/06-platforms-and-gateways/35-idp-initiated-sso-pingfed-as-sp.md).
- **You'll be able to:** explain and demo what the bank, the IAM team and the app team each configure.

---

## TL;DR

| Piece | Plays the role of | Runs on | Folder |
|---|---|---|---|
| Keycloak realm `bank-a` | Bank A's login server (their **IdP**). **Sends** RelayState. | Render | `banks/` |
| Keycloak realm `bank-b` | Bank B's login server. Sends **no** RelayState, and names its attributes the way Entra does. | Render | `banks/` |
| PingFederate | FinCo's federation server, acting as **SP** to both banks | Your laptop + tunnel | `pingfederate/` |
| Commerce Control | The app team's app. One URL base for every bank. | Render | `commerce-control/` |

```
 Bank A tile ─► Keycloak bank-a ─┐  SAML response (+ RelayState)
                                 ├─► PingFederate /sp/ACS.saml2 ─► OpenToken SP adapter
 Bank B tile ─► Keycloak bank-b ─┘  SAML response (no RelayState)          │
                                                                           ▼
                    Commerce Control   /sso/launch/org-1001   (Bank A)
                                       /sso/launch/org-2002   (Bank B)
```

**The org id in the landing URL is how the app knows which bank the user came from.**

### What is proven and what is not

| Part | Status |
|---|---|
| Both Keycloak realms import, and each one produces a signed IdP-initiated SAML response aimed at `/sp/ACS.saml2` | ✅ Tested (Keycloak 26.6.4) |
| Commerce Control accepts good tokens and rejects bad ones | ✅ Tested (`npm test`) |
| The app's OpenToken code agrees with a second, independent OpenToken implementation | ✅ Tested |
| The PingFederate console steps in §3 | ⚠️ **Written from knowledge of the product, not yet run.** Screen names can differ by version. You are the first to run them. |
| The app decoding a token minted by a *real* PingFederate | ⚠️ Not yet run — it is the first thing §4 proves. |

---

## 0. Why each bank looks different (on purpose)

The two banks differ in exactly the ways real ones do, so PingFederate has real work to do.

| | Bank A | Bank B |
|---|---|---|
| RelayState | Sends `…/sso/launch/org-1001` | Sends nothing |
| So the landing URL comes from | the bank | the connection's **Default Target URL** |
| Attribute names | `email`, `givenName`, `surname` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress`, `…/givenname`, `…/surname` |
| Dummy users | `alice` (in the app), `carol` (**not** in the app) | `bob` (in the app) |

---

## 1. Start PingFederate on the laptop

```bash
cd 01-identity-access-management/labs/05-idp-initiated-sso-opentoken/pingfederate
cp env.example .env          # .env is git-ignored
nano .env                    # paste your Ping DevOps user + key, choose an admin password
docker compose up -d
docker compose logs -f pingfederate
```

✅ **Checkpoint:** after 2–4 minutes the log settles and
`https://localhost:9999/pingfederate/app` shows a sign-in page (accept the self-signed certificate
warning). Sign in as `administrator` with the password from `.env`.

Now get the public URL of the runtime:

```bash
docker compose logs tunnel | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' | tail -1
```

Write it down as **`PF_PUBLIC_URL`**.

> **Gotchas.**
> - `Unable to download evaluation product.lic` in the log means the DevOps user or key is wrong.
> - The tunnel URL **changes every time the `tunnel` container restarts.** Leave it running for the
>   whole demo. If it changes, redo §2 step 3 and §3.1.
> - The tunnel publishes only port 9031 (logins). The admin console stays on your laptop.
> - Already have a lab PingFederate reachable from the internet (as in lab 04)? Use that and skip
>   this section.

---

## 2. Deploy the banks and the app to Render

1. Render dashboard → **New → Blueprint** → this repo. It offers `lab05-banks` and
   `lab05-commerce-control` (and the lab 04 services, which you can skip).
2. Deploy both. Note their URLs: **`BANKS_URL`** and **`APP_URL`**.
3. Set the environment variables, then **restart** each service:

| Service | Variable | Value |
|---|---|---|
| `lab05-banks` | `PF_PUBLIC_URL` | the tunnel URL from §1 |
| `lab05-banks` | `APP_ORIGIN` | `APP_URL` |
| `lab05-banks` | `LAB_USER_PASSWORD` | a password for alice, carol and bob |
| `lab05-commerce-control` | `OPENTOKEN_PASSWORD` | a long random string. **You will type the same value into PingFederate in §3.2.** |
| `lab05-commerce-control` | `BANKS_ORIGIN` | `BANKS_URL` |

✅ **Checkpoint:** `APP_URL` shows *"You are not signed in"* with two bank tiles.
`BANKS_URL/realms/bank-a/protocol/saml/descriptor` returns XML.

> **Gotcha.** Free Render services sleep. The first click after a pause can take about a minute.
> Open both URLs a few minutes before you present.

---

## 3. Configure PingFederate (what the service engineer does by hand)

Menu names are PingFederate 11.x / 12.x. ⚠️ See the status table above: this section has not been
run yet, so treat a screen that looks different as a difference in naming, and tell me.

### 3.1 Tell PingFederate its own public name

**System → Server → Protocol Settings → Federation Info**

| Field | Value |
|---|---|
| Base URL | `PF_PUBLIC_URL` |
| SAML 2.0 Entity ID | `lab05:finco:pingfederate:sp` |

**Why:** each bank's response says "this is for `PF_PUBLIC_URL/sp/ACS.saml2`, audience
`lab05:finco:pingfederate:sp`". PingFederate rejects it if either does not match itself.

### 3.2 One OpenToken SP adapter — shared by every bank

**Applications → Integration → SP Adapters → Create New Instance**

| Field | Value |
|---|---|
| Instance Name / ID | `Commerce Control OpenToken` / `ccOpenToken` |
| Type | OpenToken SP Adapter |
| Password (twice) | the same value as `OPENTOKEN_PASSWORD` on Render |
| Transport Mode | **Query Parameter** to start (the app also accepts Form POST) |
| Token Name | `opentoken` |
| Cipher Suite | AES-128/CBC or AES-256/CBC (the app accepts both) |
| Extended Contract | add `email`, `first_name`, `last_name` |

**Why one instance:** it is the single "standard badge" every bank's users are converted into.

> **On the job** the engineer also uses **Actions → Download** to export `agent-config.txt` for the
> app team's Ping agent library. That file contains the shared password, so it travels through a
> vault, never email or git. This lab's app reads the password from an environment variable instead.

### 3.3 IdP connection for Bank A

**Authentication → Integration → IdP Connections → Create Connection**

| Screen | What to do |
|---|---|
| Connection Type | **Browser SSO Profiles**, protocol **SAML 2.0** |
| Connection Options | **Browser SSO** |
| Import Metadata | **URL** → `BANKS_URL/realms/bank-a/protocol/saml/descriptor` |
| General Info | Entity ID is imported: `BANKS_URL/realms/bank-a`. Name it `Bank A (org-1001)`. |
| Browser SSO → SAML Profiles | tick **IdP-Initiated SSO** only |
| User-Session Creation → Identity Mapping | **Account Mapping** |
| Attribute Contract | `SAML_SUBJECT` plus `email`, `givenName`, `surname` |
| Target Session Mapping | **Map New Adapter Instance** → `Commerce Control OpenToken` |
| Adapter Contract Fulfillment | see the mapping table below (source: **Assertion**) |
| Default Target URL | `APP_URL/sso/launch/org-1001` |
| Protocol Settings | bindings: **POST**. Signature policy: accept the default (signed assertion or response). |
| Credentials → Signature Verification | **Unanchored**, using the certificate that came in with the metadata |
| Activation | **Active**, then **Save** |

| Adapter attribute | Bank A's assertion value | Bank B's assertion value |
|---|---|---|
| `subject` | `SAML_SUBJECT` | `SAML_SUBJECT` |
| `email` | `email` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress` |
| `first_name` | `givenName` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname` |
| `last_name` | `surname` | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname` |

**This table is the whole point of the middle layer:** two banks, two vocabularies, one set of keys
for the app.

### 3.4 IdP connection for Bank B

Repeat §3.3 with three differences:

1. Metadata URL: `BANKS_URL/realms/bank-b/protocol/saml/descriptor`. Name: `Bank B (org-2002)`.
2. Attribute Contract: the three long `http://schemas.xmlsoap.org/…` names, typed exactly.
3. Default Target URL: `APP_URL/sso/launch/org-2002`. Bank B sends no RelayState, so **this value
   is what actually routes Bank B's users.**

### 3.5 The allow-list of landing URLs

**Security → System Integration → Redirect Validation**

- Tick **Enable Target Resource Validation for SSO**.
- Add a row: host = the host of `APP_URL`, path = `/sso/launch/*`, tick **Require HTTPS** and **Allow for Target Resource**.

**Why:** RelayState comes from outside. Without this list, anyone who can craft a link could make
PingFederate redirect a freshly signed-in user to a site of their choosing.

✅ **Checkpoint:** both connections show **Active**, and the adapter instance is listed.

---

## 4. Run the flow

1. Open `APP_URL` in a private window. You see *"You are not signed in"*.
2. Click **Bank A portal tile**. Sign in as `alice` with `LAB_USER_PASSWORD`.
3. Watch the address bar: Keycloak → `PF_PUBLIC_URL/sp/ACS.saml2` → `APP_URL/sso/launch/org-1001?opentoken=…` → `APP_URL/`.

✅ **Expected:** *"Signed in through single sign-on"*, organisation **Bank A · org-1001**, and a
table of what was inside the OpenToken (`subject`, `email`, `first_name`, `last_name`, plus the
token's own `not-before` / `not-on-or-after`).

4. Sign out. Click **Bank B portal tile**, sign in as `bob`.

✅ **Expected:** organisation **Bank B · org-2002**, and the **same attribute keys** as Alice, even
though Bank B sent different names.

5. Click a tile again **without** signing out of the bank. You land in the app with no prompt.
   That is what "already signed in at their organisation" looks like.

**See it in the logs** (the empirical half):

```bash
docker compose exec pingfederate tail -n 5 /opt/out/instance/log/audit.log
```

One line per login: the connection (bank), the subject, the adapter, and `success`.

**If it fails,** read the last lines of `server.log` in the same folder first.

| Symptom | Likely cause |
|---|---|
| PingFederate error page straight after the bank login | Base URL or entity ID in §3.1 does not match what the banks were told. Re-check `PF_PUBLIC_URL` on Render and restart `lab05-banks`. |
| Error mentioning signature | The tunnel URL or realm changed after you imported metadata. Re-import it. See [note 34](../../notes/02-saml/34-saml-invalid-signature-rca.md). |
| Error mentioning target resource | §3.5 allow-list does not cover the URL. |
| App says *"could not decrypt"* | The adapter password and `OPENTOKEN_PASSWORD` differ. |
| App says *"token has expired"* straight away | The laptop's clock is wrong. |

---

## 5. Break it on purpose (attack → defence)

Every rejection shows up at `APP_URL/sso/log`.

| Try this | What stops it | Where you see it |
|---|---|---|
| Sign in as `carol` at Bank A | Carol is real at the bank but **not provisioned** in the app. The app does not auto-create users. | App: *"not provisioned"* |
| Press **Back** to the `…?opentoken=…` URL and reload | The app accepts each token **once**. | App: *"already used once (replay)"* |
| As Alice, copy a fresh `opentoken` value onto `/sso/launch/org-2002` | The app looks up **org + subject together**, and Alice is not in Bank B. | App: *"not provisioned … for Bank B"* |
| Change one character of the token | The integrity check (HMAC) fails. | App: *"could not decrypt"* or *"integrity check failed"* |
| Open `APP_URL/sso/launch/org-1001` with no token | Nothing vouches for you. | App: *"No opentoken was sent"* |
| In `bank-a`'s Keycloak client, change the IdP-initiated RelayState to `https://example.org/` | PingFederate's allow-list (§3.5). | PingFederate error page + `server.log` |

> **One honest weakness of the URL approach.** The org id travels in the URL, outside the
> encrypted token. The third row is only safe because the same email does not exist under two
> banks. The app already has a guard for this: if PingFederate also sends an `org_id` attribute
> *inside* the token, the app insists it matches the URL. It is switched off until you add `org_id`
> to the adapter contract and map it as fixed **Text** per connection. Worth raising with the
> service engineers, not something to change on your own.

---

## 6. Where the "request parameter" selector fits — open item

This lab routes each bank with the **connection's Default Target URL** (or the bank's RelayState),
which is enough to make the flow work end to end.

The service engineers also mention a selector keyed on a request parameter. In PingFederate that
is the **HTTP Request Parameter Authentication Selector**, and it lives in an **authentication
policy**, not inside an adapter. I have not wired one in, because guessing its parameter name and
position would not be a clone of the real setup. Bring back these three facts and it can be added:

1. Which **parameter name** does the selector read?
2. What are its **result values** — the org ids, the bank names, or full URLs?
3. Where is it used — a policy tree that the IdP connections map into, or somewhere else?

---

## 7. Rehearse the app without PingFederate

Useful while the laptop is off, or to show the app team their part in isolation.

```bash
cd ../commerce-control
npm test                                      # expected: pass 4, fail 0
OPENTOKEN_PASSWORD=lab-only node server.js &  # the app on http://localhost:3000
OPENTOKEN_PASSWORD=lab-only node scripts/mint-token.js alice@bank-a.example org-1001
```

The last command prints a landing URL carrying a token minted the way the adapter would. Open it.

---

## 8. Clean up

```bash
cd ../pingfederate
docker compose down          # add -v to also delete the PingFederate configuration
```

On Render, suspend or delete `lab05-banks` and `lab05-commerce-control`. Delete `.env`. Rotate
nothing else — every credential here was lab-only.

---

## What you learned

- An IdP-initiated login is a **browser POST from the bank to `/sp/ACS.saml2`**, with no request from us.
- **RelayState** picks the landing URL; the connection's **Default Target URL** is the fallback.
- **One adapter instance, one mapping per bank** is how many vocabularies become one.
- The app team builds **one landing endpoint**: decode, check time, check replay, find the user by org + subject, start its own session.

## Next

- Take §6's three questions to the service engineer, then add the selector.
- Second approach: the same two banks, but PingFederate issues **SAML** to the app through an
  **authentication policy contract** (note 35, section 6).
