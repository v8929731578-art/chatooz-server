# WebRTC Architecture & Foundation: Chatooz

## 1. Overview
The `com.chatooz.app.webrtc` package defines the contract and lifecycle management for native WebRTC media and signaling sessions.

## 2. Package Structure

- `com.chatooz.app.webrtc.model`:
  - `WebRtcConfig`: Central configuration specifying ICE servers, audio/video constraints, and logging levels across DEV, STAGING, and PRODUCTION environments.
  - `IceServerConfig`: Model representing STUN / TURN servers with transport protocol and credential support.
  - `SignalingMessage`: Strongly typed signaling messages (`offer`, `answer`, `ice_candidate`, `renegotiate`, `busy`, `reject`, `end`).
  - `WebRtcDiagnostics`: Runtime diagnostics model exposing PeerConnection state, ICE state, candidate pairs, bitrate, and packet loss.

- `com.chatooz.app.webrtc.ice`:
  - `IceServerProvider`: Central provider for resolving STUN and TURN server configurations securely based on environment.

- `com.chatooz.app.webrtc.signaling`:
  - `WebRtcSignalingClient`: Interface for asynchronous bidirectional WebRTC signaling exchange.

- `com.chatooz.app.webrtc.session`:
  - `WebRtcSession`: Interface defining session lifecycle (`startLocalMedia`, `createOffer`, `handleRemoteOffer`, `handleRemoteAnswer`, `addIceCandidate`, `close`).

- `com.chatooz.app.webrtc.realtime`:
  - `RealtimeConnection`: Persistent connection abstraction providing reconnection, heartbeat, and real-time event streaming.
