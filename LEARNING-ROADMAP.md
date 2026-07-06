# 🗺️ Learning Roadmap

A suggested path to go from onboarding to genuinely deep, especially in IAM. Adjust pace to your life — this is a guide, not a deadline. Since you're joining FinCo's IAM team, the roadmap front-loads foundations + IAM, then broadens.

---

## Phase 0 — Foundations (Weeks 1–3)
**Goal:** Speak the language. Never feel lost in a security conversation.

- [ ] CIA triad, AAA, threat vs vulnerability vs risk, defense in depth
- [ ] OSI & TCP/IP models, common ports/protocols
- [ ] Basic threat modeling (STRIDE), attack surface thinking
- [ ] Set up your lab: a hypervisor (VirtualBox/VMware) + Kali + a target VM
- **Domain:** `00-foundations`

## Phase 1 — IAM Deep Dive (Weeks 4–10) ⭐ your domain
**Goal:** Be the person on the team who actually understands identity.

- [ ] AuthN vs AuthZ — really understand the difference
- [ ] Credentials, password security, hashing, salting, MFA factors
- [ ] Session management, tokens, JWT internals
- [ ] **OAuth 2.0 & OpenID Connect** — flows, tokens, PKCE (know these cold)
- [ ] **SAML & federation** — SSO, IdP vs SP, assertions
- [ ] **Directory services** — LDAP, Active Directory, Azure AD / Entra ID
- [ ] **RBAC / ABAC / PBAC** — access models
- [ ] **PAM** (Privileged Access Management) — CyberArk/BeyondTrust concepts
- [ ] **IGA** (Identity Governance & Administration) — provisioning, SCIM, access reviews, SoD
- [ ] **Zero Trust** architecture
- **Domain:** `01-identity-access-management`

## Phase 2 — Crypto + AppSec + Network (Weeks 11–18)
**Goal:** Understand the machinery identity rides on.

- [ ] Symmetric vs asymmetric, PKI, certificates, TLS handshake — `04-cryptography`
- [ ] OWASP Top 10, hands-on with DVWA / Juice Shop — `03-application-security`
- [ ] API security, JWT attacks, injection, auth bypass
- [ ] Firewalls, segmentation, VPNs, packet capture with Wireshark — `02-network-security`

## Phase 3 — Cloud + Blue Team (Weeks 19–26)
**Goal:** Defend real systems where fintech actually lives.

- [ ] Cloud IAM (AWS IAM, Azure RBAC), misconfig, least privilege — `05-cloud-security`
- [ ] SIEM concepts, log analysis, detection engineering — `06-security-operations-blue-team`
- [ ] Incident response lifecycle, threat hunting basics
- [ ] MITRE ATT&CK familiarity — `09-threat-intelligence`

## Phase 4 — Offensive + GRC (ongoing)
**Goal:** Round out. Think like an attacker; speak to auditors.

- [ ] Pentest methodology, TryHackMe / HackTheBox paths — `07-offensive-security-red-team`
- [ ] PCI-DSS, SOX, ISO 27001, risk frameworks (critical for fintech) — `08-grc-compliance`

---

## Certification ideas (map to phases)
- **CompTIA Security+** — great foundations cert, aligns with Phase 0–2
- **Okta / Microsoft SC-300 (Identity & Access Administrator)** — directly your job ⭐
- **CyberArk Defender/Sentry** — if your team uses CyberArk for PAM
- **AWS/Azure security certs** — Phase 3
- **CISSP** — later, when you have experience (broad, management-oriented)

## Learning log
Keep a running log below — date, what you learned, what clicked, what's still fuzzy.

| Date | Domain | What I learned / what's still unclear |
|------|--------|----------------------------------------|
| 2026-07-04 | setup | Repo initialized. Starting with foundations. |
| 2026-07-05 | 01-iam | Deep dive with Janus: SAML from scratch (assertion anatomy, bindings, clock skew, XSW), OAuth 2.0 vs OIDC (why OAuth ≠ login, PKCE, ID vs access tokens), LDAP/AD/Entra + federation. Wrote notes 01–05 and built Keycloak (OIDC) + SAML-assertion labs. **Next:** run both labs; map my team's real stack (IdP/directory/IGA/PAM). |
| 2026-07-05 | 01-iam | Added transport security: HTTPS/TLS handshake, PKI/certs, **mTLS** (mutual cert auth) + the k8s service-mesh sidecar pattern (what my manager meant: pods speak HTTP to their sidecar, mesh does mTLS on the wire). Plus foundations round-up: MFA/passkeys, sessions/tokens, RBAC/ABAC, PAM, IGA/SCIM, Zero Trust. Notes 06–07 + interactive guide updated. **Ask at work:** is our mesh mTLS STRICT or PERMISSIVE? |
| 2026-07-05 | 01-iam | Pinned down **SSO** (the *experience* of logging in once vs **federation**, the machinery; engine = a live IdP session) and wrote a full **IAM buzzword glossary** (note 08). Codified **Lefler's Laws** (`LEFLER-LAWS.md`) — the beginner-first doc standard all notes/labs now follow. Interactive guide gained an SSO clarifier + a searchable 70-term glossary. |
| 2026-07-05 | 01-iam + 03-appsec | **PCI-DSS × IAM** (note 09): Req 7 (authZ/least-privilege), Req 8 (authN/MFA/unique-ID), Req 10 (logging) map onto every IAM layer — my daily provisioning/reviews *are* the audit evidence. **IAM vulnerability catalog** (note 10) mapped to OWASP A01/A07 + API BOLA. **OWASP Top 10** note filed in its correct home `03-application-security` (folder rule in action, not IAM). |
| 2026-07-05 | 01-iam | Full deep-dives on **PAM** (note 11 — vault / rotate / record / isolate, JIT & Zero Standing Privilege, service accounts & secrets, tiered admin) and **IGA** (note 12 — JML lifecycle, SCIM provisioning, access reviews/certifications, SoD). **Core IAM curriculum now complete: notes 01–12.** Ask at work: what's our PAM tool + IGA tool, and am I on the access-review / provisioning team? |
