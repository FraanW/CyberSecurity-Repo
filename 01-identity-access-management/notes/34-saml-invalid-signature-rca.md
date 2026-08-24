# 34 — "Invalid Signature": how SAML signature verification really works, and how to RCA it

> **Why this note exists.** You built an SP connection on your PingFederate IdP. The AuthnRequest
> goes out fine. The browser comes back — and the app says **Invalid Signature**. This note derives
> *why* that error exists at all, then gives you a repeatable root-cause procedure and the one fix
> that removes the whole class of problem.
>
> **Prereqs:** [note 02 — SAML deep dive](02-saml-deep-dive.md),
> [note 16 — SAML bindings and certificates](16-saml-bindings-and-certificates.md).
> **Time:** ~25 min to read · ~10 min to run the lab check. **Difficulty:** intermediate.
> **Authorized-lab-only.** Your own PingFederate, test connections, dummy users.

---

## TL;DR

| | |
|---|---|
| **What the error means** | "I could not prove this assertion was written by the IdP I trust." |
| **What it almost never means** | somebody tampered with your login |
| **What it almost always means** | **the SP is holding the wrong public key** — a different certificate from the one PingFederate is signing with |
| **Fastest correct fix** | **import the IdP's metadata file** — it carries the certificate the IdP actually uses, so there is nothing to mistype |
| **The one habit that ends these tickets** | **compare SHA-256 fingerprints, not subject names** |

**The lab app now does this for you.** After a failed login it shows the fingerprint the IdP signed
with next to the fingerprints you trust, side by side. See §6.

---

## 1. First principles — why a signature has to exist at all

Start from the constraint, not the protocol.

In SSO, the app (the **Service Provider**, or **SP** — the thing that *trusts* the login) never sees
your password. Somebody else checked it: the **Identity Provider** (**IdP** — PingFederate, in your
case). The IdP writes a note that says *"this is farhaan, I checked, let them in"* — that note is the
**assertion**.

Now the awkward part. **That note travels through the browser.** SAML's POST binding literally puts
the assertion in a hidden form field and has *your browser* submit it to the app. The browser is
under the user's control. So:

> **The constraint:** the app receives a "let them in" note from a party it does not trust, carried
> by a channel it does not control.

Anyone who can open developer tools could edit that form field to say `admin` instead of `farhaan`.
So the note needs a property that a hostile courier cannot forge:

> **The requirement:** the app must be able to tell that the note was written by the IdP, *without*
> sharing a secret with the browser, and *without* asking the IdP anything at request time.

That requirement is what a **digital signature** is for. It is not a protocol decoration; it is the
only thing holding the whole design up. **Remove the signature and SAML SSO becomes a login form
where the user types their own username.**

**Why you care at FinCo:** this is the exact question an auditor asks about your SSO estate — *"what
stops a user from editing their own assertion?"* The answer is one sentence: the assertion is signed
by the IdP's private key, and the SP holds only the public half.

---

## 2. Public key and private key — the whole idea in one paragraph

An **RSA keypair** is two numbers generated together with one useful property:

> **What you transform with one key, only the other key can untransform.**

Nothing else about them is special. There is no "encryption key" and "decryption key" — they are
symmetrical in maths and asymmetrical only in *who holds them*:

| | **Private key** | **Public key** |
|---|---|---|
| Who has it | **only** the IdP, on the PingFederate server | everyone — it is published in metadata |
| Kept | secret, in Ping's keystore, never leaves | handed out freely |
| Used to | **sign** the assertion | **verify** the assertion |
| In your lab | `LAB_SAML_SP_PRIVATE_KEY` (our side) | the `<X509Certificate>` in metadata |

### The analogy that actually holds up

Think of a **wax seal on a letter**.

- The **signet ring** is the private key. The king keeps it. Nobody else has one that makes that
  exact pattern.
- The **impression in the wax** is the signature. Anyone can look at it.
- **Knowing what the pattern looks like** is the public key. You do not need the ring to *check* a
  seal — you only need to know what a genuine one looks like.

A forger who has seen a thousand sealed letters still cannot make the ring. That asymmetry — *easy
to check, impossible to produce* — is the entire trust model.

> ⚠️ **Where the analogy breaks (and it matters).** A wax seal proves the *sender*. An XML signature
> also proves the *contents have not changed*. That second property is what stops someone changing
> `farhaan` to `admin` and leaving the seal alone. Read on.

### A certificate is just packaging

The IdP does not hand you a bare public key. It hands you an **X.509 certificate**: the public key,
plus a subject name, plus validity dates, wrapped up in a standard envelope. **For SAML, the only
part that does anything is the public key inside.** The subject name is a label. The dates are a
hint. This matters in §5 — it is why two certificates that look identical can behave completely
differently.

---

## 3. How XML signing actually works — the two steps, and the two ways to fail

Signing an XML document is not "encrypt the document with the private key". It is two steps, and
knowing which one failed tells you what went wrong.

```
 IdP side (PingFederate)                     SP side (your app)
 ─────────────────────────                   ──────────────────────────
 1. Canonicalise the <Assertion>             1. Canonicalise it the same way
        ↓ byte-exact normal form                    ↓ byte-exact normal form
 2. SHA-256 hash it        ── DigestValue ──▶ 2. SHA-256 hash it, compare
        ↓                                           ↓  ← "digest check"
 3. Sign the hash with     ── SignatureValue ─▶ 3. Verify with the PUBLIC key
    the PRIVATE key                                 ↓  ← "signature check"
                                             4. Both pass → trust the assertion
```

### Step 1 — Canonicalisation ("C14N")

Two XML documents can mean exactly the same thing and still be different bytes:

```xml
<a  x="1"  y="2"/>          <!-- extra spaces, attributes in this order -->
<a y="2" x="1"></a>         <!-- same meaning, completely different bytes -->
```

A hash cares only about bytes. So before hashing, both sides rewrite the element into one agreed
normal form — attribute order fixed, redundant namespace declarations dropped, whitespace pinned.
That algorithm is **Exclusive XML Canonicalisation**, and you will see it named in the signature:

```xml
<ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
```

> **Why it must exist (first principles).** Without canonicalisation, an XML library that
> re-serialised the document — pretty-printing it, reordering attributes, adding a namespace prefix
> — would break every signature it touched. Since the assertion passes through at least two XML
> stacks, that would make signing unusable.

### Step 2 — Hash, then sign the hash

RSA signs a small fixed-size value, not a whole document. So the canonical bytes get hashed with
SHA-256, and **the hash** is what the private key operates on.

```xml
<ds:DigestValue>…</ds:DigestValue>       <!-- the hash of the canonical bytes -->
<ds:SignatureValue>…</ds:SignatureValue> <!-- that hash, signed with the private key -->
```

### The two failure modes — and they mean opposite things

| Which check failed | What it proves | What to go look at |
|---|---|---|
| **Digest mismatch** | the bytes changed after signing | something rewrote the XML — a proxy, a WAF, a copy-paste through an editor |
| **Signature mismatch** | bytes are intact, but the **public key is the wrong one** | **the certificate you configured** |

> 🎯 **In practice, on a fresh PingFederate SP connection, it is nearly always the second.** Your
> AuthnRequest going out fine already tells you the URLs are right. What is left is the key.

---

## 4. Where your assertion actually gets rejected

Walk the flow and mark the exact line where it dies:

```
1. Browser → app                     "I want in"
2. App    → browser → IdP            AuthnRequest        ← YOURS WORKS. URLs are right.
3. IdP authenticates the user        (HTML Form adapter, LDAP, whatever)
4. IdP signs the assertion           with its PRIVATE key
5. IdP → browser → app (POST)        the assertion arrives
6. App verifies the signature        with the PUBLIC key it was configured with   ← DIES HERE
7. App creates a session
```

**Step 2 working and step 6 failing is a very specific signal.** It means:

- the SSO URL is right (the request arrived),
- the entity ID is right (Ping found your connection),
- the ACS URL is right (the response came back to you),
- **and the certificate is wrong** — the one field none of the above exercises.

---

## 5. The root causes, in the order you should check them

### 🥇 Cause 1 — the SP holds a different certificate from the one Ping signs with

**By far the most common.** Sub-causes, all of which look identical from the app:

| Sub-cause | How it happens | Tell |
|---|---|---|
| **Key rotation** | the Ping admin rotated the signing certificate; your env var still has last year's | worked yesterday, broke overnight, nothing changed on your side |
| **Wrong certificate copied** | somebody pasted PingFederate's **SSL/server certificate** instead of its **SAML signing certificate** | subject looks plausible (same hostname!), fingerprint does not match |
| **Wrong connection's certificate** | Ping can use a *different* signing certificate per SP connection. The global metadata endpoint publishes the default one | metadata fetched **without** `?PartnerSpId=…` |
| **Only one of a pair trusted** | during a rotation Ping publishes two certificates and may sign with either | intermittent — some logins work, some do not |

> 🔍 **The `PartnerSpId` gotcha, spelled out.** PingFederate's metadata URL takes a parameter:
> `https://pf.example.com/pf/federation_metadata.ping?PartnerSpId=<your SP entity ID>`.
> **With** it, you get the metadata for *your* connection, including the certificate that connection
> signs with. **Without** it, you get the server default — which may be a different certificate
> entirely. This one catches experienced people.

### 🥈 Cause 2 — the certificate is right but was mangled getting into config

Environment variables and web forms are hostile to PEM blocks. Newlines get eaten, a line gets
truncated, a stray space creeps in. Usually this throws a *parse* error rather than an invalid
signature — but a partially-correct paste can produce either.

### 🥉 Cause 3 — the SP is not trusting anything real

**This one bit this very lab, and it is worth understanding as a design lesson.** The app needs
*some* certificate to build a valid configuration at startup. When nothing was configured, it
generated a random self-signed one as a placeholder — and said nothing. So an unreachable metadata
URL silently degraded into *"trusts a key nobody on earth holds"*, and every login failed with
`invalid_signature` for a reason that had nothing to do with PingFederate.

> **The lesson, which generalises well beyond this app:** a fallback that keeps the process alive
> must never be silent about what it gave up. Fail *open* on availability if you must; never fail
> *quiet* on trust. The app now flags that state loudly in `/api/config`, on the dashboard, and in
> the logs.

### Cause 4 — the bytes changed in transit (a genuine digest mismatch)

Rare, but real: a reverse proxy or WAF that re-encodes the POST body, or an assertion copy-pasted
through an editor that re-indented it. Canonicalisation is byte-exact — one added newline *inside*
the signed element is enough.

### Cause 5 — algorithm mismatch

PingFederate signing with **RSA-SHA1** against a platform that has SHA-1 disabled looks, from the
app's side, exactly like a bad signature. Move the connection to **RSA SHA-256**
(*Credentials → Digital Signature Settings*).

### Cause 6 — signing the wrong thing

Ping can sign the **Response**, the **Assertion**, or both. If the SP demands a signature on an
element that is not signed, you get a rejection that reads like a signature failure. Ping's setting:
*Browser SSO → Protocol Settings → Signature Policy*.

---

## 6. Prove it — the empirical check (Law 12)

Talk is cheap. Here is how to *see* the cause with your own eyes.

### Option A — let the lab app tell you (30 seconds)

The `saml-sp` lab app captures the rejected response and diagnoses it.

1. Try the SAML login and let it fail.
2. You land back on the dashboard with a red banner: **"SAML login rejected — invalid_signature"**.
3. **Log in with the app's own username and password** (Step 0). *(The diagnosis contains a real
   person's attributes, so it needs a session — and the local login is deliberately independent of
   the IdP, which is exactly why it still works when SSO is broken.)*
4. Click **"Show me why →"**.

You get, side by side:

```
The key the IdP signed with
  SHA-256  5E:EB:5F:E0:01:76:C3:11:C1:0A:7C:7D:D0:49:2E:07:…   ✗ we do NOT trust this key

The keys this app trusts
  SHA-256  9A:F2:9F:70:0B:15:E0:9F:B5:FD:67:80:1B:FE:1A:22:…
```

**Two different fingerprints. That is the root cause, on screen, in one line.**

Same page also cross-checks Issuer, Audience, Destination and the Conditions window — so you find
out about the *next* failure before you go round again.

### Option B — do it by hand (the version you should be able to do in an interview)

**1. Capture the response.** Install **SAML-tracer** (Firefox/Chrome), run the login, copy the
`SAMLResponse` form value.

**2. Decode it and pull out the certificate the IdP used.**

```bash
# Bash
echo "$SAML_RESPONSE" | base64 -d > response.xml
# the certificate the IdP advertised inside the signature
python3 - <<'EOF' > idp-used.crt
import re
xml = open('response.xml').read()
b64 = re.search(r'<[^>]*X509Certificate[^>]*>([^<]+)<', xml).group(1)
body = "".join(b64.split())
print("-----BEGIN CERTIFICATE-----")
print("\n".join(body[i:i+64] for i in range(0, len(body), 64)))
print("-----END CERTIFICATE-----")
EOF
openssl x509 -in idp-used.crt -noout -fingerprint -sha256 -subject -dates
```

```powershell
# PowerShell (Windows 11)
[IO.File]::WriteAllBytes("response.xml", [Convert]::FromBase64String($env:SAML_RESPONSE))
$xml  = [xml](Get-Content response.xml)
$b64  = $xml.GetElementsByTagName("X509Certificate") | Select-Object -First 1 -ExpandProperty '#text'
$cert = [Security.Cryptography.X509Certificates.X509Certificate2]::new([Convert]::FromBase64String($b64))
$cert | Format-List Subject, NotBefore, NotAfter, Thumbprint
```

**3. Fingerprint the certificate you configured.**

```bash
openssl x509 -in the-cert-i-configured.crt -noout -fingerprint -sha256 -subject
```

**4. Compare.**

| Result | Conclusion |
|---|---|
| **Fingerprints differ** | ✅ found it — Cause 1. Wrong public key. Fix per §7. |
| **Fingerprints match** | the key is fine — go look at Cause 4 (altered bytes) or Cause 5 (algorithms) |

> ✅ **Checkpoint.** `openssl x509 -fingerprint -sha256` and the PingFederate console show the same
> value for the same certificate. If they do not, you are looking at two different certificates —
> which *is* the finding.

> ⚠️ **Do not "trust" the certificate you found in step 2.** It came out of the message you are
> trying to validate — anyone could have put it there. It is a **clue about which key was used**,
> never a credential. Real verification always runs against the certificate you configured ahead of
> time. (This distinction is the whole reason SAML libraries that verify against the embedded
> `KeyInfo` are catastrophically broken — see §9.)

---

## 7. The fix — and why metadata import is the *correct* fix, not just the fast one

### Do this

**Import the IdP's SAML metadata file.**

In PingFederate: **System → Protocol Metadata → Metadata Export**, or fetch the published URL
**with** the `PartnerSpId` parameter for your connection.

In the lab app: **Step 2 → Import the IdP's metadata → Preview → Import.** Takes effect on the next
login. **No redeploy.**

### Why this is not merely convenient

An SP needs exactly three facts about its IdP:

| Fact | Typed by hand | From metadata |
|---|---|---|
| Entity ID | a URL you can typo | read from the file |
| SSO URL | a URL you can typo | read from the file |
| **Signing certificate** | **a 1,600-character base64 blob you can mangle, or that can silently go stale** | **read from the file — always current, always exact** |

Metadata is those three facts **in a document the IdP generated about itself**. Importing it deletes
the transcription step — and with it, Causes 1 and 2 entirely. That is not a convenience; it is the
structural fix.

> **It also fixes rotation for free.** During a key rotation the IdP publishes *both* certificates.
> Importing metadata trusts both, so the switchover cannot lock you out. Trusting several public
> keys costs you nothing — an attacker gains no power from a longer list of keys they do not hold.

### The catch you must know about

**A metadata file is not self-authenticating.** Unless it is XML-signed by a key you already trust,
importing one is a decision to trust **whoever handed you the file**. So:

1. Fetch it over **HTTPS**, from a host you trust, or export it from the console yourself.
2. **Check the certificate fingerprint** in the preview against the PingFederate console *before*
   you import. That is the whole reason the lab app splits Preview from Import.

> **At FinCo:** for a partner federation, the fingerprint check is not optional ceremony — it is the
> control that stops a metadata file from an email attachment silently re-pointing a production SP.

### If you truly cannot use metadata

Set `LAB_SAML_IDP_CERTIFICATE` by hand — and during a rotation, **paste both PEM blocks one after
the other**. The lab app accepts several concatenated certificates for exactly this reason.

---

## 8. The 60-second RCA checklist

Work down this list. Stop at the first ✗.

- [ ] **1.** Does the app say it is trusting a *placeholder* certificate? → It is your app, not the IdP. Configure it.
- [ ] **2.** Do the **SHA-256 fingerprints** — the one in the response vs the one you configured — match? → If not, **that is your root cause**.
- [ ] **3.** Is the configured certificate **expired**, or **not yet valid**? → Rotation overdue, or clock skew.
- [ ] **4.** Did you fetch Ping's metadata **with `?PartnerSpId=…`**? → Without it you may have the wrong connection's certificate.
- [ ] **5.** Is Ping signing with **RSA SHA-256**? → SHA-1 on a hardened platform reads as a bad signature.
- [ ] **6.** Is the element you require signed **actually signed** (Response vs Assertion)?
- [ ] **7.** Is anything between Ping and the app **rewriting the POST body**? → Proxy, WAF, URL-rewriter.
- [ ] **8.** Read **PingFederate's `server/default/log/audit.log`** — one line per SSO transaction, and it names the connection.

---

## 9. Pair it with the defence (Law 9)

Signature verification is a *security control*, so understand how it gets broken.

| Attack | What the attacker does | Defence |
|---|---|---|
| **Verify against the embedded key** | Puts their **own** certificate in `<ds:KeyInfo>` and signs the assertion with the matching private key. A naive SP "verifies" it — successfully — and lets them in as anyone. | **Verify only against certificates configured ahead of time.** Never against `KeyInfo`. Spring Security does the right thing here; some hand-rolled SPs do not. |
| **XML Signature Wrapping (XSW)** | Keeps the genuine signed assertion in the document but adds a second, unsigned one, hoping the SP validates one element and reads another. | Use a maintained library (OpenSAML, Spring Security SAML). Confirm the SP reads the **same element** it verified. |
| **Signature stripping** | Removes the signature and hopes the SP accepts an unsigned assertion. | Configure the SP to **require** a signature. Never make it optional. |
| **Stale key after rotation** | Old private key leaks; SP still trusts the old certificate. | Remove retired certificates from the trust list after the rotation completes. Trusting both is for the *window*, not forever. |

**Detection (blue-team view — hand this to Heimdall):** a **spike in `invalid_signature` across
many SP connections at once** is a rotation gone wrong (availability incident). The same error on
**one** connection from **one** source, repeatedly, is worth looking at as a possible forgery
attempt. Log the error code and the connection ID; alert on the ratio, not the raw count.

---

## What you learned

- **Why signatures exist:** the assertion travels through a browser you do not control, so it needs a property a hostile courier cannot forge.
- **How a keypair does that:** the private key signs, the public key verifies, and only that asymmetry makes the note trustworthy.
- **What XML signing adds:** canonicalise → hash → sign the hash. Two checks, two distinct failure meanings.
- **What "invalid signature" nearly always is:** the SP is holding the **wrong public key**.
- **How to prove it in one line:** compare **SHA-256 fingerprints**, not subject names.
- **The structural fix:** import the IdP's metadata — and verify the fingerprint before you do.

## Next

- Run it yourself: [lab 04 — PingFederate app onboarding](../labs/04-pingfederate-app-onboarding/README.md), §7 *Fixing an invalid signature*.
- The console click-path: [`PINGFED-SETUP.md`](../labs/04-pingfederate-app-onboarding/PINGFED-SETUP.md) §1.6.
- Certificate lifecycle in depth: [note 16 — SAML bindings and certificates](16-saml-bindings-and-certificates.md).
- The same trust problem in OAuth/OIDC — where JWKS replaces metadata: [note 21 — OAuth 2.0 complete reference](21-oauth2-complete-reference.md).
