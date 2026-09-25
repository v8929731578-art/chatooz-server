#!/bin/bash
# ==============================================================================
# Chatooz GO ONLINE — Fully Automated Free Internet Setup
# ==============================================================================
# Kya karta hai:
#  1. Cloudflared download karta hai (FREE, koi account nahi chahiye)
#  2. Server start karta hai
#  3. Cloudflare Quick Tunnel se HTTPS URL milta hai
#  4. Android app rebuild karta hai us URL ke saath
#  5. Connected devices pe install karta hai
#
# Usage:   bash server/go_online.sh
# Stop:    bash server/stop_online.sh
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SERVER_PY="$SCRIPT_DIR/chatooz_online_server.py"
SERVER_PORT=8080
CF_LOG="/tmp/chatooz_cf.log"
SRV_LOG="/tmp/chatooz_srv.log"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
JAVA_HOME_PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✅] $1${NC}"; }
warn() { echo -e "${YELLOW}[⚠️ ] $1${NC}"; }
info() { echo -e "${BLUE}[🔹] $1${NC}"; }
die()  { echo -e "${RED}[❌] $1${NC}"; exit 1; }

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"
echo -e "${BLUE}    CHATOOZ — INTERNET PE CHALAAO (FREE TUNNEL)    ${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════${NC}"
echo ""

# ─── Step 1: Cloudflared install ──────────────────────────────────────────────
info "Step 1/5 → Cloudflared check..."
CF_BIN=""

# Check common locations
for p in "/opt/homebrew/bin/cloudflared" "/usr/local/bin/cloudflared" "$HOME/.local/bin/cloudflared" "$HOME/bin/cloudflared"; do
    if [ -x "$p" ]; then
        CF_BIN="$p"
        break
    fi
done

# Also check PATH
if [ -z "$CF_BIN" ] && command -v cloudflared &>/dev/null; then
    CF_BIN=$(command -v cloudflared)
fi

if [ -z "$CF_BIN" ]; then
    warn "Cloudflared nahi mila. Install kar raha hoon..."
    
    # Try brew first (best on macOS — correct native binary)
    if command -v brew &>/dev/null; then
        info "Brew se install kar raha hoon..."
        brew install cloudflared 2>&1 | tail -5 || true
        CF_BIN=$(command -v cloudflared 2>/dev/null || true)
    fi
    
    # If brew didn't work, download .pkg format
    if [ -z "$CF_BIN" ]; then
        ARCH=$(uname -m)
        if [ "$ARCH" = "arm64" ]; then
            CF_PKG_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-arm64.pkg"
        else
            CF_PKG_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-amd64.pkg"
        fi
        CF_PKG="/tmp/cloudflared.pkg"
        info "Downloading pkg: $CF_PKG_URL"
        curl -L --progress-bar --max-time 120 "$CF_PKG_URL" -o "$CF_PKG" && \
        sudo installer -pkg "$CF_PKG" -target /usr/local && \
        CF_BIN="/usr/local/bin/cloudflared" || CF_BIN=""
    fi
    
    [ -n "$CF_BIN" ] && log "Cloudflared install ho gaya!" || die "Cloudflared install fail! Manually karo: brew install cloudflared"
fi

log "Cloudflared: $CF_BIN ($(\"$CF_BIN\" --version 2>&1 | head -1))"

# ─── Step 2: Purana sab band karo ─────────────────────────────────────────────
info "Step 2/5 → Purani processes band kar raha hoon..."
pkill -f "chatooz_online_server" 2>/dev/null || true
pkill -f "chatooz_online"        2>/dev/null || true
pkill -f "sync_server.py"        2>/dev/null || true
pkill -f "cloudflared"           2>/dev/null || true
sleep 2

# ─── Step 3: Python deps ──────────────────────────────────────────────────────
info "Step 3/5 → Python aiohttp check..."
python3 -c "import aiohttp" 2>/dev/null || pip3 install aiohttp --quiet
log "Python deps OK"

# ─── Step 3: Server + Tunnel start ────────────────────────────────────────────
info "Step 4/5 → Server + Tunnel start kar raha hoon..."

# Server ko background me chalaao
python3 "$SERVER_PY" $SERVER_PORT > "$SRV_LOG" 2>&1 &
SRV_PID=$!
sleep 2

# Health check
curl -sf --max-time 5 "http://127.0.0.1:$SERVER_PORT/health" > /dev/null || die "Server start nahi hua! Log: $SRV_LOG"
log "Server chal raha hai (PID=$SRV_PID) on port $SERVER_PORT"

# Cloudflared ko run karo aur URL capture karo
info "Cloudflare Tunnel bana raha hoon..."
info "(ye free hai, koi account nahi chahiye, ek minute lagega)"

# Run cloudflared, capture all output to file, run in background
"$CF_BIN" tunnel --url "http://127.0.0.1:$SERVER_PORT" --no-autoupdate \
    --loglevel info 2>&1 | tee "$CF_LOG" &
CF_PID=$!

# URL dhundho
echo -n "  URL dhundh raha hoon"
TUNNEL_URL=""
for i in $(seq 1 45); do
    echo -n "."
    sleep 2
    TUNNEL_URL=$(grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' "$CF_LOG" 2>/dev/null | head -1 || true)
    [ -n "$TUNNEL_URL" ] && break
done
echo ""

if [ -z "$TUNNEL_URL" ]; then
    echo "Log file content:"
    cat "$CF_LOG" 2>/dev/null | head -40 || true
    die "Tunnel URL nahi mila! Internet slow ho sakta hai. Dobara try karo."
fi

log "🌐 TUNNEL URL MILA: $TUNNEL_URL"

# Derived URLs
WS_URL="${TUNNEL_URL/https:\/\//wss:\/\/}/media"
HEALTH_URL="$TUNNEL_URL/health"

# Test tunnel
info "Tunnel test kar raha hoon..."
sleep 5
TUNNEL_WORKING=false
for i in 1 2 3 4 5; do
    if curl -sf --max-time 10 "$HEALTH_URL" | python3 -c "import sys,json; d=json.load(sys.stdin); sys.exit(0 if d.get('status')=='ok' else 1)" 2>/dev/null; then
        TUNNEL_WORKING=true
        break
    fi
    sleep 5
done

if $TUNNEL_WORKING; then
    log "Tunnel verified — internet se accessible hai!"
else
    warn "Tunnel abhi warm up ho raha hai (normal hai, 30-60 sec lagte hain)"
fi

# ─── Step 4: App Build ────────────────────────────────────────────────────────
info "Step 5/5 → Android app rebuild kar raha hoon..."

# local.properties update
PROPS="$PROJECT_DIR/local.properties"
TMP_PROPS=$(mktemp)
grep -v "^CHATOOZ_API_URL" "$PROPS" > "$TMP_PROPS" 2>/dev/null || true
echo "CHATOOZ_API_URL=$TUNNEL_URL" >> "$TMP_PROPS"
cp "$TMP_PROPS" "$PROPS"
rm "$TMP_PROPS"
log "local.properties → CHATOOZ_API_URL=$TUNNEL_URL"

# Gradle build
export JAVA_HOME="$JAVA_HOME_PATH"
info "Gradle assembleDebug chal raha hai (2-3 min)..."
cd "$PROJECT_DIR"
JAVA_HOME="$JAVA_HOME_PATH" \
    "$PROJECT_DIR/gradlew" assembleDebug \
    -PCHATOOZ_API_URL="$TUNNEL_URL" \
    --quiet 2>&1

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK_PATH" ] || die "APK nahi bana! Gradle error check karo."
log "APK build SUCCESS!"

# Verify BuildConfig
RELEASE_BC="$PROJECT_DIR/app/build/generated/source/buildConfig/debug/com/chatooz/app/BuildConfig.java"
if [ -f "$RELEASE_BC" ]; then
    BC_URL=$(grep "API_BASE_URL" "$RELEASE_BC" | grep -o '"[^"]*"' | head -1 || true)
    log "BuildConfig URL: $BC_URL"
fi

# Install on devices
INSTALLED=0
if [ -x "$ADB" ]; then
    for DEVICE in $("$ADB" devices 2>/dev/null | awk '/device$/{print $1}'); do
        info "Install: $DEVICE"
        "$ADB" -s "$DEVICE" install -r "$APK_PATH" 2>&1 | grep -E "Success|Failure" || true
        INSTALLED=$((INSTALLED+1))
    done
fi

# ─── Final Summary ────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo -e "${GREEN}    🎉 CHATOOZ AB INTERNET PE CHAL RAHA HAI! 🎉    ${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${BLUE}Server URL:${NC}       $TUNNEL_URL"
echo -e "  ${BLUE}WebSocket:${NC}        $WS_URL"
echo -e "  ${BLUE}Health check:${NC}     $HEALTH_URL"
echo ""
echo -e "  ${GREEN}Kisi bhi network se kaam karega:${NC}"
echo -e "  📱 Wi-Fi → 4G/5G ✅"
echo -e "  🌍 Alag shahar ✅"
echo -e "  🌐 Alag desh ✅"
echo -e "  🔒 HTTPS/WSS secure ✅"
echo ""

if [ "$INSTALLED" -gt 0 ]; then
    log "$INSTALLED device(s) pe install ho gaya"
else
    warn "Abhi koi device connected nahi tha."
    echo -e "  Phone USB se lagao phir:"
    echo -e "  ${YELLOW}$ADB install -r $APK_PATH${NC}"
fi

echo ""
echo -e "${YELLOW}⚠️  NOTE:${NC}"
echo "  - Tunnel tab tak active rahega jab tak ye terminal khula hai"
echo "  - Mac band na karo — server Mac pe chal raha hai"
echo "  - Agar kal dobara chahiye: bash server/go_online.sh"
echo "  - URL har session mein naya milega (app auto-rebuild hoga)"
echo ""
echo "  Band karne ke liye: Ctrl+C ya bash server/stop_online.sh"
echo ""

# Keep running until Ctrl+C
trap "echo ''; warn 'Band kar raha hoon...'; pkill -f chatooz_online_server 2>/dev/null; pkill -f cloudflared 2>/dev/null; exit 0" INT TERM
wait $CF_PID $SRV_PID 2>/dev/null || true
