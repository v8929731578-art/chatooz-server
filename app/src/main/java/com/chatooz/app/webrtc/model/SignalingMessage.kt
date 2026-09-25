package com.chatooz.app.webrtc.model

import kotlinx.serialization.Serializable

@Serializable
enum class SignalingType {
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    RENEGOTIATE,
    BUSY,
    REJECT,
    END
}

@Serializable
data class SignalingMessage(
    val callId: String,
    val senderId: String,
    val receiverId: String,
    val type: SignalingType,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)
