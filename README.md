# 🛡️ CyberSecurity Repo — Learning & Lab Environment

A personal, hands-on cybersecurity study lab. Built to go **deep** into every major security domain — reading, notes, and real labs — with a special focus on **Identity & Access Management (IAM)**, the domain I'm joining at **FinCo (Chennai)** on the cybersecurity team.

> **Owner:** Farhaan · **Role:** Cybersecurity Analyst (IAM), FinCo — Fintech · **Started:** 2026-07
>
> ℹ️ **"FinCo"** is a stand-in codename for my employer — a large global fintech / payments company — used throughout this repo so the real company name never appears in a public place. Same picture, no real name.

---

## How this repo is organized

Each security **domain** is its own numbered folder. Inside every domain:

```
NN-domain-name/
├── README.md     ← the curriculum: concepts, reading list, and labs for this domain
├── notes/        ← your own notes as you learn (markdown, cheat sheets, diagrams)
└── labs/         ← hands-on experiments, scripts, configs, writeups
```

### The domains

| # | Domain | Why it matters for you |
|---|--------|------------------------|
| 00 | [Foundations](./00-foundations/) | The vocabulary and mental models everything else builds on (CIA triad, threat modeling, OSI). Start here. |
| 01 | [**Identity & Access Management (IAM)**](./01-identity-access-management/) | **Your job.** Authentication, authorization, SSO, federation, MFA, PAM, IGA, Zero Trust. Go deepest here. |
| 02 | [Network Security](./02-network-security/) | Firewalls, segmentation, VPNs, TLS, packet analysis — how attackers move and how you stop them. |
| 03 | [Application Security](./03-application-security/) | OWASP Top 10, secure coding, API security — most breaches start in the app layer. |
| 04 | [Cryptography](./04-cryptography/) | The math that makes identity and confidentiality possible. Underpins all of IAM. |
| 05 | [Cloud Security](./05-cloud-security/) | AWS/Azure/GCP IAM, misconfigurations, CSPM — fintech runs in the cloud. |
| 06 | [Security Operations / Blue Team](./06-security-operations-blue-team/) | SIEM, detection, incident response, threat hunting — defending in real time. |
| 07 | [Offensive Security / Red Team](./07-offensive-security-red-team/) | Pentesting, exploitation, adversary emulation — know how attacks work to defend. |
| 08 | [GRC & Compliance](./08-grc-compliance/) | Risk, audit, PCI-DSS, SOX, ISO 27001 — non-negotiable in fintech. |
| 09 | [Threat Intelligence](./09-threat-intelligence/) | MITRE ATT&CK, IOCs, adversary tracking — knowing who's coming for you. |

Plus [`resources/`](./resources/) — cross-cutting books, platforms, tools, and certification paths.

---

## Your AI security squad

This repo ships with a crew of specialized agents in [`.claude/agents/`](./.claude/agents/). Invoke one with the Agent tool or by name. See [`.claude/agents/README.md`](./.claude/agents/README.md) for the full roster.

| Agent | Domain | Use it for |
|-------|--------|-----------|
| **Mimir** | Research & Knowledge | Explaining concepts deeply, curating reading, answering "why" |
| **Lefler** | Lab Engineer | Building and running hands-on labs safely, step by step |
| **Janus** | IAM Specialist | Deep dives on identity, access, SSO, OAuth/OIDC, PAM — your day job |
| **Heimdall** | Blue Team / SOC | Detection, logging, incident response, threat hunting |
| **Loki** | Red Team / Offensive | Ethical exploitation, pentest methodology, attack paths |
| **Tyr** | GRC & Compliance | Risk frameworks, audits, PCI-DSS/SOX/ISO for fintech |

---

## How to study with this repo

1. **Start at `00-foundations`.** Don't skip it — every other domain assumes it.
2. **Then go deep on `01-identity-access-management`** since it's your role.
3. For each domain README: read the concepts → do the labs → write notes in `notes/`.
4. Use the agents. Ask **Mimir** to explain anything; ask **Lefler** to walk you through a lab; ask **Janus** for IAM depth.
5. Keep a learning log. See [`LEARNING-ROADMAP.md`](./LEARNING-ROADMAP.md) for a suggested 6-month path.

## ⚠️ Safety & ethics

Everything offensive here is for **authorized, educational use only** — your own lab VMs, intentionally-vulnerable targets (DVWA, Juice Shop, HackTheBox, TryHackMe), or systems you have **written permission** to test. Never point tools at production, at FinCo systems without authorization, or at anything you don't own. This is how you build a career, not end one.
