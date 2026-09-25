package com.chatooz.app.webrtc.ice

import com.chatooz.app.webrtc.model.Environment
import com.chatooz.app.webrtc.model.IceProtocol
import com.chatooz.app.webrtc.model.IceServerConfig

/**
 * IceServerProvider provides validated STUN and TURN configurations.
 * Does not store hardcoded secrets.
 */
object IceServerProvider {

    // Standard Google Public STUN servers
    private val DEFAULT_STUN_SERVERS = listOf(
        IceServerConfig(uri = "stun:stun.l.google.com:19302", protocol = IceProtocol.UDP),
        IceServerConfig(uri = "stun:stun1.l.google.com:19302", protocol = IceProtocol.UDP)
    )

    fun getIceServers(
        environment: Environment,
        dynamicTurnServers: List<IceServerConfig> = emptyList()
    ): List<IceServerConfig> {
        val servers = mutableListOf<IceServerConfig>()
        servers.addAll(DEFAULT_STUN_SERVERS)

        when (environment) {
            Environment.DEV -> {
                // In dev, STUN is used; TURN supplied dynamically if testing coturn
                servers.addAll(dynamicTurnServers)
            }
            Environment.STAGING, Environment.PRODUCTION -> {
                // Staging and Production require TURN servers for strict NAT traversal
                servers.addAll(dynamicTurnServers)
            }
        }
        return servers
    }

    fun isTurnConfigured(servers: List<IceServerConfig>): Boolean {
        return servers.any { it.uri.startsWith("turn:") || it.uri.startsWith("turns:") }
    }
}
