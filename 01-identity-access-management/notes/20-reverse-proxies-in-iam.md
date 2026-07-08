# Reverse proxies in IAM — the gate that does the logging-in for your apps

> **Janus's deep dive.** You keep hearing "put it behind the proxy," "PingAccess will enforce that," "the ingress does forward-auth." All of those are the *same idea*: a **gate that sits in front of your apps and does the authentication for them**. This note builds that idea from scratch, then shows you every place it shows up in your world — **PingFederate + PingAccess + Active Directory**, your **Kubernetes** ingress, and the **Envoy** sidecars already running in your mesh.
>
> **Prereqs:** [TLS/HTTPS/mTLS (note 06)](06-tls-https-mtls.md) (service mesh + Envoy sidecars — we build directly on it), [IAM foundations (note 07)](07-iam-foundations.md) (PDP/PEP + Zero Trust — we make the PEP concrete), and the handshake notes we'll route users through: [SAML #1 (02)](02-saml-deep-dive.md) · [OAuth/OIDC (03)](03-oauth-oidc-deep-dive.md) · [SAML Mastery (13)](13-saml-mastery-session2.md) · [bindings & certs (16)](16-saml-bindings-and-certificates.md) · [OAuth in practice (19)](19-oauth2-in-practice.md). PingAccess itself was named in [PingFederate (18)](18-pingfederate-explained.md) — this note gives it its proper treatment.

---

## TL;DR (the whole note in seven lines)

1. A **forward proxy** stands in front of the **client** (represents *you* going out); a **reverse proxy** stands in front of the **server** (represents the *app* to the world). This note is about the reverse kind.
2. It exists because things **every** backend needs — TLS termination, one public entry point, load balancing, hiding internal servers — shouldn't be built N times. **Factor the common concern out to one gate.** (Same instinct as the k8s sidecar in [note 06](06-tls-https-mtls.md).)
3. **In IAM, we move authentication/authorization to that gate.** The reverse proxy becomes the concrete **Policy Enforcement Point (PEP)** from [note 07 §6](07-iam-foundations.md) — "auth for apps that can't do auth themselves."
4. The **authenticating reverse proxy / Identity-Aware Proxy (IAP)** pattern: no session → redirect to the IdP (SAML/OIDC) → user logs in → proxy sets a session cookie → every later request is checked and forwarded, with identity injected as **headers or a signed JWT**.
5. The **gotcha:** the backend trusts those identity headers, so it **must** be network-isolated — nobody may reach it except through the proxy, or they can spoof the headers and walk in.
6. **Your stack:** **PingAccess** (Ping's authZ proxy, pairs with PingFederate), **oauth2-proxy**, **nginx-ingress / Traefik forward-auth**, and **Envoy `ext_authz`** (the *same* sidecar doing mTLS in note 06 can also enforce authZ). Legacy: SiteMinder, Oracle Access Manager.
7. Real answer is usually **both**: proxy does authN + coarse authZ, the app does fine-grained authZ. **Defense in depth.**

---

# PART A — Reverse proxies from first principles

## 1. Forward vs reverse — which side of the conversation is it on?

A **proxy** is just a middleman that sits between two parties and relays traffic. The only question is *whose side* it's on.

| | **Forward proxy** | **Reverse proxy** |
|---|---|---|
| Sits in front of | the **client** (the user's browser/app) | the **server(s)** (your apps) |
| Represents | **you**, going out to the internet | the **app**, to the outside world |
| Who knows it's there | the client is configured to use it | the client has *no idea* — it thinks the proxy *is* the server |
| Everyday example | a corporate web filter — all staff browsing goes out through it | the front door of `bank.example.com` — every visitor hits it first |

**The analogy that makes it stick — a building's front desk.**
Every visitor to a big office building walks up to the **receptionist** first. They don't wander the halls looking for the right office. The receptionist greets them, checks their appointment, issues a visitor badge, and *then* directs them to the correct floor. The offices behind never deal with the street directly — the front desk is the single, controlled point of entry.

That receptionist is a **reverse proxy**. The offices are your **backend apps**. Hold that picture — Part B is just "the receptionist also checks your ID and logs you in."

> **Job tie-in:** when a coworker says "it's behind the proxy," they mean "clients never touch the app directly — they hit the gate, and the gate forwards approved traffic inward." Every FinCo-facing app lives behind one.

---

## 2. Why it *must* exist — derive it from the constraint

Imagine FinCo has **N** apps: the payments console, the HR portal, three internal wikis, a customer dashboard. Now list what *every single one* of them needs before it can even do its job:

- **TLS termination** — decrypt HTTPS, manage the certificate, renew it.
- **A public entry point** — one address the world can reach, one place to firewall.
- **Load balancing** — spread traffic across multiple copies of the app.
- **Hiding internal topology** — the world shouldn't learn your internal hostnames/IPs.
- **Caching / compression / a WAF** — performance and a filter against obvious attacks.

If each app builds all of that itself, you've built the same plumbing **N times** — N certs to rotate, N load balancers to tune, N places to get the security wrong. That's the exact pain that makes engineers factor a shared concern **out** into one place.

**The principle:** *when many things need the same cross-cutting job, don't duplicate it — put it in one gatekeeper in front of them all.*

You have already seen this instinct. In [note 06 §6](06-tls-https-mtls.md), instead of every app implementing TLS, the mesh injects **one sidecar** per pod to handle encryption transparently. Same move: **factor the common concern out to a proxy.** A reverse proxy is that idea applied at the *edge* of your app estate; the sidecar is that idea applied *per pod*. Part B adds one more common concern to factor out — **login itself**.

---

## 3. The mechanics — one request, hop by hop

Here is what actually happens when a client hits an app behind a reverse proxy. The key fact: **the client only ever talks to the proxy.** The backend is on a private network the client can't reach.

```
   CLIENT (browser)              REVERSE PROXY                 BACKEND APP
   (public internet)         (edge.finco.example)          (private net, no public IP)
        |                            |                             |
   1.   |==== HTTPS (TLS) ==========>|  2. terminate TLS           |
        |    GET /reports            |     (decrypt, read request) |
        |                            |  3. route: which backend?   |
        |                            |     (by Host / URL path)    |
        |                            |  4. add X-Forwarded-* headers
        |                            |---- HTTP (or re-encrypted -->|  5. app handles
        |                            |        internal TLS) ------->|     /reports
        |                            |<---- response ---------------|
        |<=== HTTPS response ========|  6. (optional) cache/compress
        |                            |                             |
```

1. Client opens **one TLS connection to the proxy** (`edge.finco.example`), thinking it *is* the app.
2. Proxy **terminates TLS** — it holds the cert and private key, decrypts, and can now read the request.
3. Proxy **routes**: by `Host:` header (which app) and/or URL path (`/reports` vs `/api`).
4. Proxy opens a **separate connection** inward to the chosen backend, adding `X-Forwarded-*` headers (see §5).
5. Backend processes and replies; the client's identity/IP is invisible to the wire in front — the app sees the *proxy* as its client.
6. Response flows back; proxy may cache or compress before returning it over the original TLS connection.

**The one line to remember:** *two connections, not one — client↔proxy and proxy↔backend — and the client never learns the backend exists.* That's what "hiding internal topology" means concretely.

---

## 4. The classic jobs (and the real products that do them)

One line each — this is the "what a reverse proxy is for" checklist:

| Job | What it does |
|---|---|
| **TLS termination** | Decrypts HTTPS at the edge so backends don't each manage certs (re-encrypt inward if needed — §11). |
| **Load balancing** | Spreads requests across many copies of a backend (round-robin, least-conn, sticky sessions). |
| **Path / host routing** | `Host: pay.finco` → payments pods; `/api/*` → the API service. One address, many apps. |
| **Caching** | Stores responses so repeat requests skip the backend — faster, less load. |
| **Compression** | gzip/brotli responses to save bandwidth. |
| **Hiding internal servers** | The world sees one edge; internal hostnames/IPs/versions stay secret (smaller attack surface). |
| **WAF (Web Application Firewall)** | Filters obvious attacks (SQLi, XSS, path traversal) before they reach the app. |

**Real products you'll meet:**

| Category | Names |
|---|---|
| Classic software proxies | **nginx**, **HAProxy**, **Apache httpd** |
| Cloud-native / dynamic | **Envoy** (the mesh sidecar from note 06), **Traefik** |
| Cloud managed | **AWS ALB/ELB**, **Cloudflare**, GCP/Azure load balancers |

> **Job tie-in:** your Kubernetes **ingress controller** is *literally* one of these (usually nginx or Traefik) running as the cluster's front door. When someone says "add an ingress rule," they're configuring a reverse proxy's routing table.

---

## 5. The `X-Forwarded-For` trap — attack paired with defense (Law 9)

Because the backend now sees the **proxy** as its client, it loses two things it often needs: the **real client IP** and **whether the original request was HTTPS**. The proxy hands those back as headers:

| Header | Carries | Backend uses it for |
|---|---|---|
| `X-Forwarded-For` | the original client IP | audit logs, geo/rate-limiting, fraud checks |
| `X-Forwarded-Proto` | `https` or `http` (the original scheme) | "was this really over TLS?" redirects, cookie `Secure` decisions |
| `X-Forwarded-Host` | the original `Host:` the client asked for | building correct absolute URLs |

**The attack — header spoofing.** These are *just headers* — a client can send them too. If a backend blindly trusts an incoming `X-Forwarded-For`, an attacker sends `X-Forwarded-For: 10.0.0.5` and **forges their source IP** in your logs and fraud engine, or sends `X-Forwarded-Proto: https` to fool an app into thinking a plaintext request was secure. At a fintech that's fraud-attribution poisoning and audit-trail corruption — a compliance problem, not just a bug.

**The defense — trust only the proxy, strip client-supplied copies at the edge.**

1. Configure the **edge proxy to overwrite (not append blindly)** these headers, discarding whatever the client sent.
2. Backends should trust `X-Forwarded-*` **only from the known proxy IP(s)** — never from an arbitrary source.
3. In nginx: use `real_ip_header` with a trusted `set_real_ip_from <proxy-CIDR>`; in cloud LBs, the managed proxy handles this for you.

> **The rule that generalizes to all of Part B:** *a header from the proxy is only trustworthy if nobody except the proxy can set it.* Remember that sentence — it's the entire security model of identity-injecting proxies (§8).

---

# PART B — Reverse proxies in IAM (the main event)

## 6. The key move — put authN/authZ *at the gate*, not in every app

Back to the receptionist. So far she just directs visitors. Now give her one more job: **check every visitor's ID and log them in before they reach any office.** The offices stop building their own sign-in desks — the front desk does it for all of them.

That is a reverse proxy doing **authentication and authorization**. And it is the **concrete form of the PEP (Policy Enforcement Point)** you met in [note 07 §6](07-iam-foundations.md): the gate that *enforces* an allow/deny decision. (The **PDP** — the brain that *decides* — is the IdP/policy engine it talks to. This note doesn't redefine those; it shows you the PEP made of nginx or Envoy.)

**Why must this exist?** Derive it from a constraint you can't wish away:

- FinCo runs **legacy and COTS (commercial off-the-shelf) apps** — a vendor payments console, an old Java admin tool, a reporting server. Many **cannot** be retrofitted with OIDC or SAML. You don't have the source; the vendor won't add it; rewriting is a year of work.
- Yet **every** app must enforce login + MFA (PCI-DSS Req 8, SOX ITGC — [note 09](09-pci-dss-and-iam.md)).
- You can't put login *inside* apps you can't change.

**So you put login in front of them.** A proxy that authenticates *before* forwarding gives even a brain-dead backend real SSO + MFA — **without touching one line of the app's code.** That's the headline: *authentication for applications that can't authenticate themselves.*

---

## 7. The authenticating reverse proxy / Identity-Aware Proxy (IAP) pattern

This is the flow to be able to narrate aloud. An **unauthenticated** request arrives; the proxy has no session for it, so it runs a full IdP handshake, then remembers the user with a cookie.

```
  BROWSER                 AUTH PROXY (PEP)              IdP (PingFederate)         BACKEND APP
     |                         |                              |                        |
 1.  |--- GET /reports ------->|  2. no session cookie →      |                        |
     |                         |     start login              |                        |
 3.  |<-- 302 redirect to IdP -|                              |                        |
 4.  |------- SAML AuthnRequest / OIDC auth request --------->|                        |
     |                         |         5. user logs in      |                        |
     |                         |            (AD password +    |                        |
     |                         |             PingID MFA)      |                        |
 6.  |<---- assertion / code (front-channel) -----------------|                        |
 7.  |--- POST assertion / code --->|  8. validate it,        |                        |
     |                         |      map identity,           |                        |
     |                         |   9. SET SESSION COOKIE      |                        |
 10. |<-- 302 back to /reports -| (Secure, HttpOnly)          |                        |
 11. |--- GET /reports (cookie)>| 12. cookie valid?           |                        |
     |                         |     authorized for /reports? |                        |
     |                         | 13. INJECT identity ---------+----------------------->|
     |                         |     X-Authenticated-User: farhaan                     |  14. trusts
     |                         |     (or a signed JWT)         |                        |     the header,
     |<-- 200 the report ------|<------------------------------+------- response -------|      serves data
```

Step by step:

1. Browser asks for `/reports`.
2. Proxy finds **no session cookie** — this user hasn't logged in here.
3–6. Proxy **redirects to the IdP** and runs the standard handshake — **SAML** (AuthnRequest → assertion, [note 13](13-saml-mastery-session2.md)) or **OIDC** (auth request → code, [note 03](03-oauth-oidc-deep-dive.md)). The IdP does the actual authentication: AD password + **PingID** MFA.
7–8. IdP returns the assertion/code to the proxy, which **validates** it (signature, audience, expiry) and maps it to a user.
9–10. Proxy **establishes its own session** — sets a `Secure; HttpOnly` cookie — and bounces the user back to `/reports`.
11–12. Every later request carries that cookie; the proxy checks it and (coarsely) **authorizes**: is this user allowed on this path?
13. Proxy **forwards** the request inward, **injecting identity** so the backend knows who it is — either a plain header (`X-Authenticated-User: farhaan`) **or a short-lived signed JWT** the backend verifies.
14. Backend trusts the injected identity and serves the data. **It never implemented login.**

**The two-sessions echo:** just like SAML's IdP-session vs SP-session ([note 13 §3](13-saml-mastery-session2.md)), here there's the **IdP session** (at PingFederate) and the **proxy session** (the cookie the gate sets). Second app behind the same proxy/IdP? The IdP session already exists → no re-prompt → that's SSO.

---

## 8. The header-injection trust gotcha — attack + defense (Law 9)

Look at step 13 again. The backend serves data **purely because it saw `X-Authenticated-User: farhaan`.** It did zero verification — it *trusts the proxy*. That trust is the whole design, and also its soft underbelly.

**The attack — bypass the proxy, spoof the header.** If an attacker can reach the backend **directly** (not through the proxy), they send their own request:

```
GET /reports HTTP/1.1
Host: internal-reports-app
X-Authenticated-User: ceo          ← forged; no login ever happened
```

...and the app hands over the CEO's reports. No password, no MFA, no assertion — the app can't tell a proxy-set header from a client-set one. This is the exact same failure as the `X-Forwarded-For` trap in §5, now with catastrophic stakes.

**The defense — make the backend unreachable except through the proxy.** Belt and braces:

1. **Network isolation** — the backend has **no public IP** and lives on a private network/subnet. The only host allowed to open a connection to it is the proxy. In Kubernetes, a **NetworkPolicy** that permits ingress *only* from the ingress/proxy pod.
2. **mTLS from proxy to backend** — the backend accepts connections **only** from a client presenting the proxy's certificate. This is exactly the **mesh mTLS from [note 06](06-tls-https-mtls.md)** doing double duty: it both encrypts the inward hop *and* proves "this really is our proxy." STRICT mode ([note 06 §6](06-tls-https-mtls.md)) means a direct plaintext request is *rejected*.
3. **Strip the identity headers at the edge** — the proxy must **overwrite/delete** any incoming `X-Authenticated-User` from the client before forwarding, so a client can never smuggle one in.
4. **Prefer a signed JWT over a plain header** — if the proxy injects a **JWT signed by a key the backend verifies**, a spoofed header fails signature validation even if the network isolation is breached. Defense in depth: the header is only as good as the isolation; the signature stands on its own.

> **Fintech framing:** an auditor *will* ask "what stops someone hitting the app directly and forging the identity header?" The clean answer — *"network policy plus proxy-only mTLS, and we inject a signed JWT the app validates"* — is exactly the maturity they're checking for.

---

## 9. Where this shows up in *your* world

The pattern above has a name in every ecosystem. Here are the ones you'll actually meet at FinCo.

### 9a. PingAccess — the FinCo tie-in

**PingAccess** is Ping's **authenticating reverse proxy / Web Access Management (WAM) gateway** — introduced in passing in [note 18 §2](18-pingfederate-explained.md), and this is its proper home. The division of labour with PingFederate is the thing to say crisply:

- **PingFederate = the token issuer / IdP.** It answers *"who are you?"* (authN) and mints the assertion/token. It is the **PDP** for identity.
- **PingAccess = the gate.** It sits **in front of** web apps and APIs, holds the user's session, and enforces *"is this user allowed on this URL/API?"* (coarse authZ). It is the **PEP** — the concrete reverse proxy from this whole note.

```
User ──▶ PingAccess (PEP: session? allowed on this path?) ──▶ PingFederate (PDP: who are you? + MFA)
              │  injects identity headers / signed JWT               │
              ▼                                                      └─▶ AD + PingID
        protected web app / API  (never implemented login itself)
```

PingAccess uses **policies** (rules per resource) and can front both **web apps** (browser sessions, the flow in §7) and **APIs** (validating the OAuth token PingFederate issued — [note 18 §6](18-pingfederate-explained.md)). If FinCo runs the Ping stack end to end, **PingAccess is the reverse proxy you'll configure**, and PingFederate is the IdP behind it.

### 9b. oauth2-proxy — the open-source workhorse

**oauth2-proxy** is a small, popular open-source proxy that does exactly §7: it authenticates a user against any OIDC/OAuth provider (Keycloak, Entra, Google, PingFederate) and only then lets the request through. It's the default way to bolt login onto an app that has none, and it's everywhere in **Kubernetes + nginx-ingress** setups (see 9c). You'll use it in the Part C lab.

### 9c. Kubernetes ingress + forward-auth — your infra

Your cluster's **ingress controller** (nginx-ingress or Traefik) *is a reverse proxy* — the cluster's front door. Both support a **forward-auth (external auth)** pattern: before forwarding a request to the app, the ingress fires a **subrequest to an auth service** (like oauth2-proxy) and forwards the real request **only if that service says 200 OK**.

```
Client ─▶ nginx-ingress ──(subrequest)──▶ oauth2-proxy ──▶ Keycloak/PingFederate
                │           allow (200)? deny (401→login)?
                ▼ if 200, with injected identity headers
             backend Service (the app)
```

- **nginx-ingress:** the `auth-url` / `auth_request` annotation — nginx calls the auth service per request.
- **Traefik:** the **ForwardAuth** middleware — same idea, different config.

The auth service returns identity as response headers (`X-Auth-Request-User`, `X-Auth-Request-Email`) that the ingress copies onto the upstream request. **This is the §7 pattern, running in your cluster.** And §8 applies: lock the backend Services down with a **NetworkPolicy** so only the ingress can reach them.

### 9d. Service mesh (Envoy) `ext_authz` — the sidecar's second job

Here's the connection worth internalizing. In [note 06 §6](06-tls-https-mtls.md), the **Envoy sidecar** in each pod transparently did **mTLS**. That same sidecar has an **external authorization filter, `ext_authz`**: for each request, Envoy can **call out to an authorization service** ("may this identity do this?") and allow/deny before the request reaches the app container.

> **The same Envoy sidecar doing mTLS in note 06 can enforce authZ here.** mTLS answered *"which workload is calling?"* (transport identity); `ext_authz` answers *"is this call allowed?"* (per-request authZ). One sidecar, both halves of Zero Trust — verify the connection **and** authorize the request, on every hop. That's [NIST 800-207](07-iam-foundations.md) continuous verification made real.

**API-focused cousins:** **API gateways** (Kong, Apigee) are the same reverse-proxy-as-PEP idea aimed at APIs rather than browser apps — validate the OAuth token, enforce scopes/rate limits, then forward.

### 9e. Legacy WAM — the older generation

One line so you recognize them in a fintech: **CA/Broadcom SiteMinder** and **Oracle Access Manager (OAM)** are the **previous generation** of this exact pattern — web-agent/proxy gatekeepers that predate OIDC. Same concept (auth at the gate, identity injected as headers), older plumbing. If you meet one, it *is* an authenticating reverse proxy, just a 2005-vintage one.

---

## 10. Proxy enforcement vs in-app enforcement — and why you want both

Should auth live at the **gate** or **inside the app**? It's not either/or.

| | **Reverse-proxy enforcement (PEP at the edge)** | **In-app enforcement** |
|---|---|---|
| Granularity | **Coarse** — per URL/path/host ("can you reach `/admin`?") | **Fine** — business logic ("can you approve *this* £2M payment?") |
| Needs app code changes? | **No** — app-agnostic, works on legacy/COTS | **Yes** — the app must implement checks |
| Knows business context? | No — it only sees the HTTP request | Yes — it knows accounts, balances, ownership |
| Great for | SSO + MFA for apps that can't do it; a uniform front door | authorization that depends on *what's inside* the request |
| Weakness | can't see "does farhaan own account #123?" | every app reinvents login; easy to get wrong |

**The real answer is defense in depth — both:**

> **The proxy handles authN + coarse authZ** (you're logged in, MFA'd, and allowed in this URL space). **The app handles fine-grained authZ** (you may only touch *your* customers' records). The gate keeps the anonymous internet out; the app makes the per-object decision the gate can't. Neither alone is enough at a bank.

---

## 11. Security considerations — the proxy is a crown jewel

Concentrating auth at one gate is powerful *and* dangerous. The risks, each with the mitigation:

| Concern | Why it bites | What "good" looks like |
|---|---|---|
| **Chokepoint = crown jewel** | Compromise the proxy → access to *everything* behind it (like owning PingFederate in [note 18 §9](18-pingfederate-explained.md)). | Harden and patch it, minimal admin access, isolate it, ship its logs to the SIEM (ask **Heimdall**). |
| **Single point of failure** | Proxy down = *all* apps behind it are unreachable — a company-wide outage. | **HA**: run several proxy instances behind a load balancer / multiple ingress replicas. |
| **TLS termination = plaintext behind it** | Traffic is decrypted at the gate; if the internal hop is plaintext, anyone on that network reads it. | **Re-encrypt inward** — proxy→backend over TLS/mTLS. This is precisely the **mesh mTLS in [note 06](06-tls-https-mtls.md)** as the re-encryption layer. |
| **Session management at the proxy** | The proxy's session cookie *is* the keys to the app. Stolen cookie = impersonation. | `Secure; HttpOnly; SameSite`, short lifetimes, bind sessions carefully, idle timeout. |
| **Open-redirect in the login round-trip** | The "return to where you were" URL (RelayState / `redirect_uri`) can be abused to bounce users to an attacker's site after login. | **Allow-list** redirect targets; never redirect to an arbitrary client-supplied URL. (Same class as OAuth `redirect_uri` abuse, [note 03](03-oauth-oidc-deep-dive.md) / [note 19](19-oauth2-in-practice.md).) |
| **Header spoofing / proxy bypass** | §8 — direct backend access forges identity. | Network isolation + proxy-only mTLS + strip inbound identity headers + inject a signed JWT. |

> **Attack ↔ defense (repo rule):** the offensive dream is to **reach a backend directly** and forge `X-Authenticated-User`, or to **steal the proxy session cookie**. Defenses: NetworkPolicy/mTLS so only the proxy can reach backends, signed-JWT identity injection, hardened session cookies, and SIEM alerts on backend traffic that didn't transit the proxy. Hand the offensive lab to **Loki** (own-lab-only); hand the detections to **Heimdall**.

---

# PART C — See it for yourself (Law 12 empirical hook)

**Goal:** watch an authenticating reverse proxy do §7 with your own eyes — the redirect to the IdP, the session cookie, and the identity header arriving at a dummy backend. You'll reuse the **Keycloak IdP** from [Lab 01](../labs/01-keycloak-idp/README.md).

- **Time:** ~30 min · **Difficulty:** beginner · **Needs:** Docker Desktop + the running Keycloak from Lab 01. · **Authorized-lab-only**, all on your machine.

**The shape:** `browser → oauth2-proxy → dummy backend`, with oauth2-proxy pointing at your Keycloak realm. `http-echo` is a tiny backend that just prints whatever headers it receives — perfect for *seeing* the injected identity.

**1. In Keycloak (from Lab 01), register a client for the proxy.** Realm `finco-lab` → **Clients → Create** → Client ID `oauth2-proxy`, Client authentication **On**, Valid redirect URI `http://localhost:4180/oauth2/callback`. Copy the **client secret** from the Credentials tab (you'll paste it as a placeholder below — **never commit it**; the repo `.gitignore` blocks `.env`).

**2. Drop this `docker-compose.yml` in a scratch folder** (sketch — placeholders only):

```yaml
services:
  backend:                                  # the "app that can't do auth" — just echoes headers
    image: hashicorp/http-echo
    command: ["-text=hello from the backend"]
    expose: ["5678"]                        # NOT published — only the proxy can reach it (that's §8!)

  oauth2-proxy:
    image: quay.io/oauth2-proxy/oauth2-proxy
    ports: ["4180:4180"]                    # the ONLY public door
    command:
      - --http-address=0.0.0.0:4180
      - --provider=oidc
      - --oidc-issuer-url=http://host.docker.internal:8080/realms/finco-lab
      - --client-id=oauth2-proxy
      - --client-secret=REPLACE_ME          # placeholder — put the real one in an .env, never in git
      - --cookie-secret=REPLACE_32_BYTES    # any 32-byte base64 string (openssl rand -base64 32)
      - --email-domain=*
      - --upstream=http://backend:5678      # forward approved requests here
      - --pass-user-headers=true            # inject X-Forwarded-User / X-Forwarded-Email
      - --set-xauthrequest=true
```

**3. Run it and drive the flow:**

```powershell
docker compose up -d
Start-Process "http://localhost:4180/"      # open the proxy in your browser
```

**4. Watch, in order (this is the payoff — §7 with your eyes):**

| Step | What you'll observe | Which part of §7 it proves |
|---|---|---|
| First visit | Browser **302-redirects to Keycloak's login page** (`/realms/finco-lab/protocol/openid-connect/auth?...`) | steps 2–4: no session → redirect to IdP |
| You log in | Enter `farhaan / Passw0rd!` (your Lab 01 user) + consent | step 5: the IdP authenticates |
| Back to proxy | Redirect to `/oauth2/callback`, then a **`_oauth2_proxy` session cookie is Set-Cookie'd** (see it in DevTools → Application → Cookies) | steps 8–10: proxy sets its session |
| The response | You see **"hello from the backend"** — but it came *through* the proxy after login | steps 11–14: authorized → forwarded |

**5. See the injected identity header reach the backend.** Confirm the backend never sees you directly — only the proxy does, carrying your identity:

```powershell
# WITHOUT logging in, hit the backend directly → it's not published, so this FAILS (that's §8 isolation):
curl.exe http://localhost:5678/          # connection refused — good, no direct path

# The proxy adds X-Forwarded-User / X-Auth-Request-User onto the upstream request.
# Swap http-echo for an echo image that prints headers (e.g. mendhak/http-https-echo) to read them,
# or watch the proxy logs:
docker compose logs oauth2-proxy         # shows the authenticated user on each forwarded request
```

> ✅ **Checkpoint:** you saw a **302 to the IdP**, a **session cookie** appear, and a request reach a backend that **never implemented login** — because the proxy did it. That's the entire note, running on your laptop.

**6. Teardown (Law 10):**

```powershell
docker compose down
```

> **Gotcha:** `oidc-issuer-url` must match Keycloak's issuer **exactly** (trailing-slash and hostname sensitive — inside Docker, `host.docker.internal` reaches your host's `localhost:8080`). A mismatch = "issuer did not match" — the same *exact-name* discipline as SAML EntityID ([note 13 §2](13-saml-mastery-session2.md)) and PingFederate's `PartnerSpId` ([note 18 §7](18-pingfederate-explained.md)).

---

## What you learned

- **Forward vs reverse proxy** — forward stands in front of the *client*, reverse in front of the *server*; the reverse proxy is the building's **front desk** every visitor hits first (§1).
- **Why it must exist** — factor cross-cutting concerns (TLS, one entry point, load balancing, hiding topology) out of N apps into **one gate** — the same instinct as the note-06 sidecar (§2–4).
- **The `X-Forwarded-*` trap** — the backend must trust those headers *only from the proxy*; strip client-supplied copies at the edge (§5).
- **The IAM move** — put authN/authZ at the gate; the reverse proxy is the concrete **PEP** from [note 07](07-iam-foundations.md), giving SSO + MFA to **apps that can't do auth themselves** (§6).
- **The IAP pattern** — no session → redirect to IdP (SAML/OIDC) → set a cookie → inject identity as **headers or a signed JWT** on every forwarded request (§7).
- **The header-injection gotcha** — the backend trusts the injected identity, so it **must** be reachable only through the proxy: NetworkPolicy + proxy-only mTLS + strip inbound headers + signed JWT (§8).
- **Your implementations** — **PingAccess** (with PingFederate), **oauth2-proxy**, **nginx-ingress/Traefik forward-auth**, **Envoy `ext_authz`** (the note-06 sidecar's second job), API gateways, and legacy SiteMinder/OAM (§9).
- **Both, not either** — proxy does authN + coarse authZ; the app does fine-grained authZ — defense in depth (§10) — and the proxy is a crown-jewel chokepoint to harden and make HA (§11).

## Next

- **Do the lab for real:** turn Part C into a full writeup — ask **Lefler** to build **"oauth2-proxy (or nginx forward-auth) in front of Keycloak"** as `../labs/03-reverse-proxy-auth/`, the natural follow-on to [Lab 01](../labs/01-keycloak-idp/README.md).
- **Connect it back:** re-read [PingFederate (18)](18-pingfederate-explained.md) — PingAccess (§9a) is the proxy, PingFederate the IdP behind it; and [note 07 §6](07-iam-foundations.md) — you've now made the **PEP** concrete.
- **Break it (own lab only):** ask **Loki** to try the §8 proxy-bypass / header-spoof against your lab backend, and **Heimdall** what a SIEM would flag (backend traffic that skipped the proxy).
- **More labs:** the domain [README](../README.md) labs list.

*— Janus 🔐, from the front desk — the gate that logs everyone in before they reach a single one of your apps.*
