# 08 · GRC & Compliance

> In fintech, compliance is what keeps the company licensed, audited, and trusted with other people's money — it is non-negotiable, and IAM controls sit at the center of nearly every requirement.

**Agents:** ask **Mimir** for concept explanations, **Tyr** (your primary agent for this domain) for GRC, risk, and compliance work, and **Janus** for how specific IAM controls satisfy compliance requirements.

## Core concepts (learn in this order)

### 1. What GRC actually means
- **Governance** — who decides, and how: security strategy, policies, roles and responsibilities, board/leadership oversight, accountability structures.
- **Risk** — identifying what can hurt the business, how likely, how badly, and what to do about it.
- **Compliance** — proving to regulators, auditors, and customers that you meet external obligations (laws, regulations, contracts) and your own internal policies.
- Why the three are one discipline: governance sets the rules, risk prioritizes effort, compliance demonstrates it. In fintech, weak GRC means fines, lost banking licenses, and lost client contracts — not just breaches.

### 2. Risk management
- **Risk assessment**: identify assets → identify threats and vulnerabilities → estimate likelihood × impact → rank.
- Qualitative (High/Medium/Low matrices) vs. quantitative (ALE = SLE × ARO) approaches — know both, expect qualitative in practice.
- The **risk register**: the living document of identified risks, owners, ratings, and treatment status. You will build one in the labs.
- **Risk treatment** — the four options: **accept**, **mitigate** (apply controls), **transfer** (insurance, contracts), **avoid** (stop the activity).
- **Residual risk**: what remains after controls. Someone with authority must formally accept it — risk acceptance without an accountable owner is a finding.
- Risk appetite and tolerance: how much risk leadership is willing to carry, and why fintech appetites are low by design.

### 3. Security frameworks (the maps everyone navigates by)
- **NIST CSF 2.0** — six functions: Govern, Identify, Protect, Detect, Respond, Recover. The lingua franca for describing a security program; note that "Govern" was added in 2.0 precisely because governance kept being treated as optional.
- **ISO/IEC 27001** — the certifiable standard for an Information Security Management System (ISMS); **ISO/IEC 27002** — the companion catalog of controls (93 controls in the 2022 edition, grouped into organizational, people, physical, technological).
- **CIS Controls v8** — prioritized, prescriptive safeguards; Implementation Groups (IG1–IG3) let you scale to organization maturity.
- **COBIT** — ISACA's framework for governance of enterprise IT; you will meet it wherever auditors are.
- How they relate: CSF describes outcomes, ISO 27001 certifies management systems, CIS gives concrete safeguards, COBIT governs IT overall. Companies map between them constantly — learn to read a crosswalk.

### 4. Control types (the vocabulary of every audit)
- By function: **preventive** (stop it happening — MFA, least privilege), **detective** (notice it happened — logging, access reviews), **corrective** (fix it after — revocation, restore from backup). Also know deterrent and compensating controls.
- By implementation: **administrative** (policies, training, background checks), **technical** (MFA, encryption, RBAC), **physical** (badges, locks, cameras).
- Practice classifying real IAM controls both ways — e.g., a quarterly access review is a detective + administrative control; MFA is preventive + technical.

### 5. Fintech-critical regulations — learn these deeply
This is the section that matters most for FinCo. Payments companies live and die by these.
- **PCI-DSS v4.0.1** — the Payment Card Industry Data Security Standard, mandatory for anyone storing, processing, or transmitting cardholder data. Study all 12 requirements, but go deepest on:
  - **Requirement 7** — restrict access to cardholder data by business need-to-know (least privilege, role-based access, access control systems set to deny-all by default).
  - **Requirement 8** — identify users and authenticate access (unique IDs, MFA for all access into the cardholder data environment, password/credential policies, strict rules for shared and system accounts).
  - Also note Req 10 (logging) and Req 12 (policies) — IAM evidence feeds both.
- **SOX (Sarbanes-Oxley) ITGCs** — IT General Controls over systems that feed financial reporting: access provisioning/deprovisioning, periodic access reviews, segregation of duties, change management. IAM analysts produce a large share of SOX evidence.
- **GLBA** — US Gramm-Leach-Bliley Act: Safeguards Rule requirements for protecting customer financial information.
- **GDPR** — EU data protection: lawful basis, data subject rights, breach notification (72 hours), access controls as a technical measure under Article 32.
- **India's DPDP Act, 2023** — the Digital Personal Data Protection Act: consent, Data Fiduciary obligations, reasonable security safeguards, breach notification to the Data Protection Board. Directly relevant to any India-based processing.
- **RBI cybersecurity requirements** — the Reserve Bank of India's Cyber Security Framework in Banks (2016 circular) and the RBI Master Directions on IT Governance, Risk, Controls and Assurance Practices (2023). FinCo's Indian banking clients are bound by these, so vendor assessments will ask you about them.
- Key skill: for any regulation, be able to answer "which IAM controls satisfy this, and what evidence proves it?"

### 6. The audit process
- Audit lifecycle: scoping → evidence request (the PBC list — "provided by client") → control testing → findings → management response → remediation → retest.
- **Evidence**: screenshots, system exports, tickets, signed review reports — with timestamps and provenance. Auditors distrust anything that can't be tied to a system of record.
- Control testing: design effectiveness ("would this control work as described?") vs. operating effectiveness ("did it actually operate all period, every time?"). Sampling is how operating effectiveness is tested.
- Findings and severity, remediation plans, and why "we'll fix it next quarter" needs a documented risk acceptance.
- Internal vs. external audit, and attestation reports you'll hear constantly: SOC 1 (financial controls — SOX-adjacent), SOC 2 (Trust Services Criteria), PCI Report on Compliance (RoC) and Attestation of Compliance (AoC).

### 7. Policy → Standard → Procedure hierarchy
- **Policy**: what and why, approved by leadership, rarely changes ("All access must follow least privilege").
- **Standard**: mandatory specifics ("MFA required for all remote access; passwords minimum 12 characters").
- **Procedure**: step-by-step how ("To provision access: submit request in X, obtain manager approval, ...").
- Guidelines are the optional fourth layer. Auditors check that all three levels exist, are current, and match reality.

### 8. The policy → control → evidence chain
- The single most useful mental model in GRC: a **policy** states an obligation → a **control** enforces it → **evidence** proves the control operated.
- Example: "Access reviewed quarterly" (policy) → automated quarterly certification campaign in the IAM tool (control) → signed campaign completion report with reviewer decisions (evidence).
- If any link is missing, you have a finding. When you design or operate an IAM control, always ask: what evidence does this produce, and would it survive an auditor?

### 9. Third-party / vendor risk management (TPRM)
- Fintech runs on vendors — and FinCo *is* a vendor to thousands of banks, so you'll sit on both sides of this.
- Due diligence: security questionnaires (SIG, CAIQ), SOC 2 report review, contract security clauses, right-to-audit.
- Ongoing monitoring, vendor tiering by criticality and data access, fourth-party (subcontractor) risk, and offboarding (revoking vendor access — an IAM job).

### 10. Business continuity & disaster recovery (BCP/DRP)
- Business Impact Analysis (BIA): identifying critical processes and their tolerable downtime.
- **RTO** (recovery time objective) vs. **RPO** (recovery point objective) — know the difference cold.
- Backup strategies, DR site models (hot/warm/cold), and testing types (tabletop → walkthrough → simulation → full failover).
- Why regulators (RBI included) explicitly require tested BCP/DRP for financial infrastructure.

### 11. Security awareness & the human layer
- Awareness programs as an administrative control: training, phishing simulations, acceptable use policies.
- Why regulations (PCI-DSS Req 12.6, GLBA, RBI) mandate them, and how completion records become audit evidence.

## Reading list

- **NIST Cybersecurity Framework 2.0** — the framework document itself (NIST CSWP 29, Feb 2024): https://nvlpubs.nist.gov/nistpubs/CSWP/NIST.CSWP.29.pdf — read the core functions and the informative references; it's shorter than you'd expect.
- **ISO/IEC 27001 overview** — ISO's official summary page (https://www.iso.org/standard/27001) plus a free ISMS overview such as the ISMS.online or Advisera 27001 guides; the standard itself is paywalled, so learn the structure (clauses 4–10 + Annex A) from overviews first.
- **PCI-DSS v4.0.1** — the official standard and supporting documents from the PCI Security Standards Council Document Library: https://www.pcisecuritystandards.org/document_library/ — download the standard itself and read Requirements 7, 8, and 10 line by line, including the testing procedures column (that's what assessors actually check).
- **PCI SSC "Prioritized Approach" and SAQ documents** — same library; they show how real companies sequence compliance work.
- **SANS Institute** — GRC-relevant whitepapers in the SANS Reading Room (https://www.sans.org/white-papers/) and the free policy template library (https://www.sans.org/information-security-policy/) — use the templates as models for the policy-writing lab.
- **"The Basics of IT Audit"** by Stephen D. Gantz (Syngress) — the most approachable end-to-end book on how IT audits actually run; read it before your first audit season.
- **ISACA resources** — CISA (audit) and CRISC (risk) review materials and the ISACA Journal (https://www.isaca.org/resources) — CISA study content is excellent GRC education even if you don't sit the exam yet; CRISC is a natural mid-term certification target for you.
- **RBI Cyber Security Framework in Banks** — the original circular (DBS.CO/CSITE/BC.11/33.01.001/2015-16, June 2016), available on https://www.rbi.org.in under Notifications, plus the 2023 Master Direction on IT Governance.
- **DPDP Act, 2023 (official text)** — from India Code / MeitY: https://www.meity.gov.in/data-protection-framework — read the actual act (it's short) rather than summaries.
- **CIS Controls v8** — free with registration from https://www.cisecurity.org/controls — read Control 5 (Account Management) and Control 6 (Access Control Management) first; they are your job description in framework form.

## Labs / exercises (ask Tyr or Lefler)

These are mostly document and analysis exercises rather than technical labs — which is exactly what GRC work looks like. Each lives in `labs/NN-name/`.

| # | Exercise | You'll learn |
|---|----------|--------------|
| 1 | Build a risk register for a fictional payments startup (10+ risks, likelihood × impact scoring, treatment decisions, owners) | Risk assessment mechanics, treatment options, residual risk, how a register is actually structured |
| 2 | Map an IAM control set to PCI-DSS Requirement 8, sub-requirement by sub-requirement | Reading a regulation's testing procedures, control mapping, spotting gaps between "we have MFA" and "we comply with 8.4" |
| 3 | Write a mock access-control policy + supporting standard + provisioning procedure | The policy/standard/procedure hierarchy, writing auditable language, SANS template adaptation |
| 4 | Prepare an audit evidence package for a quarterly access review (fabricated data): review report, sign-offs, remediation tickets for revocations | What evidence auditors accept, provenance and timestamps, the policy→control→evidence chain end to end |
| 5 | Gap assessment of a fictional company against NIST CSF 2.0 (score each function, write findings and a prioritized roadmap) | Framework-based assessment, maturity scoring, communicating gaps to leadership |
| 6 | Tabletop exercise: compromised privileged account at a payment processor — walk the incident through detection, containment, notification obligations (GDPR 72h, DPDP, PCI) | Incident response process, regulatory breach-notification triggers, why IAM logs decide how bad an incident report looks |
| 7 | Design a segregation-of-duties (SoD) matrix for a payments operations team, then find the SoD violations in a provided access export | SoD as an audited SOX/PCI control, toxic access combinations, how access certifications catch them |
| 8 | Vendor risk assessment: review a (mock) vendor SOC 2 report and security questionnaire, write a risk summary with a recommendation | TPRM in practice, reading SOC 2 reports, exceptions and CUECs, tiering vendors by data access |
| 9 | Write the RTO/RPO section of a BIA for three systems (payment switch, HR portal, marketing site) and justify the differences | BIA thinking, RTO vs. RPO, why criticality drives DR spend |

## How this connects to IAM / fintech

This domain is not adjacent to your job — it *is* the reason your job exists in its current form. The quarterly access reviews you'll run at FinCo are SOX ITGC and PCI-DSS Req 7 controls; the certification reports they produce are audit evidence. Privileged access management maps directly to PCI Requirements 7 and 8 — MFA on admin access, unique IDs, no shared accounts. Least privilege and segregation of duties aren't best-practice suggestions; they are tested, sampled, audited controls with findings attached when they fail. Every provisioning ticket, deprovisioning within SLA, and role-mining exercise you perform produces compliance evidence that keeps banking clients contracted and regulators satisfied. Learn GRC well and you'll understand *why* every IAM task matters — and you'll be the analyst who can talk to auditors, which is the fastest way to stand out early in a fintech career.
