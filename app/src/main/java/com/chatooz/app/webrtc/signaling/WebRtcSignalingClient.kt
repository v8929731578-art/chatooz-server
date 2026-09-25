package com.chatooz.app.webrtc.signaling

import com.chatooz.app.webrtc.model.SignalingMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WebRtcSignalingClient {
    val incomingMessages: SharedFlow<SignalingMessage>
    val isConnected: StateFlow<Boolean>

    suspend fun connect(userId: String)
    suspend fun sendMessage(message: SignalingMessage): Boolean
    suspend fun disconnect()
}
