# First-week questions & the incident-channel decoder

> **Janus's onboarding playbook.** You will *not* be expected to know the answers in week one. You **will** stand out by asking **precise** questions that show you understand the *shape* of the problem. This note turns the protocol knowledge from notes 01–04 into (a) a decoder for the jargon flying past you, (b) questions tailored to each person you'll talk to, and (c) the truth about "using AI to resolve tickets" in a fintech. Prereq: skim [notes 01–04](01-iam-protocol-landscape.md).

---

## A. Decode the incident channel — jargon → meaning → the question it should trigger

When you hear this in a standup or ticket, here's what it means and the smart follow-up:

| You hear… | It means… | Your sharp follow-up |
|---|---|---|
| "SSO is down for App X" | Federation between the IdP and that SP broke | "SP-initiated or IdP-initiated? SAML or OIDC? Did a cert or metadata change recently?" |
| "The assertion is failing validation" | SP rejected the SAML Response | "Is it signature, audience, or the NotBefore/NotOnOrAfter window? Should we check clock skew / NTP?" |
| "Cert's expiring / expired" | An IdP or SP signing certificate lapsed | "Which entity's signing cert? Do we have a rotation runbook and a list of affected SPs?" |
| "Token's invalid / 401 from the API" | OAuth/OIDC access token rejected | "Expired, wrong audience, or wrong scope? Is it the access token or did someone send the ID token?" |
| "Conditional Access blocked them" | Entra Zero-Trust policy denied sign-in | "Which policy — device, location, or risk? Is this expected or a false positive?" |
| "Sync is broken / stale in Entra" | Entra Connect isn't reflecting on-prem AD | "Password hash sync or PTA? What's the last successful sync time?" |
| "They're a Leaver but still have access" | Deprovisioning failed | "Where did JML break — the directory, the sync, or the app's SCIM feed?" — *treat as high priority; it's an audit finding.* |
| "Access review / recert is due" | IGA campaign for SOX/PCI evidence | "Which system, which reviewers, and what's the SoD rule we're enforcing?" |
| "It's a PAM issue" | Privileged credential/session problem | "Is it vault check-out, rotation, or session recording? Which privileged account?" |
| "Just re-provision them in SailPoint" | Re-run the IGA provisioning | "Which role/entitlement set, and will that trigger downstream SCIM to the app?" |
| "Kerberos ticket problem" | On-prem AD auth failure | "TGT or service ticket? SPN misconfig? Time skew again?" |
| "It's a SCIM issue" | Auto-provisioning to a SaaS app failed | "Create, update, or deprovision? What did the SCIM response say?" |

> Keep your own running version of this table as you learn *your* environment's specifics. It becomes your personal runbook.

---

## B. Questions by audience — who to ask what

The art is matching the **altitude** of the question to the person. Managers and directors want *context and priorities*; senior engineers want to teach you *mechanics*; leads own *process*.

### To your **manager** (1:1s, onboarding)
Purpose: expectations, priorities, how you'll be measured.
- "What does **success in my first 30 / 60 / 90 days** look like to you?"
- "Which **systems and ticket types** should I focus on learning first?"
- "Who should be my **go-to person** for hands-on questions so I'm not blocking you?"
- "What's the **most common category of incident** the team handles, and where do new folks add value fastest?"
- "Are there **compliance deadlines** (SOX access reviews, PCI audits) coming up I should understand?"
- "What's a mistake new IAM engineers here tend to make that I can avoid?"

### To your **senior engineers / onboarding buddy** (the mechanics)
Purpose: how things actually work here. These are your highest-frequency, most valuable questions.
- "Can you **walk me through one real ticket** end to end — where do you look first?"
- "Where are the **runbooks**, and which ones do you use weekly?"
- "When a SAML login fails, what's your **debugging order**?" *(Compare to your [note 02](02-saml-deep-dive.md) checklist — see where reality differs.)*
- "How do we **capture and read assertions/tokens** here — SAML-tracer, logs, a portal?"
- "What's our **cert-rotation** process, and how do we know which SPs an IdP cert affects?"
- "How does our **AD ↔ Entra sync** work, and how do you check sync health?"
- "Which **service accounts** are highest-risk, and how are their secrets managed?"

### To your **team lead** (process & ownership)
Purpose: how work flows, escalation, on-call.
- "What's the **escalation path** when I can't resolve a ticket — and when *should* I escalate vs keep digging?"
- "How does **on-call** work, and what should I shadow before I'm in the rotation?"
- "How are tickets **prioritized** — what makes something a P1?"
- "What **change-management** rules apply when I touch a federation config or a group?" *(In fintech, changing access = a controlled change.)*
- "Which recurring incidents are **known issues** vs one-offs?"

### To your **director** (strategy — earn the right, then ask a few great ones)
Purpose: business context and direction. Ask fewer, higher-altitude questions; show you think beyond your ticket queue.
- "Where is our **IAM strategy** heading over the next year — Zero Trust maturity, cloud migration, IGA modernization?"
- "What **identity risks** keep you up at night for a company that moves money?"
- "How does the IAM team's work **map to regulatory/business outcomes** (PCI, SOX, RBI, customer trust)?"
- "What would make you say the IAM program **leveled up** this year?"

> **Etiquette:** with the director, lead with listening. One thoughtful question in a town hall or skip-level beats ten. Bring context ("I've been learning our federation setup…"), not just curiosity.

---

## C. Questions by topic — map *their* environment (do this in week 1–2)

Your goal early on is to build the **map of your actual stack**. Fill in this table by asking around — it turns every abstract note into concrete reality:

| Layer | Question | Their answer (fill in) |
|---|---|---|
| **IdP / Access Mgmt** | "Is our primary IdP **Entra ID, Okta, Ping, ForgeRock**, or a mix?" | |
| **Directory** | "On-prem **AD**, cloud **Entra**, or hybrid with **Entra Connect**?" | |
| **Federation protocols** | "Which apps are **SAML** vs **OIDC**? Any legacy WS-Fed?" | |
| **IGA** | "**SailPoint, Saviynt, Okta IG**, or in-house for access requests & reviews?" | |
| **PAM** | "**CyberArk, BeyondTrust, Delinea** for privileged access?" | |
| **MFA** | "What factors — push, TOTP, **FIDO2/passkeys**? Any phishing-resistant mandate?" | |
| **Provisioning** | "Is app provisioning **SCIM**-based, or scripted/manual?" | |
| **Zero Trust** | "Where do **Conditional Access** policies live and who owns them?" | |

Once this table is filled, you know *exactly* which of notes 01–04 to go deepest on, and you can ask **Janus** to tailor labs to your real vendors.

---

## D. "AI dev in resolving incidents/tickets" — the honest version (fintech guardrails)

You heard this because **AI/LLM assistants are increasingly used in IAM/SOC operations**. Here's what that actually looks like — and the lines you must not cross in a fintech.

### Where AI genuinely helps IAM ops
- **Ticket triage & summarization** — condense a long incident thread into "what/where/who/next step."
- **Explaining errors** — paste a (sanitized) SAML `StatusCode` or OIDC error and get "here's likely why + what to check."
- **Decoding artifacts** — reason about the *structure* of a redacted assertion/JWT (which claim is missing, is `Audience` wrong).
- **Drafting** — runbooks, access-review justifications, LDAP search filters, PowerShell/Graph snippets, post-incident writeups.
- **Knowledge lookup** — "what's the difference between PHS and PTA," "what does SCIM `active:false` do" — like having a patient senior engineer on tap.
- **Detection engineering support** — draft SIEM queries/alert logic (then verify with **Heimdall**).

### The fintech guardrails — non-negotiable
> You work where money and regulated data live. Treat AI tools like any other third party you might leak data to.

- **Never paste secrets or live credentials** into an AI tool: passwords, client secrets, private keys, **real tokens/assertions**, API keys. (Your repo's `.gitignore` blocks `*.pem`, `*.key`, `*.p12`, `secrets*`, `credentials*` for the same reason — the habit transfers.)
- **Never paste customer PII / cardholder data / account data.** PCI-DSS and privacy law apply. Redact aggressively; use dummy values (`farhaan@example.com`, `_assertABC`).
- **Use only approved / enterprise AI tooling.** Ask: *"What's our sanctioned AI tool and data-handling policy?"* Consumer chatbots may train on or retain input — a data-exfiltration path.
- **Keep a human in the loop for access decisions.** AI can *suggest*; a person **approves** granting/revoking access. Automated privilege changes without review is how you fail an audit (or cause an incident).
- **Verify, don't trust.** LLMs are confidently wrong. Confirm against official docs, your runbooks, and the actual system before acting on a production change.
- **Log & attribute.** In regulated environments, actions need an accountable human and an audit trail — "the AI told me to" is not a control.

### Sharp questions to ask about AI on your team
- "What's our **approved AI tooling** and the **data-classification rules** for what I can put into it?"
- "Where is AI **already in our workflow** (triage, detection, drafting) and where is it **prohibited**?"
- "For any AI-assisted change, what's the **human-approval and audit** requirement?"

> This makes you the new person who understands both the *upside* (faster tickets) and the *risk* (data leakage, unreviewed changes) — exactly the balance a fintech wants.

---

## E. How to *be* the person who asks good questions

- **Draw the diagram.** For every incident, sketch the [note 01 §6 login flow](01-iam-protocol-landscape.md) and mark where it broke. Ask which hop failed. Diagrams make you look (and think) senior.
- **Confirm understanding out loud.** "So the assertion is valid but the *audience* is wrong — meaning the app's entityID changed?" Restating earns trust and catches misunderstandings early.
- **Take notes relentlessly** and build your personal runbook/glossary (extend section A).
- **In an incident, don't guess — narrate your reasoning and escalate at the right moment.** "I've confirmed signature and audience are fine; NotBefore looks borderline so I suspect clock skew — is that a known issue, or should I escalate?"
- **Close the loop.** After a ticket resolves, ask "what was the root cause?" and write it down. That's how one ticket teaches you ten.
- **Pair every attack you learn with a defense** (your repo's rule) — it shows security maturity, not just curiosity.

---

## F. Worked example — your first (mock) SAML ticket

**Ticket:** *"Users intermittently can't log into Workday — sometimes it works, sometimes 'authentication failed.'"*

Apply your [note 02](02-saml-deep-dive.md) checklist, and voice the questions:
1. "Is Workday **SP- or IdP-initiated**?" → establishes the flow.
2. "Let's **capture a failing SAML Response** (SAML-tracer) and a working one — what differs?"
3. Status code says `Success` but SP rejects → not an auth failure at the IdP.
4. Signature verifies, audience matches, ACS matches → not those.
5. **`NotBefore` is sometimes a couple minutes in the future** on failures → 💡 **clock skew**.
6. Question to the team: "One of the IdP/SP nodes looks out of time-sync — **can we check NTP** on the SP fleet, and does the SP allow a clock-skew tolerance?"

You just diagnosed a classic intermittent SSO failure on day one — because you knew *where to look*. **That's** the payoff of notes 01–04.

---

## Your next moves
- **Do the labs** so this is muscle memory: [Lab 01 — Keycloak/OIDC](../labs/01-keycloak-idp/README.md), [Lab 02 — SAML assertion anatomy](../labs/02-saml-assertion-anatomy/README.md).
- **Fill in section C** with your real stack, then ask **Janus** to go deep on your actual vendors.
- **Bring one great question** from section B to your next 1:1.

*— Janus 🔐*
