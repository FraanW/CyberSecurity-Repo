# 🔐 Note 02 — PQC Readiness Deep Dive for our IAM Estate (PingFederate + Entra ID)

> **Who this is for.** Upper management on the IAM/Cybersecurity team at FinCo. This is the *complete read* behind the one-page pitch in [`01-pqc-readiness-pitch.md`](01-pqc-readiness-pitch.md) — the long version you can defend under questioning.
>
> **Our stack, stated once so the whole doc is concrete:**
> - **Customers (CIAM):** **PingFederate** — the identity provider that logs in our external users/partners.
> - **Workforce:** **Microsoft Entra ID** — the identity provider that logs in our own employees.
>
> **As of:** July 2026. Vendor roadmaps move fast — the *facts* here are current, but **re-verify the vendor dates the week you present** (links are in every section).

---

## TL;DR (read this if you read nothing else)

Post-Quantum Cryptography (**PQC**) is the migration off **RSA and elliptic-curve (ECC)** crypto — the math a large quantum computer breaks — onto new NIST-standardized algorithms it can't. **No quantum computer can break our crypto today.** That is *not* the reason to act. Three things are:

1. **Harvest Now, Decrypt Later (HNDL).** An attacker records our encrypted customer traffic *today* and decrypts it years later when a quantum computer exists. For a fintech with **7–10 year data-retention** obligations, data we protect this quarter must still be secret on "quantum day." So the clock started already.
2. **The migration takes ~a decade** — past crypto migrations (SHA-1, TLS 1.0→1.2) each took ~10 years, and those were *easier*. The expensive part is *finding out what we have*.
3. **The deadlines already exist.** NIST says RSA/ECC **deprecated 2030, disallowed 2035.** The public internet is already ~a third migrated. We haven't inventoried ours.

**The ask is not a migration project.** It's **readiness**, at roughly analyst-time cost: (a) a **crypto inventory** across PingFederate + Entra, (b) **one PQC question** added to every vendor/renewal review, (c) **crypto-agility** as a design rule, (d) a **baseline metric** leadership can track yearly.

---

## The one-slide version (screenshot this into the deck)

| Question leadership will ask | The honest answer |
|---|---|
| Is quantum breaking our crypto today? | **No.** No cryptographically-relevant quantum computer exists yet. |
| Then why now? | **HNDL** — recorded customer data has a 7–10yr secrecy requirement; and migration takes ~10yr. **X + Y > Z.** |
| Is there a deadline? | **Yes.** NIST: RSA/ECC **deprecated 2030, disallowed 2035.** Auditor/partner-bank questionnaires arrive *before* mandates. |
| Do our vendors handle it? | **Partly.** Ping & Microsoft handle *their* code. Our **certs, keys, hard-coded algorithms, and integration glue** are ours. |
| What's the first step? | A **crypto inventory (CBOM)** of PingFederate + Entra. One spreadsheet, one quarter, ~zero budget. |
| What does "done" look like this year? | We can state **"% of our external TLS endpoints already PQC-capable"** — one number, tracked yearly. |

---

## Part 1 — What actually breaks, from first principles

**Why does any of our security work today?** Because some math is easy one way and effectively impossible to reverse on a normal computer:

- **RSA** rests on: multiplying two huge primes is easy; **factoring** the result back is infeasible.
- **ECC / ECDH / ECDSA** rests on: elliptic-curve point math is easy forward; reversing it (the "discrete log") is infeasible.

Every padlock in our estate — the TLS on the PingFederate login page, the signature on an Entra SAML token, the key protecting a session — ultimately leans on one of those two "hard to reverse" problems.

**Now the break.** In 1994 Peter Shor showed that a *quantum* computer running **Shor's algorithm** reverses **both** problems efficiently. Factoring stops being hard. Discrete log stops being hard. **RSA and ECC don't get weaker — they stop working as secrets.**

> **Analogy.** Today's locks are combination locks with a trillion-trillion combinations — safe because guessing takes longer than the universe lasts. A quantum computer isn't a faster guesser; it's a key that reads the combination off the dial. You don't buy a bigger version of the same lock. You change the *kind* of lock.

**The replacements** (this is what "PQC" means — new locks, standardized by NIST in **August 2024**):

| New standard | Old thing it replaces | What it's for in our world |
|---|---|---|
| **ML-KEM** — FIPS 203 (was "Kyber") | RSA / ECDH **key exchange** | Setting up the TLS session on every login page |
| **ML-DSA** — FIPS 204 (was "Dilithium") | RSA / ECDSA **signatures** | Signing **SAML assertions, JWTs/OIDC ID tokens**, certs |
| **SLH-DSA** — FIPS 205 (hash-based) | Conservative backup signature | Firmware/code-signing where you want zero lattice risk |

**See it in one command** (empirical thinking — Law 12). On any machine with a recent OpenSSL:
```bash
openssl list -kem-algorithms | grep -i mlkem
openssl list -signature-algorithms | grep -i mldsa
```
Since **OpenSSL 3.5 ships PQC by default**, these are already present on a lot of infrastructure — including, quietly, some of ours.

---

## Part 2 — Why "we have time" is the wrong answer

This is the part that wins or loses the room. Three arguments, each defeats "quantum is decades away."

### 2.1 Harvest Now, Decrypt Later — the deadline is *today*
An adversary who captures our encrypted traffic **now** doesn't need a quantum computer now. They **store the ciphertext** and decrypt it **whenever** the machine arrives. So the real question is never *"when does quantum arrive?"* It's:

> *"Will anything we encrypt **today** still need to be secret on the day quantum arrives?"*

For FinCo, that's not a maybe. **KYC records, PANs, account and transaction history** carry **7–10+ year retention** requirements. Customer traffic flowing through **PingFederate this quarter** — login credentials, tokens, PII in assertions — may **already** be sitting in someone's capture, waiting. We won't know for a decade. That's the whole trap: the loss is silent and already in progress.

### 2.2 Mosca's inequality — the arithmetic of "are we already late?"
Michele Mosca's rule of thumb:

> **If X + Y > Z, you are already exposed**, where
> **X** = years your data must stay secret · **Y** = years your migration takes · **Z** = years until a relevant quantum computer.

Plug in FinCo numbers:
- **X ≈ 7–10** (regulatory retention on customer financial data)
- **Y ≈ 8–10** (what real crypto migrations took — see below)
- **Z ≈ ?** (estimates cluster around the 2030s; nobody sells you certainty)

Even a *generous* Z of 2035 gives **X + Y ≈ 17 > 9.** We are not early. On the arithmetic, **we are late** — the only question is by how much.

### 2.3 The migration genuinely takes ~a decade (and finding-out is the slow part)
Two crypto migrations most of us lived through:
- **SHA-1 → SHA-256:** ~10 years from "deprecated" to "actually gone."
- **TLS 1.0/1.1 → 1.2:** ~a decade of dragging.

And here's the uncomfortable bit: **those were the easy ones** — same key sizes, same handshake shapes, drop-in swaps. PQC changes **key sizes, message sizes, and handshake bytes** (a hybrid TLS ClientHello grows by ~1KB). The slow, expensive phase isn't flipping the switch — it's the **discovery**: *where is our crypto, which algorithm, hard-coded or configurable, whose to change.* You cannot start that too early because it's the part that takes years.

### 2.4 It's a compliance clock, not a prediction
You don't have to believe any quantum forecast. The **dates already exist**:

| Authority | What it says | Date |
|---|---|---|
| **NIST IR 8547** | RSA/ECC (112-bit class, e.g. RSA-2048, P-256) **deprecated** | **2030** |
| **NIST IR 8547** | RSA/ECC **disallowed** (no use at all) | **2035** |
| **NSA CNSA 2.0** | US national-security systems fully quantum-safe | **2033** |
| **US OMB M-26-15** | Federal agencies align migration plans to IR 8547 | full migration **2035** |
| **The public internet** | Major browsers ship **hybrid PQC key exchange by default**; **>30% of TLS traffic** is already hybrid — the fastest crypto transition in internet history | **now** |

**For a Chennai fintech:** these NIST/US dates are the template auditors and **partner banks** copy into their questionnaires. Indian regulators (RBI/SEBI) have begun naming quantum risk in financial-stability discussions. The pattern is reliable: **questionnaires arrive before mandates.** "Here's our crypto inventory" is a very different meeting from "we haven't looked."

---

## Part 3 — Where the breakable crypto actually lives in *our* stack

The single most useful mental model for the whole topic: **crypto in an IAM estate lives in three layers**, and they have **very different urgency.** Get this and you sound like you did the homework.

| Layer | What it is | Quantum-vulnerable? | Urgency & why |
|---|---|---|---|
| **① Transport (TLS key exchange)** | The encrypted tunnel to every login/token endpoint | **Yes — via HNDL** | **HIGHEST.** Recorded today, decrypted later. This is the only layer where waiting = data already lost. |
| **② Long-lived keys & certs** | Signing certs, device certs, any key alive for *years* | **Yes** | **HIGH.** Not HNDL, but a multi-year key you issue now must survive into the quantum era. |
| **③ Application signatures** | SAML assertion signatures, JWT/OIDC token signatures | **Yes — but later** | **LOWEST.** A recorded signature can't be "decrypted later"; **forging** one needs the quantum computer to *already exist.* Short-lived tokens are the least urgent thing we own. |

Now map our two towers onto those layers.

### 3.1 Customer tower — PingFederate (CIAM)

| Where crypto lives in PingFederate | Layer | Notes for our estate |
|---|---|---|
| **TLS on the login & token endpoints** (`/as/token.oauth2`, `/idp/SSO.saml2`, admin API) | ① Transport | The HNDL-critical surface — every customer credential and token crosses it. **Where is TLS terminated?** (See Part 4 — this decides our fastest lever.) |
| **mTLS to partner banks / APIs** | ① Transport + ② Keys | Client-cert auth between services; the certs are long-lived. |
| **SAML assertion signing certs / OIDC (JWT) signing keys** | ③ Signatures (+② the cert) | RS256/ES256 today. The *signature* is low-urgency; the *signing cert's lifetime* matters. |
| **Keys at rest** (in the PingFederate keystore / HSM) | ② Keys | How agile is rotation? Config or code? |

### 3.2 Workforce tower — Microsoft Entra ID

| Where crypto lives in Entra | Layer | Notes for our estate |
|---|---|---|
| **TLS to `login.microsoftonline.com`** | ① Transport | **Microsoft-operated.** We consume their front-door PQC rollout; we don't configure it. |
| **Token signing** (OIDC ID/access tokens, SAML) | ③ Signatures | RSA + SHA-256 today, **Microsoft-managed.** We can't swap Entra's signing algorithm — we track their roadmap and make sure *our apps* can accept whatever it rotates to. |
| **Hybrid-join / device certs, Windows Hello, any internal ADCS PKI** | ② Keys | **This is the part we actually control on the workforce side** — see Part 4. |
| **Conditional Access, app registrations, federation trusts** | ③ + config | The relying-party side: can our registered apps tolerate an algorithm change without breaking? |

> **The uncomfortable truth this table surfaces:** for **both** IdPs, the crypto that matters most (transport ① and signing ③) is **largely operated by the vendor/platform**, while the crypto we **directly control** is the long-lived key/cert layer ② and the **relying-party glue**. That's exactly why the ask is *inventory + vendor pressure + agility*, not "deploy PQC ourselves."

---

## Part 4 — Vendor reality check (what Ping & Microsoft actually ship, July 2026)

This is where the pitch earns credibility — real, current specifics, with the nuance that separates "read a headline" from "understands the stack."

### 4.1 PingFederate — it's a **Java app**, so its TLS rides on the **JDK**
The most important fact about PingFederate's PQC timeline: **PingFederate runs on the JVM**, so its native TLS crypto is whatever the **Java Development Kit (JDK)** provides.

- **ML-KEM** landed in the JDK's crypto provider (JCA) — preview in **JDK 24**, finalized in **JDK 25**.
- **Native hybrid PQC key exchange for TLS 1.3** (`X25519MLKEM768` and friends) arrives via **JEP 527 in JDK 27 (~September 2026).**
- **Therefore:** PingFederate can't negotiate hybrid PQC TLS *natively* until it's certified and running on a JDK that ships it. Through the **13.0 (Dec 2025)** release line, PingFederate release notes show **no dedicated ML-KEM/PQC TLS feature yet.**

**The practical near-term lever (this is the good news for the room):** In most real deployments, **TLS to PingFederate is terminated in front of it** — at a load balancer / reverse proxy / WAF / CDN (F5, NGINX, Cloudflare, Akamai, AWS ALB…). **Those already support hybrid PQC today.** So we may be able to make the customer-facing transport quantum-safe **without touching PingFederate at all** — by enabling hybrid key exchange at the terminator. *The inventory tells us where TLS actually terminates* — which is the single highest-value thing the inventory answers.

- **Signatures (SAML/JWT):** still RSA/ECDSA, and that's fine per Part 3 — lowest urgency, and blocked ecosystem-wide (see 4.3).
- 📌 **Verify at pitch time:** current PingFederate release notes → <https://docs.pingidentity.com/pingfederate/latest/release_notes/pf_release_notes.html>, and which JDK your deployment runs on.

### 4.2 Entra ID — strong Microsoft *platform* PQC, but Entra's own **token signing still classical**
Microsoft is one of the most advanced vendors here — but be precise about *which* Microsoft product:

- **Platform crypto (Windows / SymCrypt / .NET):** ML-KEM and ML-DSA went into the CNG libraries on **Windows Server 2025 / Windows 11** in the **Nov 2025** update. **TLS hybrid key exchange (ML-KEM)** is available on Windows 11 via recent KBs.
- **AD CS (internal PKI):** issuing **ML-DSA certificates** went **GA in May 2026** (ML-DSA-44/65/87). → If we run **internal ADCS** for workforce/device certs, **PQC certs are available to us now.**
- **Entra ID token signing (the IdP itself):** still **RSA + SHA-256**; **no GA ML-DSA token signing** for OIDC/SAML. Microsoft published its PQC strategy (Sept 2024, aligned to FIPS 203/204/205) and is migrating, but the IdP signing swap is gated on IETF drafts and the relying-party ecosystem (see 4.3).

**What this means for the workforce tower:** the transport (①) and Entra token signing (③) are **Microsoft's to migrate on their schedule** — our job is to **track it and make our relying-party apps agile**. The layer we can act on **now** is **② — internal ADCS certs, device/hybrid-join certs** — where PQC is already GA.

- 📌 **Verify at pitch time:** Microsoft PQC hub → <https://techcommunity.microsoft.com/blog/microsoft-security-blog/> (search "post-quantum"), and Entra roadmap.

### 4.3 Why *nobody's* IdP signs tokens with ML-DSA yet (and why that's OK)
Expect the question *"if it's standardized, why isn't Entra/Ping signing with it?"* The honest, credible answer:

- The **IETF drafts** that define how PQC signatures ride inside **TLS and tokens** are **not final** — e.g. `draft-ietf-tls-mldsa` was still in working-group last call as of **May 2026**.
- If an IdP shipped **GA ML-DSA token signing against a draft identifier**, and the draft-to-RFC number changed, **every relying party** (every app consuming our tokens) would need an **emergency update**. No serious IdP does that.
- **And it doesn't matter yet** (Part 3): signatures are the *lowest-urgency* layer. HNDL doesn't apply. This is the one place "we have time" is actually *true* — so we spend our early effort on **transport and long-lived keys**, not signatures.

---

## Part 5 — The priority order (put this on a slide)

Straight from the three-layer model — this is the whole strategy in five lines:

1. **① TLS / key-exchange inventory first.** HNDL makes it the only urgent layer. Focus: **where is customer-facing TLS terminated** (PingFederate front door), and can we turn on hybrid there **today**.
2. **② Long-lived keys & certs second.** PAM/SSH keys, mTLS client certs, device certs, internal ADCS. Anything alive for years and issued *now* should be on a rotation path — and where PQC certs are GA (ADCS), start issuing them in a test PKI.
3. **③ Signatures last** (SAML/JWT). Wait for the RFCs; ensure relying parties are **crypto-agile** so the eventual swap is a config change, not a rewrite.
4. **Crypto-agility everywhere:** algorithms in **config, not code**, so the next swap (there's always a next one) is a change ticket.
5. **Measure one number:** **% of external TLS endpoints already hybrid-PQC-capable** — baseline it, track it yearly.

---

## Part 6 — The ask (small, cheap, unrefusable)

Do **not** ask leadership for a migration program or capex. Ask for **readiness**:

1. **Start a Cryptography Bill of Materials (CBOM)** across PingFederate + Entra: where we use RSA/ECC, key sizes, cert lifetimes, **where TLS terminates**, what's hard-coded vs. configurable. **One spreadsheet, one quarter, analyst time.**
2. **Add one question to every vendor & renewal review:** *"What is your PQC roadmap and hybrid-TLS support date?"* — Ping, Microsoft, our load-balancer/WAF/CDN vendor, HSM, partner banks.
3. **Adopt crypto-agility as a design rule** for anything new: algorithms in config, pluggable providers, no hard-coded `RS256`.
4. **Report a baseline metric:** % of external TLS endpoints already hybrid-PQC-capable — one scan, one number, tracked yearly.

**Closing line for the meeting:**
> *"I'm not asking us to deploy PQC. I'm asking us to know what we'd have to change — because the finding-out is the part that takes years, and the recording of our customer traffic has already started."*

---

## Part 7 — See it with your own eyes (2-minute live demo — Law 12)

Make the abstract real *in the room*. Two options:

**A. The browser already migrated; our estate hasn't been looked at.**
1. Open **<https://pq.cloudflareresearch.com/>** in Chrome or Edge.
2. It reports whether your connection used **post-quantum key agreement** — it almost certainly did.
3. **DevTools → Security tab** → the key exchange shows **`X25519MLKEM768`**.
4. **The line:** *"My laptop negotiated post-quantum crypto to load a web page. We haven't checked whether our customer login page can. That gap is the entire ask."*

**B. Prove the algorithms are already on our infrastructure.**
```bash
# On any box with OpenSSL 3.5+
openssl list -kem-algorithms | grep -i mlkem      # expect: ML-KEM-512/768/1024
openssl s_client -connect cloudflare.com:443 -groups X25519MLKEM768 </dev/null 2>/dev/null | grep -i "Negotiated\|group"
```
✅ **Checkpoint:** a successful hybrid handshake to a public server proves the *client* side is ready; the question the CBOM answers is whether *our* servers are.

> **Gotcha:** older OpenSSL (1.1.x, ≤3.4) won't list ML-KEM — that's a version artifact, not proof PQC is unavailable. Check the version with `openssl version` first.

---

## Part 8 — Objection handling (you *will* hear these)

| Objection | Your response |
|---|---|
| "Quantum is decades away." | **HNDL** exposes our 7–10yr-retention customer data **today**; and NIST's **2030/2035** dates don't wait for the machine. |
| "Ping and Microsoft will handle it." | They handle *their* transport and signing. Our **certs, key rotations, where TLS terminates, hard-coded algorithms, and relying-party apps** are **ours** — the inventory tells us which is which. |
| "There's no budget." | The ask is an inventory + a vendor question = **analyst time, not capex.** Doing nothing is what creates the future *emergency* budget. |
| "No regulator requires it yet." | **Auditor and partner-bank questionnaires arrive before mandates.** "Here's our CBOM" beats "we haven't looked" — and it's cheap to be the team that saw it coming. |
| "Why not just wait for the RFCs?" | For **signatures**, we *are* waiting — correctly (Part 4.3). But **transport (HNDL)** and **long-lived keys** can't wait for signature RFCs; they're separate clocks. |
| "Can't we just flip it on when quantum's closer?" | The switch is fast; the **discovery** is what took a decade every prior migration. We're buying the *discovery*, not the switch. |

---

## Part 9 — The cost of doing nothing (frame the downside)

| If we skip readiness… | The bill arrives as… |
|---|---|
| Customer traffic recorded now | Silent breach of KYC/PAN/transaction data disclosed years later — **the loss is already happening, we just can't see it.** |
| No inventory when the questionnaire lands | Scramble to answer a partner-bank/auditor PQC questionnaire in weeks, with no data — the "we haven't looked" meeting. |
| No crypto-agility | The eventual swap is a **rewrite** across apps, not a config change — the expensive version of the same work. |
| We start in 2030 instead of now | A ~decade migration crashes into a 2035 disallowance — **the emergency-budget scenario.** |

---

## What you learned
- **PQC readiness ≠ deploying PQC.** It's **inventory + crypto-agility + vendor pressure**, started now because the *discovery* takes years.
- **HNDL** and **Mosca's X + Y > Z** are the two ideas that defeat "we have time" — and for a fintech, the arithmetic says we're already late.
- Crypto lives in **three layers** with different clocks: **transport (urgent, HNDL) → long-lived keys → signatures (can wait for RFCs).**
- **Our stack specifics:** **PingFederate** = Java, so TLS rides the **JDK (JEP 527, JDK 27, ~Sept 2026)** *or* the **TLS-terminating proxy in front of it** (our fastest lever). **Entra ID** = Microsoft SaaS: platform PQC is strong (**ADCS ML-DSA GA May 2026**), but Entra's **token signing is still classical** and **Microsoft's to migrate** — we track it and keep our relying-party apps agile.
- The **ask is cheap and unrefusable:** a CBOM, one vendor question, an agility rule, one tracked metric.

**Next:**
- Build the empirical demo — capture a hybrid `X25519MLKEM768` handshake vs. a classical one in Wireshark → `04-cryptography/labs/01-pqc-tls-handshake/` *(to be created)*.
- Draft the actual **CBOM spreadsheet template** for PingFederate + Entra (columns: endpoint, layer ①②③, algorithm, key size, cert lifetime, where TLS terminates, hard-coded vs config, vendor roadmap date).
- Pair with the elevator version in [`01-pqc-readiness-pitch.md`](01-pqc-readiness-pitch.md) for the actual meeting.

---

### Sources (verify vendor dates at pitch time)
- NIST IR 8547 transition timeline (deprecate 2030 / disallow 2035): <https://csrc.nist.gov/pubs/ir/8547/ipd>
- NIST PQC standards FIPS 203/204/205 (Aug 2024): <https://www.nist.gov/news-events/news/2024/08/nist-releases-first-3-finalized-post-quantum-encryption-standards>
- Microsoft PQC on Windows / SymCrypt / ADCS: <https://techcommunity.microsoft.com/blog/microsoft-security-blog/post-quantum-cryptography-apis-now-generally-available-on-microsoft-platforms/4469093>
- Microsoft AD CS ML-DSA support (GA May 2026): <https://directaccess.richardhicks.com/2026/05/18/microsoft-ad-cs-adds-post-quantum-cryptography-support-with-ml-dsa/>
- Java JEP 527 — Post-Quantum Hybrid Key Exchange for TLS 1.3 (JDK 27): <https://openjdk.org/jeps/527>
- Java JEP 496 — ML-KEM (JDK 24/25): <https://openjdk.org/jeps/496>
- PingFederate release notes: <https://docs.pingidentity.com/pingfederate/latest/release_notes/pf_release_notes.html>
- Cloudflare PQC (browser/network deployment status): <https://developers.cloudflare.com/ssl/post-quantum-cryptography/>
- Live PQC connection test: <https://pq.cloudflareresearch.com/>
