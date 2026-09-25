package com.chatooz.app.webrtc.realtime

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed class RealtimeEvent {
    data class Connected(val sessionId: String) : RealtimeEvent()
    data class Disconnected(val reason: String) : RealtimeEvent()
    data class Error(val throwable: Throwable) : RealtimeEvent()
    data class CustomPayload(val type: String, val payload: String) : RealtimeEvent()
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

interface RealtimeConnection {
    val connectionState: StateFlow<ConnectionState>
    val events: SharedFlow<RealtimeEvent>

    suspend fun connect(endpoint: String, authToken: String?)
    suspend fun send(type: String, payload: String): Boolean
    suspend fun disconnect()
}
