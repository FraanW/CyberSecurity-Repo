# LDAP, Active Directory & Microsoft Entra ID — the directory layer

> **Janus's deep dive.** Under every IdP, every SAML assertion, every OIDC token, there's a **directory** — the source of truth for *who exists* and *what groups they're in*. In a Microsoft-heavy fintech, that's **Active Directory** on-prem and **Entra ID** in the cloud, synced together. Most "why can't this person log in / why do they still have access" tickets bottom out here. Prereq: [the landscape note](01-iam-protocol-landscape.md).

---

## 1. Why directories exist

Authentication asks "who are you?" — but *against what*? You need a **database of identities** to check the password and read the user's attributes and group memberships. That database is the **directory**. It's optimized for **read-heavy, hierarchical** data ("look up a user," "list a group's members") rather than transactions.

Think of it as the organization's **phone book**: structured, searchable, mostly-read, the authoritative record of every person, group, service account, computer, and printer.

---

## 2. LDAP — the protocol and the data model

**LDAP (Lightweight Directory Access Protocol)** is both:
1. a **wire protocol** to query/modify a directory, and
2. a **data model** for how entries are organized.

### The tree (DIT — Directory Information Tree)
Everything is a hierarchy of **entries**, each with a unique **Distinguished Name (DN)** — like a file path, but read right-to-left (most specific first):

```
                      dc=finco,dc=com                 ← the root (domain components)
                       /            \
              ou=People           ou=Groups            ← organizational units (folders)
              /       \                |
   cn=Farhaan   cn=Service-App    cn=iam-team          ← entries (leaf objects)
```

A DN for Farhaan: **`cn=Farhaan,ou=People,dc=finco,dc=com`**
- **DN** = the full path (globally unique).
- **RDN** (Relative DN) = just this level's piece: `cn=Farhaan`.
- **dc** = domain component, **ou** = organizational unit, **cn** = common name. These are *attribute types*.

Each entry has **attributes** (`mail`, `memberOf`, `sAMAccountName`, `userPassword`) and one or more **objectClasses** (`person`, `organizationalPerson`, `user`) that define which attributes it may hold — like a schema/type.

### The operations you'll actually name
- **Bind** — authenticate to the directory. *Simple bind* = DN + password (must be over **TLS/LDAPS** or the password is in cleartext). *SASL bind* = stronger mechanisms (e.g., Kerberos).
- **Search** — the workhorse. Needs a **base DN** (where to start), a **scope** (base / one-level / subtree), and a **filter**.
- Add / Modify / Delete / Compare — write ops.

### An LDAP search filter (you'll read these in configs)
```
(&(objectClass=user)(memberOf=cn=iam-team,ou=Groups,dc=finco,dc=com)(!(userAccountControl=514)))
```
Reads as: *AND(* is a user *AND* member of iam-team *AND NOT* disabled *)*. `514` is the AD flag for a disabled account. Filters use prefix notation: `&`=AND, `|`=OR, `!`=NOT.

### Ports (handy for firewall/connectivity tickets)
| Port | Meaning |
|---|---|
| **389** | LDAP (plaintext or StartTLS) |
| **636** | **LDAPS** (LDAP over TLS) |
| **3268 / 3269** | AD **Global Catalog** (forest-wide search) / GC over TLS |

---

## 3. "Authenticating against LDAP" — and its risks

Many apps authenticate users by doing an **LDAP bind**: take the username + password the user typed, try to *bind* to the directory as that user; if the bind succeeds, the password was correct. Simple and common.

**Pair attacks with defenses (CLAUDE.md rule):**

| Risk | Problem | Defense |
|---|---|---|
| **Cleartext bind** | Simple bind without TLS sends the password in the clear | Enforce **LDAPS/StartTLS**; disable plaintext bind |
| **Anonymous bind** | Directory allows unauthenticated reads → info disclosure | Disable anonymous bind; restrict read ACLs |
| **LDAP injection** | App builds a filter from user input unsanitized → attacker alters the query (auth bypass, data exfil) | Escape special chars (`* ( ) \ NUL`), parameterize, allow-list input — see [../../03-application-security/README.md](../../03-application-security/README.md) §1 (injection family) |
| **Unlimited bind attempts** | Password brute force / spraying | Lockout policies, throttling, monitor failed binds ([../../06-security-operations-blue-team/README.md](../../06-security-operations-blue-team/README.md)) |

---

## 4. Active Directory — Microsoft's directory (LDAP + Kerberos + more)

**Active Directory Domain Services (AD DS)** is Microsoft's on-prem directory. It *speaks LDAP* for lookups, but it's much more than LDAP — it bundles:
- **Kerberos** + **NTLM** for authentication (see §5),
- **DNS** for locating services,
- **Group Policy (GPO)** for pushing configuration/security settings to machines,
- a replication system across **Domain Controllers (DCs)**.

Structure vocabulary:
- **Domain** — an administrative + security boundary (`finco.com`).
- **Forest** — one or more domains sharing a schema and trust (the top boundary).
- **OU (Organizational Unit)** — folders for organizing objects and targeting GPOs.
- **Security groups** — the containers that drive **authorization** (membership → access). *Group sprawl and nested groups are a governance headache and an audit finding waiting to happen.*
- **Domain Controller (DC)** — a server hosting AD; holds the directory + runs the **KDC** (Kerberos).
- **Service accounts** — non-human accounts running apps/services; over-privileged ones are a top attack path.

> **Why fintech obsesses over AD:** AD is the **crown-jewel** identity store. Compromise a Domain Controller and you effectively own every identity in the company. That's why banks use a **tiered admin model** (Tier 0 = identity/DC admins, isolated) and heavy **PAM** around domain admin accounts.

---

## 5. Kerberos & NTLM — how AD authenticates (the 90-second version)

**Kerberos** (the default in AD) is a ticket-based protocol. The **hotel analogy**:
- You check in at the **front desk** (the **KDC / Authentication Service**) with your ID → you get a **wristband** that proves "the hotel vouches for this guest": the **TGT (Ticket-Granting Ticket)**.
- To use the **pool** (a specific service), you show the wristband to the desk and get a **pool-specific key card**: a **service ticket (TGS)**.
- The pool (service) checks the key card — it never sees your password. Fast, and passwords don't fly around repeatedly.

```
 User ---(1. request, proves identity)---> KDC/AS
 User <--(2. TGT: "we vouch for you")----- KDC
 User ---(3. TGT + "I want fileserver")--> KDC/TGS
 User <--(4. service ticket for fileserver)
 User ---(5. service ticket)-------------> Fileserver  → access granted
```

**NTLM** is the older challenge-response mechanism, still lurking for legacy compatibility. It's weaker (no mutual auth, replayable hashes) and Microsoft is actively working to retire it.

**Attacks (defensive awareness only — these belong in your local lab, never production):**
- **Kerberoasting** — request service tickets for accounts with weak passwords, crack them offline. *Defense:* strong/managed service-account passwords (gMSA), monitor unusual TGS requests.
- **Pass-the-Hash** — reuse a stolen NTLM hash without knowing the password. *Defense:* limit NTLM, credential guard, tiered admin.
- **Golden Ticket** — forge TGTs after stealing the `krbtgt` key = total domain compromise. *Defense:* protect DCs, rotate `krbtgt`, detect anomalies.

> These attack techniques are catalogued in the IAM README §5 and are **red-team lab material** — ask **Loki** to demonstrate on your own lab VMs and **Heimdall** what the SOC would detect. Never run these against FinCo or any production system without written authorization.

---

## 6. Microsoft Entra ID — the cloud identity platform (NOT "AD in the cloud")

**Entra ID** (formerly **Azure AD**) is Microsoft's **cloud** identity service. A crucial misconception to kill early:

> **Entra ID is NOT Active Directory hosted in the cloud.** It's a different animal. AD uses LDAP + Kerberos + GPO for on-prem, domain-joined machines. Entra ID is built for the **internet**: it speaks **OIDC, OAuth 2.0, SAML, SCIM** over HTTPS/REST. It has **no LDAP or Kerberos** natively and no OUs/GPOs. Its job is to be the **IdP for SaaS and modern apps**.

Entra vocabulary:
- **Tenant** — your organization's dedicated Entra instance.
- **Users / Groups** — cloud identities (may be synced from on-prem AD).
- **App registration** — an app that uses Entra for OIDC/OAuth (your app is the *client*).
- **Enterprise application** — a SaaS app federated to Entra (often via SAML/OIDC), plus SCIM provisioning.
- **Conditional Access** — the **policy engine**: "require MFA if sign-in risk is high / from an unmanaged device / outside the corporate network." **This is where Zero Trust is actually implemented.** (See Zero Trust in [../../02-network-security/README.md](../../02-network-security/README.md) §12 and NIST SP 800-207.)

### Hybrid identity — the picture in almost every enterprise
Most companies aren't cloud-only; they run **both** and **sync** them:

```
   ON-PREM                         CLOUD
 ┌───────────┐   Entra Connect   ┌────────────┐
 │ Active    │ ───(sync users,──▶│  Entra ID  │──▶ SaaS apps (SAML/OIDC)
 │ Directory │    groups, hashes)│  (tenant)  │──▶ Microsoft 365
 │ (DCs,     │◀── writeback ─────│            │──▶ Conditional Access (Zero Trust)
 │  Kerberos)│                   └────────────┘
 └───────────┘
   domain-joined PCs, file servers, legacy apps
```

- **Entra Connect** (formerly Azure AD Connect) syncs on-prem AD → Entra: users, groups, and (with **Password Hash Sync** or **Pass-through Authentication**) the ability to sign in to cloud apps with the same password.
- **This sync is the source of a huge share of tickets:** "I changed my password on-prem but can't log into a cloud app" (sync lag), "user disabled in AD but still active in Entra" (sync failure — a *serious* Leaver/audit problem), "group membership not reflecting in the app" (sync + SCIM).

---

## 7. How directories back authentication & authorization

Tie it to the login flow from [note 01](01-iam-protocol-landscape.md) §6:
1. IdP (Entra/Okta/Keycloak) prompts for password + MFA.
2. IdP **validates the password against the directory** (Entra natively, or on-prem AD via PTA/PHS, or LDAP bind).
3. IdP reads **group memberships / attributes** from the directory → these become the **claims/attributes** in the SAML assertion or OIDC token → which the app turns into **authorization** (what you can do).

So: **directory = who you are + what groups you're in; the IdP packages that into a token; the app enforces access from it.** A stale directory = wrong access. That's why **IGA** (governance) constantly reconciles the directory against reality (access reviews, JML).

---

## 8. Directory tickets you'll actually see

| Symptom | Likely directory cause | Where to look |
|---|---|---|
| Can't log in, "account locked" | Lockout policy after failed binds/attempts | AD lockout status, failed-auth logs |
| Password changed, cloud app rejects it | Entra Connect **sync lag** or PHS/PTA issue | Entra Connect sync status |
| Logged in but missing access | Wrong/stale **group membership** or attribute | `memberOf`, group sync, SCIM mapping |
| Ex-employee still has access | **Leaver** not deprovisioned / sync didn't disable | JML process, sync health — *audit red flag* |
| App can't find users | Wrong **base DN** / search filter / GC vs DC | LDAP config, connectivity to 389/636/3268 |

---

## 9. Tools

- **`ldapsearch`** (Linux) / **`dsquery`, `ldp.exe`, ADSI Edit** (Windows) — query the directory directly.
- **PowerShell `ActiveDirectory` module** (`Get-ADUser`, `Get-ADGroupMember`) — day-to-day AD admin.
- **Apache Directory Studio** — GUI LDAP browser (great for learning the tree visually).
- **Entra admin center** / **Microsoft Graph** — cloud identity management and scripting.

**Hands-on hook:** your **Keycloak** lab ([Lab 01](../labs/01-keycloak-idp/README.md)) can **federate to an LDAP directory** — that's the cleanest way to *see* an IdP reading users from a directory. A dedicated LDAP/Samba-AD lab (IAM README lab #5) is a great next build — ask **Lefler**.

---

## 10. The mental model to keep

- **LDAP** = the protocol + tree model for directories.
- **Active Directory** = Microsoft's on-prem directory (LDAP + Kerberos + GPO); the crown jewel.
- **Entra ID** = Microsoft's cloud IdP (OIDC/SAML/SCIM, *no* LDAP/Kerberos); where Conditional Access / Zero Trust lives.
- **Entra Connect** = the sync bridge, and the source of most hybrid-identity tickets.

Next: turn all of this into sharp questions for your team → [note 05](05-first-week-questions.md).

*— Janus 🔐*
