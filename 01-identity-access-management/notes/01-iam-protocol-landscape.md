# The IAM Protocol Landscape — your day-1 map

> **Janus's orientation note.** Read this first. It decodes every word you heard in your first week and shows how the pieces fit. The deep dives ([SAML](02-saml-deep-dive.md), [OAuth/OIDC](03-oauth-oidc-deep-dive.md), [LDAP/AD/Entra](04-ldap-ad-entra.md)) drill into each piece. The [first-week questions](05-first-week-questions.md) note turns this into things you can *ask* your team.

---

## 1. The only mental model you need

Every IAM system on Earth is answering **two questions** about a request:

1. **Who are you?** → **Authentication (AuthN)** — proving identity.
2. **What are you allowed to do?** → **Authorization (AuthZ)** — granting access.

Add a third that fintech auditors care about deeply:

3. **What did you do?** → **Accounting / Audit** — logging it.

That's the **AAA** model. Keep it in your head. Whenever someone says a protocol name, your first reflex should be: *"Is this thing about authN, authZ, or the directory that backs both?"* Half of IAM confusion is people mixing up an authN protocol (SAML, OIDC) with an authZ protocol (OAuth) with a directory (LDAP/AD).

---

## 2. The vocabulary decoder — the exact words you heard

| Word you heard | What it actually is | authN / authZ / directory / product |
|---|---|---|
| **SAML** | A 2005-era XML protocol for browser SSO between companies | authN (federation) |
| **OIDC** (OpenID Connect) | The modern JSON/JWT protocol for login; built on top of OAuth | authN (federation) |
| **OAuth 2.0** | Protocol for *delegated authorization* — giving an app limited access to an API on your behalf | authZ (delegation) |
| **HTTPS / TLS** | HTTP inside an encrypted, server-authenticated tunnel (the browser padlock) | transport security |
| **mTLS** (mutual TLS) | TLS where *both* sides present certificates — machine-to-machine authN; the cert *is* the identity | authN (machines) |
| **LDAP** | A protocol + directory model for storing users/groups (the "phone book") | directory |
| **Active Directory (AD)** | Microsoft's on-prem directory; speaks LDAP + Kerberos | directory (product) |
| **MS Entra ID** | Microsoft's *cloud* identity platform (the artist formerly known as Azure AD) | directory + IdP (product) |
| **Okta** | A cloud **Identity Provider (IdP)** — a SaaS that does SSO/MFA/lifecycle for a company | IdP (product) |
| **Federation** | Two orgs/domains agreeing to *trust each other's* identity assertions | the *pattern* SAML/OIDC implement |
| **SSO** (Single Sign-On) | Log in once, reach many apps — the *user-facing result* of federation | outcome |
| **IdP / SP** | Identity Provider (vouches for you) / Service Provider (the app trusting the vouch) | the two roles in federation |

**The one-sentence version:** *Okta and Entra ID are **IdPs** (products). SAML and OIDC are the **languages** those IdPs speak to prove who you are to apps. OAuth is a related language for granting apps API access. LDAP/AD is the **database of users** underneath it all.*

---

## 3. Federation — the concept behind SAML *and* OIDC

This is the single most important idea in your job. Get the analogy and everything clicks.

**The passport analogy:**
- You want to enter a foreign country (an **app** / Service Provider).
- The border agent doesn't investigate your birth certificate, school records, etc. Instead they trust your **passport**.
- Your passport was issued by your home country's **passport office** (the **Identity Provider**).
- Countries **agreed in advance** to trust each other's passports — they exchanged the security features (holograms, cryptographic keys) so a border agent can *verify* a passport is genuine without calling your home country.

Map it to IAM:

| Passport world | IAM world |
|---|---|
| Passport office | **Identity Provider (IdP)** — Okta, Entra ID, Keycloak, Ping |
| Foreign border / country | **Service Provider (SP)** — Salesforce, Workday, your internal apps |
| The passport (stamped, hologram) | The **assertion** (SAML) or **token** (OIDC) — a signed statement "this is Farhaan" |
| Countries agreeing to trust passports | **Establishing federation** — exchanging **metadata** and **signing certificates** up front |
| Verifying the hologram | Verifying the **digital signature** on the assertion |

**Why federation exists:** without it, every app would need its own copy of your password. That's a security nightmare (100 apps = 100 places your password can leak) and an ops nightmare (change jobs → someone must disable 100 accounts). Federation means **one strong identity at the IdP**, and every app trusts it. Disable the person at the IdP → they lose access everywhere at once. *That last sentence is why federation is a security control, not just convenience — and why your Joiner/Mover/Leaver work matters.*

**The critical mental note:** the SP never sees the user's password. The user authenticates to the **IdP only**; the SP receives a **signed assertion**. The trust is anchored in cryptography (the IdP's signing certificate), established during setup.

---

## 4. The protocol family tree — who does what, and when

```
                    "Prove who someone is / grant access across trust boundaries"
                                          |
        ┌─────────────────────────────────┼──────────────────────────────┐
        |                                 |                              |
   AUTHENTICATION                    AUTHORIZATION                   DIRECTORY
   (who are you?)                    (what can an app do?)          (where users live)
        |                                 |                              |
   ┌────┴─────┐                    ┌──────┴──────┐              ┌────────┼────────┐
 SAML 2.0   OIDC                 OAuth 2.0    (OIDC reuses    LDAP      AD      Entra ID
 (XML,      (JSON/JWT,           (delegated    OAuth's         (proto/   (Kerberos (cloud
  2005)      2014, on OAuth)      API access)   machinery)     model)    +LDAP)    directory)
```

**Which protocol when** — a decision guide you can actually use in a design discussion:

| Situation | Protocol | Why |
|---|---|---|
| Log a user into a **legacy / enterprise SaaS** (Workday, older SAP, many B2B apps) | **SAML 2.0** | It's what enterprise apps standardized on 2005–2015. Still everywhere. |
| Log a user into a **modern web/mobile/SPA app** | **OIDC** | JSON/JWT, mobile-friendly, simpler than SAML. |
| Let a **web app call an API on the user's behalf** ("allow this app to read your calendar") | **OAuth 2.0** | It's the delegation protocol — it issues *access tokens* for APIs. |
| **Service-to-service** / machine-to-machine API auth | **OAuth 2.0 client credentials** | No human in the loop; app authenticates as itself. |
| **Encrypt + mutually authenticate** service traffic on the wire (e.g. k8s pods) | **mTLS** | Both services prove identity with certificates. Often automatic via a service mesh. See [note 06](06-tls-https-mtls.md). |
| **Look up / store** users, groups, org structure | **LDAP** (often against **AD** or **Entra**) | It's the directory query protocol. |
| Windows workstation → file server / internal app on a domain | **Kerberos** (inside AD) | Fast on-prem ticket-based auth. |
| **Auto-create/disable accounts** in a SaaS when someone joins/leaves | **SCIM** | The provisioning protocol IdPs use to push identities into apps. |

> **The trap to avoid:** people say "we use OAuth to log in." Strictly, **OAuth is authorization, not authentication** — using raw OAuth for login is the mistake OIDC was invented to fix. When someone means "login," the correct modern answer is **OIDC** (which *uses* OAuth underneath). This distinction will make you sound like you actually get it. See [the OAuth/OIDC note](03-oauth-oidc-deep-dive.md).

---

## 5. The vendor landscape — what each product actually *is*

Fintech IAM shops are a zoo of vendors. Here's the map so the names stop being noise:

**Identity Providers / Access Management (the "front door" — authN, SSO, MFA):**
- **Microsoft Entra ID** — cloud IdP + directory; near-universal because it ships with Microsoft 365. Does OIDC, SAML, SCIM. Its **Conditional Access** engine is where Zero Trust policy lives.
- **Okta** — pure-play cloud IdP; strong at connecting *many* apps; big in companies that aren't all-Microsoft.
- **Ping Identity**, **ForgeRock** (now part of Ping), **CA/Broadcom** — enterprise IdP/federation, common in banks with heavy legacy.
- **Keycloak** — the leading *open-source* IdP. **This is what your labs use** because it speaks SAML + OIDC + LDAP federation and you can run it free. It's an excellent stand-in to learn how Okta/Entra work under the hood.

**Directories (the "source of truth" — where accounts live):**
- **Active Directory (AD)** — on-prem, Kerberos + LDAP. Still the backbone of most enterprises.
- **Entra ID** — the cloud directory; usually *synced from* on-prem AD (via Entra Connect).
- **OpenLDAP** — open-source LDAP directory (used in labs).

**Identity Governance & Administration (IGA) (the "who-should-have-what" — lifecycle, reviews, SoD):**
- **SailPoint**, **Saviynt**, **Okta Identity Governance** — access requests, **access certifications/reviews**, provisioning, Separation-of-Duties enforcement. In a fintech this is your **SOX / PCI-DSS** audit machine.

**Privileged Access Management (PAM) (the "crown-jewel admin access"):**
- **CyberArk**, **BeyondTrust**, **Delinea** — vault admin credentials, rotate them, record privileged sessions, grant **just-in-time** access.

> **Ask early (see [note 05](05-first-week-questions.md)):** *"What's our stack across these four buckets — IdP, directory, IGA, PAM?"* The answer (e.g., "Entra + AD for identity, SailPoint for governance, CyberArk for PAM") tells you exactly what to go deep on.

---

## 6. How a real enterprise login actually flows

Put it together. A typical fintech employee opening Salesforce:

```
 1. Farhaan opens Salesforce (the SP / app).
 2. Salesforce: "I don't know you — go authenticate at our IdP." → redirects browser to Okta/Entra (SAML or OIDC).
 3. The IdP checks: is there a live session? If not, prompt for password + MFA.
       └─ The IdP validates the password against the DIRECTORY (AD/Entra ID via LDAP/native).
       └─ Conditional Access / policy engine checks device, location, risk (Zero Trust).
 4. IdP mints a SIGNED assertion/token: "This is Farhaan, authenticated at 10:03, MFA satisfied."
 5. Browser carries that assertion back to Salesforce.
 6. Salesforce verifies the SIGNATURE (using the IdP cert exchanged during federation setup),
    reads the attributes (email, groups), and creates a local session. Farhaan is in.
 7. Farhaan opens Workday next → it also trusts the IdP → the IdP already has a live session →
    no password prompt. THAT is SSO.
```

Every incident/ticket you'll touch is a break somewhere in this chain: expired certificate (step 6 fails), clock skew (assertion "not yet valid"), wrong attribute mapping (user logs in but has no access), MFA misfire (step 3), stale directory data (step 3 wrong groups), or a Leaver not deprovisioned (step 3 should have failed but didn't).

---

## 7. Where identity meets your fintech job

- **Joiner/Mover/Leaver (JML)** + **access reviews** → directly satisfy **SOX ITGC** and **PCI-DSS Requirement 7 & 8**. This is likely your bread-and-butter.
- **Federation/SSO** → fewer passwords = smaller attack surface for a company that moves money.
- **PAM** → protecting admin access to payment systems is the highest-stakes IAM control in fintech.
- **Zero Trust** → the strategic direction; the IdP's policy engine (Entra Conditional Access) is where it's implemented.

---

## 8. Your learning path through these notes

1. **This note** — the map. ✅
2. **[SAML deep dive](02-saml-deep-dive.md)** — the headline protocol; the one you'll debug most in tickets.
3. **[OAuth 2.0 & OIDC deep dive](03-oauth-oidc-deep-dive.md)** — the modern stack; know how OIDC ≠ OAuth.
4. **[LDAP, Active Directory & Entra ID](04-ldap-ad-entra.md)** — the directory underneath everything.
5. **[HTTPS, TLS & mTLS](06-tls-https-mtls.md)** — transport security; how the padlock works, and what mTLS means for machine-to-machine auth (incl. the Kubernetes service-mesh pattern).
6. **[IAM foundations round-up](07-iam-foundations.md)** — the remaining building blocks: MFA & passkeys, sessions/tokens, authZ models (RBAC/ABAC), PAM, IGA/SCIM, Zero Trust.
7. **[First-week questions & the incident-channel decoder](05-first-week-questions.md)** — turn knowledge into sharp questions.

Then **do the labs** (`../labs/`):
- **[Lab 01 — Keycloak as your own IdP (OIDC end-to-end)](../labs/01-keycloak-idp/README.md)** — run every parameter of a real login yourself.
- **[Lab 02 — SAML assertion anatomy](../labs/02-saml-assertion-anatomy/README.md)** — capture and decode a real SAML assertion, the exact XML you'll debug in tickets.

*— Janus 🔐*
