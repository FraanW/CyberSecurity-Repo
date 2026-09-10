# An SP connection with **no LDAP and no database** — the ground-up build

> **What this is.** A complete, from-zero click-path for making a PingFederate **SP connection**
> where the user's password is checked by **PingFederate itself** — no Active Directory, no LDAP,
> no SQL. You will build every object in the chain, in order, and understand *why each one exists*
> before you click Create.
>
> **Where this sits.** [`PINGFED-SETUP.md`](PINGFED-SETUP.md) is the general onboarding click-path
> and assumes you already have an authentication source. **This doc builds that source**, then
> wires it all the way through to a working SAML login. §1.8 of that doc is the two-minute summary;
> this is the full version, with the parts that trip people up spelled out.
>
> **Authorized-lab-only.** Your own PingFederate, a test connection, dummy users. Never point this
> at production, and never put a real FinCo password in a Simple Credential Validator.

- **Time:** ~45 min · **Difficulty:** intermediate (no prior PingFederate config assumed)
- **Console version:** menu names are **PingFederate 11.x / 12.x**. Older builds nest the same screens slightly differently — the **fields** are identical.
- **Platform:** anything with a browser. Farhaan is on **Windows 11**; nothing here needs a shell except the optional verification in §9.
- **Prereqs:** a running PingFederate you can log into as an admin, and the [`saml-sp` lab app](README.md) deployed (or any SAML SP). Concepts: [note 02 — SAML deep dive](../../notes/02-saml/02-saml-deep-dive.md), [note 18 — PingFederate explained](../../notes/06-platforms-and-gateways/18-pingfederate-explained.md).
- **You'll be able to:** build a Password Credential Validator, an IdP Adapter, a Policy Contract, a Sign-On Policy and an SP Connection from nothing — and explain what each one is *for*.

---

## TL;DR — the whole build in one screen

Five objects, built bottom-up. **Each one only knows about the one below it.**

```
     ┌─────────────────────────────────────────────────────────────┐
     │  5. SP CONNECTION            "which app, and what do I      │
     │     (Applications)            send it?"                     │
     └───────────────────────────┬─────────────────────────────────┘
                                 │ consumes
     ┌───────────────────────────▼─────────────────────────────────┐
     │  4. SIGN-ON POLICY           "which login method runs,      │
     │     (Authentication)          and when?"                    │
     └───────────────────────────┬─────────────────────────────────┘
                                 │ fulfils
     ┌───────────────────────────▼─────────────────────────────────┐
     │  3. POLICY CONTRACT          "the fixed set of attribute    │
     │     (Authentication)          names apps can rely on"       │
     └───────────────────────────┬─────────────────────────────────┘
                                 │ filled by
     ┌───────────────────────────▼─────────────────────────────────┐
     │  2. IdP ADAPTER (HTML Form)  "the login page the user       │
     │     (Authentication)          actually sees"                │
     └───────────────────────────┬─────────────────────────────────┘
                                 │ asks
     ┌───────────────────────────▼─────────────────────────────────┐
     │  1. PASSWORD CREDENTIAL      "is this password correct?"    │
     │     VALIDATOR (Simple)        ← the ONLY part LDAP replaces │
     └─────────────────────────────────────────────────────────────┘
```

| # | Object | Console path | Name we'll use |
|---|---|---|---|
| 1 | Password Credential Validator | Authentication → Integration → **Password Credential Validators** | `Lab-Local-Users` |
| 2 | IdP Adapter (HTML Form) | Authentication → Integration → **IdP Adapters** | `Lab-HTML-Form` |
| 3 | Policy Contract | Authentication → Policies → **Policy Contracts** | `Lab-Policy-Contract` |
| 4 | Sign-On Policy | Authentication → Policies → **Policies** | `Lab-Sign-On-Policy` |
| 5 | SP Connection | Applications → Integration → **SP Connections** | `IAM Lab SAML SP` |

> ⚠️ **The single biggest gotcha, up front:** on the **Policies** screen there is a checkbox
> labelled **IdP Authentication Policies**. If it is unticked, **your whole policy is ignored** and
> PingFederate falls back to whatever adapter the connection maps directly. People lose an hour
> here. See §6.

---

## §0 — Why we are doing this

This is the part worth reading slowly. The click-path is twenty minutes; understanding *why the
console is shaped like this* is the thing that makes you useful on a ticket.

### 0.1 Why a "simple" validator instead of LDAP

Three separate reasons, and they are all good ones:

| Reason | The argument |
|---|---|
| **You are learning the federation, not the directory** | If you start with LDAP you spend your first afternoon on bind DNs, search filters, base DNs and TLS to the directory — **none of which is SAML.** Every one of those is a place to fail for reasons unrelated to what you are trying to learn. Remove the directory and the *only* thing that can break is the federation itself. |
| **You may not have a directory to point at** | A lab PingFederate on a laptop or a cloud VM often has no AD anywhere near it. Standing up a whole directory server to test one SP connection is a poor trade. |
| **It proves the architecture is decoupled** | This is the real prize, and §0.2 is about it. |

**The honest framing:** this is a *lab-only* stand-in. At FinCo the password is checked against
**Active Directory or an LDAP directory**, always. But — and this is the point — **swapping one for
the other changes exactly one object out of five.**

### 0.2 The design principle you are about to see with your own eyes

Ask the first-principles question: *why does PingFederate have five separate objects here? Why not
one "login" screen with a directory host and an app URL on it?*

Because those five things **change at different rates, for different reasons, owned by different
people**:

- The **app** changes when someone onboards a new SaaS tool. Weekly.
- The **login method** changes when security mandates MFA. Yearly, and painfully.
- The **directory** changes when the company migrates AD → Entra ID. Once a decade, and it is a programme, not a ticket.

If those were one object, a directory migration would mean **re-doing every SP connection you own**
— hundreds of them, each needing a change window and a partner to re-test. So PingFederate puts a
**stable interface between each pair of layers**:

```
 the app knows about →  a POLICY CONTRACT   (a fixed list of attribute names)
                              ↑ fulfilled by
 the policy knows about →  an ADAPTER        (a fixed contract of what login returns)
                              ↑ fed by
 the adapter knows about → a CREDENTIAL VALIDATOR (a yes/no on a password)
```

**Nothing above the validator knows or cares how the password was checked.** That is not an
accident of the UI; it is the design. And this lab is the cleanest possible demonstration of it,
because you will build the whole chain on a validator that is *obviously* fake — and then, in §11,
see that replacing it with a real directory touches nothing else.

> **The ticket this earns you.** When someone says *"we're moving to Entra ID, how much of Ping do
> we have to rebuild?"* — the answer is "the datastores and the credential validators; the adapters,
> policies and connections stay." You will have proved that to yourself rather than read it.

### 0.3 What "simple" costs you — be honest about the limits

The Simple Username Password Credential Validator is deliberately minimal. Know what it does *not* do:

| Limitation | Why it matters | What you do about it |
|---|---|---|
| **It returns only `username`** | No `email`, no `firstName`, no group memberships — a directory is where those live | §8 shows four ways to still populate an attribute contract |
| **Users are stored in PingFederate's own config** | They are part of the server configuration, replicated to every engine in a cluster. That is not an identity store | Lab only. Never real users |
| **No password policy, expiry, lockout or history** | None of the controls an auditor expects | Do not let this near anything real |
| **No self-service reset** | The HTML Form adapter's password-change feature needs a **Password Management System**, which the Simple PCV is not | Leave "Allow Password Changes" off |
| **Passwords live in the config store** | Obfuscated, not a slow salted hash like a real directory would use | This is the reason for the "lab only" warning, not paranoia |

> 🔒 **Say this out loud once:** *a credential validator that stores users in the IdP's own
> configuration is a test fixture, not an identity store.* If you ever find one in a production
> PingFederate, that is an audit finding — write it up.

---

## §1 — Before you start

**Check these three things.** Two minutes now saves a confusing hour later.

1. **You can reach the admin console** and log in — usually `https://<pf-host>:9999/pingfederate/app`.
2. **Your runtime base URL is right.** **System → Server → Protocol Settings → Federation Info.**
   The **Base URL** here is what goes into every URL PingFederate publishes about itself, including
   the SSO endpoint in its metadata. If it says `localhost` and your app is on the internet, the
   redirect will fail later for reasons that look nothing like the cause.
   | Field | What it is |
   |---|---|
   | **Base URL** | `https://pf.example.com:9031` — the **runtime** port (9031), not the admin port (9999) |
   | **SAML 2.0 Entity ID** | PingFederate's own identity, e.g. `https://pf.example.com` — the app will need this |
3. **You have a signing certificate.** **Security → Certificate & Key Management → Signing &
   Decryption Keys & Certificates.** If the list is empty, **Create New** a self-signed one
   (`CN=pf-lab-signing`, RSA 2048, SHA-256, a few years). **Note its SHA-256 fingerprint** — you
   will compare it against the app in §9, and it is what turns an `Invalid Signature` ticket from
   an hour into a minute.

> ✅ **Checkpoint.** You know your Base URL, your entity ID, and you have one signing certificate
> with its fingerprint written down.

---

## §2 — Step 1 of 5: the Password Credential Validator

**What it is, in plain words:** the component whose entire job is to answer one question —
***"is this username and password correct?"*** — with yes or no. Nothing more. It does not draw a
login page. It does not decide who is allowed into which app. One question, one answer.

**Why it is a separate object:** because that yes/no is the *only* thing that changes when your
identity store changes. Isolating it is what makes the migration in §11 cheap.

### Build it

**Authentication → Integration → Password Credential Validators → Create New Instance**

**Screen 1 — Type**

| Field | Value |
|---|---|
| Instance Name | `Lab-Local-Users` |
| Instance ID | `LabLocalUsers` *(auto-fills from the name; no spaces)* |
| Type | **Simple Username Password Credential Validator** |
| Parent Instance | **None** |

**Screen 2 — Instance Configuration**

This is just a table of users. **Add a new row to 'Users'** for each one:

| Username | Password | Confirm Password |
|---|---|---|
| `labuser` | *(pick something)* | *(same)* |
| `farhaan` | *(pick something)* | *(same)* |

> 💡 Create **two** users. A second account is how you later prove that the assertion's
> `SAML_SUBJECT` really is per-user and not a value you accidentally hardcoded.

**Screen 3 — Extended Contract**

Leave it **empty**. This screen exists so a validator can expose extra attributes it learned while
checking the credential — an LDAP validator uses it to hand back directory attributes. The Simple
validator knows nothing beyond the username, so there is nothing to add. *Seeing this screen be
empty is itself the lesson: this is the exact spot where a real directory would give you `email`.*

**Screen 4 — Summary** → **Save**.

> ✅ **Checkpoint.** The Password Credential Validators list shows `Lab-Local-Users`, type
> **Simple Username Password Credential Validator**.

---

## §3 — Step 2 of 5: the IdP Adapter (HTML Form)

**What it is, in plain words:** the thing the user actually *sees and touches*. It renders the
login page, collects what they type, hands it to a credential validator, and — on success — returns
a small bag of attributes to PingFederate.

**Why it is separate from the validator:** because *how you ask* and *how you check* are different
concerns. The same HTML Form adapter can front a Simple validator today and an LDAP validator
tomorrow. And the same LDAP validator can sit behind an HTML form, a Kerberos adapter, or an
identifier-first flow. **Two axes, so two objects.**

### Build it

**Authentication → Integration → IdP Adapters → Create New Instance**

**Screen 1 — Type**

| Field | Value |
|---|---|
| Instance Name | `Lab-HTML-Form` |
| Instance ID | `LabHTMLForm` |
| Type | **HTML Form IdP Adapter** |
| Parent Instance | **None** |

**Screen 2 — IdP Adapter** *(the configuration table)*

| Field | Value | Why |
|---|---|---|
| **Password Credential Validator Instance** | **Add a new row to 'Credential Validators'** → select `Lab-Local-Users` | **This is the join.** The one line that connects the login page to the thing that checks the password |
| Challenge Retries | `3` | attempts before the flow fails |
| Session State | `None` | keeps the lab simple — every SSO shows the form. Set `Global` later if you want the form skipped on a second app |
| Session Timeout / Max Timeout | leave defaults | idle and absolute lifetimes for that adapter session |
| **Allow Password Changes** | **unchecked** | it needs a *Password Management System*, which the Simple validator is not. Ticking it produces an error you will not enjoy debugging |
| Enable "Remember My Username" / "This is My Device" | unchecked | cookies you do not need yet |
| Login Template | `html.form.login.template.html` | the default page. This file is on disk under `server/default/conf/template/` if you ever want to brand it |

Everything else: **leave at its default.** Click **Show Advanced Fields** if you want to look, but
change nothing.

**Screen 3 — Extended Contract**

The core contract is `username`. **If you want the assertion to carry `email` and `firstName`, add
them here** — type each name and click **Add**. They will be empty until §8 fills them.

| Add | Why |
|---|---|
| `email` | so the app's attribute contract has somewhere to source it from |
| `firstName` | same |

> **Skipping them is a legitimate choice.** Start with just `username`, get a login working
> end to end, then come back. A working simple thing beats a broken complete thing.

**Screen 4 — Adapter Attributes**

| Attribute | Pseudonym | Mask Log Values |
|---|---|---|
| `username` | ✅ **ticked** | leave unticked |
| `email` / `firstName` | unticked | unticked |

**What "Pseudonym" means:** if a connection ever asks for an opaque, non-identifying name
identifier, PingFederate needs to know which attribute uniquely identifies the user in order to
generate a stable pseudonym for them. **You must tick at least one** or the instance will not save.
`username` is the right choice.

**Screen 5 — Adapter Contract Mapping**

Leave everything alone **for now** — you will come back here in §8 if you want to fill `email` and
`firstName`. (This screen has sub-screens: *Attribute Sources & User Lookup*, *Adapter Contract
Fulfillment*, *Issuance Criteria*, *Summary*. With no datastore, only fulfilment is interesting.)

**Screen 6 — Summary** → **Save**.

> ✅ **Checkpoint.** The IdP Adapters list shows `Lab-HTML-Form`, type **HTML Form IdP Adapter**.
> You now own a **reusable building block** — any connection or policy in this server can use it.

---

## §4 — Step 3 of 5: the Policy Contract

**What it is, in plain words:** a **fixed list of attribute names** that apps are allowed to depend
on. It is a promise: *"whatever happens upstream, a successful login will hand you these fields."*

**Why it must exist — derive it.** Suppose your SP connections mapped straight to adapters. Now
security says *"everyone off-network needs MFA."* You add a PingID step. The login now ends at a
different adapter — so **every connection that mapped to the old adapter breaks**, and you re-map
all of them.

Put a contract in the middle and that disappears. The policy's job becomes *"however the user got
here, produce `subject`, `email`, `firstName`."* The connections consume the contract. **The login
method can be rebuilt underneath them without a single connection changing.**

> That is the same reasoning as an interface in code, or a stable API version. Contracts exist so
> the thing above does not have to know how the thing below did its job.

### Build it

**Authentication → Policies → Policy Contracts → Create New Contract**

**Screen 1 — Contract Info**

| Field | Value |
|---|---|
| Contract Name | `Lab-Policy-Contract` |

**Screen 2 — Contract Attributes**

`subject` is already there and cannot be removed — it is the core attribute, the "who". Extend it:

| Attribute | Note |
|---|---|
| `subject` | core, always present |
| `email` | add |
| `firstName` | add |

> Add only what you will actually fill. An attribute in the contract that is never fulfilled comes
> through as **empty**, and "the app says the email is blank" is a real ticket with a boring cause.

**Screen 3 — Summary** → **Save**.

> ✅ **Checkpoint.** Policy Contracts lists `Lab-Policy-Contract` with three attributes.

---

## §5 — Step 4 of 5: the Sign-On Policy

**What it is, in plain words:** a **flowchart**, walked top-down on every login, that decides which
authentication source runs and what happens on success or failure.

**Why it exists:** because "which login method?" is a *routing decision* with real business rules
behind it — on-network vs off, employee vs partner, low-risk app vs payments. Those rules do not
belong inside a login page, and they do not belong inside an app's connection. They get their own
object so one change updates every app at once.

Our tree is the simplest possible one — one source, one outcome — but it is a real tree, and adding
MFA later means inserting a node, not rebuilding anything.

### Build it

**Authentication → Policies → Policies**

> 🚨 **Do this first: tick the `IdP Authentication Policies` checkbox** at the top of the screen.
>
> **Unticked, PingFederate ignores every policy you write** and uses whatever adapter each
> connection maps directly. Your policy will look perfectly correct and have no effect whatsoever.
> This is the most common "but I configured it!" moment in the whole console.
>
> While you are here, leave **Fail if policy finds no authentication source** unticked for the lab —
> it makes a mis-wired policy fall back rather than hard-fail, which is friendlier while learning.
> Tick it in production, where a silent fallback is worse than an error.

**Add Policy**

| Field | Value |
|---|---|
| Name | `Lab-Sign-On-Policy` |
| Description | `Local username/password for the IAM lab — no directory` |

**Now build the tree.** It is click-through, not a form:

1. **Policy** (the root/Start node) → choose your authentication source:
   **Adapters → `Lab-HTML-Form`**.
2. The node sprouts two branches, **Fail** and **Success**.
3. On **Fail** → leave it as **Done** (the login fails; nothing to map).
4. On **Success** → **Done**, then pick **Policy Contract → `Lab-Policy-Contract`**.
5. Click the **Contract Mapping** link that appears on that branch. This is where the adapter's
   output becomes the contract's values:

   **Attribute Sources & User Lookup** → **Next** *(you have no datastore — this screen is for
   LDAP/JDBC lookups; skip it, and note that this is the second place a real directory would plug in)*

   **Contract Fulfillment** — the important screen:

   | Contract attribute | Source | Value |
   |---|---|---|
   | `subject` | **Adapter** | `username` |
   | `email` | **Adapter** | `email` *(empty until §8 — or use **Text** and type a literal)* |
   | `firstName` | **Adapter** | `firstName` *(same)* |

   **Issuance Criteria** → skip. *(This is where you would later add "only issue if `group` contains
   `finco-payments`" — an authorisation gate, evaluated before the assertion is minted. Worth
   remembering it lives here.)*

   **Summary** → **Done**.

6. **Save** the policy.

> ✅ **Checkpoint.** The Policies list shows `Lab-Sign-On-Policy`, and the **IdP Authentication
> Policies** checkbox is **ticked**. If the checkbox is not ticked, go back — nothing below will work.

---

## §6 — Step 5 of 5: the SP Connection

**What it is:** the app. *"Here is one more application I am the IdP for — this is who it claims to
be, where I send the assertion, what I put in it, and how I sign it."*

If you already created the connection by following [`PINGFED-SETUP.md`](PINGFED-SETUP.md) §1, you
only need **§6.3** below — mapping it to the policy. Otherwise, do all of it.

### 6.1 Create the connection

**Applications → Integration → SP Connections → Create Connection**

| Screen | What to do |
|---|---|
| Connection Template | **Do not use a template** |
| Connection Type | tick **Browser SSO Profiles**, protocol **SAML 2.0** |
| Connection Options | tick **Browser SSO** |
| Import Metadata | **File** → upload the app's SP metadata *(the lab app: **Step 1 → ⬇ Download SP metadata XML**)*. This fills in the entity ID, ACS URL, bindings and the app's signing certificate for you |
| General Info | Connection Name `IAM Lab SAML SP`. Leave the entity ID as imported |

### 6.2 Browser SSO — profiles, contract, and the piece that matters

**Browser SSO → Configure Browser SSO**

| Screen | What to set |
|---|---|
| SAML Profiles | tick **SP-Initiated SSO** (and **IdP-Initiated SSO** if you want to try both) |
| Assertion Lifetime | leave defaults (5 minutes either side) |
| Assertion Creation → **Identity Mapping** | **Standard** |
| **Attribute Contract** | `SAML_SUBJECT` is there. **Add `email` and `firstName`** — these are what show up in the app's Step 4 table |

### 6.3 Authentication Source Mapping — the line that ties it all together

Still inside **Configure Browser SSO**:

**Authentication Source Mapping → Map New Authentication Policy Contract**

| Field | Value |
|---|---|
| Authentication Policy Contract | **`Lab-Policy-Contract`** |

> **Why the contract and not the adapter directly?** The console offers both. Mapping the adapter
> works and is one click shorter — and it is the choice you regret. It welds this app to *this
> login method*. Map the contract and you can put MFA, an identifier-first screen, or a whole
> different directory underneath, and this connection never notices. **Always map the contract.**

Then, on that mapping:

**Attribute Contract Fulfillment**

| SAML attribute | Source | Value |
|---|---|---|
| `SAML_SUBJECT` | **Authentication Policy Contract** | `subject` |
| `email` | **Authentication Policy Contract** | `email` |
| `firstName` | **Authentication Policy Contract** | `firstName` |

**Issuance Criteria** → skip → **Summary** → **Done**.

> 📌 **Notice what just happened.** You mapped `SAML_SUBJECT` ← `subject` ← `username` ← the Simple
> validator. **Four objects, one value, three hand-offs** — and each hand-off is a place you could
> swap the layer below without touching the layer above. That chain *is* the architecture.

### 6.4 Protocol Settings and Credentials

| Screen | What to set |
|---|---|
| Protocol Settings → Assertion Consumer Service URL | **already imported.** Confirm it matches the app's Step 1 table exactly |
| Allowable SAML Bindings | tick **POST** |
| Signature Policy | tick **Require AuthN requests to be signed** — the lab app signs them by default |
| Encryption Policy | **None** to start |
| **Credentials → Digital Signature Settings** | pick the signing certificate from §1, algorithm **RSA SHA256**. If offered, tick **include the certificate in `<KeyInfo>`** — it lets the app tell you *which* key signed a rejected assertion |
| Credentials → Signature Verification Settings | already imported from the metadata — this is the app's certificate, for checking *its* AuthnRequests |

### 6.5 Save and enable

**Save**, then set the connection's status to **Active** on the SP Connections list. A saved-but-
inactive connection produces a confusing "unknown connection" error.

> ✅ **Checkpoint.** The SP Connections list shows `IAM Lab SAML SP`, **Active**.

---

## §7 — Tell the app about PingFederate

Everything so far pointed **PingFederate at the app**. The app also has to trust **PingFederate**,
which means it needs Ping's entity ID, SSO URL and **signing certificate**.

1. **System → Protocol Metadata → Metadata Export** → Metadata Role **I am the Identity Provider
   (IdP)** → export the XML.
2. In the lab app: **Step 2 → Import the IdP's metadata → Upload a file → Preview**.
3. **Check the SHA-256 fingerprint** in the preview against the certificate you noted in §1.
4. **Import and trust this IdP.**

> ⚠️ If you use Ping's published metadata **URL** instead of the export, it must carry the
> connection parameter — `…/pf/federation_metadata.ping?PartnerSpId=<the app's SP entity ID>`.
> Without it you get the server's *default* signing certificate, which may not be the one this
> connection signs with, and every login fails with `Invalid Signature`. Full story:
> [note 34](../../notes/02-saml/34-saml-invalid-signature-rca.md).

---

## §8 — The attributes problem: no directory means no `email`

You will hit this the moment you add `email` to a contract. The Simple validator returns
**`username` and nothing else**, so `email` and `firstName` arrive empty.

**This is not a bug — it is the lesson.** Attributes come from an identity store, and you removed
the identity store. Four honest ways forward, in the order I would try them:

### Option A — drop them (recommended first)

Take `email` and `firstName` out of the SP attribute contract and the policy contract. Get
`SAML_SUBJECT` working end to end. **A login that works beats a login that carries empty fields.**
Add attributes back when you have somewhere real to source them.

### Option B — a `Text` literal (good enough to see the plumbing)

Anywhere you pick a fulfilment source, **`Text`** lets you type a constant. In the policy's
**Contract Fulfillment**:

| Attribute | Source | Value |
|---|---|---|
| `subject` | Adapter | `username` |
| `email` | **Text** | `labuser@lab.invalid` |
| `firstName` | **Text** | `Lab` |

**What this is good for:** proving the whole attribute pipeline works — contract → fulfilment →
`<AttributeStatement>` → the app's Step 4 table. **What it is not:** per-user data. Every user gets
the same email. Fine for one test account; obviously wrong for two, which is exactly why §2 told
you to create two users.

### Option C — fill the adapter's extended contract (tidier)

If you added `email`/`firstName` to the adapter's **Extended Contract** in §3, go to
**Adapter Contract Mapping → Adapter Contract Fulfillment** and give them **Text** values there
instead. The value then flows adapter → policy → connection like a real attribute would, so your
downstream mappings are shaped exactly as they will be with a directory. **Only the source is fake.**

### Option D — a Local Identity Profile (the real "no LDAP" answer)

PingFederate can store user records itself, properly, in a **Local Identity Profile** backed by a
datastore — the feature behind self-service registration. It genuinely holds `email`, `firstName`
and more, per user. It is also a substantially bigger build than everything above combined, and it
still wants somewhere to persist records. **Out of scope here; know the name so you can reach for
it when "no LDAP, but I need real per-user attributes" is the actual requirement.**

> **What about OGNL expressions?** The **Expression** fulfilment source can compute values
> (`labuser` → `labuser@lab.invalid`). Expressions are **disabled by default** and are switched on
> with a server-side config-store setting — check the docs for your exact version before relying on
> it. You do not need expressions for this lab, and a `Text` value is easier to explain in a review.

---

## §9 — Test it end to end

1. Open the lab app → **Step 3 → Log in with PingFederate**.
2. You are redirected to PingFederate and see **the HTML Form login page**.
3. Enter `labuser` and its password.
4. You land back on the app.

> ✅ **Checkpoint — the whole chain.** The dashboard says **"via PingFederate (SAML)"**, and
> **Step 4** shows `SAML_SUBJECT` = `labuser`.
>
> **If you never saw a login form** and went straight through, the adapter did not run. Either the
> **IdP Authentication Policies** checkbox is unticked (§5), or an existing PingFederate session
> was reused — try a private window.

**Confirm it from the app's own API**, so you are reading the protocol and not a UI:

```bash
# Bash — the values that came out of your chain
curl -s https://<your-app>/api/whoami -b cookies.txt | python3 -m json.tool
curl -s https://<your-app>/api/assertion/highlights -b cookies.txt | python3 -m json.tool
```

```powershell
# PowerShell (Windows 11)
Invoke-RestMethod -Uri "https://<your-app>/api/whoami" -WebSession $s | ConvertTo-Json -Depth 5
```

**Expected:** `loginType: "saml"`, `nameId: "labuser"`, and under highlights an `issuer` equal to
PingFederate's entity ID and an `audience` equal to the app's SP entity ID.

**Now prove it is really per-user:** log out, log back in as `farhaan`. `SAML_SUBJECT` must change.
If it does not, something is hardcoded — most likely a `Text` fulfilment where you meant `Adapter`.

---

## §10 — When it does not work

Read **`server/default/log/audit.log`** first — **one line per SSO transaction**, and it names the
connection and the outcome. `server.log` has the stack trace when you need it.

| Symptom | Almost always | Fix |
|---|---|---|
| **No login form — SSO completes instantly** | `IdP Authentication Policies` is unticked, so the policy never ran | §5. Tick it. Or you have a live PF session — use a private window |
| **"No authentication source" / policy falls through** | The policy's Start node does not point at `Lab-HTML-Form`, or the policy is not enabled | Reopen the tree; confirm the root node names your adapter |
| **Login form appears, password rejected** | The adapter is not wired to the validator, or the password is wrong | Adapter → **IdP Adapter** screen → confirm `Lab-Local-Users` is in Credential Validators. Re-set the password in the PCV |
| **`SAML_SUBJECT` is empty** | Fulfilment not mapped | Two places to check: the **policy's** Contract Fulfillment (`subject` ← Adapter `username`) *and* the **connection's** (`SAML_SUBJECT` ← Policy Contract `subject`). Both must be set |
| **`email` / `firstName` empty** | Working as designed — the Simple PCV has no such data | §8 |
| **App says `Invalid Signature`** | The app holds the wrong copy of Ping's signing certificate | §7, then the app's diagnosis panel. Full RCA: [note 34](../../notes/02-saml/34-saml-invalid-signature-rca.md) |
| **`Invalid destination` / `Invalid audience`** | The app built an `http://` URL behind a TLS proxy, or Ping's Base URL is wrong | Set the app's `PUBLIC_BASE_URL`; check **Protocol Settings → Federation Info** (§1) |
| **Assertion rejected as expired** | Clock skew between Ping and the app | Assertions live minutes. Check NTP on both hosts before suspecting anything else |
| **"Unknown connection"** | The SP connection is saved but **not Active** | §6.5 |

---

## §11 — The payoff: swapping in a real directory

Here is the claim from §0.2, made concrete. To move this lab from a fake validator to Active
Directory, you:

1. **Create a datastore.** System → Data & Credential Stores → **Data Stores** → LDAP, pointing at AD.
2. **Create an LDAP Username Password Credential Validator** against that datastore.
3. **Change one dropdown**: the `Lab-HTML-Form` adapter's **Password Credential Validator Instance**,
   from `Lab-Local-Users` to the new LDAP one.

**That is the whole migration.** Now compare what you did *not* touch:

| Object | Changed? |
|---|---|
| Password Credential Validator | ✅ replaced — **this is the only one** |
| IdP Adapter | one dropdown |
| Policy Contract | ❌ untouched |
| Sign-On Policy | ❌ untouched |
| SP Connection | ❌ untouched |
| The app | ❌ untouched — it never knew |

And you get the attributes from §8 for free: the LDAP validator's **Extended Contract** (the screen
that was empty in §2) now offers `mail`, `givenName`, `sn`, and you map them straight through.

> **This is why the console has five objects instead of one.** You just proved it in a lab instead
> of learning it during a migration.

---

## §12 — Security notes, paired with defences

Per repo rule (Lefler's Law 9) — never describe a weakness without its mitigation.

| Weakness of this setup | Why it is dangerous | Defence |
|---|---|---|
| **Users live in the server config** | They replicate to every cluster node and land in config archives and backups. Anyone with config-read access effectively has the user list | Lab only. In production, credentials live in a directory with its own access controls and audit trail |
| **No lockout or throttling** | Unlimited password guessing against a known username | The HTML Form adapter's **Challenge Retries** limits attempts *per flow*, not overall. A real PCV plus AD lockout policy is the actual control. Also front the runtime with a WAF/rate limit |
| **No password policy or expiry** | Weak and immortal passwords | A directory-backed PCV inherits the directory's policy. This is a large part of *why* the directory exists |
| **Single factor** | A stolen password is a full account takeover, and PingFederate is the front door to *every* app behind it | Add an MFA node to the policy tree in §5 — that is a one-node insert and **no connection changes**, which is the design paying off again |
| **`Text` attribute values (§8 Option B)** | An assertion that asserts a *constant* email for every user. If an app authorises on `email`, everyone becomes the same person | Never use `Text` for an identity-bearing attribute outside a lab. Use it for genuinely constant values only (e.g. `tenant = "lab"`) |

**Detection (the blue-team view — hand this to Heimdall):** PingFederate's `audit.log` has one line
per SSO transaction. Alert on **a burst of failed authentications for one username** (credential
stuffing) and on **a successful SSO from an adapter that should not be reachable externally**.
For the signature side, a spike of `invalid_signature` across *many* connections is a rotation gone
wrong; the same error on *one* connection, repeatedly, from one source, is worth a look.

---

## §13 — Cleanup

Delete in reverse dependency order — PingFederate refuses to delete an object something else still
references, and the error does not always name the referrer.

1. **SP Connection** `IAM Lab SAML SP` — delete or set Inactive.
2. **Sign-On Policy** `Lab-Sign-On-Policy`.
3. **Policy Contract** `Lab-Policy-Contract`.
4. **IdP Adapter** `Lab-HTML-Form`.
5. **Password Credential Validator** `Lab-Local-Users` — **do this one**. A forgotten validator with
   known lab passwords on a server that later becomes real is a genuine audit finding.
6. In the app: **Step 2 → Revert to environment config**, to drop the imported IdP metadata.

---

## What you learned

- **A "login" in PingFederate is five objects, not one** — validator, adapter, policy contract,
  policy, connection — and each exists because the things it separates **change at different rates
  for different reasons**.
- **The credential validator is the only piece the identity store touches.** Everything above it is
  insulated by contracts, which is why an AD → Entra migration is a dropdown and not a rebuild.
- **A contract is a promise; fulfilment says where the value comes from.** Empty attributes are
  almost always a fulfilment gap, and there are **two** fulfilment screens in this chain to check.
- **Attributes come from an identity store.** Remove the store and you lose them — that is the
  system being honest, not broken.
- **`IdP Authentication Policies` must be ticked** or every policy you write is decoration.
- **A Simple Username Password Credential Validator is a test fixture, not an identity store.**
  Finding one in production is a write-up.

## Next

- Add a second factor: insert an MFA node into the tree from §5 and watch **zero** connections need changing.
- The general onboarding path, both protocols: [`PINGFED-SETUP.md`](PINGFED-SETUP.md).
- What the assertion your chain produced actually looks like, field by field:
  [note 02 — SAML deep dive](../../notes/02-saml/02-saml-deep-dive.md) and
  [note 13 — SAML mastery, session 2](../../notes/02-saml/13-saml-mastery-session2.md).
- When the login fails at the signature:
  [note 34 — "Invalid Signature"](../../notes/02-saml/34-saml-invalid-signature-rca.md).
- The product view of every object you just built:
  [note 18 — PingFederate explained](../../notes/06-platforms-and-gateways/18-pingfederate-explained.md) §5.
