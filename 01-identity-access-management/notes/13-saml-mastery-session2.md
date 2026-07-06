# SAML Mastery — Session 2: flows, assertion anatomy, certificates & encryption

> **Janus's deep dive, round 2.** This note captures (and levels up) the whiteboard session your senior — the Ping stack expert — ran on **6 July 2026**: SP-initiated vs IdP-initiated, how to *speed-read* an assertion, what's truly mandatory, where the EntityID lives, how the certificates create trust, and exactly **what is and isn't encrypted** end to end.
>
> **Prereqs:** [SAML deep dive #1](02-saml-deep-dive.md) (the fundamentals) and ideally [Lab 02](../labs/02-saml-assertion-anatomy/README.md) (you've decoded one assertion by hand). Quiz yourself afterwards with the [**SAML question bank**](14-saml-question-bank.md).

---

## TL;DR (the whole note in six lines)

1. **SSO** = log in once, use many apps. **Federated SSO** = the same trick *across* organizations/identity systems, made possible by a trust contract (metadata + certificates). SAML is a protocol that delivers both.
2. **SP-initiated** = user starts at the app; there's an **AuthnRequest**. **IdP-initiated** = user starts at the IdP portal; the IdP sends an **unsolicited Response** (no AuthnRequest, no `InResponseTo`) — handy for onboarding/testing, weaker for security.
3. An assertion's **Subject → NameID** is the *who* — the unique identifier the SP maps to an account. Treat it as mandatory, always.
4. **EntityID** = each party's unique name. It shows up in exactly **three load-bearing places**: AuthnRequest `Issuer` (SP's), Response/Assertion `Issuer` (IdP's), and `Audience` (SP's again).
5. Certificates do **two different jobs**: **signing** (IdP's keypair → proves authenticity/integrity) and **encryption** (SP's keypair → hides the content). The key *direction flips* between the two.
6. **TLS encrypts the pipe, not the message.** Base64 is *encoding*, not encryption — the browser (and its owner) can read every assertion unless you add XML-level encryption.

---

## 1. SSO vs Federated SSO — get the words right first

Two terms that get blurred in meetings. Untangle them once:

| Term | Plain English | Example |
|---|---|---|
| **SSO (Single Sign-On)** | Log in **once**, then open **many apps** without logging in again. Says nothing about *how*. | You unlock your laptop in the morning; every internal tool just opens. |
| **Federation** | Two **different security domains** (different companies, or different identity systems) agree to **trust each other's logins**, via a signed contract of certificates + metadata. | Your employer's IdP is trusted by Workday, Salesforce, ServiceNow — companies that have never seen your password. |
| **Federated SSO** | SSO **achieved across** those domain boundaries, using a federation protocol — **SAML** or OIDC. | One corporate login gets you into 40 third-party SaaS apps. |

**The memory hook:** *all federated SSO is SSO; not all SSO is federated.* A single company can do SSO with a shared cookie on one domain — no federation needed. The moment the apps live in **someone else's** security domain (a SaaS vendor, a partner bank), you need federation — and that's precisely the problem SAML was built to solve in 2005.

**How SAML delivers both:** the IdP authenticates you **once** and keeps an *IdP session*. Every app (SP) that trusts the IdP gets a fresh signed **assertion** instead of asking for a password. One authentication → many app sessions = SSO. The trust between IdP and each SP is established out-of-band with **metadata + certificates** = federation.

> **Job tie-in:** at a fintech like FinCo, "onboard this SaaS vendor to SSO" is a bread-and-butter ticket. What you're really doing is *establishing federation*: exchanging metadata, pinning certificates, agreeing on NameID format and attributes.

---

## 2. The cast, and the one identifier that names everybody: EntityID

Three actors (recap from [note 02](02-saml-deep-dive.md)): the **user** (principal), the **IdP** (authenticates, asserts), the **SP** (the app, trusts the assertion). The browser is the courier between them.

Every IdP and every SP has an **EntityID** — its **globally-unique name** in the federation. Think *passport number for a system*.

- Usually formatted as a URL (`https://idp.finco-lab.example.com/metadata`) — but it's **just a name**. It does **not** need to resolve to a real page.
- It must be **stable and unique**. Changing it breaks every federation partner that pinned it.
- It's declared in each party's **metadata** (the XML "business card" containing EntityID + endpoints + certificates).

**The three load-bearing places you'll see an EntityID** (this is a favorite interview probe):

| Where | Whose EntityID | What it means |
|---|---|---|
| `<Issuer>` inside the **AuthnRequest** | the **SP's** | "This login request comes from *me*, app X" — the IdP uses it to look up the right connection/config |
| `<Issuer>` inside the **Response** and **Assertion** | the **IdP's** | "This assertion was minted by *me*, IdP Y" — the SP uses it to pick which certificate to verify against |
| `<Audience>` inside the assertion's Conditions | the **SP's** | "This assertion is *only for* app X" — stops an assertion minted for app A being replayed to app B |

**Speed-read trick:** `Issuer` answers *who sent this*; `Audience` answers *who may consume this*. Mismatch in either = a "who are you?" / "not for me" rejection.

---

## 3. The end-to-end flow — SP-initiated, every hop, in detail

Your senior's right-hand column: *user hits the URL → SP sends AuthnRequest (EntityID + ACS URL) → IdP*. Here is the full version — the one to be able to narrate aloud in a QnA:

```
 Browser (you)                SP (app.example.com)                IdP (Ping/Okta/Keycloak)
     |                               |                                    |
  1. |--- GET /reports ------------->|                                    |
     |                               | 2. no local session →              |
     |                               |    build AuthnRequest:             |
     |                               |    { ID=_abc123,                   |
     |                               |      Issuer = SP EntityID,         |
     |                               |      ACS URL,                      |
     |                               |      NameIDPolicy }                |
     |                               |    RelayState = "/reports"         |
  3. |<-- 302 redirect to IdP SSO ---|                                    |
     |    ?SAMLRequest=deflate+b64   |                                    |
     |    &RelayState=/reports       |                                    |
  4. |--- GET IdP SSO URL ------------------------------------------------>|
     |                               |         5. validate the request:   |
     |                               |            known Issuer (SP)?      |
     |                               |            ACS URL matches config? |
     |                               |         6. IdP session cookie?     |
     |                               |            NO → login page:        |
     |                               |            password + MFA + policy |
     |                               |            YES → skip (THIS = SSO) |
     |                               |         7. mint the Assertion:     |
     |                               |            NameID, attributes,     |
     |                               |            Conditions, Audience    |
     |                               |            → SIGN (IdP priv key)   |
     |                               |            → encrypt if configured |
     |                               |            wrap in Response        |
     |                               |            (InResponseTo=_abc123)  |
  8. |<-- 200: auto-submitting HTML form  (SAMLResponse b64 + RelayState) -|
  9. |--- POST SAMLResponse ------->| (to the ACS URL)                    |
     |                               | 10. VALIDATE (the big checklist):  |
     |                               |     decode → parse (hardened XML)  |
     |                               |     Status = Success?              |
     |                               |     Issuer = expected IdP?         |
     |                               |     signature vs pinned IdP cert?  |
     |                               |     decrypt assertion (SP priv key)|
     |                               |     Destination/Recipient = my ACS?|
     |                               |     Audience = my EntityID?        |
     |                               |     NotBefore/NotOnOrAfter ± skew? |
     |                               |     InResponseTo = _abc123?        |
     |                               |     assertion ID never seen before?|
     |                               | 11. map NameID → local account,    |
     |                               |     attributes → roles,            |
     |                               |     create SP session cookie       |
 12. |<-- 302 to /reports (RelayState) — logged in                        |
```

**Now the SSO payoff.** Ten minutes later you open a *second* app (`app2.example.com`). Steps 1–5 repeat with app2's AuthnRequest — but at **step 6 the IdP session cookie already exists**, so the IdP skips the login page entirely and goes straight to minting app2's assertion. You never typed a password. **Two sessions per app** exist at all times: the **IdP session** (one, long-lived-ish) and an **SP session per app** (each app's own cookie). That pair of sessions *is* the mechanics of SSO.

Things to be able to say precisely:

- **RelayState** — an opaque value that survives the round trip so the SP can drop you back at the *exact page* you wanted (`/reports`), not the home page. In IdP-initiated mode it's often (ab)used to carry the target app/resource.
- The **AuthnRequest travels via HTTP-Redirect** (deflate → base64 → URL-encode into `?SAMLRequest=`); the **Response travels via HTTP-POST** (base64 in an auto-submitting form) because it's far too big for a URL and carries signatures.
- The IdP and SP **never talk directly** during login (front-channel only) — the browser carries everything. Trust was pre-wired via metadata.

---

## 4. IdP-initiated — the other column on the whiteboard

Your senior's left-hand column: *authentication starts at the IdP*. The user logs into the IdP **portal first** (think Ping/Okta dashboard full of app tiles), then clicks a tile:

```
 Browser                        IdP                              SP
    |                            |                                |
 1. |--- login at IdP portal --->|  (password + MFA)              |
 2. |    click the "App X" tile  |                                |
    |                            | 3. mint assertion for App X    |
    |                            |    wrap in UNSOLICITED Response|
    |                            |    (NO InResponseTo!)          |
 4. |<-- auto-submitting form ---|                                |
 5. |--- POST SAMLResponse --------------------------------------->| (ACS URL)
    |                            |         6. validate & create   |
    |                            |            session             |
```

| | **SP-initiated** | **IdP-initiated** |
|---|---|---|
| User starts at | the **app** (or a deep link) | the **IdP portal / app dock** |
| AuthnRequest? | **Yes** — SP sends it (Issuer = SP EntityID, ACS URL) | **No** — the Response is *unsolicited* |
| `InResponseTo`? | **Present** — binds Response to the request | **Absent** — nothing to bind to |
| Deep links (land on `/reports`)? | Natural — RelayState carries it | Clunky — needs a target parameter (Ping: `TargetResource` on the `startSSO` URL) |
| Security | **Stronger** — response must match an outstanding request | **Weaker** — replay & *login-CSRF* easier (an attacker can push an assertion for *their* account into *your* browser) |
| Where you'll meet it | The default, ~95% of real logins | **Onboarding & testing flows** (your senior's exact note!), legacy portals, "click the tile" UX |
| Best practice | Prefer it | Allow only where needed; many SPs let you disable unsolicited responses |

> **Why "onboarding/testing"?** When you're standing up a new SP connection, IdP-initiated is the fastest smoke test — you can trigger SSO **from the IdP side** without the app's login flow being finished. In PingFederate that's the `…/idp/startSSO.ping?PartnerSpId=<SP EntityID>` URL. First proof of life is usually an IdP-init test, *then* you verify SP-init.
>
> **Defense pairing (repo rule):** the attack enabled by IdP-init is unsolicited-response replay / login-CSRF. Mitigations: prefer SP-init, keep assertion validity short (minutes), enforce one-time assertion IDs, and disable unsolicited SSO on SPs that don't need it.

---

## 5. Reading the messages fast — anatomy + what's truly mandatory

The skill your senior keeps drilling: **look at raw SAML and know instantly what you're seeing.** Two messages to master.

### 5a. Identify the message in 2 seconds

| You see… | It is… | Direction |
|---|---|---|
| `<samlp:AuthnRequest` | the login request | SP → IdP |
| `<samlp:Response` | the answer envelope | IdP → SP |
| `<saml:Assertion` inside it | the security statement itself | (inside the Response) |
| `<saml:EncryptedAssertion` | an assertion you **can't read** without the SP's private key | (inside the Response) |
| `samlp:` vs `saml:` prefix | **protocol** message vs **assertion** vocabulary | two different XML namespaces |

### 5b. AuthnRequest — annotated

```xml
<samlp:AuthnRequest
    ID="_abc123"                      ← REQUIRED. Unique; the Response echoes it in InResponseTo
    Version="2.0"                     ← REQUIRED. Always "2.0"
    IssueInstant="2026-07-06T10:03:00Z"  ← REQUIRED. When this was created (UTC)
    Destination="https://idp.example.com/sso"          ← where it's going (checked if signed)
    AssertionConsumerServiceURL="https://sp.example.com/acs"  ← send the answer HERE
    ProtocolBinding="…:HTTP-POST">    ← …and send it as a POST
  <saml:Issuer>https://sp.example.com/metadata</saml:Issuer>  ← the SP's EntityID (who's asking)
  <samlp:NameIDPolicy Format="…:emailAddress"/>  ← "identify the user to me as an email"
  <!-- optional extras: ForceAuthn="true" (re-login even with a session),
       IsPassive="true" (never show UI), RequestedAuthnContext (demand MFA) -->
</samlp:AuthnRequest>
```

### 5c. Response + Assertion — annotated (the one from tickets)

```xml
<samlp:Response ID="_resp456" Version="2.0" IssueInstant="…"
    InResponseTo="_abc123"        ← ties it to the AuthnRequest (ABSENT in IdP-initiated!)
    Destination="https://sp.example.com/acs">
  <saml:Issuer>https://idp.example.com/metadata</saml:Issuer>   ← IdP's EntityID
  <samlp:Status>
    <samlp:StatusCode Value="…:status:Success"/>   ← READ THIS FIRST. Success or an error code
  </samlp:Status>

  <saml:Assertion ID="_assert789" Version="2.0" IssueInstant="…">
    <saml:Issuer>https://idp.example.com/metadata</saml:Issuer>  ← IdP's EntityID again
    <ds:Signature>…</ds:Signature>                 ← the hologram (see §6)

    <saml:Subject>                                 ← ★ the WHO — your senior's "mandatory part"
      <saml:NameID Format="…:emailAddress">farhaan@finco.com</saml:NameID>
      <saml:SubjectConfirmation Method="…:cm:bearer">      ← "whoever carries this, within limits"
        <saml:SubjectConfirmationData
            Recipient="https://sp.example.com/acs"          ← must equal the ACS it landed on
            NotOnOrAfter="2026-07-06T10:08:05Z"             ← bearer window (short!)
            InResponseTo="_abc123"/>                        ← again ties to the request
      </saml:SubjectConfirmation>
    </saml:Subject>

    <saml:Conditions NotBefore="…" NotOnOrAfter="…">        ← validity window (clock skew lives here)
      <saml:AudienceRestriction>
        <saml:Audience>https://sp.example.com/metadata</saml:Audience>  ← SP's EntityID — FOR this app only
      </saml:AudienceRestriction>
    </saml:Conditions>

    <saml:AuthnStatement AuthnInstant="…" SessionIndex="_sess999">
      <saml:AuthnContextClassRef>…PasswordProtectedTransport</saml:AuthnContextClassRef>  ← HOW they authed
    </saml:AuthnStatement>

    <saml:AttributeStatement>                      ← the claims: what the SP learns about you
      <saml:Attribute Name="email">…</saml:Attribute>
      <saml:Attribute Name="groups">…</saml:Attribute>
    </saml:AttributeStatement>
  </saml:Assertion>
</samlp:Response>
```

### 5d. Mandatory vs optional — the senior-level nuance

There are **three levels of "mandatory,"** and knowing the difference is exactly what separates a junior answer from a senior one:

1. **Schema-mandatory** — the XML is *invalid* without it (per SAML-core).
2. **Profile-mandatory** — required by the **Web Browser SSO profile** (the rules every real SSO deployment follows).
3. **Practically mandatory** — the spec technically allows omitting it, but no real SP works without it.

| Field | Level | Notes |
|---|---|---|
| `ID`, `Version`, `IssueInstant` (on Request, Response, *and* Assertion) | **Schema** | The three attributes present on *everything*. Instant answer, know it cold. |
| `<Status>` in the Response | **Schema** | Always read it first. |
| `<Issuer>` in the Assertion | **Schema** | Who minted it. |
| `<Issuer>` in the AuthnRequest | **Profile** | Web SSO requires the SP to identify itself. |
| **`<Subject>` with `NameID`** | **Profile / practical** | ★ Your senior's headline: the Subject with its **NameID — the unique identifier for the user** — must be there **irrespective of what else the Response contains**. It's the *who*; without it the SP can't map a session to an account. |
| `SubjectConfirmation Method="bearer"` + `Recipient` + `NotOnOrAfter` | **Profile** | Web SSO demands bearer confirmation with these fields. |
| `<Conditions>` with `<AudienceRestriction>` | **Profile** | The assertion must name its audience. |
| At least one `<AuthnStatement>` | **Profile** | It's an *authentication* assertion, after all. |
| A signature on the **Assertion or the Response** | **Profile** | For HTTP-POST, at least one of them MUST be signed. |
| `<AttributeStatement>` | **Optional** | Common, but a bare NameID login is legal. |
| `InResponseTo` | **Conditional** | Must be present *and match* in SP-init; absent in IdP-init. Its presence tells you which flow you're looking at! |

### 5e. The 60-second speed-read drill (say it out loud)

When someone drops a decoded Response in front of you, walk it in this order:

1. **Status** → "Is it even a success?" (If not, the error code tells you which side to blame: `Requester` vs `Responder`.)
2. **Response `Issuer`** → "Which IdP minted this?"
3. **`InResponseTo` present?** → "SP-initiated (bound to a request) or IdP-initiated (unsolicited)?"
4. **`Assertion` or `EncryptedAssertion`?** → "Can I read it, or do I need the SP's key?"
5. **Subject → NameID** → "*Who* is this, and in which format (email? opaque persistent ID?)"
6. **Conditions → Audience** → "Which SP is this *for*? Does it match the app in the ticket?"
7. **NotBefore / NotOnOrAfter** → "Is it inside the window right now? Borderline = clock skew."
8. **SubjectConfirmationData → Recipient** → "Does it equal the ACS URL it was actually posted to?"
9. **Signature** → "Signed at which level — Response, Assertion, both? Against which cert?"
10. **AttributeStatement** → "What does the app learn — and is what it *needs* actually in here?"

That ordering mirrors what the SP's own validation does — which is why it finds the failure fast.

---

## 6. Certificates — how two parties that never met come to trust each other

Your senior's bracket: **Certificates → ① Signature (verify authenticity) ② Encryption (conceal information)**. Two jobs, two keypairs, and — the part that trips everyone — the **direction flips**.

### 6a. Job 1 — Signing (authenticity + integrity)

- The **IdP** holds a **private key**; its **public certificate** was handed to the SP during onboarding (inside metadata).
- The IdP computes a digest (hash) of the assertion, signs the digest with its private key, and embeds the result as `<ds:Signature>` *inside* the XML (the signature's `Reference URI="#_assert789"` points at exactly which element is covered).
- The SP recomputes the hash, then verifies the signature using the **pinned certificate from metadata**. Any changed byte → hash mismatch → rejected.
- **Golden rule:** verify against the *pre-configured* cert, **never** against a cert embedded in the message itself (`<KeyInfo>`). Trusting embedded certs = attacker signs with their own key and walks in.

**What signing gives you:** *authenticity* ("really from this IdP") + *integrity* ("not modified in transit"). **What it doesn't give you:** secrecy — a signed assertion is fully readable.

### 6b. Job 2 — Encryption (confidentiality) — note the flip!

- Encryption uses the **SP's** keypair, not the IdP's. The SP's **public certificate** (also from metadata) is used by the **IdP to encrypt**; only the SP's **private key can decrypt**.
- Mechanically it's **hybrid encryption** (XML-Encryption): a random **AES session key** encrypts the assertion XML; that AES key is itself encrypted with the SP's **RSA public key** and shipped alongside (`<xenc:EncryptedKey>`). Fast symmetric crypto for the payload, asymmetric crypto to deliver the key.
- Order of operations: the IdP **signs first, then encrypts**. The SP **decrypts first, then verifies** the signature inside.

### 6c. Memory table — whose key does what

| Operation | Uses **whose** keypair | Private key does | Public cert does |
|---|---|---|---|
| **Signing** the assertion | the **IdP's** | signs (at the IdP) | verifies (at the SP) |
| **Encrypting** the assertion | the **SP's** | decrypts (at the SP) | encrypts (at the IdP) |

*Mnemonic: you **sign with your own** hand; you **encrypt for the reader** — with the reader's lock.*

### 6d. The trust model (surprisingly *not* the browser PKI)

SAML certs are usually **self-signed** and that's fine — trust does **not** come from a CA chain like HTTPS. It comes from the **explicit metadata exchange at onboarding**: "here is my exact certificate; pin it." The metadata swap *is* the trust ceremony. (Some stacks still enforce the cert's expiry date, so rotation remains a real operational duty.)

**Cert rotation** is the classic outage: the IdP's signing cert expires → *every* SP fails signature validation *simultaneously* → "SSO is down" for the whole company. Mature setups (Ping included) support a **primary + secondary signing cert** so SPs can trust the new cert *before* the old one retires — zero-downtime rotation. In a fintech, cert expiry dates are tracked like production incidents waiting to happen — because they are.

---

## 7. Encrypting the Response vs encrypting the Assertion — the precise version

Your notebook says: *"conceals all information of Response … encrypt only assertion."* Here's the precise picture — worth getting exactly right, because it's a favorite senior probe:

**What XML-encryption can be applied to (standard granularities):**

| What's encrypted | XML shape | When you'd use it |
|---|---|---|
| **Nothing** (sign-only) | `<Response> … <Assertion>` — everything readable | Most common baseline. TLS protects the pipe; signature protects integrity. |
| **The Assertion** | `<Response> … <EncryptedAssertion><xenc:EncryptedData…>` | The standard "encrypt it" option. Hides *the entire assertion* — NameID, attributes, conditions — from the browser and anything between. The Response envelope (Status, Destination, Issuer) stays readable. |
| **Just the NameID** | `<EncryptedID>` inside Subject | Privacy of the identifier alone (rare). |
| **Individual attributes** | `<EncryptedAttribute>` | Selective secrecy (rare). |

Two precision points that make you sound like you've actually read the spec:

1. **There is no "EncryptedResponse" element in SAML.** When people say "the *Response* is encrypted," 99% of the time they mean **the assertion inside it is** (`<EncryptedAssertion>`) — the outer envelope always stays readable, because the SP needs to route and read Status before decrypting. (The other 1% mean TLS, which is a different layer — see §8.)
2. **Encryption is about the browser, not the network.** TLS already encrypts the network. XML-encryption exists because the SAML Response takes a layover *inside the user's browser* (front-channel POST). Without `<EncryptedAssertion>`, the user — or a malicious browser extension, or anything logging the POST body — can base64-decode and read every attribute. If assertions carry PII (at a fintech: employee IDs, roles touching payment systems), encrypting the assertion closes that exposure.

**In Ping terms:** the SP connection has a *signing certificate* setting (whose key signs) and an optional *encryption certificate* (the SP's public cert used to encrypt the assertion). "Sign the Response, sign the Assertion, encrypt the Assertion" are three independent switches — know which combination a connection uses before debugging it.

---

## 8. What's encrypted and what's not — the full map

The question your senior saved for last, because it tests whether you see the **layers**:

**Layer 1 — TLS (transport).** Every hop is HTTPS: browser↔IdP and browser↔SP. On the wire, *everything* is encrypted. But TLS **terminates at each endpoint** — decrypted at the browser, re-encrypted onward. TLS protects the *pipe*, never the *parcel*.

**Layer 2 — XML signature (message integrity).** Not encryption at all! A signed assertion is fully **readable** — signature makes it *tamper-evident*, like a hologram sticker on a transparent envelope.

**Layer 3 — XML encryption (message confidentiality).** Only if configured. This is what actually hides content from the browser itself.

| Item | Readable by the user/browser? | Signed? | Encrypted (message level)? |
|---|---|---|---|
| **AuthnRequest** | ✅ Yes (deflate+base64 = trivially decoded) | Sometimes (Redirect binding: signature in URL params) | ❌ Practically never |
| **Response envelope** (Status, Issuer, Destination) | ✅ Yes | Often | ❌ (no such element) |
| **Assertion** | ✅ Yes, **unless** `<EncryptedAssertion>` | ✅ Should always be | 🔒 Optional — the real "encrypt" switch |
| **NameID / attributes** | Follow the assertion | (covered by its signature) | 🔒 Optionally, individually |
| **RelayState** | ✅ Yes (opaque but visible) | ❌ | ❌ |
| **IdP & SP session cookies** | Browser-held | n/a | TLS-only (mark them `Secure`, `HttpOnly`) |
| **Everything on the wire** | ❌ (TLS) | n/a | ✅ TLS, hop by hop |

**The line to remember:** ***base64 is encoding, not encryption.*** Anyone who captures a SAML Response (SAML-tracer, browser devtools, a proxy log) reads it in full — unless the assertion itself was encrypted. Say that sentence in a QnA and watch the senior nod.

> **Defense pairing:** the "attack" here is simple *disclosure* — assertions with PII sitting in browser history, proxy logs, or support-ticket screenshots. Mitigations: encrypt assertions carrying sensitive attributes, minimize attributes to what the SP truly needs (least privilege applies to *claims* too), and never paste raw production assertions into tickets/chat (use the lab, or redact).

---

## 9. Pulling it together — why this design achieves SSO

1. **One authentication** at the IdP → an **IdP session** is born (cookie at the IdP domain).
2. Each app delegates login to that same IdP; because the IdP session already exists, each subsequent app costs **zero prompts** — the IdP silently mints a fresh, short-lived, app-scoped assertion.
3. Each SP consumes its assertion, then maintains **its own session** — the assertion is a *5-minute entry ticket*, not the session itself.
4. Certificates + metadata make this work **across organizations** (federated SSO) with no shared passwords, no direct network path between IdP and SP, and cryptographic proof at every step.

The elegance: the **SP never sees a password**, the **IdP never learns what you did inside the app**, and the *only* thing crossing the boundary is a signed, time-boxed, audience-restricted XML statement.

---

## 10. What you learned

- **SSO vs federation** — one login vs cross-domain trust; SAML delivers both (§1).
- **EntityID** — the passport number; three load-bearing locations: request Issuer, response/assertion Issuer, Audience (§2).
- **SP-initiated end to end** — all 12 hops, two sessions (IdP + SP), RelayState, and where every validation happens (§3).
- **IdP-initiated** — unsolicited Response, no `InResponseTo`, great for onboarding/testing, weaker vs replay/login-CSRF (§4).
- **Speed-reading assertions** — the 2-second identify table, the annotated anatomy, three levels of "mandatory," and the 60-second drill; **Subject → NameID is the non-negotiable *who*** (§5).
- **Certificates** — signing (IdP's keys, authenticity) vs encryption (SP's keys, confidentiality); the direction flip; metadata pinning, not CA chains; rotation as the classic outage (§6).
- **Encryption scope** — there's no EncryptedResponse; the real options are EncryptedAssertion / EncryptedID / EncryptedAttribute; encryption defends against the *browser*, TLS defends the *wire* (§7–8).

## Next

- **Drill it:** [**note 14 — the SAML question bank**](14-saml-question-bank.md) — the QnA your senior could run, easy → very hard, with model answers.
- **Hands-on:** re-run [Lab 02](../labs/02-saml-assertion-anatomy/README.md), but this time narrate the 60-second drill out loud over the decoded response.
- **Compare:** [OAuth/OIDC deep dive](03-oauth-oidc-deep-dive.md) — same job, JSON instead of XML; interviewers love "SAML vs OIDC."

*— Janus 🔐, from your senior's whiteboard session of 6 July 2026*
