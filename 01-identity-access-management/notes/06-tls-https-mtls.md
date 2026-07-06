# HTTPS, TLS & mTLS — transport security, from scratch (and what your manager meant)

> **Janus's deep dive.** Everything you've learned so far — SAML assertions, OIDC tokens, LDAP binds — rides inside an encrypted tunnel called **TLS**. This note builds it up from plain HTTP, explains the padlock, then extends it to **mTLS** (mutual TLS) — which is pure IAM: *certificate-based authentication of machines*. It ends by decoding exactly what your manager said about Kubernetes pods using HTTP "because mTLS is there." Prereqs: [the landscape note](01-iam-protocol-landscape.md). Deep crypto: [`04-cryptography`](../../04-cryptography/README.md) §8–9. Zero Trust context: [`02-network-security`](../../02-network-security/README.md) §12.

---

## 1. Start at the bottom: what HTTP is

**HTTP (HyperText Transfer Protocol)** is how a browser (client) and a server talk on the web. It's a simple request/response text protocol:

```
GET /account/balance HTTP/1.1
Host: bank.example.com
Cookie: session=abc123
```
…and the server replies with your balance. Clean and simple — and **completely in the clear**.

**The problem:** HTTP travels as plaintext across many machines you don't control (Wi-Fi, ISPs, routers, proxies). Anyone on the path can:
- **Read** it — your session cookie, your balance (loss of *confidentiality*).
- **Change** it — rewrite the response or inject content (loss of *integrity*).
- **Impersonate** — pretend to *be* bank.example.com (loss of *authentication*).

For a bank, any one of those is catastrophic. Enter TLS.

---

## 2. TLS — the security layer

**TLS (Transport Layer Security)** — the successor to the old "SSL" (people still say "SSL" but mean TLS) — wraps a plaintext protocol in a tunnel that provides the three things HTTP lacks:

| Property | What it means | How TLS provides it |
|---|---|---|
| **Confidentiality** | Eavesdroppers can't read it | **Encryption** with a session key |
| **Integrity** | Tampering is detected | Message authentication codes (MACs) — see [crypto §5](../../04-cryptography/README.md) |
| **Authentication** | You're talking to the *real* server | The server's **certificate**, signed by a trusted **CA** |

**HTTPS = HTTP running inside a TLS tunnel.** Same HTTP as before; now it's just encrypted and authenticated. The `S` is TLS. (LDAPS, SMTPS, etc. are the same trick applied to other protocols.)

---

## 3. The TLS handshake — how two strangers agree on a secret

Before any HTTP flows, the client and server do a **handshake**. Conceptually (modern TLS 1.3):

```
 Client (browser)                                    Server (bank.example.com)
      |                                                        |
 1.   |----- ClientHello: "let's talk TLS; here are the ------>|
      |        cipher suites I support" + a random             |
      |                                                        |
 2.   |<---- ServerHello (chosen cipher) + CERTIFICATE --------|
      |        (server's public key + domain, signed by a CA)  |
      |                                                        |
 3.   | VERIFY the certificate:                                |
      |   • signed by a CA I trust? (chain to a trusted root)  |
      |   • not expired / not revoked?                         |
      |   • does the name match bank.example.com?              |
      |   → THIS is the "authentication" — proof it's the      |
      |     real bank, not an impostor.                        |
      |                                                        |
 4.   |==== key agreement (e.g. ECDHE) — both derive the ======|
      |     same SESSION KEY without an eavesdropper           |
      |     ever seeing it                                     |
      |                                                        |
 5.   |##### switch to fast SYMMETRIC encryption with the #####|
      |      session key — the rest of the HTTP session        |
      |      is encrypted end to end                           |
```

Two crypto ideas make this work (full treatment in [crypto §5–6](../../04-cryptography/README.md)):
- **Asymmetric crypto** (public/private key pairs) is used *briefly* to authenticate the server and safely agree on a shared key. It's secure but **slow**.
- **Symmetric crypto** (one shared session key) encrypts the *bulk* traffic. It's **fast**.
- TLS uses asymmetric to *bootstrap trust and a key*, then symmetric for *speed*. Best of both.

> **The padlock in your browser** simply means: step 3 succeeded. The server presented a valid certificate that chains to a CA your browser trusts, isn't expired, and matches the domain. That's it. It does **not** mean the site is "safe" or trustworthy — only that the channel is encrypted and the server proved its domain identity.

---

## 4. Certificates, CAs & the chain of trust (this is PKI — and it's IAM)

The whole thing hinges on the **certificate**. A TLS certificate is an **X.509** document that binds a **public key** to an **identity** (a domain name), stamped with a digital **signature** from a **Certificate Authority (CA)**.

**Why you can trust a stranger's certificate — the chain of trust:**
```
   Root CA  (e.g. DigiCert Root)      ← pre-installed & trusted by your OS/browser
      │  signs
   Intermediate CA
      │  signs
   Leaf certificate (bank.example.com)  ← what the server presents
```
Your device ships with a **trust store** of ~hundreds of root CAs. If a server's certificate chains up to one of those roots, and each signature verifies, your browser trusts it — **without ever contacting the bank in advance.** That pre-established trust (root CAs baked into your OS) is *exactly* the federation pattern from [note 01](01-iam-protocol-landscape.md): trust anchored in a certificate, established out-of-band. **SAML's assertion signing and TLS are the same PKI idea** — which is why "the cert expired" breaks *both* a SAML SSO and an HTTPS site, and why cert rotation is a recurring ops chore. See [SAML cert rotation](02-saml-deep-dive.md#6-signing--encryption--the-trust-anchor).

Certificates **expire** (forcing renewal) and can be **revoked** early (CRL / OCSP) if a key is compromised.

---

## 5. mTLS — when the *client* proves identity too

Here's the IAM heart of this note.

**Normal TLS is one-way authentication:** only the **server** presents a certificate. The client stays anonymous *at the TLS layer* — you (the human) authenticate *later*, at the app layer, with a password + MFA or a token. That's fine for the public web: the bank proves it's the bank, then you log in.

**mTLS (mutual TLS) is two-way authentication:** the server **also demands a certificate from the client**, and the client presents one. Now **both sides cryptographically prove their identity** before any data flows. Neither talks to an unverified peer.

```
        TLS (one-way)                         mTLS (mutual)
   Client ──────────────► Server        Client ◄────────────► Server
     "prove you're the bank"              "prove you're the bank"
      (server cert only)                  "and prove you're an
                                           authorized client"
                                          (BOTH present certs)
```

**In IAM terms, mTLS is authentication for machines: the certificate *is* the identity.** Where user-facing protocols (SAML, OIDC) answer *"who is this person?"*, mTLS answers *"which service/workload is this?"* — no passwords, no tokens, just a certificate each side verifies. That makes it the standard for:
- **Service-to-service / microservice** calls (the Kubernetes case below).
- **API / partner integrations** in banking (fintech B2B links very often require client certs).
- **Zero Trust** networks — verify *every* connection, even "internal" ones ([network §12](../../02-network-security/README.md), NIST SP 800-207).
- IoT / device authentication.

> **mTLS ↔ what you already know:** it's the transport-layer cousin of OAuth **client credentials** ([note 03 §7](03-oauth-oidc-deep-dive.md#7-the-other-grant-types-know-when-each-applies)). Both authenticate a *workload* rather than a *person* — one with a certificate, the other with a client secret/token. Same job (machine identity), different mechanism.

---

## 6. Kubernetes, service meshes & what your manager actually meant

Your manager said: *"once the mTLS is there, we can have the k8s pods talking to each other via HTTP instead of HTTPS."* This is a common, correct statement — with one nuance worth getting right so you can ask a sharp question.

**The setup.** In Kubernetes you run many small services in **pods** that constantly call each other over the network. You want that internal traffic **encrypted and mutually authenticated** — because Zero Trust says *don't trust the network just because it's "internal."* But making every application implement TLS (manage certs, rotate them, verify peers) is painful and error-prone.

**The solution: a service mesh** (Istio, Linkerd). It injects a **sidecar proxy** — a second container (e.g., Envoy) — into each pod, right next to your app container. The magic:

```
        POD A                                              POD B
  ┌───────────────────┐                            ┌───────────────────┐
  │  app container    │                            │   app container   │
  │      │  HTTP over  │                            │  HTTP over │      │
  │      ▼  localhost  │        mTLS on the         │  localhost ▲      │
  │  ┌─────────────┐   │  ═══════ wire ═══════════► │   ┌─────────────┐ │
  │  │  sidecar    │───┼── (encrypted + mutually ───┼──►│  sidecar    │ │
  │  │  proxy      │   │      authenticated)        │   │  proxy      │ │
  │  └─────────────┘   │                            │   └─────────────┘ │
  └───────────────────┘                            └───────────────────┘
```

1. Your app talks **plain HTTP to its own sidecar over `localhost`** — that traffic **never leaves the pod** (loopback, inherently trusted).
2. The sidecar **transparently wraps it in mTLS** and sends it to the destination pod's sidecar.
3. The receiving sidecar terminates mTLS and hands **plain HTTP to its app over localhost**.
4. The mesh **issues and auto-rotates the certificates** for every workload (often via **SPIFFE/SPIRE** identities; in Istio, the `istiod` control plane acts as the CA).

**So what your manager meant, precisely:** *the application code speaks HTTP (it doesn't implement TLS itself), and the mesh's mTLS layer transparently secures the real pod-to-pod traffic on the wire.* From the developer's view it's "HTTP"; on the network it's mTLS. You get encryption + mutual authentication + workload identity **for free**, without touching app code.

> **The nuance to hold (and the sharp question to ask):** the "HTTP" is **app → its own sidecar inside the pod** (localhost, safe). The traffic **between pods across the network is mTLS, not plaintext.** It would be *wrong* to think "pods send raw HTTP across nodes and that's fine." So the question that shows you get it:
> *"Are we using a service mesh with sidecar mTLS (Istio/Linkerd), and is it in **STRICT** mode — where plaintext to a pod is rejected — or **PERMISSIVE** mode, where plaintext is still allowed during migration?"*
> STRICT means the mTLS is actually enforced; PERMISSIVE means an attacker (or a misconfigured client) could still talk plaintext. That distinction is a real security control, and asking about it will land well.

**Fintech relevance:** PCI-DSS requires cardholder data to be **encrypted in transit** — and "it's internal traffic" is *not* an exemption. A mesh doing mTLS everywhere is how a modern payments platform satisfies that *and* implements Zero Trust for microservices at once. (Compliance mapping: [`08-grc-compliance`](../../08-grc-compliance/README.md) §5.)

---

## 7. Attacks & defenses (pair them — repo rule)

| Attack | What it does | Defense |
|---|---|---|
| **Man-in-the-Middle (MITM)** | Attacker sits between client and server, reading/altering traffic | TLS with proper cert validation; **mTLS** for services |
| **SSL stripping / downgrade** | Force a victim from HTTPS back to HTTP, or to an old weak TLS | **HSTS**, TLS 1.2+ only, disable TLS 1.0/1.1 & weak ciphers |
| **Expired / misconfigured certificate** | Outage (or users click through warnings, training them to ignore security) | Monitor expiry; **automate rotation** (ACME/Let's Encrypt, `cert-manager` in k8s) |
| **Rogue / compromised CA** | Attacker gets a valid cert for your domain from a bad CA | Certificate Transparency logs; **cert pinning**; a **private CA** for internal mTLS |
| **mTLS in PERMISSIVE mode** | Plaintext still accepted → the "protection" is bypassable | Enforce **STRICT** mTLS once migration is done |
| **Stolen client key/cert** | Attacker impersonates a service | **Short-lived** certs (SPIFFE SVIDs rotate often), protect private keys, revoke fast |

Purple-team: ask **Heimdall** what a SIEM would flag (TLS downgrade attempts, expired-cert errors spiking, plaintext connections to a STRICT-mode service) and **Loki** to demo a MITM against your *own lab only*.

---

## 8. Tools you'll actually use

- **`openssl s_client -connect host:443`** — see the certificate chain and handshake by hand.
- **Browser → click the padlock → certificate** — inspect any site's cert, issuer, expiry.
- **`curl -v https://…`** — watch the TLS negotiation; add `--cert`/`--key` to test **mTLS**.
- **Wireshark** — watch a real handshake on the wire ([network §*](../../02-network-security/README.md) has a handshake lab).
- **Kubernetes:** `istioctl` / `linkerd` CLIs, `cert-manager` — inspect and manage mesh mTLS and certs.

---

## 9. The one-paragraph recap (say this back to yourself)

HTTP is plaintext and unsafe. **TLS** wraps it to give confidentiality, integrity, and **server authentication via a CA-signed certificate** — that's **HTTPS**, and the padlock just means the cert checked out. **mTLS** adds *client* authentication so **both** sides prove identity with certificates — making it **machine-to-machine authentication**, i.e. pure IAM for workloads. In **Kubernetes**, a **service mesh** puts a **sidecar** next to each app: the app speaks **HTTP to its local sidecar**, and the **sidecars speak mTLS to each other**, so "the pods use HTTP" while the wire is actually encrypted and mutually authenticated — Zero Trust, transparently. The sharp question: *"STRICT or PERMISSIVE mTLS?"*

**Next:** [note 07 — the remaining IAM foundations](07-iam-foundations.md) (MFA, sessions, authZ models, PAM, IGA, Zero Trust), and see certificate identity in action in [Lab 01](../labs/01-keycloak-idp/README.md) (Keycloak's signing keys) and [Lab 02](../labs/02-saml-assertion-anatomy/README.md) (the `<ds:Signature>` / X.509 in a real assertion).

*— Janus 🔐*
