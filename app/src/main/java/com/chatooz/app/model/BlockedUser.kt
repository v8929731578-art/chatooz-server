package com.chatooz.app.model

import kotlinx.serialization.Serializable

@Serializable
data class BlockedUser(
    val blockerId: String,          // user who blocked
    val blockedId: String,          // user who was blocked
    val blockedUsername: String,    // @handle of the blocked user
    val blockedName: String = "",   // display name of the blocked user
    val timestamp: Long = System.currentTimeMillis()
)
