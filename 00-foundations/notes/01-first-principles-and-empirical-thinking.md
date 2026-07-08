# First-principles thinking & empirical thinking — the two engines of learning security

> **TL;DR.** There are two ways to *know* something: **derive it** (first-principles thinking — break a claim down to bedrock facts and rebuild it) and **observe it** (empirical thinking — go look, measure, test). Great engineers run both in a loop: *derive what should be true → check what actually is true → fix the difference.* This note explains both modes, shows them working on real IAM problems, and explains how this repo now bakes them into every note and lab.

---

## 0. First, the name — "principal" vs "principle" 😄

Your manager said **"principal thinking"** out loud — here's the fun part for an IAM person:

- In IAM, a **principal** is an authenticated identity — a user, service, or device that can be granted access. You'll manage *principals* all day.
- The thinking tool is spelled **principle** — a *first principle* is a foundational truth you can't reduce any further.

So: you administer **principals**, and you think in **principles**. Same sound, different word — and now you'll never mix them up in an email to your manager.

---

## 1. First-principles thinking — derive it

### 1a. Plain words first

Most of the time we learn by **copying**: "we hash passwords because that's best practice," "the Response uses POST because that's how it's configured." That's **reasoning by analogy** — fast, useful, and shallow. You know *that*, not *why*.

**First-principles thinking** is the opposite move: take the thing apart until you reach facts you're certain of (**the first principles** — math, how networks physically work, what humans reliably do), then **rebuild the design yourself** from those facts. If you can rebuild it, you truly understand it. If you can't, you've found exactly what you don't yet know.

> **Analogy:** reasoning by analogy is following a recipe. First-principles is knowing enough chemistry to understand *why* the bread rises — which is what you need the day the bread doesn't rise and no recipe covers your situation.

A short lineage, so you know it's not a LinkedIn buzzword: **Aristotle** called a first principle "the first basis from which a thing is known." **Descartes** rebuilt philosophy by doubting everything he couldn't prove. Modern tech folks know it from **Elon Musk's battery example** — everyone "knew" batteries cost $600/kWh, until he priced the raw materials (~$80) and asked what justified the gap. The recipe said expensive; the chemistry said otherwise.

### 1b. The method (five steps)

1. **State the claim** you've been handed. *"SAML assertions must be signed."*
2. **Ask: what would have to be true for this to be necessary?** Keep asking **"why?"** at each layer (the classic **Five Whys**).
3. **Stop at bedrock** — a fact you can't reduce further: *HTTP is stateless. Messages passing through a browser can be altered. Two servers that have never met share no secret.*
4. **Rebuild upward.** Given those facts, design the solution yourself before reading how the spec did it.
5. **Flag what you couldn't derive.** Those are *assumptions* — and assumptions are exactly what empirical thinking (Part 2) exists to test.

### 1c. Worked example — derive SAML from nothing

Try to rebuild federated SSO knowing only bedrock facts:

| Step | Bedrock fact | What it forces |
|---|---|---|
| 1 | Every app checking its own passwords = N password databases to breach | Centralize login at **one** trusted place (an IdP) |
| 2 | The app and the login server may be different companies' servers that never talk directly | The "you're approved" message must travel **through the user's browser** |
| 3 | Anything passing through a browser can be tampered with by the user | The message must be **tamper-evident** → digitally **signed** |
| 4 | Two servers that never met share no secret key | Use **asymmetric crypto** — exchange public certs ahead of time (metadata) |
| 5 | A signed message could be captured and replayed tomorrow | Add an **expiry** (`NotOnOrAfter`) and a **specific audience** |

Congratulations — you just *derived* the SAML assertion, signing certificates, metadata exchange, and validity conditions from scratch. Everything in [`01-identity-access-management/notes/02-saml-deep-dive.md`](../../01-identity-access-management/notes/02-saml-deep-dive.md) is that table with the details filled in. That's why the protocol *feels inevitable* once you see the constraints — and why memorizing it without the constraints feels arbitrary.

**One more, quick:** why does PKCE exist? Bedrock: *a mobile/single-page app cannot keep a secret* (anyone can decompile it). So the classic "prove you're the real client with a client_secret" is impossible. What CAN a secretless client prove? *That it's the same client that started the flow* — invent a one-time secret per login (code_verifier), send only its hash up front, reveal it at redemption. You've derived PKCE. (Full walkthrough: [`19-oauth2-in-practice.md`](../../01-identity-access-management/notes/19-oauth2-in-practice.md).)

### 1d. When to use it — and the traps

**Use it when:** learning a new protocol or product, evaluating a design ("should service X get a client_secret or mTLS?"), or when the runbook doesn't cover your situation (this is where copy-learners get stuck and derivers shine).

**Traps to respect:**

- ⚠️ **It's slow.** Don't first-principle a password-reset ticket at 5pm. Analogy/runbooks exist because they're fast — derive *once* while learning, then cache the result.
- ⚠️ **"I derived it, so I can build it."** No. You can derive *why* crypto signing works; you still **never roll your own crypto**. Deriving buys understanding, not implementation rights.
- ⚠️ **Fake bedrock.** "Users hate MFA" sounds like bedrock but is an assumption (passkeys are *faster* than passwords). If you can still ask "is that actually true?" — you're not at bedrock, you're at a hypothesis. Hand it to Part 2.

---

## 2. Empirical thinking — go look

### 2a. Plain words first

**Empirical thinking** means trusting **observation over assumption**: what does the system *actually do*, not what the docs, the vendor, the senior engineer, or your own beautiful derivation *says* it does. The motto is two words: **"go look."**

Lineage: **Francis Bacon** formalized it (the scientific method — knowledge comes from structured observation, not authority), and W. Edwards **Deming** gave operations its motto: *"In God we trust; all others must bring data."*

> **Analogy:** first-principles thinking is the architect's blueprint. Empirical thinking is walking the actual building with a flashlight. Buildings drift from blueprints — *always*.

### 2b. The loop (it's just the scientific method)

1. **Question** — "Is the assertion to the HR app actually encrypted?"
2. **Hypothesis** — "The connection settings say yes, so it should be."
3. **Experiment** — log in with **SAML-tracer** open and capture the Response. *(Authorized lab or your own session only — Law 10.)*
4. **Observe** — is there an `<EncryptedAssertion>` element, or readable attributes in plain XML?
5. **Update** — believe the capture, not the config page. If they disagree, you've found a finding.

### 2c. Why security *especially* runs on empiricism

- **Docs lie and configs drift.** The wiki says MFA is enforced everywhere; the conditional-access policy has a "temporary" exclusion group from 2023 with 40 members in it. Only looking finds that.
- **Attackers are empiricists.** They don't read your architecture diagram — they *probe what actually responds*. Nmap, credential testing, fuzzing: the entire offensive discipline is structured observation. To defend a system, you must observe it at least as honestly as the attacker will.
- **Audits are institutionalized empiricism.** A PCI-DSS auditor never accepts "our policy says access is reviewed." They ask for **evidence** — the review records, the logs. When you pull last quarter's access-certification report at FinCo, you are doing empirical thinking with a compliance label on it ([`09-pci-dss-and-iam.md`](../../01-identity-access-management/notes/09-pci-dss-and-iam.md)).
- **Debugging is hypothesis testing.** "User can't SSO into app X" → don't guess: pull the transaction from `audit.log`, capture the browser flow, compare the cert in the Response against the metadata. Every step of the debugging playbooks in this repo is an *experiment with an expected output*.

### 2d. Traps to respect

- ⚠️ **An anecdote is not data.** "It worked when I tried it" — once, on your machine, on the corporate network — is a sample size of one. Vary the conditions that matter (external network, different browser, non-admin user).
- ⚠️ **Production is not your laboratory.** "Go look" never means "go poke prod." Reproduce in a lab (Keycloak in Docker, your own VMs) — that's this repo's Law 10 and the entire reason `labs/` exists.
- ⚠️ **You find what you look for.** If you only check the happy path, you'll empirically "confirm" a broken system. Test the *failure* cases too — expired cert, wrong audience, replayed assertion.

---

## 3. The two together — derive, predict, verify

Neither mode is enough alone:

| Only first-principles | Only empirical |
|---|---|
| Elegant theories that don't survive contact with real systems ("the config *should* enforce signing") | A pile of observations with no model — you know *that* it broke, never *why*, so it breaks again |

The power move is the **loop**:

```
   DERIVE  ────────►  PREDICT  ────────►  TEST  ────────►  UPDATE
   (why must it        (so if I look,      (go look,        (model was wrong?
    work this way?)     I should see X)     in a lab)        fix the model)
        ▲                                                        │
        └────────────────────────────────────────────────────────┘
```

That loop has three famous names, and you already live inside all of them:

- Science calls it the **scientific method**.
- Security calls it **purple teaming** — red team empirically tests what blue team's model says should hold ([`07-offensive-security-red-team/`](../../07-offensive-security-red-team/README.md)).
- This repo calls it **notes + labs**: every note *derives* the why; every lab makes you *observe* it with your own eyes.

**Which mode leads, when:**

| Situation | Lead with | Because |
|---|---|---|
| Learning a new protocol/product | First principles | Constraints make the design memorable instead of arbitrary |
| A ticket / outage right now | Empirical | Capture and logs beat theories at 5pm; derive the root cause *after* |
| "Is our control actually working?" | Empirical | Configs drift; only evidence counts (and it's what the auditor will ask) |
| Designing something new | First principles → empirical | Derive the design, then prototype it in a lab before trusting it |
| A senior says "that's just how it's done" | Both, politely | Derive why it *might* be done that way, then verify — sometimes it's wisdom, sometimes it's 2019's workaround fossilized |

### Why managers teach this pair

A junior who runs on runbooks alone plateaus at "executes tickets." A junior who asks **"why is it designed this way?"** (derivation) and **"how do we know it's actually true?"** (evidence) is doing the two things that define a senior engineer: building transferable models and distrusting unverified claims. Your manager handing you these two words was handing you the actual promotion criteria, disguised as philosophy.

---

## 4. How this repo bakes both in

This isn't just a note — it's now house policy:

1. **[Lefler's Law 12 — *Derive the why, then prove it*](../../LEFLER-LAWS.md).** Every substantial note must (a) show *why the design must exist* from its constraints, not just describe it, and (b) give you a way to **see it for yourself** — a lab, a capture, a command with expected output.
2. **Notes derive, labs verify.** Treat every `notes/` doc as the derivation and every `labs/` folder as the experiment. If a note makes a claim you can't observe anywhere, that's a gap — file it as a lab idea.
3. **The two-question test** (use it on every new concept, forever):
   - 🧱 *"Could I re-derive this from its constraints on a whiteboard?"* — if no, you memorized it; find the why.
   - 🔬 *"Have I seen it with my own eyes in a lab?"* — if no, you're trusting a doc; go capture it.

Two ✅ and the concept is genuinely yours — that's the bar this repo now aims for on every topic.

---

## 5. What you learned

- **First-principles thinking** = reduce a claim to bedrock facts, then rebuild it — you derived SAML's signatures and PKCE from raw constraints.
- **Empirical thinking** = trust observation over assumption — configs drift, docs lie, and auditors (and attackers) only respect evidence.
- The two run as a **loop** — derive → predict → test → update — which is also the scientific method and purple teaming.
- **Principal** = an identity in IAM; **principle** = a foundational truth. You manage the first, think in the second.
- The repo now enforces the pair via **Law 12** and the **two-question test**.

## Next

→ Apply the two-question test to the newest IAM notes: derive Kerberos's design in [`15-kerberos-explained.md`](../../01-identity-access-management/notes/15-kerberos-explained.md), then *observe* a real token flow in the [Keycloak lab](../../01-identity-access-management/labs/01-keycloak-idp/README.md).
