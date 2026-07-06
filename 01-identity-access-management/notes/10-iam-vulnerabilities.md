# Vulnerabilities in IAM systems — the identity attack surface

> **Janus's deep dive, written to [Lefler's Laws](../../LEFLER-LAWS.md).** Identity is the new perimeter — so identity is also where attackers aim. This note is a **catalog of the ways IAM systems get broken**, grouped by layer, each mapped to its **OWASP** category, with the fix. It pulls together the attacks scattered across notes 02–07 into one reference. **Authorized-lab-only** for anything hands-on. Companion: [OWASP Top 10](../../03-application-security/notes/01-owasp-top-10.md).

---

## The 30-second version (TL;DR)

- Most IAM vulnerabilities fall under **two OWASP Top 10 items**: **A01 Broken Access Control** (authZ gone wrong) and **A07 Identification & Authentication Failures** (authN gone wrong).
- For APIs (fintech is API-heavy), the big one is **BOLA — Broken Object Level Authorization** (OWASP **API** Top 10 #1).
- Below, each vuln has a **plain "what it is," an attack, and a defense.** Pair them — that's the repo rule.

> **Mental model:** an IAM vuln is any way an attacker becomes **someone they're not** (authN) or does **something they shouldn't** (authZ) — or any gap that lets that go **unseen** (logging).

---

## 1. Broken authentication (OWASP A07)

*Attacker proves to be someone they're not.*

| Vulnerability | What it is | Defense |
|---|---|---|
| **Weak passwords / credential stuffing** | Reused/leaked passwords tried at scale | Strong password policy, breached-password checks, **MFA**, rate-limiting/lockout |
| **No MFA** | A single stolen password = full access | Enforce **phishing-resistant MFA** (FIDO2/passkeys) — esp. into a CDE ([PCI Req 8.4](09-pci-dss-and-iam.md)) |
| **MFA fatigue / push bombing** | Spam push prompts until the user taps "approve" | **Number-matching**, push limits, phishing-resistant factors |
| **Session fixation / hijacking** | Attacker sets or steals a session ID | Rotate session ID on login; `HttpOnly`/`Secure`/`SameSite` cookies ([appsec §4](../../03-application-security/README.md)) |
| **Weak/guessable session tokens** | Predictable IDs let attackers forge sessions | Long, random, server-side session IDs |
| **Account-recovery abuse** | Weak "forgot password" flow bypasses login | Treat recovery as auth: verify strongly, rate-limit, alert |

Depth: [note 07 §MFA & sessions](07-iam-foundations.md).

---

## 2. Token & JWT vulnerabilities (OWASP A07 / A02)

*The signed "proof of login" is forged, stolen, or mis-trusted.*

| Vulnerability | What it is | Defense |
|---|---|---|
| **`alg: none`** | Attacker strips the signature; a naive lib accepts it | Reject `none`; **allow-list** the algorithm |
| **Algorithm confusion (RS256→HS256)** | Attacker signs with the public key as an HMAC secret | Bind verification to the key type; verify via **JWKS** by `kid` |
| **Weak HMAC secret** | Short/guessable signing secret is brute-forced | Long random secrets; prefer asymmetric (RS256/ES256) |
| **No expiry / no audience check** | Tokens live forever or are replayed to another app | Validate `exp`, `nbf`, **`aud`**, `iss`, `nonce` |
| **Token leakage** | Tokens in URLs, logs, referrer headers, local storage | Auth-code flow (not implicit); careful storage; never log tokens |
| **Refresh-token theft** | Long-lived token = persistent access | Rotate refresh tokens; detect reuse; revoke on logout |

Depth: [note 03 §9 (JWT internals & attacks)](03-oauth-oidc-deep-dive.md). Crypto: [`04-cryptography`](../../04-cryptography/README.md) §5–6.

---

## 3. OAuth / OIDC flow vulnerabilities (OWASP A01 / A07)

| Vulnerability | What it is | Defense |
|---|---|---|
| **`redirect_uri` manipulation** | Loose redirect sends the auth code to the attacker | **Exact-match** allow-list of redirect URIs |
| **Missing `state`** | Login CSRF — attacker injects their code into your session | Generate + verify **`state`** |
| **Missing PKCE** | Intercepted auth code is replayed (mobile/SPA) | **PKCE** on all clients |
| **Mixing ID & access tokens** | ID token used as API credential, or vice-versa | Enforce **audience** checks on both sides |
| **Scope escalation / over-consent** | App requests/gets more than it needs | Least-privilege scopes; review consents |

Depth: [note 03 §10 (attacks table)](03-oauth-oidc-deep-dive.md).

---

## 4. SAML vulnerabilities (OWASP A01 / A07)

| Vulnerability | What it is | Defense |
|---|---|---|
| **XML Signature Wrapping (XSW)** | Forged assertion smuggled past signature validation | Hardened SAML lib; process exactly the signed element; reject multiple assertions |
| **Unsigned-assertion acceptance** | SP accepts an assertion with no/partial signature | Require the **Assertion** itself to be signed |
| **Assertion replay** | Captured valid assertion re-sent | Short `NotOnOrAfter`, one-time-use IDs, check `InResponseTo` |
| **Audience/Recipient not checked** | Assertion for App-A replayed to App-B | Strictly validate `Audience` + `Recipient` |
| **XXE** | Malicious XML entity reads files / SSRF | Disable external-entity resolution |
| **Golden SAML** | Steal the IdP signing key → forge *any* assertion | Protect signing keys (HSM/PAM); monitor for anomalous issuers |

Depth: [note 02 §9 (SAML attacks)](02-saml-deep-dive.md).

---

## 5. Broken access control / authorization (OWASP A01 · API #1 BOLA)

*Authenticated, but doing something they shouldn't.* **This is OWASP's #1 web risk.**

| Vulnerability | What it is | Defense |
|---|---|---|
| **IDOR / BOLA** | Change an ID in the URL/API (`/account/123`→`124`) to reach another user's data | Check ownership on **every** object access, server-side |
| **Missing function-level access control** | Hidden admin endpoint reachable by a normal user | Enforce role checks on every function, default-**deny** |
| **Vertical privilege escalation** | Normal user gains admin rights | Server-side authZ; never trust client-side role flags |
| **Horizontal privilege escalation** | User A acts as User B (same privilege level) | Bind actions to the authenticated subject |
| **Over-privileged roles** | Everyone is effectively admin (entitlement creep) | **Least privilege**, role reviews ([PCI Req 7](09-pci-dss-and-iam.md)) |

Depth: [note 07 §authZ models](07-iam-foundations.md), [appsec §1/§3/§12](../../03-application-security/README.md).

---

## 6. Directory / LDAP / Active Directory (OWASP A03 injection + AD attacks)

*Lab-only for the offensive techniques — never production/FinCo.*

| Vulnerability | What it is | Defense |
|---|---|---|
| **LDAP injection** | Unsanitized input alters an LDAP filter → auth bypass/data leak | Escape special chars, parameterize, allow-list input |
| **Anonymous / cleartext bind** | Directory readable unauthenticated, or password sent in clear | Disable anonymous bind; enforce **LDAPS/StartTLS** |
| **Kerberoasting** | Request service tickets, crack weak service-account passwords offline | Strong/managed service passwords (gMSA); monitor TGS requests |
| **Pass-the-Hash** | Reuse a stolen NTLM hash without the password | Limit NTLM, Credential Guard, tiered admin |
| **Over-privileged service accounts** | A cracked service account owns half the domain | Least privilege, gMSA, no interactive login |
| **Golden Ticket** | Forge TGTs after stealing `krbtgt` → total domain takeover | Protect DCs, rotate `krbtgt`, detect anomalies |

Depth: [note 04 §3–5 (LDAP risks, Kerberos attacks)](04-ldap-ad-entra.md). Ask **Loki** to demo in a lab, **Heimdall** for detections.

---

## 7. Federation & trust vulnerabilities (OWASP A01 / A05)

| Vulnerability | What it is | Defense |
|---|---|---|
| **Cert not validated / embedded-cert trust** | SP trusts a cert from the message itself | Validate only against the **pre-configured** IdP cert |
| **Expired-cert / clock-skew mishandling** | Outages, or a widened window that enables replay | Monitor cert expiry; sync **NTP**; tight skew tolerance |
| **IdP-initiated replay** | No `InResponseTo` to bind response to a request | Prefer SP-initiated; one-time-use assertions |
| **Misconfigured trust / rogue IdP** | A second, attacker-controlled IdP is trusted | Tightly govern trust config; alert on new issuers/certs |

Depth: [note 02](02-saml-deep-dive.md), [note 06 (cert/PKI trust)](06-tls-https-mtls.md).

---

## 8. Provisioning, governance & privileged (OWASP A01 / A09)

| Vulnerability | What it is | Defense |
|---|---|---|
| **Orphaned / stale accounts** | A leaver still enabled (a classic breach entry point) | Prompt deprovisioning; **access reviews** ([PCI 7.2.4](09-pci-dss-and-iam.md)) |
| **SoD violations / toxic combinations** | One person can both request *and* approve | Enforce **Separation of Duties**; certify combos |
| **SCIM misconfiguration** | Provisioning grants wrong/too much access | Validate mappings; least-privilege birthright |
| **Hard-coded / leaked secrets** | Client secrets, keys, service creds in code/repos/logs | **Vault + rotate** (PAM); secret scanning; `.gitignore` |
| **Standing privilege** | Admin rights held 24/7, waiting to be abused | **Just-in-time** access; session recording |

Depth: [note 07 §PAM & IGA](07-iam-foundations.md).

---

## 9. Misconfiguration & monitoring gaps (OWASP A05 / A09)

| Vulnerability | What it is | Defense |
|---|---|---|
| **Default / shared credentials** | `admin/admin`, generic accounts nobody owns | Unique IDs, change defaults, no shared logins ([PCI 8.2](09-pci-dss-and-iam.md)) |
| **Permissive exceptions** | A Conditional Access "exclude" or **PERMISSIVE mTLS** left on | Review exceptions; enforce **STRICT** ([note 06](06-tls-https-mtls.md)) |
| **Verbose errors** | Login errors reveal whether a username exists | Generic error messages; same timing |
| **No auth logging / no attribution** | Can't tell who did what (shared accounts) | Log all auth events, tied to unique IDs; alert on anomalies → **Heimdall** |

---

## 10. The OWASP mapping (one table)

| IAM vulnerability area | OWASP Top 10 | Also |
|---|---|---|
| Broken authentication, tokens, MFA, sessions | **A07** Identification & Authentication Failures | A02 (crypto) |
| Broken access control, IDOR, priv-esc, SAML/OAuth authZ | **A01** Broken Access Control | **API #1 BOLA** |
| LDAP injection | **A03** Injection | — |
| Default creds, permissive config, exceptions | **A05** Security Misconfiguration | — |
| No logging / can't attribute | **A09** Logging & Monitoring Failures | — |
| Leaked secrets, unsigned trust, supply chain | **A08** Data Integrity Failures | A06 components |

Full OWASP walkthrough: [../../03-application-security/notes/01-owasp-top-10.md](../../03-application-security/notes/01-owasp-top-10.md).

---

## What you learned

- Almost every IAM vuln is **A01 (authZ)** or **A07 (authN)** — plus **BOLA** for APIs.
- You can now name the attack **and** the defense for each layer — the exact purple-team habit the repo trains.
- Many of these you've already met in notes 02–07; this is your **one-page index** to them.

## Next

- Practice safely: the [JWT/OIDC lab](../labs/01-keycloak-idp/README.md) (`alg:none`, redirect_uri) and the [SAML assertion lab](../labs/02-saml-assertion-anatomy/README.md) (XSW). Ask **Loki** to attack, **Heimdall** to detect, **Lefler** to build — purple teaming.
- Read the companion [OWASP Top 10](../../03-application-security/notes/01-owasp-top-10.md) note.

*— Janus 🔐 (to Lefler's Laws ⚙️)*
