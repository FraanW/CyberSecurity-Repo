# 🖥️ Presenting the IdP — a screen-by-screen Keycloak admin console tour

> For the KT: how to walk the room through the **Keycloak admin console** and explain each screen — **where users live, how credentials are managed, where the signing certs are, how apps are onboarded** — and map every screen to **PingFederate / Entra ID**.
>
> Open: `https://cybersecurity-repo.onrender.com/admin/` → **admin / admin**. Top-left **realm dropdown** → switch to **`finco-idp`** (do everything in this realm, not `master`).

**The 30-second framing to open with:**
> "This is our IdP's control plane — same job as the PingFederate admin console. Everything here answers one of three questions: *who are the users* (Users, User federation), *how do they prove it* (Credentials, Authentication), and *which apps trust us* (Clients, Identity providers). Let me walk each screen."

---

## 0. Realms — the first concept
**Top-left dropdown.** There are two: **master** (the console's own admin realm — don't demo here) and **finco-idp** (our corporate IdP).
- **Say:** "A **realm** is a fully isolated identity world — its own users, its own apps, its own signing keys. Think **one tenant / one company**."
- **Maps to:** Entra **tenant**. *(PingFederate has no realm concept — one server, many connections.)*

---

## 1. Users — *where the users are stored*
**Left nav → Users.** You see `farhaan`, `priya`.
- **Say:** "These users live in **this realm's user store** — for us, Keycloak's own database. This is the IdP being the *directory itself*. Alternatively (next screen, User federation) the users could live in **AD/LDAP** and Keycloak just reads them — which is exactly how PingFederate works: it doesn't store users, it reads a datastore."
- **Click a user → the tabs across the top** — walk them:

| Tab | What it is / what to say |
|---|---|
| **Details** | Core identity + **attributes** (email, first/last, email-verified). "These attributes become the **claims** in the token / the **AttributeStatement** in the SAML assertion." |
| **Credentials** | **How the user proves identity** — see §2. |
| **Role mapping** | Which **realm/client roles** this user has (`iam-team`, `payments-read`). "This is what the app turns into permissions." |
| **Groups** | Group membership → inherited roles (`/iam-engineers`). |
| **Sessions** | This user's **active SSO sessions** + which clients they're logged into — logout from here. |
| **Identity provider links** | If the user was **brokered** from an external IdP, the federated link shows here (empty for local users). |

- **Maps to:** Entra **Users**; PingFederate → the **datastore** (AD) record + the attributes pulled into the **attribute contract**.

---

## 2. Credentials — *how creds are managed per user* ⭐ (they'll ask this)
**Users → pick a user → Credentials tab.**
- **Say the important part:** "Notice you **cannot see the password** — only reset it. Keycloak stores it as a **salted hash** (PBKDF2-SHA512 by default, tens of thousands of iterations), never plaintext. Same discipline as any real IdP; the admin never holds the password."
- **Set password** dialog — point out **Temporary: On/Off**: "Temporary = the user is forced to change it at next login (a **required action**). That's your **Joiner** hand-off — issue a temp password, user sets their own."
- **Other credential types** on this tab: **OTP** (authenticator app), **WebAuthn / passkeys**, **recovery codes** — "MFA and passwordless live here per-user; the *policy* for them is set under Authentication."
- **Reset actions** (email the user to reset password / configure OTP / verify email) — "the self-service side of credential management."
- **Maps to:** Entra **Authentication methods** per user; PingFederate defers this to the datastore/AD + adapters (e.g., **PingID** for MFA).

> **One-liner for the room:** *"The IdP holds the credential (as a hash) and owns how it's proven — password, OTP, passkey. The app never sees any of it."*

---

## 3. Groups & Roles — *authorization*
- **Groups** (left nav): "Bundle users; a group carries roles, so membership = access. `/iam-engineers` grants `iam-team` + `payments-read`."
- **Realm roles** (left nav): the permission labels (`payments-read`, `payments-initiate`). "These flow into the token as `realm_access.roles` and into the SAML assertion as the `Role` attribute."
- **Say:** "This is the **authorization** half — the app maps these to what you can *do*."
- **Maps to:** Entra **groups / app roles**; PingFederate **attribute fulfillment**.

---

## 4. Clients — *the apps that trust us (onboarding)*
**Left nav → Clients.** Show the list: `kt-spa`, `kt-web`, `kt-service`, `kt-device`, `kt-implicit`, `kt-saml-app`, …
- **Say:** "Every entry is an **application onboarded to the IdP** — a **client**. OIDC clients and SAML clients live together, because one IdP serves both protocols (just like PingFederate). Onboarding an app = creating one of these."
- **Open `kt-spa` (OIDC)** and walk the tabs:

| Tab | What to say |
|---|---|
| **Settings** | **Client ID** (the app's identity), **Valid redirect URIs** (exact-match allow-list — the security control behind "Invalid redirect_uri"), **Web origins** (CORS), which **flows** are on (Standard = Auth Code). |
| **Credentials** | *(confidential clients like `kt-web`)* the **client secret** — "how a server app proves itself instead of PKCE." Public clients (SPA) have none. |
| **Advanced** | **PKCE** method (S256 on `kt-spa`), token lifetimes. |
| **Client scopes** | which **scopes/claims** this app gets. |

- **Open `kt-saml-app` (SAML)** — point out **Valid redirect URIs / Master SAML Processing URL = the ACS**, **Keys** (SP signing, off here), **IDP-initiated SSO URL name**. "This is the SP onboarding: Entity ID + ACS + which attributes we release."
- **Maps to:** Entra **App registrations / Enterprise applications**; PingFederate **SP connections / OAuth clients**.

---

## 5. Client scopes — *what goes in the token*
**Left nav → Client scopes.** Open one → **Mappers**.
- **Say:** "Scopes (`profile`, `email`) are bundles of **claims**. The **mappers** decide which user attribute/role becomes which claim in the token or SAML attribute. This is where `email`, `givenName`, `Role` come from in the assertion you saw."
- **Maps to:** PingFederate **attribute contract + fulfillment**; Entra **optional claims / token config**.

---

## 6. Identity providers — *federating to ANOTHER IdP (brokering)*
**Left nav → Identity providers.**
- **Say:** "This is the opposite direction — when **we** trust an *external* IdP (social login, a partner, or another SAML/OIDC IdP). Keycloak becomes a **broker**. The user authenticates elsewhere; we accept their assertion and (optionally) create a local shadow user."
- **Maps to:** PingFederate **IdP connections**; Entra **External identities / federation**.

---

## 7. User federation — *where users can come from AD/LDAP*
**Left nav → User federation.**
- **Say:** "Instead of storing users ourselves, we can point at **LDAP / Active Directory** — then the users on the Users screen are *read from AD*, and auth is validated against AD. **This is exactly the PingFederate model** (Ping never stores users; it reads a datastore). In our lab it's empty — we're using the built-in store — but this is the screen where 'where do users live' becomes 'in AD'."
- **Maps to:** PingFederate **datastores**; Entra Connect **sync from on-prem AD**.

---

## 8. Authentication — *how login actually happens + credential policy*
**Left nav → Authentication.** Three sub-tabs worth showing:
- **Flows** — the ordered steps of a login (username/password → OTP → …). "This is where **MFA** is inserted, where **conditional** steps live. In Ping this is the **policy tree**; in Entra it's **Conditional Access**."
- **Policies → Password policy** — length, complexity, hashing iterations, history. "The realm-wide **credential rules** — the other half of §2."
- **Policies → OTP policy** — TOTP/HOTP settings for MFA.
- **Required actions** — org-wide "everyone must verify email / update password" toggles.
- **Maps to:** PingFederate **adapters + authentication policies**; Entra **Conditional Access + authentication methods policy**.

---

## 9. Realm settings → Keys — *the signing certificates* ⭐
**Left nav → Realm settings → Keys tab.**
- **Say:** "These are the realm's **signing keys**. The **active RSA key** signs every **JWT** *and* every **SAML assertion**. This cert is what the SP fetched from our metadata to verify the assertion — remember the 'Invalid signature' issue? That was a cert mismatch. In production this key is a **crown jewel** (HSM-backed); rotating it is a planned, dual-key operation."
- Also in **Realm settings**: **Login** (email-as-username, remember-me), **Tokens** (lifetimes), **Sessions** (SSO idle/max).
- **Maps to:** PingFederate **signing certs** (per connection / global); Entra Microsoft-managed signing keys.

---

## 10. Sessions & Events — *audit*
- **Sessions** (left nav): every active SSO session across the realm — "who's logged in right now; kill a session here."
- **Realm settings → Events** (enable **Login events**): "the **audit trail** — logins, failures, consents, token issuance. This is the raw material your SOC turns into detections, and what an auditor asks for. In Ping this is **`audit.log`**; in Entra, **sign-in logs**."

---

## Suggested walk order for the KT (5–7 min)
1. **Realm dropdown** → "isolated identity world."
2. **Users** → open `farhaan` → **Details** (attributes) → **Credentials** (hashed, temp, MFA) → **Role mapping**.
3. **Clients** → `kt-spa` (OIDC: redirect URIs, PKCE) → `kt-saml-app` (SAML: ACS).
4. **User federation** → "or users come from AD — the Ping model."
5. **Authentication → Flows/Policies** → "where MFA + password rules live."
6. **Realm settings → Keys** → "the signing cert behind every token & assertion."
7. **Events** → "the audit trail."

**Close:** *"Same three questions on every screen — who are the users, how do they prove it, which apps trust us. Whether it's Keycloak, PingFederate, or Entra, that's the whole job of an IdP."*

*(Companion: [USER-MANAGEMENT.md](USER-MANAGEMENT.md) for the IdP-vs-App-team ownership split. Authorized-lab-only.)*
