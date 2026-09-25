# WebRTC Migration Plan: Chatooz Real-Time Communication

## 1. Executive Summary
This document outlines the phased migration path from Chatooz's current custom HTTP long-polling and OkHttp WebSocket frame relay system to a production-grade WebRTC and FCM-backed peer-to-peer/SFU communication system.

---

## 2. Current Architecture vs. Target Architecture

| Component | Current System (Phase 1 Baseline) | Target Production System (Phase 4 Final) |
| :--- | :--- | :--- |
| **Media Transport** | Custom WebSocket binary relay (raw 16kHz PCM & JPEG bytes) | Native WebRTC PeerConnection (Opus Audio + VP8/H.264 Video via SRTP) |
| **Signaling** | HTTP polling on `/call/signal` | Dedicated persistent WebSocket signaling + fallback HTTP |
| **NAT Traversal** | None (All media relayed through Python server port 8080) | ICE framework with standard STUN (`stun.l.google.com:19302`) + TURN server |
| **Messaging** | 300ms incremental HTTP polling (`/messages/recent`) | Persistent WebSocket connection with incremental sync |
| **Notifications** | In-app notification channel (Foreground only) | Firebase Cloud Messaging (FCM) high-priority data payloads with full-screen intent |
| **Server** | Single Python `aiohttp` script handling DB + WebSocket media | Modularized backend: Fast API / Go WebRTC Signaling + coturn TURN server |

---

## 3. Migration Roadmap

### Phase 1: Architecture & Foundation (Current Phase)
- Establish WebRTC abstraction layer (`com.chatooz.app.webrtc`).
- Implement `IceServerProvider` supporting STUN configuration without fake TURN credentials.
- Implement structured `WebRtcSignalingClient` data models and serialization.
- Establish FCM setup documentation and diagnostic models.
- **Rule:** Keep existing `CallManager` and `CallMediaClient` intact and working. Do not break current APK builds.

### Phase 2: Dual Signaling & Media Abstraction
- Deploy WebSocket-based signaling server endpoint (`/ws/signaling`).
- Implement SDP Offer/Answer and ICE Candidate exchange between clients.
- Add client-side feature flag: `MEDIA_ENGINE_TYPE = WEBRTC | LEGACY_WS`.

### Phase 3: Hardware Codecs, STUN/TURN & FCM Integration
- Register Firebase Project and configure `google-services.json`.
- Configure coturn (or cloud TURN provider) for symmetric NAT / 4G / 5G traversal.
- Integrate Hardware AEC (Acoustic Echo Cancellation) and AGC (Automatic Gain Control) via WebRTC.

### Phase 4: Production Cutover & Legacy Deprecation
- Verify P2P connection quality across cross-carrier 4G/5G/Wi-Fi.
- Deprecate custom JPEG/PCM WebSocket endpoints.
- Switch default call engine to WebRTC.
