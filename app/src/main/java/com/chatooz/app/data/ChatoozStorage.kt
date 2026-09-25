package com.chatooz.app.data

import android.content.Context
import android.content.SharedPreferences
import com.chatooz.app.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * ChatoozStorage — persists local app state in SharedPreferences
 * and synchronizes with ChatoozCloudApi for multi-device support.
 */
class ChatoozStorage(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chatooz_storage", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // ─── Session ────────────────────────────────────────────────────
    fun getSessionUserId(): String? = prefs.getString("session_user_id", null)

    fun setSessionUserId(userId: String?) {
        prefs.edit().apply {
            if (userId == null) remove("session_user_id") else putString("session_user_id", userId)
        }.apply()
    }

    // ─── Users ──────────────────────────────────────────────────────
    fun getAllUsers(): List<User> {
        val raw = prefs.getString("users", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
    }

    fun saveAllUsers(users: List<User>) {
        prefs.edit().putString("users", json.encodeToString(users)).apply()
    }

    fun getUserById(id: String): User? = getAllUsers().find { it.id == id }

    fun getUserByEmail(email: String): User? =
        getAllUsers().find { it.email.lowercase() == email.lowercase() }

    fun getUserByUsername(username: String): User? =
        getAllUsers().find { it.username.lowercase() == username.lowercase() }

    fun isUsernameTaken(username: String): Boolean =
        getAllUsers().any { it.username.lowercase() == username.lowercase() }

    fun isEmailRegistered(email: String): Boolean =
        getAllUsers().any { it.email.lowercase() == email.lowercase() }

    fun upsertUser(user: User) {
        val current = getAllUsers().toMutableList()
        val idx = current.indexOfFirst { it.id == user.id || it.username.lowercase() == user.username.lowercase() }
        if (idx >= 0) current[idx] = user else current.add(user)
        saveAllUsers(current)
    }

    // ─── Friend Requests ────────────────────────────────────────────
    fun getAllFriendRequests(): List<FriendRequest> {
        val raw = prefs.getString("friend_requests", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
    }

    fun saveAllFriendRequests(requests: List<FriendRequest>) {
        prefs.edit().putString("friend_requests", json.encodeToString(requests)).apply()
    }

    fun getIncomingRequests(userId: String): List<FriendRequest> =
        getAllFriendRequests().filter { it.receiverId == userId && it.status == "PENDING" && !isAnyBlocked(userId, it.senderId) }

    fun getOutgoingRequests(userId: String): List<FriendRequest> =
        getAllFriendRequests().filter { it.senderId == userId && it.status == "PENDING" }

    fun sendFriendRequest(request: FriendRequest) {
        val current = getAllFriendRequests().toMutableList()
        // Avoid duplicate
        val existing = current.find {
            (it.senderId == request.senderId && it.receiverId == request.receiverId) ||
            (it.senderId == request.receiverId && it.receiverId == request.senderId)
        }
        if (existing == null) {
            current.add(request)
            saveAllFriendRequests(current)
        }
    }

    fun updateRequestStatus(requestId: String, newStatus: String) {
        val current = getAllFriendRequests().toMutableList()
        val idx = current.indexOfFirst { it.id == requestId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(status = newStatus)
            saveAllFriendRequests(current)
        }
    }

    fun areFriends(userId1: String, userId2: String): Boolean {
        if (isAnyBlocked(userId1, userId2)) return false
        return getAllFriendRequests().any {
            it.status == "ACCEPTED" &&
            ((it.senderId == userId1 && it.receiverId == userId2) ||
             (it.senderId == userId2 && it.receiverId == userId1))
        }
    }

    fun hasPendingRequest(fromId: String, toId: String): Boolean {
        return getAllFriendRequests().any {
            it.status == "PENDING" && it.senderId == fromId && it.receiverId == toId
        }
    }

    // ─── Friends List (accepted) ─────────────────────────────────────
    fun getFriends(userId: String): List<User> {
        val allReqs = getAllFriendRequests()
        val acceptedReqs = allReqs.filter { it.status == "ACCEPTED" && (it.senderId == userId || it.receiverId == userId) }
        val allUsers = getAllUsers()

        val friends = mutableListOf<User>()
        for (req in acceptedReqs) {
            val friendId = if (req.senderId == userId) req.receiverId else req.senderId
            val friendUsername = if (req.senderId == userId) req.receiverUsername else req.senderUsername
            if (isAnyBlocked(userId, friendId)) continue

            val user = allUsers.find { it.id == friendId || it.username.equals(friendUsername, ignoreCase = true) }
                ?: User(
                    id = friendId,
                    name = if (req.senderId == userId) (if (req.receiverName.isNotBlank()) req.receiverName else req.receiverUsername) else req.senderName,
                    username = friendUsername,
                    avatarColor = if (req.senderId == userId) req.receiverAvatarColor else req.senderAvatarColor,
                    avatarUrl = if (req.senderId == userId) req.receiverAvatarUrl else req.senderAvatarUrl
                )
            if (friends.none { it.id == user.id }) {
                friends.add(user)
            }
        }
        return friends
    }

    // ─── Blocked Users ──────────────────────────────────────────────
    fun getAllBlockedUsers(): List<BlockedUser> {
        val raw = prefs.getString("blocked_users", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
    }

    fun saveAllBlockedUsers(blocked: List<BlockedUser>) {
        prefs.edit().putString("blocked_users", json.encodeToString(blocked)).apply()
    }

    fun getBlockedUsers(myUserId: String): List<BlockedUser> =
        getAllBlockedUsers().filter { it.blockerId == myUserId }

    fun blockUser(blockerId: String, blockedId: String, blockedUsername: String, blockedName: String = "") {
        val current = getAllBlockedUsers().toMutableList()
        val existing = current.find { it.blockerId == blockerId && it.blockedId == blockedId }
        if (existing == null) {
            current.add(BlockedUser(blockerId, blockedId, blockedUsername, blockedName))
            saveAllBlockedUsers(current)
        }
    }

    fun unblockUser(blockerId: String, blockedId: String) {
        val current = getAllBlockedUsers().toMutableList()
        current.removeAll { it.blockerId == blockerId && it.blockedId == blockedId }
        saveAllBlockedUsers(current)
    }

    fun isUserBlocked(blockerId: String, blockedId: String): Boolean =
        getAllBlockedUsers().any { it.blockerId == blockerId && it.blockedId == blockedId }

    fun isBlockedBy(myId: String, otherId: String): Boolean =
        getAllBlockedUsers().any { it.blockerId == otherId && it.blockedId == myId }

    fun isAnyBlocked(user1: String, user2: String): Boolean =
        isUserBlocked(user1, user2) || isUserBlocked(user2, user1)

    // ─── Chats ──────────────────────────────────────────────────────
    fun getChats(userId: String): List<Chat> {
        val raw = prefs.getString("chats_$userId", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
    }

    fun saveChats(userId: String, chats: List<Chat>) {
        prefs.edit().putString("chats_$userId", json.encodeToString(chats)).apply()
    }

    fun upsertChat(userId: String, chat: Chat) {
        val chats = getChats(userId).toMutableList()
        val idx = chats.indexOfFirst { it.id == chat.id }
        if (idx >= 0) chats[idx] = chat else chats.add(0, chat)
        val sorted = chats.sortedByDescending { it.lastMessageTime }
        saveChats(userId, sorted)
    }

    fun chatIdFor(userId1: String, userId2: String): String {
        val sorted = listOf(userId1, userId2).sorted()
        return "chat_${sorted[0]}_${sorted[1]}"
    }

    // ─── Deleted Messages Tombstones ─────────────────────────────────
    fun getDeletedMessageIds(): Set<String> {
        val raw = prefs.getString("deleted_message_ids", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptySet() }
    }

    fun addDeletedMessageId(messageId: String) {
        val current = getDeletedMessageIds().toMutableSet()
        current.add(messageId)
        prefs.edit().putString("deleted_message_ids", json.encodeToString(current)).apply()
    }

    fun addDeletedMessageIds(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        val current = getDeletedMessageIds().toMutableSet()
        current.addAll(messageIds)
        prefs.edit().putString("deleted_message_ids", json.encodeToString(current)).apply()
    }

    // ─── Messages ───────────────────────────────────────────────────
    fun getAllMessages(): List<Message> {
        val raw = prefs.getString("all_messages", "[]") ?: "[]"
        val deletedIds = getDeletedMessageIds()
        val myUserId = getSessionUserId()
        val all: List<Message> = try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
        return all.filter { msg ->
            !deletedIds.contains(msg.id) &&
            !(myUserId != null && msg.deletedForUsers?.split(",")?.map { it.trim() }?.contains(myUserId) == true)
        }
    }

    fun saveAllMessages(messages: List<Message>) {
        prefs.edit().putString("all_messages", json.encodeToString(messages)).apply()
    }

    fun getMessages(chatId: String): List<Message> {
        val raw = prefs.getString("messages_$chatId", "[]") ?: "[]"
        val deletedIds = getDeletedMessageIds()
        val myUserId = getSessionUserId()
        val list: List<Message> = try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
        val filtered = list.filter { msg ->
            !deletedIds.contains(msg.id) &&
            !(myUserId != null && msg.deletedForUsers?.split(",")?.map { it.trim() }?.contains(myUserId) == true)
        }
        if (filtered.isNotEmpty()) return filtered
        // Fallback to all_messages
        return getAllMessages().filter { it.chatId == chatId }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        deleteMessageForMe(chatId, messageId, getSessionUserId() ?: "")
    }

    fun deleteMessageForMe(chatId: String, messageId: String, myUserId: String) {
        addDeletedMessageId(messageId)
        // Remove from chat-specific store
        val msgs = getMessages(chatId).filter { it.id != messageId }
        prefs.edit().putString("messages_$chatId", json.encodeToString(msgs)).apply()
        // Remove from all_messages store
        val all = getAllMessages().filter { it.id != messageId }
        saveAllMessages(all)
    }

    fun deleteMessageForEveryone(chatId: String, messageId: String) {
        val msgs = getMessages(chatId).map { msg ->
            if (msg.id == messageId) {
                msg.copy(
                    isDeletedForEveryone = true,
                    text = "🚫 This message was deleted",
                    audioBase64 = null,
                    mediaBase64 = null,
                    thumbnailBase64 = null,
                    audioFilePath = null,
                    mediaFilePath = null
                )
            } else msg
        }
        prefs.edit().putString("messages_$chatId", json.encodeToString(msgs)).apply()

        val all = getAllMessages().map { msg ->
            if (msg.id == messageId) {
                msg.copy(
                    isDeletedForEveryone = true,
                    text = "🚫 This message was deleted",
                    audioBase64 = null,
                    mediaBase64 = null,
                    thumbnailBase64 = null,
                    audioFilePath = null,
                    mediaFilePath = null
                )
            } else msg
        }
        saveAllMessages(all)
    }

    fun addMessage(chatId: String, message: Message) {
        var msgToSave = message
        // Cache audio file if not local
        if (msgToSave.type == "AUDIO" && !msgToSave.audioBase64.isNullOrBlank() && (msgToSave.audioFilePath.isNullOrBlank() || !java.io.File(msgToSave.audioFilePath).exists())) {
            try {
                val voiceDir = java.io.File(context.filesDir, "voice_msgs")
                if (!voiceDir.exists()) voiceDir.mkdirs()
                val audioFile = java.io.File(voiceDir, "voice_${msgToSave.id}.m4a")
                val audioBytes = android.util.Base64.decode(msgToSave.audioBase64, android.util.Base64.NO_WRAP)
                audioFile.writeBytes(audioBytes)
                msgToSave = msgToSave.copy(audioFilePath = audioFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Cache media / file if not local
        if (msgToSave.type in listOf("IMAGE", "VIDEO", "FILE") && !msgToSave.mediaBase64.isNullOrBlank() && (msgToSave.mediaFilePath.isNullOrBlank() || !java.io.File(msgToSave.mediaFilePath).exists())) {
            try {
                val mediaDir = java.io.File(context.filesDir, "chat_media")
                if (!mediaDir.exists()) mediaDir.mkdirs()
                val safeExt = when (msgToSave.type) {
                    "IMAGE" -> "jpg"
                    "VIDEO" -> "mp4"
                    else -> msgToSave.fileName?.substringAfterLast('.', "bin") ?: "bin"
                }
                val mediaFile = java.io.File(mediaDir, "media_${msgToSave.id}.$safeExt")
                val mediaBytes = android.util.Base64.decode(msgToSave.mediaBase64, android.util.Base64.NO_WRAP)
                mediaFile.writeBytes(mediaBytes)
                msgToSave = msgToSave.copy(mediaFilePath = mediaFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Update chat specific
        val messages = getMessages(chatId).toMutableList()
        val existingIdx = messages.indexOfFirst { it.id == msgToSave.id }
        if (existingIdx >= 0) {
            messages[existingIdx] = msgToSave
        } else {
            messages.add(msgToSave)
        }
        prefs.edit().putString("messages_$chatId", json.encodeToString(messages)).apply()

        // Update all_messages
        val all = getAllMessages().toMutableList()
        val allIdx = all.indexOfFirst { it.id == msgToSave.id }
        if (allIdx >= 0) {
            all[allIdx] = msgToSave
        } else {
            all.add(msgToSave)
        }
        saveAllMessages(all)
    }

    fun markMessagesRead(chatId: String, myUserId: String) {
        var changed = false
        val currentMsgs = getMessages(chatId)
        val updatedMsgs = currentMsgs.map { msg ->
            if (msg.senderId != myUserId && msg.status != "READ") {
                changed = true
                msg.copy(status = "READ")
            } else {
                msg
            }
        }
        if (changed) {
            prefs.edit().putString("messages_$chatId", json.encodeToString(updatedMsgs)).apply()
        }

        val allMsgs = getAllMessages().toMutableList()
        var allChanged = false
        for (i in allMsgs.indices) {
            val msg = allMsgs[i]
            if (msg.chatId == chatId && msg.senderId != myUserId && msg.status != "READ") {
                allMsgs[i] = msg.copy(status = "READ")
                allChanged = true
            }
        }
        if (allChanged) {
            saveAllMessages(allMsgs)
        }

        // Also immediately reset unreadCount to 0 for this chat in local chats list
        val chats = getChats(myUserId).toMutableList()
        val cIdx = chats.indexOfFirst { it.id == chatId }
        if (cIdx >= 0 && chats[cIdx].unreadCount > 0) {
            chats[cIdx] = chats[cIdx].copy(unreadCount = 0)
            saveChats(myUserId, chats)
        }
    }

    // ─── Multi-Device Cloud Sync ────────────────────────────────────
    suspend fun syncWithCloud(): Boolean {
        return try {
            val localUsers = getAllUsers().toMutableList()
            val localRequests = getAllFriendRequests().toMutableList()
            val localBlocked = getAllBlockedUsers().toMutableList()
            val deletedIdsAtStart = getDeletedMessageIds()
            val localMessages = getAllMessages().filter { !deletedIdsAtStart.contains(it.id) }.toMutableList()

            val localPayload = CloudDbPayload(
                users = localUsers,
                friend_requests = localRequests,
                messages = localMessages,
                blocked_users = localBlocked
            )

            // Fast push: returns updated merged cloud state in a single round-trip
            var remotePayload = ChatoozCloudApi.pushCloudData(localPayload)
            var pushedMerged = true
            if (remotePayload == null) {
                // Fallback to GET
                remotePayload = ChatoozCloudApi.fetchCloudData()
                pushedMerged = false
            }

            if (remotePayload != null) {
                val deletedUserIds = remotePayload.deleted_user_ids.toSet()
                val deletedMsgIds = remotePayload.deleted_message_ids.toSet()

                // Purge messages marked deleted on server
                if (deletedMsgIds.isNotEmpty()) {
                    addDeletedMessageIds(deletedMsgIds)
                    localMessages.removeAll { deletedMsgIds.contains(it.id) }
                }

                // 1. Merge and purge users (preserve non-blank local details if remote returns blank)
                val remoteUsers = remotePayload.users.filter { !deletedUserIds.contains(it.id) }
                val updatedLocalUsers = mutableListOf<User>()
                val mySessionId = getSessionUserId()
                for (ru in remoteUsers) {
                    val existingLocal = localUsers.find { it.id == ru.id }
                    if (existingLocal != null) {
                        val mergedUser = ru.copy(
                            name = if (ru.name.isNotBlank()) ru.name else existingLocal.name,
                            phone = if (ru.phone.isNotBlank()) ru.phone else existingLocal.phone,
                            address = if (ru.address.isNotBlank()) ru.address else existingLocal.address,
                            bio = if (ru.bio.isNotBlank()) ru.bio else existingLocal.bio,
                            avatarUrl = if (!ru.avatarUrl.isNullOrBlank()) ru.avatarUrl else existingLocal.avatarUrl
                        )
                        updatedLocalUsers.add(mergedUser)
                    } else {
                        updatedLocalUsers.add(ru)
                    }
                }
                saveAllUsers(updatedLocalUsers)

                // 2. Merge friend requests (filter out deleted users)
                val remoteRequests = remotePayload.friend_requests.filter { 
                    !deletedUserIds.contains(it.senderId) && !deletedUserIds.contains(it.receiverId) 
                }
                for (rr in remoteRequests) {
                    val idx = localRequests.indexOfFirst { it.id == rr.id }
                    if (idx >= 0) {
                        val localStatus = localRequests[idx].status
                        val remoteStatus = rr.status
                        if (remoteStatus in listOf("ACCEPTED", "DECLINED")) {
                            localRequests[idx] = rr
                        } else if (localStatus == "PENDING") {
                            localRequests[idx] = rr
                        }
                    } else {
                        localRequests.add(rr)
                    }
                }
                localRequests.removeAll { deletedUserIds.contains(it.senderId) || deletedUserIds.contains(it.receiverId) }
                saveAllFriendRequests(localRequests)

                // 3. Merge blocked users
                val remoteBlocked = remotePayload.blocked_users.filter { 
                    !deletedUserIds.contains(it.blockerId) && !deletedUserIds.contains(it.blockedId) 
                }
                for (rb in remoteBlocked) {
                    val exists = localBlocked.any { it.blockerId == rb.blockerId && it.blockedId == rb.blockedId }
                    if (!exists) {
                        localBlocked.add(rb)
                    }
                }
                localBlocked.removeAll { deletedUserIds.contains(it.blockerId) || deletedUserIds.contains(it.blockedId) }
                saveAllBlockedUsers(localBlocked)

                // 4. Merge messages
                val remoteMessages = remotePayload.messages
                val allDeletedIds = getDeletedMessageIds()
                val currentUserId = getSessionUserId()
                for (rm in remoteMessages) {
                    if (allDeletedIds.contains(rm.id) || deletedMsgIds.contains(rm.id)) continue
                    if (currentUserId != null && rm.deletedForUsers?.split(",")?.map { it.trim() }?.contains(currentUserId) == true) continue

                    var messageToStore = rm
                    if (rm.isDeletedForEveryone) {
                        messageToStore = messageToStore.copy(
                            isDeletedForEveryone = true,
                            text = "🚫 This message was deleted",
                            audioBase64 = null,
                            mediaBase64 = null,
                            thumbnailBase64 = null,
                            audioFilePath = null,
                            mediaFilePath = null
                        )
                    } else if (rm.type == "AUDIO" && !rm.audioBase64.isNullOrBlank()) {
                        val currentLocal = localMessages.find { it.id == rm.id }
                        val hasValidLocalFile = currentLocal?.audioFilePath?.let { java.io.File(it).exists() } == true
                        if (!hasValidLocalFile) {
                            try {
                                val voiceDir = java.io.File(context.filesDir, "voice_msgs")
                                if (!voiceDir.exists()) voiceDir.mkdirs()
                                val audioFile = java.io.File(voiceDir, "voice_${rm.id}.m4a")
                                val audioBytes = android.util.Base64.decode(rm.audioBase64, android.util.Base64.NO_WRAP)
                                audioFile.writeBytes(audioBytes)
                                messageToStore = messageToStore.copy(audioFilePath = audioFile.absolutePath)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else if (currentLocal != null) {
                            messageToStore = messageToStore.copy(audioFilePath = currentLocal.audioFilePath)
                        }
                    } else if (rm.type in listOf("IMAGE", "VIDEO", "FILE") && !rm.mediaBase64.isNullOrBlank()) {
                        val currentLocal = localMessages.find { it.id == rm.id }
                        val hasValidLocalFile = currentLocal?.mediaFilePath?.let { java.io.File(it).exists() } == true
                        if (!hasValidLocalFile) {
                            try {
                                val mediaDir = java.io.File(context.filesDir, "chat_media")
                                if (!mediaDir.exists()) mediaDir.mkdirs()
                                val safeExt = when (rm.type) {
                                    "IMAGE" -> "jpg"
                                    "VIDEO" -> "mp4"
                                    else -> rm.fileName?.substringAfterLast('.', "bin") ?: "bin"
                                }
                                val mediaFile = java.io.File(mediaDir, "media_${rm.id}.$safeExt")
                                val mediaBytes = android.util.Base64.decode(rm.mediaBase64, android.util.Base64.NO_WRAP)
                                mediaFile.writeBytes(mediaBytes)
                                messageToStore = messageToStore.copy(mediaFilePath = mediaFile.absolutePath)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else if (currentLocal != null) {
                            messageToStore = messageToStore.copy(mediaFilePath = currentLocal.mediaFilePath)
                        }
                    }

                    val idx = localMessages.indexOfFirst { it.id == messageToStore.id }
                    if (idx >= 0) {
                        if (messageToStore.isDeletedForEveryone && !localMessages[idx].isDeletedForEveryone) {
                            localMessages[idx] = localMessages[idx].copy(
                                isDeletedForEveryone = true,
                                text = "🚫 This message was deleted",
                                audioBase64 = null,
                                mediaBase64 = null,
                                thumbnailBase64 = null,
                                audioFilePath = null,
                                mediaFilePath = null
                            )
                        }
                        if (messageToStore.status == "READ" && localMessages[idx].status != "READ") {
                            localMessages[idx] = localMessages[idx].copy(status = "READ")
                        }
                        if (localMessages[idx].audioFilePath.isNullOrBlank() && !messageToStore.audioFilePath.isNullOrBlank()) {
                            localMessages[idx] = localMessages[idx].copy(audioFilePath = messageToStore.audioFilePath)
                        }
                        if (localMessages[idx].mediaFilePath.isNullOrBlank() && !messageToStore.mediaFilePath.isNullOrBlank()) {
                            localMessages[idx] = localMessages[idx].copy(mediaFilePath = messageToStore.mediaFilePath)
                        }
                    } else {
                        localMessages.add(messageToStore)
                    }
                }
                val finalCleanMessages = localMessages.filter { !allDeletedIds.contains(it.id) && !deletedMsgIds.contains(it.id) }
                saveAllMessages(finalCleanMessages)

                // 5. Merge groups
                val remoteGroups = remotePayload.groups
                if (remoteGroups.isNotEmpty()) {
                    val localGroups = getGroups().toMutableList()
                    for (rg in remoteGroups) {
                        val gIdx = localGroups.indexOfFirst { it.id == rg.id }
                        if (gIdx >= 0) localGroups[gIdx] = rg else localGroups.add(rg)
                    }
                    saveGroups(localGroups)
                }

                // Also partition into messages_{chatId}
                val grouped = finalCleanMessages.groupBy { it.chatId }
                grouped.forEach { (chatId, msgs) ->
                    prefs.edit().putString("messages_$chatId", json.encodeToString(msgs)).apply()
                }

                // 5. Rebuild chat entries for current user
                if (currentUserId != null) {
                    refreshChatsForUser(currentUserId, localUsers, localRequests, finalCleanMessages)
                }

                // If we had fallen back to GET, push the merged state back
                if (!pushedMerged) {
                    val mergedPayload = CloudDbPayload(
                        users = localUsers,
                        friend_requests = localRequests,
                        messages = finalCleanMessages,
                        blocked_users = localBlocked
                    )
                    ChatoozCloudApi.pushCloudData(mergedPayload)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun refreshChatsForUser(
        myUserId: String,
        allUsers: List<User>,
        allRequests: List<FriendRequest>,
        allMessages: List<Message>
    ) {
        val acceptedRequests = allRequests.filter { it.status == "ACCEPTED" && (it.senderId == myUserId || it.receiverId == myUserId) }
        val existingChats = getChats(myUserId).toMutableList()

        for (req in acceptedRequests) {
            val friendId = if (req.senderId == myUserId) req.receiverId else req.senderId
            if (isAnyBlocked(myUserId, friendId)) continue

            val friendUser = allUsers.find { 
                it.id == friendId || it.username.equals(if (req.senderId == myUserId) req.receiverUsername else req.senderUsername, ignoreCase = true) 
            }
            val chatId = chatIdFor(myUserId, friendId)
            val chatMessages = allMessages.filter { it.chatId == chatId }.sortedBy { it.timestamp }
            val lastMsg = chatMessages.lastOrNull()

            val unread = chatMessages.count { it.senderId != myUserId && it.status != "READ" }

            val friendName = friendUser?.name
                ?: if (req.senderId == myUserId) {
                    if (req.receiverName.isNotBlank()) req.receiverName else req.receiverUsername
                } else {
                    if (req.senderName.isNotBlank()) req.senderName else req.senderUsername
                }
            val friendUsername = friendUser?.username
                ?: if (req.senderId == myUserId) req.receiverUsername else req.senderUsername
            val friendAvatarColor = friendUser?.avatarColor
                ?: if (req.senderId == myUserId) req.receiverAvatarColor else req.senderAvatarColor

            val allStatuses = getStatuses()
            val fallbackStatusAvatar = allStatuses.find { it.userId == friendId }?.userAvatarUrl
            val friendAvatarUrl = friendUser?.avatarUrl?.ifBlank { null }
                ?: fallbackStatusAvatar?.ifBlank { null }
                ?: existingChats.find { it.id == chatId }?.friendAvatarUrl?.ifBlank { null }

            val updatedChat = Chat(
                id = chatId,
                friendId = friendId,
                friendName = friendName,
                friendUsername = friendUsername,
                friendAvatarColor = friendAvatarColor,
                friendAvatarUrl = friendAvatarUrl,
                lastMessageText = lastMsg?.text ?: "You are now friends! Say hello 👋",
                lastMessageTime = lastMsg?.timestamp ?: req.timestamp,
                unreadCount = unread
            )

            val idx = existingChats.indexOfFirst { it.id == chatId }
            if (idx >= 0) existingChats[idx] = updatedChat else existingChats.add(0, updatedChat)
        }

        // Also ensure any chat with messages for myUserId is present
        val myMessageChats = allMessages.filter { it.chatId.contains(myUserId) }.groupBy { it.chatId }
        val allStatuses = getStatuses()
        for ((chatId, msgs) in myMessageChats) {
            if (existingChats.none { it.id == chatId }) {
                val parts = chatId.removePrefix("chat_").split("_")
                val otherId = parts.find { it != myUserId } ?: continue
                if (isAnyBlocked(myUserId, otherId)) continue
                val otherUser = allUsers.find { it.id == otherId }
                val fallbackStatus = allStatuses.find { it.userId == otherId }
                val sortedMsgs = msgs.sortedBy { it.timestamp }
                val lastMsg = sortedMsgs.lastOrNull()
                val unread = sortedMsgs.count { it.senderId != myUserId && it.status != "READ" }
                val newChat = Chat(
                    id = chatId,
                    friendId = otherId,
                    friendName = otherUser?.name ?: "Chat",
                    friendUsername = otherUser?.username ?: "",
                    friendAvatarColor = otherUser?.avatarColor ?: 0xFF6366F1L,
                    friendAvatarUrl = otherUser?.avatarUrl?.ifBlank { null } ?: fallbackStatus?.userAvatarUrl?.ifBlank { null },
                    lastMessageText = lastMsg?.text ?: "",
                    lastMessageTime = lastMsg?.timestamp ?: System.currentTimeMillis(),
                    unreadCount = unread
                )
                existingChats.add(newChat)
            }
        }

        val sorted = existingChats
            .filter { !isAnyBlocked(myUserId, it.friendId) }
            .sortedByDescending { it.lastMessageTime }
        saveChats(myUserId, sorted)
    }

    // ─── Groups ───────────────────────────────────────────────────────
    fun getGroups(): List<com.chatooz.app.model.Group> {
        val raw = prefs.getString("user_groups", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    fun saveGroups(groups: List<com.chatooz.app.model.Group>) {
        prefs.edit().putString("user_groups", json.encodeToString(groups)).apply()
    }

    fun upsertGroup(group: com.chatooz.app.model.Group) {
        val list = getGroups().toMutableList()
        val idx = list.indexOfFirst { it.id == group.id }
        if (idx >= 0) list[idx] = group else list.add(0, group)
        saveGroups(list)
    }

    fun getGroupById(groupId: String): com.chatooz.app.model.Group? {
        return getGroups().find { it.id == groupId }
    }

    fun deleteGroup(groupId: String) {
        val groups = getGroups().filter { it.id != groupId }
        saveGroups(groups)
        prefs.edit().remove("messages_$groupId").apply()
        val all = getAllMessages().filter { it.chatId != groupId }
        saveAllMessages(all)
    }

    fun removeGroupMember(groupId: String, userId: String) {
        val grp = getGroupById(groupId) ?: return
        val updatedMembers = grp.members.filter { it.userId != userId }
        upsertGroup(grp.copy(members = updatedMembers))
    }

    fun deleteChat(myUserId: String, chatId: String) {
        val chats = getChats(myUserId).filter { it.id != chatId }
        saveChats(myUserId, chats)
        prefs.edit().remove("messages_$chatId").apply()
        val all = getAllMessages().filter { it.chatId != chatId }
        saveAllMessages(all)
    }

    fun clearChatMessages(chatId: String) {
        prefs.edit().remove("messages_$chatId").apply()
        val all = getAllMessages().filter { it.chatId != chatId }
        saveAllMessages(all)
    }

    // ─── Status / Stories (24-hour Expiry) ───────────────────────────
    fun getStatuses(): List<com.chatooz.app.model.Status> {
        val raw = prefs.getString("chatooz_statuses", "[]") ?: "[]"
        val list: List<com.chatooz.app.model.Status> = try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        val deletedIds = getDeletedStatusIds()
        return list.filter { it.timestamp >= cutoff && !deletedIds.contains(it.id) }
    }

    fun getDeletedStatusIds(): Set<String> {
        return prefs.getStringSet("chatooz_deleted_status_ids", emptySet()) ?: emptySet()
    }

    fun saveStatuses(statuses: List<com.chatooz.app.model.Status>) {
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        val deletedIds = getDeletedStatusIds()
        val valid = statuses.filter { it.timestamp >= cutoff && !deletedIds.contains(it.id) }
        prefs.edit().putString("chatooz_statuses", json.encodeToString(valid)).apply()
    }

    fun addStatus(status: com.chatooz.app.model.Status) {
        val current = getStatuses().toMutableList()
        current.removeAll { it.id == status.id }
        current.add(status)
        saveStatuses(current)
    }

    fun deleteStatus(statusId: String) {
        val deletedIds = getDeletedStatusIds().toMutableSet()
        deletedIds.add(statusId)
        prefs.edit().putStringSet("chatooz_deleted_status_ids", deletedIds).apply()

        val current = getStatuses().filter { it.id != statusId }
        saveStatuses(current)
    }

    fun markStatusViewed(statusId: String, viewerId: String) {
        val current = getStatuses().toMutableList()
        val idx = current.indexOfFirst { it.id == statusId }
        if (idx >= 0) {
            val item = current[idx]
            if (!item.viewers.contains(viewerId)) {
                val updated = item.copy(viewers = item.viewers + viewerId)
                current[idx] = updated
                saveStatuses(current)
            }
        }
    }

    fun toggleLikeStatus(statusId: String, userId: String) {
        val current = getStatuses().toMutableList()
        val idx = current.indexOfFirst { it.id == statusId }
        if (idx >= 0) {
            val item = current[idx]
            val newLikes = if (item.likes.contains(userId)) {
                item.likes - userId
            } else {
                item.likes + userId
            }
            val updated = item.copy(likes = newLikes)
            current[idx] = updated
            saveStatuses(current)
        }
    }

    // ─── Clear all data (logout) ─────────────────────────────────────
    fun clearSession() {
        setSessionUserId(null)
    }

    fun nukeAll() {
        prefs.edit().clear().apply()
    }
}
