# ⚛️ Note 03 — Quantum computing and the quantum realm (what "quantum" *actually* means)

> **TL;DR:** "Quantum" is one word wearing three hats. In **physics** it means the *smallest indivisible packet* of a physical quantity (a photon is one quantum of light). In **computing** it means a machine that stores information in **qubits** — which can be a blend of 0 *and* 1 at once (**superposition**), be spookily linked (**entanglement**), and cancel wrong answers out (**interference**). In **cryptography** "quantum" shows up two ways: as a *threat* (**Shor's algorithm** breaks RSA/ECC), and as a *defense* (**QKD** — using physics to detect eavesdroppers). A quantum computer is **not** "a faster PC." It's a fundamentally different machine that's exponentially better at a *narrow* set of problems — and factoring, the thing RSA relies on, is one of them. No machine can break real crypto **today**, but the whole industry is racing toward the one that will.

**Prereq:** read [`02-cryptography-deep-dive.md`](02-cryptography-deep-dive.md) first — especially §5's "quantum hinge." This note explains *why* that hinge exists. Then Note 04 for the defense.

---

## 1. The question your VP will open with: "So what *is* quantum?"

Here's your answer, structured so you never get caught: **the word means different things in different rooms.** Lead with that — it shows you actually understand it rather than parroting a headline.

| Context | What "quantum" means | One-line proof/example |
|---|---|---|
| **Physics** | The **smallest indivisible unit** of a physical quantity | Light comes in discrete packets — **photons**. You can't have half a photon. |
| **Computing** | Information stored in **qubits** that exploit quantum physics | A qubit can be 0 *and* 1 at once until measured. |
| **Cryptography** | Both a **threat** (Shor breaks RSA/ECC) and a **defense** (QKD detects eavesdroppers) | A large quantum computer factors the numbers RSA depends on. |

**The clean opener:** *"'Quantum' isn't a marketing word — it's a physics word. It means the smallest possible chunk of something. In physics, energy and light come in these indivisible chunks called quanta. Quantum computing borrows the weird rules those tiny chunks obey and uses them to compute. And in cryptography it cuts both ways — it can break our current encryption, and it can also, in a different form, help protect it."*

---

## 2. "Quantum" in physics — the smallest possible chunk

**The origin (1900, Max Planck).** Physicists hit a wall: their equations predicted a hot object should radiate *infinite* energy at high frequencies (the "ultraviolet catastrophe"). Planck fixed it with a radical assumption — energy isn't emitted in a smooth continuous stream but in tiny **discrete packets.** He called the packet a **quantum** (Latin for "how much"). The size of the packet is `E = h·f` (energy = Planck's constant × frequency).

**Plain words:** nature, at its smallest scale, is **pixelated, not smooth.** Just as a digital photo isn't infinitely zoomable — eventually you hit individual pixels — energy, light, and matter come in smallest-possible units you can't subdivide.

- **A photon** is one quantum of light. **An electron** is a quantum of the electron field. You can have 1 photon or 2, never 1.5.

**Why the tiny world is *weird* — three rules that don't exist in daily life.** These are the rules quantum computing exploits, so meet them here first:

1. **Superposition** — a quantum object can be in **multiple states at once** until measured. Not "we don't know which" — it is *genuinely both*, and the two possibilities can add or cancel (see interference).
2. **Measurement collapses it** — the instant you *look*, the blend snaps to a single definite value. You can never see the superposition directly; you only ever read out one answer.
3. **Entanglement** — two particles can be linked so that measuring one *instantly* tells you about the other, no matter how far apart. Einstein hated it — "spooky action at a distance."

> **The mind-bender to internalize (Schrödinger's cat):** the famous cat is *both alive and dead* until you open the box. It's a deliberately absurd illustration of superposition — a quantum particle really *is* in a blend of states until measured. The cat is a teaching prop; the qubit is the real thing.

**⚠️ Common misconception to correct in the room:** entanglement does **not** let you send information faster than light. You can't *control* what the collapse gives you, so there's no way to encode a message in it. (This matters — a sharp VP might test whether you've swallowed the sci-fi version.)

---

## 3. "Quantum" in computing — from bits to qubits

### The classical bit
A normal computer stores everything as **bits** — each is definitively **0 or 1**. A switch that's off or on. Eight bits = one byte = one of 256 possible values, but **exactly one** at a time. Every laptop, phone, and server on earth works this way.

### The qubit
A **qubit** (quantum bit) is the quantum version. Thanks to **superposition**, before you measure it, a qubit is a *weighted blend* of 0 and 1 at the same time:

> state = α·|0⟩ + β·|1⟩,  where |α|² + |β|² = 1

Don't panic at the notation. **Plain words:** α and β are "amplitudes" — they say how much the qubit leans toward 0 vs. 1. `|α|²` is the probability you'll read a 0; `|β|²` the probability of a 1. The `|0⟩` / `|1⟩` brackets ("ket" notation) just mean "the 0 state" / "the 1 state."

**The coin analogy that lands:**
- A **bit** is a coin lying flat — heads or tails, decided.
- A **qubit** is a coin **spinning** — not heads, not tails, but a genuine blend of both. Slap your hand on it (**measure**) and it collapses to one face. And you can *bias* the spin so it's, say, 70% likely to land heads.

### Why this is exponentially powerful — the key number
Here's the fact that makes quantum computing matter, and the one to say slowly:

- **n classical bits** represent **one** of 2ⁿ values at a time.
- **n qubits** in superposition hold a blend across **all 2ⁿ combinations at once.**

| Qubits | Combinations held simultaneously |
|---|---|
| 10 | 1,024 |
| 20 | ~1 million |
| 50 | ~1 quadrillion (10¹⁵) |
| 300 | more than the number of atoms in the observable universe |

**300 qubits can hold more values at once than there are atoms in the universe.** That's not a typo — it's why the field exists.

### The catch that stops it being magic — and where the real genius is
You **can't just read all 2ⁿ answers out.** Measurement collapses the whole blend to **one** value, at random per the probabilities. So a quantum computer isn't a machine that "tries every answer and shows you all of them."

The actual trick is **interference**: quantum amplitudes can be positive or negative (like waves), so you design the computation to make the amplitudes of the **wrong** answers **cancel out** and the amplitudes of the **right** answer **reinforce**. When you finally measure, the correct answer is overwhelmingly likely to pop out.

> **The analogy for the room:** think noise-cancelling headphones. They add a sound wave precisely shaped to cancel the noise. A quantum algorithm adds "computation waves" shaped to cancel the wrong answers, leaving the right one loud. **Superposition explores; interference selects; entanglement wires it all together.** That trio *is* quantum computing.

**This is why quantum computers are specialists, not fast PCs:** you only get a speed-up on problems where you can cleverly arrange that cancellation. For most everyday computing (email, spreadsheets, web servers) a quantum computer is *useless* — no interference pattern to exploit. It shines on a *narrow* set of structured problems. Unfortunately for us, **factoring integers is one of them.**

---

## 4. The two algorithms that matter for cryptography

Only a handful of quantum algorithms have real-world bite. Two of them are the *entire* reason your VP called this meeting.

### Shor's algorithm (1994) — the crypto-breaker ☠️
- **What it does:** factors huge numbers and solves discrete logarithms in **polynomial time** — i.e., *efficiently* — on a large enough quantum computer.
- **Why it's a bombshell:** recall from Note 02 that **RSA** rests on factoring being hard, and **Diffie–Hellman / ECC** rest on discrete logs being hard. Shor makes **both** easy. So a large quantum computer **breaks essentially all public-key crypto in use today** — RSA, ECDH, ECDSA, EdDSA, the lot.
- **The scale of the speed-up:** factoring a 2048-bit RSA key would take classical computers **longer than the age of the universe.** Shor's algorithm, on hardware that doesn't yet exist, would do it in **hours.** That's not incremental — it's a different universe of difficulty.

### Grover's algorithm (1996) — the crypto-weakener ✅
- **What it does:** searches an unsorted space of *N* items in **√N** steps instead of *N* — a **quadratic** (square-root) speed-up.
- **Effect on crypto:** it effectively **halves** the security of symmetric keys and hashes. Brute-forcing AES-128 drops from 2¹²⁸ to ~2⁶⁴ work; AES-256 drops to ~2¹²⁸.
- **Why it's survivable:** just **double the size.** AES-256 stays safe (~2¹²⁸ is still astronomically out of reach); SHA-384 stays safe. No new math needed — that's why symmetric crypto is in the "✅ weakened, not broken" column.

> **The two-sentence summary that impresses:** *"Shor's algorithm breaks the asymmetric crypto that protects key exchange and signatures — that needs entirely new algorithms. Grover's algorithm merely weakens symmetric crypto and hashes — we fix that by doubling key sizes. So the quantum threat isn't 'all crypto dies'; it's 'public-key crypto dies, symmetric crypto gets a haircut.'"* (This is the exact shape of the cheat-sheet table in Note 02 §13.)

---

## 5. How you actually *build* a qubit (and why it's brutally hard)

To sound credible, know that "qubit" is a *role*, not a specific object — several physical systems can play it, each a different bet by a different company.

| Technology | The qubit is… | Who's betting on it | Trade-off |
|---|---|---|---|
| **Superconducting** | A tiny loop of supercooled circuit (near absolute zero, −273 °C) | **IBM, Google** | Fast; but noisy and needs giant dilution fridges |
| **Trapped ions** | Single charged atoms held in electromagnetic fields, poked by lasers | **IonQ, Quantinuum** | Very accurate, long-lived; but slower operations |
| **Neutral atoms** | Neutral atoms held by "optical tweezers" (laser light) | **QuEra, Atom Computing** | Scales to many qubits; newer |
| **Photonic** | Individual photons of light | **PsiQuantum, Xanadu** | Room-temperature, networkable; hard to make photons interact |
| **Topological** | Exotic "Majorana" quasiparticles | **Microsoft** | Would be inherently error-resistant — *if* it can be built |

### The enemy: decoherence
**The core difficulty:** superposition is *fragile*. The tiniest disturbance — a stray photon, a vibration, a whisper of heat — makes the qubit "decohere," collapsing its delicate blend and destroying the computation. This is why superconducting qubits live in fridges **colder than deep space** and stay coherent for only **microseconds**. Quantum computing is, at bottom, a war against noise.

### Physical vs. logical qubits — the number that cuts through the hype
This is the single most important nuance for a VP conversation, because it's where headline qubit-counts mislead.

- Because qubits are so error-prone, you can't compute reliably on raw ("**physical**") qubits.
- You bundle **many physical qubits into one error-corrected "logical" qubit** using **quantum error correction** (e.g., surface codes). Estimates run **~1,000 physical qubits per 1 logical qubit** with today's error rates.
- **So when a press release trumpets "1,000 qubits," that may be ~1 logical qubit's worth of usable computing** — nowhere near breaking RSA, which needs **thousands of logical** (hence *millions* of physical) qubits.

> **The line that makes you sound like the expert in the room:** *"Don't read raw qubit counts as progress toward breaking crypto. The metric that matters is *logical*, error-corrected qubits — and we're still in the single digits there. Breaking RSA-2048 needs thousands of logical qubits, which is millions of physical ones. The 2024 milestone wasn't a qubit count — it was Google's Willow chip showing that adding more physical qubits could make errors go *down* instead of up. That 'below threshold' result is the real starting gun, because it means error correction finally works in the right direction."*

---

## 6. Where the world actually is, right now (2026)

Be concrete and current — vague "quantum is coming" talk is exactly what an uptight VP shreds.

**We are in the "NISQ" era** — **Noisy Intermediate-Scale Quantum.** Machines have tens to a few hundred noisy qubits, useful for research and experiments but **not** for breaking crypto or most commercial problems. Real, error-corrected, cryptographically-relevant machines are still years out.

**The scoreboard (know a few names and what they *mean*):**
- **Google — "Willow" (Dec 2024):** ~105 qubits, but the headline was **below-threshold error correction** — the first convincing demonstration that scaling up *reduces* the logical error rate. A genuine milestone in the *quality* race, not the qubit-count race.
- **IBM:** the qubit-count leader (the 1,121-qubit "Condor"), now pivoting to **modular, error-corrected** architectures ("Heron," and a public roadmap toward a fault-tolerant machine by ~2029).
- **Quantinuum & IonQ:** trapped-ion machines with the **highest-fidelity** (lowest-error) qubits, betting quality beats raw count.
- **QuEra / Atom Computing:** neutral-atom machines pushing into the hundreds-to-thousands of qubits.
- **PsiQuantum / Microsoft:** longer-shot bets (photonic / topological) aiming to leapfrog straight to fault tolerance.

**The metric that matters — "CRQC":** a **Cryptographically-Relevant Quantum Computer** is one big and clean enough to actually run Shor's algorithm against RSA-2048. **None exists.** Expert surveys (e.g. the Global Risk Institute's annual poll) put a meaningful chance of a CRQC in the **2030s** — commonly cited around **~2035**, with wide uncertainty in both directions.

**Estimates are shrinking — mention this, it's the scary part.** In 2019, breaking RSA-2048 was estimated at ~20 million noisy physical qubits over 8 hours. By 2025, algorithmic improvements had cut that estimate to **under 1 million qubits** — *without any new hardware.* **The target is moving toward us from the software side even while the hardware crawls forward.** That's why "decades away" is a dangerous assumption.

> **Why you care (FinCo) — the bridge to Note 04:** you don't need a CRQC to *exist* to be at risk **today**. Adversaries can **record your encrypted traffic now and decrypt it later** once a CRQC arrives — **"Harvest Now, Decrypt Later" (HNDL).** For a fintech holding data with 7–10-year secrecy requirements (KYC, PANs, transaction history), data you send over classical TLS *this quarter* may already be sitting in an adversary's archive, waiting. **That's the entire reason post-quantum migration starts before the machine is built.**

---

## 7. Quantum's *defensive* side (so you're not one-dimensional)

"Quantum" in cryptography isn't only a threat. Two defensive ideas, so you can field a curveball:

- **QKD (Quantum Key Distribution)** — uses the physics of §2 to share an encryption key such that **any eavesdropper is detectable**: measuring a quantum state disturbs it, so Eve's snooping leaves fingerprints. It's real (banks and governments have piloted it) but needs special hardware/fibre and doesn't scale like software crypto. **Don't confuse it with PQC** — QKD is a *hardware* method of key exchange; **PQC** (Note 04) is *new math that runs on today's computers.* **NIST and the NSA explicitly recommend PQC over QKD** for general use, because software you can deploy everywhere beats hardware you must lay fibre for.
- **QRNG (Quantum Random Number Generators)** — use quantum unpredictability to generate true randomness for keys (recall from Note 02 §12 how much rides on good randomness).

> **The distinction to bank:** *"Quantum computing breaks our crypto (Shor). Quantum *communication* — QKD — can help protect it, but it needs special hardware. The mainstream fix isn't quantum hardware at all — it's new classical algorithms that run on the computers we already have. That's post-quantum cryptography, and it's what we should actually be planning for."* — perfect segue to Note 04.

---

## What you learned

- **"Quantum" wears three hats:** the smallest indivisible unit (**physics**), information in **qubits** (computing), and a **threat *and* defense** (cryptography).
- **Three quantum rules power it:** **superposition** (0 and 1 at once), **entanglement** (linked particles), **interference** (cancel wrong answers, amplify right ones). *Superposition explores, interference selects, entanglement wires it together.*
- **n qubits hold 2ⁿ combinations at once** — but you can't read them all out; interference is the trick that makes one useful answer emerge. That's why a quantum computer is a **specialist, not a fast PC**.
- **Two algorithms matter:** **Shor's** *breaks* RSA/ECC (needs new math → PQC); **Grover's** merely *weakens* symmetric/hashes (fixed by doubling sizes).
- **Read *logical*, not raw, qubit counts.** We're in the noisy **NISQ** era; **no CRQC exists**; expert consensus points to the **2030s**, and the resource estimates keep *shrinking*.
- **HNDL** means the clock for a fintech is ticking **today**, not on "quantum day."

**Next:** [`04-post-quantum-cryptography.md`](04-post-quantum-cryptography.md) — the new algorithms that survive Shor (ML-KEM, ML-DSA, SLH-DSA), how they work, and exactly how a fintech like FinCo becomes **PQC-resilient**. For the boardroom pitch version, see [`01-pqc-readiness-pitch.md`](01-pqc-readiness-pitch.md).
