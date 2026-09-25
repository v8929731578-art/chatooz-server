package com.chatooz.app.webrtc.model

import kotlinx.serialization.Serializable

enum class Environment {
    DEV,
    STAGING,
    PRODUCTION
}

enum class IceProtocol {
    UDP,
    TCP,
    TLS
}

@Serializable
data class IceServerConfig(
    val uri: String,
    val username: String? = null,
    val credential: String? = null,
    val protocol: IceProtocol = IceProtocol.UDP
)

@Serializable
data class WebRtcConfig(
    val environment: Environment = Environment.DEV,
    val iceServers: List<IceServerConfig> = emptyList(),
    val enableAudio: Boolean = true,
    val enableVideo: Boolean = true,
    val enableHardwareAcceleration: Boolean = true,
    val preferredVideoCodec: String = "VP8",
    val preferredAudioCodec: String = "opus"
)
