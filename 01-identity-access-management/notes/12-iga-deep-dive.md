# Identity Governance & Administration (IGA) — deep dive

> **Janus's deep dive, written to [Lefler's Laws](../../LEFLER-LAWS.md).** IGA is the layer that answers *"who **should** have access to what — and can we **prove** it?"* It's very likely your literal day job at FinCo: provisioning, access reviews, and Separation-of-Duties are the machinery that keeps a fintech both running and audit-ready. Prereqs: [note 07 §IGA](07-iam-foundations.md), [note 04 (directory groups)](04-ldap-ad-entra.md), [note 11 (PAM)](11-pam-deep-dive.md). Compliance angle: [note 09 (PCI)](09-pci-dss-and-iam.md).

---

## The 30-second version (TL;DR)

- **IGA governs identity.** It decides **who should have what access**, hands it out through a **proper process**, **takes it back** when people leave, and keeps the **paperwork that proves** it to auditors.
- Its four everyday jobs: **lifecycle (Joiner/Mover/Leaver)**, **provisioning**, **access requests & approvals**, and **access reviews (certifications)** — plus **Separation of Duties (SoD)** to stop fraud.
- **In a fintech it's the SOX + PCI evidence machine.** Your access reviews and deprovisioning *are* the audit controls.

> **Analogy:** if [PAM](11-pam-deep-dive.md) is the **vault guard** for the master keys, **IGA is the HR department + rulebook + auditor for *all* access**. It decides who *should* get keys, issues them through a proper process, collects them back when someone leaves, and keeps the records that prove to the auditor it was all done by the book.

---

## 1. What IGA is — and how it differs from IAM & PAM

**IGA = Governance + Administration.**
- **Governance** — the policy/oversight side: access reviews, SoD, compliance reporting.
- **Administration** — the operational side: provisioning accounts and managing the identity lifecycle.

People blur "IAM," "IGA," and "PAM." Here's the clean split:

| Layer | Question it answers | When it acts | Example tool |
|---|---|---|---|
| **Access Management (IAM/AM)** | "Can this login proceed **right now**?" (authN/authZ) | **Runtime**, every request | Okta / Entra SSO + MFA |
| **PAM** | "Is this **privileged** access controlled?" | Runtime, for admins/secrets | CyberArk vault |
| **IGA** | "Who **should** have what — and can we **prove** it?" | **Lifecycle + periodic** | SailPoint / Saviynt |

> **The one-liner:** Access Management **lets you in**; PAM **guards the crown jewels**; IGA **decides and proves what you should have in the first place.** They interlock — IGA often feeds the roles/groups that Access Management enforces, and governs the privileged accounts PAM protects.

---

## 2. The problem IGA solves

Without IGA, access rots over time:

- **Access sprawl** — nobody can answer "who has access to the payments app?"
- **Entitlement creep** — people change roles and *keep* old access forever ([note 07](07-iam-foundations.md)).
- **Orphaned accounts** — a leaver's account stays live (a classic breach entry point).
- **Manual errors** — access granted by email/ticket, inconsistently, with no record.
- **Rubber-stamped reviews** — managers click "approve all" without looking.
- **SoD violations** — one person can quietly do a whole risky process alone.
- **Audit failures** — no evidence that access is controlled → a failed SOX/PCI assessment.

IGA replaces "access by favor and forgotten tickets" with **access by policy, process, and proof.**

---

## 3. The IGA pillars (what an IGA program actually does)

| Pillar | What it does | Why it matters |
|---|---|---|
| **1. Lifecycle (Joiner/Mover/Leaver)** | Automate access as people join, change role, and leave — driven by an **authoritative source** | Closes the gaps that create orphans and creep |
| **2. Provisioning / deprovisioning** | Create/update/remove accounts across apps (via **SCIM**/connectors) | Consistent, fast, and logged |
| **3. Access requests & approvals** | Self-service **catalog** + approval workflow | Access is granted by process, not favors |
| **4. Access certifications / reviews** | Periodic "**still needed?**" attestation campaigns | The #1 audit control ([PCI 7.2.4](09-pci-dss-and-iam.md)) |
| **5. Role management** | Roles vs entitlements; **role mining** | Manage access in bulk, cleanly |
| **6. Separation of Duties (SoD)** | Detect/prevent **toxic combinations** | Fraud prevention (a SOX core control) |
| **7. Policy, compliance & reporting** | Enforce policy; generate audit evidence | Pass the assessment |
| **8. Identity analytics** | Outlier/peer-group analysis, risk scoring, AI recommendations | Surface hidden risk; smarter decisions |

**A little more on the key ones:**

- **Lifecycle & the authoritative source.** IGA doesn't guess who works here — it listens to a **source of truth** (usually **HR / Workday**). A "new hire" event triggers **birthright access** (the baseline everyone gets: email, VPN, intranet). A "role change" re-evaluates access. A "termination" triggers removal. *Garbage in the HR feed = garbage access, so data quality matters enormously.*
- **Access requests & the catalog.** Users shop from an **access catalog** where entitlements are described in **business terms** ("Payments – Read Only") rather than cryptic group names (`CN=PMT_RO_G`). Requests route to the right **approver** with a full record.
- **Role management.** A **role** bundles many entitlements ("Teller" = these 12 permissions). **Role mining** analyzes who currently has what to *design* sensible roles. Beware **role explosion** (so many roles they're worse than entitlements). See RBAC in [note 07](07-iam-foundations.md); roles often map to directory **groups** ([note 04](04-ldap-ad-entra.md)).
- **Identity analytics.** Modern IGA flags outliers ("Farhaan has access no one else in his team has — is that right?") and can *recommend* access based on peers. **Guardrail:** AI *suggests*, a human *approves* — automated grants without review is an audit problem ([note 05 §D](05-first-week-questions.md)).

---

## 4. The IGA flow, end to end

Follow one employee through the whole lifecycle:

```
 HR hires Farhaan  (Workday = AUTHORITATIVE SOURCE)
        │  event: "joiner · dept=IAM"
        ▼
 IGA grants BIRTHRIGHT access ──[SCIM / connectors]──► email, VPN, intranet
        │
        ▼
 Farhaan needs more → requests from the ACCESS CATALOG
        → APPROVAL workflow → provisioned to the app
        │
        ▼
 Every 6 months → ACCESS REVIEW: the owner attests "still needed?"
        → unused/expired access is REVOKED (closed-loop)
        │
        ▼
 Farhaan moves to a new team (MOVER) → access RE-EVALUATED
        (old access removed, new birthright added)   ← stops entitlement creep
        │
        ▼
 Farhaan leaves (LEAVER) → AUTOMATED DEPROVISIONING everywhere
        │
        └───────────────► AUDIT TRAIL captured at every step  (SOX / PCI evidence)
```

**That flow *is* the identity lifecycle with teeth.** Every arrow is also a control an auditor will ask you to prove.

---

## 5. Provisioning, up close

Getting accounts into (and out of) apps is the "Administration" half of IGA. Four things to know:

- **SCIM** (System for Cross-domain Identity Management) — the **standard REST/JSON protocol** IGA/IdPs use to **create, update, and delete** users and groups in modern **SaaS** apps. When you hear "SCIM issue," it's a provisioning-to-a-SaaS-app problem (create/update/deprovision). Modern and clean.
- **Connectors / agents** — for **legacy / on-prem** targets that don't speak SCIM (Active Directory, mainframes, databases, old ERPs). The IGA tool ships adapters for these.
- **Automated vs request-based vs manual** — birthright/role-driven access is **automatic**; extra access is **requested**; a few stubborn systems still need a **manual** step (which auditors dislike).
- **Reconciliation** — IGA periodically compares *what's actually in the app* against *what it believes should be there*. Mismatches surface **rogue accounts** (created outside the process) and **orphans** (owner gone). This is how you catch access that sneaked in the back door.

---

## 6. Access certifications (reviews), up close

A **certification campaign** asks the right person to confirm each access is still appropriate. It's the control auditors care about most.

**Campaign types:**
| Type | Reviewer | Question |
|---|---|---|
| **User access review** | Line manager | "Should *this person* still have *these* accesses?" |
| **App-owner review** | Application owner | "Should all *these people* have access to *my app*?" |
| **Role review** | Role owner | "Does this *role* still contain the right entitlements?" |

**Closed-loop remediation (crucial):** when a reviewer clicks **revoke**, IGA must **actually deprovision** the access **and verify** it happened — not just log a note. **Open-loop** reviews (revoke = a comment nobody acts on) are worthless and fail audits.

**Beating "rubber-stamp" fatigue** (managers approving everything blindly):
- **Risk-based** — review high-risk access more often, low-risk rarely.
- **Micro-certifications** — small, event-driven reviews (e.g., right after a role change) instead of one giant annual slog.
- **Show usage data** — display "last used 14 months ago" so reviewers confidently revoke dead access.
- **Accountability** — reviewers sign off; blind approvals are themselves an audit flag.

---

## 7. Separation of Duties (SoD), up close

**SoD = no single person can complete a high-risk process alone.** It's a fraud control, and in a fintech it's front and center.

- **Toxic combination (the fintech classic):** the person who can **create/modify a payee (vendor)** must **not** also **approve/release payments** — otherwise they can pay themselves. Others: **submit *and* approve** expenses; a developer with **prod data access *and* deploy rights**.
- **Preventive vs detective:**
  - **Preventive SoD** — block the request that *would* create a violation (best; stops it before it exists).
  - **Detective SoD** — scan existing access and **flag** violations to fix (catches what's already there).
- **Mitigating controls** — when you genuinely can't split duties (e.g., a tiny team), add **compensating controls**: mandatory second approval, extra logging/monitoring, and after-the-fact review. Document them — auditors accept mitigations, not silence.

SoD is a core **SOX** control; deeper GRC treatment in [`08-grc-compliance`](../../08-grc-compliance/README.md) §5.

---

## 8. The vendors (so the names make sense)

| Vendor / tool | Where it fits |
|---|---|
| **SailPoint** (IdentityIQ / Identity Security Cloud) | The market leader; full IGA (lifecycle, certs, SoD, analytics) |
| **Saviynt** | Cloud-native IGA, strong on app/cloud governance |
| **Okta Identity Governance** | Adds reviews/requests on top of Okta |
| **Microsoft Entra ID Governance** | Native to Entra: **access reviews**, **entitlement management**, **lifecycle workflows** |
| **One Identity** (Identity Manager) | Established enterprise IGA |
| **Oracle / IBM** | Legacy enterprise identity governance |

> Ask early ([note 05](05-first-week-questions.md)): *"What's our IGA tool, which apps are onboarded to it, and which certification campaigns am I supporting?"*

---

## 9. Compliance — IGA is the SOX + PCI evidence machine

This is why IGA is funded, and why your work matters:

| IGA activity | The audit control it satisfies |
|---|---|
| **Access reviews / certifications** | **PCI Req 7.2.4** (review human access ≥ every 6 months); **SOX** access reviews |
| **Automated JML + deprovisioning** | **PCI Req 8.2.4/8.2.5** (manage lifecycle; **revoke terminated users**) |
| **SoD enforcement** | **SOX** segregation-of-duties control (fraud prevention) |
| **Provisioning records + reports** | Evidence that access is granted by process, with approval |

> **The finding that gets teams in trouble:** a **Leaver who still has access.** It violates PCI Req 8 *and* SOX, and it's exactly what an auditor hunts for. Clean deprovisioning is one of the highest-value things you do. Full mapping in [note 09](09-pci-dss-and-iam.md); auditor's view in [`08-grc-compliance`](../../08-grc-compliance/README.md) §5.

---

## 10. Risks & controls (repo rule: pair them)

| Risk | What goes wrong | IGA control |
|---|---|---|
| **Orphaned accounts** | Leaver's account stays live | Automated **JML** deprovisioning + **reconciliation** + reviews |
| **Entitlement creep** | Mover keeps old access forever | **Mover re-evaluation** + periodic certifications |
| **Rubber-stamped reviews** | Reviewers approve blindly | **Risk-based** + **micro-certs** + **usage data** |
| **SoD violation** | One person owns a risky process | **Preventive SoD** engine + mitigating controls |
| **Over-provisioning** | Bad/broad roles grant too much | **Role mining**, least privilege |
| **Rogue provisioning** | Access created outside the process | **Reconciliation** + **closed-loop** remediation |

More on these as vulnerabilities: [note 10 §8](10-iam-vulnerabilities.md).

---

## 11. Maturity journey & common pitfalls

**The journey most orgs walk:**
`manual tickets → automated provisioning → self-service requests → certifications → SoD → identity analytics / AI`

**Pitfalls to watch (and raise as smart questions):**
- **Garbage authoritative data** — if the HR feed is wrong, IGA automates the *wrong* access. Data quality first.
- **Role explosion** — thousands of over-specific roles become unmanageable. Mine and simplify.
- **Open-loop reviews** — "revoke" decisions that never actually happen. Insist on **closed-loop**.
- **Boiling the ocean** — trying to onboard every app at once. Start with the high-risk, in-scope (CDE/financial) apps.
- **Ignoring non-human identities** — service accounts need governance too (and [PAM](11-pam-deep-dive.md)).

---

## Hands-on hook

The cleanest beginner IGA lab (IAM README lab #8): a **self-contained access-review + SoD simulation**. Take a **CSV of users × entitlements**, then run a small script that (a) performs a **certification** — flagging access unused for >90 days — and (b) detects a **toxic SoD combination** (e.g., anyone holding both `payee-create` and `payment-approve`). It teaches the mechanics with zero infrastructure. Ask **Lefler** to build it (authorized-lab-only).

---

## What you learned

- **IGA governs identity**: lifecycle (JML), provisioning (SCIM/connectors), access requests, and **certifications** — plus **SoD** to stop fraud.
- It's distinct from Access Management (runtime) and PAM (privileged): IGA decides and **proves** *who should have what*.
- In a fintech, **your IGA work is the SOX/PCI evidence** — especially clean **deprovisioning** and honest **access reviews**.

## Next

- You've now covered the privileged and governance layers — pair this with [note 11 (PAM)](11-pam-deep-dive.md).
- Map it to your job: ask *"Which IGA tool do we run, which apps are onboarded, and which certification campaigns will I own?"* ([note 05](05-first-week-questions.md)).
- Optional build: ask **Lefler** for the access-review + SoD simulation above.

*— Janus 🔐 (to Lefler's Laws ⚙️)*
