#!/usr/bin/env python3
"""
Chatooz Multi-Device Sync Server & Real-time Media Relay (LOCAL DEV VERSION).
Port 8080: HTTP Sync, Persistent Signalling.
Port 8081: WebSocket Binary Media Relay (replaces old raw TCP).

For production deployment, use server/chatooz_server.py with Railway/Render/Fly.io.
"""

import asyncio
import json
import os
import socket
import struct
import sys
import threading
import time
from collections import deque
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib import parse

try:
    import websockets
    HAS_WEBSOCKETS = True
except ImportError:
    HAS_WEBSOCKETS = False
    print("WARNING: 'websockets' not installed. WS media relay disabled. Run: pip3 install websockets")


DB_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sync_db.json")

_lock = threading.Lock()

# ─── Non-destructive Signalling State ─────────────────────────────────────────
# List of dicts: {"id": int, "callId": str, "senderId": str, "type": str, "payload": str, "ts": float}
_signal_seq = 0
CALL_SIGNALS_LIST = []

def _add_signal(call_id, sender_id, sig_type, payload):
    global _signal_seq
    now = time.time()
    with _lock:
        _signal_seq += 1
        sig = {
            "id": _signal_seq,
            "callId": call_id,
            "senderId": sender_id,
            "type": sig_type,
            "payload": payload,
            "ts": now
        }
        CALL_SIGNALS_LIST.append(sig)
        # Prune signals older than 90 seconds
        cutoff = now - 90
        while CALL_SIGNALS_LIST and CALL_SIGNALS_LIST[0]["ts"] < cutoff:
            CALL_SIGNALS_LIST.pop(0)
        return _signal_seq

def _get_signals(call_id, user_id, after_seq):
    now = time.time()
    cutoff = now - 90
    with _lock:
        res = []
        for s in CALL_SIGNALS_LIST:
            if s["ts"] < cutoff:
                continue
            if s["callId"] == call_id and s["id"] > after_seq:
                if user_id and s["senderId"] == user_id:
                    # Don't echo user's own signals back to them
                    continue
                res.append({
                    "id": s["id"],
                    "type": s["type"],
                    "payload": s["payload"],
                    "senderId": s["senderId"]
                })
        return res

# ─── In-memory Audio/Video Ring Buffers (HTTP fallback) ───────────────────────
CALL_AUDIO = {}       # {callId: {sender_id: deque of (seq, b64_pcm)}}
CALL_VIDEO = {}       # {callId: {sender_id: (seq, b64_jpeg)}}  (latest frame only)
CALL_MEDIA_TS = {}
AUDIO_LIMIT = 120

def _cleanup_media():
    now = time.time()
    stale = [cid for cid, ts in CALL_MEDIA_TS.items() if now - ts > 120]
    for cid in stale:
        CALL_AUDIO.pop(cid, None)
        CALL_VIDEO.pop(cid, None)
        CALL_MEDIA_TS.pop(cid, None)

# ─── DB Helpers ───────────────────────────────────────────────────────────────
def load_db():
    if os.path.exists(DB_FILE):
        try:
            with open(DB_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {
        "users": [],
        "friend_requests": [],
        "messages": [],
        "blocked_users": []
    }

def save_db(data):
    try:
        with open(DB_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
    except Exception as e:
        print(f"Error saving DB: {e}")

# ─── HTTP Request Handler ─────────────────────────────────────────────────────
class SyncHandler(BaseHTTPRequestHandler):

    def log_message(self, fmt, *args):
        # Don't spam logs with media polling
        if "/call/audio" not in self.path and "/call/video" not in self.path:
            super().log_message(fmt, *args)

    def _set_headers(self, status=200, content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept, User-Agent")
        self.end_headers()

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length", 0))
        return self.rfile.read(length) if length > 0 else b""

    def _send_json(self, obj, status=200):
        body = json.dumps(obj).encode("utf-8")
        self._set_headers(status)
        self.wfile.write(body)

    def do_OPTIONS(self):
        self._set_headers(200)

    # ── GET ──
    def do_GET(self):
        path = parse.urlparse(self.path).path
        if path == "/call/signal":
            self._handle_call_poll()
        elif path == "/call/audio":
            self._handle_audio_poll()
        elif path == "/call/video":
            self._handle_video_poll()
        else:
            self._handle_db_read()

    def _handle_db_read(self):
        db = load_db()
        self._send_json({"name": "chatooz_cloud_sync_db", "data": db})

    def _handle_call_poll(self):
        params = dict(parse.parse_qsl(parse.urlparse(self.path).query))
        call_id   = params.get("callId", "")
        user_id   = params.get("userId", "")
        after_seq = int(params.get("afterSeq", "0"))
        if not call_id:
            self._send_json({"error": "Missing callId"}, 400)
            return
        msgs = _get_signals(call_id, user_id, after_seq)
        self._send_json({"messages": msgs})

    def _handle_audio_poll(self):
        params = dict(parse.parse_qsl(parse.urlparse(self.path).query))
        call_id = params.get("callId", "")
        sender  = params.get("sender", "")
        after   = int(params.get("after", "-1"))
        if not call_id:
            self._send_json({"error": "Missing callId"}, 400)
            return
        with _lock:
            call_buf = CALL_AUDIO.get(call_id, {})
            result = []
            for s, chunks in call_buf.items():
                if sender and s != sender:
                    continue
                for seq, data_b64 in chunks:
                    if seq > after:
                        result.append({"seq": seq, "sender": s, "data": data_b64})
        result.sort(key=lambda x: x["seq"])
        self._send_json({"chunks": result})

    def _handle_video_poll(self):
        params = dict(parse.parse_qsl(parse.urlparse(self.path).query))
        call_id = params.get("callId", "")
        sender  = params.get("sender", "")
        after   = int(params.get("after", "-1"))
        if not call_id:
            self._send_json({"error": "Missing callId"}, 400)
            return
        with _lock:
            call_buf = CALL_VIDEO.get(call_id, {})
            result = None
            for s, (seq, jpeg_b64) in call_buf.items():
                if sender and s != sender:
                    continue
                if seq > after:
                    result = {"seq": seq, "sender": s, "data": jpeg_b64}
        self._send_json({"frame": result})

    # ── POST ──
    def do_POST(self):
        path = parse.urlparse(self.path).path
        if path == "/call/signal":
            self._handle_call_signal()
        elif path == "/call/audio":
            self._handle_audio_push()
        elif path == "/call/video":
            self._handle_video_push()
        else:
            self._handle_db_write()

    def do_PUT(self):
        self._handle_db_write()

    def _handle_call_signal(self):
        try:
            data = json.loads(self._read_body().decode("utf-8"))
            call_id   = data.get("callId")
            sig_type  = data.get("type")
            payload   = data.get("payload", "{}")
            sender_id = data.get("senderId", "")
            if not call_id:
                raise ValueError("Missing callId")
            seq = _add_signal(call_id, sender_id, sig_type, payload)
            self._send_json({"status": "ok", "seq": seq})
        except Exception as e:
            self._send_json({"error": str(e)}, 400)

    def _handle_audio_push(self):
        try:
            data = json.loads(self._read_body().decode("utf-8"))
            call_id = data.get("callId")
            sender  = data.get("sender", "")
            seq     = int(data.get("seq", 0))
            pcm_b64 = data.get("data", "")
            if not call_id or not sender:
                raise ValueError("Missing callId or sender")
            with _lock:
                _cleanup_media()
                if call_id not in CALL_AUDIO:
                    CALL_AUDIO[call_id] = {}
                if sender not in CALL_AUDIO[call_id]:
                    CALL_AUDIO[call_id][sender] = deque(maxlen=AUDIO_LIMIT)
                CALL_AUDIO[call_id][sender].append((seq, pcm_b64))
                CALL_MEDIA_TS[call_id] = time.time()
            self._send_json({"status": "ok"})
        except Exception as e:
            self._send_json({"error": str(e)}, 400)

    def _handle_video_push(self):
        try:
            data = json.loads(self._read_body().decode("utf-8"))
            call_id  = data.get("callId")
            sender   = data.get("sender", "")
            seq      = int(data.get("seq", 0))
            jpeg_b64 = data.get("data", "")
            if not call_id or not sender:
                raise ValueError("Missing callId or sender")
            with _lock:
                _cleanup_media()
                if call_id not in CALL_VIDEO:
                    CALL_VIDEO[call_id] = {}
                CALL_VIDEO[call_id][sender] = (seq, jpeg_b64)
                CALL_MEDIA_TS[call_id] = time.time()
            self._send_json({"status": "ok"})
        except Exception as e:
            self._send_json({"error": str(e)}, 400)

    def _handle_db_write(self):
        try:
            req_json = json.loads(self._read_body().decode("utf-8"))
            payload_data = req_json.get("data", req_json)

            current_db = load_db()

            # Merge users
            existing_users = {u["id"]: u for u in current_db.get("users", [])}
            for u in payload_data.get("users", []):
                existing_users[u["id"]] = u
            current_db["users"] = list(existing_users.values())

            # Merge friend_requests
            existing_reqs = {r["id"]: r for r in current_db.get("friend_requests", [])}
            for r in payload_data.get("friend_requests", []):
                rid = r.get("id")
                if rid in existing_reqs:
                    curr_st = existing_reqs[rid].get("status", "PENDING")
                    new_st = r.get("status", "PENDING")
                    if curr_st in ("ACCEPTED", "DECLINED") and new_st == "PENDING":
                        continue
                existing_reqs[rid] = r
            current_db["friend_requests"] = list(existing_reqs.values())

            # Merge messages
            existing_msgs = {m["id"]: m for m in current_db.get("messages", [])}
            for m in payload_data.get("messages", []):
                mid = m.get("id")
                if mid in existing_msgs:
                    curr_st = existing_msgs[mid].get("status", "SENT")
                    new_st = m.get("status", "SENT")
                    if curr_st == "READ" and new_st != "READ":
                        continue
                existing_msgs[mid] = m
            current_db["messages"] = list(existing_msgs.values())

            # Merge blocked_users
            existing_blocks = {
                f"{b['blockerId']}_{b['blockedId']}": b
                for b in current_db.get("blocked_users", [])
            }
            for b in payload_data.get("blocked_users", []):
                existing_blocks[f"{b['blockerId']}_{b['blockedId']}"] = b
            current_db["blocked_users"] = list(existing_blocks.values())

            save_db(current_db)
            self._send_json({"name": "chatooz_cloud_sync_db", "data": current_db})
        except Exception as e:
            self._send_json({"error": str(e)}, 400)


# ─── High-Speed TCP Binary Media Relay (Port 8081) ────────────────────────────
# Clients connect via TCP and send:
# Packet: [1 byte type][4 bytes payload length (big-endian)][payload]
# Type 0x01: Handshake JSON: {"callId": "...", "userId": "..."}
# Type 0x02: Audio PCM chunk
# Type 0x03: Video JPEG frame
# Type 0x04: Ping/Keepalive

_media_lock = threading.Lock()
# {callId: {userId: socket}}
_call_peers = {}

def recv_exact(sock, n):
    buf = bytearray()
    while len(buf) < n:
        try:
            chunk = sock.recv(n - len(buf))
            if not chunk:
                return None
            buf.extend(chunk)
        except Exception:
            return None
    return bytes(buf)

def _handle_media_client(client_sock, client_addr):
    call_id = None
    user_id = None
    try:
        client_sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        while True:
            # Read header: 1 byte type, 4 bytes length
            header = recv_exact(client_sock, 5)
            if not header or len(header) < 5:
                break
            pkg_type, length = struct.unpack("!BI", header)
            if length > 2 * 1024 * 1024:  # Max 2MB safety
                break

            payload = recv_exact(client_sock, length)
            if payload is None or len(payload) < length:
                break

            # Handle packet
            if pkg_type == 0x01:
                # Handshake
                try:
                    meta = json.loads(payload.decode("utf-8"))
                    call_id = meta.get("callId")
                    user_id = meta.get("userId")
                    with _media_lock:
                        if call_id not in _call_peers:
                            _call_peers[call_id] = {}
                        _call_peers[call_id][user_id] = client_sock
                    # Send ack
                    ack = struct.pack("!BI", 0x01, 2) + b"OK"
                    client_sock.sendall(ack)
                except Exception as e:
                    print(f"Handshake error: {e}")
                    break

            elif pkg_type in (0x02, 0x03):
                # Audio (0x02) or Video (0x03) — Relay to other peers in this callId
                if call_id:
                    packet = header + payload
                    with _media_lock:
                        peers = list(_call_peers.get(call_id, {}).items())
                    for pid, psock in peers:
                        if pid != user_id:
                            try:
                                psock.sendall(packet)
                            except Exception:
                                pass

            elif pkg_type == 0x04:
                # Ping -> Pong
                pong = struct.pack("!BI", 0x04, 0)
                client_sock.sendall(pong)

    except Exception:
        pass
    finally:
        if call_id and user_id:
            with _media_lock:
                if call_id in _call_peers:
                    _call_peers[call_id].pop(user_id, None)
                    if not _call_peers[call_id]:
                        _call_peers.pop(call_id, None)
        try:
            client_sock.close()
        except Exception:
            pass



# ─── WebSocket Media Relay (replaces raw TCP) ─────────────────────────────────
# Android client connects via ws://127.0.0.1:8081 (dev) or wss://... (prod)
# Same binary packet format: [1 byte type][4 bytes big-endian length][payload]

_ws_peers_lock = asyncio.Lock() if HAS_WEBSOCKETS else None
_ws_peers_map: dict = {}  # {callId: {userId: ws}}

async def handle_ws_media(websocket):
    """WebSocket binary media relay handler."""
    call_id = None
    user_id = None
    try:
        async for message in websocket:
            if not isinstance(message, bytes) or len(message) < 5:
                continue
            pkg_type = message[0]
            import struct as _struct
            length = _struct.unpack("!I", message[1:5])[0]
            if length > 2 * 1024 * 1024:
                continue
            payload = message[5:5 + length]

            if pkg_type == 0x01:
                try:
                    import json as _json
                    meta = _json.loads(payload.decode("utf-8"))
                    call_id = meta.get("callId")
                    user_id = meta.get("userId")
                    async with asyncio.Lock():
                        if call_id not in _ws_peers_map:
                            _ws_peers_map[call_id] = {}
                        _ws_peers_map[call_id][user_id] = websocket
                    ack_body = b"OK"
                    ack = bytes([0x01]) + _struct.pack("!I", len(ack_body)) + ack_body
                    await websocket.send(ack)
                    print(f"[WS] Handshake: callId={call_id} userId={user_id}")
                except Exception as e:
                    print(f"[WS] Handshake error: {e}")
                    break
            elif pkg_type in (0x02, 0x03):
                if call_id:
                    peers = list(_ws_peers_map.get(call_id, {}).items())
                    for pid, psock in peers:
                        if pid != user_id:
                            try:
                                await psock.send(message)
                            except Exception:
                                pass
            elif pkg_type == 0x04:
                pong = bytes([0x04]) + struct.pack("!I", 0)
                await websocket.send(pong)
    except Exception as e:
        pass
    finally:
        if call_id and user_id:
            room = _ws_peers_map.get(call_id, {})
            room.pop(user_id, None)
            if not room:
                _ws_peers_map.pop(call_id, None)


def start_ws_media_relay(port=8081):
    """Start the WebSocket media relay server (dev replacement for raw TCP)."""
    if not HAS_WEBSOCKETS:
        print(f"[WS] websockets not available — WS relay not started on port {port}")
        return

    async def _serve():
        print(f"Chatooz WebSocket Media Relay running on ws://0.0.0.0:{port}")
        async with websockets.serve(handle_ws_media, "0.0.0.0", port, ping_interval=20):
            await asyncio.Future()

    asyncio.run(_serve())


# ─── Server Entry ─────────────────────────────────────────────────────────────
def run(port=8080):
    # Start WebSocket Media Relay in daemon thread (replaces old raw TCP relay)
    ws_thread = threading.Thread(target=start_ws_media_relay, args=(8081,), daemon=True)
    ws_thread.start()

    server_address = ("0.0.0.0", port)
    httpd = ThreadingHTTPServer(server_address, SyncHandler)
    print(f"Chatooz Dev Server running on http://0.0.0.0:{port}")
    print(f"  DB: {DB_FILE}")
    print(f"  HTTP: /sync, /call/signal, /call/audio, /call/video")
    print(f"  WebSocket Media Relay: ws://0.0.0.0:8081 (replaces raw TCP)")
    print(f"")
    print(f"  NOTE: For internet-wide deployment use server/chatooz_server.py")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    run(port)
