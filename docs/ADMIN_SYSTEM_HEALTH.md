# Chatooz Admin Panel: System Health & Call Diagnostics

This document provides a comprehensive technical reference for the **System Health & Call Diagnostics** observability platform introduced in Chatooz v6.0.

---

## 1. Overview & Architecture

The System Health & Call Diagnostics subsystem provides real-time telemetry, infrastructure monitoring, and evidence-based root cause analysis for WebRTC / WebSocket audio and video calling sessions without exposing sensitive user or infrastructure credentials.

```
┌─────────────────────────────────────────────────────────────┐
│                   Chatooz Admin Dashboard                   │
│         [🖥️ System Health]  [📞 Call Diagnostics]          │
└──────────────┬───────────────────────────────┬──────────────┘
               │ GET /admin/api/system-health   │ GET /admin/api/call-diagnostics
               ▼                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Chatooz Online Server (Python)              │
│  ┌─────────────────────────┐   ┌─────────────────────────┐  │
│  │ Watchdog Health Monitor │   │ Call Diagnostics Engine │  │
│  │ (CPU, RAM, Disk, DNS)   │   │ (Evidence & Timelines)  │  │
│  └─────────────────────────┘   └─────────────────────────┘  │
└──────────────┬───────────────────────────────▲──────────────┘
               │ SQLite WAL Storage            │ POST /api/call-diagnostics/event
               ▼                               │
┌─────────────────────────┐       ┌────────────┴──────────────┐
│ `system_events`         │       │ Chatooz Android App       │
│ `call_diagnostics`      │       │ (CallManager Telemetry)   │
└─────────────────────────┘       └───────────────────────────┘
```

---

## 2. Server Health Cards

The dashboard presents four primary live infrastructure status cards:

1. **Backend Server**:
   - Status: `🟢 ONLINE` / `🔴 OFFLINE`
   - Metrics: Uptime (formatted `Xh Ym Zs`), Response Time (benchmark in ms), Last Health Check, Last Restart.
2. **Cloudflare Tunnel**:
   - Status: `🟢 ONLINE` / `🔴 OFFLINE`
   - Metrics: Tunnel Health (`HEALTHY`/`DEGRADED`), Last Successful Public Endpoint Check, Reconnect Count, Egress Gateway Status.
   - *Security Note*: Public tunnel token/URL is masked and never exposed.
3. **Internet Connectivity**:
   - Status: `🟢 ONLINE` / `🔴 OFFLINE`
   - Metrics: Gateway DNS Socket Probe (`1.1.1.1:53` / `8.8.8.8:53`), Last State Change, Last Recovery, Packet Egress status.
4. **Watchdog Monitor**:
   - Status: `🟢 RUNNING` / `🔴 STOPPED`
   - Metrics: Watchdog Uptime, Process Restarts, Last Event Synced, Diagnostic Mode (`Passive Auto-Heal`).

---

## 3. Host Resource Monitoring

Live resource usage bars updated every 6 seconds:
- **CPU Usage %**: Real-time multi-core load from `psutil`.
- **Memory (RAM) %**: System virtual memory saturation.
- **Disk Storage %**: Disk usage of the server installation partition.

### Color-Coded Thresholds:
- **Green (`var(--emerald)`)**: < 70% (Healthy)
- **Yellow (`var(--amber)`)**: 70% – 85% (Moderate Load)
- **Red (`var(--rose)`)**: > 85% (High / Critical Load)

---

## 4. System Events Log

Maintains the 100 most recent system and infrastructure events with quick-filter pills:
- `ALL`: View all logs.
- `SERVER`: Server boot, port binds, query latencies.
- `TUNNEL`: Cloudflare tunnel connection and reconnect events.
- `NETWORK`: Internet drops, recoveries, and socket probes.
- `WATCHDOG`: Health loop heartbeats and manual pings.

---

## 5. Call Diagnostics & Failure Analysis

### Table Columns:
- **Call ID**: Unique call session identifier.
- **Participants**: Caller name & Callee name with call type (Audio / Video).
- **Duration**: Total call duration in seconds.
- **Result Badge**:
  - `🟢 COMPLETED`
  - `🟡 RECONNECTING`
  - `🔴 DISCONNECTED`
  - `🔴 FAILED`
  - `⚪ NOT TESTED`
- **Termination Reason Badge**:
  - `LOCAL_ENDED`, `REMOTE_ENDED`, `CALL_TIMEOUT`, `SIGNALING_DISCONNECTED`, `MEDIA_DISCONNECTED`, `SOCKET_ERROR`, `AUDIO_ERROR`, `VIDEO_ERROR`, `ICE_FAILED`, `NETWORK_ERROR`, `SERVER_ERROR`, `APP_BACKGROUND`, `UNKNOWN`.
- **Network & Media**: Network type (e.g. `WIFI`, `CELLULAR`) and ICE/Media state.
- **Diagnosis**: Primary failure/success reason and confidence level (`HIGH`, `MEDIUM`, `LOW`).
- **Actions**:
  - `🔍 View`: Opens interactive modal with evidence points and chronological event timeline.
  - `📥 JSON`: Downloads individual session report (`call_diagnostic_<callId>.json`).

---

## 6. Diagnostic JSON Report Schema

```json
{
  "export_time": 1727202100000,
  "call_id": "call_a1b2c3d4e5f6",
  "caller": {
    "id": "u_101",
    "name": "Alice"
  },
  "callee": {
    "id": "u_102",
    "name": "Bob"
  },
  "call_type": "AUDIO",
  "duration_seconds": 45,
  "result": "COMPLETED",
  "termination_reason": "LOCAL_ENDED",
  "network": {
    "type": "WIFI",
    "ice_state": "CONNECTED",
    "media_state": "CONNECTED"
  },
  "diagnosis": {
    "primary_reason": "Normal Call Termination by User",
    "confidence": "HIGH",
    "evidence": [
      "Call connected successfully and active for 45s",
      "Graceful teardown initiated (LOCAL_ENDED)",
      "Network interface: WIFI, ICE state: CONNECTED"
    ]
  },
  "events_timeline": [
    {
      "event": "CALL_OFFER_SENT",
      "status": "INFO",
      "details": "Offer signal initiated by Alice",
      "timestamp": 1727202055000
    },
    {
      "event": "CALL_ANSWERED",
      "status": "INFO",
      "details": "Answer signal accepted by u_102",
      "timestamp": 1727202057000
    },
    {
      "event": "CALL_TERMINATED_ENDED",
      "status": "INFO",
      "details": "Call ended normally",
      "timestamp": 1727202100000
    }
  ],
  "created_at": 1727202055000,
  "updated_at": 1727202100000
}
```

---

## 7. Security & Privacy Guardrails

- All endpoints require admin authentication via `X-Admin-Token`, cookie `chatooz_admin_session`, or Bearer authorization header.
- Unauthenticated requests receive `401 Unauthorized`.
- Private phone numbers, passwords, OTP keys, admin session tokens, and raw media frames are strictly excluded from telemetry logs and exports.
