package com.chatooz.app.model

import kotlinx.serialization.Serializable

@Serializable
data class Status(
    val id: String,
    val userId: String,
    val userName: String,
    val userUsername: String,
    val userAvatarColor: Long = 0xFF6366F1L,
    val userAvatarUrl: String = "",
    val type: String = "TEXT", // "TEXT" or "IMAGE"
    val textContent: String = "",
    val bgGradientIndex: Int = 0,
    val mediaBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val viewers: List<String> = emptyList(),
    val likes: List<String> = emptyList()
)
