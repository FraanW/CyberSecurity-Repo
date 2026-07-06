# The OWASP Top 10 — the ten ways web apps get broken

> **Mimir's explainer, written to [Lefler's Laws](../../LEFLER-LAWS.md).** The OWASP Top 10 is the single most useful "what goes wrong" list in application security. This note explains all ten in plain English, with a concrete example and a fix for each, and flags the ones that are really **IAM problems in disguise** (Farhaan — those are your lane). Prereqs: none. Pairs with this domain's [`README`](../README.md) §1 and the [IAM vulnerabilities note](../../01-identity-access-management/notes/10-iam-vulnerabilities.md).

---

## TL;DR (30 seconds)

- **OWASP** = a nonprofit that publishes free security knowledge. The **Top 10** = its awareness list of the **most critical web-app risks**, re-ranked every few years from real-world data (current edition: **2021**).
- It's a **starting point, not a full checklist** — clearing the Top 10 doesn't make you "secure," it means you've handled the most common ways apps get owned.
- **Three of the ten are essentially IAM:** **A01 Broken Access Control**, **A07 Identification & Authentication Failures**, and **A09 Logging & Monitoring Failures**. Those are where your identity knowledge meets app security.

---

## 1. What OWASP and the Top 10 actually are

**OWASP** (the **Open Worldwide Application Security Project**) is a nonprofit community that produces free tools, guides, and cheat sheets for building secure software. You'll hear its name constantly.

The **OWASP Top 10** is its flagship: a ranked list of the **ten most critical web application security risks**, rebuilt every few years from data across thousands of real applications. Think of it as *"the ten things most likely to get a web app hacked, in rough order."*

Two beginner traps to avoid:
- **It's a floor, not a ceiling.** Passing the Top 10 is the *minimum*, not "done."
- **The categories shift.** The 2021 list merged and renamed things from 2017 (e.g., "Broken Access Control" jumped to #1). Always say which edition you mean.

---

## 2. The Top 10 (2021), one by one

Each item below: **what it is → a concrete example → attack vs. defense.** ⭐ = strongly IAM-related.

### A01 — Broken Access Control ⭐ (the #1 risk)
- **What it is:** users can do or see things they shouldn't — the app fails to enforce *what you're allowed to do* (**authorization**).
- **Example:** you view your invoice at `/invoice?id=1001`, change it to `id=1002`, and see **someone else's** invoice. That's **IDOR** (Insecure Direct Object Reference).
- **Attack → Defense:** attacker tampers with IDs, URLs, or roles to reach other users' data or admin functions. → **Enforce access checks server-side on every request**, deny by default, check ownership on each object, never trust the client. (This is **authorization** — see [IAM note 10](../../01-identity-access-management/notes/10-iam-vulnerabilities.md) and the [RBAC/ABAC section of IAM note 07](../../01-identity-access-management/notes/07-iam-foundations.md).)

### A02 — Cryptographic Failures
- **What it is:** sensitive data isn't protected properly — weak/no encryption, in transit or at rest.
- **Example:** card numbers sent over plain HTTP, or passwords stored with fast/unsalted hashing.
- **Attack → Defense:** attacker sniffs traffic or dumps a database and reads secrets. → **TLS everywhere; strong, salted password hashing (bcrypt/scrypt/Argon2); encrypt sensitive data at rest; don't roll your own crypto.** Deep dive: [`04-cryptography`](../../04-cryptography/README.md).

### A03 — Injection
- **What it is:** untrusted input is treated as **code/commands**, not data.
- **Example:** a login form where typing `' OR '1'='1` bypasses the SQL query (SQL injection). Also **LDAP injection**, OS command injection, and **XSS** (injecting scripts into pages).
- **Attack → Defense:** attacker crafts input that changes the query/command. → **Parameterized queries / prepared statements, input validation, output encoding, and escaping.** LDAP-specific injection is covered in [IAM note 04](../../01-identity-access-management/notes/04-ldap-ad-entra.md).

### A04 — Insecure Design
- **What it is:** the flaw is in the **architecture**, not a coding bug — something unsafe was designed in.
- **Example:** a "reset password" flow that lets you reset *anyone's* password with only their email, because no ownership check was ever designed.
- **Attack → Defense:** attacker abuses a missing control that was never planned. → **Threat model early** ("what can go wrong?"), secure design patterns, and abuse-case testing. You can't patch your way out of a bad design.

### A05 — Security Misconfiguration
- **What it is:** insecure defaults, half-configured settings, or too much exposed.
- **Example:** a cloud storage bucket left public, default admin credentials unchanged, or verbose error pages leaking stack traces.
- **Attack → Defense:** attacker finds the open door or default password. → **Harden defaults, remove unused features, lock down CORS/headers, hide internal errors, and automate configuration** so it's consistent.

### A06 — Vulnerable & Outdated Components
- **What it is:** you're running libraries/frameworks with known holes.
- **Example:** an app using an old logging library with a famous remote-code-execution bug (think Log4Shell).
- **Attack → Defense:** attacker exploits a **published** CVE in your dependency. → **Inventory your components, patch promptly, use dependency scanning (SCA), and remove what you don't use.** This is the supply-chain angle.

### A07 — Identification & Authentication Failures ⭐
- **What it is:** weak **authentication** — proving *who you are* is broken.
- **Example:** no rate limiting, so attackers try millions of leaked passwords (**credential stuffing**); or session tokens that don't expire; or no MFA on an admin login.
- **Attack → Defense:** attacker guesses/reuses credentials, hijacks sessions, or brute-forces. → **MFA (ideally phishing-resistant), rate limiting/lockout, strong session management, and no default/weak credentials.** This is squarely [IAM note 07 (MFA/sessions)](../../01-identity-access-management/notes/07-iam-foundations.md) and the token/session pitfalls in [IAM note 03](../../01-identity-access-management/notes/03-oauth-oidc-deep-dive.md) and [note 02 (SAML)](../../01-identity-access-management/notes/02-saml-deep-dive.md).

### A08 — Software & Data Integrity Failures
- **What it is:** trusting code or data that hasn't been **verified** for integrity.
- **Example:** an auto-update that installs an unsigned package, or **insecure deserialization** where crafted data becomes running code; also compromised **CI/CD** pipelines.
- **Attack → Defense:** attacker slips malicious code/data into a trusted flow. → **Verify signatures, use trusted repos, sign artifacts, and secure the build pipeline.**

### A09 — Security Logging & Monitoring Failures ⭐ (IAM-adjacent)
- **What it is:** you can't **detect or investigate** an attack because logging is missing or unwatched.
- **Example:** thousands of failed logins and nobody's alerted; after a breach, there are no logs to show what happened.
- **Attack → Defense:** attacker operates undetected and un-attributed. → **Log security events (logins, access, failures) tied to a unique user, alert on anomalies, and monitor.** This is why unique IDs (IAM) + a SIEM ([`06-...blue-team`](../../06-security-operations-blue-team/README.md)) matter — ask **Heimdall**.

### A10 — Server-Side Request Forgery (SSRF)
- **What it is:** the server is tricked into making requests to places it shouldn't.
- **Example:** an app that fetches a URL you give it, and you point it at `http://169.254.169.254/` to steal cloud metadata/credentials.
- **Attack → Defense:** attacker uses the server as a proxy to reach internal systems. → **Validate/allow-list outbound URLs, block internal ranges, and don't let user input pick server destinations.**

---

## 3. The IAM-heavy items at a glance

| # | Item | IAM-relevant? | One-line defense |
|---|---|---|---|
| A01 | Broken Access Control | ⭐⭐ (authZ) | Server-side checks, deny by default, verify object ownership |
| A02 | Cryptographic Failures | ○ (crypto) | TLS + strong hashing; encrypt sensitive data |
| A03 | Injection | ○ | Parameterize queries; validate/escape input |
| A04 | Insecure Design | ○ | Threat model early; secure patterns |
| A05 | Security Misconfiguration | ○ | Harden defaults; least exposure |
| A06 | Vulnerable Components | ○ | Inventory + patch + scan dependencies |
| A07 | Identification & Auth Failures | ⭐⭐ (authN) | MFA, rate limiting, solid session management |
| A08 | Software/Data Integrity | ○ | Sign & verify code/artifacts; secure CI/CD |
| A09 | Logging & Monitoring Failures | ⭐ (audit) | Log + attribute + alert (unique IDs + SIEM) |
| A10 | SSRF | ○ | Allow-list outbound; block internal ranges |

**The identity takeaway:** the two most identity-centric risks (**A01 authorization** and **A07 authentication**) are literally the **AAA model** — the same "who are you / what can you do" from [IAM note 01](../../01-identity-access-management/notes/01-iam-protocol-landscape.md). Web app security and IAM are the same problem seen from two sides.

---

## 4. Don't forget the OWASP **API Security Top 10** (fintech is API-heavy)

The web Top 10 has a sibling: the **OWASP API Security Top 10**, focused on the APIs behind modern apps — and at a payments company, **APIs are everything**. Its #1 is **BOLA (Broken Object Level Authorization)** — the API version of IDOR: an endpoint returns object `1002` when it should only ever return *your* `1001`, because it authenticated you but never checked you **own** that object. If you internalize one API risk, make it BOLA. For the identity-specific depth (token misuse, scope errors, BOLA/BFLA), see the companion [IAM vulnerabilities note](../../01-identity-access-management/notes/10-iam-vulnerabilities.md).

---

## 5. Practice it (hands-on)

The fastest way to *get* these is to exploit them safely, then fix them:
- **OWASP Juice Shop** and **DVWA** — intentionally-vulnerable apps that cover most of the Top 10. Ask **Lefler** to stand them up (Docker), and see this domain's [`README`](../README.md) labs.
- **Authorized-lab-only** — these techniques are for your own lab targets, never production or FinCo systems.
- After each attack, ask **Heimdall** what a defender would detect — that's purple teaming.

---

## What you learned

- The **OWASP Top 10** is the essential "what goes wrong in web apps" list — a **floor, not a ceiling**, refreshed every few years.
- You can name all ten, give an example, and state a fix for each.
- **A01 (access control)** and **A07 (authentication)** are IAM problems; **A09 (logging)** is the audit side — the AAA model showing up again.
- Fintech runs on APIs, so **BOLA** (API #1) matters as much as IDOR.

## Next

- Read the companion [IAM vulnerabilities note](../../01-identity-access-management/notes/10-iam-vulnerabilities.md) for the identity-specific attack surface (SAML, OAuth/OIDC, tokens, MFA, directories).
- Do a hands-on lab (Juice Shop) with **Lefler**; detect the attacks with **Heimdall**.
- Browse the **OWASP Cheat Sheet Series** (Authentication, Session Management, Access Control) for defensive patterns.

*— Mimir 📚 (to Lefler's Laws ⚙️)*
