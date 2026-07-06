# The remaining IAM foundations — MFA, sessions, authZ, PAM, IGA & Zero Trust

> **Janus's capstone.** The protocol notes ([SAML](02-saml-deep-dive.md), [OAuth/OIDC](03-oauth-oidc-deep-dive.md), [directories](04-ldap-ad-entra.md), [transport](06-tls-https-mtls.md)) cover *how identity travels*. This note covers the building blocks that sit around them — the rest of the vocabulary you'll hear in your first month. Each section is deliberately compact: the concept, the words you'll hear, one attack/defense, the fintech angle, and a pointer to go deeper. Prereq: [the landscape note](01-iam-protocol-landscape.md). Turn it into questions with [note 05](05-first-week-questions.md).

---

## 1. Authentication factors & MFA

**The concept.** Authentication proves *who you are* using one or more **factors**, categorized by type:

| Factor family | "Something you…" | Examples |
|---|---|---|
| Knowledge | **know** | password, PIN, security question |
| Possession | **have** | phone (TOTP app), security key, smart card |
| Inherence | **are** | fingerprint, face, biometrics |
| Context | location / behavior | corporate network, device posture, typing pattern |

**MFA (Multi-Factor Authentication)** = requiring two or more *different* families. A password + an OTP from the same phone app is stronger than a password alone because an attacker must compromise two independent things.

**Password security (know this cold — it's the #1 audit topic).** Passwords must be **hashed, not encrypted** — hashing is one-way, so a database breach doesn't reveal them. Each password gets a unique **salt** (defeats precomputed "rainbow tables"); a system-wide **pepper** adds a secret not stored with the hash. Use a **slow, memory-hard** algorithm — **Argon2** (preferred), **scrypt**, or **bcrypt** — never fast hashes like MD5/SHA-1. Deep dive: [`04-cryptography`](../../04-cryptography/README.md) §7.

**MFA types, weakest → strongest:**
- **SMS OTP** — better than nothing but **weak**: SIM-swapping and SS7 interception defeat it.
- **TOTP/HOTP** (authenticator apps) — time/counter-based codes; solid, but still **phishable** (a fake site relays the code in real time).
- **Push approval** — "Approve this sign-in?"; convenient but enables the attack below.
- **FIDO2 / WebAuthn / passkeys** — public-key credentials bound to the origin; **phishing-resistant** because the browser only releases the credential to the real domain. The gold standard.

| Attack | Defense |
|---|---|
| **MFA fatigue / push bombing** — attacker who has the password spams push prompts until the user taps "Approve" out of annoyance | **Number matching** (type a code shown on screen), or move to **FIDO2/passkeys** (nothing to approve blindly); limit prompt frequency |

**Fintech relevance.** PCI-DSS **Req 8** mandates MFA into the cardholder data environment; regulators increasingly push **phishing-resistant** MFA for privileged and high-risk access. Passwordless/passkeys is a strategic direction. Try it hands-on in [Lab 01](../labs/01-keycloak-idp/README.md) (Keycloak → configure OTP, then explore WebAuthn).

---

## 2. Sessions & tokens

**The concept.** Authentication happens once; a **session** keeps you logged in afterward so you don't re-authenticate on every click. That session is carried by a **cookie** (browser) or a **token** (APIs).

**Cookies & their security flags (you'll set these constantly):**
| Flag | What it does |
|---|---|
| `HttpOnly` | JavaScript can't read the cookie → blunts **XSS** cookie theft |
| `Secure` | Cookie only sent over HTTPS → not leaked on plaintext |
| `SameSite` | Restricts cross-site sending → mitigates **CSRF** |

Web-side session flaws (fixation, hijacking, weak session IDs) live in [`03-application-security`](../../03-application-security/README.md) §4.

**Token shapes:**
- **Opaque token** — a random string; the server looks it up in a store. Easy to revoke, but requires a lookup.
- **Self-contained (JWT)** — a signed token carrying claims; no lookup needed, but harder to revoke before expiry.
- **Access vs ID vs refresh** — the OIDC trio: **access** → for APIs, **ID** → who logged in (for the client), **refresh** → get new tokens. Full treatment in [note 03 §4/§8](03-oauth-oidc-deep-dive.md).

| Attack | Defense |
|---|---|
| **Session hijacking** — stealing a session cookie/token (via XSS, sniffing, or logs) to impersonate the user | `HttpOnly`+`Secure`+`SameSite` cookies, TLS everywhere, short lifetimes, rotate on privilege change, bind tokens to client/device |

**Fintech relevance.** Short session lifetimes + step-up authentication for sensitive actions (moving money) are common controls; token handling is a frequent app-integration ticket.

---

## 3. Authorization models — RBAC, ABAC, PBAC, ReBAC

**The concept.** Once authenticated, **authorization** decides what you may do. Four models you'll hear:

| Model | Decides access by… | Example | Best when |
|---|---|---|---|
| **RBAC** | **roles** you hold | "Teller role → view accounts" | Stable job functions; most enterprises start here |
| **ABAC** | **attributes** + policy | "clearance ≥ dept AND time = business hours" | Fine-grained, context-aware rules |
| **PBAC** | central **policy** engine (often ABAC-style, e.g. OPA/Rego) | policy-as-code evaluated at runtime | You want policy decoupled from apps |
| **ReBAC** | **relationships** in a graph | "editors of a doc's parent folder" (Google **Zanzibar**) | Social/hierarchical sharing, SaaS |

**Principles that matter more than the model:**
- **Least privilege** — grant the minimum access needed.
- **Separation of Duties (SoD)** — no one person can both *initiate* and *approve* a payment (fraud control).
- **Entitlement creep** — access accumulates as people change roles and old grants are never removed. IGA (§5) exists largely to fight this.

**Tie-in:** RBAC roles are very often just **directory groups** ([note 04](04-ldap-ad-entra.md)) that arrive as **group claims** in a SAML assertion or OIDC token — which is why "logged in but no access" is usually an attribute/group-mapping problem, not an auth problem.

| Attack | Defense |
|---|---|
| **Privilege escalation via over-broad roles** — a coarse role grants far more than the job needs | Least-privilege role design, regular **access reviews** (§5), SoD policies, model sensitive rules in ABAC/PBAC |

**Fintech relevance.** RBAC + **SoD** is the backbone of **SOX** access control; auditors will ask you to prove both. Model it in [Lab 01](../labs/01-keycloak-idp/README.md) (Keycloak realm roles) and, later, an OPA/Rego policy lab.

---

## 4. Privileged Access Management (PAM)

**The concept.** Some accounts are **crown jewels** — domain admins, root, database owners, and **service accounts** running critical apps. If one is compromised, it's game over. **PAM** is the discipline (and tooling) for controlling them.

**What PAM tooling does:**
- **Credential vaulting** — privileged passwords/keys live in a vault, not on sticky notes or in scripts.
- **Rotation** — secrets are changed automatically and frequently (so a leaked one is short-lived).
- **Session recording/monitoring** — privileged sessions are proxied and recorded for audit.
- **Just-in-Time (JIT) access** — no standing admin rights; access is granted for a window, then revoked (shrinks the attack surface).
- **Tiered admin model** — separate identities/workstations for Tier 0 (identity/DC admins) so a phished laptop can't reach the domain's core.

Vendors you'll hear: **CyberArk**, **BeyondTrust**, **Delinea**.

| Attack | Defense |
|---|---|
| **Standing privileged credentials stolen & reused** (pass-the-hash, leaked service-account secret) | Vault + **rotate** + **JIT** (no permanent admin), session recording, tiered admin, MFA on privileged access |

**Fintech relevance.** Protecting admin access to payment and core-banking systems is the **highest-stakes IAM control** in a fintech; PAM maturity is a headline audit and board-level topic. Service-account secrets tie directly to OAuth **client credentials** ([note 03 §7](03-oauth-oidc-deep-dive.md)) and mTLS keys ([note 06](06-tls-https-mtls.md)) — all things a vault should hold.

---

## 5. Identity Governance & Administration (IGA) & SCIM

**The concept.** IAM isn't just *letting people in* — it's governing **who should have what, over time**. IGA manages the identity **lifecycle** and produces the evidence auditors demand.

**The lifecycle: Joiner → Mover → Leaver (JML).**
- **Joiner** — new hire is **provisioned** the right access automatically (birthright roles).
- **Mover** — role change triggers access adjustment (and removal of the old — fighting entitlement creep).
- **Leaver** — departure triggers **deprovisioning** everywhere. **A Leaver who keeps access is a serious audit finding** and a real breach risk.

**Key IGA mechanics:**
- **SCIM** (System for Cross-domain Identity Management) — the standard protocol IdPs use to **auto-provision/deprovision** users into SaaS apps (create/update/`active:false`). "It's a SCIM issue" = provisioning to an app failed.
- **Access requests & approvals** — self-service "request access," routed for approval.
- **Access certifications / reviews (recertification)** — managers periodically confirm their team's access is still appropriate; the core **SOX/PCI evidence** cycle.
- **Role mining** — analyzing who has what to design sensible roles.
- **SoD enforcement & entitlement catalogs** — codified toxic-combination rules and a catalog of grantable access.

Vendors: **SailPoint**, **Saviynt**, **Okta Identity Governance**.

| Attack | Defense |
|---|---|
| **Orphaned / over-provisioned accounts** — ex-employees or stale grants that attackers (or insiders) abuse | Automated **JML** deprovisioning, periodic **access certifications**, SoD rules, reconcile IdP ↔ directory ↔ app regularly |

**Fintech relevance.** This is your likely **bread-and-butter** and the direct evidence engine for **SOX ITGC** and **PCI-DSS Req 7 & 8** (need-to-know + unique IDs/least privilege). Deep compliance mapping: [`08-grc-compliance`](../../08-grc-compliance/README.md) §5.

---

## 6. Zero Trust — the strategy that ties it all together

**The concept.** The old model trusted anything "inside the network." **Zero Trust** rejects that: **"never trust, always verify"** — authenticate and authorize **every** request, from anywhere, continuously, based on identity and context. Reference: NIST **SP 800-207**.

**Core vocabulary:**
- **PDP (Policy Decision Point)** — the brain that decides allow/deny (e.g., Entra Conditional Access engine).
- **PEP (Policy Enforcement Point)** — the gate that enforces the decision (e.g., an app proxy, a service-mesh sidecar).
- **Continuous verification** — trust is re-evaluated during a session (risk changes → re-challenge), not granted once.
- **Micro-segmentation** — small trust zones so a breach can't move laterally.

**The key insight for you:** Zero Trust isn't a product — it's a strategy implemented by mechanisms **you've already learned**:
- **mTLS** between services ([note 06](06-tls-https-mtls.md)) → verify every internal connection.
- **Conditional Access** (Entra, [note 04](04-ldap-ad-entra.md)) → the PDP for user sign-ins (device, location, risk).
- **Phishing-resistant MFA** (§1) → strong identity at the gate.
- **Least privilege + JIT** (§3–4) → minimize what a verified identity can reach.

| Attack | Defense |
|---|---|
| **Lateral movement** — attacker breaches one host, then roams a flat "trusted" internal network | Zero Trust: mutually authenticate every hop (mTLS), micro-segment, least privilege, continuous verification |

**Fintech relevance.** Zero Trust is the strategic direction of nearly every fintech's security program; being able to say *"Conditional Access is our PDP, mTLS is our internal PEP, passkeys are our strong factor"* connects the whole picture — and is exactly the kind of thing to raise with your director ([note 05](05-first-week-questions.md)).

---

## You've now got the whole map

Protocols (notes 02–04, 06) + these foundations (this note) = the full IAM vocabulary a first-month engineer needs. Next moves:
- **Do the labs** so it's muscle memory: [Lab 01 — Keycloak/OIDC + MFA + roles](../labs/01-keycloak-idp/README.md), [Lab 02 — SAML assertion](../labs/02-saml-assertion-anatomy/README.md).
- **Map your real stack** and bring one sharp question to your team ([note 05](05-first-week-questions.md)).

*— Janus 🔐*
