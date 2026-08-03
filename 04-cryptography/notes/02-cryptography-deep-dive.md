# 🔐 Note 02 — Cryptography, from the simplest meaning to the algorithms we run today

> **TL;DR:** Cryptography is the science of writing so that only the intended reader can understand — and, in its modern form, of *proving* four things with math: **confidentiality** (only you can read it), **integrity** (it wasn't changed), **authenticity** (it's really from who it claims), and **non-repudiation** (they can't later deny sending it). This note builds the whole subject from the word up: what the terms mean, the three big families (encoding/hashing/encryption), the algorithms in production **right now** (AES-GCM, SHA-256, RSA, ECC/ECDHE, HMAC, ECDSA/EdDSA, Argon2), and the two **hard math problems** — factoring and discrete logs — that everything asymmetric rests on. That last point is the hinge: it's *exactly* what a quantum computer threatens (see Note 03), which is why this deep dive is the prerequisite for the quantum conversation.

**Who this is for:** Farhaan, prepping for a VP meeting on quantum + cryptography. Read this first, then Note 03 (quantum), then Note 04 (post-quantum). By the end you should be able to answer "what *is* cryptography, really?" from first principles and not get caught flat-footed on any primitive.

---

## 0. The word itself (so you're never caught out)

**Cryptography** = Greek *kryptós* ("hidden") + *graphein* ("to write") → **"hidden writing."** That's the literal root. But the field splits into words people mix up constantly. Get these straight first — a VP loves catching sloppy vocabulary.

| Word | Root meaning | What it actually is |
|---|---|---|
| **Cryptography** | "hidden writing" | The science of *building* secure communication — designing the schemes. |
| **Cryptanalysis** | "loosening the hidden" | The science of *breaking* them — attacking schemes to find weaknesses. |
| **Cryptology** | "study of the hidden" | The umbrella covering **both** cryptography and cryptanalysis. |
| **Cipher** | (from Arabic *ṣifr*, "zero") | A specific algorithm that transforms readable text into scrambled text. |
| **Encryption** | "to put into cipher" | The *act* of running a cipher to scramble data. |
| **Steganography** | "covered writing" | Hiding the **existence** of a message, not its content (see §11). |

**The one-liner for the room:** *"Cryptography builds the locks, cryptanalysis picks them, and cryptology is the whole discipline. Encryption is just the act of locking."*

---

## 1. Why cryptography must exist (first principles)

Forget algorithms for a second. Start from a constraint and watch the whole field fall out of it.

**The constraint:** you want to send a message across a channel you don't control — the internet, a phone line, a courier — and you must assume a hostile party can **see, copy, change, or impersonate** anything on that channel. (In security we name this attacker: the classic figures are **Alice** and **Bob** talking, **Eve** eavesdropping, **Mallory** actively tampering.)

From that single constraint, four distinct needs appear. These are the goals *all* of cryptography serves — memorize them, because every primitive maps to one or more:

| Goal | The question it answers | Plain-words meaning |
|---|---|---|
| **Confidentiality** | "Can Eve read it?" | Only the intended recipient can understand the content. |
| **Integrity** | "Did Mallory change it?" | Any tampering is detectable. |
| **Authenticity** | "Is it really from Alice?" | You can verify who sent it. |
| **Non-repudiation** | "Can Alice deny she sent it?" | The sender can't credibly disown it later. |

> **Why you care (IAM/FinCo):** these four map directly to your day job. A **JWT** needs integrity + authenticity (nobody forged the token). **TLS** on a login endpoint needs confidentiality (nobody reads the password) + authenticity (you're really talking to the bank, not a phishing proxy). A **signed transaction** needs non-repudiation (the customer can't deny they authorized the payment — that's the whole legal basis of digital signatures under IT law).

**The "Kerckhoffs" principle** — the load-bearing idea of the whole field: *a system must be secure even if everything about it except the key is public.* Security lives in the **key**, never in keeping the algorithm secret. This is why we trust AES (published, analyzed by the whole world for 25 years) and distrust any vendor selling "proprietary military-grade encryption." **If the secret is the algorithm, it's not cryptography — it's obscurity, and it always breaks.**

---

## 2. A 3-minute history (so the arc makes sense)

Cryptography evolved in three leaps. Knowing the arc makes today's algorithms feel inevitable rather than arbitrary.

1. **Classical / by-hand (until ~1900s).** Substitution and transposition ciphers.
   - **Caesar cipher** — shift every letter by 3 (`A→D`). Trivially broken by trying 25 shifts.
   - **Vigenère** — shift by a repeating keyword. Held for 300 years ("le chiffre indéchiffrable"), broken by frequency analysis once you find the key length.
   - **The lesson:** any scheme with a small key space or leftover statistical structure falls to **frequency analysis** (the letter 'e' is ~13% of English; the cipher can't hide that).

2. **Mechanical / electromechanical (WWI–WWII).**
   - **Enigma** — the German rotor machine. Broken at Bletchley Park (Turing et al.) by exploiting operator mistakes and known-plaintext. **This is the birth of modern cryptanalysis and, arguably, computing itself.**

3. **Mathematical / modern (1970s → today).** Two revolutions, both public:
   - **1976 — Diffie–Hellman** publish public-key cryptography: a way for two strangers to agree on a secret over a public wire *without meeting first.* Before this, you always needed to share a key in advance — the "key distribution problem."
   - **1977 — RSA** gives the first practical public-key encryption/signature scheme.
   - **2001 — AES** standardized after an open, worldwide competition. Still unbroken.

> **The through-line:** cryptography got *stronger* by getting *more open.* Every leap replaced "trust the secrecy of the design" with "trust well-studied math + a secret key." Hold that thought — it's exactly how the post-quantum transition is being run today (open NIST competition, public scrutiny).

---

## 3. The confusion-killer: encoding vs. hashing vs. encryption

This is the single most common muddle, and your README already flags it. Here it is with the mental test that settles every case.

| | Reversible? | Needs a key? | Purpose | Example |
|---|---|---|---|---|
| **Encoding** | Yes, by anyone | No | Formatting / transport | Base64, hex, URL-encoding |
| **Hashing** | **No** (one-way) | No (or a MAC key) | Integrity, fingerprinting, password storage | SHA-256, Argon2 |
| **Encryption** | Yes, **with the key** | **Yes** | Confidentiality | AES-GCM, RSA |

**The test that never fails:** *"Do I need the original back, and who's allowed to get it?"*
- **Anyone** can reverse it → **encoding.** (It's not security. "We Base64'd the password" is a bug, full stop.)
- **Only a key-holder** can reverse it → **encryption.**
- **No one** can reverse it → **hashing.**

**See it yourself (30 seconds, PowerShell + Python):**
```powershell
# Encoding — reversible by anyone, no key:
python -c "import base64; print(base64.b64encode(b'hello').decode())"   # aGVsbG8=
python -c "import base64; print(base64.b64decode('aGVsbG8=').decode())" # hello  <- trivially reversed

# Hashing — one-way, no key gets you back to 'hello':
python -c "import hashlib; print(hashlib.sha256(b'hello').hexdigest())"
# 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824  <- no inverse exists
```
✅ **Checkpoint:** you just decoded Base64 with zero key material, and you have no command that turns the SHA-256 digest back into `hello`. That *is* the difference, proven.

---

## 4. Symmetric cryptography — one shared key (the workhorse)

**Plain words:** one secret key both **locks and unlocks.** Fast, used for the actual bulk data. Like a physical key that both locks and opens the same door — everyone who needs in must have a copy.

**The star: AES (Advanced Encryption Standard, FIPS 197).**
- Key sizes **128 / 192 / 256 bits**; block size **128 bits**. Unbroken since 2001.
- A **block cipher**: it only encrypts one 128-bit block at a time. To encrypt a whole file you need a **mode of operation** deciding how blocks chain.

**Modes — this is where symmetric crypto lives or dies:**

| Mode | What it does | Verdict |
|---|---|---|
| **ECB** | Each block encrypted independently | ☠️ **Never.** Identical plaintext → identical ciphertext. The "ECB penguin" image is still recognizable after encryption. |
| **CBC** | Chains each block with the previous + an **IV** | Legacy. Needs padding; padding-oracle attacks if errors leak. |
| **CTR** | Turns the block cipher into a stream cipher | Good, parallelizable — but no built-in integrity. |
| **GCM** | CTR **+ authentication tag** = AEAD | ✅ **The modern default.** |

**The one idea to internalize: encryption without authentication is a trap.** Plain AES-CBC hides the data but a `Mallory` can flip bits and you won't know. **AEAD** (Authenticated Encryption with Associated Data — AES-**GCM** or **ChaCha20-Poly1305**) gives confidentiality *and* integrity in one pass, with a tamper-detecting **tag**. Always reach for AEAD.

> **Why you care (FinCo):** PCI-DSS requires "strong cryptography" for cardholder data at rest and in transit. In practice that means AES-256-GCM in your databases/KMS and ChaCha20-Poly1305 or AES-GCM in TLS. If you ever see AES-ECB or unauthenticated AES-CBC in a design review, that's a finding.

**The catch symmetric crypto can't solve alone:** how do Alice and Bob get the *same* secret key in the first place, if the channel is hostile? You can't send the key over the wire — Eve reads it. This is the **key distribution problem**, and it's what asymmetric crypto was invented to solve.

---

## 5. Asymmetric cryptography — two keys (the magic trick)

**Plain words:** two mathematically linked keys — a **public** key you hand out freely, and a **private** key you guard. **What one locks, only the other unlocks.**

Two things this buys you:
- **Encrypt to someone:** anyone encrypts with your *public* key; only your *private* key decrypts. (Solves key distribution — no prior secret needed.)
- **Sign as yourself:** you sign with your *private* key; anyone verifies with your *public* key. (Proves it came from you — see §7.)

**Analogy:** a public key is an **open padlock** you mail to the world. Anyone can snap a message shut in a box with it. Only you hold the key that opens the padlock. You never had to share the opening key.

### The two families in production today

**RSA** — security rests on the hardness of **factoring** a huge number back into its two prime factors. Easy to multiply two 2048-bit primes; astronomically hard to reverse. Needs big keys (2048/3072-bit+). Used for encryption and signatures.

**ECC (Elliptic-Curve Cryptography)** — security rests on the **elliptic-curve discrete logarithm problem**. Same strength as RSA at *far* smaller keys: **256-bit ECC ≈ 3072-bit RSA.** Efficient — it dominates TLS, mobile, passkeys. Curves you'll meet: **P-256** (NIST), **Curve25519** (used by X25519 key exchange and Ed25519 signatures).

### Key exchange — the Diffie–Hellman trick

Two parties derive the **same shared secret** over a public wire, and Eve — who saw every byte — still can't compute it.

**The paint analogy (this is the one that lands in meetings):**
1. Alice and Bob publicly agree on a shared yellow paint.
2. Each secretly mixes in a private color. Alice → orange, Bob → green.
3. They swap the *mixtures* over the public channel. Eve sees orange and green.
4. Each adds their private color again: Alice adds her secret to Bob's green, Bob adds his to Alice's orange. Both land on the **same brown**.
5. Eve can't un-mix paint to recover a private color — separating mixed paint is the "hard problem." She's stuck.

The digital version is **ECDHE** (Ephemeral Elliptic-Curve Diffie–Hellman). "Ephemeral" = a fresh key per session, which gives **forward secrecy**: even if the server's long-term key leaks *next year*, last week's recorded sessions stay unreadable, because that session's ephemeral secret is long gone.

> **⚠️ The quantum hinge — read this twice.** RSA rests on **factoring**; DH/ECC rest on **discrete logarithms**. Both are "hard" only for *classical* computers. **Shor's algorithm on a large quantum computer solves both efficiently** — which is why the *entire* asymmetric column above is what post-quantum crypto is racing to replace. Symmetric crypto (AES) and hashes are only *weakened*, not broken. This single distinction is the heart of the quantum-cryptography story (Notes 03 and 04).

### Hybrid encryption — how it's actually used

Nobody encrypts a 2 GB file with RSA (asymmetric is slow). The real pattern, and what **TLS does every time you load a page**:
1. Use **asymmetric** crypto (ECDHE) *once* to agree on a random symmetric key.
2. Switch to fast **symmetric** crypto (AES-GCM) for all the actual data.

Asymmetric solves "agree on a key with a stranger"; symmetric does the heavy lifting. Best of both.

---

## 6. Hash functions — the digital fingerprint

**Plain words:** a hash takes *any* input — a word or a 4 GB movie — and produces a **fixed-size fingerprint** (a "digest"). Same input always → same digest. Change one bit of input → the digest changes completely (the **avalanche effect**).

**Three properties a cryptographic hash must have:**
| Property | Means | Why it matters |
|---|---|---|
| **Pre-image resistance** | Given a digest, you can't find *an* input that makes it | You can't reverse a password hash |
| **Second pre-image resistance** | Given an input, you can't find a *different* one with the same digest | Nobody swaps a file for a forgery with a matching hash |
| **Collision resistance** | You can't find *any* two inputs with the same digest | Signatures and certificates stay unforgeable |

**In production:** **SHA-256** (and SHA-384/512) — the **SHA-2** family (FIPS 180). **SHA-3** (Keccak, FIPS 202) is a structurally different backup design.
**Dead — never use for security:** **MD5** and **SHA-1**. Practical collisions exist (Google's *SHAttered* produced two different PDFs with the same SHA-1 in 2017).

**The birthday bound (know this number):** an *n*-bit hash gives only **~n/2 bits** of collision resistance, because of the birthday paradox. SHA-256 → ~128-bit collision security. (This same "square-root" idea is exactly how **Grover's** quantum algorithm attacks symmetric crypto — see Note 03. The concepts rhyme.)

**Where you meet hashes daily:** file integrity checks, Git commit IDs, HMAC/JWT signing, certificate fingerprints, and password storage (with a *special* kind of hash — next).

---

## 7. The integrity/authenticity toolkit: MACs, HMAC, and signatures

Hashing alone proves integrity only if the attacker can't *also* recompute the hash. To bind integrity to a *secret* or an *identity*, you need one of two tools. **This MAC-vs-signature distinction is a classic interview and VP question — nail it.**

### MAC / HMAC — shared-secret integrity
- A **MAC (Message Authentication Code)** proves a message is authentic **and** untampered, using a **shared secret** both sides hold. Answers: *"did this come from someone with the key, and is it unchanged?"*
- **HMAC** is the standard, well-studied construction: `HMAC-SHA256(key, message)`. It safely wraps a hash with a key (resisting length-extension attacks that break naive `hash(key || message)`).
- **You'll meet it constantly:** **HS256 JWTs**, API request signing, webhook signature verification (Stripe, GitHub webhooks).
- **Limitation:** because the secret is *shared*, both sides can produce a valid MAC → you get integrity + authenticity but **no non-repudiation.** Either party could have made it; you can't prove *which*.

### Digital signatures — private-key identity + non-repudiation
- **Sign with your private key; anyone verifies with your public key.** Only you could have produced it, so you **can't deny it** → non-repudiation.
- **Mechanically:** you hash the message, then sign the *hash*. (This is why hash collision resistance protects signatures — a collision would let an attacker move a signature onto a different document.)
- **Algorithms in production:** **RSA-PSS**, **ECDSA** (P-256), **EdDSA / Ed25519** (fast, misuse-resistant — the modern favorite).
- **IAM relevance:** **RS256/ES256 JWTs**, signed **SAML** assertions, code signing, and **every X.509 certificate** (a cert *is* a signed statement — §9).

**One-line contrast for the meeting:** *"A MAC uses a shared secret — great for two systems that trust each other. A signature uses a private key — it proves identity to the whole world and is legally non-repudiable. JWTs can use either: HS256 is a MAC, RS256/ES256 is a signature."*

---

## 8. Password hashing — a *deliberately slow* special case

**The trap:** passwords look like a hashing job, so people reach for SHA-256. **Wrong** — SHA-256 is *too fast.* A GPU tries **billions** of SHA-256 guesses per second, so a stolen password-hash database is cracked in hours.

**The fix — functions built to be slow and memory-hungry:**
- **bcrypt** — battle-tested, tunable "work factor."
- **scrypt** — memory-hard, resists GPU/ASIC cracking.
- **Argon2 (Argon2id)** — winner of the 2015 Password Hashing Competition; tunable time, memory, and parallelism. **Prefer this for new systems.**

**Two seasonings:**
- **Salt** — a unique random value per password, stored *alongside* the hash. Non-secret. Defeats precomputed **rainbow tables** and stops two users with the same password from having the same hash.
- **Pepper** — a *secret* value applied to all passwords, stored *separately* (app config / HSM, never the DB). Survives a database-only breach.

> **Why you care (FinCo):** if any FinCo system stores credentials, Argon2id + per-user salt is non-negotiable, and you're the person who'll be asked "why not just SHA-256?" — now you can answer: *because fast is exactly the wrong property for a password.*

---

## 9. PKI — how the world agrees on who owns which public key

Public-key crypto has a gap: if I hand you a public key claiming to be your bank, **how do you know it's really the bank's and not Mallory's?** You need a trusted third party to vouch. That's **PKI (Public Key Infrastructure).**

- A **certificate** binds an **identity** (a domain, service, or person) to a **public key**, and is **signed by a Certificate Authority (CA)** vouching for that binding. **X.509** is the format (subject, issuer, validity dates, public key, extensions, signature).
- **Chain of trust:** a *leaf* cert (yourbank.com) is signed by an *intermediate* CA, signed by a *root* CA. Your OS/browser ships a **trust store** of root CAs it trusts. Verification walks the chain up to a trusted root — like a passport checked against an embassy checked against a government.
- **Supporting cast:** **CSR** (certificate signing request — how you ask for a cert), **revocation** via **CRL/OCSP** (how you kill a cert before it expires), and **certificate pinning** (hard-coding which cert you'll accept).

> **Why you care (FinCo):** PKI is the backbone of TLS *and* of **mutual TLS (mTLS)** and certificate-based auth — how service accounts, machine identities, and partner-bank integrations prove themselves without passwords. When an SSO outage traces back to "an expired intermediate cert," this is the machinery that broke.

---

## 10. The TLS 1.3 handshake — where *everything* comes together

Every login, API call, and service hop rides **TLS**. The handshake is the one place all the primitives above cooperate — walking it proves you understand the whole stack.

1. **ClientHello** — your browser offers supported TLS versions, cipher suites, and its **ephemeral ECDHE public key-share**.
2. **ServerHello** — the server picks the parameters, sends *its* key-share, and its **certificate** (proving identity via the PKI chain in §9).
3. **Key derivation** — both sides combine their ECDHE shares (Diffie–Hellman, §5) into the *same* shared secret, then derive **AES-GCM session keys** from it.
4. **Finished** — both sides exchange a hash-based check that the handshake wasn't tampered with; from here, application data flows encrypted with **AES-GCM** or **ChaCha20-Poly1305**.

**What to appreciate:** TLS 1.3 is **1-RTT** (one round trip — fast), stripped out every legacy/weak option, and **mandates forward secrecy** via ephemeral key exchange. It's **hybrid encryption in action**: asymmetric (cert + ECDHE) bootstraps symmetric (AES-GCM), with signatures and hashing guarding integrity throughout.

**See it yourself (1 min):**
```bash
# Watch a real handshake negotiate its cipher suite and TLS version:
openssl s_client -connect www.cloudflare.com:443 -tls1_3 </dev/null 2>/dev/null | grep -E "Protocol|Cipher"
# Expect something like:  Protocol : TLSv1.3   Cipher : TLS_AES_256_GCM_SHA384
```
✅ **Checkpoint:** you just saw asymmetric-bootstrapped, symmetric-bulk, AEAD-protected crypto negotiated live. That one line contains §§4–9.

---

## 11. Steganography — hiding the *existence*, not the content

You asked specifically about this, and it's a great VP curveball because people conflate it with encryption.

- **Cryptography** hides the **content** of a message. Eve *sees* that a secret exists — she just can't read it.
- **Steganography** (*"covered writing"*) hides the **existence** of the message entirely. Eve sees an ordinary cat photo and never suspects there's anything inside.

**How it works (classic example — LSB):** an image pixel's color is a number like `10110110`. The **least significant bit** (the last one) barely affects the color — flip it and the pixel looks identical to the eye. So you overwrite those last bits across thousands of pixels to smuggle a hidden message. The picture looks normal; the data rides invisibly inside.

**Old-school versions:** invisible ink, microdots, hiding a message in the first letter of each sentence (an *acrostic*).

**The key insight — they're complementary, not rivals:**
> **Encryption makes a message unreadable; steganography makes it unnoticed. Do both, and Eve neither finds the message *nor* could read it if she did.**

> **Why you care (blue team / FinCo):** attackers use steganography for **data exfiltration** — smuggling stolen card data out inside innocent-looking images or DNS traffic to dodge DLP tools, and for **C2** (hiding malware commands in memes on social media). Detection is **steganalysis** — statistical anomaly-hunting in files. Knowing the technique is how you'd catch it, so this pairs an "attack" with its "defense" per house rules.

---

## 12. Randomness — the silent foundation everything sits on

Nearly every operation above needs **unpredictable** numbers: keys, IVs, nonces, salts, session tokens. Weak randomness quietly voids *all* the math.

- Use a **CSPRNG** (Cryptographically Secure Pseudo-Random Number Generator): `/dev/urandom`, Python's `secrets`, Node's `crypto.randomBytes`. **Never** `random`/`Math.random` for anything security-relevant — they're predictable.
- **Famous failures:** the **2008 Debian OpenSSL bug** crippled entropy so keys became guessable (a tiny key space, brute-forceable); **nonce reuse** has broken real ECDSA implementations (the PS3 was jailbroken because Sony reused a signing nonce, leaking the private key).

**The lesson underneath §§4–12:** crypto almost never fails at the *core algorithm* (AES and SHA-256 are rock-solid). It fails at the **joints** — modes, padding, randomness, nonce reuse, key handling, and *rolling your own.* Use vetted libraries and standard constructions; the "cardinal sin" is inventing your own scheme.

---

## 13. The cheat sheet — algorithms we actually run today

Print this. It's the "what do we use for X" table a VP might quiz you on.

| Job | Use today | Avoid / legacy | Quantum status (→ Notes 03/04) |
|---|---|---|---|
| **Bulk encryption (symmetric)** | AES-256-GCM, ChaCha20-Poly1305 | AES-ECB, unauthenticated CBC, DES/3DES, RC4 | ✅ Only *weakened* (Grover) — bump to 256-bit |
| **Key exchange** | ECDHE (X25519, P-256) | Static RSA key transport, plain DH | ☠️ **Broken by Shor** — migrate first (HNDL) |
| **Public-key encryption** | RSA-OAEP 3072+, ECIES | RSA-PKCS#1v1.5, RSA-1024 | ☠️ **Broken by Shor** |
| **Digital signatures** | Ed25519, ECDSA P-256, RSA-PSS 3072+ | RSA-1024, DSA, MD5/SHA-1 signing | ☠️ **Broken by Shor** |
| **Hashing** | SHA-256, SHA-384, SHA-3 | MD5, SHA-1 | ✅ Only *weakened* (Grover) — prefer 384+ |
| **Message auth (MAC)** | HMAC-SHA-256, KMAC | Naive `hash(key‖msg)` | ✅ Weakened only |
| **Password storage** | Argon2id, scrypt, bcrypt | SHA-256/MD5 on passwords, no salt | ✅ Not directly threatened |
| **Randomness** | OS CSPRNG (`secrets`, `/dev/urandom`) | `Math.random`, `rand()` | — |

**The pattern to say out loud:** *"Everything in the ☠️ rows is asymmetric and rests on factoring or discrete logs — the exact problems Shor's algorithm breaks. Everything in the ✅ rows is symmetric or hash-based and only needs bigger sizes. That's the entire shape of the quantum threat in one table."*

---

## What you learned

- **Cryptography** is "hidden writing," and the field is really four guarantees: **confidentiality, integrity, authenticity, non-repudiation.** Every primitive maps to one or more.
- **Kerckhoffs' principle:** security lives in the *key*, never in a secret algorithm.
- The three families: **encoding** (reversible by anyone — *not* security), **hashing** (one-way), **encryption** (reversible with a key). Symmetric (**AES-GCM**) is fast bulk crypto; asymmetric (**RSA, ECC/ECDHE**) solves agreeing on keys with strangers; **hybrid** (TLS) uses both.
- **MAC vs signature:** shared-secret integrity vs private-key, non-repudiable identity.
- **Steganography** hides a message's *existence*; encryption hides its *content* — complementary tools.
- The **hinge for the whole quantum talk:** asymmetric crypto rests on **factoring** and **discrete logs**, which **Shor's algorithm breaks**; symmetric/hash crypto is only *weakened* by **Grover's** and survives with bigger sizes.

**Next:** [`03-quantum-computing-the-quantum-realm.md`](03-quantum-computing-the-quantum-realm.md) — what "quantum" means in physics vs. computing vs. cryptography, qubits, and why Shor's algorithm turns the ☠️ rows above into a countdown. Then [`04-post-quantum-cryptography.md`](04-post-quantum-cryptography.md) for the fix, and [`01-pqc-readiness-pitch.md`](01-pqc-readiness-pitch.md) for how to pitch it to *your* VP.
