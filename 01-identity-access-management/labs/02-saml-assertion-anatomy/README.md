# Lab 02 — SAML assertion anatomy (decode a real one)

> **Lefler's build, Janus's curriculum.** The single most useful SAML skill is **reading an assertion**. Do it here on a safe sample, then on a live capture, and the [SAML deep dive](../../notes/02-saml-deep-dive.md) becomes something you can *operate*, not just recite. **Authorized-lab-only** — decode only sample or your-own-lab traffic, never captured production/FinCo assertions.

- **Time:** 30–60 min · **Difficulty:** beginner-friendly · **Prereqs:** [note 02](../../notes/02-saml-deep-dive.md)
- **You'll learn:** base64 decode a SAML Response, locate every security-critical field, run the 60-second debugging checklist, capture a live assertion with SAML-tracer.

---

## Exercise A — decode the provided sample (always works, no internet needed)

This folder ships `sample-saml-response.b64` — a base64-encoded SAML Response exactly like what an SP receives on the wire (POST binding). Decode it in PowerShell:

```powershell
$b64 = Get-Content .\sample-saml-response.b64 -Raw
$xml = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64.Trim()))
$xml
```

You'll see the readable XML. (Optional prettifier if a real one comes back unindented:)
```powershell
[xml]$doc = $xml
$sw = New-Object System.IO.StringWriter
$wr = New-Object System.Xml.XmlTextWriter($sw); $wr.Formatting = 'Indented'
$doc.WriteTo($wr); $sw.ToString()
```

> ✅ **Checkpoint:** you turned an opaque blob into XML. That's step 2 of the note-02 debugging checklist — and 90% of "I can't read the SAML" is just this decode.

### Now find every field that matters (your answer key)

Locate each of these in the decoded XML and say *out loud* what it does — this is exactly what you'll do in a ticket:

| Find this | Value in the sample | Why it matters |
|---|---|---|
| `<samlp:StatusCode>` | `...:status:Success` | Did the IdP succeed? (Errors show `Requester`/`Responder`.) |
| Outer `<saml:Issuer>` | `https://idp.finco-lab.example.com/metadata` | **Which IdP** minted this — must be a trusted issuer |
| `<ds:Signature>` present? | yes, on the **Assertion** | The "hologram"; must verify against the IdP's configured cert |
| `<NameID>` | `farhaan@example.com` | **Who** the assertion is about |
| `Recipient` | `https://workday.example-sp.com/saml/acs` | Must equal the SP's **ACS URL** |
| `<Audience>` | `https://workday.example-sp.com/metadata` | Assertion is **for this SP only** |
| `NotBefore` / `NotOnOrAfter` | `10:02:35Z` / `10:08:05Z` | The ~5.5-min **validity window** (clock-skew territory) |
| `InResponseTo` | `_req4a2d7e10bc` | Ties the response to the SP's original AuthnRequest (SP-init) |
| `<AttributeStatement>` | email, displayName, department, **groups** (iam-team, payments-read-only) | The claims the SP turns into **authorization** |

### Debugging drill — reason about validity

The assertion is valid only between `NotBefore` (10:02:35) and `NotOnOrAfter` (10:08:05). Answer these:
1. If the SP's clock says **10:05:00**, is the assertion valid? *(Yes — inside the window.)*
2. If the SP's clock says **10:10:00**, what happens? *(Rejected — "assertion expired." If the real time was actually 10:05 but the SP clock is fast, that's **clock skew** → check NTP. This is the classic intermittent-SSO ticket from note 02 §8.)*
3. The user logs in successfully but has **no access** in the app. Which element do you inspect? *(`<AttributeStatement>` / `groups` — attribute mapping. Signature and window were fine.)*

> Answering these fluently *is* the job. You just triaged three of the most common SAML tickets on a sample.

---

## Exercise B — capture a LIVE assertion with SAML-tracer

Now see a real one on the wire.

1. **Install SAML-tracer** (Firefox or Chrome extension — search the add-on store). Open it (it starts recording).
2. Do a real SAML login at a **public test service** (safe, no FinCo data):
   - **mocksaml.com** — a free hosted SAML **IdP** for testing.
   - **samltest.dev** / **sptest.iamshowcase.com** — hosted SAML test SP/IdP endpoints you can bounce through.
   - Follow the site's "start SSO / test" button so an actual SAML round-trip happens.
3. In SAML-tracer, find the requests tagged **SAML**. You'll see:
   - a **`SAMLRequest`** (the AuthnRequest, SP→IdP) and
   - a **`SAMLResponse`** (the assertion, IdP→SP).
   Click the **"SAML" tab** in the tool — it auto-decodes the XML for you. Compare it field-by-field to your Exercise-A table.

**Decoding by hand (when you only have the raw parameter):**
- A **`SAMLResponse`** (POST binding) is just base64 → use the Exercise-A one-liner.
- A **`SAMLRequest`** (redirect binding) is **DEFLATE-compressed then base64** → inflate it:
  ```powershell
  $enc = '<PASTE_RAW_SAMLRequest_VALUE>'          # URL-decode first if copied from a URL
  $bytes = [Convert]::FromBase64String($enc)
  $ms = New-Object System.IO.MemoryStream(,$bytes)
  $ds = New-Object System.IO.Compression.DeflateStream($ms,[System.IO.Compression.CompressionMode]::Decompress)
  (New-Object System.IO.StreamReader($ds)).ReadToEnd()
  ```

> **SP-init vs IdP-init (note 02 §3):** if you saw a `SAMLRequest` *before* the `SAMLResponse`, it was **SP-initiated**. If a `SAMLResponse` arrived with **no** preceding request (and no `InResponseTo`), it was **IdP-initiated** — the less-secure, replay-prone mode. Being able to say which one you observed is a great first question in any SSO ticket.

---

## Exercise C — level up: make your OWN IdP emit an assertion

Reuse your Keycloak from [Lab 01](../01-keycloak-idp/README.md):
1. In realm `finco-lab` → **Clients → Create client → SAML**, Client ID = an entityID like `https://sp.example.com/metadata`.
2. Set a **Valid redirect/ACS URL** to a public test SP's ACS (e.g., iamshowcase) *or* keep it local.
3. Trigger SSO and capture the `SAMLResponse` with SAML-tracer → you're now reading an assertion **your own IdP** signed. Inspect the `<ds:Signature>` and the realm's SAML signing cert (Realm settings → Keys).

> This closes the loop: in Lab 01 you were the OIDC Authorization Server; here you're the **SAML IdP**. Same IdP, two protocols — exactly like Okta/Entra serving modern and legacy apps at once.

---

## Attack / defense sidebar (repo rule: always pair them)

Study these **only** on this sample or your own lab:
- **XML Signature Wrapping (XSW):** duplicate the `<saml:Assertion>`, give the forged copy different attributes, and position it so a weak SP validates the signature on the *real* one but reads the *forged* one. **Defense:** hardened SAML libraries that process exactly the signed element; reject multiple assertions. Ask **Loki** to demo, **Heimdall** what a SIEM would flag (multiple assertions, signature-validation failures, unexpected IdP cert).
- **Unsigned-assertion acceptance:** strip `<ds:Signature>` and see whether a (misconfigured) SP still accepts it. **Defense:** require the Assertion itself to be signed.

Full attack table: [note 02 §9](../../notes/02-saml-deep-dive.md#9-attacks--defenses-always-pair-them--claudemd-rule).

---

## ⚠️ Data-handling note (fintech habit)

- The shipped sample uses **dummy data** — safe to keep in the repo.
- **Never commit a real captured assertion** — it can contain real names, emails, group memberships (PII/CDE). Decode it, learn from it, delete it. (The repo `.gitignore` already blocks `*.pem`/`*.key`/`*.p12` so exported certs won't sneak in, but assertions aren't auto-ignored — mind what you save.)
- **Never paste a real production assertion into an online decoder or AI tool** — same rule as [note 05 §D](../../notes/05-first-week-questions.md#d-ai-dev-in-resolving-incidentstickets--the-honest-version-fintech-guardrails). Decode locally with the PowerShell above.

---

## What you learned & next

- You can now turn a SAML blob into XML and locate **every** security-critical field in seconds.
- You can reason about the validity window (clock skew), audience, signature, and attribute mapping — the four things behind most SAML tickets.
- You've seen SP-init vs IdP-init on the wire.

**Next:** revisit the [note 02 §13 debugging checklist](../../notes/02-saml-deep-dive.md#13-the-60-second-saml-debugging-checklist-tape-this-to-your-monitor) and the [first-week questions](../../notes/05-first-week-questions.md) — you're now equipped to ask about *your* environment's SAML setup with real understanding.

*Built for Farhaan's IAM track · authorized-lab-only 🔐*
