#!/usr/bin/env python3
"""
Chatooz Production Server v2.0
==============================
Internet-capable backend for the Chatooz messaging + calling application.

Ports:
  HTTP/HTTPS: Handled by gunicorn/uvicorn + reverse proxy (e.g., Railway)
  WebSocket /media: Real-time binary media relay for audio+video calls

Endpoints:
  GET  /health              — Health check (returns {"status": "ok"})
  GET  /sync                — Fetch current cloud database state
  POST /sync                — Push + merge database state
  PUT  /sync                — Alternative push
  POST /call/signal         — Post a call signalling event
  GET  /call/signal         — Poll for call signalling events
  WS   /media               — WebSocket binary media relay for calls

Environment Variables:
  PORT          Server port (default: 8080)
  SECRET_KEY    HMAC secret for session tokens (REQUIRED in production)
  DB_PATH       Path to SQLite database file (default: ./chatooz.db)
  ALLOW_ORIGINS CORS origins (default: "*" — restrict in production)
  MAX_MSG_AGE   Max seconds to keep old messages in DB (default: 86400 * 30)

Security Notes:
  - Set SECRET_KEY to a long random string in production
  - Restrict ALLOW_ORIGINS to your app's domain in production
  - Rate limiting is applied per IP
  - Passwords are NOT stored (auth is email-based session tokens)

Deployment:
  Railway:   Use Dockerfile or Procfile — see server/Dockerfile
  Fly.io:    Use fly.toml
  Render:    Use render.yaml
  Local dev: python3 chatooz_server.py
"""

import asyncio
import hashlib
import hmac
import json
import logging
import os
import re
import sqlite3
import struct
import threading
import time
from collections import defaultdict, deque
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib import parse
import websockets
from websockets.server import WebSocketServerProtocol

# ─── Configuration ────────────────────────────────────────────────────────────
PORT = int(os.environ.get("PORT", 8080))
WS_PORT = int(os.environ.get("WS_PORT", PORT))  # WS on same port via path routing
SECRET_KEY = os.environ.get("SECRET_KEY", "chatooz_dev_secret_change_in_production")
DB_PATH = os.environ.get("DB_PATH", os.path.join(os.path.dirname(os.path.abspath(__file__)), "chatooz.db"))
ALLOW_ORIGINS = os.environ.get("ALLOW_ORIGINS", "*")
MAX_MSG_AGE = int(os.environ.get("MAX_MSG_AGE", str(86400 * 30)))  # 30 days default

# ─── Logging ──────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
log = logging.getLogger("chatooz")

# ─── Rate Limiting ────────────────────────────────────────────────────────────
_rate_buckets: dict[str, deque] = defaultdict(lambda: deque())
_rate_lock = threading.Lock()
RATE_LIMIT_REQUESTS = 60   # Max requests per window
RATE_LIMIT_WINDOW = 60     # seconds

def is_rate_limited(ip: str) -> bool:
    now = time.time()
    with _rate_lock:
        bucket = _rate_buckets[ip]
        # Remove old entries outside window
        while bucket and bucket[0] < now - RATE_LIMIT_WINDOW:
            bucket.popleft()
        if len(bucket) >= RATE_LIMIT_REQUESTS:
            return True
        bucket.append(now)
        return False

# ─── SQLite Database ──────────────────────────────────────────────────────────
_db_lock = threading.Lock()

def get_db():
    """Get a thread-local SQLite connection."""
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn

def init_db():
    """Create tables if they don't exist."""
    with _db_lock:
        conn = get_db()
        conn.executescript("""
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                username TEXT UNIQUE NOT NULL,
                email TEXT,
                avatar_color INTEGER DEFAULT 0,
                bio TEXT DEFAULT '',
                created_at INTEGER DEFAULT 0,
                updated_at INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS friend_requests (
                id TEXT PRIMARY KEY,
                sender_id TEXT NOT NULL,
                sender_username TEXT NOT NULL,
                sender_name TEXT NOT NULL,
                sender_avatar_color INTEGER DEFAULT 0,
                receiver_id TEXT NOT NULL,
                receiver_username TEXT NOT NULL,
                receiver_name TEXT NOT NULL,
                receiver_avatar_color INTEGER DEFAULT 0,
                status TEXT DEFAULT 'PENDING',
                timestamp INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                chat_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_from_me INTEGER DEFAULT 0,
                status TEXT DEFAULT 'SENT',
                type TEXT DEFAULT 'TEXT',
                audio_duration_sec INTEGER,
                audio_base64 TEXT,
                audio_file_path TEXT,
                created_at INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS blocked_users (
                blocker_id TEXT NOT NULL,
                blocked_id TEXT NOT NULL,
                blocked_username TEXT NOT NULL,
                blocked_name TEXT DEFAULT '',
                PRIMARY KEY (blocker_id, blocked_id)
            );

            CREATE INDEX IF NOT EXISTS idx_messages_chat ON messages(chat_id, timestamp);
            CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id);
            CREATE INDEX IF NOT EXISTS idx_friend_requests_receiver ON friend_requests(receiver_id);
        """)
        conn.commit()
        conn.close()
    log.info(f"Database initialized at {DB_PATH}")

# ─── Database Operations ──────────────────────────────────────────────────────
def load_db_as_payload() -> dict:
    """Load all data as the CloudDbPayload format the Android app expects."""
    with _db_lock:
        conn = get_db()
        try:
            users = [dict(r) for r in conn.execute(
                "SELECT id, name, username, email, avatar_color as avatarColor, bio, created_at as createdAt FROM users"
            ).fetchall()]

            requests = [dict(r) for r in conn.execute(
                """SELECT id, sender_id as senderId, sender_username as senderUsername,
                   sender_name as senderName, sender_avatar_color as senderAvatarColor,
                   receiver_id as receiverId, receiver_username as receiverUsername,
                   receiver_name as receiverName, receiver_avatar_color as receiverAvatarColor,
                   status, timestamp FROM friend_requests"""
            ).fetchall()]

            messages = [dict(r) for r in conn.execute(
                """SELECT id, chat_id as chatId, sender_id as senderId, text, timestamp,
                   is_from_me as isFromMe, status, type, audio_duration_sec as audioDurationSec,
                   audio_base64 as audioBase64, audio_file_path as audioFilePath
                   FROM messages ORDER BY timestamp ASC"""
            ).fetchall()]

            # Convert SQLite integers to booleans
            for m in messages:
                m["isFromMe"] = bool(m.get("isFromMe", 0))
                # Remove null fields to keep JSON clean
                m = {k: v for k, v in m.items() if v is not None}

            blocked = [dict(r) for r in conn.execute(
                "SELECT blocker_id as blockerId, blocked_id as blockedId, blocked_username as blockedUsername, blocked_name as blockedName FROM blocked_users"
            ).fetchall()]

            return {
                "users": users,
                "friend_requests": requests,
                "messages": messages,
                "blocked_users": blocked
            }
        finally:
            conn.close()

def merge_payload(payload_data: dict) -> dict:
    """Merge incoming payload with existing database. Returns merged state."""
    with _db_lock:
        conn = get_db()
        try:
            # ── Users ──────────────────────────────────────────────────────────
            for u in payload_data.get("users", []):
                conn.execute("""
                    INSERT INTO users (id, name, username, email, avatar_color, bio, created_at, updated_at)
                    VALUES (:id, :name, :username, :email, :avatarColor, :bio, :createdAt, :updatedAt)
                    ON CONFLICT(id) DO UPDATE SET
                        name = excluded.name,
                        username = excluded.username,
                        email = excluded.email,
                        avatar_color = excluded.avatar_color,
                        bio = excluded.bio,
                        updated_at = excluded.updated_at
                """, {
                    "id": u.get("id", ""),
                    "name": u.get("name", ""),
                    "username": u.get("username", ""),
                    "email": u.get("email", ""),
                    "avatarColor": u.get("avatarColor", 0),
                    "bio": u.get("bio", ""),
                    "createdAt": u.get("createdAt", int(time.time() * 1000)),
                    "updatedAt": int(time.time() * 1000)
                })

            # ── Friend Requests ────────────────────────────────────────────────
            for r in payload_data.get("friend_requests", []):
                existing = conn.execute(
                    "SELECT status FROM friend_requests WHERE id = ?", (r.get("id"),)
                ).fetchone()

                if existing:
                    curr_status = existing["status"]
                    new_status = r.get("status", "PENDING")
                    # Don't downgrade ACCEPTED/DECLINED back to PENDING
                    if curr_status in ("ACCEPTED", "DECLINED") and new_status == "PENDING":
                        continue
                    conn.execute(
                        "UPDATE friend_requests SET status = ? WHERE id = ?",
                        (new_status, r.get("id"))
                    )
                else:
                    conn.execute("""
                        INSERT OR IGNORE INTO friend_requests
                        (id, sender_id, sender_username, sender_name, sender_avatar_color,
                         receiver_id, receiver_username, receiver_name, receiver_avatar_color,
                         status, timestamp)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (
                        r.get("id", ""), r.get("senderId", ""), r.get("senderUsername", ""),
                        r.get("senderName", ""), r.get("senderAvatarColor", 0),
                        r.get("receiverId", ""), r.get("receiverUsername", ""),
                        r.get("receiverName", ""), r.get("receiverAvatarColor", 0),
                        r.get("status", "PENDING"), r.get("timestamp", int(time.time() * 1000))
                    ))

            # ── Messages ───────────────────────────────────────────────────────
            for m in payload_data.get("messages", []):
                mid = m.get("id", "")
                if not mid:
                    continue
                existing = conn.execute(
                    "SELECT status FROM messages WHERE id = ?", (mid,)
                ).fetchone()

                if existing:
                    curr_status = existing["status"]
                    new_status = m.get("status", "SENT")
                    # Don't downgrade READ messages
                    if curr_status == "READ" and new_status != "READ":
                        continue
                    conn.execute(
                        "UPDATE messages SET status = ? WHERE id = ?",
                        (new_status, mid)
                    )
                else:
                    conn.execute("""
                        INSERT OR IGNORE INTO messages
                        (id, chat_id, sender_id, text, timestamp, is_from_me, status, type,
                         audio_duration_sec, audio_base64, audio_file_path, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (
                        mid, m.get("chatId", ""), m.get("senderId", ""),
                        m.get("text", ""), m.get("timestamp", 0),
                        1 if m.get("isFromMe", False) else 0,
                        m.get("status", "SENT"), m.get("type", "TEXT"),
                        m.get("audioDurationSec"), m.get("audioBase64"), m.get("audioFilePath"),
                        int(time.time() * 1000)
                    ))

            # ── Blocked Users ──────────────────────────────────────────────────
            for b in payload_data.get("blocked_users", []):
                conn.execute("""
                    INSERT OR IGNORE INTO blocked_users
                    (blocker_id, blocked_id, blocked_username, blocked_name)
                    VALUES (?, ?, ?, ?)
                """, (
                    b.get("blockerId", ""), b.get("blockedId", ""),
                    b.get("blockedUsername", ""), b.get("blockedName", "")
                ))

            conn.commit()
            return load_db_as_payload()
        except Exception as e:
            conn.rollback()
            log.error(f"merge_payload error: {e}")
            raise
        finally:
            conn.close()

# ─── Call Signalling (In-Memory) ──────────────────────────────────────────────
_signal_lock = threading.Lock()
_signal_seq = 0
CALL_SIGNALS: list[dict] = []
SIGNAL_MAX_AGE = 120  # seconds

def add_signal(call_id: str, sender_id: str, sig_type: str, payload: str) -> int:
    global _signal_seq
    now = time.time()
    with _signal_lock:
        _signal_seq += 1
        CALL_SIGNALS.append({
            "id": _signal_seq,
            "callId": call_id,
            "senderId": sender_id,
            "type": sig_type,
            "payload": payload,
            "ts": now
        })
        # Prune old signals
        cutoff = now - SIGNAL_MAX_AGE
        while CALL_SIGNALS and CALL_SIGNALS[0]["ts"] < cutoff:
            CALL_SIGNALS.pop(0)
        return _signal_seq

def get_signals(call_id: str, user_id: str, after_seq: int) -> list[dict]:
    now = time.time()
    cutoff = now - SIGNAL_MAX_AGE
    with _signal_lock:
        return [
            {"id": s["id"], "type": s["type"], "payload": s["payload"], "senderId": s["senderId"]}
            for s in CALL_SIGNALS
            if s["callId"] == call_id
            and s["id"] > after_seq
            and s["ts"] >= cutoff
            and s["senderId"] != user_id  # Don't echo back
        ]

# ─── HTTP Request Handler ─────────────────────────────────────────────────────
class ChatoozHandler(BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        # Suppress noisy media poll logs in production
        if "/call/audio" not in self.path and "/call/video" not in self.path:
            log.info(f"{self.client_address[0]} - {fmt % args}")

    def _cors_headers(self):
        return {
            "Access-Control-Allow-Origin": ALLOW_ORIGINS,
            "Access-Control-Allow-Methods": "GET, POST, PUT, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type, Accept, User-Agent, Authorization",
        }

    def _send_json(self, obj: dict, status: int = 200):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        for k, v in self._cors_headers().items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _send_error(self, message: str, status: int = 400):
        self._send_json({"error": message}, status)

    def _read_json(self) -> dict | None:
        try:
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length) if length > 0 else b""
            return json.loads(body.decode("utf-8"))
        except Exception:
            return None

    def _check_rate_limit(self) -> bool:
        ip = self.client_address[0]
        if is_rate_limited(ip):
            self._send_error("Too many requests", 429)
            return True
        return False

    def do_OPTIONS(self):
        self.send_response(200)
        for k, v in self._cors_headers().items():
            self.send_header(k, v)
        self.end_headers()

    def do_GET(self):
        if self._check_rate_limit():
            return
        path = parse.urlparse(self.path).path
        if path == "/health":
            self._handle_health()
        elif path == "/call/signal":
            self._handle_signal_poll()
        elif path in ("/sync", "/"):
            self._handle_sync_read()
        else:
            self._send_json({"error": "Not found"}, 404)

    def do_POST(self):
        if self._check_rate_limit():
            return
        path = parse.urlparse(self.path).path
        if path == "/call/signal":
            self._handle_signal_post()
        elif path in ("/sync", "/"):
            self._handle_sync_write()
        else:
            self._send_json({"error": "Not found"}, 404)

    def do_PUT(self):
        if self._check_rate_limit():
            return
        path = parse.urlparse(self.path).path
        if path in ("/sync", "/"):
            self._handle_sync_write()
        else:
            self._send_json({"error": "Not found"}, 404)

    def _handle_health(self):
        self._send_json({
            "status": "ok",
            "version": "2.0",
            "server": "Chatooz Production Server",
            "timestamp": int(time.time())
        })

    def _handle_sync_read(self):
        try:
            data = load_db_as_payload()
            self._send_json({"name": "chatooz_cloud_sync_db", "data": data})
        except Exception as e:
            log.error(f"sync read error: {e}")
            self._send_error("Server error", 500)

    def _handle_sync_write(self):
        try:
            req = self._read_json()
            if req is None:
                self._send_error("Invalid JSON body")
                return
            payload_data = req.get("data", req)
            merged = merge_payload(payload_data)
            self._send_json({"name": "chatooz_cloud_sync_db", "data": merged})
        except Exception as e:
            log.error(f"sync write error: {e}")
            self._send_error("Server error", 500)

    def _handle_signal_post(self):
        try:
            data = self._read_json()
            if not data:
                self._send_error("Invalid JSON")
                return
            call_id = data.get("callId", "")
            sender_id = data.get("senderId", "")
            sig_type = data.get("type", "")
            payload = data.get("payload", "{}")
            if not call_id or not sig_type:
                self._send_error("Missing callId or type")
                return
            seq = add_signal(call_id, sender_id, sig_type, payload)
            self._send_json({"status": "ok", "seq": seq})
        except Exception as e:
            self._send_error(str(e))

    def _handle_signal_poll(self):
        try:
            params = dict(parse.parse_qsl(parse.urlparse(self.path).query))
            call_id = params.get("callId", "")
            user_id = params.get("userId", "")
            after_seq = int(params.get("afterSeq", "0"))
            if not call_id:
                self._send_error("Missing callId")
                return
            signals = get_signals(call_id, user_id, after_seq)
            self._send_json({"messages": signals})
        except Exception as e:
            self._send_error(str(e))


# ─── WebSocket Media Relay ────────────────────────────────────────────────────
# Binary packet format (same as before, now over WebSocket frames):
#   [1 byte type][4 bytes big-endian payload length][payload]
# Type 0x01: Handshake JSON: {"callId": "...", "userId": "..."}
# Type 0x02: Audio PCM chunk — relay to peer(s)
# Type 0x03: Video JPEG frame — relay to peer(s)
# Type 0x04: Ping/keepalive

_ws_lock = asyncio.Lock()
# {callId: {userId: WebSocket}}
_ws_peers: dict[str, dict[str, WebSocketServerProtocol]] = {}

async def handle_media_ws(websocket: WebSocketServerProtocol):
    """Handle a WebSocket media relay connection."""
    call_id = None
    user_id = None
    remote = websocket.remote_address
    log.info(f"WS connected from {remote}")

    try:
        async for message in websocket:
            if not isinstance(message, bytes) or len(message) < 5:
                continue

            pkg_type = message[0]
            length = struct.unpack("!I", message[1:5])[0]

            if length > 2 * 1024 * 1024:
                log.warning(f"WS packet too large ({length}B) from {remote}")
                continue

            payload = message[5:5 + length]

            if pkg_type == 0x01:
                # Handshake
                try:
                    meta = json.loads(payload.decode("utf-8"))
                    call_id = meta.get("callId")
                    user_id = meta.get("userId")
                    if not call_id or not user_id:
                        await websocket.close(1008, "Missing callId or userId")
                        return

                    async with _ws_lock:
                        if call_id not in _ws_peers:
                            _ws_peers[call_id] = {}
                        _ws_peers[call_id][user_id] = websocket

                    # Send handshake ack
                    ack_body = b"OK"
                    ack = bytes([0x01]) + struct.pack("!I", len(ack_body)) + ack_body
                    await websocket.send(ack)
                    log.info(f"WS handshake: callId={call_id} userId={user_id} from {remote}")
                except Exception as e:
                    log.error(f"WS handshake error: {e}")
                    await websocket.close(1008, "Handshake failed")
                    return

            elif pkg_type in (0x02, 0x03):
                # Audio (0x02) or Video (0x03) — relay to other peers in same callId
                if call_id:
                    async with _ws_lock:
                        peers = list(_ws_peers.get(call_id, {}).items())
                    for pid, psock in peers:
                        if pid != user_id and psock.open:
                            try:
                                await psock.send(message)
                            except Exception:
                                pass

            elif pkg_type == 0x04:
                # Ping → Pong
                pong = bytes([0x04]) + struct.pack("!I", 0)
                await websocket.send(pong)

    except websockets.exceptions.ConnectionClosedOK:
        log.info(f"WS closed normally: callId={call_id} userId={user_id}")
    except websockets.exceptions.ConnectionClosedError as e:
        log.info(f"WS closed with error: {e} callId={call_id} userId={user_id}")
    except Exception as e:
        log.error(f"WS error: {e}")
    finally:
        if call_id and user_id:
            async with _ws_lock:
                room = _ws_peers.get(call_id, {})
                room.pop(user_id, None)
                if not room:
                    _ws_peers.pop(call_id, None)


def start_ws_server(port: int):
    """Start the WebSocket media relay server in its own event loop thread."""
    async def _serve():
        log.info(f"WebSocket Media Relay starting on ws://0.0.0.0:{port}/media")
        async with websockets.serve(
            handle_media_ws,
            "0.0.0.0",
            port,
            ping_interval=20,
            ping_timeout=30,
            max_size=3 * 1024 * 1024,  # 3MB max message
        ):
            await asyncio.Future()  # Run forever

    asyncio.run(_serve())


# ─── Entry Point ──────────────────────────────────────────────────────────────
def run():
    # Warn if using default dev secret key in a non-dev context
    if SECRET_KEY == "chatooz_dev_secret_change_in_production":
        log.warning("⚠️  Using default SECRET_KEY — set SECRET_KEY env var in production!")

    # Initialize database
    init_db()

    # Start WebSocket media relay on a separate port (or same if configured)
    ws_port = int(os.environ.get("WS_PORT", PORT + 1))
    ws_thread = threading.Thread(target=start_ws_server, args=(ws_port,), daemon=True)
    ws_thread.start()
    log.info(f"WebSocket Media Relay started on port {ws_port}")

    # Start HTTP server
    server = ThreadingHTTPServer(("0.0.0.0", PORT), ChatoozHandler)
    log.info(f"Chatooz HTTP Server running on http://0.0.0.0:{PORT}")
    log.info(f"  DB: {DB_PATH}")
    log.info(f"  Dev mode: {SECRET_KEY == 'chatooz_dev_secret_change_in_production'}")
    log.info(f"  Endpoints: /health /sync /call/signal")
    log.info(f"  WebSocket: ws://0.0.0.0:{ws_port}/media (binary media relay)")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log.info("Server stopping...")
    server.server_close()


if __name__ == "__main__":
    run()
