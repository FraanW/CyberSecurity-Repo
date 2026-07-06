# Where PCI-DSS meets IAM — the compliance layer on your day job

> **Janus's curriculum with Tyr's compliance lens, written to [Lefler's Laws](../../LEFLER-LAWS.md).** At a payments company like FinCo, PCI-DSS is a big reason your IAM team exists and is funded. This note shows **exactly where PCI-DSS plugs into each IAM layer**, the end-to-end flow, and why your everyday provisioning/review/MFA work *is* the audit evidence. Prereqs: [note 01 (the map)](01-iam-protocol-landscape.md) and [note 07 (governance/Zero Trust)](07-iam-foundations.md). Deeper compliance: [`08-grc-compliance`](../../08-grc-compliance/README.md).

---

## The 30-second version (TL;DR)

- **PCI-DSS** is the rulebook for protecting **payment card data**.
- **IAM is how most of that rulebook actually gets enforced** — who can touch card data, how they prove who they are, and the proof that it's controlled.
- **Two of its twelve requirements are pure IAM:** **Requirement 7 = authorization** (least privilege), **Requirement 8 = authentication** (identity + MFA). A third, **Requirement 10 = accounting** (logging).
- **Your daily work generates the audit evidence:** provisioning, access reviews, MFA config, deprovisioning leavers, and PAM are exactly what an assessor asks to see.

> **One sentence to remember:** *PCI-DSS says "protect card data"; IAM is the machinery that answers "who's allowed near it, how do they prove it, and can we prove we controlled it?"*

---

## 1. What PCI-DSS is (plain English)

**PCI-DSS = Payment Card Industry Data Security Standard.** It's a security standard that any organization which **stores, processes, or transmits payment card data** must meet.

A few things beginners get wrong:
- **It's not a law.** It's a **contractual** requirement, enforced by the card brands (Visa, Mastercard, Amex, Discover, JCB) through your bank. Break it and you face fines or losing the ability to process cards — which for FinCo would be existential.
- **It's assessed regularly.** A large processor is assessed **every year** by a **QSA (Qualified Security Assessor)**, producing a **Report on Compliance (RoC)**. Smaller shops fill out a **Self-Assessment Questionnaire (SAQ)**.
- **Current version: v4.0.1** (the newest requirements became mandatory **March 31, 2025** — so they're in force now).

**Why it dominates a fintech:** FinCo moves card payments at massive scale, so its **card-data systems are huge** — and PCI-DSS is the standard those systems are measured against. IAM controls are the single biggest chunk of that measurement.

---

## 2. The one word that sets the scope: the CDE

Everything in PCI orbits the **CDE — Cardholder Data Environment**: the systems that **store, process, or transmit** cardholder data (the card number/PAN and related data), *plus* anything connected to them.

- **PCI's core question is an access question:** *who and what can reach the CDE, and can you prove it's tightly controlled?* That's IAM's whole job.
- **Segmentation shrinks scope.** If you wall off the CDE from the rest of the network (micro-segmentation — a Zero Trust idea from [note 07](07-iam-foundations.md)), fewer systems are "in scope," and there's less to protect and prove. **Reducing PCI scope is a real, funded goal** — and network + identity segmentation is how it's done.

---

## 3. The two requirements that ARE IAM (plus one)

PCI-DSS has 12 requirements. Three map straight onto the **AAA model** you already know ([note 01](01-iam-protocol-landscape.md)):

| PCI Requirement | Plain-English title | IAM concept | AAA |
|---|---|---|---|
| **Requirement 7** | Restrict access to card data **by business need-to-know** | **Authorization** — least privilege, RBAC, default-deny | **A**uthZ |
| **Requirement 8** | **Identify** users and **authenticate** access | **Authentication** — unique IDs, MFA, credential hygiene | **A**uthN |
| **Requirement 10** | **Log and monitor** all access to card data | **Accounting** — attributable audit trail | **A**ccounting |

> **The elegant link:** Requirement 8 forces **unique IDs** (no shared accounts). That uniqueness is what makes Requirement 10's **logs meaningful** — you can tie every action to a real person. AuthN feeds Accounting. That's not a coincidence; it's the AAA model showing up in a compliance standard.

---

## 4. How PCI-DSS maps onto the IAM layers ⭐ (the core answer)

Picture your IAM stack as layers. PCI-DSS drops a requirement onto almost every one:

```
        ┌─────────────────────────────────────────────────────────────┐
        │  TRANSPORT / CRYPTO   → encrypt card data in transit         │  Req 4 (TLS/mTLS)
        ├─────────────────────────────────────────────────────────────┤
        │  ACCOUNTING / AUDIT   → log every access to card data        │  Req 10
        ├─────────────────────────────────────────────────────────────┤
        │  GOVERNANCE / IGA     → review access every 6 months, SoD    │  Req 7.2.4
        ├─────────────────────────────────────────────────────────────┤
        │  SESSION              → idle timeout, re-authenticate        │  Req 8.2.8
        ├─────────────────────────────────────────────────────────────┤
        │  PRIVILEGED / PAM     → manage admin & service-account creds │  Req 7.2.5, 8.6
        ├─────────────────────────────────────────────────────────────┤
        │  AUTHORIZATION        → least privilege, need-to-know, RBAC  │  Req 7.2, 7.3
        ├─────────────────────────────────────────────────────────────┤
        │  AUTHENTICATION       → MFA into the CDE, strong credentials │  Req 8.3, 8.4, 8.5
        ├─────────────────────────────────────────────────────────────┤
        │  IDENTITY / DIRECTORY → unique IDs, remove leavers promptly  │  Req 8.2
        └─────────────────────────────────────────────────────────────┘
                         every layer of IAM is a PCI control
```

The same thing as a table you can act on:

| IAM layer | What you do | PCI requirement | Note to go deeper |
|---|---|---|---|
| **Identity / Directory** | Give every human & machine a **unique ID**; remove terminated users fast | **8.2.1** (unique ID), **8.2.4/8.2.5** (lifecycle, revoke leavers) | [04](04-ldap-ad-entra.md), [07](07-iam-foundations.md) |
| **Authentication** | **MFA for all access into the CDE**; strong passwords/passphrases | **8.3** (strong auth), **8.4** (MFA into CDE — incl. non-admins), **8.5** (MFA can't be replayed/bypassed) | [07](07-iam-foundations.md) |
| **Authorization** | **Least privilege**, need-to-know, role-based, **default-deny** | **7.2** (least privilege/RBAC), **7.3** (access-control system enforces) | [07](07-iam-foundations.md) |
| **Privileged / PAM** | Manage admin + **service/application account** credentials; no hard-coded secrets; MFA for admins | **7.2.5**, **8.6** (app/system accounts), **8.4.1** (MFA remote admin) | [07 §PAM](07-iam-foundations.md) |
| **Session** | **Idle timeout** — re-authenticate after inactivity | **8.2.8** (15-minute idle timeout) | [07 §sessions](07-iam-foundations.md) |
| **Governance / IGA** | **Review human access every 6 months**; app/system accounts by risk; enforce SoD | **7.2.4** (periodic access review), **7.2.5** | [07 §IGA](07-iam-foundations.md) |
| **Accounting / Audit** | **Log every access** to card data, tied to a unique user; monitor | **Requirement 10** | SIEM → [`06-...blue-team`](../../06-security-operations-blue-team/README.md) |
| **Transport / Crypto** | **Encrypt card data in transit** (TLS; mTLS between services) | **Requirement 4** | [06 (TLS/mTLS)](06-tls-https-mtls.md) |

---

## 5. The flow — PCI in IAM, end to end

Here's the whole thing as a lifecycle. Follow one employee (or one service) that needs to touch the CDE:

1. **Scope it.** Identify the CDE and what connects to it. *(Segmentation keeps this small.)*
2. **Give a unique identity.** Every user and service gets its **own ID** — never a shared "admin" login. *(Req 8.2)*
3. **Authenticate strongly.** Logging in to anything in the CDE requires **MFA** + strong credentials. *(Req 8.3–8.5)*
4. **Authorize by need-to-know.** Grant the **least** access required, via roles, **default-deny**. A teller ≠ a DBA. *(Req 7.2–7.3)*
5. **Handle privilege carefully.** Admin and **service-account** access is vaulted, rotated, MFA-protected, ideally **just-in-time**. *(Req 7.2.5, 8.6)*
6. **Keep sessions tight.** Idle sessions time out and force re-auth. *(Req 8.2.8)*
7. **Govern over time.** **Every 6 months**, review who has access and **remove what's no longer needed**; enforce Separation of Duties. *(Req 7.2.4)*
8. **Log everything.** Every access to card data is **logged and attributable** to a unique user, and monitored. *(Req 10)*
9. **Encrypt in transit.** Card data moving between systems is protected with **TLS/mTLS**. *(Req 4)*
10. **Offboard cleanly.** When the person leaves or the service is retired, access is **revoked promptly** — a broken step here is a classic audit failure. *(Req 8.2.5)*

**That list *is* the IAM lifecycle (JML + AAA + governance).** PCI-DSS didn't invent new machinery — it put **mandatory teeth** on the IAM controls you're already learning.

---

## 6. Why you care: your day job *is* the evidence

This is the mindset shift that makes you valuable. In a PCI assessment, the QSA doesn't want theory — they want **proof the controls work**. That proof is produced by **your routine IAM tasks**:

| Your task | The PCI evidence it produces |
|---|---|
| Running a quarterly/biannual **access review** | Req **7.2.4** evidence (access is still need-to-know) |
| **Deprovisioning a leaver** promptly | Req **8.2.5** evidence (terminated access revoked) |
| Configuring/proving **MFA** on CDE apps | Req **8.4** evidence (MFA into the CDE) |
| **PAM** vaulting + session recording for admins | Req **7/8/10** evidence (privileged access controlled + logged) |
| Showing there are **no shared/generic accounts** | Req **8.2.1** evidence (unique IDs) |
| Exporting **access logs** tied to user IDs | Req **10** evidence (attributable audit trail) |

> **Reframe every "boring" task:** an access review isn't bureaucracy — it's you generating **Requirement 7.2.4 evidence** that keeps FinCo able to process cards. Say that in a standup and you'll sound like you get the big picture.

---

## 7. Attacks & defenses — why these controls exist (repo rule: pair them)

PCI's IAM requirements are countermeasures to how card data actually gets stolen:

| How breaches happen | The IAM control that stops it |
|---|---|
| **Stolen/reused credentials** walk into the CDE | **MFA** into the CDE (Req 8.4) — a password alone isn't enough |
| **Over-privileged** account is compromised → reaches card data | **Least privilege / need-to-know** (Req 7) shrinks the blast radius |
| **Shared/generic accounts** → can't tell who did what | **Unique IDs** (Req 8.2) + **logging** (Req 10) = attribution |
| **Stale access** (a leaver, a forgotten contractor) | **Prompt deprovisioning** + **access reviews** (Req 8.2.5, 7.2.4) |
| **Service account with a hard-coded/static secret** | **App/system account management** (Req 8.6) → PAM/secrets rotation |
| **Sniffing card data on the wire** | **Encrypt in transit** (Req 4) → TLS/mTLS ([note 06](06-tls-https-mtls.md)) |
| **Flat network** → one foothold reaches the CDE | **Segmentation** (Zero Trust) shrinks scope and contains breaches |

> Real-world pattern: major card breaches have started with **third-party/vendor access + over-privilege + weak segmentation**, then **lateral movement** to the CDE. Every link in that chain is an IAM control PCI mandates. Ask **Heimdall** what the SOC would detect (anomalous CDE access, failed MFA spikes, new privileged logins) and **Loki** to model the attacker path *in a lab only*.

---

## 8. Common findings & gotchas (what fails an assessment)

- **Shared/generic accounts** (a `admin`/`svc` login several people use) → violates unique-ID **8.2.1**. Very common finding.
- **A CDE access path with no MFA** → violates **8.4**. Major finding under v4.0.
- **Service accounts with static, hard-coded passwords** → violates **8.6**. Fix with PAM/secret rotation.
- **Stale access** — a leaver still enabled, or access never removed after a role change (**entitlement creep**) → violates **7/8** and shows up in the **access review**.
- **Over-broad roles** (everyone's a domain admin) → violates least privilege **7.2**.
- **Scope creep** — an unsegmented system that touches the CDE pulls *more* systems into scope. Segment to reduce it.

---

## 9. PCI vs SOX (quick, because you'll hear both)

Both are driven by the **same IAM controls** (provisioning, MFA, least privilege, access reviews, SoD, logging) — they just protect different things:

| | **PCI-DSS** | **SOX (ITGC)** |
|---|---|---|
| Protects | **Cardholder data** | **Financial-reporting integrity** |
| Enforced by | Card brands (contractual) | Securities law (regulatory) |
| Scope | The **CDE** | Financial systems |
| Shared IAM controls | Unique IDs, MFA, least privilege, access reviews, SoD, logging | *(the same)* |

So a single well-run IAM program produces evidence for **both**. Deeper treatment lives in [`08-grc-compliance`](../../08-grc-compliance/README.md) §5.

---

## What you learned

- PCI-DSS is the **contractual rulebook** for card data; **IAM enforces most of it.**
- **Req 7 = authorization, Req 8 = authentication, Req 10 = accounting** — the AAA model as compliance.
- It maps onto **every IAM layer** (identity → authN → authZ → privileged → session → governance → audit → transport).
- **Your routine IAM work is the audit evidence** — that's the reframe that makes you valuable.

## Next

- Skim [`08-grc-compliance`](../../08-grc-compliance/README.md) §5 for the auditor's-eye view (RoC, SAQ, control mapping) — that's **Tyr's** domain.
- Tie it to your stack: ask *"Which of our IAM systems are in PCI scope, and who owns the evidence for Req 7 & 8?"* ([note 05](05-first-week-questions.md)).
- Related: IAM-specific vulnerabilities and the OWASP mapping (coming next).

### Sources
- [PCI-DSS v4.0.1 standard (PCI SSC)](https://www.middlebury.edu/sites/default/files/2025-01/PCI-DSS-v4_0_1.pdf) · [MFA changes in v4.0/4.0.1 (HYPR)](https://www.hypr.com/blog/pci-dss-4.0.1-what-changed-and-how-is-this-the-next-step-for-universal-mfa) · [v4.0 requirements guide (Linford)](https://linfordco.com/blog/pci-dss-4-0-requirements-guide/)

*— Janus 🔐 × Tyr ⚖️ (to Lefler's Laws ⚙️)*
