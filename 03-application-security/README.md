# 03 · Application Security

> Most breaches don't start in the firewall — they start in the app. A single broken access-control check or leaked token in a fintech platform can expose money movement, PII, and customer accounts, which is exactly why this domain sits next to IAM in your career at FinCo.

**Agents to use here:** ask **Mimir** for concepts and to explain the *why*, **Lefler** to build and walk you through labs, **Heimdall** for the defensive and detection side (logging, WAF rules, secure patterns), and **Loki** for the offensive side — attacking apps to understand how they break, **in authorized labs only**.

> 📓 **Deep-dive note:** [**OWASP Top 10 (2021)** — all ten risks in plain English, each with attack → defense](notes/01-owasp-top-10.md) (written to [Lefler's Laws](../LEFLER-LAWS.md)). The identity-specific slice lives next door: [IAM vulnerabilities](../01-identity-access-management/notes/10-iam-vulnerabilities.md).

---

## Core concepts (learn in this order)

Work top-to-bottom. Each concept builds on the previous one. Don't rush the first three — access control and injection are where the real-world breaches (and your IAM career) live.

### 1. OWASP Top 10 (2021) — your map of the territory

The industry-standard list of the most critical web app risks. Learn the shape of all ten first, then go deep on each.

- **A01 Broken Access Control** — users doing things they shouldn't (viewing other accounts, escalating privileges). Now the #1 risk. Directly your IAM world.
- **A02 Cryptographic Failures** — weak/missing encryption of data in transit and at rest; exposed secrets, weak hashing of passwords.
- **A03 Injection** — untrusted input interpreted as code/commands (SQLi, command injection, LDAP injection). XSS now lives under this category.
- **A04 Insecure Design** — flaws baked into the architecture, not the code. Fixed by threat modeling, not patching.
- **A05 Security Misconfiguration** — default creds, verbose errors, open cloud buckets, unnecessary features enabled.
- **A06 Vulnerable and Outdated Components** — running libraries/frameworks with known CVEs (the SCA problem).
- **A07 Identification and Authentication Failures** — weak login, broken session management, credential stuffing exposure. Core IAM overlap.
- **A08 Software and Data Integrity Failures** — trusting unverified updates, insecure deserialization, CI/CD supply-chain tampering.
- **A09 Security Logging and Monitoring Failures** — you can't detect or respond to what you don't log. Heimdall's territory.
- **A10 Server-Side Request Forgery (SSRF)** — tricking the server into making requests to internal systems.

### 2. Injection (SQLi and command injection)

The classic "input becomes code" flaw.

- **SQL injection (SQLi):** attacker input alters a database query. Learn in-band (error-based, UNION-based), blind (boolean- and time-based), and second-order SQLi.
- **Command injection:** input reaches an OS shell (`;`, `|`, `&&`, backticks, `$()`).
- **The fix is always the same shape:** never build queries/commands by string concatenation. Use **parameterized queries / prepared statements** and safe APIs. Input validation is defense-in-depth, not the primary control.
- **Why it matters in fintech:** a SQLi on a balance or transaction endpoint is game over.

### 3. Broken access control and IDOR

- **Vertical access control:** can a normal user reach admin functions?
- **Horizontal access control:** can user A reach user B's data?
- **IDOR (Insecure Direct Object Reference):** `/account?id=1001` → change to `1002` and see someone else's account. The purest form of broken access control.
- **Key principle:** authorization must be enforced **server-side on every request**, based on the authenticated session — never on hidden fields, URL parameters, or "you can't see the button" UI logic. **Deny by default.**

### 4. Authentication and session flaws (tie this to IAM)

This is the bridge between AppSec and your IAM day job.

- **Authentication** = proving *who you are*. Weaknesses: weak password policy, no rate limiting (enables brute force and credential stuffing), missing/optional MFA, username enumeration, insecure password reset flows.
- **Session management** = staying logged in safely. Weaknesses: predictable session IDs, session fixation, tokens not rotated after login, missing logout/idle timeout, tokens in URLs.
- **Cookie flags to know cold:** `HttpOnly` (blocks JS access), `Secure` (HTTPS only), `SameSite` (CSRF mitigation).
- **Tokens:** understand JWT structure (header.payload.signature), common JWT mistakes (`alg: none`, weak signing keys, not verifying signature/expiry), and OAuth 2.0 / OIDC basics — these are the protocols IAM runs on.

### 5. Cross-Site Scripting (XSS) — the three types

Attacker-controlled JavaScript runs in a victim's browser.

- **Reflected XSS:** payload in the request is echoed straight back in the response (e.g. a search term).
- **Stored (persistent) XSS:** payload is saved server-side (a comment, profile field) and served to every viewer — the most dangerous.
- **DOM-based XSS:** the vulnerability is entirely in client-side JS handling untrusted input (`innerHTML`, `location.hash`).
- **Impact:** session/token theft, keylogging, account takeover, defacing.
- **Fix:** context-aware **output encoding**, a strong **Content Security Policy (CSP)**, and framework auto-escaping. Don't rely on input filtering alone.

### 6. Cross-Site Request Forgery (CSRF)

- Tricks a logged-in victim's browser into sending an unwanted authenticated request (e.g. "transfer money").
- Exploits the browser automatically attaching cookies to requests.
- **Fixes:** anti-CSRF tokens (synchronizer token pattern), `SameSite` cookies, and re-authentication for sensitive actions. Understand why CSRF matters most when auth is cookie-based.

### 7. Server-Side Request Forgery (SSRF)

- The server is tricked into making a request to a URL the attacker chooses — often internal-only services (`http://169.254.169.254/` cloud metadata endpoints, internal admin panels).
- Huge in cloud/fintech because it can reach internal microservices and steal cloud credentials.
- **Fixes:** allow-list outbound destinations, block requests to internal IP ranges and metadata IPs, don't let user input choose the host.

### 8. Insecure deserialization

- Turning attacker-controlled serialized data back into objects can lead to remote code execution or object injection.
- Learn what serialization is, why untrusted serialized blobs are dangerous, and language-specific gotchas (Java, .NET, PHP, Python pickle).
- **Fix:** avoid deserializing untrusted data; if unavoidable, use integrity checks and strict type allow-lists.

### 9. Security misconfiguration

- Default credentials, directory listing enabled, stack traces leaking to users, unnecessary ports/features, permissive CORS, missing security headers.
- **Security headers to know:** `Content-Security-Policy`, `Strict-Transport-Security` (HSTS), `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`.
- Hardening = secure defaults + minimizing attack surface.

### 10. Secure SDLC (Software Development Life Cycle)

- Security is cheapest when it's early. Learn "shift left": build security into requirements, design, coding, testing, and deployment — not bolted on at the end.
- Key gates: threat modeling in design, secure code review + SAST in dev, DAST/pen-testing in QA, dependency scanning in CI/CD, monitoring in production.

### 11. Input validation and output encoding

- The two complementary controls behind most fixes above.
- **Input validation:** prefer **allow-listing** (accept known-good) over deny-listing (block known-bad). Validate type, length, format, range — server-side.
- **Output encoding:** encode data for the context it lands in (HTML body, HTML attribute, JavaScript, URL, SQL). Right control, right place — validation doesn't replace encoding, and vice versa.

### 12. API security (REST and GraphQL)

Modern fintech is APIs all the way down — this is where a lot of your real risk lives.

- **BOLA / IDOR at the object level (API #1 risk):** APIs expose object IDs directly; missing per-object authorization = mass data exposure. This is IDOR for APIs.
- **Broken authentication**, **excessive data exposure** (API returns more fields than the UI shows), **lack of rate limiting**, **broken function-level authorization** (BFLA).
- **GraphQL-specific:** introspection left on, query depth/complexity abuse, batching attacks.
- Study the **OWASP API Security Top 10 (2023)** as its own list — it's not the same as the web Top 10.

### 13. Secrets management

- Never hardcode API keys, DB passwords, or tokens in source or config committed to git.
- Use a vault (HashiCorp Vault, AWS Secrets Manager, cloud KMS), inject secrets at runtime, rotate them, and scan repos for leaked secrets (gitleaks, trufflehog).

### 14. Dependency and supply-chain security (SCA)

- Most of your app is code you didn't write. **Software Composition Analysis (SCA)** finds known-vulnerable dependencies.
- Learn: CVE/CVSS basics, SBOM (Software Bill of Materials), typosquatting and malicious packages, pinning versions, and tools like OWASP Dependency-Check, `npm audit`, Snyk, Dependabot.

### 15. SAST, DAST, and IAST

- **SAST (Static):** scans source code without running it — finds bugs early, but noisy (false positives). Runs in CI.
- **DAST (Dynamic):** attacks the *running* app from the outside — fewer false positives, but only finds what it can reach. (OWASP ZAP, Burp.)
- **IAST (Interactive):** instruments the running app to observe from the inside — hybrid of both.
- Know the trade-offs and where each fits in the pipeline.

### 16. Threat modeling for apps

- Structured way to find design flaws before code exists. Answer: *What are we building? What can go wrong? What do we do about it? Did we do a good job?*
- Learn **STRIDE** (Spoofing, Tampering, Repudiation, Information disclosure, Denial of service, Elevation of privilege) and **data-flow diagrams** with trust boundaries.
- For fintech, model money-movement flows and authentication boundaries first.

---

## Reading list

Start free and authoritative (OWASP), then go deep with the classics.

- **OWASP Top 10 (2021)** — https://owasp.org/Top10/ — read the whole thing; it's short and it's the baseline.
- **OWASP API Security Top 10 (2023)** — https://owasp.org/API-Security/ — essential for fintech API work.
- **OWASP Cheat Sheet Series** — https://cheatsheetseries.owasp.org/ — the single best free reference for *how to fix* things (Authentication, Session Management, SQL Injection Prevention, XSS Prevention, Password Storage, JWT, REST Security, and more).
- **OWASP ASVS (Application Security Verification Standard)** — https://owasp.org/www-project-application-security-verification-standard/ — a checklist of what "secure" actually means, by level. Great for structuring reviews.
- **OWASP WSTG (Web Security Testing Guide)** — https://owasp.org/www-project-web-security-testing-guide/ — the methodology for *how to test* an app end to end.
- **PortSwigger Web Security Academy** — https://portswigger.net/web-security — free, interactive, best-in-class labs and written material for every web vuln class. This is your primary hands-on classroom.
- **"The Web Application Hacker's Handbook"** (Stuttard & Pinto, 2nd ed.) — the definitive deep book on attacking web apps. Dense but foundational; pair it with the Academy (same authors' company).
- **"Real-World Bug Hunting"** (Peter Yaworski) — approachable, example-driven look at real vulnerabilities.
- **OWASP Juice Shop companion guide** — https://pwning.owasp.org/ — the official book for the Juice Shop practice app.
- **OWASP Cheat Sheet: Threat Modeling** and **OWASP Threat Dragon** (free tool) — https://owasp.org/www-project-threat-dragon/ — for the design-phase skills.
- **MDN Web Docs — HTTP security** — https://developer.mozilla.org/en-US/docs/Web/Security — solid, accurate reference for headers, cookies, CORS, CSP.

---

## Labs (ask Lefler to set these up)

All labs live in `labs/NN-name/` in this domain folder. Offensive work is **authorized-lab-only** — you attack DVWA, Juice Shop, and PortSwigger's targets because they exist to be attacked. Never point these tools at FinCo systems or anything you don't own without written authorization.

| # | Lab | You'll learn |
|---|-----|--------------|
| 1 | Set up Burp Suite Community + intercept traffic against DVWA (Docker) | HTTP internals, proxy interception, repeater, how requests/responses actually look on the wire |
| 2 | SQL injection on DVWA (low → high security levels) | Manual SQLi, blind SQLi, and why parameterized queries fix it — progressing through weaker/stronger defenses |
| 3 | PortSwigger Academy — Access control & IDOR labs | Horizontal/vertical privilege escalation, tampering with object IDs, server-side authorization (your IAM core) |
| 4 | XSS trilogy on OWASP Juice Shop | Find reflected, stored, and DOM-based XSS; steal a session cookie in a lab; then fix with encoding + CSP |
| 5 | Broken authentication & session on Juice Shop | Weak passwords, credential-based attacks, session token handling, JWT tampering (`alg:none`) |
| 6 | CSRF & SSRF labs on PortSwigger Academy | Forge an authenticated request; reach an internal service via SSRF; apply SameSite/token and allow-list fixes |
| 7 | API / BOLA lab (Juice Shop REST + a GraphQL target) | Object-level authorization gaps, excessive data exposure, introspection abuse — the OWASP API Top 10 in practice |
| 8 | Dependency & secrets scanning on a deliberately vulnerable repo | Run OWASP Dependency-Check / `npm audit` + gitleaks; read a CVE, understand CVSS, remediate |
| 9 | Threat model a simple money-transfer feature (Heimdall + Loki) | STRIDE, data-flow diagrams, trust boundaries — find design flaws before writing code |

---

## How this connects to IAM / fintech

Application Security and IAM are the same problem seen from two sides. Three of the OWASP Top 10 — **A01 Broken Access Control**, **A07 Authentication Failures**, and the API list's **#1 BOLA** — are *IAM problems expressed in application code*. Every access-control check, session token, JWT, OAuth flow, and per-object authorization decision you'll study here is the enforcement point for the identities and permissions IAM defines.

At FinCo, this overlap is the job: an IDOR on an account endpoint, a missing authorization check on a transfer API, or a mishandled session token doesn't just leak data — it moves money. Learn AppSec well and you're not just a stronger IAM engineer; you understand *where identity and access actually get enforced*, which is the difference between writing a policy and knowing whether it holds.
