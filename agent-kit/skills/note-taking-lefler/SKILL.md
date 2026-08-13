---
name: note-taking-lefler
description: Token-efficient documentation & note-making conventions based on Lefler's Laws. Use whenever writing a doc, note, README, or reference meant to be read (by a human or an AI for context) — to make it beginner-first, skimmable, and cheap on tokens without losing the full flow. Covers the writing laws, token-thrift rules, and a standard docs folder structure.
---

# Note-Taking — Lefler's Laws, Token-Thrifty Edition

**TL;DR** — Write for maximum understanding per token. Plain words before jargon, one idea per chunk, tables over prose, skimmable by design. Every word earns its place.

---

## The writing laws (condensed)
1. **Beginner first.** Assume zero prior knowledge. Define a term on first use. Never make the reader feel dumb.
2. **Plain words, then the term.** "the login server (the *IdP*)" — not just "the IdP."
3. **One idea per chunk.** Short sentences. 2–4 line paragraphs. Break walls of text with a header/table/list.
4. **Show, don't tell.** One concrete example/analogy per concept. Exact commands, not vague descriptions.
5. **Skimmable = the goal.** Headers, **bold** keywords, tables. A skim should give 80% of the value.
6. **Lead with the point.** TL;DR at top for anything longer than a screen.
7. **Number steps; verify each.** For procedures: numbered steps, expected output, a checkpoint, the gotcha called out *before* it bites.
8. **Prerequisites up front.** Time, difficulty, what's needed — before a procedure.
9. **Close the loop.** End substantial docs with **What you learned** + **Next**.
10. **Derive the why, then prove it.** Show why a design must exist from its constraints; give a way to see it (a command, a capture, a test).

## Token-thrift rules (the efficiency layer)
- **No preamble, no filler conclusion.** Start at the first real idea. Cut "In this doc we will…" and "In conclusion…".
- **Cut hedge/adjective words**: very, really, in order to, it's important to note, basically.
- **Tables > prose** for anything with 2+ parallel items (options, params, comparisons, steps).
- **Fragments in lists.** No full sentences where 4 words work.
- **Don't restate the prompt** or repeat what a linked doc already says. Link, don't duplicate.
- **One doc = one topic.** Smaller, linked docs beat one sprawling file — cheaper to load only what's needed.
- **Diagrams only when a flow needs one** (ASCII/mermaid). A diagram can replace three paragraphs.

## Standard folder structure
Create only what you need:
```
docs/
├── README.md          # index + links
├── concepts/          # what things ARE
├── flows/             # how things WORK end-to-end
├── decisions/         # ADRs: why X over Y (dated, numbered)
├── how-to/            # task recipes
└── reference/         # configs, APIs, params, glossary
```
- **Naming:** `NN-kebab-case.md` — numbers set reading order.
- **Front-matter** on every doc: `title`, `summary` (1 line), `updated` (date), `tags`.
- **ADRs:** `decisions/NNNN-title.md` → Context · Decision · Consequences.
- Match the repo's existing docs layout if it has one. Don't impose a second system.

## Doc template
```markdown
---
title: <thing>
summary: <one line>
updated: <YYYY-MM-DD>
tags: [ ]
---

**TL;DR** — <the point in 1–2 lines>

## What it is
<plain-words definition + why it exists>

## How it works
<steps or a small diagram>

## Gotchas
<what bites people>

**Next:** <link / what to read next>
```

## The 20-second check (before saving)
- [ ] Total beginner could follow? (1,2)
- [ ] Skimmable — headers, bold, tables, short chunks? (3,5)
- [ ] Every concept has an example? (4)
- [ ] Steps numbered with expected output + gotchas? (7)
- [ ] Jargon defined once, on first use? (2)
- [ ] Ends with What-you-learned + Next? (9)
- [ ] No filler, no restated prompt, tables where they help? (token-thrift)

If any fails, fix it — **the doc is wrong, not the reader.**

**Next:** pair this with the `phemius` agent, which writes to these rules by default.
