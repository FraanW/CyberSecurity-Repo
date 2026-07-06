# 04 · Cryptography

> Cryptography is the machinery every identity system rides on: it turns "I claim to be this user" into a mathematically verifiable fact, and turns "this data is private" into a guarantee rather than a hope. Master it and IAM stops being magic.

**Which agents to ask:** **Mimir** for concepts and theory (the *why* and the math intuition), **Lefler** to set up and troubleshoot the hands-on labs, and **Janus** for how each primitive actually shows up in IAM work at FinCo — JWT/token signing, TLS termination, certificate-based auth, and key handling.

---

## Core concepts (learn in this order)

The order matters. Each block assumes the one before it. Don't skip ahead — the confusion people carry for years usually comes from muddling the first block.

### 1. Encoding vs. hashing vs. encryption (kill the confusion first)
- **Encoding** (Base64, hex, URL-encoding) is *reversible with no key* — it's for transport/formatting, **not** security. Anyone can decode it. If you ever hear "we Base64'd the password," that's a bug.
- **Hashing** is *one-way* — you cannot recover the input. Same input always gives the same output. Used for integrity and password storage.
- **Encryption** is *reversible with a key* — designed to protect confidentiality. Without the key you can't read it.
- The single most useful mental test: *"Do I need to get the original back, and who is allowed to?"* → encoding (anyone), encryption (key holder), hashing (no one).

### 2. Symmetric cryptography (AES and modes)
- One shared secret key encrypts **and** decrypts. Fast, used for bulk data.
- **AES** (Advanced Encryption Standard, FIPS 197) — 128/192/256-bit keys, 128-bit block size. The workhorse.
- **Modes of operation** — a block cipher only encrypts one block; the mode decides how blocks chain together:
  - **ECB** — never use it. Identical plaintext blocks produce identical ciphertext blocks (the famous "ECB penguin" is still visible after encryption).
  - **CBC** — chains blocks with an IV; needs padding, vulnerable to padding-oracle attacks if error handling leaks.
  - **CTR** — turns a block cipher into a stream cipher; parallelizable.
  - **GCM** — **authenticated encryption (AEAD)**: gives you confidentiality *and* integrity/authenticity in one pass. This is the modern default. Learn why "encrypt-then-MAC" and AEAD beat encryption alone.
- Key idea to internalize: encryption without authentication is a trap. Always reach for an AEAD mode.

### 3. Asymmetric cryptography (RSA, ECC, key exchange)
- Two keys: a **public** key (shareable) and a **private** key (secret). What one locks, only the other unlocks.
- **RSA** — based on the hardness of factoring large numbers. Used for encryption and signatures; needs large keys (2048/3072-bit+).
- **ECC** (Elliptic Curve Cryptography) — same security as RSA at far smaller key sizes (a 256-bit ECC key ≈ 3072-bit RSA). Efficient; dominant in TLS and mobile. Curves: P-256, Curve25519.
- **Key exchange / Diffie-Hellman** — lets two parties derive a shared secret over a public channel without ever transmitting it. **ECDHE** (ephemeral elliptic-curve DH) is what gives modern TLS its **forward secrecy** — compromising the server's long-term key later doesn't decrypt past sessions.
- Why the split matters: asymmetric crypto solves the "how do we agree on a key without meeting first" problem. In practice you use it to *establish* a symmetric key, then switch to fast symmetric crypto for the actual data. This is **hybrid encryption** — and it's exactly what TLS does.

### 4. Hash functions (SHA-2/3, properties, collisions)
- A hash maps arbitrary input to a fixed-size digest. Three required properties:
  - **Pre-image resistance** — given a hash, you can't find an input that produces it.
  - **Second pre-image resistance** — given an input, you can't find a different input with the same hash.
  - **Collision resistance** — you can't find *any* two inputs with the same hash.
- **SHA-2** (SHA-256, SHA-384, SHA-512 — FIPS 180) is the current default.
- **SHA-3** (Keccak — FIPS 202) is a structurally different design (sponge construction), kept as a hedge against a break in SHA-2.
- **Broken hashes:** MD5 and SHA-1 are dead — practical collisions exist (see the SHAttered attack on SHA-1). Never use them for security.
- Understand the **avalanche effect** (one bit flips ~half the output) and why hash length relates to collision resistance via the birthday bound (n-bit hash → ~2^(n/2) work to find a collision).

### 5. MACs and HMAC
- A **MAC** (Message Authentication Code) proves a message is authentic **and** untampered, using a shared secret. It answers "did this come from someone who holds the key, and is it unchanged?"
- **HMAC** — a specific, well-studied construction that wraps a hash function (HMAC-SHA256) with a key. Resistant to the length-extension attacks that plague naive `hash(key || message)`.
- Where you'll meet it constantly in IAM: **HMAC-signed JWTs (HS256)**, API request signing, webhook verification.
- Contrast with signatures (next): a MAC uses a *shared* secret — both parties can produce and verify. It gives integrity/authenticity but **not non-repudiation**.

### 6. Digital signatures
- Sign with a **private** key, verify with the corresponding **public** key. Anyone can verify; only the key holder could have signed.
- Gives integrity, authenticity, **and non-repudiation** (the signer can't credibly deny it).
- Algorithms: **RSA-PSS**, **ECDSA**, **EdDSA (Ed25519)**.
- Mechanically: you hash the message, then sign the hash. This is why hash collision resistance matters for signatures.
- IAM relevance: **RS256/ES256 JWTs**, signed SAML assertions, code signing, certificate signatures (a certificate *is* a signed statement — see PKI).

### 7. Password hashing (bcrypt / scrypt / Argon2, salt & pepper)
- **Do not use general-purpose hashes (SHA-256) for passwords.** They're too fast — an attacker can try billions of guesses per second on a GPU.
- Use **deliberately slow, memory-hard** functions built for passwords:
  - **bcrypt** — battle-tested, tunable work factor.
  - **scrypt** — memory-hard, resists GPU/ASIC cracking.
  - **Argon2** (specifically **Argon2id**) — the modern winner (Password Hashing Competition, 2015). Tunable time, memory, and parallelism. Prefer this for new systems.
- **Salt** — a unique random value per password, stored alongside the hash. Defeats precomputed rainbow tables and stops identical passwords from having identical hashes. Non-secret by design.
- **Pepper** — a secret value applied to *all* passwords, stored separately (ideally in an HSM or app config, not the DB). Adds a layer that survives a database-only breach.

### 8. Public Key Infrastructure (PKI): certificates, CAs, chain of trust, X.509
- A **certificate** binds an identity (a domain, a person, a service) to a public key — and is **signed by a Certificate Authority** to vouch for that binding.
- **X.509** is the certificate format/standard: subject, issuer, validity dates, public key, extensions, signature.
- **Chain of trust:** a leaf cert is signed by an intermediate CA, signed by a root CA. Your OS/browser ships a **trust store** of root CAs. Verification walks the chain up to a trusted root.
- Also learn: **CSR** (certificate signing request), **revocation** (CRL and OCSP — how you invalidate a cert before it expires), and **certificate pinning**.
- This is the backbone of TLS and of certificate-based / mutual-TLS authentication in fintech.

### 9. The TLS handshake, in detail
- Walk through **TLS 1.3** (and know how 1.2 differed):
  1. **ClientHello** — client offers supported cipher suites, TLS versions, and a key-share (its ephemeral ECDHE public value).
  2. **ServerHello** — server picks the parameters, sends its key-share, and its **certificate** (proving its identity via the PKI chain above).
  3. **Key derivation** — both sides combine their ECDHE shares to derive the same shared secret (Diffie-Hellman), then derive symmetric session keys from it.
  4. **Finished** — both confirm the handshake wasn't tampered with; encrypted application data flows using AES-GCM (or ChaCha20-Poly1305).
- Key wins to understand: TLS 1.3 is a **1-RTT** handshake (faster), removed all the legacy/weak options, and mandates **forward secrecy** via ephemeral key exchange.
- This ties *everything* together: asymmetric crypto (cert + key exchange) bootstraps symmetric crypto (session keys), with signatures and hashing ensuring integrity throughout.

### 10. Key management and rotation
- Cryptography's hardest problem in practice isn't the algorithms — it's **key management**. A perfect cipher with a leaked key is worthless.
- Concepts: **key generation** (from a good CSPRNG), **secure storage** (HSMs, KMS, secrets managers — never hardcoded), **rotation** (changing keys on a schedule and after suspected compromise), **key hierarchy** (a KEK — key-encryption-key — wrapping DEKs — data-encryption-keys), and **separation of duties** (no one person holds a full key).
- **HSM** (Hardware Security Module) — tamper-resistant hardware that generates and uses keys without ever exporting them. Standard in fintech and mandated by parts of PCI-DSS.

### 11. Randomness and entropy
- Nearly every crypto operation needs unpredictable random numbers: keys, IVs, nonces, salts, session tokens.
- Use a **CSPRNG** (cryptographically secure PRNG): `/dev/urandom`, `secrets` in Python, `crypto.randomBytes` in Node. **Never** use `random`/`Math.random` for anything security-relevant.
- Understand **entropy** (real unpredictability the system harvests), **seeding**, and famous failures — the 2008 Debian OpenSSL bug (crippled entropy made keys guessable) and nonce reuse breaking ECDSA/GCM.

### 12. Common crypto mistakes and attacks
- **ECB mode** — leaks plaintext patterns (the ECB penguin).
- **Weak/predictable RNG** — makes keys and tokens guessable.
- **Padding oracle** (e.g., against CBC) — error messages that reveal whether padding was valid let an attacker decrypt without the key.
- **Downgrade attacks** — tricking a connection into a weaker protocol/cipher (POODLE, FREAK, Logjam). Mitigated by disabling old versions.
- **Nonce/IV reuse** — catastrophic for CTR/GCM (can leak keys or plaintext).
- **Rolling your own crypto** — the cardinal sin. Use vetted libraries and standard constructions.
- The lesson underneath all of these: crypto fails at the *joints* — modes, padding, randomness, key handling — far more often than at the core algorithm.

### 13. Post-quantum cryptography (a brief note)
- A sufficiently large quantum computer would break RSA and ECC (via Shor's algorithm), while only weakening symmetric crypto and hashes (Grover's — mitigated by doubling key/output sizes).
- **NIST has standardized post-quantum algorithms** (2024): **ML-KEM** (Kyber, key encapsulation — FIPS 203), **ML-DSA** (Dilithium, signatures — FIPS 204), and **SLH-DSA** (SPHINCS+ — FIPS 205).
- Watch for **"harvest now, decrypt later"** — adversaries recording encrypted traffic today to decrypt once quantum computers arrive. Fintech is already planning **hybrid** (classical + PQC) migrations. You don't need to implement this yet, but know the vocabulary.

---

## Reading list

Real, reputable sources — start with the first two, use the rest as you go.

- ***Serious Cryptography* (2nd ed.), Jean-Philippe Aumasson** — the best modern, practitioner-focused crypto book. Rigorous but readable; start here.
- ***Crypto 101* by Laurens Van Houtven** — free (crypto101.io), gentle, hands-on introduction. Great companion for the early concepts.
- **The Cryptopals Crypto Challenges** (cryptopals.com) — free, legendary set of programming exercises where you *break* real crypto (padding oracles, ECB detection, weak RNG). The single best way to make this stick.
- **NIST crypto standards** — read the actual specs for the primitives you use: **FIPS 197** (AES), **FIPS 186** (Digital Signature Standard / ECDSA), **FIPS 180** (SHA-2), plus **SP 800-57** (key management) and **SP 800-63** (digital identity — directly relevant to IAM).
- **Cloudflare blog TLS explainers** — clear, diagram-rich walkthroughs of the TLS 1.3 handshake, forward secrecy, and PKI. Search "Cloudflare TLS 1.3" and "Cloudflare a detailed look at RFC 8446."
- **RFC 8446** (TLS 1.3) — dense, but the authoritative source once the blog posts have oriented you.
- *(Optional, deeper)* **Dan Boneh's Cryptography I** (Stanford, free on Coursera) — if you want the mathematical foundations properly.

---

## Labs (ask Lefler to set these up)

All labs use free, local, open tools. Each lab lives in `labs/NN-name/` with a short brief and starter files.

| # | Lab | You'll learn |
|---|-----|--------------|
| 1 | Encoding vs hashing vs encryption with `openssl` + Python | Prove to yourself Base64 is reversible with no key, hashes are one-way, and encryption needs a key — the confusion-killer, hands-on |
| 2 | AES encryption with the Python `cryptography` library | Encrypt/decrypt with AES-GCM, handle IVs correctly, and see an AEAD tag catch tampering |
| 3 | The ECB penguin | Encrypt a bitmap in ECB vs CBC/GCM and *see* why ECB leaks structure |
| 4 | RSA & ECC keypairs with `openssl` | Generate keys, encrypt small messages, sign and verify — feel the public/private split directly |
| 5 | Password hashing with Argon2 / bcrypt | Store passwords correctly, add per-user salts, tune work factors, and benchmark why fast hashes are dangerous |
| 6 | Build your own PKI: root CA → intermediate → leaf cert | Create a CA, issue and sign X.509 certs, build a chain of trust, and inspect certs with `openssl x509 -text` |
| 7 | Inspect a real TLS handshake in Wireshark | Capture a TLS 1.3 connection, identify ClientHello/ServerHello/certificate/key-share, and watch the switch to encrypted traffic |
| 8 | Cryptopals Set 1 (+ Set 2 padding oracle) | Detect ECB, break repeating-key XOR, and exploit a CBC padding oracle — attacker's-eye view of real weaknesses |
| 9 | Sign and verify a JWT (HS256 vs RS256) | See HMAC vs signature-based tokens end-to-end, and reproduce the classic `alg: none` / algorithm-confusion vulnerability |

---

## How this connects to IAM / fintech

Everything above is daily reality in an IAM role at a fintech like FinCo:

- **JWT / token signing** — access and ID tokens are signed (HMAC or RSA/ECDSA). Understanding signatures vs MACs is what lets you reason about token integrity and avoid `alg`-confusion bugs.
- **TLS everywhere** — every API call, every login, every service-to-service hop rides TLS. You'll configure cipher suites, terminate TLS, and debug handshake failures.
- **Password storage** — if your systems hold credentials, Argon2/bcrypt with proper salting is non-negotiable, and you'll be the person who knows why.
- **Certificate-based auth & mutual TLS** — machine identities, service accounts, and partner integrations often authenticate with client certificates. PKI fluency is the entry ticket.
- **HSMs & key management** — fintech keys live in HSMs/KMS with strict rotation and separation of duties. This is where "crypto" becomes "governed crypto."
- **PCI-DSS crypto requirements** — the standard mandates strong cryptography for cardholder data at rest and in transit, key-management procedures, and regular key rotation. Knowing the primitives is how you turn a compliance checkbox into an actual secure system.

Learn the machinery here, and the identity work in the other modules stops feeling like trusting a black box — you'll know exactly what's holding it up.
