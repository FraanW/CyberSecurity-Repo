# SAML Question Bank — from "warm-up" to "senior Ping expert"

> **Janus's QnA drill.** Everything a senior — especially a **Ping stack expert** — could reasonably grill you on in a SAML deep-dive session. Ordered **easy → very hard**, every question with a **model answer** hidden in a spoiler so you can self-test: *read the question, answer out loud, then expand to check.*
>
> **Prereqs:** [SAML deep dive #1](02-saml-deep-dive.md) and [SAML mastery session 2](13-saml-mastery-session2.md). **How to use:** cover the answers, go tier by tier, and say answers *aloud* — interviews are spoken, not written.

---

## How this bank is organized

| Tier | Level | What it tests |
|---|---|---|
| **1** | Warm-up (easy) | Definitions, the three actors, "what is SSO" — the stuff you must never fumble |
| **2** | Core (medium) | Flow, assertion anatomy, mandatory fields, EntityID, SP-init vs IdP-init, SSO vs federation |
| **3** | Hard | Certificates, signing vs encryption, what's encrypted, clock skew, validation order, SLO |
| **4** | Very hard / expert | Attacks & defenses, Ping-specific mechanics, design trade-offs, spec edge cases |
| **R** | Rapid-fire | One-line answers to drill until they're reflex |
| **S** | Scenarios | "Here's a ticket — diagnose it." The real job |
| **↩** | Reverse | Sharp questions *you* ask the senior — engagement points |

**Legend in answers:** ★ = say-this-and-you-sound-senior · ⚠ = common wrong answer to avoid.

---

## Tier 1 — Warm-up (easy)

**Q1.1 — What does SAML stand for, and in one sentence, what is it for?**

<details><summary>Model answer</summary>

**Security Assertion Markup Language.** It's an **XML-based standard for browser-based Single Sign-On**, letting an **Identity Provider** make a **signed statement** ("I authenticated this user; here are their attributes") that a **Service Provider** trusts instead of collecting a password itself. ★ Add: "It's *authentication + federation*, carried through the browser, secured by XML digital signatures — 2005-era, so XML and verbose."
</details>

**Q1.2 — Name the three actors in a SAML flow.**

<details><summary>Model answer</summary>

1. **Principal** (the user/subject) — the human trying to reach an app.
2. **Identity Provider (IdP)** — authenticates the user, issues the signed assertion (Ping, Okta, Entra, Keycloak, ADFS).
3. **Service Provider (SP)** — the app the user wants; trusts the IdP's assertion (Workday, Salesforce, an internal app).

★ "And the **browser is the courier** — the IdP and SP normally never talk directly during login."
</details>

**Q1.3 — What is Single Sign-On (SSO) in plain terms?**

<details><summary>Model answer</summary>

**Log in once, then reach many apps without logging in again.** The mechanism: after your first login the **IdP holds a session** (a cookie at the IdP), so every later app gets a fresh assertion silently — no new password prompt. ★ "SSO is the *experience*; SAML/OIDC is the *machinery* that delivers it."
</details>

**Q1.4 — Is SAML authentication or authorization?**

<details><summary>Model answer</summary>

Primarily **authentication** (proving *who* you are) plus **federation**. It *can* carry authorization data as **attributes** (groups/roles in the `AttributeStatement`), which the SP uses for its own access decisions — but the protocol's core job is authN. ⚠ Don't say "it's like OAuth" — OAuth is authorization/delegated access, a different job.
</details>

**Q1.5 — What's the SAML equivalent of OIDC's ID token?**

<details><summary>Model answer</summary>

The **`<Assertion>`** — specifically an assertion carrying an `AuthnStatement`. Both are the signed "proof of authentication" the SP consumes. (SAML = XML assertion; OIDC = JWT ID token — same job, different format.)
</details>

**Q1.6 — What file do two parties exchange to set up a SAML trust, and what's in it?**

<details><summary>Model answer</summary>

**Metadata** (an XML document — the "business card"). It declares the **EntityID**, the **endpoints** (IdP SSO URL, SP ACS URL, SLO URL), the **signing/encryption certificates** (public keys), and supported **NameID formats & bindings**. ★ "Setting up a new SAML app is basically *'exchange metadata, agree on attributes.'*"
</details>

---

## Tier 2 — Core (medium)

**Q2.1 — Walk me through an SP-initiated SSO flow, end to end.**

<details><summary>Model answer</summary>

1. User hits a protected URL at the **SP**.
2. SP has no session → builds an **AuthnRequest** (its EntityID as Issuer, its ACS URL, a NameIDPolicy, a unique `ID`), sets **RelayState** = where the user wanted to go.
3. SP **redirects** the browser to the IdP's SSO URL with `?SAMLRequest=` (deflate+base64+URL-encoded).
4. Browser delivers it to the **IdP**.
5. IdP validates the request (known Issuer? ACS matches config?); if no IdP session, it **authenticates** the user (password + MFA + policy). If a session already exists, it skips this — *that's the SSO moment.*
6. IdP **mints the assertion** (NameID, attributes, Conditions/Audience), **signs** it (its private key), optionally **encrypts** it (SP's public key), wraps it in a **Response** with `InResponseTo` = the request ID.
7. IdP returns an **auto-submitting HTML form** (HTTP-POST binding) → browser POSTs the `SAMLResponse` to the SP's **ACS URL**.
8. SP **validates** (signature, Status, Issuer, Audience, Recipient, time window, InResponseTo, replay), maps NameID→account and attributes→roles, and **creates its own session cookie**.
9. SP redirects the user to the original page (from RelayState).

★ Emphasize the **two sessions**: one long-ish **IdP session** + one **SP session per app**. That pair is the engine of SSO.
</details>

**Q2.2 — SP-initiated vs IdP-initiated: what's the concrete difference?**

<details><summary>Model answer</summary>

| | SP-initiated | IdP-initiated |
|---|---|---|
| User starts at | the **app** | the **IdP portal** (clicks an app tile) |
| **AuthnRequest** | Yes (SP sends it) | **No** — the Response is *unsolicited* |
| **`InResponseTo`** | Present, must match the request | **Absent** (nothing to match) |
| Deep-linking | Natural via RelayState | Needs a target parameter |
| Security | Stronger (response bound to a request) | Weaker (replay / login-CSRF easier) |
| Typical use | ~95% of real logins | **Onboarding & testing**, legacy portals, "click-the-tile" UX |

★ "The single fastest tell is **`InResponseTo`**: present = SP-init, absent = IdP-init."
</details>

**Q2.3 — What are the mandatory fields of a SAML Response/Assertion? What's *the* one that must always be there?**

<details><summary>Model answer</summary>

The headline: **`<Subject>` with a `<NameID>`** — the **unique identifier for the user**, present **irrespective of whatever else** the Response carries. Without it the SP can't map the login to an account.

Then, by level:
- **Schema-mandatory:** `ID`, `Version`, `IssueInstant` on the Response *and* Assertion; a `<Status>` in the Response; an `<Issuer>` on the Assertion.
- **Profile-mandatory (Web Browser SSO):** `<Issuer>` on the AuthnRequest; `<Subject>`+`NameID`; a bearer `<SubjectConfirmation>` with `Recipient` + `NotOnOrAfter`; `<Conditions>` with `<AudienceRestriction>`; at least one `<AuthnStatement>`; and a **signature on the Assertion or the Response**.
- **Optional:** `<AttributeStatement>` (common, but a bare-NameID assertion is legal).

★ Naming the **three levels** (schema vs profile vs practical) is what separates a junior from a senior answer.
</details>

**Q2.4 — How do you tell an AuthnRequest from a Response just by looking?**

<details><summary>Model answer</summary>

- `<samlp:AuthnRequest>` → the **login request**, SP→IdP.
- `<samlp:Response>` → the **answer envelope**, IdP→SP; it *contains* a `<saml:Assertion>` (or `<saml:EncryptedAssertion>` if you can't read it).
- Namespace prefix tells you the layer: **`samlp:`** = protocol messages; **`saml:`** = assertion vocabulary.

★ "And `<EncryptedAssertion>` means I need the SP's private key to read it."
</details>

**Q2.5 — What is the ACS URL and why does it cause so many tickets?**

<details><summary>Model answer</summary>

**Assertion Consumer Service URL** — the SP endpoint that *receives* the SAML Response (the POST lands here). It's declared in SP metadata and echoed in the assertion's `Recipient`. It causes tickets because a wrong/typo'd/changed ACS means the response is delivered nowhere, or the SP rejects it because `Recipient` ≠ its configured ACS. ★ "It's one of the two things the SP puts in its AuthnRequest — EntityID and ACS URL."
</details>

**Q2.6 — Where does the EntityID appear, and whose is it in each place?**

<details><summary>Model answer</summary>

Three load-bearing places:
1. **AuthnRequest `<Issuer>`** → the **SP's** EntityID ("this request is from me").
2. **Response & Assertion `<Issuer>`** → the **IdP's** EntityID ("I minted this") — the SP uses it to pick the verification cert.
3. **`<Audience>`** (in Conditions) → the **SP's** EntityID ("this assertion is *for* me only").

★ "`Issuer` = *who sent it*; `Audience` = *who may consume it*. It's just a unique name — usually a URL, but it need not resolve to anything."
</details>

**Q2.7 — SSO vs Federated SSO — what's the difference, and how does SAML give you each?**

<details><summary>Model answer</summary>

- **SSO** = one login → many apps. Can be done *within one domain* with a shared cookie — no federation needed.
- **Federation** = two **different security domains** (companies / identity systems) agree to **trust each other's logins** via a signed contract of **metadata + certificates**.
- **Federated SSO** = SSO achieved **across** that boundary, using SAML/OIDC.

SAML delivers both: the IdP authenticates you **once** (→ SSO via the IdP session), and the **metadata + certificate** trust lets SPs in *other* organizations accept the assertion without ever seeing your password (→ federation). ★ "All federated SSO is SSO; not all SSO is federated."
</details>

**Q2.8 — What are the SAML bindings, and which message uses which?**

<details><summary>Model answer</summary>

- **HTTP-Redirect** — XML deflated→base64→URL-encoded into a query string. Used for the small **AuthnRequest**. Length-limited.
- **HTTP-POST** — base64 XML in an auto-submitting HTML form. Used for the large, signed **Response**. The workhorse.
- **HTTP-Artifact** — the browser carries only a small reference ("artifact"); the SP fetches the real assertion over a **back-channel** SOAP call. More secure (assertion never in the browser) but more complex; less common.

★ "Redirect for the request, POST for the response — because the response is big and signed."
</details>

**Q2.9 — What is RelayState?**

<details><summary>Model answer</summary>

An **opaque round-trip value** that preserves "where the user was going" so the SP can return them to the exact page after login (e.g., `/reports` instead of the home page). In IdP-initiated flows it's often used to carry the **target app/resource**. ⚠ It's not signed and not secret — validate/allow-list it (open-redirect risk).
</details>

**Q2.10 — What's inside an `<AttributeStatement>` and why do you care?**

<details><summary>Model answer</summary>

The user's **attributes/claims** — email, `groups`, roles, department, employee ID — as `<saml:Attribute>` elements. The SP maps them to **authorization** (which roles/permissions the user gets). ★ "When a user *logs in fine but has no access*, it's almost always attribute mapping — the SP expected `groups` but the IdP sent `memberOf`, or the values don't match."
</details>

---

## Tier 3 — Hard

**Q3.1 — Certificates in SAML do two different jobs. Name them and say whose keys each uses.**

<details><summary>Model answer</summary>

1. **Signing (authenticity + integrity)** — uses the **IdP's** keypair. IdP signs with its **private** key; SP verifies with the IdP's **public** cert (from metadata).
2. **Encryption (confidentiality)** — uses the **SP's** keypair. IdP encrypts with the SP's **public** cert; only the SP's **private** key can decrypt.

★ The direction flips: *"you **sign with your own** key; you **encrypt with the reader's** key."* ⚠ Common mistake: thinking encryption uses the IdP's cert — it uses the SP's.
</details>

**Q3.2 — Walk me through how the SP verifies the signature. What must it never do?**

<details><summary>Model answer</summary>

The IdP embeds a `<ds:Signature>` whose `Reference URI` points at the exact element it covers (e.g., the Assertion). The SP **recomputes the hash** of that element (after canonicalization) and verifies the signature using the **pre-configured IdP certificate from metadata**. Any changed byte → hash mismatch → reject.

**It must NEVER** trust a certificate **embedded in the message** (`<KeyInfo>/<X509Certificate>`). If it validates against the attacker-supplied cert, the attacker just signs with their own key and walks in. ★ "Pin the cert from metadata; ignore in-message certs."
</details>

**Q3.3 — How does SAML encryption actually work mechanically?**

<details><summary>Model answer</summary>

**Hybrid encryption** (XML-Encryption): a random **symmetric key (AES)** encrypts the assertion XML (fast); that AES key is itself encrypted with the SP's **RSA public key** and shipped alongside as `<xenc:EncryptedKey>`. The SP uses its **RSA private key** to unwrap the AES key, then AES-decrypts the assertion. ★ "Symmetric for the payload, asymmetric to deliver the key — same pattern as TLS and PGP."
</details>

**Q3.4 — When both signing and encryption are used, what's the order of operations?**

<details><summary>Model answer</summary>

IdP: **sign first, then encrypt.** SP: **decrypt first, then verify** the signature inside. If you verified before decrypting you'd be verifying ciphertext (impossible), and if the IdP encrypted before signing you'd be signing something the SP can't check without decrypting. ★ "Sign-then-encrypt on the way out; decrypt-then-verify on the way in."
</details>

**Q3.5 — "Encrypt the Response" vs "encrypt the Assertion" — explain precisely.**

<details><summary>Model answer</summary>

★ **There is no `EncryptedResponse` element in SAML.** When people say "encrypt the Response," they almost always mean **`<EncryptedAssertion>`** — the whole assertion (NameID, attributes, conditions) is encrypted, while the **outer Response envelope** (Status, Destination, Issuer) stays **readable** so the SP can route and read Status *before* decrypting.

Finer granularities also exist: **`<EncryptedID>`** (just the NameID) and **`<EncryptedAttribute>`** (individual attributes). ⚠ So "the Response is encrypted" is loose talk — pin them down to *which element*.
</details>

**Q3.6 — In the whole SAML process, what is encrypted and what is not?**

<details><summary>Model answer</summary>

Three independent layers:
- **TLS (transport):** every hop is HTTPS, so on the wire *everything* is encrypted — but TLS **terminates at each endpoint** (decrypted at the browser, re-encrypted onward). Protects the *pipe*, not the *parcel*.
- **XML signature (integrity):** **not encryption** — a signed assertion is fully **readable**; the signature only makes tampering detectable.
- **XML encryption (confidentiality):** *optional*, and the only thing that hides content **from the browser itself** (`<EncryptedAssertion>` etc.).

So by default the AuthnRequest, Response envelope, and assertion are all **readable** (base64 is *encoding*, not encryption) — only TLS protects them, and only until the browser. ★ **"Base64 is not encryption — anyone who captures the POST reads the assertion unless it's `EncryptedAssertion`."**
</details>

**Q3.7 — What is clock skew and why is it the sneakiest SAML ticket?**

<details><summary>Model answer</summary>

The assertion is valid only between `NotBefore` and `NotOnOrAfter` (~5-min window), checked against **each server's own clock**. If IdP and SP clocks disagree beyond the tolerance (often ±3–5 min), the SP sees a perfectly good assertion as **"not yet valid"** or **"expired"** and rejects the login. Root cause: **NTP drift** or a paused/resumed VM. Fix: NTP on both sides + a small configurable **skew tolerance**. ★ "It produces *intermittent* 'sometimes login fails' tickets — recognizing the pattern instantly makes you look sharp."
</details>

**Q3.8 — In what order does the SP validate a SAML Response? Give the checklist.**

<details><summary>Model answer</summary>

1. Decode & **parse safely** (hardened XML parser — no external entities).
2. **Status** = Success? (else read the error code.)
3. **Issuer** = the expected IdP?
4. **Signature** present, and verifies against the **pinned** IdP cert (not expired)?
5. **Decrypt** the assertion if `<EncryptedAssertion>` (SP private key).
6. **Destination / Recipient** = my ACS URL?
7. **Audience** = my EntityID?
8. **NotBefore / NotOnOrAfter** inside the window (± skew)?
9. **InResponseTo** matches an outstanding AuthnRequest (SP-init)?
10. **Replay check** — assertion `ID` never seen before (one-time use)?
11. Then map NameID→account, attributes→roles, create session.

★ "This is also the fastest *debugging* order — walk it top-down and you find the failing check quickly."
</details>

**Q3.9 — What is Single Logout (SLO) and why is it always broken?**

<details><summary>Model answer</summary>

**SLO** tries to log the user out of *all* SP sessions at once — the IdP sends a `LogoutRequest` to every SP that has a live session (front- or back-channel). It's fragile because many SPs don't implement it, back-channel calls fail silently, and SP sessions linger. ★ "SLO is **best-effort, not guaranteed** — expect 'logout didn't actually log me out' tickets, and know that killing the IdP session ≠ killing every SP session."
</details>

**Q3.10 — Why does SAML trust self-signed certificates when HTTPS won't?**

<details><summary>Model answer</summary>

Because SAML trust **doesn't come from a CA chain** — it comes from the **explicit metadata exchange at onboarding** ("here is my exact certificate; pin it"). The metadata swap *is* the trust ceremony, so a self-signed cert is fine as long as both sides pinned it. ★ "It's *direct trust / cert pinning*, not *transitive PKI trust* like the web. That's also why cert **rotation** is a manual, scheduled operation, not automatic."
</details>

**Q3.11 — Why is certificate rotation such a big deal, and how do you do it with zero downtime?**

<details><summary>Model answer</summary>

If the IdP's **signing cert expires**, *every* SP fails signature validation **simultaneously** → company-wide "SSO is down." Zero-downtime rotation: publish **both** the old and new signing certs in metadata (a **primary + secondary** cert) so SPs trust the new one *before* the old one retires; roll SPs over; then remove the old cert. ★ "In Ping you stage the new cert as secondary, re-distribute metadata, then promote — never a hard cutover. Fintechs track cert expiry like a production incident waiting to happen."
</details>

---

## Tier 4 — Very hard / expert (attacks, Ping, edge cases)

**Q4.1 — Explain XML Signature Wrapping (XSW). Why is it devastating, and how do you defend?**

<details><summary>Model answer</summary>

**Attack:** the attacker takes a legitimately-signed assertion and **wraps** the XML so the SP **verifies the signature on the original element** but **reads identity/attributes from an injected, unsigned copy** (exploiting a gap between the code that *validates* and the code that *reads*). Result: forge any user.

**Defenses:** use a **hardened, maintained SAML library**; ensure the element that's **validated is exactly the element that's processed** (verify-then-extract on the *same* node); **schema-hardening**; reject messages with **multiple assertions**; process only the signed element by secure ID reference. ★ Pair with **Heimdall**: SIEM-alert on multiple `<Assertion>`/`<Signature>` nodes or signature-validation anomalies; walk the mechanics only on your **local Keycloak lab**.
</details>

**Q4.2 — An SP accepts an assertion where only the outer Response is signed but the inner Assertion isn't. What's wrong?**

<details><summary>Model answer</summary>

Signature scope confusion. If the SP **trusts the inner Assertion** but only the **outer Response** signature was verified (or vice-versa), an attacker can swap in an unsigned/forged assertion. **Rule: the security-critical element — the Assertion — must itself be signed**, and the SP must require it. Best practice: **sign the Assertion** (and ideally the Response too), and **reject unsigned assertions**. ⚠ "Signed Response, unsigned Assertion" is a classic misconfiguration that enables tampering.
</details>

**Q4.3 — Why is IdP-initiated SSO considered less secure, specifically?**

<details><summary>Model answer</summary>

The Response is **unsolicited** — there's **no `InResponseTo`** to bind it to a request the SP actually made. That enables:
- **Assertion replay** (a captured still-valid assertion can be re-POSTed).
- **Login-CSRF** — an attacker injects an assertion **for their own account** into the victim's browser, silently logging the victim into *the attacker's* account (then the victim's actions/data go to the attacker).

**Mitigations:** prefer SP-init; short assertion lifetimes; **one-time-use** assertion IDs; disable unsolicited SSO on SPs that don't need it. ★ "The missing `InResponseTo` is the whole story."
</details>

**Q4.4 — (Ping) In PingFederate, what is an IdP connection vs an SP connection, and where do certs live?**

<details><summary>Model answer</summary>

- When PingFederate acts as the **IdP**, each partner app is configured as an **SP connection**; when it acts as the **SP**, each partner IdP is an **IdP connection**.
- Each connection has a **Credentials** section: the **signing certificate** (your private key to sign outgoing messages), the partner's **signature verification certificate** (their public cert to verify incoming), and an optional **encryption certificate** (the partner SP's public cert used to encrypt the assertion).
- The connection also pins the **partner's EntityID**, endpoints (ACS/SSO/SLO), SAML profile (IdP-init/SP-init), and attribute contract.

★ Mentioning **"attribute contract"** and **"connection Credentials"** signals real PingFederate familiarity.
</details>

**Q4.5 — (Ping) What are adapters in PingFederate, and why do they matter?**

<details><summary>Model answer</summary>

Adapters bridge SAML to the actual authentication/session:
- **IdP adapter** — *how the user authenticates* (e.g., **HTML Form Adapter**, Kerberos/IWA, X.509, or a composite with MFA). Its output populates the assertion.
- **SP adapter** — *how the identity is handed to the target app* after validation (e.g., OpenToken, Reference ID / agentless) so the app can create its session.

★ "So the SAML connection defines the *federation contract*, and adapters define the *local* authentication and session hand-off on each side." Attribute mapping flows **adapter → assertion (IdP side)** and **assertion → adapter/app (SP side)**.
</details>

**Q4.6 — (Ping) How would you trigger an IdP-initiated SSO for a smoke test, and how do you deep-link?**

<details><summary>Model answer</summary>

PingFederate exposes an IdP-init SSO endpoint roughly:
`https://<pf-host>/idp/startSSO.ping?PartnerSpId=<SP-EntityID>&TargetResource=<landing-url>`
- `PartnerSpId` selects which **SP connection** to fire.
- `TargetResource` is the deep-link (Ping's equivalent of carrying RelayState to a specific page).

For SP-init the mirror is `/sp/startSSO.ping?PartnerIdpId=<IdP-EntityID>`. ★ "IdP-init via `startSSO.ping` is exactly why we call IdP-init the *onboarding/testing* flow — you can fire SSO from the IdP side before the app's own login is wired up."
</details>

**Q4.7 — The signature verified but the login still fails with "audience mismatch." What happened?**

<details><summary>Model answer</summary>

The assertion is **authentic and untampered** (signature is fine) but its **`<Audience>` doesn't equal this SP's EntityID** — it was minted **for a different SP**, or the SP's configured EntityID/Audience value doesn't match what the IdP put in. Fix: align the **Audience/EntityID** on both sides (a frequent cause is a trailing slash or http-vs-https difference in the EntityID string). ★ "Signature valid + audience wrong = *right IdP, wrong intended app* — it's a config mismatch, not a trust failure."
</details>

**Q4.8 — Is the AuthnRequest signed? When would it need to be?**

<details><summary>Model answer</summary>

Often **not** signed. It's signed when the IdP **requires** signed requests (to stop forged/tampered AuthnRequests — e.g., someone tampering with the requested ACS URL or NameIDPolicy). On the **HTTP-Redirect** binding the signature is a **separate URL parameter** (`Signature` + `SigAlg`) over the query string, *not* an enveloped XML signature, because the deflated request has no room for embedded XML-DSig. ★ "Request signing uses the **SP's** signing key; the IdP verifies with the SP's public cert from metadata."
</details>

**Q4.9 — What's the difference between `Destination`, `Recipient`, and `Audience`? They all look like URLs.**

<details><summary>Model answer</summary>

- **`Destination`** (on Request/Response) — the **URL this message was sent to**; guards against it being redirected/replayed to a different endpoint.
- **`Recipient`** (in `SubjectConfirmationData`) — the **ACS URL** the assertion must be delivered to; the SP checks it equals its own ACS.
- **`Audience`** (in Conditions) — the **EntityID of the SP** the assertion is *for* (a logical identity, not an endpoint).

★ "`Destination`/`Recipient` are *where it goes* (endpoints); `Audience` is *who it's for* (an identity). Confusing them is a classic mistake."
</details>

**Q4.10 — What does `SubjectConfirmation Method="bearer"` mean, and what's the risk?**

<details><summary>Model answer</summary>

**Bearer** = "whoever bears (holds) this assertion is treated as the subject" — like cash. Whoever presents it within its short validity is accepted. **Risk:** if intercepted before expiry, it can be **replayed**. That's why bearer assertions have a **short `NotOnOrAfter`**, a pinned **`Recipient`**, `InResponseTo` binding, and **one-time-use** enforcement — and why TLS everywhere matters. (The stronger, rarely-used alternative is **holder-of-key**, which binds the assertion to a key the presenter must prove they hold.)
</details>

**Q4.11 — Design question: an app supports both SAML and OIDC. How do you choose?**

<details><summary>Model answer</summary>

- **SAML** if: it's an enterprise/legacy SaaS that standardized on it, you need it to match existing federation, or the vendor's SAML integration is more mature. XML, browser-only.
- **OIDC** if: it's modern web/mobile/SPA, you need **API access tokens** (OAuth) alongside login, or you want lighter JSON/JWT and discovery. Better for native mobile (no browser-POST gymnastics).

★ "Same job (federated authN); I'd default to **OIDC for new/mobile**, **SAML for enterprise/legacy** — and note many shops run both for years. I'd also weigh which one our IdP + the vendor support *best*, not just in theory."
</details>

**Q4.12 — What stops a valid assertion for App A being replayed to App B?**

<details><summary>Model answer</summary>

The **`<AudienceRestriction>`** — the assertion names App A's EntityID as its audience, and App B **rejects** an assertion whose Audience isn't itself. Reinforced by the **`Recipient`** (App A's ACS, not App B's) and by **encryption to App A's key** if used (App B can't even decrypt it). ★ "Audience + Recipient + short lifetime + one-time-use together kill cross-SP replay."
</details>

**Q4.13 — What is the `AuthnContextClassRef`, and how is it used for step-up/MFA?**

<details><summary>Model answer</summary>

It states **how the user authenticated** (e.g., `PasswordProtectedTransport`, or a stronger MFA class). The SP can **demand** a level via `<RequestedAuthnContext>` in the AuthnRequest; the IdP must satisfy it (possibly **stepping up** to MFA) and echoes the achieved class in the assertion. ★ "It's how a high-value app forces MFA even if the base session was password-only — the SP asks for a stronger context, the IdP steps up."
</details>

---

## Tier R — Rapid-fire (drill until reflex)

<details><summary>Expand the full rapid-fire set (Q → A)</summary>

| # | Question | Answer |
|---|---|---|
| R1 | SAML version in use? | **2.0** (2005). |
| R2 | Data format? | **XML.** |
| R3 | The "login proof" element? | **`<Assertion>`.** |
| R4 | Binding for the request? | **HTTP-Redirect.** |
| R5 | Binding for the response? | **HTTP-POST.** |
| R6 | The most secure binding? | **HTTP-Artifact** (back-channel). |
| R7 | Fastest SP-init vs IdP-init tell? | Presence of **`InResponseTo`.** |
| R8 | The always-present user field? | **`<Subject>` / `<NameID>`.** |
| R9 | "For which SP" field? | **`<Audience>`.** |
| R10 | "Where to deliver" field? | **`Recipient` / ACS URL.** |
| R11 | Validity-window fields? | **`NotBefore` / `NotOnOrAfter`.** |
| R12 | Signing uses whose key? | **IdP's private key.** |
| R13 | Encryption uses whose key? | **SP's public key.** |
| R14 | Is base64 encryption? | **No — encoding.** |
| R15 | Element for an encrypted assertion? | **`<EncryptedAssertion>`.** |
| R16 | Is there an `EncryptedResponse`? | **No.** |
| R17 | #1 sneaky outage cause? | **Clock skew** (NTP). |
| R18 | #1 total-outage cause? | **Signing cert expiry.** |
| R19 | Logout standard? | **SLO** (best-effort). |
| R20 | Trust source (not a CA)? | **Metadata exchange / pinned cert.** |
| R21 | Classic tampering attack? | **XML Signature Wrapping (XSW).** |
| R22 | "Logged in, no access" cause? | **Attribute mapping.** |
| R23 | SAML's OIDC counterpart token? | **ID token (JWT).** |
| R24 | What carries "return to this page"? | **RelayState.** |
| R25 | Unique name of a party? | **EntityID.** |

</details>

---

## Tier S — Scenarios (diagnose the ticket)

**S1 — "Login works in the morning but randomly fails midday with 'assertion expired.' Sometimes a retry works."**

<details><summary>Model answer</summary>

**Clock skew.** The SP and IdP clocks are drifting apart; when the offset exceeds the skew tolerance the (valid) assertion falls outside `NotBefore`/`NotOnOrAfter`. Retry sometimes works because the window is borderline. **Fix:** sync **NTP** on both hosts (suspect a paused/resumed VM), and confirm the SP's skew tolerance. First thing to capture: the assertion timestamps vs both servers' current time.
</details>

**S2 — "Every SP broke at 2 a.m., all at once, 'signature validation failed.'"**

<details><summary>Model answer</summary>

**IdP signing certificate expired** (or was rotated without redistributing metadata). All SPs verify against the same IdP cert, so they all fail together. **Fix:** roll out the new signing cert to all SPs (ideally you'd have staged it as a **secondary** cert beforehand). **Prevent:** track cert expiry, rotate with primary+secondary overlap.
</details>

**S3 — "The user authenticates at the IdP (MFA prompt and all), but the app shows 'access denied / no roles.'"**

<details><summary>Model answer</summary>

Authentication succeeded, **authorization data is wrong** — i.e., **attribute mapping**. The SP expects e.g. a `groups` attribute with specific values, but the IdP sent a different **name** (`memberOf`), **format**, or **values**. **Fix:** capture the assertion, read the `<AttributeStatement>`, compare to what the SP's role mapping expects. This is the "logged in but no access" pattern.
</details>

**S4 — "A brand-new SP connection: the browser POSTs the response but the SP says 'Recipient/Destination mismatch.'"**

<details><summary>Model answer</summary>

The **ACS URL** the IdP is sending to (in `Destination`/`Recipient`) doesn't match the SP's **actual ACS endpoint** — a typo, http-vs-https, trailing slash, or wrong environment (test URL in prod config). **Fix:** align the ACS URL in the IdP's SP connection with the SP's real ACS from its metadata.
</details>

**S5 — "Signature verifies, timestamps are fine, but the SP rejects with 'audience/issuer not recognized.'"**

<details><summary>Model answer</summary>

**EntityID mismatch.** Either the assertion's `<Issuer>` (IdP EntityID) isn't what the SP has configured for trust, or the `<Audience>` doesn't equal the SP's EntityID — commonly a **string mismatch** (trailing slash, case, http/https) rather than a real trust problem. **Fix:** make the EntityID strings **byte-identical** on both sides. ★ "Authentic message, wrong *name* — config, not crypto."
</details>

**S6 — "SAML-tracer shows the assertion in plain text with all the user's attributes. Is that a vulnerability?"**

<details><summary>Model answer</summary>

By itself, **no — that's normal**: base64 is *encoding*, and the assertion is only signed (readable), protected on the wire by **TLS**. It becomes a concern if **sensitive attributes** (PII, internal IDs) shouldn't be exposed to the browser/logs/screenshots — then enable **`<EncryptedAssertion>`**, **minimize attributes** to what the SP needs, and never paste raw assertions into tickets/chat. ★ "Readable ≠ broken; but readable + sensitive = encrypt the assertion and trim the claims."
</details>

---

## Tier ↩ — Reverse questions (ask the senior — earn respect)

<details><summary>Sharp questions that show you're thinking like an operator</summary>

- "On our Ping stack, do we default new SP connections to **SP-init only**, or do we allow **unsolicited (IdP-init)** — and if so, how do we mitigate login-CSRF?"
- "What's our **signing-cert rotation** runbook — do we stage a secondary cert, and how far ahead of expiry?"
- "Do we **encrypt assertions** by default, or only for connections carrying sensitive attributes? What's the policy?"
- "How do we handle **attribute contracts** — is there a standard mapping, or is it per-connection bespoke?"
- "What's our **clock-skew tolerance**, and how do we monitor IdP/SP time drift?"
- "Do we bother with **SLO**, or do we rely on short session lifetimes since SLO is flaky?"
- "Where do we **draw the line between SAML and OIDC** for new integrations?"
- "What does our **detection** look like for XSW / malformed assertions — is Heimdall's SIEM watching for multiple-assertion or signature-failure patterns?"

</details>

---

## What you learned

- You can now field **easy → expert** SAML questions: definitions, the full SP-init and IdP-init flows, assertion anatomy, the **three levels of mandatory**, EntityID's three homes, certificate sign-vs-encrypt (and the key-direction flip), the encryption-scope precision (**no `EncryptedResponse`**), what's encrypted vs merely encoded, clock skew, cert rotation, SLO, the headline **attacks + defenses** (XSW, replay, login-CSRF, cert injection), and **Ping-specific** mechanics (connections, credentials, adapters, `startSSO.ping`).
- You have **scenario reflexes** — the exact patterns behind the most common SAML tickets.

## Next

- **Narrate it live:** re-run [Lab 02](../labs/02-saml-assertion-anatomy/README.md) and speak the [60-second read](13-saml-mastery-session2.md#5e-the-60-second-speed-read-drill-say-it-out-loud) over a real decoded assertion.
- **Cross-train:** the [OAuth/OIDC deep dive](03-oauth-oidc-deep-dive.md) — interviewers love "SAML vs OIDC," and you'll want the same fluency there.
- **Purple-team:** ask **Heimdall** what a SIEM would detect for each Tier-4 attack, and **Loki** to demo XSW on your **local Keycloak lab only**.

*— Janus 🔐, sparring partner for your senior's QnA*
