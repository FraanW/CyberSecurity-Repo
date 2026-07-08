# Kerberos — passwordless authentication for legacy systems

> **Janus's deep dive.** This captures the Kerberos session from your last study block — "full everything with examples and scenarios." Kerberos is the quiet engine under every Windows domain login. You rarely see it, but at a fintech like **FinCo** it's authenticating you into legacy intranet apps all day long, and it's the thing PAM and red teams fight over.
>
> **Prereqs:** [LDAP / AD / Entra](04-ldap-ad-entra.md) (the directory Kerberos lives inside) and a rough feel for symmetric encryption (a shared secret key locks and unlocks the same box). Related later: [PAM deep dive](11-pam-deep-dive.md) (service accounts and `krbtgt`).

---

## TL;DR (the whole note in seven lines)

1. **The 1980s problem:** an untrusted campus network, no TLS, and you must NOT send your password over the wire. Kerberos solves it: prove you know the password *without ever transmitting it*.
2. **Three-headed dog** (Cerberus) = the three parties it guards between: **client**, **KDC** (the trusted middle), and **service**.
3. The **KDC** (Key Distribution Center) is two services in one: the **AS** (Authentication Service, "who are you?") and the **TGS** (Ticket Granting Service, "here's a pass for that app").
4. **Three exchanges:** **AS-REQ/REP** → get a **TGT** (your "master pass"). **TGS-REQ/REP** → trade the TGT for a **service ticket** for one specific app. **AP-REQ/REP** → show the service ticket to the app.
5. **The key insight:** your password never crosses the network. You encrypt a **timestamp** with a key *derived* from your password; the KDC decrypts it to check you. That's **pre-authentication**.
6. **Why "passwordless":** (a) the password never travels — only cryptographic proof; (b) after the morning logon, **tickets** silently do every later login = seamless SSO inside the domain. True passwordless entry (smart card / Windows Hello) uses **PKINIT** — a certificate, no password at all.
7. **The attacks all target the same weak spots:** crackable service-account passwords (**Kerberoasting**), the `krbtgt` master key (**Golden Ticket**), and stealing tickets from memory (**Pass-the-Ticket**). Every one has a defense.

---

## 1. The problem Kerberos was built to solve

Rewind to **MIT, mid-1980s, Project Athena.** Thousands of shared workstations, one campus network, and a hard rule: **the network is hostile — assume someone is sniffing every cable.** There was no HTTPS, no TLS. So the naive answer ("send username + password to the server") was a non-starter: anyone tapping the wire would harvest passwords all day.

The team needed a way to prove *"I know Farhaan's password"* to a file server **without the password (or even its hash) ever crossing the wire**, and to do it once so you weren't re-typing it into every service.

Their answer: a **trusted third party** that everyone already shares a secret with, plus short-lived encrypted **tickets**. They named it **Kerberos** after the three-headed dog guarding the gates of the underworld — because the protocol stands between **three** parties:

| Head of the dog | In Kerberos | Plain English |
|---|---|---|
| Head 1 | **Client** (you / your laptop) | the thing that wants in |
| Head 2 | **KDC** (Key Distribution Center) | the trusted referee everyone shares a secret with |
| Head 3 | **Service** (the app / server) | the thing you're trying to reach |

> **Why you care at FinCo:** Microsoft adopted Kerberos as the **default** authentication protocol for Active Directory in Windows 2000, and it has been ever since. Every Windows domain login you'll touch is Kerberos. The 1980s "untrusted network" mindset is exactly the **Zero Trust** mindset your team is moving toward today — Kerberos was Zero Trust before the phrase existed.

---

## 2. The cast — who's who

Learn these six names cold; every later section reuses them.

| Actor | What it is | The house analogy |
|---|---|---|
| **Client** | The user + their machine (you logging into your FinCo laptop). | The guest arriving at a resort. |
| **KDC** | **Key Distribution Center** — the trusted server. In AD, **every Domain Controller is a KDC**. | The resort's front desk. |
| **AS** (inside the KDC) | **Authentication Service** — checks who you are and issues the master pass. | The check-in counter. |
| **TGS** (inside the KDC) | **Ticket Granting Service** — trades your master pass for a pass to a specific facility. | The concierge who hands out room/gym/pool passes. |
| **`krbtgt`** | A special, disabled AD account whose password-derived key **encrypts every TGT**. The KDC's own master key. | The master key the front desk uses to seal every wristband so nobody can forge one. |
| **Service account** | The identity a server app *runs as* (e.g. `svc-payroll`). It shares a secret key with the KDC. | A specific facility (the gym) that shares a code with the concierge. |
| **SPN** | **Service Principal Name** — the unique "address" of a service, like `HTTP/payroll.finco.example`. Maps a service to its service account. | The label on the facility door the concierge looks up. |

Two ideas that trip up beginners, pinned down now:

- **The KDC is one server doing two jobs.** AS and TGS are *roles*, not separate machines. On a Domain Controller they're the same process. We split them because the *messages* differ.
- **An SPN is just a name that ties a running service to an account.** When you ask for "a ticket to the payroll app," you ask by SPN (`HTTP/payroll.finco.example`). AD looks up which account owns that SPN (`svc-payroll`) and encrypts the ticket with *that account's* key. **No SPN registered → no Kerberos → Windows silently falls back to NTLM** (§5). Half of "why isn't Kerberos working?" tickets are a missing or duplicate SPN.

---

## 3. The full mechanism — a FinCo morning, step by step

**Scenario.** It's 9:00 AM. You unlock your FinCo Windows laptop with your domain password. Ten minutes later you open the **legacy intranet payroll app** in your browser. It just *opens* — no second password prompt. Here's every hop that made that happen.

### Exchange 1 — AS-REQ / AS-REP: log in, get the master pass (the TGT)

This happens **once**, at unlock, before you touch any app.

1. **AS-REQ (client → AS).** Your laptop takes the **current timestamp** and encrypts it with a key **derived from your password** (your "long-term key" = a hash of your password). It sends: *"I'm `farhaan@FINCO.EXAMPLE`, and here's a timestamp I locked with my secret."* This locked timestamp is **pre-authentication data**.
   - ★ **The key insight of the whole protocol:** your password is **not** in this message. Not the password, not even its hash. Only a *timestamp encrypted with a key made from* the password. The KDC already knows your key (it's stored in AD), so it can decrypt and check.
2. **The AS decrypts it.** If the timestamp comes out as valid, sensible, and recent, then you must hold the right key → you proved you know the password **without sending it**. (The timestamp being *fresh* also stops an attacker from replaying an old captured AS-REQ.)
3. **AS-REP (AS → client)** returns **two** things:
   - a **TGT** (**Ticket Granting Ticket**) — your master pass — **encrypted with the `krbtgt` key**. *You cannot read or forge it*, because you don't have the `krbtgt` key. You just carry it around.
   - a **TGS session key** — a fresh shared secret for talking to the TGS later — **encrypted with your long-term key**, so only *you* can open it.

> **Analogy:** you show ID at check-in (prove your identity), and get a **tamper-proof wristband (TGT)** the desk sealed with a master key you can't copy, plus a **secret handshake (session key)** whispered only to you.

**Gotcha — what's *inside* the TGT:** your username, group memberships (in Windows, the **PAC** — Privilege Attribute Certificate), timestamps, and a copy of that TGS session key. It's all sealed by `krbtgt`. This is why compromising `krbtgt` is catastrophic (§6, Golden Ticket): whoever holds that key can mint a TGT claiming to be *anyone*, in *any group*.

### Exchange 2 — TGS-REQ / TGS-REP: trade the TGT for a ticket to the payroll app

This happens the moment you open the payroll app.

4. **TGS-REQ (client → TGS).** Your laptop sends: the **TGT** (still sealed), the **SPN** you want (`HTTP/payroll.finco.example`), and an **authenticator** — a fresh timestamp encrypted with the **TGS session key** (proving you're the legitimate owner of this TGT, not someone who stole it).
5. **The TGS opens the TGT** with the `krbtgt` key (it can — it *is* the KDC), reads your identity and the session key, checks your authenticator's timestamp is fresh, and confirms the SPN exists.
6. **TGS-REP (TGS → client)** returns:
   - a **service ticket** for the payroll app — **encrypted with `svc-payroll`'s key** (the account that owns that SPN). *You can't read this either* — it's for the app, not you.
   - a new **service session key** for you ↔ the app — encrypted with the TGS session key so you can open it.

> Notice: **you never re-authenticated.** You proved yourself with the TGT, not your password. That's the SSO magic — steps 4–6 repeat silently for *every* app you open all day.

### Exchange 3 — AP-REQ / AP-REP: show the ticket to the app

7. **AP-REQ (client → service).** Your browser sends the **service ticket** plus a fresh **authenticator** (timestamp encrypted with the *service* session key) to the payroll app.
8. **The app opens the service ticket** with **its own** account key (`svc-payroll`'s key — which it has locally, e.g. in a **keytab**, see §5). Inside it finds the service session key, uses that to check your authenticator, reads your identity + groups from the ticket → **you're in.** *The app never contacted the KDC to do this.*
9. **AP-REP (service → client, optional).** If **mutual authentication** is requested, the app encrypts *your* timestamp back to you with the service session key — proving the app is the real payroll server and not an impostor. (Without this you'd have proven yourself to the app, but not vice-versa.)

### The three exchanges, on one diagram

```
                          ┌──────────────── KDC (Domain Controller) ────────────────┐
                          │            AS                          TGS               │
  You / laptop            │   (Authentication Service)     (Ticket Granting Service) │        Payroll app
 (farhaan@FINCO)          └─────────────────────────────────────────────────────────┘      (SPN HTTP/payroll…)
      |                              |                             |                              |
      |  1. AS-REQ  (timestamp encrypted with MY password-key)     |                              |
      |----------------------------->|                             |                              |
      |                              | decrypt → I proved myself   |                              |
      |  3. AS-REP  { TGT (sealed by krbtgt) + TGS-session-key }    |                              |
      |<-----------------------------|                             |                              |
      |                                                            |                              |
      |  4. TGS-REQ  { TGT + SPN=payroll + authenticator }         |                              |
      |----------------------------------------------------------->|                              |
      |                                            open TGT w/ krbtgt, check authenticator         |
      |  6. TGS-REP  { service-ticket (sealed by svc-payroll key) + service-session-key }          |
      |<-----------------------------------------------------------|                              |
      |                                                                                            |
      |  7. AP-REQ  { service-ticket + authenticator }                                             |
      |------------------------------------------------------------------------------------------->|
      |                                                        open ticket w/ svc-payroll key → IN  |
      |  9. AP-REP  { your timestamp back }  (optional mutual auth — proves the app is real)        |
      |<-------------------------------------------------------------------------------------------|
```

**Read it as three trades:** password-proof → **TGT** · TGT → **service ticket** · service ticket → **access**. Everything sealed by a key the *recipient* holds, and every message carries a **fresh timestamp** so old captures can't be replayed.

---

## 4. Why this counts as "passwordless"

Two distinct senses — say both in an interview and you sound senior:

**Sense (a): the password never travels the network.** Even at the very first step (AS-REQ), only a *timestamp encrypted with a key derived from* your password crosses the wire. A sniffer sees ciphertext, never the secret. Compare that to a plain web form POSTing `password=...`. This was the entire point in 1985, and it's *still* why Kerberos matters: **there's no password on the wire to steal.**

**Sense (b): after logon, tickets do all the work.** You typed your password **once** at 9:00 AM. Every app you open afterward — payroll, the file share, the intranet wiki, a legacy Java app — is authenticated by **tickets**, silently. Zero further prompts. That seamless domain-wide **Single Sign-On** *feels* passwordless to the user because, after the first unlock, it is.

**And the *truly* passwordless front door.** You can remove the password from step 1 entirely using **PKINIT** (public-key Kerberos, RFC 4556): instead of a password-derived key, you prove yourself with a **certificate**. That's exactly what powers:

- **Smart cards** (insert card + PIN → cert → TGT),
- **Windows Hello for Business** (biometric/PIN unlocks a device-bound cert → TGT),
- **FIDO2 → Entra/AD** hybrid passwordless.

In all three, the KDC issues a normal TGT and everything downstream (§3) is unchanged — but there was **never a password anywhere.** That's the direction fintechs are moving for phishing-resistant login, and it plugs straight into the Kerberos machinery you already understand.

---

## 5. The plumbing a beginner actually meets

The mechanism is elegant; the day-job is these five operational realities.

### 5a. Keytab files — how non-Windows apps hold their key

A Windows service reads its account key from the OS. But a **legacy app on Linux** (a Java payroll backend, an Apache reverse proxy, PingFederate itself) has no domain login. So its key lives in a **keytab file** — literally "key table": a file containing the service's principal name and its **long-term keys**, derived from the service account's password.

- It lets the app do step 8 (**open service tickets**) and, if it's a client, prove itself to the KDC — **without a human typing a password.**
- **Gotcha you *will* hit:** if the service account's password is changed (or rotated by PAM) but the **keytab isn't regenerated**, every Kerberos login to that app breaks with a decrypt error. "Rotate the password, forget the keytab" is a classic self-inflicted outage.
- **Never commit a keytab to git** — it's a credential. (Our `.gitignore` blocks `*.keytab`; use placeholders like `/etc/security/svc-payroll.keytab`.)

### 5b. Clock skew — why "check the time" is the classic Kerberos fix

Every authenticator is a **timestamp**. The KDC and services reject any timestamp outside a tolerance window — **±5 minutes by default** in Windows (`MaxClockSkew`). This is the anti-replay defense: an attacker can't replay a captured authenticator once its 5-minute window closes.

- **Symptom:** `KRB_AP_ERR_SKEW` ("clock skew too great"), Kerberos logins fail on one machine while everything else is fine.
- **Fix:** sync the clock (NTP). This is why **"is the time right?"** is the first question a seasoned admin asks about a Kerberos failure — and why domain time hierarchy (PDC emulator → NTP) matters.

### 5c. Ticket lifetimes

| Ticket | Default lifetime | Renewable up to |
|---|---|---|
| **TGT** | **10 hours** | **7 days** (renew without re-entering password, until the renewal cap) |
| **Service ticket** | ≤ the TGT's remaining life | — |

Short lifetimes limit how long a *stolen* ticket is useful — the reason Pass-the-Ticket (§6) is a race against the clock. Your `klist` command on Windows shows your current tickets and their expiry.

### 5d. Kerberos vs NTLM — and why NTLM is the fallback to avoid

Both are Windows authentication protocols, but they're not equals:

| | **Kerberos** | **NTLM** |
|---|---|---|
| Age / status | Modern default (AD since 2000) | **Legacy fallback** |
| How it proves you | Tickets + timestamps, trusted KDC | Challenge/response against your NT hash |
| Mutual auth | **Yes** (AP-REP) | No — client never verifies the server |
| Known abuses | Kerberoasting, etc. (fixable) | **Pass-the-Hash, NTLM relay** — structural, hard to fix |
| When it kicks in | Hostname + registered **SPN** present | Connecting by **IP**, no SPN, workgroup, some cross-forest cases |

**Rule of thumb:** you *want* Kerberos and you want to **suppress NTLM** where you can. If an app is silently using NTLM, it's usually because Kerberos couldn't (missing SPN, IP-based URL). Modern hardening tracks and restricts NTLM; a mature fintech is actively driving it down.

### 5e. Kerberos vs LDAP — two jobs, same directory

A perennial beginner mix-up. **A Domain Controller does both**, but they answer different questions:

- **Kerberos = authentication.** *"Prove who you are."* Issues tickets. (This whole note.)
- **LDAP = the directory lookup.** *"Tell me about this object."* Query users, groups, attributes, group membership. (See [note 04](04-ldap-ad-entra.md).)

You **authenticate with Kerberos**, then **look things up with LDAP**. (An LDAP *bind* can even be authenticated *via* Kerberos using GSSAPI — the two cooperate.) One-liner: **Kerberos gets you in; LDAP tells you what's inside.**

---

## 6. Attacks — each paired with detection & mitigation

Kerberos crypto is sound; the attacks exploit **weak keys, a stolen master key, or tickets lifted from memory**. All of the below are **authorized-lab-only** for you (a lab DC + `Rubeus`/`Impacket`) — never FinCo systems. Hand the offensive build-out to **Loki**; here's the defender's map.

| Attack | How it works (plain) | Detect | Mitigate |
|---|---|---|---|
| **Kerberoasting** | Anyone can request a **service ticket** for any SPN (step 4–6). That ticket is encrypted with the **service account's password key**. Crack it **offline** to recover a weak `svc-*` password. | Spike in **TGS-REQs** (Event **4769**), especially **RC4 (etype 0x17)** requests, one user pulling many SPNs. Honeypot SPN that no one should ever request. | **gMSA/dMSA** (auto-rotated 120-char random passwords → uncrackable), or long (25+char) random service-account passwords. **Enforce AES, disable RC4.** Minimize SPNs. |
| **AS-REP Roasting** | Accounts with **"Do not require pre-authentication"** skip step 1's proof — so the AS hands out a chunk encrypted with the user's key to *anyone who asks*. Crack offline. | Event **4768** with **pre-auth type = 0**; enumerate accounts flagged `DONT_REQ_PREAUTH`. | **Never disable pre-auth** (audit for it and fix). Strong passwords as backstop. |
| **Pass-the-Ticket (PtT)** | Steal a **TGT or service ticket** from LSASS memory on a compromised host and **reuse** it elsewhere — no password needed. | Tickets used from an unexpected host/IP; logons with no preceding 4768; EDR flagging **LSASS access** (Mimikatz/Rubeus). | Credential Guard (protect LSASS), least privilege so few hosts hold juicy TGTs, short ticket lifetimes, disable accounts on compromise. |
| **Golden Ticket** | Attacker who has the **`krbtgt` key** forges a **TGT** for *anyone*, *any* group, valid for years. Total domain forgery — they became the front desk's master key. | TGTs with **anomalous lifetimes** (e.g. 10 years), logons for accounts that never AS-REQ'd, mismatched PAC data. | **Rotate `krbtgt` twice** (invalidates all existing TGTs) after any DC compromise, on a schedule. Tier-0 protection of DCs. Assume-breach monitoring. |
| **Silver Ticket** | Attacker with **one service account's key** forges a **service ticket** for that service only — **skips the KDC entirely** (no TGS-REQ, so no 4769 to spot). Quieter than golden. | Harder — no KDC event. Look at the **service host's** logs for logons with no matching 4769; PAC validation anomalies. | Strong/rotated service-account keys (gMSA again), enable **PAC validation**, monitor service hosts, AES-only. |

**The through-line:** four of the five attacks are starved by **strong, rotated keys** — which is precisely why **gMSA** and PAM-managed service accounts (§7) are the single highest-leverage Kerberos defense. The fifth (Golden) is why `krbtgt` is treated as the crown jewel of the domain.

---

## 7. How this lands in your IAM job at FinCo

- **Service-account sprawl is *the* Kerberos problem at a fintech.** Every legacy app runs as some `svc-*` account with an SPN. Old ones have weak, never-rotated passwords and stale RC4 — a Kerberoasting buffet. Part of IGA/PAM hygiene is **inventorying SPNs, migrating to gMSA, and killing RC4.** Tie this to [PAM deep dive](11-pam-deep-dive.md): service accounts are exactly what PAM vaults and rotates.
- **`krbtgt` is a Tier-0, PAM-guarded secret.** When your PAM/AD team schedules a **double `krbtgt` rotation**, now you know *why*: it's the master key that seals every TGT, and rotating it twice is the only way to fully invalidate forged Golden Tickets.
- **Keytabs and IWA on your legacy stack.** Those older intranet apps (payroll, ledger front-ends) authenticate via **keytabs** and **Integrated Windows Authentication (IWA)** — the seamless "no prompt" experience from §3. Expect keytab regeneration to be part of every service-account password rotation runbook.
- **PingFederate's Kerberos (IWA) adapter.** This is where your two worlds meet. Ping's **Kerberos Adapter** lets a domain-joined browser do **SPNEGO/Kerberos** (present a service ticket for Ping's SPN, e.g. `HTTP/sso.finco.example`) so PingFederate authenticates you **silently from your desktop session** — then federates you via **SAML/OIDC** into web and SaaS apps. Net effect: your **morning Windows logon** becomes SSO into cloud apps, **no extra password**. You configure it with a **service account + SPN + keytab** for Ping, and an in-scope browser (SPNEGO enabled). When "desktop SSO into the SaaS portal" breaks, the usual suspects are — you guessed it — **SPN, keytab, or clock skew.**

> **The mental model to carry into standups:** Kerberos is the *authentication substrate* under the domain; SAML/OIDC (your Ping world) is the *federation layer* on top. The Kerberos IWA adapter is the **bridge** — it turns a Kerberos desktop logon into a federated SSO session.

---

## What you learned

- **The 1980s problem** — untrusted network, no TLS, can't send passwords — and the **three-headed dog** naming: client · KDC · service (§1).
- **The cast** — KDC = **AS + TGS**; **`krbtgt`** seals every TGT; **service accounts + SPNs** identify apps (§2).
- **The three exchanges** — **AS-REQ/REP** (pre-auth → TGT + session key), **TGS-REQ/REP** (TGT → service ticket), **AP-REQ/REP** (service ticket → access + optional mutual auth) — each sealed by the recipient's key, each carrying a fresh timestamp (§3).
- **Why "passwordless"** — (a) the password never crosses the wire, only encrypted proof; (b) tickets give seamless domain SSO after logon; and **PKINIT** (smart card / Windows Hello) removes the password entirely (§4).
- **The plumbing** — **keytabs** (non-Windows key storage), **±5-min clock skew** ("check the time!"), **10h/7-day** ticket lifetimes, **Kerberos vs NTLM** (want the former, suppress the latter), **Kerberos = authN vs LDAP = lookup** (§5).
- **The attacks + defenses** — Kerberoasting, AS-REP roasting, Pass-the-Ticket, Golden & Silver tickets — all starved by **strong/rotated keys (gMSA)**, **AES-not-RC4**, **double `krbtgt` rotation**, and LSASS protection (§6).
- **Your FinCo tie-in** — service-account sprawl, `krbtgt` as a PAM crown jewel, keytab/IWA on legacy apps, and the **PingFederate Kerberos (IWA) adapter** that bridges desktop logon into federated SSO (§7).

## Next

- **Go deeper on the federation layer:** [note 16 — SAML bindings & certificates](16-saml-bindings-and-certificates.md) — how the SAML/OIDC layer that sits *on top* of your Kerberos desktop login moves and protects its messages.
- **Hands-on (hand to Lefler):** stand up a lab DC + a Linux service with a **keytab**, capture the three exchanges in Wireshark, then break a deliberately-weak `svc-*` account with Kerberoasting — and watch Event **4769** fire.
- **Cross-link:** revisit [PAM deep dive](11-pam-deep-dive.md) with new eyes — every "vault this service account / rotate `krbtgt`" control now has a *why*.

*— Janus 🔐, from your Kerberos study session*
