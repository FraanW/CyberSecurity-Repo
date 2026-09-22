# Linux command line basics — navigate, view, find, change, edit (a working cheat-sheet)

> **TL;DR.** The Linux terminal feels scary until you learn ~20 commands — then you *drive* instead of copy-pasting. This note is your reference: the mental model (files are a tree), the five things you actually do (**move around → look inside → find stuff → change files → edit files**), and how it all maps to your **Kubernetes** work at FinCo. Keep this open; `grep` it when you forget a command. This replaces the OneNote copy-paste habit.

---

## Prerequisites

- **Time:** 10 min to skim; keep it as a lookup after that.
- **Difficulty:** beginner. Zero prior terminal experience assumed.
- **What you need:** your **Ubuntu on WSL** (Windows 11). Open it and type along.
- **How to read commands:** anything after a `#` is a comment — *don't type it*. Text like `<file>` means "put your own filename here."

---

## 0. The one mental model — everything is a tree 🌳

Linux files live in a single **tree**. The trunk is the **root**, written `/`. Every folder branches from it.

- A **directory** = a **folder**. Same thing, different word.
- Where you're "standing" in the tree = your **working directory**. Every command runs *from where you're standing*.
- `~` is a shortcut for **your home folder** (`/home/yourname`) — where your stuff lives.

**The `/` folders worth knowing (ignore the rest for now):**

| folder | what's in it |
|---|---|
| `/home` | your personal files (you live in `/home/yourname`) |
| `/etc` | **config** files — settings for everything ("et-see") |
| `/var` | changing data — **logs live in `/var/log`** |
| `/tmp` | temporary scratch space (wiped on reboot) |
| `/bin`, `/usr` | the programs/commands themselves |

---

## 1. Move around 🧭

| type | means | example |
|---|---|---|
| `pwd` | *where am I?* (print working directory) | `pwd` → `/home/farhaan` |
| `ls` | *what's here?* (list) | `ls` |
| `ls -la` | list **all** (incl. hidden) in **long** detail | `ls -la` |
| `cd <dir>` | *walk into* a folder (change directory) | `cd /var/log` |
| `cd ~` | go **home** | `cd ~` |
| `cd ..` | go **up** one level | `cd ..` |
| `cd -` | jump **back** to the last folder | `cd -` |

> **Gotcha:** `cd` with no name = straight home. `..` means "the folder above me." A single `.` means "right here."

---

## 2. Look inside files 👀

| type | means | when to use |
|---|---|---|
| `cat <file>` | dump the **whole** file | small files |
| `less <file>` | **scroll** a big file (press **`q`** to quit) | big files, logs |
| `head <file>` | **first** 10 lines | peek at the top |
| `tail <file>` | **last** 10 lines | logs (newest is at the bottom) |
| `tail -f <file>` | **follow** live — new lines appear as they're written | watching a log in real time |

**The word a senior will say to you:** `tail`. It shows the *end* of a file. `head` shows the *start*. That's the whole trick.

> **Gotcha:** `less` traps everyone the first time. **Press `q`** to get out. Scroll with ↑ ↓ or Page Up/Down; search inside with `/word` then Enter.

```bash
cat /etc/os-release        # your Ubuntu version (small file)
tail -f /var/log/syslog    # watch the system log live — Ctrl+C to stop
```

---

## 3. Find stuff 🔍 (the power tools)

| type | means | example |
|---|---|---|
| `grep "word" <file>` | find **lines containing** "word" inside a file | `grep error app.log` |
| `grep -i "word" <file>` | ignore capital/lowercase | `grep -i warning app.log` |
| `grep -r "word" <dir>` | search **every file** under a folder | `grep -r "password" /etc` |
| `find <path> -name "x"` | find **files named** x (wildcards ok) | `find /etc -name "*.conf"` |
| <code>cmd \| grep "word"</code> | **the pipe** — filter *any* command's output | `ls -la \| grep .yaml` |

**The pipe `|` is the single biggest unlock.** It takes the output of one command and feeds it into the next. You'll use it constantly (see the Kubernetes section).

```bash
grep root /etc/passwd              # which accounts mention "root"
cat /etc/os-release | grep VERSION # filter output down to the VERSION lines
```

---

## 4. Change files ✋ (create, copy, rename, delete)

| type | means | example |
|---|---|---|
| `mkdir <name>` | make a folder | `mkdir project` |
| `touch <file>` | create an empty file | `touch notes.txt` |
| `cp <a> <b>` | copy a → b | `cp notes.txt backup.txt` |
| `cp -r <a> <b>` | copy a **folder** (recursive) | `cp -r project project-copy` |
| `mv <a> <b>` | **move OR rename** (same command!) | `mv notes.txt readme.txt` |
| `rm <file>` | **delete** — ⚠️ permanent | `rm backup.txt` |
| `rm -r <dir>` | delete a **folder** and everything in it | `rm -r old-project` |

### ⚠️ Danger zone — `rm` has no undo

There is **no Recycle Bin** on the Linux command line. `rm` means *gone forever*.

- **Golden rule:** run `ls` to look *before* you `rm`.
- **Never** run `rm -rf /` or a careless `rm -rf *` — that deletes everything, no questions asked.
- On work servers you rarely delete — you mostly *read and inspect*. When in doubt, don't `rm`; ask.

**Safe practice sandbox** — do experiments where nothing real can break:

```bash
mkdir ~/practice && cd ~/practice   # make a sandbox and go in
touch a.txt && cp a.txt b.txt       # create + copy
mv a.txt renamed.txt                # rename
ls                                  # ✅ see: b.txt  renamed.txt
rm -r ~/practice                    # tear it all down when done
```

---

## 5. Edit files ✏️ — `nano`

`nano <file>` opens a beginner-friendly editor. Two shortcuts trip everyone up (nano shows them at the bottom, where `^` means the **Ctrl** key):

| keys | does |
|---|---|
| `Ctrl`+`O`, then `Enter` | **save** (Write Out) |
| `Ctrl`+`X` | **exit** |

```bash
nano hello.txt   # type text → Ctrl+O, Enter to save → Ctrl+X to exit
cat hello.txt    # ✅ your text is there
```

> **The other editor — `vim`:** powerful but confusing. If a senior's terminal drops you into `vim` and you're stuck, type **`:q!`** then Enter to escape without saving. (The classic "how do I exit vim" moment — now you know.)

---

## 6. Flags & help — decode any command 🏳️

Most commands take **flags** (options starting with `-`). A few show up everywhere:

| flag | usually means |
|---|---|
| `-r` / `-R` | **recursive** (include sub-folders) |
| `-i` | **ignore case** (grep) / **interactive/confirm** (rm, cp) |
| `-a` | **all** (incl. hidden) |
| `-l` | **long** listing / **list** |
| `-f` | **follow** (tail) / **force** (rm — dangerous) |
| `-n` | a **number** (e.g. `tail -n 50` = last 50 lines) |

**When you forget how a command works:**

```bash
tail --help    # quick summary of options
man tail       # full manual (press q to quit)
```

---

## 7. Why this matters for your job — Kubernetes at FinCo 🚢

Here's the payoff. **Once you `kubectl exec` into a pod, it's just Linux** — every command above works inside it. And the pipe + `grep` + `tail` you just learned are exactly how you read what a cluster is doing.

| task | command |
|---|---|
| list pods | `kubectl get pods` |
| list pods in a namespace | `kubectl get pods -n <namespace>` |
| **find your pod** in a huge list | `kubectl get pods \| grep <myapp>` |
| a pod's recent logs | `kubectl logs <pod>` |
| **last 50 log lines** | `kubectl logs <pod> \| tail -50` |
| **follow logs live** | `kubectl logs -f <pod>` |
| full details of a pod | `kubectl describe pod <pod>` |
| **get a shell inside a pod** | `kubectl exec -it <pod> -- bash` |

```bash
# The move seniors use constantly — find one pod in a list of hundreds:
kubectl get pods -n payments | grep auth-service
```

> **Ties back to IAM:** those pods talk to each other over **mTLS via a service-mesh sidecar** (your team's setup). When you `kubectl logs` a sidecar and `grep` for a cert or handshake error, *this* is the identity layer you own — you're reading authentication happening on the wire.

### Switching clusters / cloud subscriptions

You mentioned wrestling with "subscriptions." Two likely meanings:

```bash
# Kubernetes: which cluster am I pointed at, and switch between them
kubectl config get-contexts        # list clusters you can reach
kubectl config use-context <name>  # switch to one

# Azure (if your AKS lives there): list & pick a subscription
az account list -o table           # readable table of subscriptions
az account set --subscription "<name>"
```

*(If your cloud is AWS/GCP instead, tell me and I'll add those.)*

---

## 8. Kill the OneNote habit 🗒️➡️⌨️

Instead of alt-tabbing to OneNote and copy-pasting, keep your commands **in a file you search from the terminal** — combining `nano` (§5) and `grep` (§3):

```bash
nano ~/cheats.md              # paste your commands here, one per line, save (Ctrl+O, Ctrl+X)
grep kubectl ~/cheats.md      # instantly pull up your kubectl commands
```

**Even better — make a nickname (alias) for commands you retype:**

```bash
alias k=kubectl               # now 'k get pods' works
```

To make an alias **stick** (survive closing the terminal), add it to your startup file:

```bash
echo "alias k=kubectl" >> ~/.bashrc   # add it permanently
source ~/.bashrc                       # load it now without reopening
```

> `~/.bashrc` runs every time you open a terminal. Add your favorite aliases there once, and they're always ready.

---

## What you learned

1. **Files are a tree** rooted at `/`; `~` is home; you always run from *where you're standing*.
2. **Move** — `pwd` `ls` `cd`
3. **View** — `cat` `less` `head` **`tail`** (and `tail -f` for live logs)
4. **Find** — `grep` `find` and the **pipe `|`**
5. **Change** — `mkdir` `touch` `cp` `mv` `rm` (⚠️ no undo)
6. **Edit** — `nano` (`Ctrl+O` save, `Ctrl+X` exit), escape `vim` with `:q!`
7. **Kubernetes is just Linux inside** — the same skills read your cluster.

## Next

- **Do it:** open your Ubuntu and run the §4 sandbox story until it's muscle memory.
- **Build your `~/cheats.md`** from your OneNote commands and start `grep`-ing it.
- **Then:** ask Lefler to spin up a first hands-on lab (nmap enumeration on Metasploitable) — you'll navigate the whole thing with the commands above.
- **Related:** [`01-first-principles-and-empirical-thinking.md`](01-first-principles-and-empirical-thinking.md) — how to *learn* everything in this repo.
