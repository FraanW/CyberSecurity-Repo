# CLAUDE.md — CyberSecurity Learning Repo

Guidance for Claude Code when working in this repository.

## What this is
A personal cybersecurity study lab for **Farhaan**, who is starting a security career in the **IAM (Identity & Access Management)** domain at **FinCo** (fintech, Chennai). The repo is organized for deep, hands-on learning across all major security domains, with IAM as the primary focus.

See `README.md` for the full layout and `LEARNING-ROADMAP.md` for the study plan.

## Structure
- `NN-domain-name/` — one folder per security domain (`00-foundations` … `09-threat-intelligence`). Each contains:
  - `README.md` — the curriculum: concepts (in learning order), reading list, and hands-on labs
  - `notes/` — Farhaan's own notes
  - `labs/` — hands-on experiments; each lab in its own `labs/NN-name/` subfolder with a writeup
- `resources/` — cross-cutting platforms, books, tools, certification paths
- `.claude/agents/` — the AI security squad (see below)

## The agent squad
Route work to the right specialist (invoke via the Agent tool or by name):
- **Mimir** — research & knowledge; explains concepts from first principles
- **Lefler** — lab engineer; builds and walks through hands-on labs safely
- **Janus** — IAM specialist ⭐ (Farhaan's day job): OAuth/OIDC, SAML, LDAP/AD, PAM, IGA, Zero Trust
- **Heimdall** — blue team / SOC: detection, SIEM, incident response
- **Loki** — red team (ethical): offensive methodology, authorized-lab-only
- **Tyr** — GRC & compliance: PCI-DSS, SOX, ISO 27001, risk for fintech

## Working principles
- **Teach, don't just answer.** Farhaan is learning deeply — explain the "why," connect concepts to his IAM/fintech context, and check understanding.
- **Derive the why, then prove it.** Explain concepts from first principles (what constraints force this design to exist?) and pair every important claim with an empirical check — a lab, a capture, a command with expected output. See `00-foundations/notes/01-first-principles-and-empirical-thinking.md` and Law 12.
- **Write to Lefler's Laws.** Every note, lab, README, and artifact follows `LEFLER-LAWS.md` (repo root) — beginner-first, plain words before jargon, skimmable, examples over abstraction, prerequisites stated, tied to his job. Run its 20-second checklist before saving any doc.
- **Everything hands-on is authorized-lab-only.** All offensive techniques target his own lab VMs, intentionally-vulnerable apps (DVWA, Juice Shop, Metasploitable), or explicitly-authorized systems. Never production, never FinCo systems without authorization, never third parties.
- **Never commit secrets.** Use `.gitignore`d files and placeholders; the `.gitignore` already blocks keys, certs, `.env`, and capture files.
- **Pair attacks with defenses.** When covering an offensive technique, always include how to detect and mitigate it.
- **Save knowledge.** When a good explanation or lab writeup is produced, store it in the relevant domain's `notes/` or `labs/` folder.
