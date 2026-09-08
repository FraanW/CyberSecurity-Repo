# IAM notes — the index

> **What this is.** Every written deep-dive in the IAM domain, grouped into **topic folders** so you can read one subject end to end instead of hopping around a flat list. Start at [`01-foundations/`](01-foundations/) and work down; each folder below is listed in its own reading order.
>
> **Why the numbers look jumbled inside a folder.** Each note keeps the number it was written under (note 02, note 21, note 34 …). That number is the note's permanent name — the notes, the labs, the [roadmap](../../LEARNING-ROADMAP.md) and your own memory all refer to "note 21" — so grouping them into folders **did not renumber them**. Read the folder top to bottom in the order shown here, not in numeric order.

---

## The nine topics

| # | Folder | What lives here | Notes |
|---|---|---|---|
| 01 | [`01-foundations/`](01-foundations/) | The mental model, the map of the field, the vocabulary | 5 |
| 02 | [`02-saml/`](02-saml/) | SAML 2.0 — the protocol you debug most | 5 |
| 03 | [`03-oauth-oidc/`](03-oauth-oidc/) | OAuth 2.0 & OpenID Connect — the modern stack | 4 |
| 04 | [`04-directories-and-authn-protocols/`](04-directories-and-authn-protocols/) | Where identities are stored, and how users prove who they are | 3 |
| 05 | [`05-pam-and-governance/`](05-pam-and-governance/) | Privileged access + the identity lifecycle | 2 |
| 06 | [`06-platforms-and-gateways/`](06-platforms-and-gateways/) | The products your team actually runs | 2 |
| 07 | [`07-security-and-compliance/`](07-security-and-compliance/) | How identity gets attacked, and what the auditor asks | 2 |
| 08 | [`08-infrastructure-and-platform/`](08-infrastructure-and-platform/) | The plumbing under the Ping stack — TLS, Docker, Kubernetes, cloud | 8 |
| 09 | [`09-presentations-and-kt/`](09-presentations-and-kt/) | Teaching the room — decks, scripts, analogies | 3 |

**Suggested path if you're starting cold:** 01 → 02 → 03 → 04 → 06 → 05 → 07 → 08 → 09.
Folder 08 is a support topic — dip into it when a Kubernetes or TLS word blocks you, not as a front-to-back read.

---

## 01-foundations — start here

The map before the territory. Read all five before any deep dive.

1. [`01-iam-protocol-landscape.md`](01-foundations/01-iam-protocol-landscape.md) — authN vs authZ, federation, the vendor zoo, one real login end to end.
2. [`17-iam-domains-map.md`](01-foundations/17-iam-domains-map.md) — the eight domains of IAM: what each covers and by what mechanism.
3. [`08-sso-and-glossary.md`](01-foundations/08-sso-and-glossary.md) — what SSO *really* means (vs federation) + every IAM term in one line. Keep it open.
4. [`07-iam-foundations.md`](01-foundations/07-iam-foundations.md) — MFA, sessions & tokens, RBAC/ABAC, and a first look at PAM, IGA and Zero Trust.
5. [`05-first-week-questions.md`](01-foundations/05-first-week-questions.md) — turns all of the above into questions to ask your team, plus the incident-channel decoder.

## 02-saml — the protocol you'll debug most

1. [`02-saml-deep-dive.md`](02-saml/02-saml-deep-dive.md) — assertion anatomy, bindings, clock skew, attacks, and the 60-second debugging checklist.
2. [`13-saml-mastery-session2.md`](02-saml/13-saml-mastery-session2.md) — SP-init vs IdP-init, speed-reading an assertion, EntityID's three homes, signing vs encryption certs.
3. [`16-saml-bindings-and-certificates.md`](02-saml/16-saml-bindings-and-certificates.md) — how messages physically travel (Redirect/POST/Artifact/SOAP) + a cert-rollover playbook.
4. [`34-saml-invalid-signature-rca.md`](02-saml/34-saml-invalid-signature-rca.md) 🔍 — the failure you'll hit most on a new SP connection, derived from first principles and root-caused.
5. [`14-saml-question-bank.md`](02-saml/14-saml-question-bank.md) — self-test, easy → very hard, with model answers. Use it last.

Interactive companion: [`../saml-complete-guide.html`](../saml-complete-guide.html).

## 03-oauth-oidc — the modern stack

1. [`03-oauth-oidc-deep-dive.md`](03-oauth-oidc/03-oauth-oidc-deep-dive.md) — why **OAuth ≠ login**, Authorization Code + PKCE, ID vs access tokens, JWT attacks.
2. [`19-oauth2-in-practice.md`](03-oauth-oidc/19-oauth2-in-practice.md) — one login, every byte: the full flow wire-by-wire against the Keycloak lab.
3. [`21-oauth2-complete-reference.md`](03-oauth-oidc/21-oauth2-complete-reference.md) — the look-it-up card: roles, endpoints, tokens, 15 attacks with defenses, RFC 9700 hardening.
4. [`22-oauth2-grant-types-and-scenarios.md`](03-oauth-oidc/22-oauth2-grant-types-and-scenarios.md) — every grant walked step by step, why Implicit and ROPC are dead, and OAuth 2.1.

## 04-directories-and-authn-protocols — where identities live, how users prove them

1. [`04-ldap-ad-entra.md`](04-directories-and-authn-protocols/04-ldap-ad-entra.md) — the directory layer: DIT/DN, and why Entra ≠ "AD in the cloud".
2. [`15-kerberos-explained.md`](04-directories-and-authn-protocols/15-kerberos-explained.md) — how legacy systems do passwordless: TGTs, service tickets, keytabs, clock skew.
3. [`29-fido2-webauthn-passkeys-complete-reference.md`](04-directories-and-authn-protocols/29-fido2-webauthn-passkeys-complete-reference.md) — phishing-resistant authentication, from the key pair up.

## 05-pam-and-governance — privileged access and the identity lifecycle

1. [`11-pam-deep-dive.md`](05-pam-and-governance/11-pam-deep-dive.md) — vaulting, rotation, session recording/isolation, JIT & Zero Standing Privilege, service accounts.
2. [`12-iga-deep-dive.md`](05-pam-and-governance/12-iga-deep-dive.md) — JML lifecycle, SCIM provisioning, access reviews/certifications, SoD. **Likely your day job.**

## 06-platforms-and-gateways — the boxes your team runs

1. [`18-pingfederate-explained.md`](06-platforms-and-gateways/18-pingfederate-explained.md) — SP vs IdP connections, adapters, policy trees, attribute contracts, the audit.log playbook.
2. [`20-reverse-proxies-in-iam.md`](06-platforms-and-gateways/20-reverse-proxies-in-iam.md) — the gate that logs users in for your apps: PingAccess, nginx forward-auth, Envoy `ext_authz`.

## 07-security-and-compliance — attack surface and audit

1. [`10-iam-vulnerabilities.md`](07-security-and-compliance/10-iam-vulnerabilities.md) — the identity attack surface mapped to OWASP A01/A07 and API **BOLA**, every vuln paired with its defense.
2. [`09-pci-dss-and-iam.md`](07-security-and-compliance/09-pci-dss-and-iam.md) — how PCI Req 7/8/10 land on every IAM layer, and why your daily work *is* the audit evidence.

## 08-infrastructure-and-platform — the plumbing under the stack

Support material. Read a note when its vocabulary is blocking you.

1. [`06-tls-https-mtls.md`](08-infrastructure-and-platform/06-tls-https-mtls.md) — the padlock, PKI/certs, and **mTLS** (machine auth).
2. [`26-docker-complete-reference.md`](08-infrastructure-and-platform/26-docker-complete-reference.md) — containers from scratch to advanced.
3. [`27-kubernetes-complete-reference.md`](08-infrastructure-and-platform/27-kubernetes-complete-reference.md) — Kubernetes from scratch to advanced.
4. [`31-kubernetes-services-and-ingress-deep-dive.md`](08-infrastructure-and-platform/31-kubernetes-services-and-ingress-deep-dive.md) — how traffic actually reaches a pod.
5. [`30-subnets-and-k8s-networking-for-ping.md`](08-infrastructure-and-platform/30-subnets-and-k8s-networking-for-ping.md) — subnets and cluster networking, framed for the Ping stack.
6. [`32-cloud-to-container-hierarchy-and-reachability.md`](08-infrastructure-and-platform/32-cloud-to-container-hierarchy-and-reachability.md) — subscription → container: how it nests, and how to reach each layer.
7. [`33-istio-service-mesh-explained.md`](08-infrastructure-and-platform/33-istio-service-mesh-explained.md) — the two-container pod, and where mesh mTLS fits.
8. [`28-docker-kubernetes-question-bank.md`](08-infrastructure-and-platform/28-docker-kubernetes-question-bank.md) — self-test, warm-up → senior platform-security.

## 09-presentations-and-kt — teaching the room

1. [`23-reverse-kt-presentation-guide.md`](09-presentations-and-kt/23-reverse-kt-presentation-guide.md) 🎤 — the 33-slide deck: slide content, talk track, Mermaid diagrams, demo cue cards, Q&A prep. Pairs with **Lab 03**.
2. [`25-reverse-kt-presentation-script.md`](09-presentations-and-kt/25-reverse-kt-presentation-script.md) — the spoken flow, start to finish.
3. [`24-analogies-and-real-world-narrative.md`](09-presentations-and-kt/24-analogies-and-real-world-narrative.md) — the analogies and fresh examples the talk leans on.

---

## Where a note moved to

Every note kept its filename; only its folder changed. If an old link `notes/<file>` breaks, find the file here:

| Note | Now in |
|---|---|
| `01-iam-protocol-landscape.md` | `01-foundations/` |
| `02-saml-deep-dive.md` | `02-saml/` |
| `03-oauth-oidc-deep-dive.md` | `03-oauth-oidc/` |
| `04-ldap-ad-entra.md` | `04-directories-and-authn-protocols/` |
| `05-first-week-questions.md` | `01-foundations/` |
| `06-tls-https-mtls.md` | `08-infrastructure-and-platform/` |
| `07-iam-foundations.md` | `01-foundations/` |
| `08-sso-and-glossary.md` | `01-foundations/` |
| `09-pci-dss-and-iam.md` | `07-security-and-compliance/` |
| `10-iam-vulnerabilities.md` | `07-security-and-compliance/` |
| `11-pam-deep-dive.md` | `05-pam-and-governance/` |
| `12-iga-deep-dive.md` | `05-pam-and-governance/` |
| `13-saml-mastery-session2.md` | `02-saml/` |
| `14-saml-question-bank.md` | `02-saml/` |
| `15-kerberos-explained.md` | `04-directories-and-authn-protocols/` |
| `16-saml-bindings-and-certificates.md` | `02-saml/` |
| `17-iam-domains-map.md` | `01-foundations/` |
| `18-pingfederate-explained.md` | `06-platforms-and-gateways/` |
| `19-oauth2-in-practice.md` | `03-oauth-oidc/` |
| `20-reverse-proxies-in-iam.md` | `06-platforms-and-gateways/` |
| `21-oauth2-complete-reference.md` | `03-oauth-oidc/` |
| `22-oauth2-grant-types-and-scenarios.md` | `03-oauth-oidc/` |
| `23-reverse-kt-presentation-guide.md` | `09-presentations-and-kt/` |
| `24-analogies-and-real-world-narrative.md` | `09-presentations-and-kt/` |
| `25-reverse-kt-presentation-script.md` | `09-presentations-and-kt/` |
| `26-docker-complete-reference.md` | `08-infrastructure-and-platform/` |
| `27-kubernetes-complete-reference.md` | `08-infrastructure-and-platform/` |
| `28-docker-kubernetes-question-bank.md` | `08-infrastructure-and-platform/` |
| `29-fido2-webauthn-passkeys-complete-reference.md` | `04-directories-and-authn-protocols/` |
| `30-subnets-and-k8s-networking-for-ping.md` | `08-infrastructure-and-platform/` |
| `31-kubernetes-services-and-ingress-deep-dive.md` | `08-infrastructure-and-platform/` |
| `32-cloud-to-container-hierarchy-and-reachability.md` | `08-infrastructure-and-platform/` |
| `33-istio-service-mesh-explained.md` | `08-infrastructure-and-platform/` |
| `34-saml-invalid-signature-rca.md` | `02-saml/` |

---

## Adding a new note

1. Pick the folder whose topic it belongs to. If nothing fits, that's a signal you may need a new folder — say so rather than dumping it in `01-foundations/`.
2. Name it `<next global number>-<slug>.md` — keep counting up from **34**, repo-wide, not per folder. The number is the note's permanent ID.
3. Add it to its folder's list above, in reading order, with a one-line description.
4. Write it to **[Lefler's Laws](../../LEFLER-LAWS.md)** and run the 20-second checklist before saving.

**What you learned:** the IAM notes are grouped by topic, but numbered globally — folders give you a reading order, numbers give you a stable name.
**Next:** open [`01-foundations/01-iam-protocol-landscape.md`](01-foundations/01-iam-protocol-landscape.md), or jump to the domain [README](../README.md) for the notes-plus-labs view.

*— Janus 🔐*
