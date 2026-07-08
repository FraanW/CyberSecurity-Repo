# The eight domains of IAM — the full map

> **Janus's orientation note.** Last session you named the **eight domains of IAM** on the whiteboard. This note is the map that ties them together: what each one *is*, the **outcome** it buys FinCo, and the **mechanisms** (real protocols, real products) that make it happen. Think of it as the city map you keep pinned above your desk — every ticket you touch lands in one of these eight districts.
>
> **Prereqs:** [note 07 — IAM foundations](07-iam-foundations.md) (MFA, sessions, authZ models, Zero Trust). This note **references** the deep dives rather than repeating them: [PAM](11-pam-deep-dive.md), [IGA](12-iga-deep-dive.md), [SAML](02-saml-deep-dive.md) / [SAML session 2](13-saml-mastery-session2.md), [OAuth/OIDC](03-oauth-oidc-deep-dive.md), [LDAP/AD/Entra](04-ldap-ad-entra.md).

---

## TL;DR (the whole note in seven lines)

1. IAM is **not one thing** — it's eight cooperating disciplines. Products blur them; the *concepts* stay distinct.
2. **Directory Services** is the bedrock everything reads from. **Identity Management** creates and maintains the identity; **IGA** governs it (who approved, is it certified, does it break SoD).
3. **Authentication** proves *who you are*; **Federation/SSO** carries that proof between systems so one login works everywhere.
4. **Authorization** decides *what you may do* once you're in. **PAM** guards the crown-jewel accounts.
5. **CIAM** is the whole stack again, but pointed at *customers* (millions, UX-first, privacy-regulated) instead of *staff*.
6. The memory hook: **AuthN = front door, AuthZ = which rooms, IGA = the records office, PAM = the vault, Directory = the foundation, Federation = the shared badge, CIAM = the customer branch across the street.**
7. Almost every IAM ticket you'll ever get is one of these eight districts talking to another.

---

## The master table (skim this, then dive)

| # | Domain | The question it answers | What it achieves (risk removed) | Core mechanisms | Example products |
|---|---|---|---|---|---|
| 1 | **Identity Management** | *Who exists, and what's true about them?* | One authoritative, correct identity per human — no duplicates, no ghosts | HR→IdM sync, unique IDs, attributes, JML events, SCIM, connectors, correlation | Microsoft Identity Manager, SailPoint (IdM side), Entra ID provisioning |
| 2 | **Authentication (AuthN)** | *Are you really who you claim?* | Stolen passwords alone don't become breaches | Password hashing, MFA (TOTP/push/FIDO2), passkeys, adaptive/risk-based auth, session issue | Okta, PingFederate, Entra ID, Duo, YubiKey/FIDO2 |
| 3 | **Authorization (AuthZ)** | *What are you allowed to do?* | Blast-radius control — a valid login can't do everything | RBAC, ABAC, PBAC, ReBAC, least privilege, SoD, scopes/claims, OPA/Rego | OPA, Cedar, PlainID, in-app RBAC, API gateways |
| 4 | **PAM** | *Who touches the crown jewels, and is it recorded?* | Admin/service credentials can't be quietly stolen and reused | Vaulting, rotation, session brokering + recording, JIT, zero standing privilege | CyberArk, BeyondTrust, Delinea |
| 5 | **IGA** | *Should you still have this, and can we prove it?* | Entitlement creep + orphaned access killed; SOX/PCI evidence produced | Access requests/approvals, certifications, role mining, SoD policies, SCIM provisioning, reporting | SailPoint, Saviynt, Okta Identity Governance |
| 6 | **Federation / SSO** | *Can one login be trusted everywhere?* | No password sprawl; partners/SaaS trust FinCo without seeing passwords | SAML, OIDC/OAuth, metadata + certs, IdP/SP roles, token translation | **PingFederate**, Okta, Entra ID, Shibboleth |
| 7 | **Directory Services** | *Where is the source of accounts, groups, auth?* | A single, fast, reliable place every app checks identity against | LDAP, Active Directory, Entra ID, Kerberos/NTLM, OUs/groups/GPOs, virtual directories | **Active Directory**, Entra ID, OpenLDAP, Ping Directory |
| 8 | **CIAM** | *How do millions of customers sign up and log in — safely and smoothly?* | Onboarding at scale without fraud, and privacy-law compliance | OIDC-first, passwordless/OTP, social login, progressive profiling, risk/fraud engines, consent | Auth0 (Okta CIAM), PingOne for Customers / ForgeRock, Entra External ID |

> **The one-liner to remember:** *Directory* is where identity lives, *Identity Management* keeps it true, *IGA* keeps it justified, *AuthN* + *Federation* get you through the door, *AuthZ* + *PAM* decide what you touch inside, and *CIAM* runs the same play for customers.

---

## The map of the IAM city

```
                THE IAM CITY  —  workforce side                        CUSTOMER SIDE
 ┌──────────────────────────────────────────────────────────────┐  ┌──────────────────────┐
 │                     THE OFFICE TOWER                           │  │   CUSTOMER BRANCH    │
 │                                                                │  │   (8) CIAM           │
 │  FRONT DOOR / GATE            INSIDE-THE-BUILDING RULES        │  │                      │
 │  (2) AuthN  ── prove who      (3) AuthZ ── which rooms         │  │  millions of         │
 │      you are                      may you enter?              │  │  customers self-     │
 │  (6) Federation/SSO ── one                                     │  │  register + log in   │
 │      badge, trusted in every      ┌────────────────────┐       │  │  to the FinCo app    │
 │      partner + SaaS building       │     THE VAULT      │       │  │                      │
 │                                    │     (4) PAM        │       │  │  OIDC + OTP/passkey  │
 │                                    │  admin + service   │       │  │  + fraud/bot engine  │
 │                                    │  crown jewels      │       │  │  + consent (DPDP)    │
 │                                    └────────────────────┘       │  └──────────┬───────────┘
 ├────────────────────────────────────────────────────────────────┤             │
 │  RECORDS OFFICE  (lifecycle layer)                             │   different  │
 │  (1) Identity Mgmt ── create + keep the identity true          │   scale,     │
 │  (5) IGA          ── govern it: approved? certified? SoD ok?   │   different  │
 ├────────────────────────────────────────────────────────────────┤   team      │
 │  FOUNDATION / BEDROCK                                          │   (usually)  │
 │  (7) Directory Services ── the phonebook everything reads from ◄──────────────┘
 └────────────────────────────────────────────────────────────────┘
     ▲                                                        (CIAM often has its OWN
     └── HR system (Workday/SAP) feeds the Records Office          directory + own team)
```

**How to read it:** you build a city bottom-up. The **directory** is bedrock. The **records office** (Identity Mgmt + IGA) writes and audits who's allowed in the tower. The **front door** (AuthN + Federation) admits verified people; **inside rules** (AuthZ) and the **vault** (PAM) govern what they reach. The **customer branch** (CIAM) is a separate building — same disciplines, different crowd, different rules.

---

## 1. Identity Management — the source of truth

**In plain words.** Before anyone can log in, *the system has to know they exist.* Identity Management is the district that **creates one authoritative identity per person and keeps its facts (attributes) correct** for their whole time at FinCo.

**Scope.** Where identities are born and maintained: unique IDs, attributes (name, email, department, manager, employee number), and the **Joiner–Mover–Leaver (JML)** lifecycle events. It answers *who exists* and *what's true about them*.

**What it achieves.** Kills the mess of **duplicate, mismatched, and ghost identities**. One person = one identity, correlated across systems. Without it you get the classic breach setup: an ex-contractor whose account nobody knew existed, still logging in.

**How (mechanisms).**
- **Authoritative source (system of record):** usually **HR** (Workday, SAP SuccessFactors). HR is the *truth* for "does this person work here?"
- **HR → IdM sync:** a scheduled or event-driven feed creates/updates identities the moment HR does.
- **Unique identifier:** a stable ID (employee number) that never changes even if the name/email does.
- **[SCIM](https://www.rfc-editor.org/rfc/rfc7644) + connectors:** push the identity out to downstream apps and directories.
- **Identity correlation:** matching "Farhaan in AD" = "Farhaan in Salesforce" = "Farhaan in HR" into one logical person.

**Identity Management vs IGA — the distinction to nail.** Identity Management is the *plumbing* (create/update/sync the identity). **IGA (§5) is the governance layer on top**: *who approved this access, is it still certified, does it violate Separation of Duties?* Same records office, two jobs: **IdM = accurate**, **IGA = justified**.

**FinCo scenario.** HR marks a new analyst as "hired, start 21 July." Overnight, the IdM sync creates her identity with employee number, department = Payments Ops, manager = her lead. That single event will later trigger her directory account, her birthright roles, and her first login — none of which can happen until IdM says she exists.

**Products.** Microsoft Identity Manager (MIM), SailPoint / Saviynt (their provisioning side), Entra ID provisioning, custom HR-feed connectors.

---

## 2. Authentication (AuthN) — proving who you are

**In plain words.** The **front door check**: you claim to be Farhaan; AuthN makes you *prove it* before any door opens.

**Scope.** Everything that verifies identity at login: passwords and how they're stored, **MFA**, passwordless, and *how strongly* you were verified (the assurance level). It stops at "yes, it's really you" — it says nothing about what you're allowed to do (that's §3).

**What it achieves.** Ensures **a stolen password alone is not enough**. Credential theft is the #1 breach entry point; strong AuthN is the control that stops a leaked password from becoming an incident.

**How (mechanisms).**
- **Factors:** something you *know* (password/PIN), *have* (phone, security key), *are* (biometric), plus *context* (device, location).
- **Password storage:** **hashed, not encrypted**; unique **salt** per password; slow, memory-hard algorithm (**Argon2**, scrypt, bcrypt) — never MD5/SHA-1. (Deep dive in [note 07 §1](07-iam-foundations.md).)
- **MFA, weakest → strongest:** SMS OTP → **TOTP/push** → **FIDO2/WebAuthn/passkeys** (phishing-resistant, the gold standard).
- **Adaptive / risk-based auth:** raise the bar only when risk is high — new device, impossible travel, odd hour → **step-up** to MFA.
- **Session establishment:** on success, issue a session (cookie or token) so you don't re-auth every click.

**Attack ↔ defense.** **MFA fatigue / push bombing** (attacker with the password spams "Approve?" until you cave) → **number matching** or move to **passkeys**; **credential stuffing** (reused leaked passwords) → MFA + breached-password detection + rate limiting.

**FinCo scenario.** PCI-DSS **Req 8** requires MFA into the cardholder-data environment. Farhaan opens the admin console; PingFederate checks his password against the directory, then demands a **FIDO2 key tap** because the app is high-risk. Password phished last week? Useless without the physical key.

**Products.** PingFederate, Okta, Entra ID, Duo, YubiKey/FIDO2 authenticators.

---

## 3. Authorization (AuthZ) — what you may do

**In plain words.** You're through the door — now, **which rooms can you enter, and what can you touch?** AuthZ decides permissions *after* AuthN has proven identity.

**Scope.** Permission models, policy evaluation, least privilege, and **Separation of Duties (SoD)**. The distinction to keep sharp: **AuthN = who you are; AuthZ = what you may do.** Systems that confuse them ("logged in, therefore trusted with everything") are how privilege-escalation breaches happen.

**What it achieves.** **Blast-radius control.** A valid session should reach *only* what the job needs, so a compromised account (or a malicious insider) can do limited damage.

**How (mechanisms).**
- **RBAC** — access by **role** ("Teller → view accounts"). Most enterprises start here; roles are often just **directory groups** (§7) arriving as **group claims** in a token.
- **ABAC** — access by **attributes + policy** ("clearance ≥ dept AND business hours").
- **PBAC** — central **policy engine**, policy-as-code (e.g. **OPA/Rego**, AWS **Cedar**), decoupled from apps.
- **ReBAC** — access by **relationships** in a graph (Google **Zanzibar** model).
- **Scopes & claims** — in OAuth/OIDC tokens, `scope` and claims *are* the authorization the API enforces ([note 03](03-oauth-oidc-deep-dive.md)).
- **Least privilege + SoD** — grant the minimum; never let one person both *initiate* and *approve* a payment.

**Attack ↔ defense.** **Over-broad roles / privilege escalation** (a coarse role grants far more than needed) → least-privilege role design, **access reviews** (§5), SoD policies, model sensitive rules in ABAC/PBAC.

**FinCo scenario — the one to remember.** A support agent gets a ticket about a disputed charge. Her role lets her **VIEW** the customer's transactions — but the **REFUND** action is a separate entitlement she doesn't hold; issuing money back requires a second, approver role. That split is **SoD**, enforced by AuthZ, and it's exactly what a **SOX** auditor will ask you to prove.

**Products.** OPA, Cedar, PlainID, API gateways, in-app RBAC engines.

---

## 4. PAM — the vault for the crown jewels

**In plain words.** A few accounts can do *anything* — domain admins, root, database owners, the service accounts running payments. **PAM is the armored vault** around those.

**Scope.** Privileged human accounts *and* non-human/service accounts: how their secrets are stored, rotated, used, and recorded. Full treatment in **[note 11 — PAM deep dive](11-pam-deep-dive.md)** — this is the map-level summary.

**What it achieves.** Stops the endgame move: **a standing admin/service credential stolen and quietly reused** (pass-the-hash, a leaked service-account password in a script). PAM makes privileged access *temporary, brokered, and recorded* so it can't be stolen once and abused forever.

**How (mechanisms).**
- **Credential vaulting** — privileged secrets live in a vault, never in scripts or on sticky notes.
- **Rotation** — secrets change automatically and often, so a leaked one is short-lived.
- **Session brokering + recording** — you connect *through* the PAM proxy; the session is monitored and recorded for audit.
- **JIT access + zero standing privilege (ZSP)** — no permanent admin rights; access is granted for a window then revoked, shrinking the attack surface to near zero between uses.
- **Service-account management** — the hardest part in a Kubernetes/mTLS shop: rotating secrets and certs that apps depend on without breaking them.

**FinCo scenario.** Farhaan needs to run a query on the core-banking DB. Instead of holding a standing DBA password, he **requests JIT access in CyberArk**; on approval he gets a **60-minute** window, connects through a recorded session, and the credential rotates the moment it closes. If his laptop is later compromised, there's no standing DB password to steal.

**Products.** CyberArk, BeyondTrust, Delinea. (Ties to OAuth **client credentials** and **mTLS** keys — all things a vault should hold; see [note 06](06-tls-https-mtls.md).)

---

## 5. IGA — governance and evidence

**In plain words.** IAM isn't just *letting people in* — it's proving, over time, that **everyone has exactly the access they should, and no more.** IGA is the **records office with an audit trail**.

**Scope.** The full identity lifecycle *with governance*: access requests, approvals, periodic reviews, role design, SoD policy, and the reporting auditors demand. Deep dive in **[note 12 — IGA deep dive](12-iga-deep-dive.md)**.

**What it achieves.** Two big wins: (1) kills **entitlement creep** (access that piles up as people move roles and old grants are never removed) and **orphaned accounts**; (2) produces the **evidence** for **SOX ITGC** and **PCI-DSS Req 7 & 8** — the "prove least privilege" audit.

**How (mechanisms).**
- **Access requests + approvals** — self-service "request access," routed to the right approver.
- **Access certifications / reviews (recertification)** — managers periodically confirm their team's access is still appropriate. This *is* the core SOX/PCI evidence cycle.
- **Role mining** — analyzing who-has-what to design sensible roles.
- **SoD policies + entitlement catalog** — codified toxic-combination rules and a catalog of grantable access.
- **Provisioning orchestration** — **[SCIM](https://www.rfc-editor.org/rfc/rfc7644)** and connectors to auto-provision on Joiner, adjust on Mover, and **deprovision on Leaver everywhere**.

**Attack ↔ defense.** **Orphaned / over-provisioned accounts** (ex-employees or stale grants abused by attackers or insiders) → automated JML deprovisioning, periodic certifications, SoD rules, reconcile IdP ↔ directory ↔ app regularly.

**FinCo scenario.** Quarter-end: SailPoint kicks off an **access certification**. Every manager gets a list of their team's entitlements to approve or revoke. Farhaan's job is to chase the stragglers and export the signed-off report — that report is what the external **SOX** auditor takes as proof FinCo enforces least privilege. This is likely his **bread-and-butter**.

**Products.** SailPoint, Saviynt, Okta Identity Governance.

---

## 6. Federation / SSO — one login, trusted everywhere

**In plain words.** **SSO** = log in once, use many apps. **Federation** = two *different* organizations/systems agree to trust each other's logins via a signed contract (metadata + certificates). **Federated SSO** = SSO achieved *across* that boundary.

**Scope.** The trust and protocols that carry a proven identity from an **Identity Provider (IdP)** to a **Service Provider (SP)**: SAML and OIDC/OAuth, metadata, signing/encryption certs, and **token translation** (e.g. SAML-in, OIDC-out). Deep dives: **[SAML](02-saml-deep-dive.md)**, **[SAML session 2](13-saml-mastery-session2.md)**, **[OAuth/OIDC](03-oauth-oidc-deep-dive.md)**.

**What it achieves.** **No password sprawl.** Staff have one strong login; SaaS vendors and partner banks trust FinCo's IdP **without ever seeing a password**. One place to enforce MFA and disable a leaver — instead of 40 separate app logins.

**How (mechanisms).**
- **SAML 2.0** — XML assertions, `<Issuer>`/`<Audience>`, signed with the IdP's key, pinned via metadata. IdP-init vs SP-init flows.
- **OIDC on OAuth 2.0** — JSON **ID token** (who you are) + **access token** (for APIs); the modern default for new apps and mobile.
- **Trust setup** — exchange **metadata** + **certificates** at onboarding; that swap *is* the trust ceremony (not a CA chain).
- **Token translation / IdP chaining** — a federation hub converts one protocol/token to another for downstream apps.

**Attack ↔ defense.** **SAML assertion tampering / `redirect_uri` manipulation / token replay** → verify signatures against the *pinned* cert (never an embedded one), strict audience + recipient checks, short validity windows, one-time assertion IDs, **PKCE** for OAuth code flows.

**FinCo scenario.** "Onboard this new SaaS analytics vendor to SSO" lands in Farhaan's queue. In **PingFederate** he creates an SP connection: import the vendor's metadata, pin their certificate, agree on the NameID format and which attributes to release. Result: staff click the tile and they're in — no new password, and a leaver loses access to it the instant PingFederate disables them.

**Products.** **PingFederate** (FinCo's stack), Okta, Entra ID, Shibboleth, ForgeRock.

---

## 7. Directory Services — the phonebook and auth backbone

**In plain words.** The **foundation everything else reads from**: the authoritative list of accounts, groups, and (often) the thing that actually checks passwords. Deep dive: **[note 04 — LDAP/AD/Entra](04-ldap-ad-entra.md)** and the forthcoming [note 15 — Kerberos explained](15-kerberos-explained.md).

**Scope.** The store and its protocols: **LDAP** (the query protocol), **Active Directory** (the on-prem workhorse), **Entra ID** (cloud), authentication protocols **Kerberos** and **NTLM**, and the organizing structures — **OUs**, **groups**, **GPOs** — plus **virtual directories** that present one view over several stores.

**What it achieves.** A **single, fast, reliable place** to answer "does this account exist, what groups is it in, is this password right?" When it's healthy, AuthN and AuthZ just work. When it's down or slow, *everything* in the tower stalls.

**How (mechanisms).**
- **LDAP** — hierarchical directory of entries (DN, `cn`, `ou`, `dc`) you bind and search against.
- **Active Directory** — LDAP + **Kerberos** (ticket-based SSO on the LAN) + **NTLM** (legacy challenge-response) + **GPOs** (policy pushed to machines).
- **Groups** — the raw material of RBAC (§3): membership becomes a role, delivered as a group claim in a token.
- **Entra ID** — the cloud directory behind Microsoft 365 and Conditional Access.

**Attack ↔ defense.** **Kerberoasting** (request a service ticket, crack its hash offline to recover a service-account password) → long/random service-account passwords, **gMSA** (managed accounts), monitor for anomalous ticket requests; **LDAP injection** → parameterize/escape directory queries.

**FinCo scenario.** Farhaan adds the new analyst to the AD group `GRP-Payments-Ops-ReadOnly`. That single group membership flows into PingFederate as a **group claim**, which the payments app maps to its read-only role. One directory change → correct access in a downstream app, no per-app fiddling.

**Products.** **Active Directory**, Entra ID, OpenLDAP, Ping Directory.

---

## 8. CIAM — the customer-facing branch

**In plain words.** Take the whole IAM stack you just learned and point it at **customers instead of staff.** That one change flips almost every priority. This district gets the fullest treatment because none of your other notes cover it yet.

**Scope.** Identity for the people who *use FinCo's product* — registration, login, profile, consent, and recovery for potentially **millions** of external users. It's a different discipline from **workforce IAM** (staff) even though the protocols overlap.

**Workforce IAM vs CIAM — the contrast that explains everything:**

| Dimension | Workforce IAM (staff) | **CIAM (customers)** |
|---|---|---|
| **Scale** | thousands, known in advance | **millions**, unknown, spiky (marketing launch = 10x traffic) |
| **Who creates the account** | HR/IdM provisions it | the **customer self-registers** |
| **Top priority** | control, audit, least privilege | **UX & conversion** — every extra field loses signups |
| **Directory** | AD / Entra (internal) | a separate high-scale customer store |
| **Login options** | password + MFA / passkey | **passwordless/OTP, social login, passkeys** |
| **Governance** | IGA certifications, SoD | **consent & privacy** (opt-ins), not SoD |
| **Regulation** | SOX, PCI-DSS, internal audit | **India's DPDP Act, GDPR**, consumer-protection, KYC-adjacent |
| **Main threat** | insider misuse, credential theft | **bots, fake signups, account-takeover fraud at scale** |

**What it achieves.** Lets FinCo **onboard and retain customers smoothly *without* opening the floodgates to fraud or breaking privacy law.** A clunky signup loses revenue; a weak one invites mass account-takeover and regulator fines. CIAM threads that needle.

**How (mechanisms).**
- **OIDC-first** — customer login is almost always OAuth 2.0 / OIDC (mobile + web friendly), rarely SAML.
- **Self-registration + progressive profiling** — collect the *minimum* to sign up, then gather more over time (fewer fields = higher conversion).
- **Passwordless / OTP** — email/SMS one-time codes and **passkeys**, so customers never manage a password (and can't reuse a breached one).
- **Social login** — "Sign in with Google/Apple," delegating authentication to a big IdP.
- **Risk & fraud engines** — device fingerprinting, velocity checks, bot detection at the signup and login edge.
- **Delegated consent & preference management** — capturing, storing, and honoring **DPDP/GDPR** consent, with an audit trail of what the customer agreed to.
- **Account recovery at scale** — self-service reset that's smooth for real users yet resistant to account-takeover (the hardest UX/security trade-off in CIAM).

**Attack ↔ defense.** **Credential-stuffing + bot signup fraud** (automated ATO against a huge, password-reusing user base) → passwordless/passkeys, bot detection, rate limiting, breached-password checks, step-up on risky logins; **consent/privacy violations** → explicit, logged consent + data minimization (a DPDP/GDPR requirement, not a nice-to-have).

**FinCo scenario — and why it's usually a *separate* system and team.** The customer-facing FinCo app's **login and onboarding** (which sits right next to **KYC** identity verification) is CIAM. Farhaan's **workforce IAM** — PingFederate, AD, IGA for staff — is a different world:
- **Different scale & availability** — a customer outage is lost revenue and front-page news; it can't share fate with the internal HR-driven stack.
- **Different priorities** — conversion and privacy vs audit and SoD.
- **Different regulation** — DPDP/GDPR/consumer law vs SOX/PCI/internal ITGC.

So FinCo very likely runs **two stacks and two teams**: workforce IAM (Farhaan's home) and a CIAM/product-security team. Knowing *which* stack a ticket belongs to is half the battle — and knowing the vocabulary of both makes you the person who can bridge them.

**Products.** Auth0 (Okta CIAM), PingOne for Customers / ForgeRock, Microsoft Entra External ID.

---

## One identity's day through all eight domains

Watch a single day stitch the whole city together:

1. **Identity Management (§1)** — HR marks a new analyst hired. Overnight the HR→IdM sync **creates her identity** with a unique employee number and attributes (dept = Payments Ops, manager assigned).
2. **Directory Services (§7)** — IdM provisions her **AD account** and drops her into the birthright group `GRP-Payments-Ops-ReadOnly`.
3. **IGA (§5)** — her **birthright roles** are granted automatically; the grant is logged with an approver and will surface in the next **access certification**. SoD rules confirm none of her access is a toxic combination.
4. **Authentication (§2)** — first morning, she logs in. Password checks against the directory, then **PingFederate demands a passkey/MFA** because it's a new device. Identity proven.
5. **Federation / SSO (§6)** — she clicks the payments app tile. PingFederate (**IdP**) mints a token for the app (**SP**); her AD group arrives as a **group claim**. One login now opens every federated app.
6. **Authorization (§3)** — inside the app, that group claim maps to a **read-only role**. She can **VIEW** transactions; the **REFUND** action is a separate entitlement she doesn't hold (**SoD**).
7. **PAM (§4)** — a month later she needs to run a core-banking DB query. No standing DBA rights — she requests **JIT access in CyberArk**, gets a recorded 60-minute session, and the credential rotates when it closes.
8. **CIAM (§8)** — meanwhile, across the street, a *customer* opens the FinCo mobile app, logs in with a **passkey via OIDC**, a **fraud engine** silently scores the login, and their earlier **DPDP consent** governs what data the app may use. Same disciplines, entirely different building and team.

Eight districts, one seamless day. Every step you can now name, place on the map, and tie to a control an auditor cares about.

---

## Where the boundaries blur (the real-org caveat)

The eight domains are **concepts, not org charts**. In practice:

- **One product spans several domains.** Okta does AuthN + Federation + IGA + CIAM. Entra ID is Directory + AuthN + Federation + Conditional Access (AuthZ). SailPoint does Identity Management *and* IGA. Don't expect one tool per district.
- **Team names rarely match domain names.** FinCo might call it "Identity Engineering," "Access Management," "IAM Ops," or split it as "Workforce Identity" vs "Customer Identity / Product Security." The *work* still maps to these eight.
- **AuthN and AuthZ get conflated constantly.** People say "auth" for both. When you hear it, ask *"authentication or authorization?"* — the fix and the team differ.
- **Federation blurs into AuthN.** The IdP does the authenticating; federation just *carries* the result. On a whiteboard they merge; in a debug they're separate hops.
- **CIAM is often owned outside "IAM" entirely** — by the product or platform team — which is exactly why knowing its vocabulary makes you valuable across the org boundary.

The skill is **triage**: hear a ticket, place it in a district (or name the two districts talking), and know which mechanism and which team owns it.

---

## What you learned

- **The eight IAM domains** and, for each, the *question it answers*, the *outcome it buys FinCo*, the *mechanisms/protocols*, a *FinCo scenario*, and *real products* — all in the master table.
- **The city map:** directory as bedrock, Identity Mgmt + IGA as the records office, AuthN + Federation as the front door, AuthZ + PAM as the inside rules and the vault, CIAM as the separate customer branch.
- **Identity Management vs IGA** — accurate vs justified — and **AuthN vs AuthZ** — who you are vs what you may do — the two distinctions systems most often confuse.
- **CIAM in depth** — why customer identity is a different discipline (scale, UX, fraud, DPDP/GDPR) and usually a separate stack and team from Farhaan's workforce IAM.
- **The end-to-end walkthrough** — one new hire's day touching all eight domains — plus how the boundaries blur in real orgs.

## Next

- **[Note 18 — PingFederate explained](18-pingfederate-explained.md)** — zoom into FinCo's federation engine: connections, adapters, token translation, and how it ties directory, AuthN, and SSO together in one product.
- **Revisit the deep dives** as needed: [PAM (§4)](11-pam-deep-dive.md), [IGA (§5)](12-iga-deep-dive.md), [SAML (§6)](13-saml-mastery-session2.md), [Directory (§7)](04-ldap-ad-entra.md).
- **Do the drill:** for each of the last five tickets you saw (or imagine), name its district(s) out loud — that's the triage muscle this note builds.

*— Janus 🔐, mapping the city so no ticket ever feels lost*
