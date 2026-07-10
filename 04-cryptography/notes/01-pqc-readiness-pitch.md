# 🔮 Note 01 — Pitching PQC Readiness to Leadership (without getting the "we have time" brush-off)

> **TL;DR:** Post-Quantum Cryptography (PQC) is the migration away from RSA/ECC before quantum computers can break them. The pitch is **not** "quantum is coming, panic." The pitch is: **attackers can record our encrypted traffic today and decrypt it later, regulators have already set deadlines, and the fix takes ~a decade — so the cheap first step (an inventory) has to start now.** Ask for something small: a crypto inventory of the three towers + one question added to vendor reviews.

---

## First, what PQC is (30 seconds, plain words)

Today's encryption rests on math problems that normal computers can't solve — factoring huge numbers (**RSA**) and elliptic-curve math (**ECC**). A large-enough quantum computer running **Shor's algorithm** solves both. **PQC** = new algorithms, standardized by NIST in **August 2024**, that quantum computers *can't* break:

| New standard | Replaces | Used for |
|---|---|---|
| **ML-KEM** (FIPS 203, "Kyber") | RSA/ECDH key exchange | Setting up TLS sessions |
| **ML-DSA** (FIPS 204, "Dilithium") | RSA/ECDSA signatures | Certs, JWTs, SAML signing |

No cryptographically-relevant quantum computer exists yet. **That's not the point — timing is.**

---

## The three arguments that kill "we have a lot of time"

### 1. Harvest Now, Decrypt Later (HNDL) — the deadline is *today*, not quantum day
An adversary who records our encrypted traffic **now** just stores it and decrypts it **whenever** quantum arrives. So the real question isn't *"when do quantum computers arrive?"* — it's *"will any data we encrypt **today** still need to be secret on that day?"*

For a fintech, obviously yes: KYC records, PANs, transaction history carry **7–10+ year retention** requirements. Data we send over classical TLS *this quarter* may already be compromised — we just won't know for a decade.

### 2. Mosca's inequality — the math of "are we already late?"
> **If X + Y > Z, you're already in trouble**, where
> **X** = years the data must stay secret, **Y** = years the migration takes, **Z** = years until a quantum computer.

Plug in real numbers: X ≈ 7–10 (regulatory retention) and Y ≈ 8–10 — that's what past crypto migrations actually took (SHA-1 deprecation and TLS 1.0 → 1.2 each dragged on for roughly a decade, and those were *easy* — same key sizes, same protocols). If Z is 2035, **X + Y ≈ 17 > 9.** We're not early. We're arguably late.

### 3. The deadlines already exist — this is a compliance clock, not a prediction
- **NIST IR 8547:** RSA/ECC **deprecated by 2030, disallowed after 2035.**
- **NSA CNSA 2.0:** US national-security systems fully quantum-safe by **2033**.
- **The internet already moved:** Chrome, Firefox, iOS, OpenSSH and Cloudflare ship **hybrid PQC key exchange (X25519MLKEM768) by default today.** A meaningful share of global TLS traffic is already quantum-safe — while we haven't inventoried ours.
- **India:** RBI/SEBI have begun flagging quantum risk in financial-stability discussions; for a fintech, expect regulator and partner-bank questionnaires to follow the NIST dates.

**See it yourself (2 min):** open <https://pq.cloudflareresearch.com/> in Chrome → it tells you your connection already used post-quantum key agreement. DevTools → Security tab shows `X25519MLKEM768`. Great live demo in the meeting: *"your laptop already migrated; our estate hasn't been looked at."*

---

## The AI angle (connect it to what's already happening)

AI bots are **already** scanning us and surfacing vulnerabilities — the gap between "theoretical weakness" and "found and exploited at scale" is collapsing, because discovery is now automated. Weak/legacy crypto is exactly the kind of finding automated scanners surface next: expired certs, RSA-1024, deprecated TLS. **PQC readiness is the same discipline** — you can't fix crypto you haven't inventoried, just like you can't patch assets you haven't discovered. The inventory we'd build for PQC (a **CBOM — Cryptography Bill of Materials**) is also what lets us answer *any* future crypto finding in hours instead of weeks.

---

## What it means for each tower (make it concrete for the room)

| Tower | Where quantum-vulnerable crypto lives | First question to ask |
|---|---|---|
| **PAM** | Vaulted **SSH keys** (RSA/ECDSA) that live for *years*, vault TLS, session-recording signatures | Can our PAM vendor rotate to PQC keys? What's their roadmap? |
| **Workforce (SailPoint)** | **SAML signing certs** (RSA), LDAPS/TLS to connectors, signed provisioning traffic | What does SailPoint's PQC roadmap look like? How agile are our signing-cert rotations? |
| **AuthN (products)** | **JWT signing** (RS256/ES256), TLS on login endpoints, **mTLS between services**, FIDO2/passkeys (ECDSA) | Which endpoints already negotiate hybrid TLS? Where are key algorithms hard-coded? |

**A nuance that makes you sound like you did the homework:** key exchange (TLS) is urgent *now* because of HNDL; signatures (JWTs, SAML) matter only once a quantum computer actually exists — a recorded signature can't be "decrypted later," and forging one requires the machine. So the priority order is **1) TLS/key-exchange inventory, 2) long-lived keys (PAM SSH keys!), 3) signatures.** Short-lived access tokens are the *least* urgent thing we own.

---

## The ask (small, cheap, unrefusable)

Don't ask for a migration program. Ask for **readiness**, ~zero budget:

1. **Start a crypto inventory (CBOM)** across the three towers: where we use RSA/ECC, key sizes, cert lifetimes, what's hard-coded vs. configurable. One spreadsheet, one quarter.
2. **Add one question to every vendor review:** *"What is your PQC migration roadmap and hybrid-TLS support date?"* (SailPoint, PAM vendor, IdP, HSM, partner banks.)
3. **Adopt crypto-agility as a design rule** for new systems: algorithms in config, not code — so the eventual swap is a change ticket, not a rewrite.
4. **Report a baseline:** % of our external TLS endpoints already capable of hybrid PQC. One scan, one number leadership can track yearly.

**Closing line for the meeting:** *"I'm not asking us to deploy PQC. I'm asking us to know what we'd have to change — because the finding-out is the part that takes years, and the recording of our traffic has already started."*

---

## Objection handling (you will hear these)

| Objection | Response |
|---|---|
| "Quantum computers are decades away." | HNDL means our long-retention data is exposed **today**. And NIST's 2030/2035 dates don't wait for the machine. |
| "Vendors will handle it." | Vendors handle *their* code. Our SSH keys, cert rotations, hard-coded algorithms and integration glue are ours — the inventory tells us which is which. |
| "There's no budget." | The ask is an inventory and a vendor question — analyst time, not capex. Doing nothing is what creates the future emergency budget. |
| "No regulator requires it yet." | Auditor and partner-bank questionnaires arrive *before* mandates. "Here's our CBOM" beats "we haven't looked" — and it's cheap to be the team that saw it coming. |

---

## What you learned
- PQC readiness ≠ deploying PQC — it's **inventory + crypto-agility + vendor pressure**, starting now because migrations take ~a decade.
- **HNDL** and **Mosca's inequality (X + Y > Z)** are the two ideas that defeat "we have time."
- Key exchange first, long-lived keys second, signatures last — and each tower (PAM / SailPoint / AuthN) has a concrete first question.

**Next:** build the actual demo — a lab capturing a hybrid `X25519MLKEM768` TLS handshake vs. a classical one in Wireshark (`04-cryptography/labs/01-pqc-tls-handshake/`, to be created).
