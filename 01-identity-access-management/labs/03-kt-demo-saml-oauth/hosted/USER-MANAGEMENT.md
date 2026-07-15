# User management — who owns the user, and where you add them

> For the KT: the single most important federation idea is **the IdP owns the identity; the app trusts it.** This doc shows *where a user lives*, *where you add one* in this lab, and *how the IdP team and the App team split responsibilities* for each flow.

---

## The one principle
In federation (SAML / OIDC), **the application does not store users or passwords.** It trusts a **signed assertion / token** from the IdP. So:

- **IdP team** = source of truth for *identity*: create/disable users, credentials, MFA, attributes, group/role membership, the JML lifecycle.
- **App team** = consumes what the IdP sends: maps incoming **claims → app permissions**, and (optionally) keeps a **local mirror** of the user for app-specific data.

If the app starts keeping its own passwords, you've broken SSO.

---

## Where users live in THIS lab
Everything authenticates against **Keycloak, realm `finco-idp`** (our IdP / PingFederate stand-in). The two demo users (`farhaan`, `priya`) are defined in the realm import file:
`hosted/keycloak/realms/finco-idp-realm.json` → `"users": [ … ]`.

### Add a user — two ways

**A) Persisted (survives redeploys) — edit the realm import** *(recommended for the lab)*
In `finco-idp-realm.json`, add to the `users` array:
```json
{
  "username": "meera",
  "enabled": true,
  "emailVerified": true,
  "email": "meera@example.com",
  "firstName": "Meera", "lastName": "Rao",
  "credentials": [ { "type": "password", "value": "Passw0rd!", "temporary": false } ],
  "realmRoles": ["payments-read"]
}
```
Commit → redeploy Keycloak. *(The DB is ephemeral, so the import file is the durable source — users added only in the UI vanish on the next cold start.)*

**B) Quick / temporary — the admin console**
`https://<keycloak>/admin/` → realm **finco-idp** → **Users → Add user** (username, email, **Email verified: On**) → **Create** → **Credentials → Set password** (Temporary: **Off**) → **Role mapping** (assign `iam-team` / `payments-read` / …). *This is exactly the manual JML "Joiner" step.* It works immediately but is lost on the next Keycloak restart unless you also put it in the import file.

> **Attributes matter as much as the account.** The roles you assign here (`iam-team`, `payments-read`) are what show up as the `Role` attribute in the SAML assertion / the `realm_access.roles` in the JWT — i.e. what the app turns into permissions.

---

## Who manages users, per scenario

| Flow in the lab | Where the user lives | IdP team does | App team does |
|---|---|---|---|
| **SAML SSO** (SP-init / IdP-init) | IdP (`finco-idp`) | Own the user + credentials + MFA; release the right **attributes** (email, name, `Role`) | **Map assertion attributes → app roles**; never store a password. Register the SP (Entity ID + ACS) |
| **OIDC — Auth Code + PKCE / no-PKCE** | IdP | Same as above; issue the ID/access token | Validate the token; map claims → app roles; register the client (redirect URIs) |
| **ROPC (trusted first-party)** | IdP | Own the user; **note MFA can't apply on this path** | Collect credentials in the *first-party* app only; forward to the IdP; never persist the password |
| **Client Credentials (M2M)** | *No human* — a **service account** | Create the **client/service account** + its secret; grant it scopes; rotate the secret (PAM) | Own the calling service; request least-privilege scopes |
| **Device Code** | IdP | Own the user (auth happens on the phone) | The device app just polls; no user store |

### First federated login → "JIT provisioning" (a nuance to say out loud)
The first time a user logs into an app via federation, the app often **auto-creates a local record** (a *shadow account*) from the assertion — **just-in-time provisioning**. After that:
- **IdP** stays the source of truth for *authentication* (who you are, can you log in).
- The **app's local mirror** holds *app-specific* data (preferences, app-only roles).
- **Deprovisioning:** when the IdP disables the user (Leaver), the app should also disable/clean its mirror — that link is what audits check.

*(In this lab, the OIDC apps don't keep a separate store — they trust the token each time, the simplest model. The SAML SP keeps only a short session cookie.)*

---

## The JML lifecycle — split of duties
| Stage | IdP team | App team |
|---|---|---|
| **Joiner** | Create the user, set credentials/MFA, add to groups/roles | Ensure the app maps the new user's roles correctly (or JIT-provisions on first login) |
| **Mover** | Update group/role membership → attributes change | App automatically sees new roles in the next token/assertion — **remove old access too** |
| **Leaver** | **Disable the user** (kills all SSO) | Disable/clean the local mirror; revoke app sessions/tokens |

> **Why the Leaver step is a security control:** disable the user at the **IdP** and every federated app is cut off at once — that's the payoff of central identity. A user who still has an active app session/token after a Leaver event is the classic audit finding.

---

## How this maps to the real IdPs you run
| | Keycloak (this lab) | **Entra ID** | **PingFederate** |
|---|---|---|---|
| Where users live | built-in realm DB *(or federated LDAP)* | the Entra **directory** (often synced from on-prem AD) | **doesn't store users** — reads a **datastore** (AD/LDAP/JDBC) |
| Add a user | Users → Add user / import | Entra admin center / AD sync | add to the backing directory; Ping just reads it |
| Attributes → app | protocol mappers | claims mapping / app roles | **attribute contract + fulfillment** |
| App onboarding | create SAML/OIDC client | Enterprise app / App registration | **SP connection / OAuth client** |

**Takeaway for the room:** whichever product, the pattern is identical — **provision the human once at the IdP; every app consumes claims and never holds the password.** Your job on the IAM team is the IdP side (identities, attributes, connections); the app teams own claim-to-permission mapping and any local mirror.

*Authorized-lab-only · demo users/passwords, never real data 🔐*
