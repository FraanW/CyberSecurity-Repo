# 🤖 Your AI Security Squad

Six specialized agents live here. Each is a Claude Code subagent with a focused system prompt for one part of your cybersecurity learning. Invoke one via the Agent tool (`subagent_type: "mimir"`) or just say "ask Mimir to…".

You asked for **Mimir** and **Lefler** — they're here. I added four more that round out a real security team, themed as guardians and gatekeepers.

| Agent | Theme / origin | Role | Ask it to… |
|-------|----------------|------|-----------|
| **Mimir** | Norse well of wisdom | Research & Knowledge Curator | Explain a concept from first principles, curate reading, compare approaches, answer "why does this work this way?" |
| **Lefler** | Ensign R. Lefler (resourceful problem-solver) | Hands-on Lab Engineer | Build a lab from scratch, walk you through it step by step, debug your setup — safely |
| **Janus** | Roman god of gates & doorways | IAM Specialist ⭐ | Deep-dive OAuth/OIDC/SAML, LDAP/AD, RBAC/ABAC, PAM, IGA, Zero Trust — your actual job at FinCo |
| **Heimdall** | Norse watchman of the Bifrost | Blue Team / SOC / IR | Detection logic, log analysis, SIEM queries, incident response playbooks, threat hunting |
| **Loki** | Norse trickster / adversary | Red Team / Offensive (ethical) | Attack methodology, exploitation walk-throughs on lab targets, thinking like an adversary |
| **Tyr** | Norse god of law & justice | GRC, Risk & Compliance | Risk frameworks, PCI-DSS/SOX/ISO 27001, audit prep, control mapping for fintech |

## Which model to use
- **Mimir / Janus / Tyr** — reasoning-heavy explanation & analysis → run on **Opus 4.8** or **Fable 5** for depth.
- **Lefler / Heimdall / Loki** — practical, iterative, tool-driven → **Fable 5** works great; drop to **Sonnet** for speed on mechanical steps.

You (the main session) act as the **team lead** — route questions to the right agent, or handle them yourself.

## Shared thinking philosophy (all six inherit it)
Every agent runs the repo's two engines — **first-principles thinking** (*derive* the design from its constraints) and **empirical thinking** (*prove* it by observation) — codified as **[Lefler's Law 12](../../LEFLER-LAWS.md)** and taught in full in **[`00-foundations/notes/01-first-principles-and-empirical-thinking.md`](../../00-foundations/notes/01-first-principles-and-empirical-thinking.md)**. Each agent file has a **"How you think"** section applying the loop to its domain, so the philosophy carries over whether you work in the main session or invoke an agent directly:

| Agent | Derives… | Proves it by… |
|-------|----------|---------------|
| **Mimir** | the mechanism from bedrock facts | primary sources + a capture/command |
| **Janus** | the protocol from its constraints | decoding the token / SAML-tracer / Keycloak flow |
| **Lefler** | *(is the empirical engine)* | building the lab that lets Farhaan see it |
| **Heimdall** | the detection from attacker constraints | triggering it and watching it fire (purple team) |
| **Loki** | the exploit from the trust it breaks | probing what actually responds on a lab target |
| **Tyr** | the control from the risk it treats | the audit evidence that proves it operates |

## Model & tool notes
Each agent file has YAML frontmatter (`name`, `description`, `tools`, `model`). Edit `model:` to pin an agent to a specific model, or remove it to inherit your session model. Offensive tooling (Loki) is deliberately scoped to *authorized lab use only* — see each file's guardrails.
