package com.chatooz.app.model

import kotlinx.serialization.Serializable

enum class MessageStatus { SENDING, SENT, DELIVERED, READ }
enum class MessageType   { TEXT, IMAGE, VIDEO, FILE, AUDIO, CALL }

@Serializable
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean,
    val status: String = "SENT",
    val type: String = "TEXT", // "TEXT", "IMAGE", "VIDEO", "FILE", "AUDIO"
    val audioDurationSec: Int? = null,
    val audioFilePath: String? = null,
    val audioBase64: String? = null,
    // Media & File sharing properties
    val mediaBase64: String? = null,
    val mediaFilePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val thumbnailBase64: String? = null,
    val isDeletedForEveryone: Boolean = false,
    val deletedForUsers: String? = null
)

