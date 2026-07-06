# 02 · Network Security

> Every identity you protect travels the wire — network security is the terrain that IAM decisions ride on, and in fintech a single unencrypted session or spoofed route can turn a valid credential into a breach. Master the network and you understand where trust actually lives and where it leaks.

**Agents to use:** Ask **Mimir** to explain concepts and drill you on the "why." Ask **Lefler** to build and run the hands-on labs. Ask **Heimdall** for the defensive and detection angle (what to log, alert on, and harden). Ask **Loki** only for offensive technique in authorized, self-contained labs — never against systems you don't own.

## Core concepts (learn in this order)

### 1. The models: OSI & TCP/IP (recap)
- The 7-layer OSI model and the 4-layer TCP/IP model, and how real protocols map onto them.
- Encapsulation and decapsulation: how a packet gains and sheds headers layer by layer.
- Why "which layer?" is the first question in any incident — attacks and controls both live at specific layers (L2 ARP, L3 IP, L4 TCP/UDP, L7 HTTP).
- Key protocols to know cold: Ethernet, ARP, IP, ICMP, TCP (handshake, flags, sequence numbers), UDP, DNS, HTTP/HTTPS, DHCP.
- Ports, sockets, and the difference between a connection and a datagram.

### 2. Addressing, routing & switching fundamentals
- IPv4 vs IPv6, subnetting and CIDR, private vs public address space (RFC 1918).
- How switches learn MAC addresses; how routers forward between subnets.
- Default gateways, routing tables, and why a wrong route is a security event.
- NAT and PAT: what they hide, and why they are not a security control on their own.

### 3. Firewalls & their types
- Packet-filtering (stateless) vs stateful inspection — tracking connection state and why it matters.
- Application-layer / proxy firewalls and Next-Generation Firewalls (NGFW): deep packet inspection, app awareness, user-ID integration.
- Web Application Firewalls (WAF) vs network firewalls — different layers, different jobs.
- Rule design: default-deny, least privilege, explicit allow-lists, and rule ordering.
- Egress filtering — often ignored, critical for stopping data exfiltration and C2 callbacks.

### 4. Network segmentation, VLANs & the DMZ
- Why a flat network is a breach amplifier; segmentation as blast-radius containment.
- VLANs: logical segmentation, 802.1Q tagging, and VLAN hopping risks.
- The DMZ: isolating internet-facing services from the internal network.
- Micro-segmentation and the shift toward Zero Trust ("never trust, always verify").
- East-west vs north-south traffic — why internal traffic needs controls too.

### 5. IDS / IPS — detection & prevention
- IDS (detect and alert) vs IPS (detect and block inline).
- Signature-based vs anomaly/behavior-based detection, and the tradeoffs (false positives vs unknown threats).
- Network vs host-based sensors (NIDS/HIDS).
- Practical tooling: Snort, Suricata, Zeek — where they sit and what they output.
- Tuning: the real work is reducing noise so real alerts surface.

### 6. VPNs & IPsec
- Why VPNs exist: confidentiality and integrity over untrusted networks.
- IPsec: Authentication Header (AH) vs Encapsulating Security Payload (ESP), transport vs tunnel mode.
- IKE / IKEv2 key exchange and Security Associations.
- SSL/TLS VPNs (e.g. OpenVPN, WireGuard) vs IPsec — when each fits.
- Split tunneling risks and the move toward ZTNA (Zero Trust Network Access) replacing broad VPN access.

### 7. TLS/SSL — protecting data in transit
- The TLS handshake: cipher negotiation, key exchange, certificate validation.
- Certificates, Certificate Authorities, chains of trust, and revocation (CRL/OCSP).
- Perfect Forward Secrecy, cipher suite selection, and why old versions (SSLv3, TLS 1.0/1.1) must be disabled.
- Mutual TLS (mTLS) — client and server both authenticate; heavily used in service-to-service fintech traffic.
- Common failures: expired certs, weak ciphers, missing hostname validation, mixed content.

### 8. DNS & DNS security
- How recursive and authoritative resolution works; caching and TTLs.
- DNS as an attack surface: cache poisoning, subdomain takeover, DNS tunneling for exfiltration.
- DNSSEC: origin authentication and integrity for DNS records.
- Encrypted DNS: DoH (DNS over HTTPS) and DoT (DNS over TLS) — privacy gains and monitoring tradeoffs.
- DNS as a detection goldmine — malicious domains and beaconing show up in DNS logs first.

### 9. Network attacks (know the offense to build the defense)
- **MITM (Man-in-the-Middle):** intercepting and possibly altering traffic between two parties.
- **ARP spoofing/poisoning:** forging L2 mappings to redirect LAN traffic — the classic MITM enabler.
- **DNS poisoning/spoofing:** feeding false resolution answers to redirect victims.
- **DDoS:** volumetric, protocol, and application-layer floods; amplification/reflection (DNS, NTP).
- **Others to recognize:** SYN floods, session hijacking, rogue DHCP, VLAN hopping, port scanning as recon.
- For each: the indicator you'd detect, and the control that prevents or contains it.

### 10. Packet analysis with Wireshark
- Reading a capture: filters (display vs capture), following streams, and spotting anomalies.
- Watching a real TCP handshake, a TLS negotiation, and a DNS query end-to-end.
- Identifying suspicious patterns: retransmissions, unexpected resets, cleartext credentials, odd beaconing intervals.
- Capturing safely and legally — only on networks and traffic you're authorized to inspect.

### 11. Proxies, NAC & access enforcement
- Forward proxies (outbound control, content filtering) vs reverse proxies (protecting/serving backend services).
- TLS interception at proxies — powerful for inspection, sensitive for privacy and compliance.
- Network Access Control (NAC): 802.1X, posture checks, and quarantining non-compliant devices before they reach the network.
- How NAC ties device identity to network access — the network-layer cousin of IAM.

### 12. Secure network architecture
- Defense-in-depth: layered controls so no single failure is catastrophic.
- Zero Trust architecture (per NIST SP 800-207): identity- and context-aware access, no implicit trust from network location.
- Reference patterns: tiered networks, bastion/jump hosts, service meshes with mTLS.
- Logging, monitoring, and network flow visibility (NetFlow/IPFIX) as first-class design requirements.

### 13. Wireless security
- Wi-Fi standards and the evolution of encryption: WEP (broken) → WPA/WPA2 → WPA3.
- WPA2-Personal (PSK) vs WPA2/WPA3-Enterprise (802.1X + RADIUS) — enterprise ties Wi-Fi to identity.
- Attacks: evil twin / rogue AP, deauth, handshake capture and offline cracking.
- Guest network isolation and why corporate and guest SSIDs must be segmented.

## Reading list

**Foundational books**
- *Computer Networking: A Top-Down Approach* — Kurose & Ross. The clearest on-ramp to how networks actually work.
- *TCP/IP Illustrated, Volume 1: The Protocols* — W. Richard Stevens. The deep reference for what's really on the wire.
- *Network Security Essentials* — William Stallings. Solid coverage of firewalls, IPsec, and TLS.
- *Practical Packet Analysis* — Chris Sanders. The best hands-on Wireshark book for beginners.
- *The Practice of Network Security Monitoring* — Richard Bejtlich. Defensive mindset, detection over prevention.

**NIST publications (free, authoritative)**
- NIST SP 800-41 Rev. 1 — *Guidelines on Firewalls and Firewall Policy*.
- NIST SP 800-207 — *Zero Trust Architecture* (essential given fintech's direction).
- NIST SP 800-77 Rev. 1 — *Guide to IPsec VPNs*.
- NIST SP 800-52 Rev. 2 — *Guidelines for TLS Implementations*.
- NIST SP 800-125 — *Security for Full Virtualization Technologies* (context for segmented/virtual networks).

**RFCs (read the source)**
- RFC 8446 — TLS 1.3.
- RFC 4301 — Security Architecture for IP (IPsec).
- RFC 1918 — Private address allocation.
- RFC 4033 — DNS Security Introduction and Requirements (DNSSEC).
- RFC 793 / RFC 9293 — TCP (original and the modern consolidated spec).

**Vendor & practitioner docs**
- Cloudflare Learning Center — excellent, free plain-English explainers on DDoS, DNS, TLS, and VPNs.
- Wireshark User's Guide and the official sample capture wiki.
- Cisco and Palo Alto Networks documentation for real-world firewall/NGFW and segmentation design.
- OWASP — for the L7/WAF and application-boundary perspective.

**Free courses & practice**
- Professor Messer's Security+ (SY0-701) video series — free, and the network security modules are strong.
- TryHackMe — "Network Fundamentals" and "Network Security" paths (guided, beginner-friendly).
- Cisco Networking Academy — free "Networking Basics" and "Network Support and Security."

## Labs (ask Lefler to set these up)

Labs live in `labs/NN-name/`. All are free, local, and self-contained — no scanning or attacking anything you don't own.

| # | Lab | You'll learn |
|---|-----|-------------|
| 1 | `labs/01-wireshark-handshake/` | Capture and dissect a TCP 3-way handshake, a DNS query, and a TLS negotiation on your own loopback/host traffic. |
| 2 | `labs/02-nmap-discovery/` | Map hosts, open ports, and services on a local Docker network you built — recon from the defender's seat. |
| 3 | `labs/03-pfsense-firewall/` | Stand up pfSense in a VM, write default-deny rules with explicit allow-lists, and test egress filtering. |
| 4 | `labs/04-vlan-segmentation/` | Build segmented networks in GNS3 (or Docker networks), route between them, and prove isolation holds. |
| 5 | `labs/05-suricata-ids/` | Deploy Suricata, replay a sample PCAP, and tune signatures to catch a simulated attack while cutting noise. |
| 6 | `labs/06-arp-spoof-detect/` | In an isolated lab VM, observe an ARP-spoofing MITM and, more importantly, detect it in the capture. |
| 7 | `labs/07-tls-inspection/` | Inspect a TLS handshake, examine the cert chain, and deliberately break/fix cipher and validation settings. |
| 8 | `labs/08-wireguard-vpn/` | Configure a WireGuard tunnel between two hosts and verify confidentiality by capturing before and inside the tunnel. |
| 9 | `labs/09-dns-security/` | Explore DNS resolution, simulate cache poisoning in an isolated lab, and see how DNSSEC/logging defends and detects. |

## How this connects to IAM / fintech

At FinCo, the identities you manage don't exist in a vacuum — every authentication, token exchange, and privileged session crosses a network you must trust or verify. **Zero Trust** is where IAM and network security converge: access is granted by identity and context, not by where a packet came from, so understanding segmentation, mTLS, and NAC makes you a far stronger IAM engineer. **mTLS and TLS** are how service identities authenticate to each other in a fintech microservices stack. **802.1X/NAC and RADIUS** tie device and user identity directly to network admission. And when an IAM incident happens — a stolen token, a suspicious login — the answer often lives in network telemetry: DNS logs, flow data, and packet captures. Learn this domain well and you won't just protect credentials; you'll understand the entire path a trusted identity travels, and every place an attacker might try to hijack it.
