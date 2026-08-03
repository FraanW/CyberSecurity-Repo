# 🛡️ Note 04 — Post-Quantum Cryptography (PQC): the fix, the algorithms, and how a fintech becomes quantum-resilient

> **TL;DR:** Post-Quantum Cryptography is **new public-key algorithms that run on today's ordinary computers but resist attacks from a future quantum computer.** They replace the crypto Shor's algorithm breaks (RSA/ECC key exchange and signatures) with math based on **different hard problems** — mostly **lattices** — that neither classical *nor* quantum machines can solve efficiently. NIST finalized the first standards in **August 2024**: **ML-KEM** (key exchange), **ML-DSA** and **SLH-DSA** (signatures). The winning migration play for a fintech isn't a rip-and-replace — it's **hybrid** (classical + PQC together), driven by **crypto-agility** (algorithms in config, not hard-coded), and it starts with a **crypto inventory**, because migrations take ~a decade and the "harvest now, decrypt later" clock is already running. This note is the **technical** companion to the boardroom pitch in [`01-pqc-readiness-pitch.md`](01-pqc-readiness-pitch.md).

**Prereqs:** [`02-cryptography-deep-dive.md`](02-cryptography-deep-dive.md) (what breaks) and [`03-quantum-computing-the-quantum-realm.md`](03-quantum-computing-the-quantum-realm.md) (why it breaks). This note is the "now what do we do about it."

---

## 1. First, get the name right (VPs test vocabulary)

Three terms people smear together — separate them cleanly:

| Term | What it is | Runs on… |
|---|---|---|
| **PQC** (Post-Quantum Cryptography) | New **algorithms / math** that resist quantum attacks | **Today's normal computers** ✅ |
| **Quantum Cryptography** (e.g. **QKD**) | Using **quantum physics hardware** to exchange keys | Special quantum hardware/fibre |
| **Quantum-safe / quantum-resistant** | Umbrella adjective = "not broken by a quantum computer" | (describes either of the above) |

> **The correction to have ready:** *"Post-quantum crypto is not quantum crypto. PQC is new math that runs on the laptops and servers we already own — that's exactly why it's the practical answer. Quantum cryptography (QKD) needs special hardware and dedicated fibre, so NIST and the NSA both steer general industry toward PQC instead."* (Also called **PQC** or sometimes **QRC** — quantum-resistant cryptography. Same thing.)

---

## 2. Recap the threat in one screen (the setup for the fix)

From Notes 02–03, distilled to what a fintech must act on:

| What we use today | Quantum fate | Why |
|---|---|---|
| **RSA, ECDH, ECDSA, EdDSA** (all asymmetric) | ☠️ **Broken** | **Shor's algorithm** solves factoring + discrete logs efficiently |
| **AES, ChaCha20** (symmetric) | ✅ **Weakened only** | **Grover's** halves strength → **use AES-256** |
| **SHA-256 / SHA-3** (hashes) | ✅ **Weakened only** | Grover → **prefer SHA-384+** |

**Two ideas that make the timeline urgent (memorize both — they defeat "we have time"):**

1. **Harvest Now, Decrypt Later (HNDL).** An adversary records our encrypted traffic **today** and simply waits. The moment a **CRQC** (cryptographically-relevant quantum computer) exists, they decrypt the archive. **So the deadline isn't "when does quantum arrive" — it's "does any data we send today still need to be secret then?"** For fintech (KYC, PANs, transactions with 7–10+ year retention), the answer is obviously yes → **we're already exposed on the wire.**

2. **Mosca's Inequality — the math of "are we late?"**
   > **If X + Y > Z, you're already in trouble.**
   > **X** = years the data must stay secret · **Y** = years the migration takes · **Z** = years until a CRQC exists.

   Plug in fintech reality: X ≈ 7–10 (retention), Y ≈ 8–10 (what SHA-1 and TLS migrations *actually* took), Z ≈ 2035. **X + Y ≈ 17 > 9.** We are not early. Arguably we're already behind.

---

## 3. How PQC works — swap the hard problem

The trick is simple to state: **base security on a math problem that's hard for quantum computers too.** Shor's algorithm is a specialist — it eats *factoring* and *discrete logs*. It does **not** help with several *other* hard problems. PQC builds on those. Five families, each a different bet:

| Family | The hard problem (plain words) | Vibe | Standardized? |
|---|---|---|---|
| **Lattice-based** ⭐ | Find the shortest vector / nearest point in a huge multi-dimensional grid — easy to state, brutally hard to solve | Fast, small-ish keys, versatile | ✅ **Yes — the main winners** |
| **Hash-based** | Security relies *only* on a hash function being secure (§ Note 02 §6) | Ultra-conservative, very trusted; big signatures | ✅ Yes (SLH-DSA) |
| **Code-based** | Decode a deliberately error-riddled message without the secret pattern (40+ years unbroken) | Very trusted; **huge** public keys | ✅ Yes (HQC, 2025) |
| **Isogeny-based** | Navigate maps between elliptic curves | Tiny keys — **but** the front-runner (SIKE) was **broken in 2022** on a classical laptop | ⚠️ Research |
| **Multivariate** | Solve big systems of nonlinear equations | Small signatures; several broken | ⚠️ Mostly research |

> **The lattice one-liner to say out loud:** *"Imagine an infinite grid of points in a thousand dimensions. Given a random spot, finding the *nearest* grid point is easy to describe and — as far as anyone knows, classical or quantum — computationally brutal. That 'nearest-point' hardness is what most of the new standards rest on. It's a different mountain entirely from the factoring mountain Shor's algorithm learned to climb."*

**⚠️ The honesty caveat that makes you credible:** PQC math is *newer* and less battle-tested than RSA. SIKE's 2022 collapse — an entire NIST finalist broken over a weekend on a normal computer — is the cautionary tale. **That's precisely why the industry deploys PQC in *hybrid* mode (§5): if the new algorithm falls, the classical one still guards you.**

---

## 4. The actual standards — what NIST published (know these names cold)

NIST ran an **open, 8-year global competition** (2016–2024) — the same open-scrutiny model that gave us AES. The finalized standards (**August 13, 2024**):

| Standard | Nickname | Type | Replaces | Where it lands in *your* stack |
|---|---|---|---|---|
| **ML-KEM** (FIPS 203) | **Kyber** | Key encapsulation (KEM) — agree on a session key | RSA / ECDH key exchange | **TLS handshakes**, VPNs — the HNDL-urgent one |
| **ML-DSA** (FIPS 204) | **Dilithium** | Digital signature | RSA / ECDSA signatures | **JWT signing, SAML certs, code signing** — the default signature |
| **SLH-DSA** (FIPS 205) | **SPHINCS+** | Hash-based signature | (backup signature) | Firmware/root-of-trust — slow & big, but maximally conservative |
| **FN-DSA** (FIPS 206, draft) | **Falcon** | Compact lattice signature | ECDSA where size matters | Constrained systems needing small signatures |
| **HQC** (selected Mar 2025) | — | Code-based KEM | (backup for ML-KEM) | Diversity hedge — different math than Kyber |

**Decode the naming (a nice detail to drop):** NIST renamed the winners to describe the *math*, not the marketing. **ML** = "Module-Lattice"; **SLH** = "Stateless Hash." So **ML-KEM = Module-Lattice Key-Encapsulation Mechanism.** Knowing "Kyber = ML-KEM = FIPS 203" in one breath signals you did the reading.

**The priority order that shows real understanding:**
> *"Key exchange first, signatures later — and here's why. A recorded **key exchange** can be **harvested and decrypted later** (HNDL), so ML-KEM is urgent **now**. A **signature** can't be 'decrypted later' — forging one requires the quantum computer to already exist — so ML-DSA can follow. And long-lived **signing keys and SSH keys** matter more than short-lived access tokens. So the sequence is: **1) TLS/key-exchange → 2) long-lived keys → 3) signatures → and short-lived tokens last.**"*

---

## 5. Hybrid mode — the deployment pattern that's already live

Nobody flips straight from ECC to pure PQC. The industry standard is **hybrid**: run the **classical** algorithm **and** the **PQC** algorithm together, and combine both shared secrets. **You're safe unless *both* break.**

- **Why hybrid:** it hedges the §3 risk. If ML-KEM has an undiscovered flaw (SIKE-style), the classical X25519 still protects you. If the quantum computer arrives, the PQC half protects you. Belt *and* braces.
- **The concrete name to know:** **X25519MLKEM768** — classical Curve25519 **+** ML-KEM-768 combined. This is what's shipping.
- **It's not theoretical — it's already the default on the internet:** Chrome, Firefox, Edge, Apple iMessage (PQ3), Signal, OpenSSH, and Cloudflare have **turned hybrid PQC key exchange on by default.** A large and growing share of global HTTPS traffic is *already* quantum-safe on the key-exchange side.

**See it yourself (2 min — great live demo for the meeting):**
1. Open **<https://pq.cloudflareresearch.com/>** in an up-to-date Chrome.
2. It reports whether your connection used post-quantum key agreement.
3. Chrome **DevTools → Security tab** shows the negotiated group — look for **`X25519MLKEM768`**.

✅ **Checkpoint / the killer line:** *"My laptop's browser already migrated to post-quantum key exchange — automatically, months ago. The open question isn't whether PQC works. It's whether **our** estate has even been looked at."*

---

## 6. Why a fintech like FinCo specifically benefits — and the risks of not moving

Make it about money, regulators, and trust — not physics.

**The upside of being early:**
| Benefit | What it means for FinCo |
|---|---|
| **Protect long-lived secrets** | KYC, PANs, transaction history have 7–10+ yr retention. PQC on the wire kills the HNDL exposure that classical TLS can't. |
| **Get ahead of regulators** | Auditor and partner-bank questionnaires arrive *before* mandates. "Here's our crypto inventory" beats "we haven't looked." |
| **Win enterprise/partner trust** | Partner banks and large clients will start *requiring* a PQC roadmap in vendor due-diligence. Having one is a sales asset. |
| **Cheaper now than in crisis** | An orderly, multi-year migration is analyst time. A rushed one after a CRQC announcement is emergency capex + downtime. |

**The compliance clock (this is a deadline, not a prediction):**
- **NIST IR 8547:** RSA/ECC **deprecated by 2030, disallowed after 2035.**
- **NSA CNSA 2.0:** US national-security systems quantum-safe by **2033**.
- **PCI-DSS:** already mandates "strong cryptography" and crypto-agility for cardholder data — PQC is the natural next reading of that.
- **India (your context):** **RBI/SEBI** have started flagging quantum risk in financial-stability discussions; expect regulator and partner-bank questionnaires to track the NIST dates.

**Where the quantum-vulnerable crypto actually lives in an IAM shop (make it concrete):**
| Tower | Vulnerable crypto | First question to ask |
|---|---|---|
| **PAM** | Vaulted **SSH keys** (RSA/ECDSA) that live for *years*, vault TLS, session-recording signatures | Can our PAM vendor rotate to PQC keys? Roadmap? |
| **Workforce IGA (SailPoint)** | **SAML signing certs** (RSA), LDAPS/TLS to connectors, signed provisioning | What's the vendor's PQC roadmap? How agile are signing-cert rotations? |
| **AuthN (customer products)** | **JWT signing** (RS256/ES256), login-endpoint TLS, **mTLS between services**, FIDO2/passkeys (ECDSA) | Which endpoints already negotiate hybrid TLS? Where are algorithms hard-coded? |

---

## 7. Crypto-agility — the real deliverable (not "deploy PQC")

**The single most important concept in this whole note.** The goal isn't "install Kyber." It's to make swapping *any* algorithm a **config change, not a code rewrite** — because there *will* be a next migration after this one.

**What crypto-agility means in practice:**
- **Algorithms in configuration, not hard-coded.** `algorithm = "ES256"` in a config file — not `ES256` scattered across 40 code files. The eventual swap becomes a change ticket.
- **A crypto inventory (CBOM — Cryptography Bill of Materials).** You can't fix crypto you can't see. Catalogue *where* you use RSA/ECC, key sizes, cert lifetimes, and what's hard-coded vs. configurable — one spreadsheet, one quarter. This is the same discipline as asset inventory: you can't patch what you haven't discovered.
- **Abstraction layers.** Route crypto through a library/service you can upgrade centrally, so one change propagates everywhere.
- **Fast key/cert rotation as muscle memory.** If rotating a signing cert is already a painless, routine drill, the PQC swap is just another rotation.

> **Why you care:** the team that inventoried its crypto for PQC can answer **any** future crypto finding — an expired cert, an RSA-1024 straggler, the next SHA-1 — in **hours instead of weeks.** The CBOM outlives this one migration. That's the durable win to sell internally.

---

## 8. The FinCo PQC-resilience roadmap (a phased plan you can present)

A defensible, low-drama sequence. Note it maps to the "small, unrefusable ask" in [`01-pqc-readiness-pitch.md`](01-pqc-readiness-pitch.md) — this is the fuller engineering version.

| Phase | Timeframe | What you do | Deliverable |
|---|---|---|---|
| **1 · Discover** | Now (this quarter) | Build the **CBOM**: inventory all RSA/ECC use across PAM / IGA / AuthN; key sizes, cert lifetimes, hard-coded vs. config. Add *"What's your PQC roadmap?"* to every vendor review. | A crypto inventory + a baseline metric (% of external TLS endpoints already hybrid-capable). |
| **2 · Prioritize** | Next 1–2 quarters | Rank by risk using HNDL + data lifetime: **long-retention data over the wire first** (key exchange), then long-lived keys (PAM SSH keys!), then signatures. | A ranked migration backlog. |
| **3 · Pilot** | 6–12 months | Turn on **hybrid TLS (X25519MLKEM768)** on a non-critical external endpoint. Measure the (small) handshake-size/latency cost. Test that monitoring/DLP/load-balancers cope with bigger handshakes. | A working hybrid endpoint + a performance report. |
| **4 · Adopt agility** | Ongoing | Make **crypto-agility a design rule** for all new systems: algorithms in config, central crypto library, rehearsed rotations. | An engineering standard + updated architecture review checklist. |
| **5 · Migrate** | Multi-year, tracked | Roll hybrid → PQC across the estate, worst-risk first, pressuring vendors on their roadmaps. Report the % quantum-safe number to leadership yearly. | A tracked, auditable migration program. |

**Gotchas to flag before they bite (Law 6):**
- **Bigger keys/handshakes.** PQC keys and signatures are **larger** than ECC — ML-KEM handshakes are bigger, SLH-DSA signatures much bigger. Watch for problems in size-sensitive spots: **TLS record limits, packet fragmentation, embedded/IoT devices, and QR-code-sized payloads.** Pilot to catch these.
- **Vendor lag.** Your PQC timeline is hostage to your vendors' (SailPoint, PAM, HSM, IdP). Start the roadmap questions **now** — that's the long pole.
- **Don't drop the classical half early.** Stay **hybrid** until PQC has years more scrutiny. Pure-PQC too soon reintroduces the SIKE-style single-point-of-failure risk.
- **Crypto-agility is the point, not any one algorithm.** If ML-KEM were ever weakened, an agile shop swaps it via config. Build for the swap, not the specific winner.

---

## 9. Objection handling — what your VP will push back with

| Objection | Your response |
|---|---|
| *"Quantum's decades away — why now?"* | **HNDL** exposes our long-retention data **today**, and **Mosca's X+Y>Z** says an 8–10-yr migration against 7–10-yr secrecy is *already* underwater. NIST's 2030/2035 dates don't wait for the machine. |
| *"Vendors will handle it."* | Vendors handle *their* code. Our **SSH keys, cert rotations, hard-coded algorithms, and integration glue** are ours. The CBOM tells us which is which — and it's the part that takes years. |
| *"There's no budget."* | The ask is an **inventory + one vendor question**, not a migration program — analyst time, not capex. Doing nothing is what creates the future *emergency* budget. |
| *"No regulator requires it yet."* | Auditor and partner-bank questionnaires arrive *before* mandates. "Here's our CBOM" beats "we haven't looked," and RBI/SEBI are already discussing it. |
| *"Isn't PQC unproven / didn't one get broken?"* | Yes — **SIKE** fell in 2022, which is *exactly why we deploy hybrid*: classical + PQC together, safe unless **both** break. The standardized lattice/hash schemes survived 8 years of open global attack. |
| *"Why not quantum key distribution (QKD)?"* | QKD needs special hardware and dedicated fibre and doesn't scale. **NIST and the NSA both recommend PQC over QKD** for general use — it's software on the machines we already own. |

---

## What you learned

- **PQC = new algorithms on today's computers** that resist Shor — **not** quantum hardware (that's QKD, which NIST/NSA de-prioritize).
- The fix **swaps the hard problem**: from factoring/discrete-logs (Shor-breakable) to **lattices/hashes/codes** (not). **Lattice-based** schemes are the main winners.
- **Know the standards cold:** **ML-KEM** (FIPS 203, key exchange, HNDL-urgent), **ML-DSA** (FIPS 204, signatures), **SLH-DSA** (FIPS 205, conservative hash-based), plus Falcon/HQC as hedges.
- **Hybrid** (e.g. **X25519MLKEM768**) is the deployment reality and is **already on by default** across major browsers — safe unless *both* halves break.
- **Crypto-agility + a CBOM** are the durable deliverables. Sequence: **key exchange → long-lived keys → signatures → short-lived tokens.**
- A fintech benefits by **protecting long-retention data, beating the regulatory clock, and winning partner trust** — and the migration takes ~a decade, so **the inventory starts now.**

**Next:** take [`01-pqc-readiness-pitch.md`](01-pqc-readiness-pitch.md) into the room — it's this material compressed into a boardroom ask ("inventory + one vendor question," HNDL + Mosca, the live Cloudflare demo). Optionally, build the hands-on capture: a hybrid `X25519MLKEM768` TLS handshake vs. a classical one in Wireshark (`04-cryptography/labs/01-pqc-tls-handshake/`, to be created).
