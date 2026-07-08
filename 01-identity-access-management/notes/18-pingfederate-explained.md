# PingFederate — a field guide to the federation hub you'll work in

> **Janus's new-joiner orientation.** This is the product FinCo runs SSO on. You'll open its admin console, read its `audit.log`, and get tickets that live and die inside it. This note is your map: what PingFederate *is*, how it's built, the handful of building blocks you'll configure over and over, and two real walkthroughs (create a connection, debug a broken login). Read it once now; come back to the tables when a ticket lands.
>
> **Prereqs:** [SAML deep dive #1](02-saml-deep-dive.md) and [SAML Mastery Session 2](13-saml-mastery-session2.md) (flows, assertions, the `/idp/startSSO.ping` URL) and [OAuth/OIDC deep dive](03-oauth-oidc-deep-dive.md). PingFederate is where all three protocols come to live in one server — so those notes are the vocabulary for this one.

---

## TL;DR (the whole note in eight lines)

1. **PingFederate = an enterprise federation server.** One self-hosted Java server that speaks **SAML, OAuth 2.0, OIDC, and WS-Fed/WS-Trust** on behalf of your whole company. It's the *hub* every login passes through.
2. **PingFederate (self-hosted) ≠ PingOne (Ping's SaaS cloud).** Same company (Ping Identity), two delivery models. FinCo runs the self-hosted server.
3. **Two console pieces:** the **admin console** (where you configure) and the **runtime engine nodes** (where live logins actually happen). Prod = several engines behind a load balancer.
4. **The core mental model is *connections*.** An **SP connection** = PingFederate is the **IdP** for an app. An **IdP connection** = PingFederate is the **SP**, trusting an outside partner IdP. Everything hangs off that one distinction.
5. **The building blocks you'll touch:** adapters (how you authenticate the user), datastores (where user data lives), authentication policies (the if-this-then-that routing), attribute contracts (what the token/assertion will carry), and certificates (signing + encryption per connection).
6. **As an OAuth server**, PingFederate adds: clients, grant types, scopes, **Access Token Managers** (JWT vs reference tokens), and **OIDC policies** (what goes in the ID token).
7. **Two log files are your best friends:** `server/default/log/audit.log` (every SSO transaction, one line each) and `server.log` (the stack traces). Most tickets are solved by reading these.
8. **It's crown-jewel infrastructure.** Compromise PingFederate and you compromise *every app behind it*. Patch it, lock the admin console down hard, and watch cert expiries like a hawk.

---

## 1. What PingFederate actually is (plain words first)

Imagine every app at FinCo — the HR portal, the payments console, the internal wikis, the SaaS tools — each needs to know *who you are* before letting you in. You do **not** want 40 apps each storing passwords and each doing MFA their own way. That's 40 places to breach and 40 audits to pass.

So you put **one server in the middle** that does the authenticating, and every app just *trusts* that server. That middle server is a **federation server**. **PingFederate is that server.**

- It **authenticates** the user (checks the password against Active Directory, triggers MFA).
- It then **issues a signed proof** of who you are, in whatever language the app speaks: a **SAML assertion**, an **OIDC ID token**, an **OAuth access token**, or a **WS-Fed token**.
- The app trusts the proof because of a **pre-arranged trust contract** (metadata + certificates — exactly the trust ceremony from [note 13](13-saml-mastery-session2.md)).

**One plain sentence for the interview:** *"PingFederate is an enterprise federation server — a protocol translator and trust broker that lets one login work across many apps and many organizations, speaking SAML, OAuth, OIDC and WS-Fed."*

**Under the hood** it's a **self-hosted Java application** (runs on a JVM, on your own servers or VMs — including, at FinCo, inside your Kubernetes world). That "self-hosted" part matters: *you* patch it, *you* cluster it, *you* own its uptime. Contrast with PingOne (§3), which is Ping running it for you in the cloud.

> **Job tie-in:** when a coworker says "just add an SP connection in Ping," they mean "configure PingFederate to be the IdP for one more app." That sentence is your day job in miniature.

### A paragraph of history & positioning

**Ping Identity** (founded 2002, Denver) built PingFederate as one of the first standalone **federation servers** back when SAML 2.0 was brand new (~2005). For years it was *the* enterprise choice for hard, on-prem, standards-heavy federation — banks, insurers, governments. The market now has three big neighbors you'll hear named in the same breath:

| Product | Company | Shape | Where it's strong |
|---|---|---|---|
| **PingFederate** | Ping Identity | Self-hosted federation server (+ PingOne cloud) | Deep protocol control, on-prem/hybrid, regulated industries (fintech!) |
| **Okta** | Okta | Cloud-first IDaaS | Fast SaaS SSO, huge app catalog, easy admin |
| **Entra ID** (was Azure AD) | Microsoft | Cloud identity, bundled with M365 | Microsoft shops, Conditional Access |
| **ForgeRock** (now part of Ping) | Ping Identity | Self-hosted/cloud identity platform | Heavy customization, CIAM at scale |

FinCo chose the **self-hosted, standards-deep** path — which is why *you're* learning the server, not just clicking a SaaS dashboard. That depth is a career asset.

---

## 2. The Ping product family — so you can decode coworker sentences

Ping ships several products with confusingly similar names. When someone says "push that to PingAccess" or "does the user have PingID?", here's what they mean:

| Product | One-line what-it-is | Its job in a login |
|---|---|---|
| **PingFederate** | The **federation server** (this note). | Authenticates the user and mints SAML/OIDC/OAuth tokens. The hub. |
| **PingOne** | Ping's **SaaS cloud platform** (federation + directory + MFA as-a-service). | The cloud alternative to running your own PingFederate; also hosts add-on cloud services. |
| **PingID** | Ping's **MFA** product (push, TOTP, FIDO2, SMS). | The "second factor" step — PingFederate calls out to PingID during login. |
| **PingAccess** | **Web Access Management** — an authZ reverse proxy / policy gateway. | Sits *in front of* apps and enforces "is this user allowed on this URL?" *after* PingFederate says who they are. |
| **PingDirectory** | A high-scale **LDAP directory** (user store). | Where identities/attributes live; PingFederate reads it as a datastore. |

**How they combine in one flow (mental picture):**

```
User ──▶ PingAccess (gatekeeper: "need a session?") ──▶ PingFederate (authN: who are you?)
                                                              │
                                                              ├─▶ PingDirectory / AD  (check the password, read attributes)
                                                              └─▶ PingID              (do MFA)
              ◀── signed token / session ──────────────────────┘
User is now let through PingAccess to the app.
```

**The one-liner to keep straight:** *PingFederate says **who you are** (authN + tokens); PingAccess says **what you're allowed to reach** (authZ + proxy); PingID does the **second factor**; PingDirectory **stores** the users.* (This is the AuthN-vs-AuthZ split, made concrete in products.)

---

## 3. Core architecture — console, engines, cluster, files

### 3a. Admin console vs runtime engine (the split that confuses new joiners)

PingFederate has **two faces**, and they are *not* the same thing:

| Face | What it is | Who touches it | Port (typical default) |
|---|---|---|---|
| **Admin console** | The web UI + admin API where you **configure** connections, adapters, certs. | You (the admin). | `https://host:9999/pingfederate/app` |
| **Runtime engine** | The service that handles **live user logins** — receives AuthnRequests, mints assertions/tokens. | Every end user, indirectly. | `https://host:9031/...` |

**Why the split matters:** you can be reconfiguring on the console while millions of logins flow through the engine. And crucially — **the admin console being down does not stop logins** (the engine keeps serving); but the **engine being down = SSO outage for everyone.** Know which one a ticket is about.

### 3b. Clustering — why prod has several engines behind a load balancer

One engine node is a single point of failure and a throughput ceiling. So production runs a **cluster**:

```
                       ┌──────────────────────────┐
   Users ──▶ Load Balancer ──▶  Engine node 1     │
                       │        Engine node 2     │  ← all stateless-ish, identical config
                       │        Engine node 3     │
                       └──────────────────────────┘
                                    ▲
                        Admin console node (1)  ── pushes config to all engines
```

- **One admin (console) node** holds the master configuration; it **replicates config** out to the engine nodes ("push to the cluster").
- **Several engine nodes** do the real work, sitting behind a **load balancer** for high availability and scale.
- A node's role is set in its `run.properties` (operational mode: standalone, clustered console, or clustered engine). You'll rarely edit this, but you'll *hear* it.

> **Job tie-in (FinCo/k8s):** in a containerized deployment each engine is a pod; the load balancer is a Service/Ingress; "scale up for month-end payroll load" = add engine replicas. Your mTLS service mesh wraps the pod-to-pod traffic.

### 3c. The admin API

Everything you can click in the console is also a **REST admin API** call. That's how config gets **automated** (Terraform/CI pipelines), **backed up**, and **promoted** dev → test → prod without hand-clicking. As a beginner you'll use the UI; as you grow, the API is how teams keep two dozen connections consistent and auditable.

### 3d. Files & directories a beginner hears named

You don't need to memorize the tree, but these get mentioned in tickets. Paths are relative to the PingFederate install (`<pf>/pingfederate/`):

| Path | What's in it | When you care |
|---|---|---|
| `server/default/log/` | **The log directory.** | Constantly — see below. |
| `server/default/log/audit.log` | **One line per SSO/token transaction** — who, which connection, success/fail, timestamp. | **First stop** for "did this login even reach us, and did it succeed?" |
| `server/default/log/server.log` | The **application log** — stack traces, errors, warnings, cert problems. | When `audit.log` says "fail" and you need *why*. |
| `server/default/conf/` | Config files (`log4j2.xml` for log levels, etc.). | Turning up log verbosity to debug. |
| `server/default/data/` | Where the runtime config/state persists. | Backups, migrations. |
| `bin/` | Start/stop scripts (`run.sh` / `run.bat`), admin tools. | Restarts. |

**The two you'll actually live in:** `audit.log` (did it happen / did it work) and `server.log` (why did it break). Burn those two filenames into memory — they anchor the debugging walkthrough in §9.

---

## 4. THE core mental model — connections

If you learn one thing about PingFederate, learn this. Almost everything you configure is a **connection**, and there are exactly **two kinds**. They are mirror images. The whole trick is knowing **which role PingFederate is playing.**

| Connection type | PingFederate's role | Plain meaning | The other party is… |
|---|---|---|---|
| **SP connection** | PingFederate is the **IdP** | "**I** log people **into** this app." | the app / **S**ervice **P**rovider |
| **IdP connection** | PingFederate is the **SP** | "**I** trust logins **coming from** that outside IdP." | an external **IdP** (partner, another company) |

**Memory hook:** *the connection is named after **the other guy**.* An **SP connection** points *at* an SP (so you must be the IdP). An **IdP connection** points *at* an IdP (so you must be the SP). Get this backwards and nothing else makes sense.

### 4a. SP connection — PingFederate as the IdP (the common case)

> **FinCo example:** Employees need to SSO into **Workday** (an HR SaaS app). You create an **SP connection** to Workday. Now PingFederate is the **IdP**: an employee hits Workday, gets bounced to PingFederate, logs in with AD + PingID MFA, and PingFederate mints a **SAML assertion** back to Workday. This is *most* of your work — onboarding SaaS apps to SSO.

```
Employee ──▶ Workday (SP) ──AuthnRequest──▶ PingFederate (IdP) ──▶ AD + PingID
   ◀────────── logged in ◀──── signed assertion ◀────────────────────┘
                (PingFederate = IdP;  Workday = SP;  this is an SP connection)
```

### 4b. IdP connection — PingFederate as the SP (federating a partner in)

> **FinCo example:** A **partner bank** wants *its* employees to access a shared FinCo portal, but they should keep authenticating at *their own* IdP (their AD, their MFA). You create an **IdP connection** to the partner bank's IdP. Now PingFederate is the **SP**: a partner user arrives, PingFederate redirects them to the *partner's* IdP, the partner authenticates them and sends **PingFederate** an assertion, which PingFederate trusts and turns into a FinCo session.

```
Partner user ──▶ FinCo portal ──▶ PingFederate (SP) ──AuthnRequest──▶ Partner bank IdP
      ◀───── FinCo session ◀─── trusts assertion ◀──── signed assertion ◀──────┘
                (PingFederate = SP;  partner = IdP;  this is an IdP connection)
```

**The senior-level nuance:** PingFederate is often **both at once** — it's the SP that receives a partner login (IdP connection) *and* the IdP that then logs that user into downstream apps (SP connections). That chaining ("proxy/broker" pattern) is common in fintech partner ecosystems. When you can narrate *which hat it's wearing at each hop*, you've got it.

---

## 5. The building blocks you'll configure

Every connection is assembled from a small set of reusable parts. Learn these five (plus the OAuth pieces in §6) and the console stops being scary.

### 5a. Adapters — *how* you authenticate the user at the front door

An **adapter** is the plugin that actually collects and checks the user's credentials — it's the "front door." PingFederate calls an adapter, the adapter returns "here's who they are (and here are some attributes)."

| Adapter | What it does | FinCo scenario |
|---|---|---|
| **HTML Form** | Shows a **username/password login page**, validates against AD/LDAP. | The standard FinCo login screen for off-network/external users. |
| **Kerberos / IWA** | **Seamless desktop SSO** — no password prompt; uses the Windows domain login you already did. | On-network employees on domain-joined laptops just *walk in*. (See [Kerberos explained](15-kerberos-explained.md).) |
| **Identifier-First** | Asks only for the **username/email first**, *then* decides where to send you (which IdP, which method). | Route `@partnerbank.com` users to the partner IdP; `@finco` users to the form. |
| **Composite** | **Chains several adapters** into one step (e.g., form **and** device certificate). | Password + client-cert for high-assurance apps. |
| **PingID** | Adds **MFA** (push/TOTP/FIDO2) as an authentication step. | The mandatory second factor for anything touching payments. |

**Mental model:** *an adapter answers "who is this user and how sure am I?"* Policies (§5c) then decide *which* adapter to use when.

### 5b. Datastores — *where* the user data lives

A **datastore** is a configured connection to a place where identities and attributes are kept. PingFederate uses it to **validate credentials** and to **look up attributes** (department, groups, employee ID).

| Datastore type | Example | Used for |
|---|---|---|
| **LDAP / Active Directory** | On-prem AD, PingDirectory | Password check + group/attribute lookup |
| **JDBC** (database) | A SQL user/entitlement table | Attribute lookup, custom claims |
| **REST / custom** | An internal API | Fetching data an app needs in its token |

> **Job tie-in:** "the app is missing the `costCenter` attribute" almost always traces to a **datastore lookup** — the value has to be *fetched from somewhere* before it can go in the assertion. That "somewhere" is a datastore.

### 5c. Authentication policies (policy trees) — the if-this-then-that routing

A **policy tree** decides, per login, **which adapters/MFA to run and in what order**, based on conditions (network, app, user type, risk). It's a flowchart the engine walks top-down.

```
                       ┌─ on corporate network? ──▶ Kerberos adapter (silent SSO) ──▶ DONE
   Login request ──────┤
                       └─ external? ──▶ HTML Form ──▶ success? ──▶ PingID MFA ──▶ DONE
                                                   └─ fail ──▶ deny
```

> **FinCo example:** *"On-network staff get seamless Kerberos; anyone off-network gets password + PingID push; partner-domain emails get routed to the partner IdP."* That entire sentence is **one policy tree**. When someone says "the policy sent them down the wrong path," this is the thing to open.

### 5d. Attribute contracts & fulfillment — *what* the token carries, and *where each value comes from*

Two linked ideas, and the source of a *huge* fraction of tickets:

- **Attribute contract** = the **promise** of *which* attributes the assertion/token will contain. It's the named list: `email`, `groups`, `employeeId`, `costCenter`. (This is exactly the SAML `<AttributeStatement>` from [note 13 §5c](13-saml-mastery-session2.md) — the contract defines what ends up in it.)
- **Attribute fulfillment** = **where each promised value is filled from**. Every attribute in the contract must be *mapped to a source*:

| Fulfillment source | Meaning | Example |
|---|---|---|
| **Adapter** | Value came from the login step. | `subject` = username the form captured |
| **Datastore lookup** | Value fetched from AD/DB. | `costCenter` = AD attribute `departmentNumber` |
| **Expression / text** | Computed or constant. | `role` = `"employee"`, or an OGNL expression combining fields |

**The classic failure:** the app expects `email`, but fulfillment maps it from an AD field that's empty for some users → those users log in but the app rejects them ("no email in assertion"). **Contract says what; fulfillment says from where — check both.**

### 5e. Certificates in Ping — signing & encryption, per connection

Straight from [SAML Mastery §6](13-saml-mastery-session2.md): certs do **two jobs**, and in PingFederate each is a **per-connection setting**.

| Setting | Whose key | Job | Where it lives |
|---|---|---|---|
| **Signing certificate** | **PingFederate's** private key | Proves the assertion/token is really from us (authenticity + integrity). | Set on the connection; you publish the public cert in your metadata. |
| **Encryption certificate** | **The partner/SP's** public cert | Hides the assertion content from the browser (optional). | Imported from the partner's metadata. |

**Cert rotation in the console** is the outage-prevention chore: PingFederate lets you stage a **new signing cert alongside the old one** and publish both in metadata, so partners trust the new key *before* the old expires — zero-downtime rotation. (Deep dive on bindings + cert mechanics: [note 16](16-saml-bindings-and-certificates.md).)

> **Why you care (fintech):** an expired signing cert breaks **every SP connection at once** — a company-wide "SSO is down." Mature teams track Ping cert expiries as scheduled incidents. Proactive monitoring of expiry dates (§10) is not optional at a bank-grade shop.

---

## 6. PingFederate as an OAuth Authorization Server (the OAuth/OIDC half)

PingFederate isn't only a SAML IdP — it's also a full **OAuth 2.0 Authorization Server (AS)** and **OIDC Provider (OP)**. This is the modern half of the job (APIs, mobile apps, microservices). Vocabulary comes from [OAuth/OIDC deep dive](03-oauth-oidc-deep-dive.md); here's how it maps onto Ping's screens.

### 6a. The OAuth objects you'll configure

| Ping object | What it is | Example |
|---|---|---|
| **Client** | A registered app that can request tokens (has a client ID, secret or keypair, allowed redirect URIs). | The FinCo mobile app; a backend microservice. |
| **Grant type** | *How* the client is allowed to get a token. | `authorization_code` + **PKCE** (mobile/web), `client_credentials` (service-to-service), device flow (TVs/CLIs). |
| **Scopes** | Named permissions the client may request. | `accounts.read`, `payments.initiate` |
| **Access Token Manager (ATM)** | The **factory + policy for access tokens** — defines the token *format*, lifetime, and which attributes go inside. | One ATM per API family. |
| **OpenID Connect policy** | Defines what goes in the **ID token** (the *authentication* proof for OIDC). | Puts `sub`, `email`, `name` into the ID token. |

### 6b. Access Token Managers — JWT vs reference tokens (know this cold)

An **ATM** decides what an access token physically *is*. Two families, and the choice is an architecture decision:

| ATM type | The token is… | Pros | Cons | Use when |
|---|---|---|---|---|
| **JWT ATM** | A **self-contained signed JSON** token (header.payload.signature). The API validates it **offline** by checking the signature. | Fast, no call back to Ping per request; scales. | **Can't be revoked instantly** (valid until expiry); contents visible (base64). | High-throughput APIs, microservices/mesh. |
| **Reference (internally-managed) ATM** | An **opaque random string** — a *ticket number*. The API must call Ping's **introspection** endpoint to learn what it means. | **Revocable instantly**; nothing leaks in the token. | Extra network call per validation (Ping is in the hot path). | Sensitive scopes, when instant revocation matters. |

> **Fintech judgment call:** payment-initiation scopes often favor **reference tokens** (you can kill a stolen token *now*), while read-only, high-volume APIs favor **JWTs** (speed). Expect to defend this tradeoff in reviews.

### 6c. Token introspection — the reference-token lookup

The endpoint (typically `/as/introspect.oauth2`) where a **resource server** hands Ping an opaque token and asks *"is this valid, and what scopes/claims does it carry?"* This is what makes reference tokens work — and what makes instant revocation possible. (JWTs *can* be introspected too, but usually aren't, since the point of a JWT is offline validation.)

### 6d. Where the OAuth/OIDC endpoints live

You'll see these engine URLs in client configs and logs:

| Endpoint | Purpose |
|---|---|
| `/as/authorization.oauth2` | Start the authorization-code flow (user consent/login). |
| `/as/token.oauth2` | Exchange code (or credentials) for tokens. |
| `/as/introspect.oauth2` | Validate/inspect a token (reference tokens). |
| `/idp/userinfo.openid` | OIDC UserInfo — return claims about the user. |
| `/.well-known/openid-configuration` | OIDC **discovery** — the machine-readable config clients auto-load. |

More on wiring real clients (PKCE, refresh tokens, common mistakes): [OAuth 2.0 in practice](19-oauth2-in-practice.md).

### 6e. Token Exchange / STS (WS-Trust) — the legacy corner

One short section, because it *does* show up in fintech: PingFederate can act as a **Security Token Service (STS)** — swapping one token type for another (the WS-Trust world of older SOAP/enterprise apps). Two halves:

- **Token processors** (IdP side) — *validate an incoming* token (e.g., a Kerberos or SAML token).
- **Token generators** (SP side) — *mint an outgoing* token in another format.

Together they do **token exchange**: e.g., a desktop app presents a Kerberos ticket, and PingFederate hands back a SAML token a legacy middleware understands. You'll rarely build new STS integrations, but you'll **inherit** them — recognize the words "token processor/generator" and you'll know you're in the WS-Trust/STS corner, not the SAML/OAuth mainstream.

---

## 7. Walkthrough 1 — anatomy of creating a SAML SP connection

Conceptual steps (no live system needed) for the most common ticket: *"onboard app X to SSO."* PingFederate is the **IdP**; app X is the **SP**.

1. **Get the SP's metadata.** The app vendor gives you their metadata XML (or a URL). It contains their **EntityID**, their **ACS URL**, and their certs. *Import it* rather than hand-typing — fewer errors.
2. **Create the SP connection**, choosing the **Browser SSO / SAML 2.0** profile.
3. **Confirm the EntityID** — the SP's unique name (the *"who is this for"* from [note 13 §2](13-saml-mastery-session2.md)). Must match exactly what the app expects.
4. **Confirm the ACS URL** — the **Assertion Consumer Service** endpoint you'll POST the assertion to. (Getting this wrong = assertion lands nowhere; a top failure.)
5. **Choose the binding** — normally **HTTP-POST** for the Response (it carries signatures and is too big for a URL). (Bindings deep dive: [note 16](16-saml-bindings-and-certificates.md).)
6. **Define the attribute contract** — the promised attributes (e.g., `SAML_SUBJECT` = email, plus `groups`).
7. **Map attribute fulfillment** — point each attribute at its source (adapter value, AD lookup, or expression). *This is the step that breaks silently — double-check it.*
8. **Set the signing certificate** — pick PingFederate's signing key; enable "sign the assertion." Add **encryption** only if the app supports it and the attributes are sensitive.
9. **Pick the authentication policy / adapter** the connection uses (form? Kerberos? +PingID?).
10. **Activate** the connection and **export your (IdP) metadata** to hand back to the vendor so *they* can trust *you*. Trust is mutual — both sides import the other's metadata.

**✅ Smoke test (do this first, it's the fastest proof of life):** trigger an **IdP-initiated** login from the Ping side — no need for the app's login page to be wired yet:

```
https://sso.finco-lab.example.com/idp/startSSO.ping?PartnerSpId=<the SP's EntityID>
```

If you land inside app X logged in → the connection, cert, and attribute mapping all basically work. *Then* verify the real **SP-initiated** flow (user starts at the app). This mirrors [note 13 §4](13-saml-mastery-session2.md): IdP-init for onboarding/testing, SP-init for production.

> **Gotcha:** `PartnerSpId` must be the SP's **EntityID**, character-for-character (often a URL, but it's just a name — it need not resolve). A trailing slash mismatch is a classic "why won't it work" hour.

---

## 8. Walkthrough 2 — debugging "a user can't SSO into app X"

The bread-and-butter ticket. Here's the mini-playbook — **symptom → likely cause → where to look** — the table to keep open when a ticket lands.

| Symptom (what the user/app reports) | Likely cause | Where to look / what to do |
|---|---|---|
| "Nothing happens / error at the app after login" | Assertion never reached the app, or app rejected it | **`audit.log`** — find the transaction. Did Ping even *see* it? Success or fail? |
| `audit.log` shows the transaction **failed** | Signature, cert, or mapping error | **`server.log`** — the stack trace names the reason (bad signature, decrypt fail, null attribute). |
| "Invalid signature" at the SP | Cert mismatch or **expired signing cert** | Check the connection's signing cert **expiry**; confirm the SP has your *current* public cert (metadata drift). |
| Login works for some users, fails for others | **Attribute fulfillment** returns null for those users | Check the datastore lookup — is the source AD field populated for the failing users? |
| "SSO was fine yesterday, everyone's broken today" | A cert expired overnight | Cert expiry across the whole IdP — the company-wide outage pattern (§5e). |
| Intermittent failures around token validity | **Clock skew** between Ping and the SP | Check NTP on both sides; assertion `NotBefore/NotOnOrAfter` windows (from [note 13 §5c](13-saml-mastery-session2.md)). |
| App says "wrong user / no account" | **NameID** format or value mismatch | Compare the assertion's `NameID` to what the app keys accounts on (email vs opaque ID). |
| Redirect loop / lands back at login | Session/policy issue, or wrong ACS/EntityID | Re-check EntityID + ACS URL; check the policy tree path the user took. |

**Your two-tool debugging loop:**

1. **Server side — `audit.log`:** find the exact transaction (by timestamp/user). It tells you **did the login reach Ping, which connection, and success/fail**. One line, one truth.
2. **Server side — `server.log`:** when `audit.log` says "fail," this gives the **why** (the exception).
3. **Browser side — SAML-tracer:** the browser extension that captures the actual SAML request/response mid-flow. Decode it and run the **60-second speed-read drill** from [note 13 §5e](13-saml-mastery-session2.md): Status → Issuer → InResponseTo → Subject/NameID → Audience → time window → Recipient → Signature → attributes. That drill *is* the debugging sequence.

> **Job tie-in:** "check audit.log, grab a SAML-tracer capture, verify cert expiry and clock skew, confirm the attribute mapping" — that four-step reflex is what makes you the person the team hands SSO tickets to. Say it in an interview and you sound like you've done it.

---

## 9. Security & ops — why this box is the crown jewels

PingFederate authenticates **everyone into everything**. That makes it the single highest-value target at FinCo. Treat it accordingly.

| Concern | Why it matters | What "good" looks like |
|---|---|---|
| **Compromise = total** | Own the IdP → mint assertions for *any* user into *any* app. Game over. | Isolate it; least-privilege admin; nothing else shares the host. |
| **Protect the admin console** | The console can rewrite trust for the whole company. | Separate admin network/VPN, strong MFA for admins, IP-allowlist, never internet-exposed. |
| **Patching** | It's internet-adjacent Java handling untrusted XML/JWT — a juicy target for parser/deserialization bugs. | Track Ping security advisories; patch on a defined SLA (fintech = fast). |
| **Cert expiry monitoring** | Expired signing cert = company-wide SSO outage (the recurring theme). | Automated expiry alerts weeks ahead; staged rotation (primary+secondary). |
| **Audit trail** | SOX/PCI-DSS require *who logged into what, when*. `audit.log` is that evidence. | Ship `audit.log` to the SIEM; retain per policy; alert on anomalies. |
| **Admin change control** | An unauthorized connection = a backdoor into every user. | All connection changes via change tickets / the admin API + code review. |
| **Key protection** | The signing private key *is* the company's identity. | Store in an HSM where possible; strict access; rotate on schedule. |

> **Attack ↔ defense pairing (repo rule):** the offensive dream against an IdP is **golden-SAML** style — steal the signing key and forge assertions for anyone, bypassing MFA entirely and leaving little trace at the app. **Defenses:** protect/HSM the signing key, monitor `audit.log` for assertions that don't correlate to a real login, encrypt assertions carrying PII, and short-lived tokens so a stolen one dies fast. (Hand the offensive lab to **Loki**; hand the SIEM detections to **Heimdall**.)

---

## 10. What you learned

- **PingFederate = a self-hosted enterprise federation server** — one Java hub that speaks SAML, OAuth, OIDC, WS-Fed and brokers trust for the whole company (§1). PingOne is the cloud sibling (§2).
- **The Ping family:** PingFederate (federation), PingOne (cloud), PingID (MFA), PingAccess (authZ proxy), PingDirectory (LDAP) — *who you are* vs *what you can reach* vs *second factor* vs *user store* (§2).
- **Architecture:** admin console (configure) vs runtime engines (live logins); prod clusters engines behind a load balancer; **`audit.log` + `server.log`** are your debugging home (§3).
- **The core mental model — connections:** **SP connection** = Ping is the IdP for an app; **IdP connection** = Ping is the SP trusting a partner IdP. *The connection is named after the other guy* (§4).
- **Building blocks:** adapters (how you authenticate), datastores (where users live), policy trees (routing), attribute contract + fulfillment (*what* the token carries and *from where*), and per-connection signing/encryption certs (§5).
- **As an OAuth AS:** clients, grant types, scopes, **Access Token Managers** (JWT = offline/fast/hard-to-revoke vs reference = opaque/revocable), OIDC policies, introspection; plus the legacy STS/WS-Trust corner (§6).
- **Two walkthroughs:** creating a SAML SP connection (+ the `/idp/startSSO.ping?PartnerSpId=` smoke test), and the symptom→cause→where-to-look debugging playbook (§7–8).
- **It's crown-jewel infrastructure:** compromise = every app; protect the console, patch fast, watch cert expiries, ship audit logs to the SIEM (§9).

## Next

- **Deepen the OAuth half:** [**note 19 — OAuth 2.0 in practice**](19-oauth2-in-practice.md) — wiring a real client with PKCE, refresh tokens, and the mistakes that bite in production.
- **Ask Lefler** to stand up a lab: **Keycloak** (a free, open-source federation server that mirrors most PingFederate concepts) — configure an OIDC client and a SAML SP connection, then break a weak JWT, so the words above become muscle memory.
- **Cross-links:** [Kerberos & IWA adapter](15-kerberos-explained.md) · [SAML bindings & certificates](16-saml-bindings-and-certificates.md) · [SAML Mastery Session 2](13-saml-mastery-session2.md) · [OAuth/OIDC deep dive](03-oauth-oidc-deep-dive.md).

*— Janus 🔐, your gate into the federation server you'll live in at FinCo.*
