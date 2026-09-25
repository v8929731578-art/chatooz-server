package com.chatooz.app

import com.chatooz.app.data.ChatoozCloudApi
import com.chatooz.app.data.CloudDbPayload
import com.chatooz.app.model.BlockedUser
import com.chatooz.app.model.FriendRequest
import com.chatooz.app.model.Message
import com.chatooz.app.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CloudSyncAndBlockTest {

    @Test
    fun testBlockedUserModel() {
        val blocked = BlockedUser(
            blockerId = "user_1",
            blockedId = "user_2",
            blockedUsername = "spammer_99",
            blockedName = "Spam Account"
        )
        assertEquals("user_1", blocked.blockerId)
        assertEquals("user_2", blocked.blockedId)
        assertEquals("spammer_99", blocked.blockedUsername)
        assertTrue(blocked.timestamp > 0)
    }

    @Test
    fun testBlockFilterLogic() {
        val myId = "user_me"
        val spammerId = "user_spammer"
        val friendId = "user_friend"

        val blockedList = mutableListOf(
            BlockedUser(blockerId = myId, blockedId = spammerId, blockedUsername = "spammer")
        )

        fun isBlocked(from: String, target: String): Boolean =
            blockedList.any { it.blockerId == from && it.blockedId == target }

        fun isAnyBlocked(u1: String, u2: String): Boolean =
            isBlocked(u1, u2) || isBlocked(u2, u1)

        // Verify spammer is blocked
        assertTrue(isBlocked(myId, spammerId))
        assertFalse(isBlocked(myId, friendId))
        assertTrue(isAnyBlocked(myId, spammerId))
        assertFalse(isAnyBlocked(myId, friendId))

        // Verify incoming friend requests filter out blocked user
        val incomingRequests = listOf(
            FriendRequest(id = "r1", senderId = spammerId, senderUsername = "spammer", senderName = "Spam", senderAvatarColor = 0L, receiverId = myId, receiverUsername = "me"),
            FriendRequest(id = "r2", senderId = friendId, senderUsername = "friend", senderName = "Friend", senderAvatarColor = 0L, receiverId = myId, receiverUsername = "me")
        )
        val filtered = incomingRequests.filter { !isAnyBlocked(myId, it.senderId) }
        assertEquals(1, filtered.size)
        assertEquals("friend", filtered[0].senderUsername)

        // Verify message prevention when blocked
        val canSendMessage = !isAnyBlocked(myId, spammerId)
        assertFalse("Should not be allowed to send message to blocked user", canSendMessage)

        // Unblock spammer
        blockedList.removeAll { it.blockerId == myId && it.blockedId == spammerId }
        assertFalse(isBlocked(myId, spammerId))
        assertTrue("Message should be allowed after unblocking", !isAnyBlocked(myId, spammerId))
    }

    @Test
    fun testMultiDeviceSyncAndMessagingFlow() = runBlocking {
        val device1User = User(
            id = "device1_user_${System.currentTimeMillis()}",
            name = "Device 1 User",
            username = "dev1_${System.currentTimeMillis() % 10000}",
            email = "dev1_${System.currentTimeMillis() % 10000}@gmail.com"
        )
        val device2User = User(
            id = "device2_user_${System.currentTimeMillis()}",
            name = "Device 2 User",
            username = "dev2_${System.currentTimeMillis() % 10000}",
            email = "dev2_${System.currentTimeMillis() % 10000}@gmail.com"
        )

        // 1. Fetch current cloud state
        val initialPayload = ChatoozCloudApi.fetchCloudData() ?: CloudDbPayload()

        // 2. Simulate Device 1 creating account
        val withDev1 = initialPayload.copy(
            users = initialPayload.users + device1User
        )
        assertNotNull("Device 1 account registration sync should succeed", ChatoozCloudApi.pushCloudData(withDev1))

        delay(400)

        // 3. Simulate Device 2 syncing and seeing Device 1
        val syncedToDev2 = ChatoozCloudApi.fetchCloudData()
        assertNotNull(syncedToDev2)
        assertTrue(syncedToDev2!!.users.any { it.id == device1User.id })

        // 4. Simulate Device 2 creating account and sending friend request to Device 1
        val friendReq = FriendRequest(
            id = "req_test_${System.currentTimeMillis()}",
            senderId = device2User.id,
            senderUsername = device2User.username,
            senderName = device2User.name,
            senderAvatarColor = 0xFF8B5CF6L,
            receiverId = device1User.id,
            receiverUsername = device1User.username,
            status = "PENDING"
        )
        val withDev2AndReq = syncedToDev2.copy(
            users = syncedToDev2.users + device2User,
            friend_requests = syncedToDev2.friend_requests + friendReq
        )
        delay(400)
        assertNotNull("Device 2 sending friend request sync should succeed", ChatoozCloudApi.pushCloudData(withDev2AndReq))

        delay(400)

        // 5. Simulate Device 1 syncing, seeing the request, and accepting it
        val syncedToDev1 = ChatoozCloudApi.fetchCloudData()
        assertNotNull(syncedToDev1)
        val incomingOnDev1 = syncedToDev1!!.friend_requests.find { it.id == friendReq.id }
        assertNotNull(incomingOnDev1)
        assertEquals("PENDING", incomingOnDev1!!.status)

        val acceptedRequests = syncedToDev1.friend_requests.map {
            if (it.id == friendReq.id) it.copy(status = "ACCEPTED") else it
        }

        // 6. Simulate Device 1 sending a message to Device 2
        val chatId = "chat_${listOf(device1User.id, device2User.id).sorted()[0]}_${listOf(device1User.id, device2User.id).sorted()[1]}"
        val chatMessage = Message(
            id = "msg_${System.currentTimeMillis()}",
            chatId = chatId,
            senderId = device1User.id,
            text = "Hello from Device 1 via Cloud!",
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = "SENT"
        )

        val withAcceptedAndMsg = syncedToDev1.copy(
            friend_requests = acceptedRequests,
            messages = syncedToDev1.messages + chatMessage
        )
        delay(400)
        assertNotNull("Device 1 accepting request and sending message sync should succeed", ChatoozCloudApi.pushCloudData(withAcceptedAndMsg))

        delay(400)

        // 7. Simulate Device 2 syncing and receiving the accepted status and message
        val finalSyncOnDev2 = ChatoozCloudApi.fetchCloudData()
        assertNotNull(finalSyncOnDev2)
        val reqOnDev2 = finalSyncOnDev2!!.friend_requests.find { it.id == friendReq.id }
        assertEquals("ACCEPTED", reqOnDev2?.status)
        val msgOnDev2 = finalSyncOnDev2.messages.find { it.id == chatMessage.id }
        assertNotNull(msgOnDev2)
        assertEquals("Hello from Device 1 via Cloud!", msgOnDev2?.text)

        // 8. Simulate Device 2 blocking Device 1
        val blockAction = BlockedUser(
            blockerId = device2User.id,
            blockedId = device1User.id,
            blockedUsername = device1User.username,
            blockedName = device1User.name
        )
        val withBlock = finalSyncOnDev2.copy(
            blocked_users = finalSyncOnDev2.blocked_users + blockAction
        )
        delay(400)
        assertNotNull("Device 2 blocking Device 1 sync should succeed", ChatoozCloudApi.pushCloudData(withBlock))

        delay(400)

        // 9. Verify block state is synced across devices
        val checkBlockOnDev1 = ChatoozCloudApi.fetchCloudData()
        assertNotNull(checkBlockOnDev1)
        val isDev1Blocked = checkBlockOnDev1!!.blocked_users.any { it.blockerId == device2User.id && it.blockedId == device1User.id }
        assertTrue("Device 1 must be recorded as blocked in cloud database", isDev1Blocked)
    }
}
