# Runbook for the Claude agent on Farhaan's laptop — lab 05 PingFederate

> **Who this is for.** A Claude Code session running on Farhaan's Windows 11 laptop, driven
> remotely from his phone. He is away from the keyboard. Your job: get a lab PingFederate running
> in Docker, with everything stored on the **D: drive**, expose its runtime through a tunnel,
> configure it, and report back.
>
> **Read first:** [`../README.md`](../README.md) (the lab) — especially its status table. §3 of
> that README has **never been run**. You are the first to run it, and correcting it is part of
> the job.

## Ground rules

1. **Authorized-lab-only.** Evaluation licence, dummy users, made-up banks. Never import or copy
   any FinCo/work configuration, certificate, hostname or credential.
2. **Secrets stay out of git and out of chat.** The admin password and the OpenToken password
   live only in `pingfederate/.env`; the licence lives only in `pingfederate/license/`. Both are
   git-ignored. Never print them, never
   commit them, never put them in a commit message or a report. Run `git status` before any commit.
3. **Stop and ask before anything destructive.** In particular: `wsl --unregister`, deleting a
   `.vhdx`, `docker system prune`, removing volumes, or changing Windows settings outside what
   this runbook names. Farhaan answers from his phone, so ask short, self-contained questions.
4. **Verify, then report.** Each phase ends with a check. Report the check's real output. If a
   step fails, say so with the error; do not paper over it.
5. **The admin console (port 9999) is never exposed.** Only the runtime port 9031 goes through
   the tunnel.

## What should already be on the laptop

- Laptop on, signed in, not set to sleep (Settings → System → Power → Sleep: Never while plugged in).
- This repo cloned inside Ubuntu, on the lab branch (or `main` once merged).
- A PingFederate **trial licence file** (`.lic`) that Farhaan generated from his Ping Identity
  account, and the PingFederate **`.zip`** he downloaded. They are probably in his Windows
  Downloads folder.

**Your first job is to finish this setup, without asking for anything you can find yourself:**

```bash
cd 01-identity-access-management/labs/05-idp-initiated-sso-opentoken/pingfederate
ls /mnt/c/Users/*/Downloads/*.lic /mnt/d/*.lic /mnt/d/*/*.lic 2>/dev/null     # find the licence
ls /mnt/c/Users/*/Downloads/pingfederate*.zip /mnt/d/pingfederate*.zip 2>/dev/null
```

1. Copy the licence to `license/pingfederate.lic`. If there are several `.lic` files, or none,
   ask him which one.
2. Read the licence (it is a text file) and note its **product**, **version** and **expiry
   date**. Do not print its signature block. If it is not for PingFederate or has expired, stop
   and tell him.
3. If `.env` is missing: copy `env.example` to `.env`, generate `PF_ADMIN_PASSWORD` and
   `OPENTOKEN_PASSWORD` with `openssl rand -base64 32`, and tell him the values are in the file.
   He will need to read both from the file later.
4. Set `PF_IMAGE_TAG` in `.env` to a Docker tag of the **same major.minor version as the
   licence**. List real tags first; do not guess:
   `curl -s 'https://hub.docker.com/v2/repositories/pingidentity/pingfederate/tags?page_size=100' | grep -o '"name":"[^"]*"'`

---

## Phase 0 — Look before touching (read-only)

Run these and report a short summary. Change nothing yet.

```powershell
wsl --version
wsl -l -v
Get-PSDrive D | Select-Object Used,Free
Get-ChildItem "$env:LOCALAPPDATA\Packages" -Filter "*Ubuntu*" -ErrorAction SilentlyContinue | Select-Object Name
Get-Command docker -ErrorAction SilentlyContinue
```

```bash
# inside the Ubuntu distro
df -h / ; free -g ; docker version 2>/dev/null | head -5 ; docker info 2>/dev/null | grep -i "docker root dir"
```

Decide which case applies:

| Finding | Case |
|---|---|
| Docker Desktop is installed on Windows | **A** |
| No Docker Desktop; Docker Engine is (or will be) installed inside Ubuntu | **B** |

✅ **Checkpoint:** you know the distro's exact name, where its disk file lives now, how much space
D: has free (need **20 GB+**), and whether it is case A or B.

## Phase 1 — Put the storage on D:

Goal: the Ubuntu distro's disk **and** Docker's images/volumes live under `D:\WSL\`.

### 1a. Move the Ubuntu distro (both cases)

Skip if `wsl -l -v` already shows it running from D: (check the `BasePath` under
`HKCU:\Software\Microsoft\Windows\CurrentVersion\Lxss`).

Preferred, non-destructive on recent WSL (2.3+):

```powershell
wsl --shutdown
wsl --manage <DistroName> --move D:\WSL\<DistroName>
```

Fallback if `--move` is not supported — **export first, verify, and only then ask before
unregistering**:

```powershell
wsl --shutdown
New-Item -ItemType Directory -Force D:\WSL\backup, D:\WSL\<DistroName>
wsl --export <DistroName> D:\WSL\backup\<DistroName>.tar
Get-Item D:\WSL\backup\<DistroName>.tar | Select-Object Length      # must be > 0 and plausible
# STOP: ask Farhaan to confirm before the next line. It deletes the original distro.
wsl --unregister <DistroName>
wsl --import <DistroName> D:\WSL\<DistroName> D:\WSL\backup\<DistroName>.tar --version 2
```

After an import the default user becomes root. Restore it by adding to `/etc/wsl.conf` inside the
distro (`[user]` / `default=<his-username>`), then `wsl --shutdown`.

> If *this Claude session itself runs inside that Ubuntu distro*, moving it will kill the session.
> In that case tell Farhaan first, and run the move from a PowerShell-hosted session instead.

### 1b. Docker's data

- **Case A (Docker Desktop):** Docker Desktop → Settings → Resources → Advanced → **Disk image
  location** → `D:\WSL\DockerDesktop` → Apply & restart. Also enable Settings → Resources → WSL
  integration for the Ubuntu distro. This needs the GUI; if you cannot drive it, ask Farhaan.
- **Case B (Engine inside Ubuntu):** nothing extra. Docker's data lives in `/var/lib/docker`
  inside the distro, whose disk is now on D:. If Docker is not installed, install Docker Engine
  from Docker's official apt repository and add the user to the `docker` group.

✅ **Checkpoint:** `wsl -l -v` shows the distro running; its `.vhdx` is under `D:\WSL\`;
`docker run --rm hello-world` succeeds inside Ubuntu.

## Phase 2 — Start PingFederate and the tunnel

Inside Ubuntu, in this folder. Keep the repo on the Linux filesystem (not `/mnt/c` or `/mnt/d`),
or file access will be slow.

```bash
test -f .env || { echo "STOP: .env missing"; exit 1; }
test -f license/pingfederate.lic || { echo "STOP: licence file missing"; exit 1; }
docker compose pull
docker compose up -d
docker compose logs -f pingfederate        # wait for the server to finish starting (2-4 min)
```

| Log line | Meaning |
|---|---|
| Licence invalid / wrong version / expired | The image version and the licence version differ, or the trial ended. Fix `PF_IMAGE_TAG`, or tell Farhaan to generate a new licence. |
| `license/pingfederate.lic` is a *directory* | `up` ran before the file existed. `docker compose down`, delete the empty folder, copy the file, start again. |
| Out-of-memory or the container restarts in a loop | Give WSL more memory: `%UserProfile%\.wslconfig` → `[wsl2]` / `memory=6GB`, then `wsl --shutdown` |
| Cannot sign in to the admin API as `administrator` | The image's start-up profile did not create the account. Read the container log for the first-run steps it took, and check the image documentation before trying anything else. |

### Fallback — no Docker image matches the licence

Run the `.zip` Farhaan downloaded instead. It is the exact version the licence was issued for.

1. Install a supported Java (17 or 21) in Ubuntu and set `JAVA_HOME`.
2. Unzip under the distro's home folder (which is on D: after Phase 1), copy the licence to
   `pingfederate/server/default/conf/pingfederate.lic`, and start `pingfederate/bin/run.sh` in the
   background with its output in a log file.
3. A fresh install needs first-run setup (accept the licence agreement, create the first
   administrator). Check the admin API docs for doing this without the browser; if it can only be
   done in the console, ask Farhaan to do that one step when he is back.
4. Start only the tunnel from compose, pointed at the host:
   `docker run -d --name lab05-tunnel --network host cloudflare/cloudflared:latest tunnel --no-autoupdate --no-tls-verify --url https://localhost:9031`
5. Logs are then under `pingfederate/log/` in the unzipped folder, not `/opt/out/instance/log/`.

Tell Farhaan which path you took and why.

Get the public URL:

```bash
docker compose logs tunnel | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' | tail -1
```

✅ **Checkpoint (report all three):**

```bash
curl -sk -o /dev/null -w '%{http_code}\n' https://localhost:9999/pingfederate/app      # 200 or 302
curl -sk -o /dev/null -w '%{http_code}\n' https://localhost:9031/pf/heartbeat.ping     # 200
curl -s  -o /dev/null -w '%{http_code}\n' <TUNNEL_URL>/pf/heartbeat.ping               # 200
```

**Then send Farhaan the tunnel URL.** He has to set it as `PF_PUBLIC_URL` on the Render service
`lab05-banks` and restart that service (README §2). Also ask him for `BANKS_URL` and `APP_URL`
(the two Render URLs); you need them for Phase 3.

> The tunnel URL changes whenever the `tunnel` container restarts. Do not restart it without
> telling him, because Render and PingFederate's Base URL must then be updated.

## Phase 3 — Configure PingFederate

Target state = README §3.1 to §3.5. He is not at the screen, so use the **admin API** instead of
the console. Same objects, same fields.

```bash
set -a; . ./.env; set +a
PF() { curl -sk -u "administrator:${PF_ADMIN_PASSWORD}" -H 'X-XSRF-Header: PingFederate' \
            -H 'Content-Type: application/json' "$@"; }
PF https://localhost:9999/pf-admin-api/v1/version
```

The API documents itself at `https://localhost:9999/pf-admin-api/api-docs/`. **The payload shapes
below are not given on purpose** — nobody has run them against this version. Read each one from
the live server first (GET the existing object or its descriptor), change only the needed fields,
then PUT/POST, then GET it back to confirm.

| README step | Likely endpoint (confirm in api-docs) | Set |
|---|---|---|
| §3.1 Federation Info | `/serverSettings` | base URL = tunnel URL; SAML 2.0 entity ID = `lab05:finco:pingfederate:sp` |
| §3.2 OpenToken SP adapter | `/sp/adapters` (field names from `/sp/adapters/descriptors`) | id `bankPortalOpenToken`; password = `OPENTOKEN_PASSWORD`; transport = query parameter; token name `opentoken`; extended attributes `email`, `first_name`, `last_name` |
| §3.3 / §3.4 IdP connections | `/sp/idpConnections` (`/connectionMetadata/convert` can turn a metadata XML into a starting payload) | one per bank, IdP-initiated SSO only, adapter mapping per the README's table, default target URL `APP_URL/sso/launch/org-1001` and `…/org-2002`, active |
| §3.5 Allow-list | `/redirectValidation` | target resource validation on; allow the `APP_URL` host with path `/sso/launch/*`, HTTPS required |

`OPENTOKEN_PASSWORD` must equal the value on the Render service `lab05-bank-portal`. Ask Farhaan
to add `OPENTOKEN_PASSWORD=<value>` to `.env` himself, or generate one, write it to `.env`, and
tell him to copy it **from the file** into Render. Do not echo it in chat.

Bank metadata: `BANKS_URL/realms/bank-a/protocol/saml/descriptor` and `…/bank-b/…`. The Render
service may be asleep; the first request can take a minute.

✅ **Checkpoint:** GET each object back and report (without secrets): both connections `active`,
their entity IDs, their default target URLs, the adapter id, and the allow-list entries.

## Phase 4 — Prove the whole flow

Do one login per bank. `LAB_USER_PASSWORD` is the value Farhaan set on Render; ask him for it.

Follow the redirects by hand with `curl` and a cookie jar, or with a headless browser:

1. GET `BANKS_URL/realms/bank-a/protocol/saml/clients/bank-portal` → Keycloak login form.
2. POST the form as `alice` → a page with a hidden form holding `SAMLResponse` and `RelayState`.
3. POST those to `<TUNNEL_URL>/sp/ACS.saml2` → a redirect to `APP_URL/sso/launch/org-1001?opentoken=…`.
4. GET that → `303` to `/` with a `bp_session` cookie; the home page shows "Alice Anand" and Bank A.

Repeat for `bank-b` / `bob` (no RelayState; expect `org-2002`).

Then show the audit trail:

```bash
docker compose exec pingfederate tail -n 5 /opt/out/instance/log/audit.log
```

If a step fails, read `/opt/out/instance/log/server.log` and the README's troubleshooting table
before changing anything.

✅ **Checkpoint:** both users land signed in, and `APP_URL/sso/log` shows two `accepted` rows.
This is also the first proof that the app decodes a token from a real PingFederate — say clearly
whether it worked.

## Phase 5 — Report and tidy

Send Farhaan:

1. What is running, the tunnel URL, and where the data lives on D:.
2. Each phase's checkpoint result — passed, failed (with the error), or skipped (with the reason).
3. **Every place README §3 was wrong** for this PingFederate version: menu paths, field names,
   defaults. Then fix the README, re-run its 20-second Lefler checklist, and commit **only**
   documentation and non-secret files to the lab branch. Do not push unless he says so.
4. What he still has to do himself.

Leave PingFederate and the tunnel **running**. Do not `docker compose down`.
