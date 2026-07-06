# SSO explained correctly + the IAM buzzword glossary

> **Janus's curriculum, written to [Lefler's Laws](../../LEFLER-LAWS.md).** Two jobs here: (1) pin down **SSO** precisely — because it's the most-used and most-muddled word in IAM — and (2) give you a **plain-English glossary of every IAM buzzword** you'll hear, grouped so you can find things fast. Skim it now; keep it open during your first weeks. Prereq: none — this is your dictionary.

---

# Part 1 — SSO, explained correctly

## The one-line definition

**Single Sign-On (SSO) = log in once, then reach many apps without logging in again.**

That's it. You authenticate a single time, and a whole set of apps let you in based on that one login.

## The mistake almost everyone makes

People use **"SSO"** and **"federation"** as if they're the same thing. They're not:

- **SSO is the *experience*** — the outcome you feel: "I only logged in once."
- **Federation (SAML / OIDC) is the *machinery*** that usually delivers that experience across different companies/apps.

> **Say it like this:** *"SSO is the result; federation is one way to achieve it."* Federation is the most common engine for SSO, but not the only one (Kerberos gives you SSO inside a Windows domain without SAML/OIDC).

## How SSO actually works (the engine)

The secret is a **session held at the Identity Provider (IdP)** — your Okta/Entra/Keycloak. Walk through it:

```
 1. You open App A  →  App A: "I don't know you, go to the IdP."
 2. IdP: prompts for password + MFA  →  you authenticate  →  IdP CREATES A SESSION (a cookie for the IdP).
 3. IdP sends App A a signed "approved" note (assertion/token)  →  App A logs you in.
 4. You open App B  →  App B: "go to the IdP."
 5. IdP sees its session from step 2 is STILL ALIVE  →  no prompt  →  sends App B an approved note.
 6. App B logs you in. You never re-entered a password. ← THAT is SSO.
```

**The whole trick:** step 5. Because the IdP already has a live session for you, the second, third, and tenth app get you in silently. Kill that IdP session (log out, or it expires) and the next app makes you sign in again.

## The flavors of SSO (so the variations make sense)

| Flavor | What it is | Example |
|---|---|---|
| **Enterprise / federated SSO** | One IdP logs you into many company/SaaS apps via SAML or OIDC | Okta logs you into Salesforce, Workday, Slack |
| **Social login** | SSO for consumers, using a big provider as the IdP (via OIDC) | "Sign in with Google / Apple" |
| **Web SSO (same domain)** | One company's own apps share a session | `mail.acme.com` and `docs.acme.com` share a login |
| **Desktop / Kerberos SSO** | Log into your Windows PC → get into intranet apps automatically | Domain-joined laptop → internal SharePoint, no re-login |

## What SSO is **not** (clear these up and you're ahead of most)

- **SSO ≠ a password manager.** A password manager (LastPass, 1Password) just *autofills a different password* for each site — you still have many separate accounts. SSO means there's **one identity** and the apps trust it; there's no separate password per app at all.
- **SSO ≠ MFA.** SSO is *how many* logins (one). MFA is *how strong* a login is (multiple factors). You combine them: **one strong login** (SSO + MFA) is the goal.
- **SSO ≠ "same password everywhere."** Reusing one password across apps is the *opposite* of SSO — it's a security disaster. SSO means apps never hold your password at all.
- **SSO vs SLO.** **Single Logout (SLO)** is the reverse: log out once, and the IdP tries to end your session at *every* app. It's real, but flaky in practice (expect "logout didn't log me out everywhere" tickets).

## Why SSO matters at a fintech (the "why you care")

**Good for security *and* users at the same time — rare:**
- **Fewer passwords** = smaller attack surface (no password stored in 100 apps).
- **One place to enforce policy** — put MFA and Conditional Access at the IdP, and *every* app benefits.
- **One switch for offboarding** — disable a **leaver** at the IdP and they lose access everywhere at once (a SOX/PCI win).

**The catch (always name the risk):**
- The IdP becomes a **single point of failure** and a **high-value target**. If it's down, *everything* is down. If it's breached, the attacker gets *everything*. That's why the IdP is protected harder than anything else — and why **PAM**, phishing-resistant **MFA**, and monitoring around it matter so much.

## Say it back (30-second self-test)

1. What's the difference between SSO and federation? *(SSO = the "log in once" experience; federation = the SAML/OIDC machinery that delivers it.)*
2. What makes the second app skip the login prompt? *(A live session already held at the IdP.)*
3. Why isn't a password manager SSO? *(It stores many separate passwords; SSO means apps trust one identity and hold no password.)*
4. What's the trade-off of SSO? *(Convenience + central control, but the IdP is a single point of failure and a prime target.)*

---

# Part 2 — The IAM buzzword glossary

**How to use this:** grouped by theme, plain-English one-liners. Skim the group you need. ⭐ = you'll hear it constantly.

## Identity basics

| Term | In one line |
|---|---|
| **Identity** | The digital representation of a person, service, or device. |
| **Account** | A specific login record in a specific system (one identity can have many accounts). |
| **Credential** | The secret you prove yourself with — a password, a certificate, a passkey. |
| **Subject / Principal** ⭐ | The "who" a request is about — the authenticated entity. |
| **Claim** ⭐ | A single statement about a subject ("email = farhaan@…", "group = admins"). |
| **Assertion** ⭐ | A bundle of signed claims from an IdP — SAML's "approved" note. |
| **Entitlement** | A specific permission a user holds (access to *this* app, *this* folder). |
| **JML (Joiner/Mover/Leaver)** ⭐ | The identity lifecycle: hire → change role → leave. |

## Authentication (proving *who you are*)

| Term | In one line |
|---|---|
| **Authentication (AuthN)** ⭐ | Proving who you are. |
| **Factor** | A category of proof: something you **know** / **have** / **are**. |
| **MFA / 2FA** ⭐ | Requiring 2+ factors (e.g., password + phone). 2FA = exactly two. |
| **OTP / TOTP / HOTP** | One-Time Password; TOTP = time-based (the 6-digit code that rotates), HOTP = counter-based. |
| **Push authentication** | Approve a login by tapping "Yes" in an app (Okta Verify, Microsoft Authenticator). |
| **FIDO2 / WebAuthn** ⭐ | The modern standard for hardware-backed, **phishing-resistant** login. |
| **Passkey** ⭐ | A FIDO2 credential that replaces passwords; synced across your devices. |
| **Passwordless** | Logging in with no password at all (passkey, biometric, hardware key). |
| **Phishing-resistant MFA** ⭐ | MFA that can't be tricked by a fake site (FIDO2/passkeys, smart cards). |
| **Step-up / adaptive auth** | Asking for *more* proof only when risk is higher (new device, big transfer). |
| **SSO (Single Sign-On)** ⭐ | Log in once, reach many apps (see Part 1). |

## Authorization (deciding *what you can do*)

| Term | In one line |
|---|---|
| **Authorization (AuthZ)** ⭐ | Deciding what an authenticated subject may do. |
| **RBAC** ⭐ | Role-Based Access Control — access by your **role** ("Teller," "Admin"). |
| **ABAC** | Attribute-Based — access by **attributes/rules** ("dept=finance AND country=IN"). |
| **PBAC / policy-based** | Access decided by a central **policy** engine. |
| **ReBAC** | Relationship-Based — access by **relationships** ("owner of this doc"); Google Zanzibar. |
| **Scope** ⭐ | In OAuth, the slice of API access an app is granted (`read:email`). |
| **Least privilege (PoLP)** ⭐ | Give the *minimum* access needed — nothing more. |
| **Separation of Duties (SoD)** ⭐ | No one person can do a risky action end-to-end (request *and* approve). |
| **Entitlement creep** | Permissions piling up over time as people change roles. |

## Federation, SSO & protocols

| Term | In one line |
|---|---|
| **Federation** ⭐ | Two parties agreeing to trust each other's logins (the engine of SSO). |
| **IdP (Identity Provider)** ⭐ | The service that authenticates you and vouches for you (Okta, Entra, Keycloak). |
| **SP (Service Provider)** ⭐ | The app that trusts the IdP's vouch. (In OIDC, called the **Relying Party / RP**.) |
| **SAML 2.0** ⭐ | XML-based federation for enterprise SSO (see [note 02](02-saml-deep-dive.md)). |
| **OAuth 2.0** ⭐ | Delegated **authorization** — lets an app call an API for you (see [note 03](03-oauth-oidc-deep-dive.md)). |
| **OIDC (OpenID Connect)** ⭐ | **Authentication** built on OAuth; modern login. |
| **JWT** ⭐ | JSON Web Token — a compact, signed token (`header.payload.signature`). |
| **Access / ID / Refresh token** ⭐ | Access = for the API; ID = who logged in (OIDC); Refresh = get a new access token. |
| **Metadata** | The setup "contract" swapped during federation (endpoints + certs). |
| **ACS (Assertion Consumer Service)** | The SP endpoint that receives the SAML assertion. |
| **SLO (Single Logout)** | Log out once → end sessions everywhere (best-effort). |
| **WS-Fed** | Older Microsoft federation protocol; you'll meet it on legacy apps. |
| **SCIM** ⭐ | The protocol that auto-creates/updates/deletes accounts in SaaS apps. |

## Directories (where users live)

| Term | In one line |
|---|---|
| **LDAP** ⭐ | The protocol + model for directory lookups (the "phone book"). |
| **DN / RDN** | Distinguished Name — an entry's full path in the directory tree. |
| **OU (Organizational Unit)** | A folder in the directory for organizing objects. |
| **Active Directory (AD)** ⭐ | Microsoft's on-prem directory (LDAP + Kerberos + Group Policy). |
| **Domain Controller (DC)** | A server running AD; the crown jewel. |
| **Kerberos** ⭐ | AD's ticket-based authentication (TGT → service ticket). |
| **NTLM** | Older Windows auth; weaker, being retired. |
| **Entra ID** ⭐ | Microsoft's **cloud** IdP (formerly Azure AD) — OIDC/SAML/SCIM, no LDAP/Kerberos. |
| **Tenant** | Your org's dedicated instance of a cloud service (Entra tenant). |
| **Entra Connect** ⭐ | Syncs on-prem AD → Entra (a top source of hybrid tickets). See [note 04](04-ldap-ad-entra.md). |

## Transport & certificates

| Term | In one line |
|---|---|
| **HTTP / HTTPS** ⭐ | HTTP = plaintext; HTTPS = HTTP inside an encrypted TLS tunnel (the padlock). |
| **TLS** ⭐ | The protocol that encrypts + authenticates the connection (see [note 06](06-tls-https-mtls.md)). |
| **mTLS (mutual TLS)** ⭐ | TLS where **both** sides show certificates — machine-to-machine auth. |
| **PKI** | Public Key Infrastructure — the whole system of keys, certs, and CAs. |
| **CA (Certificate Authority)** | The trusted issuer that signs certificates. |
| **X.509 certificate** | The standard certificate format (binds a public key to an identity). |
| **JWKS** | The URL where an OIDC provider publishes its public signing keys. |

## Privileged access

| Term | In one line |
|---|---|
| **PAM** ⭐ | Privileged Access Management — securing admin/superuser access. |
| **PIM** | Privileged Identity Management (Microsoft's term for JIT admin roles). |
| **Vault** | A secure store for privileged credentials/secrets (CyberArk, HashiCorp Vault). |
| **JIT (Just-in-Time) access** ⭐ | Granting admin rights only for a short window, on request. |
| **Session recording** | Recording what an admin did during a privileged session (audit). |
| **Service account** ⭐ | A non-human account that runs an app/script; a prime attack target. |
| **Secret** | Any credential a machine uses (API key, client secret, private key). |
| **Break-glass account** | An emergency admin account, tightly controlled and monitored. |

## Governance (who *should* have what)

| Term | In one line |
|---|---|
| **IGA** ⭐ | Identity Governance & Administration — the lifecycle + review machine. |
| **Provisioning / Deprovisioning** ⭐ | Creating / removing accounts and access (often via SCIM). |
| **Access review / certification** ⭐ | Periodic "does this person still need this?" sign-off (SOX/PCI evidence). |
| **Recertification** | Re-running an access review on a schedule. |
| **Birthright access** | The baseline access everyone gets on day one (email, intranet). |
| **Access request** | A user asking for extra access, routed for approval. |
| **Role mining** | Analyzing who has what to *design* sensible roles. |
| **Entitlement catalog** | The menu of grantable access, described in business terms. |

## Zero Trust & policy

| Term | In one line |
|---|---|
| **Zero Trust** ⭐ | "Never trust, always verify" — check every request, trust no network by default. |
| **Conditional Access** ⭐ | Entra's policy engine ("require MFA if risky/off-network") — Zero Trust in practice. |
| **PEP / PDP** | Policy Enforcement Point (the gate) / Policy Decision Point (the brain). |
| **Micro-segmentation** | Splitting the network into tiny zones so a breach can't spread. |
| **Continuous verification** | Re-checking trust *during* a session, not just at login. |
| **BYOD** | Bring Your Own Device — personal devices accessing work resources. |

## Ops & incident-channel shorthand

| Term | In one line |
|---|---|
| **"SSO is down"** | Federation to an app broke (cert? metadata? clock skew?). |
| **"Cert rotation"** ⭐ | Replacing an expiring signing/TLS certificate before it breaks logins. |
| **"Sync is stale"** | Entra Connect hasn't reflected an on-prem change yet. |
| **"They're a leaver"** ⭐ | An ex-employee — access should already be gone (audit red flag if not). |
| **Clock skew** ⭐ | Servers' clocks disagree → assertions "expired"/"not yet valid." |
| **Allowlist / blocklist** | Explicitly permitted / denied items (e.g., redirect URIs). |
| **STRICT vs PERMISSIVE (mTLS)** | STRICT rejects plaintext; PERMISSIVE still allows it (mesh setting). |

---

## What you learned

- **SSO** is the "log in once" *experience*; **federation** is the *machinery* — and the engine is a **live session at the IdP**.
- You can now decode essentially any IAM word thrown at you, grouped by where it fits in the [big picture](01-iam-protocol-landscape.md).

## Next

- Keep this open during standups; when a new word appears, find it here (or ask **Janus** to add it).
- Turn words into questions with [note 05 — first-week questions](05-first-week-questions.md).
- See many of these live in the [interactive guide](../iam-protocols-visual-guide.html) and the [labs](../labs/).

*— Janus 🔐 (to Lefler's Laws ⚙️)*
