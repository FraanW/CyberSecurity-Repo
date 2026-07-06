# 06 · Security Operations / Blue Team

> This is where security stops being theory: watching an enterprise in real time through its logs, catching attackers mid-move with SIEM and detection rules, and running incident response when something gets through — the discipline that decides whether a breach is a footnote or a headline.

**Agents:** Ask **Mimir** to teach concepts, **Lefler** to build labs, **Heimdall** (your primary agent for this domain) for detection engineering and incident response work, and **Loki** to generate attacks for you to detect — that pairing is purple teaming, and it's the fastest way to learn detection.

## Core concepts (learn in this order)

### 1. SOC roles & tiers
- What a Security Operations Center does: continuous monitoring, triage, investigation, response.
- Tier 1 (alert triage), Tier 2 (investigation), Tier 3 (hunting, forensics, detection engineering) — and the modern trend toward tierless SOCs.
- Adjacent roles you'll work with: detection engineers, threat intel analysts, IR responders, and IAM teams (you'll be on the other side of many SOC escalations at FinCo).
- Key metrics: MTTD (mean time to detect), MTTR (mean time to respond), alert-to-incident ratio.

### 2. Logging & telemetry sources
- Where signal comes from: OS logs (Windows Event Log, Linux syslog/auditd), authentication logs, DNS, proxy/firewall, cloud audit logs (CloudTrail, Azure Activity Log), application logs.
- Windows Event IDs every defender memorizes: 4624/4625 (logon success/failure), 4672 (special privileges), 4688 (process creation), 4720 (account created), 4728/4732 (group membership changes).
- Sysmon: why Microsoft's free sensor is the single biggest telemetry upgrade for a Windows fleet, and what its event types cover (process creation, network connections, image loads).
- Log formats and transport: syslog, JSON, CEF, Windows Event Forwarding, agents (Beats, universal forwarder).
- The hard truth: you can't detect what you don't log. Coverage gaps are the attacker's friend.

### 3. SIEM fundamentals (Splunk / ELK / Sentinel)
- What a SIEM actually does: collect, normalize, index, correlate, alert, retain.
- The three you'll encounter: **Splunk** (SPL, dominant in fintech), **Elastic/ELK** (open source, Elasticsearch + Logstash + Kibana), **Microsoft Sentinel** (KQL, cloud-native — and deeply tied to Entra ID, which matters for IAM).
- Data models and normalization (ECS in Elastic, CIM in Splunk) — why field naming consistency makes or breaks correlation.
- Retention, licensing-by-ingest, and why "log everything forever" isn't a strategy.

### 4. Log analysis & correlation
- Reading raw logs fluently before you lean on dashboards — timestamps, time zones, and time synchronization (NTP) as the foundation of every investigation.
- Single-event vs. correlated detections: one failed login is noise; 50 failures then a success from a new country is a story.
- Pivoting: from an alert to a user, to a host, to a process, to a network connection, and back.
- Baselines and anomalies: you can't spot "weird" until you know "normal."

### 5. Detection engineering
- Detection-as-code: rules in version control, peer-reviewed, tested, deployed through CI — not hand-edited in a console.
- **Sigma**: the vendor-neutral rule format (YAML) that converts to SPL, KQL, and Elastic queries. Learn its structure: logsource, detection, condition, level.
- Mapping every detection to **MITRE ATT&CK** techniques (e.g., T1110 Brute Force, T1078 Valid Accounts) so you can measure coverage and find gaps.
- The detection lifecycle: hypothesis → rule → test against real attack data → tune → deploy → measure.
- Precision vs. recall: a rule that fires on everything detects nothing, because analysts stop reading it.

### 6. Writing queries (SPL / KQL basics)
- **SPL** (Splunk): `search`, `stats`, `eval`, `where`, `table`, `timechart`, and the pipe-based mental model.
- **KQL** (Sentinel / Defender): `where`, `summarize`, `project`, `join`, `bin()` — you will use this constantly if FinCo runs Entra ID / Microsoft security tooling.
- Aggregation patterns that power detections: count by user, distinct sources per account, first-seen/last-seen, rare-value analysis.
- Practice goal: given a plain-English question ("which accounts failed MFA more than 5 times today?"), write the query without looking anything up.

### 7. EDR / XDR
- Endpoint Detection & Response: process trees, behavioral detections, memory inspection, and response actions (isolate host, kill process) — vs. legacy antivirus signatures.
- XDR: correlating endpoint, identity, email, and cloud signals in one platform (e.g., Microsoft Defender XDR, CrowdStrike Falcon).
- What EDR telemetry looks like and how it feeds the SIEM.
- Limits: EDR blind spots (unmanaged devices, BYOD, network appliances) and why identity signals fill part of that gap.

### 8. The incident response lifecycle (NIST SP 800-61)
- The canonical phases: **Preparation → Detection & Analysis → Containment, Eradication & Recovery → Post-Incident Activity (lessons learned)**.
- Preparation: playbooks, contact trees, evidence handling, and having access *before* the incident (an IAM concern — break-glass accounts).
- Containment strategy trade-offs: watch-and-learn vs. immediate isolation; short-term vs. long-term containment.
- Eradication and recovery: credential resets, token revocation, rebuilding vs. cleaning — note how identity-heavy modern eradication is.
- Blameless post-incident reviews and turning lessons into new detections.
- Severity classification, escalation paths, and communication (legal, PR, regulators — fintech incidents often have mandatory reporting clocks).

### 9. Digital forensics basics
- Order of volatility: memory → network state → disk → backups. Collect the most fragile evidence first.
- Chain of custody and forensically sound acquisition (hashing images, write blockers) — this matters in regulated finance where evidence may reach court or a regulator.
- Core artifacts: Windows event logs, registry, prefetch, browser history, `$MFT`, memory dumps (Volatility), and timeline building.
- Know your lane: as a beginner, your job is to *preserve* evidence and escalate, not to trample it.

### 10. Threat hunting
- Hypothesis-driven hunting: start from "if an attacker did X, what would it look like in our data?" — not from an alert.
- The Pyramid of Pain (David Bianco): hash values and IPs are trivial for attackers to change; **TTPs** (tactics, techniques, procedures) are expensive. Hunt for behavior, not indicators.
- IOCs vs. TTPs in practice: blocking a known-bad IP vs. detecting *any* credential-stuffing pattern.
- Hunts that find nothing still produce value: validated telemetry, new baselines, and often a new detection rule.

### 11. SOAR & automation
- Security Orchestration, Automation, and Response: playbooks that enrich alerts (geo-IP, user context, threat intel lookups) and take gated response actions.
- What to automate first: enrichment and ticketing (safe), then containment actions like disabling an account or revoking sessions (needs approval gates — and hooks directly into IAM systems).
- Tools to know by name: Splunk SOAR, Microsoft Sentinel playbooks (Logic Apps), Tines, Shuffle (open source).

### 12. Alert triage & reducing false positives
- The triage loop: validate → scope → prioritize → escalate or close, with documented reasoning every time.
- Alert fatigue is a security vulnerability: tune thresholds, add allowlists for known-good behavior, suppress duplicates, and retire detections that never yield true positives.
- Track fidelity per rule (true positive rate) and treat noisy rules as bugs to fix.

### 13. Identity-centric detections (your IAM bridge)
- Identity is the modern perimeter — most breaches now involve valid credentials, not exploits. This is where your IAM role and blue team overlap daily.
- **Impossible travel**: logins from geographically impossible locations within a short window.
- **MFA fatigue / push bombing**: repeated MFA prompts until the user approves one (the Uber 2022 breach pattern).
- **Privilege escalation**: unexpected additions to privileged groups (Event 4728/4732), dormant admin accounts waking up, service accounts logging in interactively.
- Other high-value identity detections: password spray patterns, legacy-auth usage, consent-grant abuse, token theft/replay, first-time access to sensitive apps.
- Learn what Entra ID Protection and similar tools detect natively, and what you must build yourself in the SIEM.

## Reading list

- **NIST SP 800-61 Rev. 2, *Computer Security Incident Handling Guide*** — the canonical IR lifecycle; free PDF from NIST (csrc.nist.gov). Rev. 3 (2025) realigns it to the NIST CSF 2.0 — read Rev. 2 for the classic lifecycle, then skim Rev. 3.
- **Don Murdoch, *Blue Team Handbook: Incident Response Edition*** — dense, practical field notes for SOC work; also the *SOC, SIEM, and Threat Hunting* volume (v1.02) once you're in the SIEM chapters.
- **Richard Bejtlich, *The Practice of Network Security Monitoring* (No Starch Press, 2013)** — the philosophy of NSM: detection is about collection first. Foundational even though tooling has moved on.
- **Chris Sanders & Jason Smith, *Applied Network Security Monitoring* (Syngress, 2013)** — the hands-on companion: collection, detection, and analysis with real packet data.
- **MITRE ATT&CK** (attack.mitre.org) — the shared language of adversary behavior. Start with the Enterprise matrix; pay special attention to TA0004 Privilege Escalation, TA0006 Credential Access, and T1078 Valid Accounts.
- **Sigma HQ** (github.com/SigmaHQ/sigma) — the open detection rule repository. Read real rules in `rules/windows/` before writing your own; the wiki documents the rule spec.
- **Splunk free training & docs** — Splunk's free eLearning ("Intro to Splunk", "Search Expert" paths) plus the Search Reference docs; run Splunk Free locally to practice.
- **Elastic documentation** — the Elastic Security and Elasticsearch guides (elastic.co/guide), plus the free open detection rules at github.com/elastic/detection-rules.
- **Microsoft Learn: KQL & Sentinel** — the free "Must Learn KQL" path and SC-200 learning modules; directly relevant if FinCo uses Entra ID/Sentinel.

## Labs (ask Lefler to set these up)

Labs live in `labs/NN-name/` inside this folder.

| # | Lab | You'll learn |
|---|-----|--------------|
| 1 | Sysmon on Windows: install with the SwiftOnSecurity config, generate activity, read events in Event Viewer | What quality endpoint telemetry looks like; key event IDs; why default Windows logging isn't enough |
| 2 | ELK stack via Docker Compose: stand up Elasticsearch + Kibana, ship your Sysmon/Windows logs with Winlogbeat | SIEM architecture hands-on; ingest pipelines; ECS field mapping; building your first Kibana dashboard |
| 3 | Splunk Free locally: ingest the Boss of the SOC (BOTS) dataset and answer investigation questions | SPL fluency under realistic data; pivoting through an actual attack scenario |
| 4 | Write your first Sigma rule: detect local admin group changes, convert it with `sigma-cli` to both SPL and KQL, test it | Detection-as-code workflow; Sigma syntax; one rule, many backends; mapping to ATT&CK T1098 |
| 5 | Purple team round 1: Loki runs Atomic Red Team tests (e.g., T1136 create account, T1110 brute force) on a lab VM — you catch them in your SIEM | The full detect loop: attack → telemetry → query → alert; where your visibility gaps are |
| 6 | Identity attack detection: Loki simulates a password spray and an MFA-fatigue pattern against a test Active Directory / local accounts; you build the correlation rules | Identity-centric detection engineering — failed-auth aggregation, threshold tuning, the IAM-SOC overlap |
| 7 | Security Onion in a VM: monitor a small virtual network, trigger Suricata alerts with a scan from a second VM | Network security monitoring; IDS alerts vs. endpoint logs; Bejtlich's NSM ideas in practice |
| 8 | Incident response tabletop: Heimdall injects a scenario (compromised service account); you run the NIST 800-61 phases and write the post-incident report | IR process end-to-end; containment decisions; writing lessons-learned that become new detections |
| 9 | Alert tuning exercise: take the noisy rules from labs 4-6, measure false positives over a week of lab activity, and tune them | The unglamorous skill that separates good SOC engineers from alert factories |

(If your machine can handle it, DetectionLab-style multi-VM environments give you a full AD domain with logging pre-wired — ask Lefler whether to build that or keep labs lightweight.)

## How this connects to IAM / fintech

In a fintech SOC, identity is the loudest and most valuable telemetry source: authentication events, privilege changes, and access grants are precisely the logs your IAM systems at FinCo produce, and most modern intrusions ride valid credentials rather than exploits. Understanding blue team work makes you a better IAM engineer in concrete ways — you'll design access models that are *detectable* (clean role boundaries make anomalies visible), you'll know why the SOC escalates access-anomaly and insider-threat cases to your team, and you'll be the person who can revoke sessions and disable accounts correctly during containment. Regulation seals the link: SOX and PCI DSS both mandate logging and review of access to financial systems and cardholder data (PCI DSS Requirement 10 is essentially a blue team requirement), so IAM and security operations are two halves of the same audit story. Learn to read the logs your own systems generate, and you'll be rare and valuable on both teams.
