# Privileged Access Management (PAM) — deep dive

> **Janus's deep dive, written to [Lefler's Laws](../../LEFLER-LAWS.md).** PAM protects the **most dangerous accounts in the company** — the admin and machine credentials that, if stolen, hand an attacker the keys to everything. In a fintech, that means admin access to payment and core-banking systems: the single highest-stakes IAM control. Prereqs: [note 07 §PAM](07-iam-foundations.md), [note 04 (service accounts, tiered admin)](04-ldap-ad-entra.md). Compliance angle: [note 09 (PCI)](09-pci-dss-and-iam.md).

---

## The 30-second version (TL;DR)

- **Privileged accounts = keys to the kingdom** (admins, DBAs, service accounts, secrets). Attackers hunt them because one gives access to *everything*.
- **PAM** secures them with a handful of moves: **vault** the credential, **rotate** it, **record** the session, and grant it **just-in-time** instead of 24/7.
- **The goal:** no human knows the admin password, no privilege sits idle waiting to be stolen, and every privileged action is recorded.

> **Analogy:** privileged credentials are the **master keys to the bank vault**. You don't hand out copies. You keep them in a **monitored key cabinet** (the vault), sign one out for a **specific job**, have a **guard watch** you use it (session recording), and **change the lock afterward** (rotation). Best of all — a supervised escort opens the door *for* you, so you never actually hold the key (credential injection).

---

## 1. What counts as "privileged" (the account zoo)

Privilege hides in more places than beginners expect. PAM covers all of it:

| Type | Examples | Why it's dangerous |
|---|---|---|
| **Human admin accounts** | Domain Admin, root, local admin, DBA, network-device admin | Can change/destroy systems and cover tracks |
| **Cloud admin** | AWS root, Entra **Global Administrator**, GCP owner | Controls the entire cloud tenant |
| **Service accounts** | Accounts that run services/scheduled tasks | Often over-privileged, rarely rotated, easy to forget |
| **Application accounts** | App-to-app / API credentials, DB connection strings | Frequently **hard-coded** in code/config |
| **Secrets** | API keys, client secrets, SSH keys, certificates, tokens | Leak in repos, logs, images |
| **Break-glass / emergency** | The "in case of disaster" super-admin | Powerful, tempting to abuse, must be tightly watched |

> **Key insight:** the majority of privileged accounts are **non-human** (service/app accounts + secrets). They outnumber human admins many-to-one and are the most commonly forgotten — which is exactly why they get exploited.

---

## 2. The problem PAM solves

Without PAM, a typical org has: **shared** admin passwords in a spreadsheet or vault-of-sticky-notes, **standing** privilege (admins hold rights 24/7), **no accountability** (who used the shared `admin` account?), and **static** service-account passwords that never change.

That's a gift to attackers. The classic breach path:

```
 phish an employee → steal a credential → find an OVER-PRIVILEGED or
 STANDING admin/service account → move laterally → reach the crown jewels
 (Domain Controller / payment system) → game over
```

**Almost every major breach involves privileged-credential abuse somewhere in the chain.** PAM breaks the chain by making privileged credentials hard to steal, useless if stolen (rotation), and impossible to use unseen (recording).

---

## 3. The PAM pillars (what a PAM program actually does)

| Pillar | What it does | Why it matters |
|---|---|---|
| **1. Credential vaulting** | Store privileged creds in an encrypted **vault**; check out / check in | No admin *knows* the password; it's not on sticky notes or in scripts |
| **2. Rotation** | Automatically change the password **after each use** or on a schedule | A stolen credential is useless minutes later; kills reuse/pass-the-hash |
| **3. Session management + recording** | **Proxy** the privileged session, **record** it (video/keystrokes), monitor live, kill suspicious sessions | Full accountability + audit evidence; deters insider misuse |
| **4. Session isolation / credential injection** | The PAM proxy connects to the target; the admin **never sees the password** | Even a compromised admin workstation can't leak the credential |
| **5. Just-in-Time (JIT) access** | Grant elevated rights only for a **time-boxed window**, on request/approval | No standing privilege sitting idle to be stolen |
| **6. Least privilege / PEDM** | **Endpoint Privilege Management** — remove local admin; elevate specific commands/apps instead | Users get work done without full admin rights |
| **7. Secrets management** | Vault + rotate **machine** secrets (API keys, app-to-app passwords); eliminate hard-coding | Removes the #1 non-human privilege risk |
| **8. Discovery** | Continuously **find** privileged accounts (they hide everywhere) | You can't protect what you don't know exists |
| **9. MFA + approval workflows** | Require MFA and approval before privileged access | Stops stolen-password-alone access ([PCI 8.4.1](09-pci-dss-and-iam.md)) |
| **10. Audit & monitoring** | Full, attributable log of every privileged action | SOX/PCI evidence; feeds the SOC (**Heimdall**) |

Gartner groups most of these into three buckets you'll hear named: **PASM** (Privileged Account & **Session** Management — pillars 1–5, 9), **PEDM** (Privilege **Elevation** & Delegation Mgmt — pillar 6), and **Secrets Management** (pillar 7).

---

## 4. The PAM workflow (how it flows, end to end)

Follow an admin who needs to patch a payment database server:

```
 1. Admin requests access to "PaymentsDB-Prod" in the PAM portal.
 2. Approval + MFA  →  (JIT: access granted for 2 hours only).
 3. PAM VAULT checks out the credential and INJECTS it — the admin
    never sees the password.
 4. The session is PROXIED through the PAM server and RECORDED
    (keystrokes + video). Live monitoring can terminate it.
 5. Admin does the work.
 6. Session ends → PAM ROTATES the password → JIT rights REVOKED.
 7. Everything is LOGGED — a full audit trail for the QSA / SOX auditor.
```

Notice what the admin *never* does: know the password, hold standing rights, or act unobserved. That's the whole point.

---

## 5. Just-in-Time & Zero Standing Privilege (the modern direction)

- **Standing privilege** = an account holds admin rights **all the time**. That's a permanent, idle target.
- **Just-in-Time (JIT)** = rights are granted **only when needed**, for a short window, then removed.
- **Zero Standing Privilege (ZSP)** = the end goal: **no account has standing admin rights at all**; every elevation is JIT and temporary.

> Why it's powerful: if privilege only exists for 2 hours a week per admin, the window an attacker can steal-and-use it shrinks from "always" to "almost never." **Microsoft Entra PIM** (Privileged Identity Management) is the common cloud implementation — admins "activate" a role for a few hours with MFA + justification.

---

## 6. The tiered admin model (protect the crown)

Not all admin is equal. The **tiered model** (from Active Directory security, [note 04](04-ldap-ad-entra.md)) separates privilege so a breach in one tier can't reach the top:

- **Tier 0** — identity infrastructure: Domain Controllers, the PAM vault itself, cloud tenant admins. *Compromise here = total control.* Guarded hardest.
- **Tier 1** — servers and applications (e.g., the payments app servers).
- **Tier 2** — user workstations and devices.

**The rule: credentials from a higher tier are never exposed on a lower tier.** A Tier 0 admin never logs into a Tier 2 laptop (where malware could grab the credential). PAM enforces these boundaries.

---

## 7. Machine identity & secrets (the non-human half)

The fastest-growing PAM problem is **machines**, not people:

- **Service/application accounts** should use **managed** credentials — e.g., Windows **gMSA** (group Managed Service Accounts) rotate automatically and have no human-known password.
- **No hard-coded secrets.** API keys and app-to-app passwords in code/config are a top vulnerability ([note 10 §8](10-iam-vulnerabilities.md)). Replace them with a secrets manager the app calls at runtime.
- **Dynamic / short-lived secrets** are even better: the secrets manager issues a **fresh, expiring** credential per request (nothing long-lived to steal). This is the same idea as short-lived certs in **mTLS** ([note 06](06-tls-https-mtls.md)) — machine identity done right.

---

## 8. The vendors (so the names make sense)

| Vendor / tool | Where it fits |
|---|---|
| **CyberArk** | The market-leading full PAM suite (vault, session mgmt, secrets via Conjur) |
| **BeyondTrust**, **Delinea** (Thycotic + Centrify) | Major full-PAM competitors |
| **HashiCorp Vault** | Secrets management + dynamic secrets (huge in DevOps/cloud) |
| **Microsoft Entra PIM** | JIT elevation for Entra/Azure roles (cloud, part of Entra) |
| **AWS IAM roles + STS** | Cloud-native: short-lived credentials instead of long-lived keys |
| **Teleport**, **Devolutions** | Access proxy + session recording (good for labs) |

> Ask early ([note 05](05-first-week-questions.md)): *"What's our PAM tool, and what's in scope — just human admins, or service accounts and secrets too?"*

---

## 9. Attacks & defenses (repo rule: pair them)

| Attack | What it does | PAM defense |
|---|---|---|
| **Pass-the-Hash / credential theft** | Reuse a stolen admin credential | **Rotation** + **session isolation** (admin never holds it) |
| **Kerberoasting** | Crack weak **service-account** passwords offline | PAM-managed strong passwords / **gMSA** |
| **Hard-coded secrets in code** | Attacker reads a repo/config and gets a live credential | **Secrets management**, dynamic secrets, secret scanning |
| **Standing-privilege abuse** | Idle admin rights get hijacked | **JIT / Zero Standing Privilege** |
| **Shared admin account** | No idea who did what | **Vault check-out** + **session recording** = individual accountability |
| **Tier 0 / Golden Ticket** | Steal a Domain Controller credential → forge access to all | **Tiered admin** + PAM around DCs |
| **PAM bypass** (local admin still present) | User sidesteps PAM using retained local rights | **PEDM** removes local admin; enforce all admin via PAM |

Purple-team: **Loki** demonstrates credential theft in a lab; **Heimdall** builds detections (privileged logon anomalies, check-outs at odd hours, session-recording gaps).

---

## 10. Compliance & the audit evidence PAM produces

PAM is a compliance goldmine — it directly satisfies:
- **PCI-DSS** Req **7.2.5** (app/system account access), **8.6** (managing app/system account credentials), **8.4.1** (MFA for admin), **10** (logging privileged access). See [note 09](09-pci-dss-and-iam.md).
- **SOX ITGC** — privileged-access controls over financial systems.

The evidence a QSA/auditor loves: **session recordings**, **check-out logs**, **rotation records**, proof there are **no shared admin accounts**. Your PAM work *is* that evidence.

---

## 11. Maturity journey & common pitfalls

**The journey most orgs walk:**
`shared passwords in a spreadsheet → vault → auto-rotation → session recording → JIT → Zero Standing Privilege → secrets management for machines`

**Pitfalls to watch (and to raise as smart questions):**
- **Incomplete discovery** — unmanaged privileged/service accounts are the ones that get exploited. "How do we find privileged accounts we don't know about?"
- **Service accounts left out** — teams vault human admins but forget machines.
- **Protect the vault itself** — the PAM system is **Tier 0**; if it falls, everything falls. It's a single point of trust, guarded accordingly.
- **Users bypassing PAM** — if there's an easier back door (retained local admin), people use it. Close the back doors.
- **Break-glass abuse** — emergency accounts must be alarmed and reviewed after every use.

---

## Hands-on hook

The cleanest beginner PAM lab (IAM README lab #9): stand up **HashiCorp Vault** (Docker) and **vault a secret, read it via a short-lived dynamic credential, and rotate it** — or use **Teleport** to proxy and **record an SSH session**. Ask **Lefler** to build either; both run locally and are disposable (authorized-lab-only).

---

## What you learned

- **Privileged accounts are the top target**; most are **non-human** (service accounts + secrets).
- PAM's moves: **vault, rotate, record, isolate, and grant just-in-time** — ideally toward **Zero Standing Privilege**.
- It's the **highest-stakes IAM control** in a fintech and a **compliance evidence machine** (PCI/SOX).

## Next

- Pair it with governance: [note 12 — IGA deep dive](12-iga-deep-dive.md) (who *should* have access — including who should have privilege).
- Tie to your stack: ask *"What's in our PAM vault today, and are service accounts and secrets covered?"* ([note 05](05-first-week-questions.md)).

*— Janus 🔐 (to Lefler's Laws ⚙️)*
