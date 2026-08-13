---
name: agent-forge
description: A guided playbook for creating a custom AI agent from scratch. Use when someone wants to build a new agent to their own specification — it supplies the interview question bank, the model-tier guidance, the agent-file template, and a validation checklist so the result is well-scoped, correctly-modeled, portable, and safe. Powers the Hephaestus (agent-forge) agent.
---

# Agent Forge — Playbook for Building Any Agent

**TL;DR** — Don't write an agent from a one-liner. Interview across 4 rounds (purpose · behavior · model/tokens · character/guardrails), pick the model tier from the task complexity, fill the template, then validate. A tool forged to fit beats a generic one.

---

## How to run it
Interview in **batches, not 15 separate questions.** Each question carries a **smart default** so the user can just confirm. Skip anything obvious from context; fold rounds together for a simple agent; go deeper for a complex one. Draft → confirm → save.

## The question bank

### Round 1 — Purpose & fit
| Ask | Why it matters |
|-----|----------------|
| **Job-to-be-done** (one sentence) + why needed | The whole reason it exists; shapes everything |
| **Trigger** — by name / by default for a task type / on request | Becomes the `description` "Use when…" |
| **Success looks like** | Defines the output shape and quality bar |

### Round 2 — Behavior (what & how)
| Ask | Why |
|-----|-----|
| **Responsibilities** (in scope) | The core duties section |
| **Boundaries** (out of scope / never do) | Prevents scope creep; drives tool choice |
| **Method** — step-by-step process | The "how it works" section |
| **Inputs / outputs** + shape | Makes it predictable |
| **Clarify-first?** stop-and-ask vs proceed | The STOP rule, or not |

### Round 3 — Model, tokens & complexity
| Ask | Why |
|-----|-----|
| **Complexity**: trivial / standard / hard | Sets the tier |
| **Model tier** (recommend from complexity) | The `model:` field |
| **Token posture**: thrifty vs thorough | Becomes explicit body rules |
| **Tools needed** | The `tools:` field |

### Round 4 — Character & guardrails
| Ask | Why |
|-----|-----|
| **Persona / tone** (+ optional Odyssey name) | Identity & style |
| **Guardrails** — never-do rules, safety/security | Non-negotiable constraints |
| **Output conventions** — where it saves, house style | Keeps work organized |
| **Portability** — Claude Code / Cursor / Copilot | Determines the tier-line + notes |

## Model-tier guidance (map complexity → model)
| Complexity of tasks | Tier | Claude | OpenAI |
|---------------------|------|--------|--------|
| Trivial / mechanical (rename, format, boilerplate, simple docs) | **light** | `haiku` | `gpt-5-mini` / `nano` |
| Standard implementation / explanation / routine judgment | **mid** | `sonnet` | `gpt-5` / `gpt-5-mini` |
| Design, architecture, security, subtle correctness, orchestration | **superior** | `opus` | GPT-5 (high) |

Rule: **match the model to the hardest task the agent will actually face** — not the average. Under-model and it fails on the hard 10%; over-model and you burn tokens on the easy 90%.

## Tool-selection guide
| The agent needs to… | Give it |
|---------------------|---------|
| Only read & explain (safe anywhere) | `Read, Grep, Glob` |
| Also research the web | `+ WebSearch, WebFetch` |
| Change files | `+ Write, Edit` |
| Run commands / tests / builds | `+ Bash` |
| Orchestrate other agents | `+ Task, Agent` |
Prefer the **least tools** that do the job — smaller surface = safer and cheaper.

## The agent-file template
```markdown
---
name: <lowercase-name>
description: <crisp, trigger-oriented — "Use when…". This is how it gets picked.>
tools: <minimal set>
model: <opus | sonnet | haiku>
---

# <Name> <emoji> — <one-line role>

> **Model tier: <LIGHT|MID|SUPERIOR>.** <why>. Claude → `<model>`. OpenAI → <model>. In Cursor/Copilot, pick a <tier> model.

You are **<Name>**, <persona/identity in one or two lines>.

## Your one job
<the JTBD, sharply>

## How you work
1. <method step>
2. <method step>
   …

## Boundaries / guardrails
- <what it never does>
- <safety/security/domain rules>

## Token posture
<thrifty rules OR "depth over cost" — be explicit>

## Style
<tone, and the clarify-first/STOP rule if wanted>

## Output shape
<what it produces and where it saves>
```

## Validation checklist (run before saving)
- [ ] **Trigger is clear** — the `description` says exactly when to use it ("Use when…").
- [ ] **Duties scoped** — core responsibilities listed; no vague "does everything."
- [ ] **Boundaries present** — what it must NOT do is explicit.
- [ ] **Tools fit** — least-privilege set that still does the job.
- [ ] **Tier matches complexity** — modeled for the hardest real task, not the average.
- [ ] **Token posture stated** — thrifty or thorough, as explicit rules.
- [ ] **Guardrails baked in** — safety/security/never-do rules in the body.
- [ ] **Clarify behavior set** — STOP-and-ask rule included if the agent needs it.
- [ ] **Portable** — model-tier line present; tools note-adjusted for the target tools.
- [ ] **House style** — skimmable, beginner-first, examples over abstraction.

## After forging
- Save to `agent-kit/agents/<name>.md` (or the user's chosen location).
- If the agent leans on a reusable playbook, forge a matching **skill** in `agent-kit/skills/<name>/SKILL.md` and reference it from the agent.
- Add a row to the kit **README** so the new agent is discoverable.

**Next:** pair with the `hephaestus` agent, which runs this playbook interactively.
