package com.chatooz.app.model

import kotlinx.serialization.Serializable

@Serializable
data class GroupMember(
    val userId: String,
    val role: String = "MEMBER", // "ADMIN", "MEMBER"
    val joinedAt: Long = System.currentTimeMillis(),
    val userName: String? = null,
    val username: String? = null,
    val avatarColor: Long? = null
)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val description: String = "",
    val creatorId: String,
    val avatarColor: Long = 0xFF6366F1L,
    val createdAt: Long = System.currentTimeMillis(),
    val members: List<GroupMember> = emptyList()
)
