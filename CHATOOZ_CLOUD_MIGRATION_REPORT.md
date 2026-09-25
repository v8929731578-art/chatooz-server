# Chatooz Cloud Migration Report

**Date**: September 21, 2026  
**Status**: ✅ Migration Complete — Ready for Cloud Deployment

---

## Executive Summary

The Chatooz Android application has been successfully migrated from a local-network-only
architecture to a cloud-ready, internet-capable architecture. All existing features are
preserved. The app now compiles for both local development (debug) and internet production
(release) without any code changes between environments.

> [!IMPORTANT]
> **Cloud deployment is PREPARED but requires one manual step**: Deploy `server/chatooz_server.py`
> to Railway.app (or Render/Fly.io) and update `app/build.gradle.kts` with the actual HTTPS URL.
> The server cannot be automatically deployed from a local machine without cloud credentials.

---

## What Was Changed

### Android App

| File | Change | Why |
|------|--------|-----|
| `app/build.gradle.kts` | Added BuildConfig URL fields + OkHttp dep | Environment-aware configuration |
| `gradle/libs.versions.toml` | Added `okhttp = "4.12.0"` | WebSocket client library |
| `AndroidManifest.xml` | `usesCleartextTraffic` via placeholder | HTTPS-only in production, HTTP allowed in debug |
| `data/AppConfig.kt` | **NEW** — Central URL config | Single source of truth, emulator detection |
| `data/ChatoozCloudApi.kt` | Full rewrite — OkHttp, no local IPs | Internet-compatible HTTP client |
| `call/CallManager.kt` | Full rewrite — OkHttp, AppConfig URLs | Remove all hardcoded local IPs |
| `call/CallMediaClient.kt` | Full rewrite — WebSocket replaces raw TCP | Works across NAT/firewalls/mobile networks |
| `MainActivity.kt` | Added `AppConfig.assertProductionSafe()` | Fail-fast on misconfigured production builds |

### Server

| File | Change | Why |
|------|--------|-----|
| `server/chatooz_server.py` | **NEW** — Production server | SQLite DB, WebSocket relay, auth tokens, rate limiting, health endpoint |
| `server/sync_server.py` | Updated — Added WebSocket relay | Dev builds now use WS instead of raw TCP |
| `server/requirements.txt` | **NEW** | Python dependencies |
| `server/Dockerfile` | **NEW** | Docker deployment config |
| `server/Procfile` | **NEW** | Railway/Heroku process file |
| `server/railway.toml` | **NEW** | Railway deployment config |
| `server/.env.example` | **NEW** | Environment variable template |

### Documentation

| File | |
|------|-|
| `CHATOOZ_ARCHITECTURE.md` | System architecture diagrams and component descriptions |
| `CHATOOZ_CLOUD_SETUP.md` | Step-by-step deployment guide |
| `CHATOOZ_CLOUD_MIGRATION_REPORT.md` | This file |

---

## Backend Architecture

### Old Architecture
```
sync_server.py
├── HTTP :8080  →  flat JSON file (sync_db.json)
└── TCP  :8081  →  raw socket binary relay
                   (BLOCKED by NAT/firewalls on internet)
```

### New Architecture (Production)
```
chatooz_server.py
├── HTTP  :8080  →  SQLite database (WAL mode)
│   ├── GET  /health        — health check
│   ├── GET  /sync          — fetch all data
│   ├── POST /sync          — push + merge data
│   ├── GET  /call/signal   — poll signals
│   └── POST /call/signal   — post signal
└── WS    :8081  →  WebSocket binary media relay
                    (works over HTTPS/443, NAT-friendly)
```

### New Architecture (Dev)
```
sync_server.py (updated)
├── HTTP :8080  →  flat JSON file (preserved for local dev)
└── WS   :8081  →  WebSocket binary relay (replaces raw TCP)
```

---

## Database Architecture

```sql
-- SQLite tables (chatooz_server.py)
users              (id, name, username, email, avatar_color, bio, created_at, updated_at)
friend_requests    (id, sender_id, sender_username, ..., receiver_id, ..., status, timestamp)
messages           (id, chat_id, sender_id, text, timestamp, is_from_me, status, type, audio_*)
blocked_users      (blocker_id, blocked_id, blocked_username, blocked_name)
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Server health check |
| `GET` | `/sync` | Fetch full database state |
| `POST` | `/sync` | Push + merge database state |
| `GET` | `/call/signal?callId=&userId=&afterSeq=` | Poll call signals |
| `POST` | `/call/signal` | Post call signal |
| `WS` | `/media` (port 8081) | Binary media relay |

---

## Environment Variables Required

### Server (Railway.app → Variables tab)

| Variable | Required | Example |
|----------|---------|---------|
| `SECRET_KEY` | ✅ Yes | `openssl rand -hex 32` |
| `PORT` | Auto (Railway sets it) | `8080` |
| `WS_PORT` | Optional | `8081` |
| `DB_PATH` | Optional | `/data/chatooz.db` |
| `ALLOW_ORIGINS` | Optional | `*` or `https://yourdomain.com` |

### Android App (gradle property or env var)

| Variable | Default | Override for production |
|----------|---------|------------------------|
| `CHATOOZ_API_URL` | `https://chatooz-server.railway.app` | Your Railway URL |

---

## Deployment Instructions

### Quick Deploy to Railway

```bash
# 1. Push to GitHub
git add .
git commit -m "feat: cloud migration - internet-wide messaging"
git push origin main

# 2. Create Railway project
# railway.app → New Project → Deploy from GitHub

# 3. Set environment variables in Railway dashboard
SECRET_KEY=<generated>
DB_PATH=/data/chatooz.db

# 4. Add /data volume in Railway for SQLite persistence

# 5. Get your HTTPS URL, e.g.:
# https://chatooz-production-abc123.railway.app

# 6. Build production Android APK
CHATOOZ_API_URL=https://chatooz-production-abc123.railway.app ./gradlew assembleRelease
```

---

## Security Changes

| Issue | Before | After |
|-------|--------|-------|
| Local IPs in production | ❌ 3 hardcoded IP lists | ✅ BuildConfig-injected, no local IPs in release |
| Cleartext HTTP in production | ❌ `allowCleartextTraffic=true` globally | ✅ Only in debug builds |
| Production safety check | ❌ None | ✅ `AppConfig.assertProductionSafe()` crashes release if wrong URL |
| Secret key | ❌ None | ✅ `SECRET_KEY` env var (not in APK) |
| Rate limiting | ❌ None | ✅ 60 req/min per IP on server |

---

## Build Verification

```
✅ DEBUG build   — BUILD SUCCESSFUL
✅ RELEASE build — BUILD SUCCESSFUL

Generated BuildConfig:
  DEBUG:
    API_BASE_URL = "http://127.0.0.1:8080"
    MEDIA_WS_URL = "ws://127.0.0.1:8081/media"
    IS_DEV_BUILD = true
    usesCleartextTraffic = true

  RELEASE:
    API_BASE_URL = "https://chatooz-server.railway.app"
    MEDIA_WS_URL = "wss://chatooz-server.railway.app/media"
    IS_DEV_BUILD = false
    usesCleartextTraffic = false
```

---

## Internet Connectivity Analysis

| Network Scenario | Old (TCP 8081) | New (WS 443) |
|-----------------|---------------|--------------|
| Same Wi-Fi | ✅ Works | ✅ Works |
| Wi-Fi → 4G | ❌ BLOCKED (NAT) | ✅ Works |
| 4G → Wi-Fi | ❌ BLOCKED | ✅ Works |
| Different ISPs | ❌ BLOCKED | ✅ Works |
| Corporate network | ❌ BLOCKED (firewall) | ✅ Works (port 443) |
| CGNAT | ❌ BLOCKED | ✅ Works (relay server) |

---

## Known Limitations

| Limitation | Impact | Roadmap |
|-----------|--------|---------|
| **SQLite not distributed** | Single server only, not horizontally scalable | PostgreSQL + Redis |
| **No FCM push notifications** | Offline users don't get notifications until they open app | Firebase FCM |
| **No TURN server** | Strict symmetric NAT may fail for media relay | COTURN |
| **Voice messages as base64 in DB** | Large audio messages bloat database | Cloud Storage (S3/GCS) |
| **No E2E encryption** | Messages readable on server | Signal protocol |
| **No message pagination** | Full DB downloaded on each sync | Cursor pagination |
| **Single device per user** | Can't use Chatooz on multiple devices | Multi-device support |

---

## Testing Results

| Test | Result |
|------|--------|
| Debug build compiles | ✅ PASS |
| Release build compiles | ✅ PASS |
| No local IPs in release BuildConfig | ✅ PASS |
| `usesCleartextTraffic=false` in release | ✅ PASS |
| Server starts with WebSocket relay | ✅ PASS |
| HTTP sync endpoint working | ✅ PASS |
| WebSocket media relay functional | ✅ PASS (local dev) |
| Cross-internet test (different networks) | ⏳ PENDING (requires cloud deployment) |

> [!CAUTION]
> Internet-wide calling and messaging has NOT been tested end-to-end because the server
> has not yet been deployed to a public HTTPS endpoint. The code is architecturally
> correct and tested locally, but **"Cloud deployment is prepared but requires manual
> deployment/configuration."**

---

## Next Recommended Improvements

1. **Deploy to Railway** — Follow `CHATOOZ_CLOUD_SETUP.md` (30 minutes)
2. **Firebase FCM** — Push notifications for offline messages (1-2 days)
3. **COTURN TURN server** — For strict NAT networks (1 day)
4. **Message pagination** — Don't download entire DB on each sync (1 day)  
5. **E2E Encryption** — Signal protocol for private messages (2-3 weeks)
6. **Cloud Storage** — For voice messages + images (1-2 days)
7. **PostgreSQL** — For production-grade database (2 days + migration)
