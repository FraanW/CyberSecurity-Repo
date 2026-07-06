# 05 · Cloud Security

> Fintech runs on the cloud — FinCo's payments, ledgers, and customer data live in AWS/Azure/GCP, and in the cloud the perimeter *is* identity. Cloud IAM is a massive overlap with your day job: the same least-privilege, federation, and access-control principles you practice on-prem, just expressed as policies, roles, and trust relationships.

**Agents to use:** ask **Mimir** for concepts, **Lefler** to set up labs, **Janus** for cloud IAM depth (this is your lane — lean on him hard), **Heimdall** for cloud detection & CSPM, and **Loki** for cloud attack techniques (authorized labs only).

## Core concepts (learn in this order)

### 1. Shared responsibility model
- The provider secures *of* the cloud (hardware, hypervisor, managed-service internals); you secure *in* the cloud (data, identities, config, network rules).
- The line moves by service type: with IaaS you own the OS and patching; with SaaS you mostly own identity and data configuration.
- Most breaches are customer-side misconfigurations, not provider failures — internalize this early, it reframes everything below.

### 2. Cloud service models (IaaS / PaaS / SaaS)
- **IaaS** (EC2, Azure VMs, GCE): you manage OS, runtime, app, and network config.
- **PaaS** (RDS, App Service, Cloud Run): provider manages the platform; you manage app + access.
- **SaaS** (Workspace, M365): you manage identities, sharing, and data governance only.
- Map each model back to the responsibility line — it tells you where your controls must sit.

### 3. Cloud IAM — deep (this is your job)
This is the most important section in the whole module. Spend real time here.

**AWS IAM**
- **Users** (long-lived humans/keys — avoid where possible), **groups**, **roles** (assumable, temporary, the preferred pattern for workloads and cross-account access).
- **Policies**: identity-based vs resource-based; managed vs inline; the JSON structure (`Effect`, `Action`, `Resource`, `Condition`, `Principal`).
- **STS** (`AssumeRole`, `GetSessionToken`, federation): how temporary credentials are minted; session tokens and role chaining.
- **Instance profiles** and how EC2/Lambda get credentials via the metadata service (this becomes an attack path later — see §14).
- **Permission boundaries**, **SCPs** (Service Control Plans at the Organizations level), and how effective permissions are the *intersection* of all of them.

**Azure**
- **Entra ID** (formerly Azure AD): tenants, users, groups, service principals, managed identities, app registrations.
- **RBAC**: role assignments = principal + role definition + scope (management group → subscription → resource group → resource); scope inheritance.
- **Conditional Access**, PIM (Privileged Identity Management) for just-in-time elevation.

**GCP**
- IAM bindings = member + role + resource; the resource hierarchy (organization → folder → project → resource) and policy inheritance.
- **Service accounts**, `serviceAccountUser`/`actAs`, and impersonation (a common privilege-escalation path).
- Primitive vs predefined vs custom roles.

> Cross-cloud takeaway: every provider expresses the same triangle — *who* (principal/identity), *what* (permission/role), *where* (resource/scope) — plus conditions. Learn to read any policy by finding those three pieces.

### 4. Identity federation to cloud
- **SAML** and **OIDC** federation from an IdP into the cloud (e.g., Okta/Entra → AWS IAM Identity Center / GCP Workforce Identity).
- **Workload identity federation**: letting external workloads assume cloud roles without long-lived keys (OIDC trust, e.g. GitHub Actions → AWS).
- SSO, SCIM provisioning, and why federation kills the "long-lived access key" anti-pattern — directly relevant to your IAM domain.

### 5. Least privilege & policy evaluation
- How each provider evaluates a request: **explicit deny > allow > implicit (default) deny**.
- AWS evaluation with SCPs, permission boundaries, identity + resource policies all in play — walk the full decision flow.
- Right-sizing: start from zero, grant what's proven necessary (use access analyzers / last-accessed data to trim).
- Conditions and context keys (source IP, MFA present, tags) to constrain grants.

### 6. Common misconfigurations
- **Public S3 buckets** / blob containers — the classic data-leak headline.
- **Over-permissive roles** (`*:*`, `AdministratorAccess` handed out casually, wildcards in `Resource`).
- **Exposed keys**: long-lived access keys in code, CI logs, or public repos — rotate, vault, or eliminate.
- Wide-open security groups (`0.0.0.0/0` on SSH/RDP/DB ports), disabled logging, unencrypted storage.

### 7. Secrets management
- **AWS KMS** / **Secrets Manager** / SSM Parameter Store; **Azure Key Vault**; **GCP KMS** / Secret Manager.
- Envelope encryption, key rotation, and separating who can *use* a key from who can *manage* it.
- Never bake secrets into images, env files, or code — inject at runtime from a vault with tight IAM around the vault itself.

### 8. Network security in cloud
- **VPC / VNet**: subnets, route tables, public vs private subnets.
- **Security groups** (stateful, instance-level) vs **NACLs** (stateless, subnet-level) — know the difference cold.
- Private connectivity (VPC endpoints / Private Link) to keep traffic off the public internet; egress control.

### 9. CSPM & posture management
- Continuous scanning for misconfig and drift against a baseline (public buckets, open ports, missing encryption, IAM sprawl).
- Native tools (AWS Security Hub, Azure Defender for Cloud, GCP Security Command Center) and open-source scanners (Prowler, ScoutSuite).
- Ask **Heimdall** here — posture management is his specialty.

### 10. CWPP / container & Kubernetes security basics
- **CWPP** (Cloud Workload Protection): securing VMs, containers, and serverless at runtime.
- Container basics: image scanning, minimal base images, no secrets in layers, non-root.
- Kubernetes: RBAC (yes, IAM again — pod → service account → role bindings), network policies, pod security standards, and securing the API server. Managed clusters (EKS/AKS/GKE) still leave RBAC and workloads to you.

### 11. Logging & monitoring
- **AWS CloudTrail** (API audit log — the single most important cloud detection source), CloudWatch, VPC Flow Logs.
- **Azure Monitor** / Activity Log; **GCP Cloud Audit Logs**.
- Centralize, retain, and alert. If it isn't logged, the incident is invisible — tie this back to detection with Heimdall.

### 12. CIS benchmarks
- Vendor-neutral, consensus hardening baselines per platform (CIS AWS Foundations, Azure, GCP, Kubernetes).
- Used as the scoring rubric by most CSPM tools — learn what a benchmark control looks like and why it exists.

### 13. Cloud attack paths (authorized study — work with Loki)
- **Privilege escalation via IAM**: chaining weak permissions (e.g., `iam:PassRole` + launch a service, `iam:CreatePolicyVersion`, service-account impersonation in GCP) to climb from low-priv to admin.
- **SSRF to the metadata service** (`169.254.169.254`): tricking a vulnerable app into fetching instance credentials, then using them from outside. Understand IMDSv2 as the mitigation.
- Public-resource enumeration, over-shared snapshots, and lateral movement across accounts via trust relationships.

## Reading list

- **AWS Well-Architected — Security Pillar** — the canonical AWS security design guide. (`aws.amazon.com/architecture/well-architected`)
- **AWS IAM User Guide** — read the policy-evaluation and roles sections end to end. (`docs.aws.amazon.com/IAM`)
- **Microsoft Azure security documentation** + the **Azure Well-Architected Framework — Security** pillar; **Microsoft Entra** identity docs. (`learn.microsoft.com/azure/security`)
- **Google Cloud security docs** + the **Google Cloud Architecture Framework — Security** pillar; **GCP IAM** docs. (`cloud.google.com/security`)
- **flaws.cloud** and **flaws2.cloud** — free, browser-based AWS misconfiguration walkthroughs by Scott Piper. Start here for hands-on intuition.
- **CIS Benchmarks** — download the AWS/Azure/GCP/Kubernetes foundations benchmarks. (`cisecurity.org/cis-benchmarks`)
- **Hacking the Cloud** — community-maintained offensive cloud techniques encyclopedia (AWS/Azure/GCP). (`hackingthe.cloud`)
- **cloudsecdocs** — Marco Lancini's structured cloud security reference notes. (`cloudsecdocs.com`)
- **SANS cloud security resources** — whitepapers, the Cloud Security Reading Room, and posters (e.g., the AWS/Azure/GCP cheat sheets); SEC488/SEC510 course roadmaps for direction.

## Labs (ask Lefler to set these up)

Labs live in `labs/NN-name/`. **Authorized targets only** — use your own accounts, free tiers, or the intentionally-vulnerable ranges below. Never point offensive tools at systems you don't own or aren't explicitly permitted to test.

| # | Lab | You'll learn |
|---|-----|--------------|
| 1 | AWS free-tier account setup + IAM baseline | Root lockdown, MFA, an admin *role* (not user), billing alarms, CloudTrail on. First-hour hardening muscle memory. |
| 2 | Write & test IAM policies (least privilege) | Craft JSON policies, use the AWS Policy Simulator, apply conditions, and prove deny-beats-allow evaluation. |
| 3 | flaws.cloud + flaws2.cloud | Walk real S3/IAM/metadata misconfigurations start to finish; connect each finding to a control that would have stopped it. |
| 4 | CloudGoat scenarios (Rhino Security Labs) | Deploy vulnerable AWS scenarios with Terraform, then perform authorized IAM privilege-escalation and exploitation. Work with Loki. |
| 5 | LocalStack local AWS lab | Practice S3/IAM/Lambda/STS locally with zero cost or cloud risk — safe sandbox for policy and API experiments. |
| 6 | Prowler scan of your own account | Run a CIS-benchmark posture assessment, read the findings, and remediate the top issues. Heimdall helps interpret. |
| 7 | ScoutSuite multi-cloud audit | Generate an HTML posture report, compare it against Prowler, and understand CSPM output. |
| 8 | Azure RBAC + Entra lab (free tier) | Assign roles at different scopes, watch inheritance, and set up a Conditional Access rule + PIM elevation. |
| 9 | Federation lab: OIDC workload identity | Configure GitHub Actions (or an IdP) to assume a cloud role with *no* long-lived keys — federation done right. |

## How this connects to IAM / fintech

Cloud IAM *is* your identity domain, just relocated to AWS/Azure/GCP — same principals, entitlements, and access reviews, expressed as policies and roles. **Least privilege** is the through-line: whether you're right-sizing an on-prem group or an IAM role, the discipline is identical. **Federated SSO to cloud** (SAML/OIDC, SCIM provisioning, workload identity) is the bridge between the corporate directory you'll administer and the cloud workloads that consume those identities — master it and you own the seam most teams get wrong. And in fintech, all of this is **compliance-load-bearing**: PCI DSS, SOC 2, RBI/regulatory expectations, and audit trails all rest on provable, least-privilege, well-logged access control. Get cloud IAM right and you're not just securing infrastructure — you're the person who makes the audit pass.
