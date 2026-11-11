# IdP-initiated SSO with PingFederate as the SP — many banks into one app

> **Janus's design note.** External banks log their users in at *their own* login server, then hand
> them to *our* PingFederate, which hands them to *one* internal app. This note explains every hop,
> the two ways PingFederate can do the last mile (OpenToken vs. SAML through a policy contract), and
> who configures what. It is the theory for [lab 05](../../labs/05-idp-initiated-sso-opentoken/README.md).
>
> **Prereqs:** [SAML deep dive](../02-saml/02-saml-deep-dive.md) (assertion, ACS, RelayState) and
> [PingFederate explained](18-pingfederate-explained.md) (connections, adapters, policies).
>
> **Confidence labels.** Anything marked **(prove in lab)** is my reading of PingFederate's behaviour
> that we have not yet watched happen. Anything marked **(ask the service engineer)** depends on how
> FinCo's server is actually configured.

---

## TL;DR

1. The user **starts at the bank**, never at our app. Their browser carries a signed note (the
   **SAML response**) from the bank's login server to our PingFederate.
2. PingFederate plays the **Service Provider (SP)** towards each bank. Each bank is one
   **IdP connection**.
3. **RelayState** is a sticky note travelling with the SAML response that says "send the user
   *here* afterwards". No sticky note → PingFederate uses the connection's **default target URL**.
4. **Last mile, option A — OpenToken:** a shared **SP adapter** wraps the user's attributes in a
   small encrypted token and sends the browser to the app.
5. **Last mile, option B — SAML via a policy contract:** PingFederate turns around and acts as an
   **IdP** to the app, issuing a fresh SAML assertion of its own.
6. Either way the app must learn **which bank** vouched for the user, and look the user up by
   **bank + user ID together**, never by email alone.

---

## 1. Why this design exists (first principles)

| Constraint | What it forces |
|---|---|
| Bank staff already have a login at their bank. Nobody wants a second password. | The **bank's** login server (their **Identity Provider, IdP**) must do the authenticating. |
| The app team cannot speak SAML to 50 banks, each with its own certificate and attribute names. | Put **one translator** in the middle: PingFederate. The app integrates once. |
| Banks launch apps from their own portal. | The flow starts at the IdP with no request from us: **IdP-initiated SSO**. |
| Each bank names things differently (`mail`, `emailaddress`, `upn`). | A **per-bank mapping** that normalises names before the app sees them. |

**Analogy.** PingFederate is the reception desk of an office tower. Visitors arrive with passes
issued by 50 different companies. Reception checks each pass against that company's known stamp,
then issues *one standard building badge*. The app only ever reads building badges.

---

## 2. What the user's browser actually does

The user's machine does nothing special. It is all browser redirects and cookies.

```
 Bank user's browser
   │ 1. clicks the app tile (or a bookmarked launch link)
   ▼
 Bank's Entra ID ── already signed in? reuse session : show login + MFA
   │ 2. returns a tiny web page containing a hidden form:
   │       SAMLResponse = <signed assertion>
   │       RelayState   = <where to land>      (optional)
   │    JavaScript auto-submits it (an HTTP POST)
   ▼
 Our PingFederate   https://<pf-host>/sp/ACS.saml2
   │ 3. finds the IdP connection by the assertion's Issuer, verifies signature,
   │    audience, time window, and that this assertion was not used before
   │ 4. maps attributes, builds the last-mile token (OpenToken or SAML)
   ▼
 The app   https://<app-host>/<sso-landing-path>
     5. validates the token, finds the existing user, sets ITS OWN session cookie
```

**Where the click happens (step 1) with Entra as the bank's IdP:**

| Starting point | What it is |
|---|---|
| **My Apps** — `https://myapps.microsoft.com` | Microsoft's app launcher. The app shows up as a tile for assigned users. |
| **Microsoft 365 app launcher** (the "waffle") | Same tiles, inside Office. |
| **User access URL** | A direct link Entra generates per app. The bank can paste it into its own intranet portal. |

> **Gotcha.** In the bank's Entra app, the **Sign on URL** field must be **blank**. If it is filled
> in, the tile sends the user to that URL instead (an SP-initiated start), and nothing arrives at
> our ACS.

**Cookies left behind:** one at Microsoft (the bank session), possibly one at PingFederate, one at
the app. The app's cookie is the only one the app's frontend cares about.

### What the starting point looks like in real life

Almost nobody opens My Apps. The realistic start is **a link on the bank's own intranet home page**
— the same kind of page where you find Workday at FinCo. The bank's admin puts a "Commerce Control"
tile there, and behind the tile is the Entra **user access URL**. Users also bookmark that link.

| User's state when they click | What they see |
|---|---|
| **Already signed in** (normal on a work laptop: signing in to Windows or the browser signs them in to Entra) | A second or two of redirects, then the app. No prompt. |
| **Not signed in** (personal device, expired session) | The bank's Entra login page and MFA, then the same redirects, then the app. |

Either way, the login happens **at the bank**, before our PingFederate sees anything.

### What if the user opens the app's own URL directly?

Then nothing has vouched for them, and the app has no session. The app must choose:

| Option | What happens | Flow type |
|---|---|---|
| **"Launch from your organisation's portal" page** | The user goes back to the bank's link. | Stays IdP-initiated only. |
| **A sign-in button or automatic redirect** | The app sends the browser to PingFederate (`/sp/startSSO.ping`), which sends it to the bank. | **SP-initiated** — a different flow. |

A "Sign in with SSO" button is always SP-initiated, because the *app* starts it. With many banks it
has an extra problem: **which bank?** The app or PingFederate must work that out (a per-bank URL,
or asking for the email address first). This is called **IdP discovery**.

> **Why you care.** IdP-initiated-only means two things the app team must design for: a bookmark of
> the app's own URL does not log anyone in, and when the app session expires the user has to
> re-launch from the bank's portal. **(ask the service engineer)** whether the "base URL + bank
> name" entries exist precisely to tell PingFederate which bank to use on a direct hit — that would
> mean SP-initiated is already supported.

---

## 3. RelayState — the sticky note

In IdP-initiated SSO there is no request from us, so there is nothing to "reply" to. RelayState is
the only way the bank can say where the user should land.

| Case | Who decides the landing URL |
|---|---|
| Bank **sends** RelayState | The bank. They type it into the **Relay State** field of their Entra app. It is a fixed value per app, not per user. |
| Bank sends **nothing** | Us. The IdP connection's **Default Target URL** is used — this is the "override" the service engineers set. |

**Attack and defence.** RelayState comes from outside, so an attacker could try to make
PingFederate redirect a freshly-logged-in user to an evil site (an **open redirect**).
PingFederate's defence is **target resource validation**: an allow-list of URLs it will redirect
to. Detect abuse by watching `server.log` / `audit.log` for rejected target resources.

---

## 4. Attributes — the path from the bank to the app

```
 Entra claims  →  SAML assertion  →  IdP connection      →  mapping          →  last-mile token  →  app
 (bank picks)     (NameID +          attribute contract     (rename, add        (OpenToken keys      (reads
                   attributes)       (what we accept)        constants)          or SAML attrs)       them)
```

- **Names must match exactly.** Entra's default claim names are long URIs, for example
  `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress`. The connection's contract
  must list that exact string.
- **The mapping is where banks become uniform.** Bank A's `emailaddress` and Bank B's `mail` both
  map to one key, say `email`. The app sees the same keys for every bank.
- **Add the bank's identity as a constant.** In each connection's mapping, set an attribute such as
  `bank_id` to a fixed text value (`bank-a`). This is more trustworthy than putting the bank's name
  in a URL, because it travels *inside* the protected token.

> **Why you care.** Users already exist in the app. So the bank and the app team must agree on one
> **join key** (employee ID? email? the NameID?) before anything is configured. That, plus the
> attribute names, is the core of what the onboarding chatbot must collect.

---

## 5. Last mile, option A — OpenToken (the confirmed approach)

**Plain words.** An OpenToken is a short encrypted envelope containing key=value pairs. PingFederate
and the app share one secret, so only they can open or forge it.

| Piece | Detail |
|---|---|
| **Made by** | An **OpenToken SP adapter** instance in PingFederate. **One instance**, shared by every bank's connection. |
| **Contains** | `subject` (the user ID) plus the adapter's extra attributes, plus not-before / expiry times. |
| **Protected by** | Symmetric encryption and an integrity check, keyed from a shared password. |
| **Delivered by** | Cookie, URL query parameter, or form POST — a setting on the adapter. |
| **Opened by** | Ping's **OpenToken agent** library inside the app, configured from an `agent-config.txt` file exported from the adapter. |

**Gotchas.**
- `agent-config.txt` **contains the shared secret.** Never commit it; hand it over through a vault.
- **Query-parameter transport puts the token in URLs**, so it lands in browser history and proxy
  logs. Prefer form POST. Cookie transport only works if PingFederate and the app share a parent
  domain.
- The token is short-lived by design. The app must **swap it for its own session** immediately, not
  keep using it.
- Changing the adapter's password or contract affects **every bank at once**, because the instance
  is shared.

**What the app team builds:**

1. **One backend landing endpoint** that receives the token.
2. Decode with the agent → get `subject`, `bank_id`, other attributes.
3. Look up the existing user by **`bank_id` + join key**. No match → friendly error page, no auto-create.
4. Create the app's own session cookie, then redirect to the frontend.
5. Decide what happens on **session expiry**: there is no login page, so the user must be sent back
   to their bank's portal.

The frontend never touches SAML or the OpenToken.

### How the "request parameter selector with base URL + bank name" fits

My reading of what the service engineers do today, **(ask the service engineer)** to confirm:

- Each bank's landing URL is the app's **base URL plus a bank marker**, e.g.
  `https://app.example/sso?bank=bank-a`. It arrives as RelayState or is set as that connection's
  default target URL.
- That URL must also be on the **target-URL allow-list**, so a new bank means a new allow-list entry.
- An **HTTP Request Parameter Authentication Selector** is a policy building block that branches on
  a query parameter's value. It lives in the **authentication policy tree**, not in the adapter.

Three questions to settle it:

1. Is the selector in a policy tree, and which parameter name does it read?
2. Is the list of per-bank URLs the redirect allow-list, the selector's values, or both?
3. Does the app read the bank from the URL, or from an attribute inside the OpenToken?

---

## 6. Last mile, option B — SAML to the app through a policy contract

**Plain words.** PingFederate wears two hats: **SP** towards the banks, **IdP** towards the app.
It receives the bank's assertion and issues a *new* assertion, signed with *our* key. This is
called **federation hub** or **bridging**.

```
 Bank IdP ──SAML──► [IdP connection] ─► policy contract ─► [SP connection] ──SAML──► app
                      PingFederate as SP   (the standard        PingFederate as IdP
                                            badge layout)
```

| Building block | Role |
|---|---|
| **Authentication policy contract** | A named list of attributes — the "standard badge layout" between the two hats. |
| **IdP connection (per bank)** | Maps the bank's attributes **into** the contract, instead of into an adapter. |
| **Authentication policy tree** | Names each bank's IdP connection as an authentication source. A selector picks the branch. This is the "connection name inside the policy tree". |
| **SP connection (one, for the app)** | Maps **from** the contract into the assertion sent to the app. |

**How IdP-initiated chains through (prove in lab).** The bank's connection lands on PingFederate's
*own* IdP start URL rather than on the app:

```
Default Target URL = https://<pf-host>/idp/startSSO.ping?PartnerSpId=<app-entity-id>&<bank marker>
```

For the second hop to be silent, PingFederate must remember the login it just accepted from the
bank (its **authentication sessions** feature). Otherwise it would try to authenticate the user
again.

**What the app team builds instead:** a standard SAML SP using any SAML library. It trusts **one**
IdP (us), one certificate, one metadata file. No Ping agent, no shared secret.

### A vs. B at a glance

| | A — OpenToken | B — SAML via policy contract |
|---|---|---|
| App needs | Ping agent library + shared secret file | Any standard SAML library + our public cert |
| Secret type | Shared (both sides can forge) | Public/private key (only we can sign) |
| Per-bank work on PingFederate | IdP connection + adapter mapping | IdP connection + contract mapping + policy branch |
| Portability | Ping-specific | Open standard |

---

## 7. Who configures what

| Party | Configures | Gives to the next party |
|---|---|---|
| **Bank** (Entra admin) | Enterprise application, SAML sign-on: **Identifier** = our SP entity ID, **Reply URL** = our ACS URL, **Relay State** (optional), **Sign on URL** blank, claims, user assignment | Their **federation metadata** (URL or XML): issuer, signing certificate |
| **IAM team** (PingFederate) | IdP connection from that metadata, attribute contract, mapping, default target URL, allow-list entry | Our **SP metadata** to the bank; landing URL and attribute list to the app team |
| **App team** | Landing endpoint, user lookup, session, error page | Base URL and the attribute names it expects |

---

## 8. Security checklist (attack → defence)

| Attack | Defence | Where to see it |
|---|---|---|
| Replay a captured SAML response | One-time-use check + short validity window | `audit.log` failure for the repeated assertion |
| Assertion minted for a different SP | Audience restriction must equal our entity ID | `server.log` validation error |
| RelayState pointing to an attacker site | Target resource allow-list | Rejected-redirect error |
| Bank A asserts a user that belongs to Bank B | App looks up by `bank_id` **and** user ID; `bank_id` is set by us per connection, never taken from the bank's attributes | App-side lookup failure |
| Stolen OpenToken from a URL or log | POST transport, short lifetime, app swaps it for a session at once | — |

---

## What you learned

- IdP-initiated SSO starts at the bank's launcher; the browser POSTs the SAML response to our ACS.
- RelayState picks the landing page; the connection's default target URL is the fallback.
- One shared adapter (or one policy contract) makes many banks look identical to the app.
- OpenToken is a shared-secret envelope; the SAML bridge is the standards-based alternative.

## Next

- Take the three selector questions in section 5 to the service engineer.
- Run [lab 05](../../labs/05-idp-initiated-sso-opentoken/README.md): a local PingFederate, two stand-in banks and one target
  app — authorized-lab-only, dummy users, no FinCo configuration or secrets.
