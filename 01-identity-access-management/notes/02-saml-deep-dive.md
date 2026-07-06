# SAML 2.0 — from scratch, for the person who has to debug it

> **Janus's deep dive.** SAML is the protocol you'll debug most in enterprise IAM tickets. It's old (2005), verbose (XML), and full of sharp edges — clock skew, certificates, attribute mapping — that generate a steady stream of support tickets. Learn to *read an assertion* and you can solve most of them. Prereq: [the landscape note](01-iam-protocol-landscape.md). Hands-on: [Lab 02 — SAML assertion anatomy](../labs/02-saml-assertion-anatomy/README.md).

---

## 1. What SAML is, in one breath

**SAML (Security Assertion Markup Language) 2.0** is an XML-based standard for **browser-based Single Sign-On between organizations**. It lets an **Identity Provider (IdP)** make a signed statement — an **assertion** — that says *"I authenticated this user; here are their attributes,"* and a **Service Provider (SP)** trust that statement instead of asking for a password itself.

It is **authentication + federation**, carried over a **web browser** (front-channel), secured by **XML digital signatures**.

You meet SAML whenever a company logs its employees into a third-party or enterprise app: Workday, Salesforce, ServiceNow, SAP, Concur, thousands of B2B SaaS apps, and plenty of internal apps built before ~2015.

---

## 2. The three actors

| Actor | Also called | Role |
|---|---|---|
| **Principal** | Subject, the user | The human (you, Farhaan) trying to reach an app |
| **Identity Provider (IdP)** | Asserting party | Authenticates the user and issues the signed assertion. *Okta, Entra ID, Keycloak, Ping, ADFS.* |
| **Service Provider (SP)** | Relying party | The app the user wants. Trusts the IdP's assertion. *Salesforce, Workday, your internal app.* |

Everything in SAML is a message passing between these three, **through the user's browser**. The IdP and SP usually never talk to each other directly during login — the browser is the courier. (Trust is pre-established out-of-band via metadata + certificates; see §7.)

---

## 3. The flow you'll see 95% of the time: SP-initiated SSO

The user starts at the app. This is the common case.

```
  Browser (Farhaan)            SP (Salesforce)              IdP (Okta/Entra)
        |                            |                            |
   1.   |---- GET /some-page ------->|                            |
        |                            | (no session — start SSO)   |
   2.   |<-- redirect w/ SAML -------|                            |
        |    AuthnRequest            |                            |
        |                            |                            |
   3.   |------ AuthnRequest -------------------------------------->|
        |    (via browser redirect)                               |
        |                            |          4. authenticate:  |
        |                            |          password + MFA,   |
        |                            |          check policy,      |
        |                            |          look up directory  |
        |                            |                            |
   5.   |<-- HTML form w/ signed SAML Response (auto-POSTs) -------|
        |                            |                            |
   6.   |--- POST SAML Response ---->|                            |
        |    to the ACS URL          | 7. VALIDATE:               |
        |                            |    - signature (IdP cert)  |
        |                            |    - Audience = me?        |
        |                            |    - NotBefore/NotOnOrAfter|
        |                            |    - InResponseTo matches  |
        |                            |    - not replayed          |
   8.   |<---- set session cookie ---| (create local session)     |
        |      Farhaan is logged in  |                            |
```

Key vocabulary from this diagram:
- **AuthnRequest** — the SP's message: *"please authenticate this user for me."*
- **ACS (Assertion Consumer Service) URL** — the SP endpoint that *receives* the SAML Response. You will configure this constantly. Wrong ACS URL = a huge fraction of SAML tickets.
- **SAML Response** — the IdP's reply, which *contains* the **Assertion**.
- **RelayState** — an opaque parameter that carries "where the user was trying to go" through the round trip, so the SP can bounce them back to the original page after login.

**IdP-initiated SSO** is the other variant: the user starts *at the IdP* (e.g., clicks a Salesforce tile in the Okta dashboard). Then steps 1–3 are skipped — the IdP POSTs an unsolicited Response straight to the SP's ACS. It's convenient but **less secure** (no `InResponseTo` to bind the response to a request → easier to replay/CSRF), and modern best practice prefers SP-initiated. Knowing which mode an app uses is often the first debugging question.

---

## 4. The two messages, decoded

### 4a. The AuthnRequest (SP → IdP)

Sent via **HTTP-Redirect binding**: XML is *deflated → base64 → URL-encoded* and put in a `?SAMLRequest=` query parameter. That's why you can't read it by eye — you must inflate+decode it (Lab 02 shows how).

```xml
<samlp:AuthnRequest
    xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
    xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
    ID="_abc123"                    <!-- unique; the Response must echo this in InResponseTo -->
    IssueInstant="2026-07-05T10:03:00Z"
    Destination="https://idp.example.com/sso"           <!-- must match the IdP's SSO URL -->
    AssertionConsumerServiceURL="https://sp.example.com/acs"   <!-- where to send the answer -->
    ProtocolBinding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST">
  <saml:Issuer>https://sp.example.com/metadata</saml:Issuer>   <!-- who I am (SP entityID) -->
  <samlp:NameIDPolicy Format="urn:oasis:names:tc:SAML:2.0:nameid-format:emailAddress"/>
</samlp:AuthnRequest>
```

### 4b. The SAML Response + Assertion (IdP → SP) — *this is the one you'll stare at in tickets*

Sent via **HTTP-POST binding**: base64-encoded XML in a hidden form field the browser auto-submits to the ACS URL.

```xml
<samlp:Response ID="_resp456" InResponseTo="_abc123"        <!-- echoes the request ID -->
    Destination="https://sp.example.com/acs" IssueInstant="2026-07-05T10:03:05Z" ...>
  <saml:Issuer>https://idp.example.com/metadata</saml:Issuer>
  <samlp:Status>
    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>  <!-- SUCCESS or an error -->
  </samlp:Status>

  <saml:Assertion ID="_assert789" IssueInstant="2026-07-05T10:03:05Z" ...>
    <saml:Issuer>https://idp.example.com/metadata</saml:Issuer>

    <ds:Signature> ...digital signature over the assertion... </ds:Signature>   <!-- the "hologram" -->

    <saml:Subject>
      <saml:NameID Format="...emailAddress">farhaan@finco.com</saml:NameID>   <!-- WHO this is -->
      <saml:SubjectConfirmation Method="...cm:bearer">
        <saml:SubjectConfirmationData
            NotOnOrAfter="2026-07-05T10:08:05Z"          <!-- this bearer assertion expires fast -->
            Recipient="https://sp.example.com/acs"       <!-- must equal my ACS URL -->
            InResponseTo="_abc123"/>                     <!-- ties it to my request -->
      </saml:SubjectConfirmation>
    </saml:Subject>

    <saml:Conditions NotBefore="2026-07-05T10:02:35Z"      <!-- CLOCK SKEW lives here -->
                     NotOnOrAfter="2026-07-05T10:08:05Z">
      <saml:AudienceRestriction>
        <saml:Audience>https://sp.example.com/metadata</saml:Audience>   <!-- assertion is FOR this SP only -->
      </saml:AudienceRestriction>
    </saml:Conditions>

    <saml:AuthnStatement AuthnInstant="2026-07-05T10:03:04Z" SessionIndex="_sess999">
      <saml:AuthnContext>
        <saml:AuthnContextClassRef>
          urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport   <!-- HOW they authed -->
        </saml:AuthnContextClassRef>
      </saml:AuthnContext>
    </saml:AuthnStatement>

    <saml:AttributeStatement>                              <!-- the "claims": what the SP learns -->
      <saml:Attribute Name="email">
        <saml:AttributeValue>farhaan@finco.com</saml:AttributeValue>
      </saml:Attribute>
      <saml:Attribute Name="groups">
        <saml:AttributeValue>iam-team</saml:AttributeValue>
        <saml:AttributeValue>payments-ro</saml:AttributeValue>
      </saml:Attribute>
    </saml:AttributeStatement>
  </saml:Assertion>
</samlp:Response>
```

**Memorize these five fields — they cause most tickets:**

| Field | What it means | Ticket it causes when wrong |
|---|---|---|
| `<Issuer>` / **entityID** | Unique name of IdP or SP | "Unknown issuer" / "SP not configured" |
| `Recipient` / **ACS URL** | Where the response is delivered | Response rejected / lands nowhere |
| `<Audience>` | Which SP this assertion is *for* | "Audience mismatch" / "not intended for this SP" |
| `NotBefore` / `NotOnOrAfter` | Validity window (usually ~5 min) | **"Assertion not yet valid / expired"** = clock skew (see §8) |
| `<AttributeStatement>` | The user's attributes/groups | User logs in but has **no permissions** (attribute mapping wrong) |

---

## 5. Bindings — *how* the XML travels

A "binding" is the transport mechanism. Three you should know:

- **HTTP-Redirect** — XML compressed into a URL query string. Used for the (small) AuthnRequest. Length-limited.
- **HTTP-POST** — base64 XML in an auto-submitting HTML form. Used for the (large, signed) Response. The workhorse.
- **HTTP-Artifact** — the browser carries only a small reference ("artifact"); the SP then makes a **back-channel** SOAP call to the IdP to fetch the real assertion. More secure (assertion never in the browser) but more complex; less common.

---

## 6. Signing & encryption — the trust anchor

SAML's security rests on **XML Digital Signature**. The IdP signs with its **private key**; the SP verifies with the IdP's **public certificate** (shared in advance via metadata). This is the "hologram on the passport."

- **What gets signed?** The IdP can sign the *whole Response*, the *Assertion*, or both. **Best practice: sign the Assertion** (and ideally the Response too). The *Assertion* is the security-critical part — if only the outer Response is signed but the SP trusts an inner unsigned assertion, you get attacks (§9).
- **Encryption (optional):** the assertion can also be *encrypted* to the SP's public key (`<EncryptedAssertion>`), so intermediaries (including the browser/user) can't read the attributes. Signing proves *authenticity*; encryption provides *confidentiality*. They're independent.
- **Certificate expiry is a top-3 SAML outage cause.** IdP signing certs expire (often yearly). When they do, *every* SP silently breaks at once until the new cert is rolled out. Fintech shops track cert expiry like a hawk. (See [../../04-cryptography/README.md](../../04-cryptography/README.md) for the signing/PKI fundamentals.)

---

## 7. Metadata — the federation "contract"

Before any SSO works, IdP and SP **exchange metadata** — an XML document that declares:
- **entityID** (the party's unique name)
- endpoints: **SSO URL** (IdP), **ACS URL** (SP), **SLO URL** (logout)
- **signing certificate(s)** (public keys)
- supported **NameID formats** and bindings

Setting up a new SAML app = exchanging metadata + mapping attributes. "Federation setup" is largely *"give me your metadata, here's mine, let's agree on attributes."* When metadata drifts (a cert rotates, a URL changes) and isn't re-shared, SSO breaks — a classic ticket.

---

## 8. Clock skew — the #1 sneaky SAML ticket

The assertion is valid only between `NotBefore` and `NotOnOrAfter` (~5-minute window). Validation uses **each server's own clock**. If the IdP's and SP's clocks disagree by more than the allowed skew (often ±3–5 min), the SP sees the assertion as **"not yet valid"** or **"expired"** and rejects a perfectly good login.

- **Root cause:** unsynchronized clocks (NTP drift), or a VM that paused/resumed.
- **Fix:** ensure **NTP** on both sides; SPs allow a small configurable *clock skew tolerance*.
- **Why you should care:** it produces intermittent, hard-to-reproduce "sometimes login fails" tickets. Recognizing the pattern instantly makes you look sharp.

---

## 9. Attacks & defenses (always pair them — CLAUDE.md rule)

| Attack | What it does | Defense |
|---|---|---|
| **XML Signature Wrapping (XSW)** | Attacker adds a second, forged assertion and tricks the SP into validating the signature on the *real* one but reading attributes from the *forged* one | Use a hardened SAML library; validate that the signed element is exactly the one processed; schema-hardening; reject multiple assertions |
| **Unsigned / partially-signed assertion accepted** | SP accepts an assertion whose signature is missing or only covers the outer Response | Require the **Assertion** itself to be signed; reject unsigned assertions |
| **Assertion replay** | Attacker resends a captured (still-valid) assertion | Enforce short `NotOnOrAfter`, one-time-use (cache assertion IDs), require `InResponseTo` |
| **Recipient/Audience not checked** | Assertion minted for SP-A is replayed to SP-B | Strictly validate `Audience` and `Recipient` against your own entityID/ACS |
| **XXE (XML External Entity)** | Malicious XML entity in the SAML message reads files / SSRFs the SP | Disable external entity resolution in the XML parser |
| **Open redirect via RelayState** | Attacker stuffs a malicious URL into RelayState to redirect the user post-login | Validate/allow-list RelayState targets |
| **`KeyInfo` / cert injection** | Attacker supplies their own cert in the message and signs with it | Verify signature against the **pre-configured** IdP cert from metadata, *never* a cert embedded in the message |

> Purple-team habit (per your repo's working principles): after seeing an attack, ask **Heimdall** *"what would we detect?"* (e.g., SIEM alert on multiple assertions, signature-validation failures, or logins from a new IdP cert) and **Loki** to walk the XSW mechanics on your **local Keycloak lab only**.

---

## 10. Single Logout (SLO) — why it's always broken

SSO logs you into many apps from one session. **SLO** tries to log you out of all of them at once — the IdP notifies every SP to kill its session. In practice SLO is fragile (some SPs don't implement it, back-channel calls fail silently, sessions linger). Expect "logout didn't actually log me out" tickets. Know that SLO is *best-effort*, not guaranteed.

---

## 11. SAML vs OIDC — the 30-second contrast (bridge to the next note)

| | SAML 2.0 | OIDC |
|---|---|---|
| Era / format | 2005, **XML** | 2014, **JSON / JWT** |
| Transport | Browser POST/redirect, XML-DSig | HTTPS + JWT signatures (JOSE) |
| Best for | Enterprise SaaS, B2B, legacy | Modern web, **mobile**, SPAs, APIs |
| Token | `<Assertion>` | **ID token** (JWT) |
| Discovery | Metadata XML | `/.well-known/openid-configuration` |
| Pain points | Clock skew, cert rotation, XML | Redirect URI validation, token storage |

Both do the *same job* (federated authentication); OIDC is the modern, lighter-weight successor. Many enterprises run **both** for years. Continue in [the OAuth/OIDC deep dive](03-oauth-oidc-deep-dive.md).

---

## 12. Tools you'll actually use

- **SAML-tracer** (Firefox/Chrome extension) — captures and pretty-prints SAML messages live in the browser. *Your #1 debugging tool.* Used in [Lab 02](../labs/02-saml-assertion-anatomy/README.md).
- **base64 + inflate** — decode `SAMLRequest`/`SAMLResponse` by hand (Lab 02 shows the PowerShell one-liners).
- **xmlsec1 / a SAML library** — verify signatures.
- Public test services: **mocksaml.com**, **samltest.dev**, **sptest.iamshowcase.com** — safe places to generate real assertions.
- **jwt.io** is for JWT/OIDC, **not** SAML — don't mix them up.

---

## 13. The 60-second SAML debugging checklist (tape this to your monitor)

1. **SP-init or IdP-init?** Where does the user start?
2. **Capture** the SAML Response with SAML-tracer; base64-decode it.
3. **Status code** — Success, or an error like `Responder` / `AuthnFailed`?
4. **Signature** — present on the Assertion? Verifies against the configured IdP cert? Cert expired?
5. **Audience** — matches the SP's entityID?
6. **Recipient / ACS URL** — matches where it was delivered?
7. **NotBefore / NotOnOrAfter** — inside the window? (If borderline → **clock skew**, check NTP.)
8. **InResponseTo** — matches the AuthnRequest ID? (SP-init only.)
9. **Attributes** — are the expected email/groups present and named as the SP expects? (User logs in but no access → this.)

Master this list and you'll close most SAML tickets without escalating. Now go **decode a real one** in [Lab 02](../labs/02-saml-assertion-anatomy/README.md).

*— Janus 🔐*
