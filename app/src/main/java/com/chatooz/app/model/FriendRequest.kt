package com.chatooz.app.model

import kotlinx.serialization.Serializable

enum class RequestStatus { PENDING, ACCEPTED, DECLINED }

@Serializable
data class FriendRequest(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val senderName: String,
    val senderAvatarColor: Long,
    val senderAvatarUrl: String? = null,
    val receiverId: String,
    val receiverUsername: String,
    val receiverName: String = "",
    val receiverAvatarColor: Long = 0xFF6366F1L,
    val receiverAvatarUrl: String? = null,
    val status: String = "PENDING",   // Using String to be easily serializable
    val timestamp: Long = System.currentTimeMillis()
)
