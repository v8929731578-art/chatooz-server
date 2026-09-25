package com.chatooz.app.model

import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: String,
    val friendId: String,
    val friendName: String,
    val friendUsername: String,
    val friendAvatarColor: Long,
    val friendAvatarUrl: String? = null,
    val lastMessageText: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)
