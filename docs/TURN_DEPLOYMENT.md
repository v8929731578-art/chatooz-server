# TURN Server Deployment & Architecture Guide

## 1. Overview
WebRTC Peer-to-Peer media (Opus audio and VP8/H.264 video) flows directly between devices whenever STUN NAT hole punching succeeds. However, on mobile networks (e.g. Jio/Airtel/Vi 4G/5G) and enterprise firewalls with **Symmetric NAT**, direct P2P connections are blocked by carrier gateways. 

A dedicated **TURN (Traversal Using Relays around NAT)** server is required to relay encrypted SRTP media packets over UDP/TCP/TLS.

---

## 2. Server & Infrastructure Requirements

### Recommended Hardware Spec:
- **Cloud Provider:** DigitalOcean, AWS EC2, Hetzner, or Linode
- **Specs:** 2 vCPU, 2GB–4GB RAM, 1Gbps unmetered/high-bandwidth network
- **Static Public IP:** Mandatory (Must not be behind NAT)
- **Domain:** e.g., `turn.chatooz.com` (with valid SSL/TLS certificate for TURNS)

### Firewall & Port Configuration:

| Protocol | Port Range | Purpose |
| :--- | :--- | :--- |
| **UDP / TCP** | `3478` | Standard STUN / TURN listening port |
| **TCP / TLS** | `5349` | Secure TLS TURN listening port (TURNS) |
| **UDP** | `49152–65535` | Relay dynamic port range for media packet allocation |

---

## 3. coturn Configuration Guide (`/etc/turnserver.conf`)

```ini
# Listening Ports
listening-port=3478
tls-listening-port=5349

# External Public IP & Realm
realm=turn.chatooz.com
server-name=chatooz-turn

# Secure Ephemeral Authentication (HMAC-SHA1 time-limited tokens)
use-auth-secret
static-auth-secret=CHATOOZ_COTURN_SECRET_KEY_REPLACE_ME

# Dynamic Port Range for Relayed Media
min-port=49152
max-port=65535

# Security & Anti-Abuse
no-tcp-relay
no-multicast-peers
fingerprint
lt-cred-mech

# TLS Certificates (Certbot Let's Encrypt)
cert=/etc/letsencrypt/live/turn.chatooz.com/fullchain.pem
pkey=/etc/letsencrypt/live/turn.chatooz.com/privkey.pem
```

---

## 4. Ephemeral Credential Mechanism
1. Client requests short-lived credentials from `POST /turn-credentials` on Chatooz API.
2. Server generates timestamped username `timestamp:userId` and HMAC-SHA1 signature using `static-auth-secret`.
3. Client uses the credentials for the duration of the call (TTL 24 hours).
4. No persistent passwords or static secrets are bundled in the Android APK.

---

## 5. Current Deployment Status
- **TURN Infrastructure Status:** `NOT DEPLOYED` (Pending VPS provisioning)
- **STUN Infrastructure Status:** `DEPLOYED & CONFIGURED` (Google Public STUN: `stun.l.google.com:19302`)
