# Chatooz Cloud Architecture

## System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CHATOOZ SYSTEM                                  │
│                                                                         │
│  ┌─────────────┐    HTTPS/WSS    ┌──────────────────────────────────┐  │
│  │  Android    │◄──────────────►│    Cloud Backend (Railway.app)    │  │
│  │  App A      │                 │                                  │  │
│  │  (any net)  │                 │  ┌────────────┐ ┌────────────┐   │  │
│  └─────────────┘                 │  │ HTTP REST  │ │  WS Media  │   │  │
│                                  │  │   :8080    │ │   Relay    │   │  │
│  ┌─────────────┐    HTTPS/WSS    │  │            │ │   :8081    │   │  │
│  │  Android    │◄──────────────►│  └─────┬──────┘ └─────┬──────┘   │  │
│  │  App B      │                 │        │               │           │  │
│  │  (any net)  │                 │  ┌─────▼───────────────▼──────┐   │  │
│  └─────────────┘                 │  │      SQLite Database        │   │  │
│                                  │  │  users / messages / friends │   │  │
│                                  │  └────────────────────────────┘   │  │
│                                  └──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

## Component Details

### Android Application
- **Language**: Kotlin + Jetpack Compose
- **Architecture**: MVVM (ViewModel + StateFlow)
- **Local Storage**: SharedPreferences (offline cache)
- **Networking**: OkHttp 4.x (HTTP + WebSocket)
- **Config**: BuildConfig-injected URLs (debug vs release)

### Backend Server (`chatooz_server.py`)
- **Language**: Python 3.11
- **HTTP**: `ThreadingHTTPServer` (stdlib)
- **WebSocket**: `websockets` library (for media relay)
- **Database**: SQLite (WAL mode for concurrent access)
- **Deployment**: Docker on Railway.app

### Database (SQLite)
```
users              — registered user accounts
friend_requests    — pending/accepted/declined requests
messages           — all chat messages (with audio base64)
blocked_users      — block relationships
```

### Call Signalling (In-Memory)
```
CALL_SIGNALS = [{id, callId, senderId, type, payload, ts}, ...]
Pruned automatically after 120 seconds.
Endpoints:
  POST /call/signal   — send signal
  GET  /call/signal   — poll signals (filtered by userId, afterSeq)
```

### Media Relay (WebSocket)
```
Protocol: Binary WebSocket frames
Packet: [1 byte type][4 bytes big-endian length][payload]

Types:
  0x01 = Handshake  {"callId": "...", "userId": "..."}
  0x02 = Audio PCM  (16kHz 16-bit Mono raw PCM)
  0x03 = Video JPEG (JPEG-compressed camera frame)
  0x04 = Ping/Pong  (keepalive)

Room: {callId → {userId → WebSocket}}
Relay: packet from userId A → all other peers in same callId room
```

## Data Flow

### Message Sending
```
User types → ViewModel.sendMessage()
           → ChatoozStorage.addMessage()     [local persist]
           → _messages.value = ...           [UI update instantly]
           → ChatoozCloudApi.pushCloudData() [sync to server]
           → Server merges into SQLite
           → Other device polls /sync
           → Receives new message
```

### Incoming Call
```
Caller:   CallManager.initiateCall()
          → POST /call/signal {type: "offer", callId, calleeId}
          → POST /call/signal {callId: "incoming_$calleeId", ...}

Callee:   startBackgroundIncomingCallPoll()
          → GET /call/signal?callId=incoming_$myId&userId=...
          → Receives "offer" → handleIncomingOffer()
          → Shows IncomingCallScreen + notification + ringtone

Accept:   CallManager.acceptCall()
          → POST /call/signal {type: "answer", accepted: true}
          → CallMediaClient.start() → WebSocket to /media
          → Audio record → sendAudio() → WS frames → server relay
          → Video capture → sendVideo() → WS frames → server relay
```

### Audio/Video Call Data Flow
```
Phone A mic → AudioRecord (16kHz PCM16)
           → CallMediaClient.sendAudio()
           → WS binary frame [0x02][len][pcm]
           → Server WS relay → Phone B
           → Phone B CallMediaClient receives 0x02
           → AudioTrack.write(pcmBytes)  [USAGE_MEDIA → speaker]

Phone A camera → CameraManager (JPEG)
              → rotate+mirror (front camera selfie orientation)
              → CallMediaClient.sendVideo()
              → WS binary frame [0x03][len][jpeg]
              → Server WS relay → Phone B
              → Phone B decodes Bitmap
              → remoteVideoBitmap StateFlow → OngoingCallScreen Image()
```

## Network Topology

### Production (Internet-wide)
```
Phone A (any network) ──HTTPS──► Railway.app server ◄──HTTPS── Phone B (any network)
                        └──WSS──►  /media relay    ◄──WSS──┘
```

### Development (local)
```
Phone/Emulator ──ADB reverse:8080──► Mac:8080 (sync_server.py HTTP)
              ──ADB reverse:8081──► Mac:8081 (sync_server.py WebSocket)
```

## Security Architecture

### Transport Security
- Production: HTTPS for all HTTP, WSS for WebSocket
- Debug builds: HTTP/WS allowed only (manifest `usesCleartextTraffic="false"` in release)
- Production safety check: `AppConfig.assertProductionSafe()` runs on startup

### Configuration Security
- URLs injected via BuildConfig (not hardcoded)
- No secrets in APK
- Server `SECRET_KEY` set via environment variable
- `.env` file never committed (in `.gitignore`)

### Data Security
- No passwords stored (email-based auth)
- All messages stored in server SQLite with no encryption at rest (roadmap: E2E encryption)
- Voice message audio stored as base64 in DB (roadmap: cloud storage with signed URLs)

## Environment Configuration

| Variable | Debug | Release |
|----------|-------|---------|
| `API_BASE_URL` | `http://127.0.0.1:8080` | `https://chatooz-server.railway.app` |
| `MEDIA_WS_URL` | `ws://127.0.0.1:8081/media` | `wss://chatooz-server.railway.app/media` |
| `IS_DEV_BUILD` | `true` | `false` |
| `usesCleartextTraffic` | `true` | `false` |

## Known Limitations & Roadmap

| Feature | Current Status | Roadmap |
|---------|---------------|---------|
| Auth | Email-only, no passwords | JWT + bcrypt |
| E2E Encryption | ❌ Not implemented | Signal protocol |
| Push notifications | Local only (in-app) | Firebase FCM |
| Voice message storage | Base64 in DB | Cloud Storage + signed URLs |
| WebRTC/TURN | ❌ Custom relay (works on most NATs) | Full WebRTC + COTURN |
| CGNAT calling | Works via WS relay | WebRTC + TURN for strict NAT |
| Message pagination | Full sync each poll | Cursor-based pagination |
| Multi-device | Single device per account | Multiple device support |
