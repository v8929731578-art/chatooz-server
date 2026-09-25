# STUN / TURN Setup & Configuration Guide

## 1. Overview
Direct Peer-to-Peer WebRTC media flows work over open networks and simple NATs using STUN. However, mobile carrier networks (4G/5G) and symmetric corporate firewalls block direct UDP hole punching. A TURN (Traversal Using Relays around NAT) server is required to relay encrypted media packets.

## 2. Public STUN Configuration
Chatooz uses Google's public STUN servers for NAT discovery:
- `stun:stun.l.google.com:19302`
- `stun:stun1.l.google.com:19302`

## 3. Production TURN Options

### Option A: Self-Hosted coturn Server
1. Provision an Ubuntu VM with a public static IPv4 address.
2. Install coturn: `sudo apt-get install coturn`.
3. Configure `/etc/turnserver.conf`:
   ```ini
   listening-port=3478
   tls-listening-port=5349
   realm=turn.chatooz.com
   use-auth-secret
   static-auth-secret=<GENERATED_SECRET>
   ```
4. Client dynamically requests ephemeral HMAC-SHA1 credentials from `/turn-credentials` endpoint.

### Option B: Cloud TURN Services (e.g., Metered / Twilio / Cloudflare Calls)
- Secure API key configured on server.
- Server returns short-lived time-based TURN credentials to client on call initialization.
- Never store raw credentials in client source code.
