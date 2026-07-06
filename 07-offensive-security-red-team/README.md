# 07 · Offensive Security / Red Team

> You learn to attack — in a lab — so you can defend. You cannot reliably stop an attack you don't understand from the inside.

> ⚠️ **Ethics & scope** — Everything in this domain is for **your own lab VMs**, intentionally-vulnerable targets (DVWA, OWASP Juice Shop, Metasploitable, VulnHub boxes, HackTheBox, TryHackMe), CTFs, or systems for which you hold **explicit written authorization**. That is the entire allowed universe. **Never** production. **Never** FinCo systems, networks, or data without a signed engagement authorizing it. **Never** third parties, and never "just to see if it works." Running any of these techniques against a system you are not authorized to test is a crime (in India, the Information Technology Act, 2000 — §43 and §66 — plus international equivalents) and it ends careers. The written scope and authorization is the single thing that separates a security professional from a criminal. Internalize this before you touch a single tool.

**Agents to use:** ask **Mimir** to explain the concepts, **Lefler** to stand up the lab, **Loki** — your primary agent here — for ethical offensive methodology and technique walkthroughs, and **Heimdall** to show how each attack is *detected* (log artifacts, telemetry, alerts) so you're purple teaming, not just breaking things.

---

## Core concepts (learn in this order)

This ordering follows how a real engagement flows — from planning, to finding the way in, to what you do once inside, to writing it all up. **Pair every attack with its defense.** For each technique below, ask Heimdall: "What does this look like in the logs, and how would a blue team catch or block it?" That habit is what makes you employable in a defensive role.

### 1. Pentest methodology & frameworks
- What a penetration test actually is vs. a vulnerability scan vs. a red team engagement vs. a bug bounty.
- The **Cyber Kill Chain** (Lockheed Martin): recon → weaponization → delivery → exploitation → installation → C2 → actions on objectives.
- **MITRE ATT&CK**: the tactic/technique matrix that both attackers and defenders share as a common language. Learn to read a technique ID (e.g., T1558.003 = Kerberoasting) — you'll map every attack you learn back to ATT&CK.
- Rules of engagement, scope documents, and authorization letters — the paperwork *is* the profession.

### 2. Reconnaissance
- **Passive recon / OSINT**: gathering information without touching the target — WHOIS, DNS records, certificate transparency logs, Google dorking, Shodan, LinkedIn/employee footprinting, breach-data awareness.
- **Active recon**: interacting with the target directly (which starts leaving traces).
- Why recon is the phase defenders most often ignore — and how attack surface management counters it.

### 3. Scanning & enumeration
- **nmap** deeply: host discovery, TCP connect vs. SYN scans, service/version detection (`-sV`), OS fingerprinting, the NSE scripting engine, timing templates and why they matter for stealth vs. speed.
- Service enumeration: SMB, LDAP, HTTP, DNS, SNMP, SMTP — pulling users, shares, versions.
- Enumeration is where most of the real work happens. "Enumerate harder" is the oldest advice in the field.

### 4. Vulnerability assessment
- Reading service/version output and mapping it to known CVEs.
- Using scanners (OpenVAS/Greenbone, Nikto for web) and — critically — **validating** findings by hand to weed out false positives.
- CVSS scoring, and why raw CVSS ≠ real-world risk in a given environment.

### 5. Exploitation basics
- **Metasploit Framework**: modules, payloads, `msfconsole` workflow, meterpreter, staged vs. stageless payloads.
- The difference between using a canned exploit and understanding *why* it works.
- Defense pairing: patch management, EDR, and why exploit mitigations (ASLR, DEP) exist.

### 6. Web exploitation (OWASP Top 10, hands-on)
- Work the **OWASP Top 10** as living skills, not a checklist: broken access control, injection (SQLi, command injection), XSS (reflected/stored/DOM), SSRF, insecure deserialization, security misconfiguration, authentication failures.
- **Burp Suite** as your daily driver: proxying, repeater, intruder, decoder.
- Defense pairing: input validation, parameterized queries, output encoding, CSP, WAFs — and their limits.

### 7. Password attacks
- How credentials are stored: hashing vs. encryption, salting, common algorithms (bcrypt, NTLM, MD5, SHA-family).
- **Offline cracking** with **hashcat** and **John the Ripper**: dictionary, rule-based, mask, and hybrid attacks; using wordlists (rockyou) and rules.
- **Online attacks**: password spraying vs. brute force vs. credential stuffing (and why spraying evades lockouts).
- Defense pairing: strong hashing, MFA, lockout policies, credential-stuffing detection, breached-password screening.

### 8. Privilege escalation
- **Linux privesc**: SUID/SGID binaries, sudo misconfigurations, cron jobs, writable paths, kernel exploits, capabilities. Tools: LinPEAS, `linux-smart-enumeration`.
- **Windows privesc**: unquoted service paths, weak service permissions, token abuse, AlwaysInstallElevated, DLL hijacking. Tools: WinPEAS, PowerUp.
- Defense pairing: least privilege, hardening baselines (CIS), and monitoring for privesc indicators.

### 9. Active Directory attacks — *tie this directly to IAM*
This is the section that matters most for your career at a fintech IAM role. AD is identity infrastructure, and these are attacks against identity itself.
- **Kerberos** basics: TGTs, TGSs, service accounts, SPNs — enough theory to understand the attacks.
- **Kerberoasting** (ATT&CK T1558.003): requesting service tickets for SPN-linked accounts and cracking them offline to recover service-account passwords.
- **AS-REP roasting**: abusing accounts that don't require Kerberos pre-authentication.
- **Pass-the-Hash** and **Pass-the-Ticket**: authenticating with a stolen NTLM hash or Kerberos ticket instead of a plaintext password.
- **BloodHound**: graphing AD relationships to find attack paths from a low-priv user to Domain Admin.
- Defense pairing (your future day job): tiered admin models, managed/group-managed service accounts (gMSA), strong service-account passwords, AES-only Kerberos, LAPS, privileged access workstations, and detection of anomalous ticket requests.

### 10. Lateral movement & persistence
- Moving between hosts: PsExec, WMI, WinRM, SSH pivoting, SOCKS proxies.
- Persistence: scheduled tasks, services, registry run keys, SSH keys, AD backdoors (golden/silver tickets — conceptually).
- Defense pairing: network segmentation, EDR behavioral detection, honeytokens.

### 11. Post-exploitation
- What you actually do once you have a foothold: credential harvesting, data discovery, understanding blast radius.
- Documenting evidence properly — screenshots, command logs — because it all goes in the report.

### 12. Command & Control (C2) — brief
- The concept: how implants beacon to an operator, common frameworks (awareness of Cobalt Strike, Sliver, Havoc), beacon intervals, redirectors.
- You don't need to build C2 as a beginner — you need to *recognize* it so the blue side can hunt it.

### 13. Reporting & remediation
- The deliverable that pays the bills: executive summary, technical findings, reproduction steps, risk ratings, and — most important — **actionable remediation**.
- A finding without a clear fix is half a finding. This is where offensive work becomes defensive value.

---

## Reading list

Real, reputable sources — start near the top and go deeper as you progress:

- **"The Web Application Hacker's Handbook"** (Dafydd Stuttard & Marcus Pinto) — still the canonical deep dive on web app attacks; pairs perfectly with PortSwigger.
- **"Penetration Testing: A Hands-On Introduction to Hacking"** (Georgia Weidman) — the best beginner-to-intermediate book on end-to-end pentest methodology; builds a lab as it goes.
- **PortSwigger Web Security Academy** (portswigger.net/web-security) — free, hands-on, world-class labs on every web vulnerability class. Do these alongside Burp Suite.
- **TryHackMe** — beginner-friendly guided rooms and learning paths (e.g., *Complete Beginner*, *Jr Penetration Tester*, *Offensive Pentesting*). Start here.
- **HackTheBox** (and **HTB Academy**) — less hand-holding; graduate to this after TryHackMe. Academy's job-role paths are structured; the main platform is boxes/CTF-style.
- **PEH — Practical Ethical Hacking** (TCM Security) — affordable, modern, methodology-focused course; a great structured syllabus and a common on-ramp to the **PNPT** cert.
- **OSCP / PEN-200** (Offensive Security) — the industry-standard practical pentest cert; study its syllabus as a north-star curriculum even if you don't sit the exam soon.
- **HackTricks** (book.hacktricks.xyz) — the field's go-to reference wiki for enumeration and privesc checklists across platforms. Use it as a lookup, not a substitute for understanding.
- **MITRE ATT&CK** (attack.mitre.org) — the framework itself; browse techniques and their detections/mitigations. Read the "Detection" and "Mitigation" sections, not just the technique.
- **The AD attack references** — the "Active Directory Security" material (adsecurity.org) and the BloodHound docs for the identity-attack depth your IAM role needs.

---

## Labs (ask Lefler to set these up)

Every lab below runs against **free, intentionally-vulnerable targets** inside your own isolated lab network — nothing external, nothing you don't own or aren't explicitly authorized to test. Labs live in `labs/NN-name/` with your notes, commands, and findings for each. **Authorized targets only — no exceptions.**

| # | Lab | You'll learn |
|---|-----|--------------|
| 1 | **nmap enumeration on Metasploitable 2** | Host discovery, service/version detection, NSE scripts, turning scan output into an attack surface map |
| 2 | **DVWA — SQL injection & XSS** (all difficulty levels) | Manual injection, Burp proxy/repeater workflow, how the same bug looks at low vs. high security settings |
| 3 | **OWASP Juice Shop** | Modern web app / API attacks, broken access control, JWT abuse, working the OWASP Top 10 as a challenge board |
| 4 | **Metasploit against Metasploitable** | Full exploit workflow: module selection, payloads, meterpreter, and mapping each step to ATT&CK |
| 5 | **Offline password cracking** (hashcat + John) | Dictionary/rule/mask attacks on sample hashes, why salting and slow hashes matter, cracking NTLM |
| 6 | **Linux privilege escalation** (a TryHackMe privesc room or a VulnHub box) | SUID, sudo, cron, and kernel privesc paths; using LinPEAS and validating findings by hand |
| 7 | **Windows privilege escalation** (TryHackMe privesc room) | Service misconfigs, token abuse, WinPEAS/PowerUp; pairing each with its hardening control |
| 8 | **Local Active Directory lab** — Kerberoasting, AS-REP roasting, Pass-the-Hash, BloodHound | Attacking identity infrastructure end-to-end, then mapping every step to the IAM defenses you'll build at work |
| 9 | **Purple-team review with Heimdall** (revisit labs 4–8) | For each attack, find the log artifact / detection and write the mitigation — turning offense into defensive skill |

---

## How this connects to IAM / fintech

Identity is the modern breach. Stolen and abused credentials — not zero-day exploits — are the number-one initial access vector in real-world attacks, and Active Directory / identity infrastructure is the terrain attackers fight over once inside. That is *exactly* the domain you'll own at FinCo.

When you understand **Kerberoasting**, you understand why service accounts need long, managed passwords and AES-only Kerberos. When you've performed **pass-the-hash** in a lab, credential-theft mitigations, tiered admin models, and privileged access workstations stop being abstract policies and become obvious necessities. When you've watched **BloodHound** trace a path from an intern's laptop to Domain Admin, you'll design least-privilege and monitor for those exact paths in production. Every attack in this domain has a mirror-image defense — and in a fintech IAM role, you are the person who builds that defense. Learning the offense here, ethically and in the lab, is what makes you a genuinely dangerous *defender*.
