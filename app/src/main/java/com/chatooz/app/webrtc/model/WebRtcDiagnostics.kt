package com.chatooz.app.webrtc.model

import kotlinx.serialization.Serializable

@Serializable
data class WebRtcDiagnostics(
    val connectionState: String = "NEW",
    val iceConnectionState: String = "NEW",
    val iceGatheringState: String = "NEW",
    val signalingState: String = "STABLE",
    val localAudioTrackEnabled: Boolean = false,
    val localVideoTrackEnabled: Boolean = false,
    val remoteAudioTrackActive: Boolean = false,
    val remoteVideoTrackActive: Boolean = false,
    val activeCandidatePair: String = "None",
    val isTurnRelay: Boolean = false,
    val turnConfigured: Boolean = false,
    val stunConfigured: Boolean = true,
    val iceServersCount: Int = 0,
    val localHostCandidateCount: Int = 0,
    val localSrflxCandidateCount: Int = 0,
    val localRelayCandidateCount: Int = 0,
    val remoteHostCandidateCount: Int = 0,
    val remoteSrflxCandidateCount: Int = 0,
    val remoteRelayCandidateCount: Int = 0,
    val lastError: String? = null,
    val bytesReceived: Long = 0,
    val bytesSent: Long = 0
)
