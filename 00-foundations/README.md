# 00 · Foundations

> Start here. Every other domain assumes this vocabulary and these mental models. A day spent here saves a week of confusion later.

Ask **Mimir** to explain any concept below from first principles.

---

## Core concepts

### The goals of security — the CIA triad
- **Confidentiality** — only authorized parties can read data
- **Integrity** — data isn't tampered with undetectably
- **Availability** — systems are up when needed
- (Extended: **Authenticity** and **Non-repudiation**)

Everything you'll do maps back to protecting one of these.

### The vocabulary of risk
- **Asset** — something worth protecting
- **Threat** — something that could cause harm (threat *actor* = who)
- **Vulnerability** — a weakness a threat can exploit
- **Risk** = likelihood × impact (the thing you actually manage)
- **Exploit** — the method that turns a vulnerability into an incident
- **Control / countermeasure** — what reduces risk (preventive / detective / corrective)

### Access & accountability
- **AAA** — Authentication, Authorization, Accounting/Auditing
- **Least privilege** & **need to know**
- **Defense in depth** — layered controls; assume any one layer can fail

### How networks work (enough to be dangerous)
- **OSI model** (7 layers) and **TCP/IP** model
- IP addressing, ports, common protocols (HTTP/S, DNS, TLS, SSH, DHCP)
- The 3-way handshake; client/server model

### Thinking like a defender & attacker
- **Threat modeling** — **STRIDE** (Spoofing, Tampering, Repudiation, Information disclosure, DoS, Elevation of privilege)
- **Attack surface** & attack vectors
- The **cyber kill chain** and an intro to **MITRE ATT&CK** (see `09-threat-intelligence`)

### Cryptography preview
- Hashing vs encryption vs encoding (people constantly confuse these)
- Symmetric vs asymmetric (deep dive in `04-cryptography`)

### Governance preview
- Policies, standards, procedures, guidelines
- Compliance vs security (related, not the same) — deep dive in `08-grc-compliance`

---

## Reading list
- **NIST Cybersecurity Framework (CSF) 2.0** — the shared language of the field (Identify, Protect, Detect, Respond, Recover, Govern)
- **CompTIA Security+** objectives (SY0-701) — an excellent structured syllabus even if you don't sit the exam
- "The Web Application Hacker's Handbook" — intro chapters for how the web really works
- OWASP's foundational glossary; NIST glossary (csrc.nist.gov/glossary)
- Professor Messer's free Security+ videos (YouTube) — great for commuting

---

## Labs (ask Lefler)

| # | Lab | You'll learn |
|---|-----|--------------|
| 1 | Build your **home lab**: hypervisor (VirtualBox/VMware/WSL2) + Kali + a target VM | The safe sandbox everything else runs in |
| 2 | **Network recon basics**: `nmap` a target VM, read the results | Ports, services, the attacker's first move |
| 3 | **Packet capture**: sniff traffic with Wireshark, watch a TCP handshake and a TLS handshake | How data actually moves |
| 4 | **Hashing vs encryption** hands-on: hash a string, then encrypt it, observe the difference | Kills the #1 beginner confusion |
| 5 | **Threat model** a simple app using STRIDE on paper | Structured security thinking |

---

## When you're done here
You should be able to read any security article or job ticket and not get lost in the vocabulary. Then go straight to **`01-identity-access-management`** — your domain.
