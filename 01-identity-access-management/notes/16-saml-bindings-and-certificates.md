# SAML bindings & the two certificates — how the messages travel and who signs what

> **Janus's operational deep-cut.** In [note 13](13-saml-mastery-session2.md) we covered *what* an assertion is and, at a concept level, *why* there are two certificates (see its §6–8). This note goes **operational**: the exact **bindings** (how the same XML physically travels over HTTP) and the **certificate lifecycle** that generates real "SSO is down" tickets at FinCo. Your team runs **PingFederate**, so every setting below is mapped to a Ping connection.
>
> **Prereqs:** [SAML deep dive #1](02-saml-deep-dive.md) (fundamentals + bindings intro §5) and [SAML mastery session 2](13-saml-mastery-session2.md) (signing vs encryption, §6). Drill it after with the [question bank](14-saml-question-bank.md).

---

## TL;DR (the whole note in seven lines)

1. A **binding** is the *delivery method* for a SAML message — same XML "letter," different envelope. The XML doesn't change; only how it rides over HTTP does.
2. **HTTP-Redirect** = message squeezed into a URL query param (`?SAMLRequest=…`); tiny messages only; signature is **separate URL params**, not inside the XML.
3. **HTTP-POST** = base64 XML in an auto-submitting HTML form; big messages; signature is **embedded inside** the XML (XML-DSig). This is how the Response travels.
4. **HTTP-Artifact** = browser carries only a short reference; the SP fetches the real message over a **back-channel** SOAP call. The assertion never touches the browser.
5. The **real-world combo:** AuthnRequest via **Redirect**, Response via **POST**. Configured in **metadata** and in the Ping connection.
6. **Signing cert** = IdP's keypair, proves authenticity. **Encryption cert** = SP's keypair, provides confidentiality. The key *direction flips* — memorize which keypair owns which job.
7. **Signing cert expiry = every app breaks at once** (the classic outage). **Encryption cert problem = one SP can't decrypt.** Different blast radius → different first question.

---

# PART A — Protocol bindings (how the XML travels)

## A1. What a "binding" actually is

Plain words first: a **binding** is the **agreed way a SAML XML message physically travels over HTTP** between the browser, the IdP, and the SP.

Think of the XML message as a **letter**. The *content* of the letter (the AuthnRequest, the Response, the assertion) is the same no matter what. The **binding is the delivery method** — hand it over folded in an envelope, read it aloud, or send a claim-ticket the reader redeems later. Same letter, different logistics.

> **Why you care at FinCo:** when a Ping connection won't work, half the time the message *content* is fine and the **binding is misconfigured** — the SP is POSTing to a Redirect endpoint, or the ACS expects POST but Ping is sending Artifact. Knowing the four bindings tells you *where on the wire* to look.

SAML 2.0 defines these bindings (in the SAML Bindings spec, `saml-bindings-2.0-os`):

| Binding | Channel | Carries | You meet it in |
|---|---|---|---|
| **HTTP-Redirect** | Front (browser) | Small messages (AuthnRequest, LogoutRequest) | Every SP-initiated login |
| **HTTP-POST** | Front (browser) | Large messages (Response + Assertion) | Every login response |
| **HTTP-Artifact** | Front reference + **back-channel** | A short pointer, then the real message | High-security / assertion-never-in-browser setups |
| **SOAP** | Back-channel only | Artifact resolution, back-channel SLO | Behind the scenes |
| PAOS / ECP | Reverse-SOAP | Non-browser SAML clients | Rare (thick clients) — one-line awareness only |

---

## A2. HTTP-Redirect binding — the message stuffed into a URL

**Used for:** small messages the SP sends *to* the IdP — chiefly the **AuthnRequest** (and LogoutRequest).

### What actually happens to the XML

The raw XML is transformed through **three steps** so it survives inside a URL:

1. **DEFLATE-compress** the XML (raw deflate, no zlib header) — shrinks it and is why you can't read it by eye.
2. **base64-encode** the compressed bytes.
3. **URL-encode** the base64 and drop it into a `SAMLRequest=` query parameter.

> **Gotcha:** DEFLATE happens **only** in the Redirect binding. People assume "SAML is always compressed" — it isn't. POST is *not* deflated (see A3). If you try to inflate a POST body you'll get garbage.

### The signature is NOT inside the XML — this is the key nuance

On Redirect, you **cannot** embed an XML signature, because the XML got compressed and stuffed into a URL — there's no room and it wouldn't survive re-serialization. So SAML signs the Redirect binding a different way:

- The signature is computed over the **URL query string itself** (the `SAMLRequest` + `RelayState` + `SigAlg` params, in order).
- It travels as **two separate query parameters**: `SigAlg` (which algorithm) and `Signature` (the signature bytes, base64+URL-encoded).

So on Redirect the signature is a **sibling of the message in the URL**, not a child of the XML. Contrast this with POST, where the signature lives *inside* the document. This distinction decides *how you verify* (see Part B, §B7).

### A realistic example URL (placeholder values only)

```
https://idp.finco-lab.example.com/idp/SSO.saml2
  ?SAMLRequest=fVLBauMwEP0Vo7tjWXYcR8Qpu2GhgW1r6uxhb4o8SgS25JXG2%2Fbv...   ← deflate+base64+urlencode of the AuthnRequest
  &RelayState=%2Freports                                                  ← where the SP wants you to land
  &SigAlg=http%3A%2F%2Fwww.w3.org%2F2001%2F04%2Fxmldsig-more%23rsa-sha256 ← the algorithm (URL-encoded)
  &Signature=Rc8x2pQ...b64...%3D%3D                                       ← signature over the query string
```

(The `SAMLRequest` value is truncated — a real one is a few hundred to ~1,500 characters.)

### Why Redirect is only for small messages

URLs have length limits (browsers/proxies often choke past ~2,000–8,000 chars). A **Response with a full signed, maybe-encrypted assertion is far too big** for a URL. So Redirect is reserved for the slim AuthnRequest; the fat Response must use POST.

---

## A3. HTTP-POST binding — the auto-submitting form

**Used for:** the big message the IdP sends *back* — the **SAML Response** (containing the assertion).

### What actually happens to the XML

1. **base64-encode** the raw XML (**no DEFLATE** — this is the difference from Redirect).
2. Drop it into a **hidden form field** named `SAMLResponse` in an HTML page.
3. The page has a tiny script that **auto-submits** the form as an HTTP `POST` to the SP's **ACS URL**.

The user sees a blank/"Please wait…" page for a split second while JavaScript posts the form. No clicking required.

### The skeleton HTML form (this is what the IdP returns)

```html
<html>
 <body onload="document.forms[0].submit()">
  <form method="POST" action="https://sp.finco-lab.example.com/acs">   <!-- the SP's ACS URL -->
    <input type="hidden" name="SAMLResponse" value="PHNhbWxwOlJlc3BvbnNl...base64 XML...=="/>
    <input type="hidden" name="RelayState" value="/reports"/>
  </form>
  <noscript><button type="submit">Continue</button></noscript>          <!-- fallback if JS is off -->
 </body>
</html>
```

### The signature lives INSIDE the XML here

Because POST sends the full document in the request **body**, the IdP embeds a full **XML Digital Signature** (`<ds:Signature>`) *inside* the Response and/or Assertion. The signature's `Reference URI="#_assert789"` names exactly which element it covers.

### Why the Response uses POST, not Redirect (two reasons)

1. **Size.** A signed (and often encrypted) assertion is **way too big** for a URL. base64 without compression is even larger. POST bodies have effectively no length limit.
2. **Signatures survive better in the body.** An XML-DSig depends on the exact bytes. Cramming it through deflate → base64 → URL-encode → browser → un-encode risks canonicalization/whitespace damage. In a POST body the XML arrives intact, so the embedded signature verifies cleanly.

> **Memory hook:** *small + one-way question → Redirect; big + signed answer → POST.* Request goes out compressed in a URL; answer comes back whole in a form.

---

## A4. HTTP-Artifact binding — the claim-ticket

Sometimes you don't want the assertion to **ever pass through the browser** (it could be logged, screenshotted, or read by a malicious extension). Artifact solves that.

### How it works

1. Instead of the real message, the IdP sends the browser a tiny **artifact** — a short opaque reference (~44 bytes, base64) in a `SAMLart=` parameter. Think **coat-check ticket**, not the coat.
2. The browser hands that artifact to the SP.
3. The SP then opens a **direct back-channel** to the IdP (server-to-server, no browser) and makes a **SOAP** call:
   - SP → IdP: **`<ArtifactResolve>`** ("here's the ticket, give me the real message").
   - IdP → SP: **`<ArtifactResponse>`** (the actual Response + Assertion).
4. The SP validates the real assertion it just fetched.

```
 Browser              SP                         IdP
   |                  |                            |
   |-- SAMLart=AAQ... ->|                          |   1. browser carries only the ticket
   |                  |-- ArtifactResolve (SOAP) ->|   2. SP redeems it on the BACK channel
   |                  |<- ArtifactResponse (SOAP) -|   3. IdP returns the real assertion
   |                  |   (validate, make session) |
```

### When and why you'd use it — and the trade-off

- **Why:** the sensitive assertion travels **only** over the trusted SP↔IdP back-channel, never through the user's browser. Attractive for high-assurance flows or when assertions carry sensitive data and you don't want to rely solely on `<EncryptedAssertion>`.
- **Trade-off:** it **requires a direct network path** from the SP to the IdP. In a fintech with segmented networks, firewalls, and SaaS SPs sitting outside your perimeter, that path often **doesn't exist** — which is exactly why POST (pure front-channel, no direct path needed) dominates. Artifact is also more moving parts to break.

> **FinCo reality:** you'll mostly see POST. Artifact shows up for a security-sensitive **internal** SP that shares a network with your Ping cluster, or when a partner mandates "assertion must not transit the browser." If you see `SAMLart=` in SAML-tracer and then *nothing else in the browser*, that's Artifact — the real assertion is on the back-channel and won't appear in the trace.

---

## A5. SOAP binding (and one line on PAOS/ECP)

- **SOAP binding** = pure **back-channel** SOAP-over-HTTP, no browser involved. It's the transport under **ArtifactResolve/Response** (A4) and under **back-channel Single Logout** (an IdP tells each SP "kill the session" server-to-server instead of bouncing the browser around).
- **PAOS / ECP** (Enhanced Client or Proxy) = a "reverse SOAP" profile for **non-browser** SAML clients (thick desktop/mobile apps that speak SAML themselves). Rare; just recognize the name.

---

## A6. The real-world combination + where it's configured

The overwhelmingly common setup:

| Message | Direction | Binding | Why |
|---|---|---|---|
| **AuthnRequest** | SP → IdP | **HTTP-Redirect** | Small; fits a URL; sign via query params |
| **Response + Assertion** | IdP → SP | **HTTP-POST** | Big + signed; needs a body |

### Where it lives in metadata

Bindings are declared in each party's **metadata** by endpoint. Two things to spot:

**IdP metadata** advertises how you can *reach* its SSO endpoint:

```xml
<IDPSSODescriptor ...>
  <!-- the IdP offers SSO over BOTH bindings; the SP picks one -->
  <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                       Location="https://idp.finco-lab.example.com/idp/SSO.saml2"/>
  <SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
                       Location="https://idp.finco-lab.example.com/idp/SSO.saml2"/>
</IDPSSODescriptor>
```

**SP metadata** advertises where the answer should be POSTed — the **ACS**, each with an **`index`** so the AuthnRequest can point at one by number:

```xml
<SPSSODescriptor ...>
  <AssertionConsumerService index="0" isDefault="true"
      Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
      Location="https://sp.finco-lab.example.com/acs"/>
  <AssertionConsumerService index="1"
      Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Artifact"
      Location="https://sp.finco-lab.example.com/acs-artifact"/>
</SPSSODescriptor>
```

> **The `index` matters:** an AuthnRequest can say `AssertionConsumerServiceIndex="0"` instead of spelling out the full URL. If the SP registers a *new* ACS at a different index and the request still points at the old one, the Response goes to the wrong place — a real ticket. Prefer matching by index **or** by URL, never a mismatched mix.

### The equivalent in a PingFederate connection

On the **SP Connection** (Ping is acting as IdP for that app), the same choices appear as UI toggles:

- **Protocol Settings → Allowable SAML Bindings:** checkboxes for **POST / Redirect / Artifact / SOAP**. Uncheck what you don't want the partner to use.
- **Assertion Consumer Service URL(s):** the list of ACS endpoints + their **binding** + **index** + which is **default** — this is literally the `<AssertionConsumerService>` block above, entered by hand or auto-imported from the SP's metadata.
- **Inbound (SSO) binding** for the AuthnRequest the partner sends you (usually Redirect).

> **Ping tip:** when you "import metadata" for a new SP, Ping fills these ACS + binding rows automatically. When a partner emails you a *URL* instead of metadata, you type them in — and a typo'd binding or index is a classic day-one connection failure. Always prefer the metadata file.

---

## A7. Decision table — pick the binding at a glance

| Binding | Direction (typical) | Size limit | Where the signature lives | Common use |
|---|---|---|---|---|
| **HTTP-Redirect** | SP → IdP | **Small** (URL length) | **Separate URL params** (`SigAlg` + `Signature`) | AuthnRequest, LogoutRequest |
| **HTTP-POST** | IdP → SP | **Large** (form body) | **Inside the XML** (`<ds:Signature>`) | SAML Response + Assertion |
| **HTTP-Artifact** | either (front ref + back-channel) | Small ref only | Inside the fetched XML | Assertion must never hit the browser |
| **SOAP** | back-channel only | Large | Inside the XML | Artifact resolution, back-channel SLO |

---

# PART B — The two certificates (operational)

> This builds on [note 13 §6](13-saml-mastery-session2.md) (the concept + the whose-key-does-what table). Here we go into **rotation, expiry, blast radius, and debugging** — the job.

## B1. One-table recap, then straight to operations

| Certificate | Whose keypair | IdP does | SP does | Gives you |
|---|---|---|---|---|
| **Signing cert** | the **IdP's** | **signs** the assertion with its **private** key | **verifies** with the IdP's **public** cert (pinned from metadata) | **Authenticity + integrity** ("really from this IdP, unaltered") |
| **Encryption cert** | the **SP's** | **encrypts** the assertion with the SP's **public** cert | **decrypts** with its **own private** key | **Confidentiality** ("only this SP can read it") |

**The direction flip — say it out loud until it sticks:** you **sign with your own** private key (IdP signs); you **encrypt with the reader's** public lock (IdP uses the *SP's* public cert). So the signing keypair belongs to the **sender** (IdP), the encryption keypair belongs to the **receiver** (SP). Two certs, two owners, opposite directions.

---

## B2. SP-side signing too — signed AuthnRequests

It's not only IdPs that sign. An **SP can have its own signing cert** and sign its **AuthnRequest** so the IdP can prove *the request* really came from that SP.

- The SP signs the AuthnRequest with **its** private key; the IdP verifies with the **SP's** public cert (pinned from the SP's metadata).
- On the **Redirect binding** this is the `SigAlg` + `Signature` query params from §A2.
- **Why bother?** It stops an attacker forging AuthnRequests (e.g., tampering with the ACS URL or NameID policy). At a fintech, signed AuthnRequests are commonly **required** for higher-assurance connections.

So a fully-locked-down connection can involve **three** cert relationships: IdP signs the Response (IdP's key), SP signs the AuthnRequest (SP's key), IdP encrypts the assertion (SP's key). Know which are switched on before you debug.

> **In PingFederate:** the SP connection has **Signature Verification** settings (the partner's cert used to check inbound signed AuthnRequests) and **Digital Signature Settings** (which of Ping's own certs signs the outbound Response). These are separate boxes — don't confuse "the cert I verify *them* with" and "the cert I sign *my* messages with."

---

## B3. The trust model — metadata pinning, not browser PKI

SAML certs are usually **self-signed**, and **that is fine**. This surprises people coming from HTTPS.

- HTTPS trust = "does a **CA chain** vouch for this cert?"
- SAML trust = "is this the **exact cert** I was handed in the partner's metadata at onboarding?" That's **pinning**.

The **metadata exchange at connection setup IS the trust ceremony.** You (or the partner) hand over metadata containing the public cert; the other side pins it. No CA needed. A self-signed cert pinned in metadata is *more* precise than a CA-issued one you didn't pin.

> **Consequence for the job:** because trust is "byte-for-byte the pinned cert," **any** cert change — even a legitimate renewal — **breaks the connection until the new cert is shared and pinned.** That's the root of every rotation outage below. (Note: some stacks, Ping included, still enforce the cert's **NotAfter** expiry date even though there's no CA — so expiry is a real clock ticking.)

---

## B4. Scenario: the signing cert expires — "SSO is down for everything"

This is *the* classic SAML incident. Internalize the blast radius.

- The IdP signs **every** app's assertion with **one** signing cert.
- When that cert **expires** (or is rotated without telling SPs), **every SP that pins it rejects every login simultaneously.** Not one app — **all** of them.
- The user-visible error at the SP is some flavor of **"signature validation failed"** / "invalid signature" / "unable to verify the SAML Response."

**Why it's brutal:** the blast radius is the **entire IdP**. One expired cert = a company-wide "I can't log into anything" flood. At FinCo that's a **Sev-1**, and in scope for audit (SOX/PCI care about availability of access controls and change management).

### How rollover is done safely (zero-downtime)

The fix is to make SPs trust the **new** cert *before* the old one dies, so there's an overlap window:

1. **Generate** the new signing cert/key on the IdP well before expiry (weeks, not hours).
2. **Publish both certs in the IdP metadata** — the new one alongside the old — as `<KeyDescriptor use="signing">` entries. Now compliant SPs that consume metadata will accept **either**.
3. **Coordinate with SPs** that *don't* auto-refresh metadata (many SaaS apps require you to upload the cert by hand) — get them to add the new cert during the overlap.
4. **Cut over:** the IdP starts **signing with the new cert**. SPs that trust both still verify fine.
5. **Retire** the old cert from metadata after everyone's confirmed.

**In PingFederate** this is directly supported: a connection/signing config can have a **primary (active) signing cert plus a secondary cert**, and Ping can publish both in its metadata so partners trust the incoming cert during the overlap. That secondary-cert / dual-publish capability is exactly what makes **zero-downtime rotation** possible — use it; never hard-swap a signing cert in production.

> **Prevention:** track every signing cert's **NotAfter** like a production deadline. A cert-expiry dashboard / calendar reminder set ~60 days out is standard fintech hygiene. "We didn't know it was expiring" is not an acceptable post-incident line.

---

## B5. Scenario: the encryption cert expires or mismatches — one SP can't decrypt

Different cert, different — and **smaller** — blast radius.

- The encryption cert is the **SP's**. Only that SP is affected.
- Symptom appears **only on the SP side**: **"unable to decrypt assertion"** / "decryption failed" / "cannot find matching private key."
- Common causes:
  - The **SP rotated its keypair** but the IdP is still encrypting with the SP's **old public cert** (so the SP's *new* private key can't open it).
  - The IdP has the **wrong** SP encryption cert pinned (typo, stale metadata).
  - The SP has **multiple** decryption keys and none matches the one used to encrypt.

**Blast radius = one SP.** If *one* app throws "can't decrypt" while everything else is fine, suspect the **encryption** cert. If *every* app throws "signature invalid" at once, suspect the **signing** cert. That single contrast routes the incident in seconds.

---

## B6. Mini debugging playbook — which error points at which cert

| Symptom / error text | Blast radius | Prime suspect | First check |
|---|---|---|---|
| "signature validation failed" / "invalid signature" across **many** apps | Whole IdP | **IdP signing cert** expired/rotated | Compare `<ds:X509Certificate>` in the Response vs the signing cert pinned in the SP's config |
| "signature validation failed" on **one** newly-onboarded app | That connection | Wrong signing cert pinned, or wrong binding | Re-import the IdP metadata into that SP |
| "unable to decrypt assertion" on **one** app | That SP | **SP encryption cert** mismatch/expiry | Confirm the IdP is encrypting with the SP's *current* public cert |
| "AuthnRequest signature invalid" (IdP-side log) | That connection | **SP signing cert** for signed requests | Check the cert Ping uses to verify inbound requests |
| Login works, then "not yet valid / expired" | Any | **Not a cert** — clock skew | NTP on both sides (see [note 02 §8](02-saml-deep-dive.md)) |

### The SAML-tracer routine (front-channel bindings)

1. **Capture** the flow in **SAML-tracer** (Firefox/Chrome). You'll see the Redirect `SAMLRequest` and the POST `SAMLResponse`.
2. **base64-decode** the `SAMLResponse` (SAML-tracer shows the SAML tab pretty-printed).
3. Find **`<ds:Signature>` → `<ds:X509Certificate>`** — that's the cert the IdP *actually* signed with. Copy it.
4. **Compare** it to the cert **pinned in the SP** (or in the IdP metadata you were given). Byte-mismatch or different serial/fingerprint = you found it. This is the metadata-vs-Response diff that solves most "signature invalid" tickets.
5. If it's **`<EncryptedAssertion>`** and you see "can't decrypt," the trace *can't* show you the plaintext — pivot to the SP's decryption key config instead.

> **Gotcha:** Artifact-binding assertions **won't appear** in SAML-tracer at all (they're fetched on the back-channel). If the browser shows only `SAMLart=`, stop looking in the trace and read the SP's server logs for the ArtifactResolve/Response.

---

## B7. How the signature is verified — Redirect vs POST — plus the attacks

The verification mechanism **depends on the binding**, and each has its own failure mode.

### Redirect binding → verify the query-param signature

The signature covers the **URL query string** (`SAMLRequest`/`SAMLResponse` + `RelayState` + `SigAlg`, concatenated in the spec's order). The verifier rebuilds that exact string and checks `Signature` with the sender's public cert. **Gotcha:** re-ordering or re-encoding the params breaks the check — you must verify the *raw received* string, not a re-serialized one.

### POST binding → verify the embedded XML-DSig

The signature is `<ds:Signature>` inside the document, with a `Reference URI` naming the signed element. The verifier canonicalizes that element, re-hashes it, and checks it against the pinned cert.

### The classic attacks (each with its defense — repo rule)

| Attack | Mechanism | Defense |
|---|---|---|
| **Signature stripping / "not required to be signed" misconfig** | SP is configured to *accept* unsigned assertions (or only checks the outer Response, not the Assertion). Attacker removes/forges the signature. | **Require the Assertion itself to be signed**; reject unsigned or partially-signed messages. In Ping, enforce "require signed assertion" on the connection. |
| **XML Signature Wrapping (XSW)** | Attacker adds a **second, forged** assertion and rearranges the XML so the SP **verifies the signature on the real element but reads attributes from the forged one.** The signature is technically valid — over the wrong node. | Use a **hardened, current SAML library**; **validate against the schema**; ensure the element that was *verified* is exactly the element that gets *processed*; reject documents with multiple assertions. |
| **Replay** | Attacker resends a captured, still-valid assertion. | Enforce **`NotOnOrAfter`** (short window), check **`Recipient`/ACS** and **`Audience`** match you, and **cache one-time assertion IDs** to reject reuse. |
| **`KeyInfo` / embedded-cert injection** | Attacker signs with *their* key and ships *their* cert inside the message. | **Verify only against the pinned metadata cert** — never a cert embedded in the message (`<KeyInfo>`). This is the golden rule from [note 13 §6a](13-saml-mastery-session2.md). |

> **XSW in one line to remember:** *a valid signature over the wrong element is still a bypass.* The defense isn't "check the signature" — it's "make sure the thing you verified is the thing you trusted."
>
> **Hand-offs:** ask **Loki** to walk XSW / signature-stripping on your **local Keycloak lab only** (never a FinCo connection), and ask **Heimdall** what a SIEM should alert on — signature-validation failures spiking, a new/unexpected signing cert appearing in Responses, or multiple assertions in one message.

---

## What you learned

- **Bindings** = the *delivery method* for the same SAML letter. Four to know: **Redirect** (URL, small, signature in query params, DEFLATEd), **POST** (form body, big, signature inside the XML, not DEFLATEd), **Artifact** (browser carries a ticket; SP fetches the real message on a back-channel), and **SOAP** (pure back-channel) — plus PAOS/ECP awareness (§A1–A5).
- **The real combo:** AuthnRequest via **Redirect**, Response via **POST** — declared in **metadata** (`<SingleSignOnService>` / `<AssertionConsumerService index=…>`) and mirrored in the **PingFederate** connection's binding checkboxes and ACS list (§A6–A7).
- **Two certificates, opposite directions:** **signing** = IdP's keypair (authenticity), **encryption** = SP's keypair (confidentiality); SPs can also sign their AuthnRequests (§B1–B2). Trust is **metadata pinning**, not CA chains, so *any* cert change breaks things until re-shared (§B3).
- **Blast radius routes the incident:** signing-cert expiry breaks **every** app at once ("signature validation failed") and needs **overlap/dual-cert rollover** in Ping; encryption-cert problems break **one** SP ("unable to decrypt") (§B4–B6).
- **Verification differs by binding** (query-param signature vs XML-DSig), and the classic attacks — signature stripping, **XSW**, replay, KeyInfo injection — each pair with a concrete defense (§B7).

## Next

- **Zoom out:** [note 17 — the IAM domains map](17-iam-domains-map.md) — where SAML/federation sits among the other IAM pillars (AuthN/AuthZ, directories, PAM, IGA, Zero Trust) so you can see the whole board.
- **Drill it:** re-test yourself with the [SAML question bank](14-saml-question-bank.md) — Tier 3 covers bindings and cert rotation.
- **Hands-on:** ask **Lefler** to stand up a Keycloak IdP + a sample SP, then (a) capture a Redirect AuthnRequest and a POST Response in SAML-tracer, and (b) deliberately swap the signing cert to reproduce the "signature validation failed" outage — safely, in the lab.

*— Janus 🔐, building on your senior's whiteboard session ([note 13](13-saml-mastery-session2.md))*
