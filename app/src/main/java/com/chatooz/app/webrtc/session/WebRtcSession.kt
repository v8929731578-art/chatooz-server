package com.chatooz.app.webrtc.session

import com.chatooz.app.webrtc.model.SignalingMessage
import com.chatooz.app.webrtc.model.WebRtcConfig
import com.chatooz.app.webrtc.model.WebRtcDiagnostics
import kotlinx.coroutines.flow.StateFlow

interface WebRtcSession {
    val sessionId: String
    val diagnostics: StateFlow<WebRtcDiagnostics>

    suspend fun initialize(config: WebRtcConfig)
    suspend fun createOffer(): SignalingMessage
    suspend fun handleRemoteOffer(offer: SignalingMessage): SignalingMessage
    suspend fun handleRemoteAnswer(answer: SignalingMessage)
    suspend fun addIceCandidate(candidate: SignalingMessage)
    suspend fun setAudioEnabled(enabled: Boolean)
    suspend fun setVideoEnabled(enabled: Boolean)
    suspend fun close()
}
