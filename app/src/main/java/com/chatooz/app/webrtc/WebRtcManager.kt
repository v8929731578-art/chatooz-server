package com.chatooz.app.webrtc

import com.chatooz.app.webrtc.ice.IceServerProvider
import com.chatooz.app.webrtc.model.Environment
import com.chatooz.app.webrtc.model.WebRtcConfig
import com.chatooz.app.webrtc.model.WebRtcDiagnostics
import com.chatooz.app.webrtc.session.WebRtcSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WebRtcManager — Top-level entry point for WebRTC initialization, configuration, and diagnostics.
 * Preserves clean abstraction without breaking legacy CallManager.
 */
object WebRtcManager {

    private val _config = MutableStateFlow(WebRtcConfig())
    val config: StateFlow<WebRtcConfig> = _config.asStateFlow()

    private val _diagnostics = MutableStateFlow(WebRtcDiagnostics())
    val diagnostics: StateFlow<WebRtcDiagnostics> = _diagnostics.asStateFlow()

    private val _currentSession = MutableStateFlow<WebRtcSession?>(null)
    val currentSession: StateFlow<WebRtcSession?> = _currentSession.asStateFlow()

    fun configure(
        environment: Environment = Environment.DEV,
        enableAudio: Boolean = true,
        enableVideo: Boolean = true
    ) {
        val iceServers = IceServerProvider.getIceServers(environment)
        _config.value = WebRtcConfig(
            environment = environment,
            iceServers = iceServers,
            enableAudio = enableAudio,
            enableVideo = enableVideo
        )
    }

    fun updateDiagnostics(updater: (WebRtcDiagnostics) -> WebRtcDiagnostics) {
        _diagnostics.value = updater(_diagnostics.value)
    }

    fun setCurrentSession(session: WebRtcSession?) {
        _currentSession.value = session
    }
}
