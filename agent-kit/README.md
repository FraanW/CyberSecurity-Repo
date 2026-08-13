# 🛠️ Agent Kit — a portable crew you can drop into any workspace

A set of **drop-in AI agents + skills**, named after characters from *The Odyssey*, built to work across **Claude Code, Cursor, and GitHub Copilot**. Copy the files you want into a project and go.

They're written **generic/portable** (usable on any codebase) — except **Mentor**, which is deliberately security/IAM-flavored.

---

## The crew

| Agent | Role | What it's for | Model tier |
|-------|------|---------------|-----------|
| 🦉 **Athena** | Recon / explainer | Understand an unfamiliar codebase — what you're seeing, how it works, how it maps to business logic & the user experience. Read-only, witty. | **mid** |
| 📜 **Phemius** | Note-maker | Turn explanations into concise, skimmable docs. Token-thrifty, plain language, consistent folder structure. | **light** |
| 🧭 **Odysseus** | Master delegator | *Default for implementation work.* Sizes up a task, then does it or delegates to the cheapest capable agent/model with a crisp spec. | **superior** |
| ⚔️ **Eurylochus** | Worker | Executes a well-specified task crisply; **stops and asks** when the spec is ambiguous. Pairs with Odysseus. | **light → mid** |
| 🏛️ **Mentor** | IAM/Sec system design | Senior security architect — best practices, trade-offs, threat modeling, IAM patterns for real systems. | **superior** |
| 🎨 **Circe** | Frontend design | Interfaces that look designed, not generic. Clarifies the direction first, then builds anti-slop UI. | **mid → superior** |

## The skills (shared reference playbooks)

| Skill | Backs | What it holds |
|-------|-------|---------------|
| `frontend-design-system` | Circe | The full anti-AI-slop UI/UX playbook: direction, tokens, type, color, spacing, layout, motion, a11y. |
| `note-taking-lefler` | Phemius | Lefler's Laws + token-thrift rules + docs folder structure. |
| `task-delegation` | Odysseus / Eurylochus | Triage rubric, model-tier map, hand-off spec format. |

## Model tiers (the token-conservation play)
The whole point: **run each agent on the cheapest model that does its job well.**

| Tier | Claude | OpenAI | Cursor/Copilot |
|------|--------|--------|----------------|
| **superior** | `opus` | GPT-5 (high-reasoning) | pick your strongest model |
| **mid** | `sonnet` | `gpt-5` / `gpt-5-mini` | pick a mid model |
| **light** | `haiku` | `gpt-5-mini` / `gpt-5-nano` | pick the lightest capable model |

Each agent file states its tier at the top. **Odysseus** orchestrates the rest so expensive models only run on hard work.

---

## How to import

### Claude Code
Agents live in `.claude/agents/`, skills in `.claude/skills/`.
```bash
# from your project root
mkdir -p .claude/agents .claude/skills
cp path/to/agent-kit/agents/*.md               .claude/agents/
cp -r path/to/agent-kit/skills/*               .claude/skills/
```
The `model:` and `tools:` front-matter is read automatically. Invoke by name (e.g. "have **athena** explain this module") or via the Agent tool. Odysseus can spawn the others as sub-agents.

### Cursor
Cursor uses `.cursor/rules/*.mdc`. Convert each agent into a rule:
1. Create `.cursor/rules/athena.mdc` (etc.).
2. Copy the agent's **body** (everything below the front-matter) as the rule content.
3. Add Cursor front-matter: a `description`, and `alwaysApply: false` so it's invoked on demand.
4. Cursor has no sub-agents — pick the model from the tier table in the model picker when you use that agent. Odysseus becomes a **planner**: it emits a delegation spec you hand to a cheaper model.

### GitHub Copilot
Copilot supports **custom chat modes** (`.github/chatmodes/*.chatmode.md`) and instruction files (`.github/instructions/*.instructions.md`).
1. For each agent, create `.github/chatmodes/athena.chatmode.md`.
2. Add front-matter: `description`, and `tools`/`model` per Copilot's schema (map the tier to a model Copilot offers).
3. Paste the agent body as the mode's instructions.
4. Skills → drop into `.github/instructions/` so they apply as shared context.

> **Note on models & tools:** front-matter here uses Claude Code's schema (`model: opus|sonnet|haiku`, `tools: …`). Each agent body carries a plain-English **model tier** line so you can pick the right model in any tool. Adjust `tools:` to whatever the host supports.

---

## Suggested workflow
1. **Understand** → 🦉 Athena maps the codebase.
2. **Design** (if non-trivial) → 🏛️ Mentor for architecture/security; 🎨 Circe for UI.
3. **Build** → 🧭 Odysseus triages and delegates to ⚔️ Eurylochus (cheap model) or handles it directly.
4. **Document** → 📜 Phemius writes it up, cheaply.

---

*An away team named for The Odyssey. Amend freely — keep them portable.*
