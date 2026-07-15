# 01 · Identity & Access Management (IAM) ⭐

> **This is your domain at FinCo.** Go deeper here than anywhere else. Identity is the new perimeter — in a fintech, the difference between a locked vault and a breach is almost always an identity control that did or didn't hold.

Ask **Janus** (your IAM agent) for deep dives, **Lefler** to build the labs, and **Loki**/**Heimdall** for the attack/defend view.

---

## 📚 Deep-dive notes — start here

Full written walkthroughs now live in [`notes/`](notes/), in learning order:

1. [**The IAM protocol landscape**](notes/01-iam-protocol-landscape.md) — the map: authN vs authZ, federation, the vendor zoo (Entra/Okta/Ping), how a real login flows end to end.
2. [**The eight domains of IAM**](notes/17-iam-domains-map.md) — the full map of the field: Identity Mgmt, AuthN, AuthZ, PAM, IGA, Federation/SSO, Directory Services, **CIAM** — what each covers, achieves, and by what mechanisms.
3. [**SSO explained + the buzzword glossary**](notes/08-sso-and-glossary.md) — your dictionary: what SSO *really* means (vs federation) + every IAM term in one line. Skim early, keep open.
4. [**SAML 2.0 deep dive**](notes/02-saml-deep-dive.md) — the protocol you'll debug most; assertion anatomy, bindings, clock skew, attacks + a 60-second debugging checklist.
   - ↳ [**SAML mastery — session 2**](notes/13-saml-mastery-session2.md) — a senior's whiteboard deep dive: SP-init vs IdP-init, speed-reading assertions, EntityID's three homes, certificates (sign vs encrypt), and exactly what's encrypted vs merely encoded.
   - ↳ [**SAML bindings & the two certificates**](notes/16-saml-bindings-and-certificates.md) — how the messages physically travel (Redirect/POST/Artifact/SOAP), where each is configured, and the signing-vs-encryption cert deep-cut with a rollover/outage playbook.
   - ↳ [**SAML question bank**](notes/14-saml-question-bank.md) — a self-test from *easy → very hard*, tuned for a Ping-expert QnA, with model answers in spoilers.
   - ↳ [**SAML — the complete visual guide**](saml-complete-guide.html) — an interactive one-page walkthrough of the whole protocol + the question bank (open in a browser).
5. [**OAuth 2.0 & OIDC deep dive**](notes/03-oauth-oidc-deep-dive.md) — the modern stack; why **OAuth ≠ login**, Authorization Code + PKCE, ID vs access tokens, JWT attacks.
   - ↳ [**OAuth 2.0 in practice**](notes/19-oauth2-in-practice.md) — one login, every byte explained: the full Code+PKCE flow wire-by-wire, decoded tokens, refresh rotation, client-credentials vs mTLS, + curl commands against the Keycloak lab.
   - ↳ [**OAuth 2.0 + OIDC — the complete reference card**](notes/21-oauth2-complete-reference.md) — the look-it-up note: all roles, endpoints, tokens, grant types, the full flow in numbered pointers, 15 attacks paired with defenses, attacker motivations, and the RFC 9700 hardening checklist.
   - ↳ [**OAuth 2.0 grant types & scenarios**](notes/22-oauth2-grant-types-and-scenarios.md) — the behind-the-screen playbook: a decision tree for picking a grant, **every** grant type walked step-by-step (Auth Code+PKCE, Client Credentials, Device Code, Refresh, + JWT-Bearer/Token-Exchange/CIBA), why Implicit & ROPC are dead, a deeper OIDC section, a real-world scenario gallery, and a brief on **OAuth 2.1**.
6. [**PingFederate — a field guide**](notes/18-pingfederate-explained.md) — the federation hub your team runs: SP vs IdP connections, adapters, policy trees, attribute contracts, Access Token Managers, and the audit.log debugging playbook.
   - ↳ [**Reverse proxies in IAM**](notes/20-reverse-proxies-in-iam.md) — the gate that does the logging-in for your apps: forward vs reverse, the authenticating-proxy/PEP pattern, and where it lives in your stack (PingAccess, nginx-ingress forward-auth, Envoy `ext_authz`).
7. [**LDAP, Active Directory & Entra ID**](notes/04-ldap-ad-entra.md) — the directory layer; DIT/DN, Kerberos, and why Entra ≠ "AD in the cloud".
   - ↳ [**Kerberos explained**](notes/15-kerberos-explained.md) — how legacy systems do "passwordless" auth: TGT/service tickets step by step, why the password never crosses the wire, keytabs & clock skew, attacks + defenses.
8. [**HTTPS, TLS & mTLS**](notes/06-tls-https-mtls.md) — transport security from scratch: the padlock, PKI/certs, and **mTLS** (machine auth) incl. the Kubernetes service-mesh pattern.
9. [**IAM foundations round-up**](notes/07-iam-foundations.md) — MFA & passkeys, sessions/tokens, authZ models (RBAC/ABAC), PAM, IGA/SCIM, Zero Trust.
10. [**PAM — Privileged Access Management**](notes/11-pam-deep-dive.md) — deep dive: vaulting, rotation, session recording/isolation, JIT & Zero Standing Privilege, service accounts & secrets, tiered admin.
11. [**IGA — Identity Governance & Administration**](notes/12-iga-deep-dive.md) — deep dive: JML lifecycle, SCIM provisioning, access reviews/certifications, SoD. **Likely your day job.**
12. [**PCI-DSS × IAM**](notes/09-pci-dss-and-iam.md) — where compliance meets identity: how PCI Req 7/8/10 map onto every IAM layer, and why your daily work *is* the audit evidence.
13. [**IAM vulnerabilities**](notes/10-iam-vulnerabilities.md) — the identity attack surface, mapped to OWASP (A01/A07) + the API **BOLA** risk; every vuln paired with its defense.
14. [**First-week questions & incident-channel decoder**](notes/05-first-week-questions.md) — turn all of the above into sharp questions for your manager, seniors, lead, and director (+ AI-in-tickets guardrails).
15. [**Reverse-KT presentation guide**](notes/23-reverse-kt-presentation-guide.md) 🎤 — a **33-slide** teach-the-room deck for explaining IAM + SAML + OAuth/OIDC end to end: slide content **and** first-person talk track for each slide, **Mermaid flow diagrams** for every protocol (SAML SP/IdP-init, all 4 OAuth grants, OIDC), the full **PingFederate** mapping, live-demo cue cards, and a Q&A prep sheet. Pairs with **Lab 03**.

> ✍️ Every note & lab here is written to **[Lefler's Laws](../LEFLER-LAWS.md)** — the repo's beginner-first documentation standard.

**Hands-on labs** in [`labs/`](labs/):
- [**Lab 01 — Keycloak as your own IdP (OIDC end to end)**](labs/01-keycloak-idp/README.md) — run a full login by hand and mint real tokens.
- [**Lab 02 — SAML assertion anatomy**](labs/02-saml-assertion-anatomy/README.md) — decode a real assertion and run the debugging checklist.
- [**Lab 03 — Reverse-KT demo stack (SAML + OAuth 2.0)**](labs/03-kt-demo-saml-oauth/README.md) 🎤 — one-command Keycloak that's both a **SAML IdP** and an **OAuth/OIDC Authorization Server**, plus a browser app: demo **SAML SSO** and **all 4 OAuth grant types** live, captured with **SAML-tracer** + DevTools. Built to present alongside [note 23](notes/23-reverse-kt-presentation-guide.md).

---

## The mental model

IAM answers two questions for every request to every system:
1. **Who are you?** → **Authentication (AuthN)**
2. **What are you allowed to do?** → **Authorization (AuthZ)**

Everything in this domain is a refinement of those two questions — plus the lifecycle around them (how identities are created, governed, and destroyed) and the privileged edge cases (admins, service accounts, secrets).

---

## Core concepts (learn in this order)

### 1. Foundations of identity
- Identity vs. account vs. credential vs. entitlement
- AuthN vs AuthZ vs Accounting (the **AAA** model)
- Subject, principal, claim, assertion
- Identity lifecycle: **Joiner → Mover → Leaver (JML)**

### 2. Authentication
- Something you know / have / are (factors) + location/behavior
- Password security: hashing vs encrypting, salting, peppering, bcrypt/scrypt/Argon2, credential stuffing
- **MFA**: TOTP/HOTP, push, SMS (and why it's weak), **FIDO2 / WebAuthn / passkeys**, phishing-resistant MFA
- MFA fatigue / push bombing attacks

### 3. Sessions & tokens
- Cookies, session IDs, session fixation/hijacking
- **JWT** internals: header, payload, signature; `alg: none` and algorithm-confusion attacks; expiry/`nbf`/`aud`
- Opaque vs. self-contained tokens; access vs. refresh vs. ID tokens

### 4. Federation & SSO — the big three protocols
- **OAuth 2.0** (authorization): grant types (authorization code + **PKCE**, client credentials, device code), scopes, redirect_uri attacks. RFC 6749.
- **OpenID Connect** (authentication, built on OAuth): ID token, claims, `/userinfo`, discovery. Know how OIDC ≠ OAuth.
- **SAML 2.0**: IdP vs SP, assertions, SSO/SLO, signing & the classic assertion-tampering / XML-signature-wrapping attacks.

### 5. Directory services
- **LDAP**: DIT, DN/RDN, bind operations
- **Active Directory**: Kerberos (TGT/TGS), NTLM, GPOs; attacks: Kerberoasting, pass-the-hash, golden ticket (defensive awareness)
- **Azure AD / Microsoft Entra ID**: cloud identity, conditional access

### 6. Authorization models
- **RBAC** (roles), **ABAC** (attributes/policy), **PBAC**, **ReBAC** (relationship, e.g., Google Zanzibar)
- Principle of **least privilege**, **separation of duties (SoD)**, entitlement creep

### 7. Privileged Access Management (PAM)
- Privileged accounts & why they're the crown jewels
- Credential vaulting, rotation, session recording/monitoring, **just-in-time (JIT)** access
- Concepts from CyberArk / BeyondTrust / Delinea

### 8. Identity Governance & Administration (IGA)
- Provisioning/deprovisioning, **SCIM** (System for Cross-domain Identity Management)
- Access requests & approvals, **access certifications/reviews**, role mining
- SoD policy enforcement, entitlement catalogs
- Tools: SailPoint, Saviynt, Okta Identity Governance

### 9. Zero Trust
- "Never trust, always verify"; NIST **SP 800-207**
- Policy Decision Point (PDP) / Policy Enforcement Point (PEP), continuous verification, micro-segmentation

---

## Reading list

**Start here**
- NIST **SP 800-63** Digital Identity Guidelines (800-63A/B/C) — the canonical reference
- OWASP **Authentication** & **Session Management** Cheat Sheets
- Okta's free "Identity 101" articles & auth0 blog (excellent OAuth/OIDC explainers)

**Protocols (primary sources)**
- RFC 6749 (OAuth 2.0), RFC 7519 (JWT), RFC 6750 (Bearer tokens), OpenID Connect Core spec
- OAuth 2.0 Security Best Current Practice (RFC 9700)
- OASIS SAML 2.0 Technical Overview

**Deeper**
- NIST **SP 800-207** Zero Trust Architecture
- "OAuth 2 in Action" (Richer & Sanso) — the best OAuth book
- Microsoft Entra & Active Directory security documentation

**Fintech context**
- PCI-DSS Requirements **7 & 8** (access control & authentication) — see `08-grc-compliance`
- RBI cybersecurity framework (India), FFIEC authentication guidance

---

## Labs (ask Lefler to set these up)

| # | Lab | You'll learn |
|---|-----|--------------|
| 1 | ✅ [**Stand up Keycloak** (Docker) as an IdP; register an OIDC client app](labs/01-keycloak-idp/README.md) | Real OAuth/OIDC flows end to end |
| 2 | Configure **SSO** between two apps via Keycloak (OIDC + SAML) | Federation, IdP/SP roles |
| 3 | Decode & tamper a **JWT**; exploit `alg:none` on a lab app; then fix it | Token internals & attacks |
| 4 | Walk an **OAuth authorization code + PKCE** flow with Burp intercepting | Every hop, every parameter |
| 5 | Spin up a small **LDAP / Samba AD** and query it; explore users/groups | Directory structure |
| 6 | Model **RBAC vs ABAC** for a mock banking app; write policies (e.g., OPA/Rego) | Access modeling in practice |
| 7 | Configure **MFA** (TOTP) and then break weak MFA; try WebAuthn/passkey | Strong vs weak authentication |
| 8 | Set up an **access review / certification** simulation; enforce a SoD rule | IGA governance mechanics |
| 9 | **PAM concepts**: vault a credential, rotate it, record a session (open-source PAM) | Privileged access hygiene |

Each lab lives in `labs/NN-name/` with its own writeup. Purple-team: after each attack lab, ask Heimdall what it would detect.

---

## How this maps to your FinCo job
- **Provisioning/deprovisioning & access reviews** → likely your bread-and-butter, and directly satisfy **SOX ITGC** and **PCI-DSS Req. 7/8**.
- **PAM** → protecting admin access to payment systems.
- **Federation/SSO** → letting employees and partners access many apps with one strong identity.
- **Zero Trust** → the strategic direction most fintechs are moving toward.

Ask Janus early: *"What's my team's stack — Okta, Ping, ForgeRock, SailPoint, CyberArk, Entra?"* — then tailor every lab to it.
