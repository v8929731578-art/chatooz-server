# Chatooz Cloud Setup Guide

## Overview

Chatooz is now structured for internet-wide deployment. This guide walks through deploying
the backend to Railway.app (free HTTPS hosting) and configuring the Android app.

---

## 1. Required Services

| Service | Purpose | Cost |
|---------|---------|------|
| Railway.app | Backend hosting (HTTP + WebSocket) | Free tier / $5/mo Hobby |
| GitHub | Source control + Railway deployment | Free |

**No domain required.** Railway provides a `*.railway.app` HTTPS URL automatically.

---

## 2. Cloud Backend Deployment (Railway.app)

### Step 1: Create Railway Account
1. Go to [railway.app](https://railway.app)
2. Sign up with GitHub

### Step 2: Deploy from GitHub
```bash
# Push your project to GitHub first
cd /Users/chitra/Documents/antigravity/joyful-hawking
git add .
git commit -m "chore: cloud migration"
git push origin main
```

3. In Railway dashboard: **New Project → Deploy from GitHub Repo**
4. Select your repo
5. Railway auto-detects the `server/Dockerfile`

### Step 3: Set Environment Variables
In Railway project settings → **Variables**:

```
SECRET_KEY = <generate with: python3 -c "import secrets; print(secrets.token_hex(32))">
DB_PATH = /data/chatooz.db
PORT = 8080
WS_PORT = 8081
ALLOW_ORIGINS = *
```

### Step 4: Add Persistent Volume (for SQLite)
1. Railway project → **Add Volume**
2. Mount path: `/data`
3. This ensures your database persists across deployments

### Step 5: Get Your HTTPS URL
Railway gives you a URL like: `https://chatooz-server-production-xxxx.railway.app`

### Step 6: Expose Both Ports
Railway handles HTTPS on port 443 → proxies to your `PORT`.
For WebSocket on port 8081, add a second service or use Railway's TCP proxy feature.

> [!IMPORTANT]
> **WebSocket Port**: Railway free tier supports one public HTTP/HTTPS port. To expose
> WebSocket on port 8081, either:
> - Use Railway Pro ($5/mo) which allows TCP proxy, OR
> - Run both HTTP and WebSocket on **the same port 8080** using a WebSocket upgrade handler
> 
> **Quick fix**: Change `WS_PORT` to `8080` in the server environment variables and update
> Android's `MEDIA_WS_URL` in `build.gradle.kts` to use port 80/443 (the standard HTTPS port).

---

## 3. Alternative Free Deployment: Render.com

### Render Free Tier
1. Go to [render.com](https://render.com) → New → Web Service
2. Connect GitHub repo
3. Build command: `pip install -r server/requirements.txt`
4. Start command: `python3 server/chatooz_server.py`
5. Add environment variables (same as Railway)

> [!NOTE]
> Render free tier sleeps after 15 minutes of inactivity. For always-on, use Render Starter
> ($7/mo) or Railway ($5/mo).

---

## 4. Android App Configuration

### For Production Release Build

The production URL is already configured in `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://chatooz-server.railway.app\"")
buildConfigField("String", "MEDIA_WS_URL", "\"wss://chatooz-server.railway.app/media\"")
```

To use **your actual Railway URL**, either:

**Option A: Gradle property (recommended)**
```bash
# Create local.properties (already in .gitignore)
echo "CHATOOZ_API_URL=https://your-server.railway.app" >> local.properties
```

**Option B: Environment variable**
```bash
CHATOOZ_API_URL=https://your-server.railway.app ./gradlew assembleRelease
```

**Option C: Direct edit** (not recommended — don't commit)
```kotlin
// in app/build.gradle.kts release block:
buildConfigField("String", "API_BASE_URL", "\"https://YOUR-ACTUAL-URL.railway.app\"")
```

### Build Debug APK (local dev)
```bash
# Start local server first
cd server
python3 sync_server.py

# Set up ADB tunnels (physical phone)
adb -s <device-id> reverse tcp:8080 tcp:8080
adb -s <device-id> reverse tcp:8081 tcp:8081

# Build and install
./gradlew installDebug
```

### Build Release APK (production)
```bash
CHATOOZ_API_URL=https://your-server.railway.app ./gradlew assembleRelease
```

---

## 5. Database Setup

The server uses SQLite (automatic — no setup needed). On first run, tables are created.

**To migrate existing data from sync_db.json**:
```bash
cd server
python3 -c "
import json, sqlite3

with open('sync_db.json') as f:
    data = json.load(f)

# Run the server once to init tables, then use the /sync endpoint:
import urllib.request, json as _json
req = urllib.request.Request(
    'http://localhost:8080/sync',
    data=_json.dumps({'data': data}).encode(),
    headers={'Content-Type': 'application/json'},
    method='POST'
)
urllib.request.urlopen(req)
print('Migration done')
"
```

---

## 6. Push Notifications (Future)

Currently: In-app polling + local Android notifications only.

**To add FCM push notifications:**
1. Create Firebase project at console.firebase.google.com
2. Add `google-services.json` to `app/`
3. Add FCM dependency: `implementation("com.google.firebase:firebase-messaging")`
4. Create `ChatoozFirebaseService : FirebaseMessagingService`
5. Store FCM token on server: add `device_tokens` table
6. Server sends FCM notification when message arrives for offline user

**Estimated effort**: 1-2 days

---

## 7. WebRTC / STUN / TURN (Future)

Currently: Custom binary relay over WebSocket. Works over most NATs via port 443.

**Known limitation**: Strict CGNAT or symmetric NAT may block direct WebSocket to server.
This is uncommon but possible on some mobile carrier networks.

**To add full WebRTC:**
1. Add dependency: `implementation("org.webrtc:google-webrtc:1.0.32006")`
2. Deploy COTURN: `docker run -d -p 3478:3478 -p 3478:3478/udp coturn/coturn`
3. Replace `CallMediaClient` with WebRTC PeerConnection
4. Use short-lived TURN credentials (HMAC-SHA1 with expiry)

**Estimated effort**: 3-5 days

---

## 8. Local Development

```bash
# Terminal 1: Start local server
cd server
python3 sync_server.py 8080
# → HTTP on :8080, WebSocket on :8081

# Terminal 2: ADB tunnels for physical phone
adb -s RZCW11V1J6W reverse tcp:8080 tcp:8080
adb -s RZCW11V1J6W reverse tcp:8081 tcp:8081

# Terminal 3: Build and install
./gradlew installDebug

# Emulator: No ADB tunnel needed — uses 10.0.2.2 automatically
# (AppConfig.resolveDevUrl() handles the translation)
```

---

## 9. Testing Internet-Wide

### Test Scenario: Different Networks
1. Phone A on Wi-Fi → deploy to Railway → message Phone B on mobile data
2. Phone A initiates video call → Phone B (on different ISP) accepts
3. Check audio is audible both ways
4. Check video is visible both ways

### Verify Endpoints
```bash
# Health check
curl https://your-server.railway.app/health
# Expected: {"status": "ok", "version": "2.0", ...}

# Sync read
curl https://your-server.railway.app/sync
# Expected: {"name": "chatooz_cloud_sync_db", "data": {...}}
```

---

## 10. Troubleshooting

| Problem | Solution |
|---------|----------|
| Calls connect but no audio | Check ADB tunnel: `adb reverse tcp:8081 tcp:8081` |
| App crashes on release build | Check `CHATOOZ_API_URL` is set to HTTPS URL |
| Messages not syncing | Verify `/health` endpoint returns OK |
| Video call shows black screen | Camera permission not granted? |
| WebSocket media not connecting | Port 8081 must be reachable; use same-port solution for Railway |
| Emulator can't reach server | AppConfig auto-translates 127.0.0.1→10.0.2.2 |
