# 09 · Threat Intelligence

> Threat intelligence is knowing who is coming for you and how they operate — and as a fintech, FinCo sits squarely in the crosshairs of the most motivated, best-funded adversaries there are.

**Agents to use here:** ask **Mimir** for concepts and theory, **Heimdall** for operationalizing intel into detections, and **Loki** for understanding adversary TTPs from the attacker's point of view.

## Core concepts (learn in this order)

### 1. What CTI is & the intelligence lifecycle

- Cyber Threat Intelligence (CTI) is *analyzed* information about threats — not raw data. A list of bad IPs is data; "FIN7 is targeting payment processors in APAC with this phishing lure, so watch these mailboxes" is intelligence. The difference is context, relevance, and actionability.
- The **intelligence lifecycle** is the discipline's backbone. Learn its six phases and why each exists:
  - **Direction** — define what questions the intel program must answer (intelligence requirements). Bad direction = collecting everything, answering nothing.
  - **Collection** — gather raw data: feeds, logs, OSINT, dark web, vendor reports, ISAC sharing.
  - **Processing** — normalize, deduplicate, translate, structure (e.g., into STIX objects).
  - **Analysis** — the human/analytical step: assess reliability, connect dots, form judgments with stated confidence levels.
  - **Dissemination** — deliver the right product to the right audience (a CISO briefing looks nothing like a SOC detection rule).
  - **Feedback** — did the intel help? Refine requirements and repeat.
- Key habit to build now: always ask "who is the consumer of this intel, and what decision does it support?"

### 2. Levels of intelligence

- **Strategic** — long-term, non-technical, for executives: threat landscape, geopolitical drivers, risk to the business ("ransomware groups are increasingly targeting fintech payment infrastructure").
- **Operational** — campaign-level: who is attacking, what campaigns are active, what capabilities they have. Feeds threat hunting and incident response prioritization.
- **Tactical** — technical and immediately actionable: TTPs, IOCs, detection logic. Consumed by SOC analysts and detection engineers.
- Understand which level each source and product serves — mixing them up is the most common beginner mistake (e.g., handing an executive a list of file hashes).

### 3. IOCs vs IOAs vs TTPs

- **IOC (Indicator of Compromise)** — forensic artifact of an intrusion that already happened: file hash, IP address, domain, registry key. Reactive by nature.
- **IOA (Indicator of Attack)** — evidence of attacker *behavior* in progress: a process spawning PowerShell to disable logging, regardless of which malware did it. Proactive.
- **TTPs (Tactics, Techniques, Procedures)** — the adversary's playbook at three altitudes: *tactic* = the goal (credential access), *technique* = the method (kerberoasting), *procedure* = the specific implementation (Rubeus with these flags).
- Internalize why defenses built on TTPs outlast defenses built on IOCs — which leads directly to the next concept.

### 4. The Pyramid of Pain

- David Bianco's model ranking indicator types by how much *pain* denying them causes the adversary:
  - Bottom (trivial for attackers to change): hash values, IP addresses, domain names.
  - Middle: network/host artifacts, tools.
  - Top (hardest to change): **TTPs** — forcing an adversary to change *how they operate* is the most expensive thing you can do to them.
- This single diagram explains why the industry moved from signature-based blocking to behavior-based detection. Refer back to it constantly.

### 5. MITRE ATT&CK

- The de facto common language of CTI. A knowledge base of real-world adversary behavior organized as **tactics** (columns — the "why": Initial Access, Credential Access, Lateral Movement...) → **techniques** (the "how": T1078 Valid Accounts, T1110 Brute Force) → **procedures** (documented real-world usage by specific groups and software).
- Learn to navigate the Enterprise matrix fluently; know the tactic names by heart. Spend real time in **Credential Access (TA0006)** and the **Valid Accounts (T1078)** technique family — this is your IAM home turf.
- **ATT&CK Navigator** — the web tool for building layers: highlight the techniques a threat group uses, overlay your detection coverage, and see the gaps. This is the single most practical CTI skill you can build early.
- Also explore the group (G-prefixed) and software (S-prefixed) entries — e.g., G0046 FIN7 — which link actors to their techniques.

### 6. The Diamond Model

- Every intrusion event has four vertices: **Adversary**, **Capability**, **Infrastructure**, **Victim**. Analysis is pivoting between them: known infrastructure → what capabilities were deployed from it → which victims were hit → which adversary that implies.
- Powerful for clustering activity into campaigns and for structured attribution reasoning. Pairs naturally with the Kill Chain (Diamond = analysis of a single event; Kill Chain = sequence of events).

### 7. The Cyber Kill Chain

- Lockheed Martin's seven-phase model of an intrusion: Reconnaissance → Weaponization → Delivery → Exploitation → Installation → Command & Control → Actions on Objectives.
- Core insight: defenders only need to break **one** link to stop the attack, and mapping detections to phases shows where you're blind.
- Know its limitations too: it models perimeter-breach intrusions well but insider threats and credential-abuse attacks (very IAM-relevant) poorly — ATT&CK handles those better. Being able to articulate when each model fits is a mark of depth.

### 8. Threat actors & motivations

- **Nation-state** — espionage, sabotage, sanctions evasion, and (notably for finance) direct theft. Patient, well-resourced, stealthy.
- **Cybercrime** — profit. Ransomware operators, access brokers, carding crews, banking-trojan operators. The dominant threat to fintech by volume.
- **Hacktivist** — ideology and attention: defacement, DDoS, leaks.
- **Insider** — employees or contractors, malicious or negligent. Uniquely an *identity* problem: they already have valid credentials, which is why IAM controls are the primary defense.
- Motivation predicts behavior: a profit-driven actor abandons a hardened target; an espionage actor doesn't. This shapes how you prioritize defenses.

### 9. APT groups

- "Advanced Persistent Threat" — actors with long-term objectives who maintain access over months or years. Learn the naming chaos: the same group gets different names per vendor (APT29 = Cozy Bear = Midnight Blizzard).
- Study a few deeply rather than many shallowly. For finance, start with **Lazarus Group (APT38)** — North Korea's financially motivated arm behind the Bangladesh Bank SWIFT heist ($81M) and numerous crypto-exchange thefts. It is the clearest example of a nation-state actor with direct financial-theft objectives.

### 10. Financial-sector threats

This is your sector — go deep here.

- **FIN groups** — Mandiant's designation for financially motivated actors: **FIN7** (Carbanak backdoor, point-of-sale and payment-card theft, later ransomware affiliations), **FIN6** (POS intrusions, card data theft), **FIN8** (POS malware, backdoors against hospitality/finance).
- **Banking trojans** — the lineage matters: Zeus → its leaked source spawned an ecosystem; Emotet (trojan turned malware-delivery botnet); TrickBot; QakBot/Qbot; Dridex (Evil Corp). Many evolved into ransomware initial-access vectors.
- **Magecart** — web-skimming: injecting JavaScript into checkout pages to steal card data in the browser (British Airways, Ticketmaster breaches). Directly relevant to any company processing payments.
- **BEC (Business Email Compromise)** — no malware needed: compromised or spoofed email identities tricking finance teams into fraudulent wire transfers. Consistently the highest-loss crime category in FBI IC3 reports. Note that it is fundamentally an *identity* attack.
- **Card fraud ecosystem** — skimming, carding shops, fullz markets — understand the criminal economy your company's products defend against.

### 11. Threat feeds & sharing

- **STIX** (Structured Threat Information eXpression) — the standard JSON format for describing threats as objects (indicator, malware, threat-actor, attack-pattern) and relationships between them. **TAXII** is the transport protocol for exchanging STIX over HTTPS. Learn to read a STIX 2.1 bundle by hand.
- **ISACs** (Information Sharing and Analysis Centers) — sector-based trust communities. **FS-ISAC** is the financial sector's — FinCo is the kind of member organization it exists for. Real, non-public, sector-specific intel flows through it.
- Understand **TLP** (Traffic Light Protocol: RED/AMBER/GREEN/CLEAR) — the rules governing what shared intel you may re-share. Violating TLP destroys trust relationships.
- Be skeptical of raw feed value: an unvetted feed of a million IPs generates alerts, not intelligence.

### 12. OSINT

- Open-source intelligence: what can be learned about a target (or an adversary) from public sources — DNS/WHOIS, certificate transparency logs, Shodan/Censys, code repos, social media, breach corpuses, job postings.
- Two directions: adversaries use OSINT for reconnaissance against you; you use it to profile adversary infrastructure and to see your own attack surface as they do.
- Practice safely and legally: passive collection against your own organization or deliberately public data only.

### 13. Dark web monitoring

- What actually lives there for a fintech to care about: stolen credential dumps, initial-access broker listings ("access to a US payment processor, $15k"), carding shops, ransomware leak sites, insider-recruitment posts.
- For IAM specifically: monitoring for your organization's leaked credentials is a direct input into forced resets, session revocation, and MFA policy. Services like Have I Been Pwned and commercial identity-monitoring feeds operationalize this.
- Understand the access-broker economy — most ransomware incidents begin with purchased credentials or access, not novel exploits.

### 14. Threat modeling with intel

- Turn intel into prioritization: build a **threat profile** — which actors plausibly target an organization like yours, which ATT&CK techniques they use, which of your assets they'd want — then rank defensive investments against that profile instead of generic best-practice lists.
- Learn to run a simple crown-jewels analysis: for a payments company, that's payment rails, customer PII/card data, and the *identity infrastructure that gates access to both*.

### 15. Attribution (and its limits)

- Attribution spans a spectrum: clustering activity ("this is the same actor as last month") → naming a group → naming a country or person. Each step up requires exponentially more evidence.
- Know the pitfalls: false flags, shared tooling (many groups use Cobalt Strike and Mimikatz), recycled infrastructure, and analyst bias. Learn confidence language ("we assess with moderate confidence...") and Admiralty-style source grading.
- The practical takeaway: for a defender, *what* the adversary does (TTPs) usually matters more than *who* they are. Attribution informs strategy; TTPs inform defense.

## Reading list

- **MITRE ATT&CK** — [attack.mitre.org](https://attack.mitre.org). Read the "Getting Started" resources and the *ATT&CK Design and Philosophy* paper, then live in the Enterprise matrix. Free.
- **Intelligence-Driven Incident Response** — Scott J. Roberts & Rebekah Brown (O'Reilly). The best single book on operational CTI; introduces the F3EAD cycle for fusing intel with IR. Read cover to cover.
- **Lockheed Martin, "Intelligence-Driven Computer Network Defense Informed by Analysis of Adversary Campaigns and Intrusion Kill Chains"** — Hutchins, Cloppert & Amin (2011). The original Cyber Kill Chain paper. Short and foundational.
- **"The Diamond Model of Intrusion Analysis"** — Caltagirone, Pendergast & Betz (2013). The primary source; more readable than you'd expect, and the pivoting sections are gold.
- **David Bianco, "The Pyramid of Pain"** — his Enterprise Detection & Response blog post (2013, revised 2014). Ten minutes that reframes everything.
- **MISP documentation** — [misp-project.org](https://www.misp-project.org). The leading open-source threat intelligence platform; the docs double as a course in how intel is structured and shared.
- **FS-ISAC** — [fsisac.com](https://www.fsisac.com). Read their public reports and advisories to understand financial-sector sharing; ask internally whether FinCo's membership gives you portal access.
- **Vendor threat reports** — read real APT reporting to learn how professionals write: Mandiant **M-Trends** (annual), the original **Mandiant APT1 report** (2013 — the report that defined public attribution), **CrowdStrike Global Threat Report** (annual), and **Recorded Future / Insikt Group** public research. All free.
- **SANS FOR578: Cyber Threat Intelligence** — the reference training course for this domain. The course itself is expensive, but the free FOR578 poster, SANS CTI Summit talks (on YouTube), and associated whitepapers are excellent and free.

## Labs / exercises (ask Heimdall or Lefler)

Labs live in `labs/NN-name/` inside this folder.

| # | Exercise | You'll learn |
|---|----------|--------------|
| 1 | Map a public incident (e.g., the Bangladesh Bank heist) to ATT&CK using Navigator: build a layer of every technique used | ATT&CK fluency, Navigator layers, reading incident narratives with a technique lens |
| 2 | Build a threat profile for a fictional Chennai-based payments fintech: likely actors, their TTPs, crown jewels, top 5 defensive priorities | Threat modeling with intel, actor-to-asset reasoning, writing for a decision-maker |
| 3 | Stand up a MISP instance (Docker) and ingest a public feed; explore events, attributes, and tags | TIP mechanics, feed hygiene, how shared intel is actually structured |
| 4 | Analyze a real public APT report (Mandiant APT1 or a recent FIN7 report): extract actors, capabilities, infrastructure, victims, and TTPs into a structured summary | Critical reading of vendor reporting, separating evidence from assessment, confidence language |
| 5 | Take the IOCs from that same report and hand-author them as a valid STIX 2.1 bundle (indicator, malware, threat-actor objects + relationships) | STIX object model, machine-readable intel, why structure enables sharing |
| 6 | OSINT investigation on a provided sample domain/persona (passive only): WHOIS, DNS history, cert transparency, breach exposure | OSINT tradecraft, pivoting between artifacts, documenting a collection trail ethically |
| 7 | Build a Diamond Model diagram of one intrusion event from lab 4, then pivot: list what each vertex lets you discover about the others | Diamond analysis, analytic pivoting, event clustering |
| 8 | Credential-intel drill: given a mock dark-web credential dump for your fictional fintech, decide the IAM response — which accounts to reset, sessions to revoke, policies to change — and write the detection logic for reuse attempts | Turning intel into IAM action; the intel-to-detection pipeline with Heimdall |
| 9 | Detection gap analysis: overlay FIN7's ATT&CK techniques against a sample detection inventory in Navigator and write a prioritized gap report | Coverage mapping, Pyramid-of-Pain-driven prioritization, communicating gaps |

## How this connects to IAM / fintech

Identity **is** the modern attack surface, and ATT&CK proves it: **Valid Accounts (T1078)** is among the most-used techniques across every threat group, and the entire **Credential Access** tactic — phishing for credentials, kerberoasting, password spraying, MFA fatigue, token theft — exists to defeat what IAM builds. When you read intel on a credential-theft campaign, you are reading a to-do list for IAM: which auth flows to harden, which conditional-access policies to add, which accounts to monitor. Dark-web credential monitoring feeds directly into reset and revocation workflows; BEC intel drives email-identity controls; insider-threat intel shapes least-privilege and access-review priorities. And because FinCo is a financial institution, **FS-ISAC** is the sharing community where sector-specific versions of all of this circulate first — learn to consume it early. In this domain, threat intelligence isn't adjacent to your IAM career; it's the targeting data that tells you where to point it.
