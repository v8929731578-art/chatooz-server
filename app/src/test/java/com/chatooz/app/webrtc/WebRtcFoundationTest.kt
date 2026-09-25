package com.chatooz.app.webrtc

import com.chatooz.app.webrtc.ice.IceServerProvider
import com.chatooz.app.webrtc.model.Environment
import com.chatooz.app.webrtc.model.IceProtocol
import com.chatooz.app.webrtc.model.IceServerConfig
import com.chatooz.app.webrtc.model.SignalingMessage
import com.chatooz.app.webrtc.model.SignalingType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcFoundationTest {

    @Test
    fun testIceServerProviderDefaults() {
        val servers = IceServerProvider.getIceServers(Environment.DEV)
        assertTrue(servers.isNotEmpty())
        assertTrue(servers.any { it.uri.contains("stun.l.google.com") })
        assertFalse(IceServerProvider.isTurnConfigured(servers))
    }

    @Test
    fun testIceServerProviderWithDynamicTurn() {
        val customTurn = listOf(
            IceServerConfig(
                uri = "turn:turn.chatooz.com:3478",
                username = "testUser",
                credential = "testPassword",
                protocol = IceProtocol.UDP
            )
        )
        val servers = IceServerProvider.getIceServers(Environment.PRODUCTION, customTurn)
        assertTrue(servers.size >= 3)
        assertTrue(IceServerProvider.isTurnConfigured(servers))
    }

    @Test
    fun testSignalingMessageSerialization() {
        val msg = SignalingMessage(
            callId = "call_12345",
            senderId = "user_A",
            receiverId = "user_B",
            type = SignalingType.OFFER,
            payload = "{\"sdp\":\"v=0...\"}",
            timestamp = 1790000000000L
        )
        val jsonStr = Json.encodeToString(SignalingMessage.serializer(), msg)
        val deserialized = Json.decodeFromString(SignalingMessage.serializer(), jsonStr)

        assertEquals(msg.callId, deserialized.callId)
        assertEquals(msg.senderId, deserialized.senderId)
        assertEquals(msg.receiverId, deserialized.receiverId)
        assertEquals(SignalingType.OFFER, deserialized.type)
        assertEquals(msg.payload, deserialized.payload)
    }

    @Test
    fun testAnswerAndIceCandidateMessageSerialization() {
        val ans = SignalingMessage(
            callId = "call_999",
            senderId = "user_B",
            receiverId = "user_A",
            type = SignalingType.ANSWER,
            payload = "{\"type\":\"answer\",\"sdp\":\"v=0...\"}"
        )
        val ansJson = Json.encodeToString(SignalingMessage.serializer(), ans)
        val decodedAns = Json.decodeFromString(SignalingMessage.serializer(), ansJson)
        assertEquals(SignalingType.ANSWER, decodedAns.type)

        val cand = SignalingMessage(
            callId = "call_999",
            senderId = "user_A",
            receiverId = "user_B",
            type = SignalingType.ICE_CANDIDATE,
            payload = "{\"sdpMid\":\"0\",\"sdpMLineIndex\":0,\"sdp\":\"candidate:1 1 UDP...\"}"
        )
        val candJson = Json.encodeToString(SignalingMessage.serializer(), cand)
        val decodedCand = Json.decodeFromString(SignalingMessage.serializer(), candJson)
        assertEquals(SignalingType.ICE_CANDIDATE, decodedCand.type)
    }
}
