---
name: task-delegation
description: Token-conserving task triage and delegation strategy. Use when deciding whether to do a task with the current (expensive) model or hand it to a cheaper capable model/agent — and how to write a hand-off spec that gets correct work without a back-and-forth. Powers the Odysseus (delegator) + Eurylochus (worker) pattern.
---

# Task Delegation — Route for Quality-per-Token

**TL;DR** — Score the task on 5 axes. Do it yourself only when it's genuinely hard/risky/cross-cutting. Otherwise delegate down to the cheapest capable model with a crisp spec. Resolve ambiguity *before* handing off, never during.

---

## 1. Triage the task
Score each axis low / med / high:

| Axis | Question |
|------|----------|
| **Scope** | One file/function, or many moving parts? |
| **Ambiguity** | Crisp goal, or under-specified? |
| **Risk** | Reversible & low-stakes, or irreversible / security- / data-sensitive? |
| **Cross-cutting** | Local, or ripples across modules? |
| **Novelty** | Routine pattern, or real design/algorithm problem? |

## 2. Decide
- **Do it yourself (superior model)** when high on **Novelty, Risk, or Cross-cutting** — real design judgment, threat modeling, architecture, or subtle correctness.
- **Delegate (cheaper model)** when the task is **well-scoped and mechanical-to-moderate** — clear change, routine feature, obvious refactor, boilerplate, tests, docs.
- **Ambiguity is not delegatable.** If the spec is fuzzy, clarify with the user or scope it down yourself first. Never hand a worker a guess.

## 3. Model-tier map

| Task shape | Tier | Claude | OpenAI |
|------------|------|--------|--------|
| Architecture, security design, subtle bugs | superior | opus | GPT-5 (high) |
| Standard implementation, refactor, tests | mid | sonnet | gpt-5 / gpt-5-mini |
| Trivial edits, boilerplate, formatting, docs | light | haiku | gpt-5-mini / nano |

Rule: **delegate down to the cheapest model that still gets it right.** A rename doesn't need superior; a concurrency bug does. The gap is the savings.

## 4. The hand-off spec
Give the worker everything needed, nothing extra:
```
GOAL:        <one sentence — "done" defined>
CONTEXT:     <2–3 facts; link files by path:line>
CONSTRAINTS: <patterns to follow, what NOT to touch>
ACCEPT:      <how correctness is judged — tests, behavior>
ESCALATE IF: <when to stop and ask>
```
Always append the standing order: **"If anything is ambiguous or you'd guess on something that changes the outcome — STOP and ask."**

## 5. Verify on return
Delegation ≠ abdication. Check returned work against **ACCEPT**. If wrong: fix it (if small) or re-delegate with a sharper spec. The delegator owns the outcome.

## Portability
- **Claude Code:** delegate by spawning sub-agents (Agent/Task tool).
- **Cursor / Copilot (no sub-agents):** emit the spec as output; either execute it cheaply yourself or tell the user which model to switch to and hand them the ready spec. Same triage, different hand-off.

**Next:** pair with the `odysseus` (delegator) and `eurylochus` (worker) agents, which implement this end-to-end.
