# Docker & Kubernetes Question Bank — from "warm-up" to "senior SRE / platform-security expert"

> **Lefler & Janus's drill.** Everything a senior platform, SRE, or **container-security** interviewer could grill you on — plus the **edge cases and production incidents** that separate "I've read the docs" from "I've been paged at 3 a.m." Ordered **easy → very hard**, every question with a **model answer in a spoiler** so you can self-test: *read the question, answer aloud, then expand to check.*
>
> **Prereqs:** [Docker reference](26-docker-complete-reference.md) and [Kubernetes reference](27-kubernetes-complete-reference.md). **How to use:** cover the answers, go tier by tier, say answers *aloud* — interviews are spoken, not written. Tie security answers back to **IAM at FinCo** where you can; that's your edge.

---

## How this bank is organized

| Tier | Level | What it tests |
|---|---|---|
| **1** | Warm-up (easy) | Definitions: container vs VM, image vs container, pod, what K8s is |
| **2** | Core (medium) | Dockerfile, layers, Services, Deployments, networking basics |
| **3** | Hard | Internals (namespaces/cgroups), scheduling, RBAC, probes, storage |
| **4** | Expert | Security deep cuts, workload identity, admission control, spec edge cases |
| **E** | Edge cases | The nasty gotchas — exit codes, races, silent failures |
| **S** | Scenarios | "Here's a broken cluster — diagnose it." The real job |
| **R** | Rapid-fire | One-line answers to drill until reflex |
| **↩** | Reverse | Sharp questions *you* ask the interviewer |

**Legend in answers:** ★ = say-this-and-you-sound-senior · ⚠ = common wrong answer to avoid.

---

## Tier 1 — Warm-up (easy)

**Q1.1 — In one sentence, what is a container, and how is it different from a VM?**

<details><summary>Model answer</summary>

A **container is a normal process** the kernel isolates with **namespaces** (private view) and limits with **cgroups** (resource caps) — it **shares the host kernel**. A **VM** runs a **full guest OS on a hypervisor** with its own kernel. ★ "A VM has a *hardware* boundary — strong isolation, gigabytes, minute to boot. A container has a *kernel* boundary — cheap, megabytes, milliseconds, but a kernel bug is shared blast radius." ⚠ Don't call a container "a lightweight VM" — there's no second OS inside.
</details>

**Q1.2 — What's the difference between an image and a container?**

<details><summary>Model answer</summary>

An **image** is the immutable, read-only template — a stack of layers + metadata. A **container** is a **running (or stopped) instance** of that image: the read-only layers **plus one thin writable layer** on top. ★ "One image → many containers. The image is the class; the container is the object." The writable layer **dies with the container** — persistent data needs a volume.
</details>

**Q1.3 — What is Kubernetes, in plain terms?**

<details><summary>Model answer</summary>

An **orchestrator**: it runs your containers across many machines, and **keeps them in the state you declared** — restarting, rescheduling, scaling, and updating them automatically. ★ "It's a **feedback control loop for infrastructure**: you declare desired state, controllers continuously reconcile reality to match." ⚠ It's not "a way to start containers" — that undersells the reconciliation model.
</details>

**Q1.4 — What is a Pod?**

<details><summary>Model answer</summary>

The **smallest deployable unit** in K8s: **one or more containers that share a network namespace** (same IP, same `localhost`) and can share volumes. ★ "You rarely create Pods directly — a **Deployment** or **StatefulSet** manages them. And a Pod is **mortal**: it gets an IP, but its replacement gets a new one, which is *why* Services exist."
</details>

**Q1.5 — Docker vs Kubernetes — are they competitors?**

<details><summary>Model answer</summary>

No — different layers. **Docker builds and runs a container on one host.** **Kubernetes orchestrates many containers across many hosts.** ★ "Docker is the brick; K8s is the building. In fact K8s doesn't even use the Docker *daemon* anymore — it talks to **containerd** via the CRI — but it runs Docker-built **images** unchanged, because the **OCI image spec** is the real contract."
</details>

**Q1.6 — What does `docker run -p 8080:80 nginx` do, and what's the common mistake?**

<details><summary>Model answer</summary>

Runs an nginx container and **publishes** host port 8080 → container port 80. ⚠ The common mistake: assuming **`EXPOSE 80`** in the Dockerfile publishes the port — it doesn't; `EXPOSE` is **documentation only**. Only `-p`/`--publish` (or K8s Service) actually routes traffic. "Server runs but I can't reach it" is almost always a missing `-p`.
</details>

---

## Tier 2 — Core (medium)

**Q2.1 — Why does Dockerfile instruction order matter? Give the canonical example.**

<details><summary>Model answer</summary>

Each `RUN/COPY/ADD` is a **cached layer**; a change busts that layer **and everything after it**. So put **least-changing** things first. ★ Canonical: copy the dependency manifest and install **before** copying source —
```dockerfile
COPY requirements.txt .
RUN pip install -r requirements.txt   # cached until requirements.txt changes
COPY . .                              # code changes don't reinstall deps
```
⚠ `COPY . .` then `RUN pip install` reinstalls every dependency on every code change.
</details>

**Q2.2 — What's a multi-stage build and why use one?**

<details><summary>Model answer</summary>

A build with multiple `FROM` stages: a **build stage** with the full toolchain, and a tiny **runtime stage** that `COPY --from=build` copies **only the finished artifact**. ★ Two wins: **size** (800 MB → <20 MB) and **security** — the runtime image has **no compiler, shell, or package manager** for an attacker to abuse. Pair with a **distroless** runtime base for the strongest form. This is the single highest-leverage Docker practice.
</details>

**Q2.3 — ENTRYPOINT vs CMD?**

<details><summary>Model answer</summary>

**ENTRYPOINT** = the fixed executable that always runs ("what this image *is*"). **CMD** = default arguments, **overridable** at `docker run`. Together: `ENTRYPOINT ["nginx"]` + `CMD ["-g","daemon off;"]` runs `nginx -g "daemon off;"`, but `docker run img -v` runs `nginx -v`. ★ Always use **exec form** (`["cmd","arg"]`), not shell form — see the SIGTERM gotcha in E-tier.
</details>

**Q2.4 — What is a Kubernetes Service and what problem does it solve?**

<details><summary>Model answer</summary>

Pods are mortal and get **new IPs** when replaced, so you can't hardcode a pod IP. A **Service** is a **stable virtual IP + DNS name** that **load-balances** to the current set of matching pods (selected by **label**). ★ "It's not a process — it's **iptables/IPVS/eBPF rules** that **kube-proxy** programs on every node, backed by an **EndpointSlice** that tracks healthy pod IPs. CoreDNS gives it a name like `auth.default.svc`."
</details>

**Q2.5 — Name the Service types and when you'd use each.**

<details><summary>Model answer</summary>

- **ClusterIP** (default) — internal only; service-to-service.
- **NodePort** — opens a port (30000–32767) on every node; quick external / behind an LB.
- **LoadBalancer** — provisions a cloud load balancer; internet-facing on a cloud.
- **ExternalName** — DNS CNAME to an off-cluster host.
★ "For real HTTP with host/path routing + TLS you go past Services to **Ingress / Gateway API**."
</details>

**Q2.6 — Deployment vs StatefulSet?**

<details><summary>Model answer</summary>

**Deployment** = interchangeable **cattle**: identical pods, any replica serves any request, order doesn't matter — for **stateless** apps. **StatefulSet** = named **pets**: stable identity (`db-0`,`db-1`), **each pod its own persistent volume**, ordered startup/shutdown — for **databases/brokers**. ★ "Use StatefulSet only when identity or per-pod storage truly matters; default to Deployment." ⚠ Don't reach for StatefulSet just because there's a volume — a Deployment can mount shared storage too.
</details>

**Q2.7 — How do two containers in a Compose file (or two pods) find each other?**

<details><summary>Model answer</summary>

**By name, via DNS.** Compose creates a user-defined network with an embedded DNS server, so `db` resolves to the db container. In K8s, **CoreDNS** resolves a **Service** name (`db.default.svc.cluster.local`). ★ "You never hardcode IPs — service discovery by name is the whole point. On Docker's *default* bridge there's no DNS, which is why you always create a user-defined network."
</details>

---

## Tier 3 — Hard

**Q3.1 — Which Linux kernel features make a container, and what does each do?**

<details><summary>Model answer</summary>

- **Namespaces** — *isolation* (what a process can **see**): PID, mount, network, UTS, IPC, **user**, cgroup, time.
- **cgroups (v2)** — *limits* (what it can **use**): CPU, memory, I/O, PIDs.
- **Union/overlay filesystem** (overlay2) — cheap **layered** rootfs with copy-on-write.
★ "The **user namespace** is the security keystone — it maps root *inside* to an unprivileged UID *outside*, the basis of **rootless** containers." Plus **capabilities** and **seccomp** to shrink what the process may do.
</details>

**Q3.2 — What are requests and limits, and how do they relate to QoS and OOMKilled?**

<details><summary>Model answer</summary>

**requests** = reserved capacity the **scheduler** guarantees (won't overbook a node). **limits** = the hard cap the **cgroup** enforces; exceed the **memory limit** → **OOMKilled (exit 137)**. QoS is derived: **Guaranteed** (requests==limits) > **Burstable** > **BestEffort** (none set); under node memory pressure, **BestEffort is evicted first**. ★ "CPU over-limit gets **throttled**; **memory** over-limit gets **killed** — memory is incompressible."
</details>

**Q3.3 — Explain the three probes and the classic mistake.**

<details><summary>Model answer</summary>

- **liveness** — "is it wedged?" fail → **restart** the container.
- **readiness** — "ready for traffic now?" fail → **remove from Service endpoints** (no restart).
- **startup** — "has a slow app booted?" gates the other two during boot.
⚠ The classic outage: an aggressive **liveness** probe on a **slow-starting** app keeps restarting it before boot completes → permanent **CrashLoopBackOff**. Fix with a **startup probe** or longer `initialDelaySeconds`. ★ "Readiness protects *traffic*; liveness protects *the process* — confusing them causes cascading restarts under load."
</details>

**Q3.4 — Walk through what happens from `kubectl apply -f deploy.yaml` to a running pod.**

<details><summary>Model answer</summary>

1. **kubectl** → **API server**: authN (cert/token/OIDC) → authZ (**RBAC**) → **admission** (validate/mutate) → write object to **etcd**.
2. **Deployment controller** sees the new spec → creates a **ReplicaSet** → which creates **Pods** (still unscheduled).
3. **Scheduler** sees pods with no node → scores nodes (resources, affinity, taints) → binds each pod to a node.
4. That node's **kubelet** sees the assignment → tells **containerd** (via CRI) to pull the image and start containers.
5. **kube-proxy** + **EndpointSlice** wire Service networking so traffic reaches the ready pod.
★ Name the **one door**: everything flows through the **API server**; nothing else touches etcd.
</details>

**Q3.5 — Explain Kubernetes RBAC: the four objects and the golden rules.**

<details><summary>Model answer</summary>

- **Role** / **ClusterRole** = a set of **permissions** (verbs × resources); Role is namespace-scoped, ClusterRole cluster-wide.
- **RoleBinding** / **ClusterRoleBinding** = **grants** a role to a **subject** (user, group, or **ServiceAccount**).
★ Golden rules: **least privilege**, **no wildcards** (`["*"]`), almost never bind **cluster-admin**, and give **every workload its own ServiceAccount** (never the shared `default`), scoped to exactly what it needs. "This is application RBAC/ABAC applied to the infrastructure — same principle I enforce in IAM."
</details>

**Q3.6 — PV, PVC, StorageClass, CSI — how do they fit together?**

<details><summary>Model answer</summary>

A pod requests storage with a **PVC** ("10Gi, ReadWriteOnce"). It binds to a **PV** (a real disk/share) — either pre-created (static) or created on demand by a **StorageClass** via a **CSI driver** (dynamic). ★ "A **StatefulSet** gives each pod its **own** PVC (`data-db-0`…) that survives reschedule — that's how databases keep their data. Access modes matter: **RWO** = one node at a time; most block storage is RWO, which surprises people who expect many pods to share it."
</details>

**Q3.7 — What is a DaemonSet and give a security-relevant use.**

<details><summary>Model answer</summary>

A controller that runs **exactly one pod per node** (and on new nodes automatically). ★ Security uses: node-level **log collectors**, the **CNI**, and **runtime-security sensors like Falco/eBPF** that must observe every node. "In fintech, your Falco DaemonSet is how you get runtime threat detection coverage on 100% of nodes without remembering to deploy it."
</details>

---

## Tier 4 — Expert (security & spec deep cuts)

**Q4.1 — A Secret is `base64`. Is that secure? What must you actually do?**

<details><summary>Model answer</summary>

⚠ **No — base64 is encoding, not encryption.** By default etcd stores Secrets base64-decoded-to-anyone. ★ You must: (1) enable **encryption at rest with a KMS v2 provider** so etcd holds ciphertext; (2) prefer an **external secrets manager** (Vault / cloud KMS) synced via **External Secrets Operator** or the CSI Secrets Store driver; (3) lock down **RBAC on `secrets`** — reading secrets = reading credentials = privileged access; (4) know that **anyone who can read etcd, or has broad secret RBAC, has every credential**. This is a standard fintech audit finding.
</details>

**Q4.2 — Explain Workload Identity and why it's better than a stored cloud credential.**

<details><summary>Model answer</summary>

A pod needs to call a cloud API. Old way: store a **long-lived static credential** in a Secret — it leaks, rarely rotates, is a standing liability. ★ **Workload Identity:** the cluster is an **OIDC issuer**; the pod's **ServiceAccount token is a short-lived, auto-rotated OIDC JWT**; cloud IAM **federates trust** to that issuer, so the pod **exchanges the token for cloud creds with no stored secret**. "It's OIDC token-exchange and federated trust — `iss`/`aud`/`exp`, JWKS signature verification — applied to **non-human identity**. Names: **EKS IRSA / Pod Identity**, **GKE Workload Identity**, **Entra Workload Identity**. This is the K8s topic an IAM engineer owns." ⚠ Don't describe it as "a nicer Secret" — the point is **there is no secret**.
</details>

**Q4.3 — PodSecurityPolicy is gone. What replaced it, and what does "restricted" enforce?**

<details><summary>Model answer</summary>

**PSP was removed in v1.25**, replaced by **Pod Security Admission (PSA)** enforcing the **Pod Security Standards**: **Privileged / Baseline / Restricted**. You set it per-namespace with a label (`pod-security.kubernetes.io/enforce: restricted`). ★ **Restricted** requires: **runAsNonRoot**, **no privilege escalation**, **drop ALL capabilities**, **seccomp RuntimeDefault**, **read-only root FS**, no host namespaces/hostPath. "Target `restricted` for app namespaces; keep `privileged` only for trusted system pods." For richer rules (image signing, no `:latest`) layer on **Kyverno/OPA-Gatekeeper** admission policies.
</details>

**Q4.4 — Default pod networking is wide open. How do you lock it down?**

<details><summary>Model answer</summary>

Apply a **default-deny NetworkPolicy** (ingress **and** egress) per namespace, then add **explicit allows** for required flows (e.g. `auth` → `db:5432`). Requires a **policy-capable CNI** (Calico/Cilium). ★ "This is **Zero-Trust micro-segmentation** at the network layer — the sibling of least-privilege RBAC. For east-west encryption, add a **service mesh** (Istio/Linkerd) for automatic **mTLS between pods**." ⚠ A NetworkPolicy with a plain CNI like Flannel does **nothing** — no enforcer.
</details>

**Q4.5 — What is admission control and why is it your best preventive control?**

<details><summary>Model answer</summary>

After authN + RBAC, a request hits **admission controllers** — the last gate that can **mutate** (e.g. inject a sidecar) or **reject** an object before it's persisted. ★ Policy engines (**OPA/Gatekeeper**, **Kyverno**) enforce org rules here: "no `:latest`", "images must be **signed** (verify Sigstore signature)", "every namespace must have a NetworkPolicy", "no privileged pods". "It's a **preventive** control — codified compliance that **blocks** bad config at the door instead of finding it in an audit." Ties directly to supply-chain security (SBOM, provenance, signatures).
</details>

**Q4.6 — Why can't Kubernetes just use pod IPs directly, and what's an EndpointSlice?**

<details><summary>Model answer</summary>

Pod IPs are **ephemeral** — every reschedule changes them — so clients need a stable indirection (the **Service**). ★ The **EndpointSlice** is the controller-maintained, **sharded** list of the Service's currently-healthy backend pod IPs/ports; kube-proxy turns it into forwarding rules. "It replaced the older monolithic **Endpoints** object, which didn't scale to thousands of endpoints and is now being deprecated. EndpointSlices also enable dual-stack (IPv4/IPv6)."
</details>

**Q4.7 — What does `--privileged` (Docker) / a privileged pod actually grant, and why is it a red flag?**

<details><summary>Model answer</summary>

It **removes the guardrails**: all Linux **capabilities**, disables the **seccomp** filter, and gives broad device access — effectively **root on the host** if the process escapes. ★ "A privileged container that mounts the host filesystem or the **Docker socket** is a one-step host takeover. In an audit it's a top finding — allowed only as logged, exceptional break-glass. Treat 'who can run privileged / mount the socket' like a **privileged-access grant** in the IAM model."
</details>

---

## E — Edge cases & gotchas (the stuff that pages you)

**QE.1 — Container exits with code 137. What is it and what do you check?**

<details><summary>Model answer</summary>

**137 = 128 + 9 (SIGKILL) = OOMKilled** — the container hit its **memory limit** (or node memory pressure). ★ Check: `kubectl describe pod` → `State: Terminated, Reason: OOMKilled`; then raise the memory **limit** *or* fix the leak. ⚠ Don't just bump the limit blindly — if it's a leak you're delaying the same crash. Related codes: **143** = 128+15 (SIGTERM, graceful stop), **139** = 128+11 (segfault), **1** = generic app error, **125** = docker run itself failed, **126** = command not executable, **127** = command not found.
</details>

**QE.2 — A pod is in CrashLoopBackOff. Walk your debugging.**

<details><summary>Model answer</summary>

1. `kubectl describe pod` → read **Events** and the **last exit code / reason**.
2. `kubectl logs <pod> --previous` → the **crashed** container's output (current logs are the *new* attempt and may be empty). ★ The `--previous` flag is the senior tell.
3. Check **probes** — an aggressive liveness probe on a slow app causes it. Temporarily remove/loosen it to test.
4. Check **exit code**: 137 → OOM; 1 → app error (bad config/missing env/unreachable dependency); 127 → bad command/entrypoint.
5. Check **config/secrets/dependencies** — can it reach the DB? Is a required env var/mount present?
"BackOff" = K8s deliberately waiting longer between restarts (up to ~5 min) — the crash is upstream of the backoff.
</details>

**QE.3 — Pod stuck in ImagePullBackOff. Causes?**

<details><summary>Model answer</summary>

The kubelet can't pull the image. Causes: **wrong image name/tag** (typo, tag doesn't exist), **private registry with no/invalid `imagePullSecret`**, **rate limiting** (Docker Hub anonymous pull limits), or **network/registry unreachable**. ★ `kubectl describe pod` Events shows the exact pull error. "Pinning by **digest** avoids the 'tag moved / tag missing' class; missing registry creds is the most common in enterprise." ErrImagePull is the first failure; ImagePullBackOff is K8s backing off retries.
</details>

**QE.4 — The SIGTERM / shell-form trap. What breaks and why?**

<details><summary>Model answer</summary>

If your Dockerfile uses **shell form** (`CMD myapp`), the app runs as a **child of `/bin/sh -c`**, so **PID 1 is `sh`** — and `sh` doesn't forward **SIGTERM**. On `docker stop` / pod termination, your app never gets the signal, ignores graceful shutdown, and is **SIGKILL**ed after the grace period (default 30s in K8s, 10s in Docker). ★ Fix: **exec form** (`CMD ["myapp"]`) so your app is PID 1 and receives SIGTERM; or use an init like `tini`. "In K8s this shows up as slow rolling deploys and dropped in-flight requests during rollout." ⚠ Also: PID 1 doesn't reap zombies — another reason for a real init or exec-form single process.
</details>

**QE.5 — initContainer fails and the pod has `restartPolicy: Never`. What happens?**

<details><summary>Model answer</summary>

The pod is stuck in **`Init:Error`** / **`Init:CrashLoopBackOff`** and the **main container never starts** — init containers must **all succeed in order** first. ★ With `restartPolicy: Never`, Kubernetes **won't retry** the failed container at all; with `Always`/`OnFailure` it retries with backoff. "Common cause: an init container waiting on a dependency (DB migration, config fetch) that isn't ready — so a slow/unreachable dependency wedges the whole pod at init."
</details>

**QE.6 — "My app can't resolve the database service name." DNS gotchas?**

<details><summary>Model answer</summary>

Check, in order: (1) is the **Service** name right and in the **same namespace**? cross-namespace needs the FQDN `db.other-ns.svc.cluster.local`. (2) Does the Service's **selector** actually match pod **labels**? a typo → **zero endpoints** → resolves but connection refused. (3) Is **CoreDNS** healthy (`kubectl get pods -n kube-system`)? (4) `ndots:5` in the pod's `resolv.conf` can make external lookups slow/odd. ★ "Resolves-but-refused usually means the Service has **no endpoints** — a label mismatch or all pods failing readiness, not a DNS problem." Debug with `kubectl get endpointslices` and `kubectl exec ... nslookup db`.
</details>

**QE.7 — You set a CPU limit and the app got *slower* even though usage looks low. Why?**

<details><summary>Model answer</summary>

**CPU throttling.** A CPU **limit** is enforced by the CFS scheduler in short quota windows (100ms). A bursty app can hit its quota **within a window** and be **throttled** even though average utilization looks low — causing latency spikes. ★ "Check `container_cpu_cfs_throttled_periods`. Common fix: raise or **remove** the CPU limit (keep the **request**), since CPU is compressible — many shops set CPU requests but **no CPU limits** to avoid throttling, while always setting **memory limits**." ⚠ Don't do the same for memory — unbounded memory → node OOM.
</details>

**QE.8 — A node goes NotReady. What happens to its pods, and how fast?**

<details><summary>Model answer</summary>

The node controller marks it **NotReady**; after a grace period (default **~40s** to notice + **5min** `tolerationSeconds` for the `not-ready`/`unreachable` taints) pods are **evicted/rescheduled** elsewhere. ★ "For **StatefulSet** pods this is deliberately cautious — K8s won't force-delete `db-0` automatically because two instances with the same identity/volume could corrupt data (split-brain). You may have to intervene." Faster failover needs tuned tolerations + PodDisruptionBudgets + anti-affinity so replacements land on healthy nodes.
</details>

---

## S — Scenarios (diagnose the ticket)

**QS.1 — "We deployed v2. Half the users get v1, half get v2, and it won't stop." What's happening?**

<details><summary>Model answer</summary>

A **rolling update in progress or wedged**: during `RollingUpdate` both ReplicaSets run and the Service load-balances across **both**, so users hit mixed versions — normal *briefly*, but stuck if new pods aren't passing **readiness** (so old ones can't be retired). ★ Check `kubectl rollout status deploy/x`; `kubectl get rs` (two ReplicaSets with replicas); describe the new pods for failing readiness/crashes. Fixes: fix the new version's health, or `kubectl rollout undo`. "For clean version cuts use a **blue-green** or **canary** (mesh/Argo Rollouts), not a bare rolling update." ⚠ Session-affinity assumptions break here — stateless design matters.
</details>

**QS.2 — "One namespace's pods can reach another namespace's database. Compliance says no." Fix?**

<details><summary>Model answer</summary>

Default K8s networking is **flat and open** — any pod reaches any pod cluster-wide. ★ Apply a **default-deny NetworkPolicy** (ingress+egress) in the DB's namespace, then an explicit allow only from the authorized app's pods (by label/namespace selector). Requires **Calico/Cilium**. "This is the **micro-segmentation** an auditor expects — PCI-style separation between cardholder-data workloads and everything else. Layer on **namespace + node isolation** (taints) and mesh **mTLS** for defense in depth." Verify with a test pod that the connection is now refused.
</details>

**QS.3 — "A pod was compromised via an app RCE. How bad is it, and what limited the blast radius?"**

<details><summary>Model answer</summary>

Assess what the pod *was allowed to do*. ★ Blast-radius limiters (name them as controls that should already be on): **runAsNonRoot** + **drop ALL caps** + **read-only root FS** + **seccomp** (attacker has few tools/syscalls); **NetworkPolicy** (can't pivot laterally); **its own least-privilege ServiceAccount + tight secret RBAC** (can't read other secrets or hit the API server); **no host mounts / not privileged / no Docker socket** (can't escape to the node); **Workload Identity short-lived tokens** (any stolen cloud cred expires fast). Detection: **Falco/eBPF** runtime alerts + **API server audit logs**. "The container shares the host kernel, so I'd also check for **escape** attempts and rotate anything the SA could touch. This is exactly a least-privilege + segmentation + short-lived-credential story — my IAM discipline applied to workloads."
</details>

**QS.4 — "Pods are Pending and nothing is scheduling." Diagnose.**

<details><summary>Model answer</summary>

`kubectl describe pod` → the **Events** name it. Usual causes: (1) **insufficient resources** — requests exceed any node's free CPU/mem (fix requests or add nodes / Cluster Autoscaler); (2) **taints** with no matching toleration; (3) **node affinity / nodeSelector** matches no node; (4) **unbound PVC** — no PV/StorageClass satisfies it; (5) **topology spread / anti-affinity** can't be satisfied. ★ "Pending is a **scheduling** problem — read the scheduler's Events, don't guess. If it's resources and you run Cluster Autoscaler/Karpenter, check *why* it isn't adding nodes (quota, instance types)."
</details>

**QS.5 — "Rolling update is stuck; old pods won't terminate." Why might K8s refuse?**

<details><summary>Model answer</summary>

A **PodDisruptionBudget (PDB)** is doing its job: if `minAvailable` would be violated, K8s **won't** evict/terminate more pods — good during voluntary disruptions, but a too-strict PDB (e.g. `minAvailable: 100%`) **deadlocks** rollouts and node drains. ★ Also check: new pods **failing readiness** (so old can't retire), `maxUnavailable: 0` with no spare capacity, or finalizers/terminationGracePeriod hanging. "Fix the PDB to leave headroom (`minAvailable: N-1`) and make sure the new version actually goes Ready."
</details>

---

## R — Rapid-fire (drill to reflex)

<details><summary>Expand for the rapid-fire set</summary>

- **Smallest K8s unit?** Pod. · **Smallest *scheduling* unit?** Also the Pod.
- **Only component that talks to etcd?** kube-apiserver.
- **What consensus does etcd use?** Raft.
- **Exit 137?** OOMKilled (SIGKILL). · **143?** SIGTERM. · **127?** command not found.
- **Restart a wedged container?** liveness probe. · **Stop routing traffic?** readiness probe.
- **Where does the writable layer go on `docker rm`?** Gone — use a volume.
- **Publish a port in Docker?** `-p host:container` (not `EXPOSE`).
- **PSP replacement?** Pod Security Admission (Restricted profile).
- **Default pod network?** Wide open — add NetworkPolicy (needs Calico/Cilium).
- **Secret encoding vs encryption?** base64 (encoding) — enable **KMS** for real encryption.
- **Static cloud creds in pods → replace with?** Workload Identity (OIDC federation).
- **Deployment = ?** stateless cattle. **StatefulSet = ?** stateful pets with stable identity + own PVC.
- **One pod per node?** DaemonSet. · **Run to completion?** Job. · **On a schedule?** CronJob.
- **kube-proxy programs?** iptables/IPVS (or eBPF via Cilium).
- **Service DNS name?** `svc.namespace.svc.cluster.local`.
- **Multi-stage build gives you?** small + no-toolchain (secure) images.
- **Distroless has no?** shell / package manager / extra binaries.
- **Docker's default storage driver?** overlay2.
- **`--previous` on `kubectl logs`?** the crashed container's last output.
- **dockershim removed in?** v1.24 (images still work via CRI/containerd).
- **cgroup v1 deprecated in?** v1.35 (move to cgroup v2).

</details>

---

## ↩ — Reverse questions (ask these; you'll sound senior)

<details><summary>Expand for sharp questions to ask an interviewer / architect</summary>

- "Do we enforce **Pod Security Admission `restricted`** and **default-deny NetworkPolicies**, or are they aspirational?"
- "How are cloud credentials handled — **Workload Identity / OIDC federation**, or static secrets in etcd? Is etcd **KMS-encrypted**?"
- "What's our **image supply chain** — are images **signed** and **verified at admission** (cosign/Kyverno), and do we pin by **digest**?"
- "How is **secret RBAC** scoped — who can `get secrets` in the payments namespace, and is it audited?"
- "What's our **runtime detection** story — Falco/eBPF? And are **API-server audit logs** shipped to the SIEM?"
- "How do we handle **multi-tenancy isolation** — namespaces only, or dedicated nodes/taints for regulated (PCI) workloads?"
- "What's the **upgrade cadence** — are we clear of EOL versions, and ready for **cgroup v2 / containerd 2.0**?"

</details>

---

## What you learned

- The **easy tier** is definitions you must never fumble (container vs VM, image vs container, Pod, Docker vs K8s).
- The **hard/expert tiers** are where seniority shows: **internals** (namespaces/cgroups), the **apply→running** flow through the API server, **RBAC**, **PSA**, **NetworkPolicy**, and **Workload Identity**.
- The **edge cases** are muscle memory: **137 = OOMKilled**, `logs --previous` for CrashLoopBackOff, exec-form for SIGTERM, initContainer wedges, DNS = check endpoints, CPU throttling.
- Wherever you can, **frame security answers as IAM** — least privilege, segmentation, short-lived federated identity, preventive policy. That framing is your differentiator.

## Next

- Re-read the [Docker reference](26-docker-complete-reference.md) §9 and [Kubernetes reference](27-kubernetes-complete-reference.md) §9 with these questions in mind — every answer maps to a section.
- **Do it for real:** convert [`../labs/01-keycloak-idp/`](../labs/01-keycloak-idp/) from Compose to K8s manifests, then run yourself through the S-tier scenarios against your own cluster.
- Connect to your IAM notes: [OAuth/OIDC reference](21-oauth2-complete-reference.md) (workload identity), [TLS/mTLS](06-tls-https-mtls.md) (mesh), [PCI-DSS & IAM](09-pci-dss-and-iam.md) (segmentation).

*Curated with Lefler ⚙️ and Janus ⭐ — read the question, answer aloud, then expand. Interviews are spoken.*
