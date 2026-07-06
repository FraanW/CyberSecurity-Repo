# ⚙️ Lefler's Laws — the house style for every doc & lab in this repo

> **What this is.** Lefler is the squad's Lab Engineer: *resourceful, practical, safety-first, beginner-aware.* These are his laws for writing. **Every note, lab, README, and artifact in this repo follows them** so Farhaan can read anything here and *get it* — fast, without feeling lost. If a doc breaks a law, the doc is wrong, not the reader.

---

## The Laws

**Law 1 — Beginner first.** Assume zero prior knowledge. Define a term the first time it appears. Never make the reader feel dumb for not knowing something.

**Law 2 — Plain words before jargon.** Say it in everyday English first, *then* name the technical term.
> *"the app that trusts the login — the **Service Provider (SP)**"* — not just "the SP."

**Law 3 — One idea per chunk.** Short sentences. Short paragraphs (2–4 lines). Break every wall of text with a header, table, or list. If a paragraph has three ideas, make it three chunks.

**Law 4 — Show, don't just tell.** Every concept earns a concrete **example**, **analogy**, or **diagram**. Every instruction shows the **exact command**, not a vague description.

**Law 5 — Skimmable by design.** A reader skimming headers, bold keywords, and tables should get **80%** of the value. Put a one-line **TL;DR** or summary where it helps. Lead with the point.

**Law 6 — Number the steps; verify each one.** For anything you *do*: numbered steps, the **expected output**, a **✅ checkpoint** to confirm it worked, and the **gotcha** called out *before* it bites.

**Law 7 — Prerequisites up front.** Before a lab or procedure, state **time, difficulty, what's needed, and what machine it fits.** Farhaan is on **Windows 11 (PowerShell + a Bash tool)** — give PowerShell and Bash variants when they differ.

**Law 8 — Always connect to the job.** Tie every concept back to Farhaan's reality: **IAM at FinCo (fintech)**. Answer "why do I care?" — the ticket it causes, the audit it satisfies, the question it lets him ask.

**Law 9 — Pair attacks with defenses.** Never teach an attack without its **detection and mitigation**. (This is a repo-wide rule; Lefler enforces it in writing.)

**Law 10 — Safe & reproducible.** Everything hands-on is **authorized-lab-only** (his own VMs/containers, never production/FinCo/third parties). Use disposable targets, include **cleanup/teardown**, and **never** put real secrets in the repo (use placeholders; `.gitignore` blocks keys/certs/`.env`).

**Law 11 — Close the loop.** End substantial docs with **"What you learned"** and a **"Next"** pointer, so every doc leads somewhere.

---

## The 20-second checklist (run it before saving any doc)

- [ ] Could a **total beginner** follow this? (Law 1, 2)
- [ ] Is it **skimmable** — headers, bold, tables, short chunks? (Law 3, 5)
- [ ] Does every concept have an **example/analogy/diagram**? (Law 4)
- [ ] Steps **numbered**, with **expected output** and **gotchas**? (Law 6)
- [ ] **Prerequisites** stated (and Windows/PowerShell-aware)? (Law 7)
- [ ] Tied back to **Farhaan's IAM/fintech job**? (Law 8)
- [ ] Any attack **paired with a defense**? (Law 9)
- [ ] **Authorized-lab-only**, **cleanup** included, **no real secrets**? (Law 10)
- [ ] Ends with **"what you learned" + "next"**? (Law 11)

---

## Before → after (what the laws feel like)

**❌ Breaks the laws:**
> The SP consumes the assertion at the ACS and validates the signature against the IdP's configured X.509 cert before establishing a session.

*(One dense sentence, five undefined acronyms, no example, no "why.")*

**✅ Follows the laws:**
> When you open an app (the **app** is called the **Service Provider**, or **SP**), it receives a signed "you're approved" note from the login server (the **assertion**). The SP checks the note's signature — like verifying a hologram on an ID — using a certificate it was given ahead of time. If it checks out, you're let in.
>
> **Why you care:** if that certificate expired, *every* app breaks at once. That's a top cause of "SSO is down" tickets.

*Same facts. One is a wall; the other teaches.*

---

*Curated with Lefler ⚙️. Amend freely — but keep them beginner-first.*
