# Lab 05 — A PingFederate SP connection with **no LDAP and no database**

> **Janus specified it, Lefler wrote it up.** Build a complete, working **SAML 2.0 SP connection**
> in PingFederate from an empty server, where the thing that actually checks the user's password is
> a **list of usernames stored inside PingFederate itself** — no Active Directory, no LDAP, no
> JDBC datastore, nothing external at all.
>
> **Authorized-lab-only.** Your own PingFederate instance, a test SP, dummy users, throwaway
> passwords. Never a production connection, never a real FinCo credential, never a real customer's
> name in the user table.

- **Time:** ~60–75 min the first time · ~15 min once you know the click-path · **Difficulty:** beginner → intermediate
- **Platform:** any machine with a browser that can reach your PingFederate. Commands are given for **Windows 11 (PowerShell)** and **Bash**.
- **Prereqs:**
  - A PingFederate **11.x or 12.x** instance you control, with admin-console access. A trial install on a laptop is perfect.
  - [note 18 — PingFederate explained](../../notes/06-platforms-and-gateways/18-pingfederate-explained.md) and [note 02 — SAML deep dive](../../notes/02-saml/02-saml-deep-dive.md). If SAML is brand new to you, read note 02 first — this lab assumes you know what an *assertion* is.
  - **A test SP to log in to.** Easiest: the `saml-sp` app from [Lab 04](../04-pingfederate-app-onboarding/README.md). §9 gives you two other options if you don't want to deploy anything.
- **You'll be able to:** stand up an authentication chain end to end — **credential validator → adapter → policy contract → sign-on policy → SP connection** — explain what each of those five objects is *for*, fill an attribute contract when your credential store has no attributes to give you, and say out loud why this setup is fine in a lab and a finding in production.

---

## TL;DR — the whole lab on one screen

You will create **five objects**, in this order. Each one feeds the next.

| # | Object | Console location | What it is, in plain words |
|---|---|---|---|
| 1 | **Password Credential Validator** (Simple Username Password) | Authentication → Integration → **Password Credential Validators** | The list of usernames and passwords, stored inside PingFederate. *"Is this password right?"* |
| 2 | **IdP Adapter** (HTML Form) | Authentication → Integration → **IdP Adapters** | The login page the user actually sees and types into. *"Ask them for a password."* |
| 3 | **Authentication Policy Contract** | Authentication → Policies → **Policy Contracts** | A fixed list of attribute names that authentication promises to deliver. *"Whatever happened, you get `subject`, `email`, `firstName`."* |
| 4 | **Sign-On Policy** | Authentication → Policies → **Policies** | The flowchart: which adapter runs, in what order, and what fills the contract. |
| 5 | **SP Connection** | Applications → Integration → **SP Connections** | The app itself: where to send the assertion, what's in it, how it's signed. |

**The one sentence to remember:** the SP connection never knows *how* the user proved who they
were — only *that* they did, and what attributes came out. That seam is why swapping LDAP for a
hardcoded list changes **nothing** downstream, and it is the whole point of this lab.

---

## §0 — Why are we doing this? (read this part, don't skip it)

### 0.1 The question behind the question

Every PingFederate tutorial you find starts with *"first, configure your LDAP datastore."* So the
obvious question is: **if every real deployment uses a directory, why would I deliberately build
one without?**

Four honest reasons. The fourth is the real one.

**Reason 1 — You can't always get a directory.** A trial PingFederate on your laptop has no AD next
to it. Standing up an LDAP server just to test a *SAML* configuration means debugging two products
at once, and the one you're trying to learn is not the one that will break first.

**Reason 2 — It isolates the variable.** This is the professional reason. When an SP connection
fails, the failure could be in the directory (bad bind account, wrong search base, user not in the
right OU), in the adapter, in the policy, in the attribute mapping, in the certificate, or in the
SP. That's six suspects. Swap the directory for a hardcoded list and you have **five**, and you have
*proved* the directory wasn't it. This is the same move as `ping 8.8.8.8` before you blame DNS.

> **The FinCo ticket this solves.** "The new payments SP won't log in." You have no idea whether
> it's the connection or the directory. You build a `LAB-Local-Users` validator, point a *copy* of
> the policy at it, and log in. Works → the connection is fine, go talk to the directory team.
> Fails → the connection is yours to fix. Ten minutes, and you've turned an argument into a fact.

**Reason 3 — You can onboard the app before the directory team is ready.** Real onboarding is
blocked on things that aren't technical: a bind account request, a firewall rule, a service-account
approval. You can build and test the *entire* SP connection against local users while you wait, then
change **one field** in the policy when the directory is finally available. Nothing else moves.

**Reason 4 — It makes the architecture visible.** This is the one that will make you better at the
job. When there's an LDAP in the picture, it's easy to believe PingFederate "does LDAP login" as one
feature. It doesn't. It does *four separate things* that happen to be chained together, and the
chain is only obvious when you rip out the directory and everything still works.

### 0.2 Deriving the design — why does the "Password Credential Validator" exist as its own object?

Don't memorise the object list. **Derive it.** Ask what an IdP must do, and the objects fall out.

An identity provider has to answer one question — *"is this really them?"* — for **many kinds of
credential**, from **many kinds of user interface**, for **many different applications**. So three
things vary independently:

| What varies | Examples |
|---|---|
| **Where the truth lives** | Active Directory · an LDAP server · a SQL table · a RADIUS server · a list in a config file |
| **How you collect the credential** | an HTML login form · Kerberos/Windows SSO · a client certificate · a push to a phone |
| **Which app is asking** | the payments app · the HR portal · a partner's SP |

If PingFederate had built these as one lump — "LDAP form login" — then supporting a SQL table would
mean rewriting the form, and adding a second app would mean copying the whole thing. **Three
independent dimensions must become three separate objects**, or you get a combinatorial explosion.

So:

- **Where the truth lives** → the **Password Credential Validator (PCV)**. Its entire job: take a
  username and a password, return yes or no. It has no UI.
- **How you collect it** → the **IdP Adapter**. Its entire job: render something the user interacts
  with, collect the credential, hand it to a validator. It doesn't know *how* the password is checked.
- **Which app is asking** → the **SP Connection**. Its entire job: build and sign an assertion for
  one specific app. It doesn't know *how* the user authenticated.

And because the third one must not depend on the first two, you need one more thing in the middle —
a **stable set of attribute names** that authentication always delivers, no matter which adapter
ran. That's the **Authentication Policy Contract**. The **Sign-On Policy** is just the wiring that
decides which adapter runs and fills that contract.

> **That's five objects, and now none of them are arbitrary.** Each one exists because something had
> to vary independently. This derivation is worth more than the click-path below — the click-path
> changes between versions, the reasoning doesn't.

**The Simple Username Password Credential Validator is the proof.** It is the smallest possible
thing that satisfies the PCV interface: a list. If you can plug a *list* into the same socket that
normally holds Active Directory, and the four objects downstream don't notice, then the seam is
real — not marketing.

### 0.3 The honest limits (this is a lab tool, and here's exactly why)

Being able to say *why* this is lab-only, specifically, is the difference between a junior and a
senior answer in an interview or an audit meeting.

| What a real directory gives you | What the Simple PCV gives you | Why that matters at FinCo |
|---|---|---|
| Attributes: `mail`, `givenName`, `department`, group memberships | **`username`. That's it.** | Your attribute contract has nothing to fill from. §4.4 shows the workaround, and it's a workaround |
| Password policy: complexity, expiry, history, account lockout | A retry counter on the login form | PCI-DSS 8.3.x expects enforced password controls. A retry counter is not that |
| Central deprovisioning — disable in AD, disabled everywhere | Users exist only in this PingFederate's config | A leaver stays live here. This is *the* audit finding |
| Its own audit trail of authentications and admin changes | Changes ride along in the PingFederate config archive | "Who added this user, and when?" has no clean answer |
| Self-service password reset, MFA integration, delegated admin | None | No path to satisfy MFA requirements from this validator alone |

**So the rule is:** Simple PCV for labs, proofs-of-concept, break-glass diagnostics, and demos.
**Never** as the authentication source for anything a real user or real money touches. §11 covers
how to detect one that got left behind, because that's the way this actually goes wrong.

---

## §1 — The mental model: follow one login through the chain

Before you click anything, hold this picture. Every screen you're about to fill in is one box here.

```
  ┌────────────┐
  │  browser   │  user clicks "Log in" in the app
  └─────┬──────┘
        │  1. SAML AuthnRequest  ("who is this?")
        ▼
  ┌───────────────────────────────────────────────────────────────┐
  │  PingFederate                                                 │
  │                                                               │
  │   ┌──────────────────────┐                                    │
  │   │ 2. Sign-On Policy    │  "which adapter runs for this?"    │
  │   └──────────┬───────────┘                                    │
  │              ▼                                                │
  │   ┌──────────────────────┐                                    │
  │   │ 3. HTML Form Adapter │  renders the login page  ◄─── user types
  │   └──────────┬───────────┘                          username+password
  │              │  hands the credential down                     │
  │              ▼                                                │
  │   ┌──────────────────────────────────────┐                    │
  │   │ 4. Simple Username Password          │  ✅ / ❌            │
  │   │    Credential Validator (the LIST)   │  ← the only part   │
  │   └──────────┬───────────────────────────┘    LDAP would      │
  │              │  returns: username                replace      │
  │              ▼                                                │
  │   ┌──────────────────────┐                                    │
  │   │ 5. Policy Contract   │  subject / email / firstName       │
  │   └──────────┬───────────┘  (a fixed set of names)            │
  │              ▼                                                │
  │   ┌──────────────────────┐                                    │
  │   │ 6. SP Connection     │  builds + signs the assertion      │
  │   └──────────┬───────────┘                                    │
  └──────────────┼────────────────────────────────────────────────┘
                 │  7. signed SAML assertion, POSTed to the ACS URL
                 ▼
           ┌────────────┐
           │  the app   │  verifies the signature, creates a session
           └────────────┘
```

**Read box 4 again.** That's the *only* box that changes when FinCo swaps this for Active
Directory. Boxes 2, 3, 5, 6 and 7 are byte-for-byte identical. That's what you're proving.

---

## §2 — Prep the server (the ground-up bit everyone skips)

If your PingFederate is brand new, do these four things first. On an existing lab server, check them
and move on.

### 2.1 Turn on the IdP role

**System → Server → Protocol Settings → Roles & Protocols**

| Field | Value |
|---|---|
| Enable Identity Provider (IdP) role | ✅ tick it |
| … and support the following | ✅ **SAML 2.0** |

> ⚠️ **Gotcha.** If the IdP role is off, the **Applications → Integration → SP Connections** menu is
> either missing or refuses to save. If you can't find the menu, this is why — you are not going mad.

### 2.2 Set the Base URL and your SAML entity ID

**System → Server → Protocol Settings → Federation Info**

| Field | Value | Why |
|---|---|---|
| **Base URL** | `https://pf.lab.local:9031` — your runtime host and port, no trailing slash | Every endpoint PingFederate publishes is built from this. Wrong here = every URL in your metadata is wrong |
| **SAML 2.0 Entity ID** | e.g. `urn:lab:pingfederate:idp` | Your IdP's permanent name. The SP will match on it exactly |

> ⚠️ **The two ports.** PingFederate listens on **9999** for the admin console and **9031** for
> runtime SSO traffic. Users never touch 9999. If your Base URL says 9999, logins break in a way that
> makes no sense until you spot it.

> 🔍 **Entity ID is a name, not an address.** It looks like a URL but nothing fetches it. It's a
> globally unique string both sides agree on. Pick it once and never change it — changing it later
> breaks every connection at once.

**✅ Checkpoint.** Fetch the IdP metadata over the runtime port:

```bash
curl -sk https://pf.lab.local:9031/pf/federation_metadata.ping | head -20
```
```powershell
# PowerShell
curl.exe -sk https://pf.lab.local:9031/pf/federation_metadata.ping | Select-Object -First 20
```

You should see an `<EntityDescriptor entityID="urn:lab:pingfederate:idp">`. If you get connection
refused, the runtime port is wrong or blocked.

### 2.3 Create a signing certificate

The assertion has to be **signed**, so the app can tell it really came from you. In a lab, a
self-signed certificate is fine — the SP will trust it because you hand it over directly.

**Security → Certificate & Key Management → Signing & Decryption Keys & Certificates → Create New**

| Field | Value |
|---|---|
| Common Name | `pf-lab-idp-signing` |
| Organization / Country | anything — it's a lab |
| Validity (days) | `365` |
| Key Algorithm | **RSA**, key size **2048** (or 3072) |
| Signature Algorithm | **RSA SHA256** |

**Then note its SHA-256 fingerprint.** Click into the certificate and copy the fingerprint into your
notes now. You'll compare it against what the SP trusts in §8, and it's the single fastest way to
resolve a signature failure later.

> ⚠️ **`SHA1withRSA` is a red flag.** Some older builds still default to it. Pick **RSA SHA256**
> explicitly. SHA-1 signatures are rejected outright by modern SP libraries and will fail with an
> unhelpful error.

### 2.4 Know your runtime endpoints

Write these down — you'll paste them into the SP in §8.

| Endpoint | URL |
|---|---|
| SSO (SP-initiated) | `https://pf.lab.local:9031/idp/SSO.saml2` |
| Single Logout | `https://pf.lab.local:9031/idp/SLO.saml2` |
| IdP-initiated SSO | `https://pf.lab.local:9031/idp/startSSO.ping?PartnerSpId=<the SP's entity ID>` |
| IdP metadata | `https://pf.lab.local:9031/pf/federation_metadata.ping?PartnerSpId=<the SP's entity ID>` |

> ⚠️ **The `PartnerSpId` gotcha, and it's a nasty one.** On the metadata URL, *always* include
> `PartnerSpId`. Without it, PingFederate serves its **default** signing certificate. If this
> connection signs with a different one, the SP imports everything cleanly and then **every login
> fails** with an invalid-signature error while nothing looks wrong anywhere. This burns people
> constantly — see [note 34](../../notes/02-saml/34-saml-invalid-signature-rca.md).

---

## §3 — Object 1: the Password Credential Validator (the list)

**This is the object that replaces LDAP.** Everything after this point is identical whether the
truth lives in a list or in Active Directory.

**Authentication → Integration → Password Credential Validators → Create New Instance**

**Screen 1 — Type**

| Field | Value |
|---|---|
| Instance Name | `LAB-Local-Users` |
| Instance ID | `LABLocalUsers` (auto-fills; no spaces allowed) |
| Type | **Simple Username Password Credential Validator** |

**Screen 2 — Instance Configuration**

Click **Add a new row to 'Users'** for each user. Add two, so you can prove the negative case later:

| Username | Password |
|---|---|
| `labuser` | a throwaway you'll type a lot, e.g. `Lab-Passw0rd!` |
| `labuser2` | a different throwaway |

Click **Update** on each row — **the row is not saved until you do.**

> ⚠️ **The #1 mistake on this screen.** You type the row, hit **Next**, and the user silently
> vanishes. You *must* click **Update** on the row first. If your user list looks empty on the
> summary screen, this is why.

> 🔒 **Naming is a control, not decoration.** Prefix it `LAB-`. In §11 you'll see that the way these
> cause incidents is by being forgotten, and a name that screams "lab" is what makes a quarterly
> review catch it. Adopt this habit now, in your own lab, so it's automatic at FinCo.

> 🔒 **Never a real password.** Not yours, not a colleague's, not a pattern you use elsewhere. These
> live in the PingFederate config, which ends up in config archives and backups.

**Screen 3 — Extended Contract**

Leave it empty. **Here's the important part:** this validator hands back exactly one attribute —
`username`. There is no `mail`, no `givenName`, no groups, because there's no directory to read them
from. Adding names here gives you empty values, not data.

> 💡 **This is the trade-off from §0.3 made concrete.** Hold on to it — in §4.4 you'll see what it
> costs and how to work around it.

**Screen 4 — Summary** → **Save**.

**✅ Checkpoint.** The Password Credential Validators list shows `LAB-Local-Users`, type
**Simple Username Password Credential Validator**. Nothing uses it yet — it's an ingredient.

---

## §4 — Object 2: the IdP Adapter (the login page)

The PCV can check a password but can't ask for one. The **adapter** is the part the user sees.

**Authentication → Integration → IdP Adapters → Create New Instance**

### 4.1 Type

| Field | Value |
|---|---|
| Instance Name | `LAB-HTML-Form` |
| Instance ID | `LABHTMLForm` |
| Type | **HTML Form IdP Adapter** |
| Parent Instance | **None** |

### 4.2 IdP Adapter (the configuration screen)

**The one required field:** click **Add a new row to 'Credential Validators'** and select
**`LAB-Local-Users`**. Click **Update**.

*(Yes — it's a table, not a dropdown. You can list several validators and PingFederate tries them in
order. That's how a real deployment falls back from one directory to another. You need one.)*

Everything else has a sane default. The handful worth understanding:

| Field | Set it to | What it does |
|---|---|---|
| **Challenge Retries** | `3` | Wrong-password attempts allowed before the flow fails. **This is not account lockout** — it's per-flow. Nothing gets disabled; the user opens a new tab and gets 3 more. A real directory does lockout; the Simple PCV cannot |
| **Session State** | `None` | Whether the adapter remembers this login. `None` keeps the lab honest — you get the login form on every test instead of wondering why it skipped |
| Allow Password Changes | **unticked** | Needs a Password Management System. The Simple PCV isn't one |
| Password Reset Type | **None** | Same reason |
| Enable 'Remember My Username' | unticked | Fewer moving parts while you're learning |
| Login Template | `html.form.login.template.html` | The default page. You can rebrand it later by editing the template on disk |

### 4.3 Extended Contract

Add two attributes: **`email`** and **`firstName`**.

> **Why add them when §3 just told you the validator has no data for them?** Because the *contract*
> and the *source* are separate things. Declaring them here means the adapter promises to output
> them; §4.4 is where you decide what fills them. Contract first, data second — that ordering is
> the whole PingFederate mental model.

### 4.4 Adapter Attributes — and the attribute problem, solved

| Attribute | Pseudonym | Mask |
|---|---|---|
| `username` | ✅ **tick it** | no |
| `email` | no | no |
| `firstName` | no | no |

**Pseudonym** = "this is the value that identifies the user." Tick exactly one. `username` is it.

**Now the interesting screen: Adapter Contract Mapping → Contract Fulfillment.**

This is where you tell PingFederate what fills each attribute. With a directory you'd pick
**Datastore** and choose an LDAP field. You have no datastore. Use **Text** instead — a literal
value, which can reference other attributes with `${...}`:

| Attribute | Source | Value |
|---|---|---|
| `username` | **Adapter** | `username` |
| `email` | **Text** | `${username}@lab.local` |
| `firstName` | **Text** | `Lab` |

**✅ Checkpoint on your understanding.** `${username}@lab.local` turns `labuser` into
`labuser@lab.local`. It is a **string built from the login name** — it is not a lookup, nothing
verified that mailbox, and it is not identity data. It exists so the downstream contract has a
value of the right shape to carry.

> 🔍 **This is exactly the limit from §0.3, and it's worth naming out loud.** With a real directory,
> `email` is *authoritative* — the SP can act on it. Here it's *fabricated*. If an SP made an
> authorization decision on `email`, this configuration would be handing it a value invented by
> string concatenation. **That is the actual reason this is lab-only** — not the missing password
> policy, though that too. Remember this the next time someone suggests a "quick local user" in a
> real environment.

**Summary → Save.**

**✅ Checkpoint.** IdP Adapters shows `LAB-HTML-Form`, type **HTML Form IdP Adapter**.

---

## §5 — Object 3: the Authentication Policy Contract (the promise)

**Authentication → Policies → Policy Contracts → Create New Contract**

| Screen | Field | Value |
|---|---|---|
| Contract Info | Contract Name | `LAB-Policy-Contract` |
| Contract Attributes | (core, already there) | `subject` |
| | Extend the Contract | add **`email`**, add **`firstName`** |

**Save.**

### Why does this object exist at all? (it looks redundant — it isn't)

The adapter already produces `username`, `email`, `firstName`. So why copy them into a second
contract?

**Because of what happens next month.** Suppose FinCo adds step-up MFA for the payments app. Now
some users authenticate through the HTML form, and some through form-*then*-MFA. Different adapters,
different output attribute names.

- If your SP connection maps directly to **`LAB-HTML-Form.username`**, it is now welded to one
  specific adapter. Change the authentication and **you must edit every connection.**
- If it maps to **`LAB-Policy-Contract.subject`**, the policy can route through one adapter, five
  adapters, or a completely different mechanism — and the SP connection never changes, because the
  contract still delivers `subject`.

**In one line:** the policy contract is an *interface*; adapters are *implementations*. The
indirection buys you the ability to change authentication without touching applications — and with
dozens of connections, that difference is a weekend of change tickets versus one field.

> ⚠️ **You can skip it.** PingFederate lets an SP connection map an adapter instance directly, and
> for a one-app lab it works fine. **Don't.** Building the contract is the point of this lab — it's
> the thing you'll be maintaining at work.

---

## §6 — Object 4: the Sign-On Policy (the wiring)

**Authentication → Policies → Policies**

### 6.1 The gotcha that costs people an hour

At the top of the Policies screen there's a checkbox: **IdP Authentication Policies** (some builds:
*"Enable IdP Authentication Policies"*).

**Tick it.** If it's unticked, PingFederate ignores every policy you build and falls back to
whatever adapter is mapped directly on the connection. Your policy exists, looks perfect, and does
nothing.

> ⚠️ **Symptom to recognise:** you edit the policy, nothing changes at runtime, and there's no error
> anywhere. Check this checkbox first, always.

### 6.2 Build the policy

**Add Policy**

| Field | Value |
|---|---|
| Name | `LAB-Sign-On-Policy` |
| Description | `Local users only — lab. No directory.` |

Now you build a small tree, not a form. Click it like a flowchart:

1. **Policy** (the first dropdown) → select **`LAB-HTML-Form`**.
   That's the root: *"start by running the HTML form adapter."*

2. Two branches appear — **Fail** and **Success**.

3. On **Fail** → choose **Done**.
   *(The user couldn't authenticate. There's nothing else to try — no second directory, no fallback.
   In a real policy this branch is where you'd route to another source.)*

4. On **Success** → choose **Policy Contract** → **`LAB-Policy-Contract`** → then click
   **Contract Mapping** and fill it in:

| Contract attribute | Source | Value |
|---|---|---|
| `subject` | **Adapter (`LAB-HTML-Form`)** | `username` |
| `email` | **Adapter (`LAB-HTML-Form`)** | `email` |
| `firstName` | **Adapter (`LAB-HTML-Form`)** | `firstName` |

5. **Done** → **Save**.

> 💡 **Read the tree back to yourself:** *"Run the form. If it fails, stop. If it succeeds, fill
> `LAB-Policy-Contract` from the adapter's output."* That sentence **is** the policy. Every
> production policy you'll ever meet is that same shape with more branches — an MFA adapter on one
> path, a selector that checks the network on another.

**✅ Checkpoint.** The Policies list shows `LAB-Sign-On-Policy`, enabled, with `LAB-HTML-Form` as its
first authentication source, and the **IdP Authentication Policies** checkbox is ticked.

---

## §7 — Object 5: the SP Connection (the app)

Four objects down. **None of them mentioned SAML.** That's the seam working — authentication was
built without any knowledge of who'd consume it. Now we consume it.

**Applications → Integration → SP Connections → Create Connection**

### 7.1 The opening screens

| Screen | What to set |
|---|---|
| Connection Template | **Do not use a template** |
| Connection Type | ✅ **Browser SSO Profiles**, Protocol **SAML 2.0** |
| Connection Options | ✅ **Browser SSO** (leave OAuth and provisioning unticked) |
| Import Metadata | **File** if your SP gave you a metadata XML (fastest, fewest typos), or **None** to type it by hand — §7.2 covers both |
| General Info | **Partner's Entity ID (Connection ID)** = the SP's entity ID, exactly as the SP states it. **Connection Name** = a human label, `LAB SAML SP (local users)`. **Base URL** = the SP's base URL |

> ⚠️ **Copy, never retype.** Entity IDs and ACS URLs are matched **character for character**. One
> trailing slash, one `http` where it should be `https`, and PingFederate rejects the request with a
> message that doesn't point at the typo.

### 7.2 Browser SSO → Assertion Creation

**Browser SSO → Configure Browser SSO**

**SAML Profiles**

| Field | Value |
|---|---|
| SP-Initiated SSO | ✅ — the app sends users to Ping. This is the normal case |
| IdP-Initiated SSO | ✅ — you start at Ping. Handy for testing without touching the app |
| SP-Initiated SLO / IdP-Initiated SLO | optional; tick if your SP supports logout |

**Assertion Lifetime** — leave the defaults (5 minutes before / after).

> 💡 **Why an assertion expires in minutes.** It's a bearer credential: anyone holding it can replay
> it. A short window is the mitigation, which is why **clock skew between your IdP and SP is a real
> failure mode**. If both machines aren't on NTP, you get "assertion not yet valid" errors that come
> and go.

**Assertion Creation → Identity Mapping**

| Option | Choose |
|---|---|
| **Standard** | ✅ — the SP sees the real username in the subject. What you want in a lab, so you can read it |
| Pseudonym | an opaque per-SP identifier — privacy-preserving, unreadable while debugging |
| Transient | a fresh random ID every login — anonymous SSO |

**Assertion Creation → Attribute Contract**

This is **the SAML-facing contract** — the attribute names that go inside the assertion. (Yes, this
is the *third* contract. Adapter contract → policy contract → attribute contract. Each one is a
handoff between two layers that shouldn't know about each other.)

| Attribute | Note |
|---|---|
| `SAML_SUBJECT` | already there — the core identity |
| `email` | **Extend the contract** to add it. Format: `urn:oasis:names:tc:SAML:2.0:attrname-format:basic` |
| `firstName` | same |

> ⚠️ **The names must match what the SP expects**, not what looks tidy to you. Some SPs want
> `mail`, some want the full URN `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress`.
> **Ask the app team for their expected attribute names before you build this**, and put it in the
> onboarding ticket. Half of all "SSO works but the app says access denied" tickets are this.

### 7.3 Authentication Source Mapping — the moment it all connects

**Assertion Creation → Authentication Source Mapping → Map New Authentication Policy Contract**

| Field | Value |
|---|---|
| Authentication Policy Contract | **`LAB-Policy-Contract`** |

*(There's also **Map New Adapter Instance** — that's the shortcut past the policy. §5 explains why
you're not using it.)*

**Attribute Contract Fulfillment** — the final wiring:

| SAML attribute | Source | Value |
|---|---|---|
| `SAML_SUBJECT` | **Authentication Policy Contract** | `subject` |
| `email` | **Authentication Policy Contract** | `email` |
| `firstName` | **Authentication Policy Contract** | `firstName` |

**Issuance Criteria** — leave empty for now.

> 💡 **What Issuance Criteria is for, in one line:** *"even if authentication succeeded, refuse to
> issue this assertion unless <condition>."* That's how FinCo restricts an app to one department —
> a condition on a group attribute. You can't demo it here, because the Simple PCV has no groups.
> One more thing the directory buys you.

**✅ Trace the whole chain out loud** — if you can say this, you understand the lab:

```
labuser types a password
  → LAB-HTML-Form collects it
  → LAB-Local-Users says yes, returns username=labuser
  → adapter contract fills username / email / firstName
  → LAB-Sign-On-Policy copies those into LAB-Policy-Contract (subject/email/firstName)
  → SP connection copies those into SAML_SUBJECT / email / firstName
  → assertion is signed and POSTed to the app
```

### 7.4 Protocol Settings

| Screen | What to set |
|---|---|
| **Assertion Consumer Service URL** | The SP's **ACS URL** — where the assertion gets POSTed. Binding **POST**, ✅ **Default**. Imported already if you used metadata |
| Allowable SAML Bindings | ✅ **POST**. (Redirect is for requests, POST for assertions — an assertion is too big for a URL) |
| **Signature Policy** | ✅ **Always sign the SAML Assertion**. Tick **Require AuthN requests to be signed** only if your SP actually signs them — if it doesn't, this rejects every login |
| Encryption Policy | **None** to start. Turn it on later as an experiment; the SP needs the matching private key |

> ⚠️ **"Require AuthN requests to be signed" is the classic self-inflicted wound.** It's good
> practice and it's the right answer in production — but tick it only when you've confirmed the SP
> signs, *and* you've imported the SP's certificate in §7.5. Otherwise you get a signature failure
> before the user ever sees a login page.

### 7.5 Credentials

**Credentials → Configure Credentials**

| Screen | What to set |
|---|---|
| **Digital Signature Settings** | **Signing Certificate** = the one from §2.3. **Signing Algorithm** = **RSA SHA256**. ✅ **Include the certificate in the `<KeyInfo>` element** |
| **Signature Verification Settings** | Only needed if you required signed AuthnRequests: **Manage** → **Unanchored** → **Import Certificate** → paste the SP's **public** certificate |

> 🔍 **Why tick "include the certificate in `<KeyInfo>`"?** It embeds your public certificate in the
> assertion itself. When a signature is rejected, the SP can then tell you **which key signed it**,
> instead of only that the key it holds didn't work. That one checkbox turns a half-day of guessing
> into a fingerprint comparison. It's not a security weakening — the certificate is public by design.

### 7.6 Save and activate

**Save** the connection, then on the SP Connections list set its status to **Active**.

> ⚠️ A saved-but-inactive connection returns "unknown connection" at runtime. Check the toggle.

---

## §8 — The other half of the handshake: give the SP *your* details

**Everything so far told PingFederate about the app. Nothing has told the app about PingFederate.**
Both halves are required, and forgetting this one is the most common reason a brand-new connection
fails on the very first login.

The SP needs three facts:

| Fact | Where you get it |
|---|---|
| IdP **entity ID** | §2.2 |
| IdP **SSO URL** | `https://pf.lab.local:9031/idp/SSO.saml2` |
| IdP **signing certificate** | §2.3 — the public certificate |

Hand over the metadata document rather than typing three fields:

**System → Protocol Metadata → Metadata Export**

| Screen | Choose |
|---|---|
| Metadata Role | **I am the Identity Provider (IdP)** |
| Metadata Mode | **Use a connection for metadata generation** → pick your SP connection *(this makes sure the certificate in the file is the one this connection actually signs with)* |
| Signing Certificate | your §2.3 certificate — signing the metadata is optional but better |
| Export | save the `.xml` |

Then import that file into your SP.

**✅ Checkpoint — compare fingerprints.** Whatever the SP shows as the trusted certificate's SHA-256
fingerprint must equal the one you noted in §2.3. **Compare fingerprints, not subject names** — two
certificates can share a subject, an issuer and overlapping dates and still be different keys, which
is precisely what a rotation leaves behind.

---

## §9 — Test it

### 9.1 Pick a test SP

| Option | Good for | Watch out |
|---|---|---|
| **Lab 04's `saml-sp` app** ⭐ | Best option — it shows you every assertion field on screen and diagnoses signature failures | Needs a deploy; see [Lab 04](../04-pingfederate-app-onboarding/README.md) |
| A **second PingFederate** as SP | Realistic IdP↔SP federation | Twice the config |
| A **public test SP** (e.g. `samltest.id`) | Zero setup | ⚠️ **It's on the public internet.** Your assertion — usernames, attributes — is sent to a third party. Dummy data only, never anything real, and your PingFederate must be internet-reachable |

### 9.2 The happy path

1. **IdP-initiated first** — it takes the app out of the picture:

   ```
   https://pf.lab.local:9031/idp/startSSO.ping?PartnerSpId=<the SP entity ID>
   ```

2. You should land on the **HTML Form login page** — plain PingFederate branding, Username and
   Password.

3. Log in as `labuser` with its password.

4. You should be redirected to the SP and logged in.

**✅ Checkpoint — you're done when all four are true:**

- [ ] The login form appeared (the adapter ran — you weren't waved straight through)
- [ ] `labuser` + correct password → you land on the SP, logged in
- [ ] The SP shows `SAML_SUBJECT` = `labuser`, plus `email` = `labuser@lab.local` and `firstName` = `Lab`
- [ ] `labuser` + **wrong** password → you stay on the login page with an error, and get 3 tries

**Then do the SP-initiated flow** — start at the app, click its login button, get bounced to Ping and
back. That's the flow real users take.

### 9.3 See the chain with your own eyes (Law 12)

Reading about the pipeline is one thing. **Watch it:**

1. **Install SAML-tracer** (Firefox/Chrome extension) and run the login again. You'll see the
   AuthnRequest go out and the `<saml:Assertion>` come back — with your three attributes in an
   `<AttributeStatement>`, in plain XML. That's the output of five objects, on one screen.

2. **Read the audit log.** One line per SSO transaction, and it names the connection:

   ```bash
   tail -f <pf-install>/pingfederate/server/default/log/audit.log
   ```
   ```powershell
   Get-Content <pf-install>\pingfederate\server\default\log\audit.log -Wait -Tail 20
   ```

3. **Prove the seam.** Add a third user to `LAB-Local-Users`, click **Update**, **Save**. Log in as
   them. **You touched one object; the other four didn't move.** That's the §0.2 derivation,
   demonstrated rather than asserted — and it's exactly what happens when FinCo swaps this validator
   for Active Directory.

---

## §10 — When it fails

Work in this order: **which layer broke?** Each row names the layer, so you know which object to open.

| Symptom | Layer | Cause | Fix |
|---|---|---|---|
| No login form — straight to success or straight to error | Policy | The policy never ran | Tick **IdP Authentication Policies** (§6.1); confirm the policy's first source is `LAB-HTML-Form` |
| Login form appears, correct password rejected | Validator | User row wasn't saved | You didn't click **Update** on the row (§3). Re-add and click **Update** |
| `Unknown connection` / `Partner not found` | Connection | Entity ID mismatch, or connection inactive | Compare the SP's entity ID character-for-character; set the connection **Active** (§7.6) |
| SP rejects with `Invalid Signature` | Certificates | The SP has the wrong copy of your signing certificate | §8 — re-export metadata **with `PartnerSpId`**, re-import at the SP, compare SHA-256 fingerprints. Full RCA: [note 34](../../notes/02-saml/34-saml-invalid-signature-rca.md) |
| PingFederate rejects the AuthnRequest before any login page | Certificates | You required signed AuthnRequests but Ping has the wrong/no SP certificate | §7.5 Signature Verification, or untick **Require AuthN requests to be signed** (§7.4) |
| Login works, but SP says "access denied" / "no email" | Contracts | Attribute names don't match what the SP expects | §7.2 — ask the app team for their exact names and rename in the attribute contract |
| `email` / `firstName` arrive **empty** | Contracts | A gap in one of the three contracts | Walk the chain in §7.3 backwards: attribute contract fulfillment → policy contract mapping → adapter contract mapping. One of the three is unmapped |
| Destination / ACS mismatch | Connection | ACS URL isn't an exact match | Copy it from the SP, don't retype (§7.1) |
| "Assertion not yet valid" / "expired", intermittently | Server | Clock skew between IdP and SP | Put both on NTP. See §7.2 |
| Everything looks right, nothing works | Server | Base URL points at the admin port | §2.2 — runtime is **9031**, not 9999 |

**Two logs, two jobs:**

| Log | Read it for |
|---|---|
| `server/default/log/audit.log` | One line per SSO transaction: which connection, which adapter, success or failure. **Start here** |
| `server/default/log/server.log` | The stack trace, when the audit line isn't enough |

---

## §11 — Pair it with a defence: the forgotten lab validator

Law 9 — never teach a technique without its detection and mitigation. This one has a real abuse case,
and it is not hypothetical.

### The attack

A `LAB-Local-Users` validator built for a proof-of-concept is never deleted. Six months later it's
still attached to a policy, and that policy is still reachable from an internet-facing SP connection.

That's a **live credential store that bypasses everything the directory does** — no MFA, no account
lockout, no leaver process, no central audit, and a password from a POC that was probably weak and
possibly in a wiki page. An attacker who finds the login endpoint has a username/password path into a
federated app that your directory controls never see, because the directory is never consulted.

**Why it's worse than an ordinary weak password:** your monitoring is watching AD. Failed logins here
don't appear there. It is a side door with no camera on it.

### Detection

| Where to look | What to look for |
|---|---|
| **Admin console** — Authentication → Integration → Password Credential Validators | Any Simple Username Password Credential Validator that isn't documented and time-boxed. In production, **any** instance is the finding |
| **`audit.log`** | Successful authentications naming the local adapter/validator instance. Alert on them — in production, the true rate should be **zero**. This is a high-signal, low-noise detection rule (**Heimdall's** kind of rule) |
| **Config archive diff** | Compare exported configuration between environments. A validator that exists in prod but not in the documented baseline is a finding |
| **Policy tree review** | An orphaned validator is untidy; one wired into an active policy on an active connection is an incident |

### Mitigation

1. **Name it so it can't hide** — `LAB-` / `POC-` prefix, always.
2. **Time-box it.** Put the deletion in the same ticket that created it. "Delete `LAB-Local-Users`"
   should be a checklist item, not a memory.
3. **Never attach it to a policy an active production connection can reach.** A validator with no
   path to it is dormant, not dangerous.
4. **Alert on any successful authentication through it in production** (see above).
5. **Treat the admin console as Tier-0.** Anyone who can add a row to that user table can mint an
   identity for any federated app. That's the same blast radius as domain admin, and it deserves the
   same protection — MFA, jump host, restricted admin network.
6. **Do the cleanup in §12.**

> **The audit framing (Tyr's angle).** For FinCo, a local credential store bypassing the directory
> undercuts PCI-DSS 8.x (unique IDs, password controls, MFA) and SOX access-control evidence in one
> go. The answer to "do you have any authentication paths that don't go through the directory?" needs
> to be a confident **no** — and this lab is exactly the thing that turns it into an embarrassed
> *"let me check."*

---

## §12 — Clean up

Do this. It's part of the lab, not an afterthought.

1. Set the **SP connection** to inactive, then delete it.
2. Delete the **Sign-On Policy** (`LAB-Sign-On-Policy`).
3. Delete the **Policy Contract** (`LAB-Policy-Contract`).
4. Delete the **IdP Adapter** (`LAB-HTML-Form`).
5. Delete the **Password Credential Validator** (`LAB-Local-Users`) — **especially this one**.
6. Delete the **signing certificate** if it was made only for this lab.
7. If you used a public test SP, remember your dummy assertion reached a third party. Nothing real
   should have been in it.

> ⚠️ **Delete in that order — dependents first.** PingFederate refuses to delete an object that
> something else still references, and the error doesn't always name the referrer. Bottom-up is the
> path of least frustration.

---

## What you learned

- **The five objects, and why each one has to exist** — derived from three things that vary
  independently (where the credential lives, how it's collected, which app asks), not memorised.
- **The Simple Username Password Credential Validator** is a drop-in replacement for a directory at
  exactly one seam — and the four objects downstream can't tell the difference. That's the proof the
  architecture is real.
- **Three contracts in a row** — adapter contract → policy contract → SAML attribute contract — and
  why the indirection means changing authentication doesn't mean editing every application.
- **Filling an attribute contract with no data source** using `Text` and `${username}`, and why the
  resulting `email` is a fabricated string rather than identity data.
- **The gotchas that actually cost hours:** the un-ticked **IdP Authentication Policies** box, the
  un-clicked **Update** on a user row, the missing **`PartnerSpId`** on a metadata URL, port **9031**
  vs **9999**, and comparing **SHA-256 fingerprints instead of subject names**.
- **Why this is lab-only**, in specific, defensible terms — and how a forgotten instance becomes a
  real finding, how to detect one, and how to prevent it.

## Next

- **[Lab 04 — App onboarding with PingFederate](../04-pingfederate-app-onboarding/README.md)** — the
  same connection against a real deployed app, plus the OAuth/OIDC side of the house.
- **[note 34 — "Invalid Signature": how SAML signature verification really works](../../notes/02-saml/34-saml-invalid-signature-rca.md)** —
  the failure you're most likely to hit next, from first principles.
- **[note 18 — PingFederate explained](../../notes/06-platforms-and-gateways/18-pingfederate-explained.md)** — the product map
  around the five objects you just built.
- **Then swap the validator for a real directory.** Stand up an LDAP container, create an **LDAP
  Datastore**, add an **LDAP Username Password Credential Validator**, and point `LAB-HTML-Form` at
  it instead. Watch `email` and `firstName` start arriving from a real source — and watch the SP
  connection, the policy and the contracts not change at all. **That non-change is the lesson.**

---

*Written to [Lefler's Laws](../../../LEFLER-LAWS.md) ⚙️ · specified by Janus 🔑*
