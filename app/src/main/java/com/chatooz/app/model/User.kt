package com.chatooz.app.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val username: String,          // unique handle e.g. "chitra_dev"
    val email: String = "",        // gmail address
    val phone: String = "",        // phone number (optional/normalized)
    val address: String = "",      // location / physical address (optional)
    val avatarColor: Long = 0xFF6366F1,
    val avatarUrl: String? = null,
    val bio: String = "Hey, I'm on Chatooz! 🚀",
    val createdAt: Long = System.currentTimeMillis()
)
