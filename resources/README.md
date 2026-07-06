# 📚 Resources — Cross-cutting

Materials that span every domain: platforms to practice on, books worth owning, tools to install, and certification paths. Domain-specific reading lives in each domain's own README.

---

## 🧪 Hands-on practice platforms (free / freemium)

| Platform | What it's for |
|----------|---------------|
| **TryHackMe** | Guided, beginner-friendly rooms across every topic. Best starting point. |
| **HackTheBox** (+ HTB Academy) | Realistic machines & structured courses; steeper curve. |
| **PortSwigger Web Security Academy** | The gold standard for web/app security — free, hands-on labs. |
| **OverTheWire** (Bandit) | Learn Linux + basic security via wargames. |
| **RangeForce / Blue Team Labs Online** | Defensive/SOC-focused exercises. |
| **CyberDefenders** | Blue-team & DFIR challenges with real artifacts. |
| **flaws.cloud / CloudGoat** | Intentionally-vulnerable cloud (AWS) for cloud IAM practice. |
| **Cryptopals** | Learn cryptography by breaking it. |
| **picoCTF / CTFtime** | Capture-the-flag competitions to test everything. |

## 🎯 Intentionally-vulnerable targets (for your local lab)
DVWA · OWASP Juice Shop · Metasploitable 2/3 · VulnHub boxes · WebGoat · bWAPP · badssl.com · DetectionLab (blue team)

> All practice is **authorized-lab-only**. See the safety note in the root README.

---

## 📖 Books worth owning
- **"The Web Application Hacker's Handbook"** — Stuttard & Pinto (web/app security bible)
- **"Serious Cryptography"** — Aumasson (approachable, rigorous crypto)
- **"Penetration Testing"** — Georgia Weidman (great first pentest book)
- **"Blue Team Handbook"** & **"The Practice of Network Security Monitoring"** — Bejtlich (defense)
- **"Intelligence-Driven Incident Response"** — Roberts & Brown
- **"OAuth 2 in Action"** — Richer & Sanso (directly relevant to your IAM job)
- **"Sandworm"** / **"Countdown to Zero Day"** — narrative books to fall in love with the field

## 🎓 Free courses & channels
- **Professor Messer** — CompTIA Security+ (SY0-701) full course, free on YouTube
- **John Hammond, IppSec, LiveOverflow, NetworkChuck** — YouTube
- **TCM Security "Practical Ethical Hacking"** — affordable, excellent
- **Cybrary**, **SANS "Cyber Aces"**, **Microsoft Learn** (SC-300 for identity)

---

## 🛠️ Toolbox to install (in your lab)
- **Kali Linux** or **Parrot OS** — the pentester's distro (run in a VM)
- **VirtualBox / VMware Workstation Player** — free hypervisors
- **Docker Desktop** + **WSL2** — for containerized labs (you're on Windows 11)
- **Wireshark** — packet analysis
- **Burp Suite Community** — web proxy
- **nmap**, **Metasploit**, **hashcat/John**, **BloodHound** — offensive (lab only)
- **Keycloak** — open-source IdP for IAM labs ⭐
- **ELK stack / Splunk Free / Security Onion** — SIEM/blue-team labs

---

## 🧭 Certification roadmap (see also `../LEARNING-ROADMAP.md`)
1. **CompTIA Security+** (SY0-701) — foundations
2. **Microsoft SC-300** (Identity & Access Administrator) — ⭐ your job
3. **CyberArk Defender / Okta Certified** — if your team uses them (PAM/IAM vendors)
4. **AWS/Azure Security specialty** — cloud
5. **CISSP** — later, management-oriented, needs experience
6. **OSCP** — if you go deep offensive (hard, hands-on)

---

## 🇮🇳 India / fintech-specific
- **RBI** cybersecurity framework & guidelines for banks/NBFCs
- **CERT-In** advisories and directions
- **DPDP Act 2023** (India's data protection law)
- **PCI-DSS**, **SOX**, **FS-ISAC** (financial-sector threat sharing) — see `08-grc-compliance` & `09-threat-intelligence`
