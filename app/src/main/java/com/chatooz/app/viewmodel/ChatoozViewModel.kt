package com.chatooz.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatooz.app.call.CallInfo
import com.chatooz.app.call.CallManager
import com.chatooz.app.call.CallState
import com.chatooz.app.data.ChatoozCloudApi
import com.chatooz.app.data.ChatoozStorage
import com.chatooz.app.model.*
import com.chatooz.app.util.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** Main app screens for navigation */
sealed class Screen {
    object Auth     : Screen()
    object OtpVerification : Screen() // Enter 6-digit email OTP
    object Setup    : Screen()   // Choose username after OTP verification
    object Home     : Screen()
    data class Chat(
        val chatId: String,
        val friendId: String,
        val friendName: String,
        val friendUsername: String,
        val friendAvatarColor: Long,
        val friendAvatarUrl: String? = null
    ) : Screen()
    object Friends  : Screen()
    object Status   : Screen()
    object Profile  : Screen()
}

class ChatoozViewModel(app: Application) : AndroidViewModel(app) {

    val storage = ChatoozStorage(app.applicationContext)

    // ─── Navigation ──────────────────────────────────────────────────
    private val _screen = MutableStateFlow<Screen>(Screen.Auth)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    // ─── Auth State ──────────────────────────────────────────────────
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // ─── Auth UI ─────────────────────────────────────────────────────
    private val _pendingEmail = MutableStateFlow("")
    val pendingEmail: StateFlow<String> = _pendingEmail.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _otpTimerSeconds = MutableStateFlow(0)
    val otpTimerSeconds: StateFlow<Int> = _otpTimerSeconds.asStateFlow()

    private val _authSuccessMessage = MutableStateFlow<String?>(null)
    val authSuccessMessage: StateFlow<String?> = _authSuccessMessage.asStateFlow()

    private val _suggestedOtp = MutableStateFlow<String?>(null)
    val suggestedOtp: StateFlow<String?> = _suggestedOtp.asStateFlow()

    // ─── Home & Chats ────────────────────────────────────────────────
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    // ─── Friends ─────────────────────────────────────────────────────
    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends: StateFlow<List<User>> = _friends.asStateFlow()

    private val _incomingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<FriendRequest>> = _incomingRequests.asStateFlow()

    private val _outgoingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val outgoingRequests: StateFlow<List<FriendRequest>> = _outgoingRequests.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _friendActionFeedback = MutableStateFlow<String?>(null)
    val friendActionFeedback: StateFlow<String?> = _friendActionFeedback.asStateFlow()

    // ─── Active Chat ─────────────────────────────────────────────────
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    // ─── Blocked Users ───────────────────────────────────────────────
    private val _blockedUsers = MutableStateFlow<List<BlockedUser>>(emptyList())
    val blockedUsers: StateFlow<List<BlockedUser>> = _blockedUsers.asStateFlow()

    private val _isCurrentChatBlocked = MutableStateFlow(false)
    val isCurrentChatBlocked: StateFlow<Boolean> = _isCurrentChatBlocked.asStateFlow()

    private val _isBlockedByCurrentChat = MutableStateFlow(false)
    val isBlockedByCurrentChat: StateFlow<Boolean> = _isBlockedByCurrentChat.asStateFlow()

    // ─── Cloud Sync Status ───────────────────────────────────────────
    private val _cloudSyncStatus = MutableStateFlow("Synced")
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    // ─── Dark mode ───────────────────────────────────────────────────
    private val _isDark = MutableStateFlow(true)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    // ─── All registered accounts (for account switcher) ──────────────
    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    // ─── Groups ──────────────────────────────────────────────────────
    private val _userGroups = MutableStateFlow<List<Group>>(emptyList())
    val userGroups: StateFlow<List<Group>> = _userGroups.asStateFlow()

    // ─── Status / Stories ─────────────────────────────────────────────
    private val _statuses = MutableStateFlow<List<Status>>(storage.getStatuses())
    val statuses: StateFlow<List<Status>> = _statuses.asStateFlow()

    // Format timestamp for display
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())

    // ─── Calling (delegated to CallManager) ──────────────────────────
    val callState: StateFlow<CallState>  = CallManager.callState
    val callStatusMessage: StateFlow<String> = CallManager.callStatusMessage
    val callInfo:  StateFlow<CallInfo?>  = CallManager.callInfo
    val isMuted:   StateFlow<Boolean>    = CallManager.isMuted
    val isSpeakerOn: StateFlow<Boolean>  = CallManager.isSpeakerOn
    val isCameraOn: StateFlow<Boolean>   = CallManager.isCameraOn
    val callDurationSeconds: StateFlow<Int> = CallManager.callDurationSeconds
    val remoteVideoBitmap: StateFlow<android.graphics.Bitmap?> = CallManager.remoteVideoBitmap


    // ─── Recording UI state ───────────────────────────────────────────
    private val _showVoiceRecorder = MutableStateFlow(false)
    val showVoiceRecorder: StateFlow<Boolean> = _showVoiceRecorder.asStateFlow()

    init {
        CallManager.init(app.applicationContext)
        restoreSession()
        loadStatuses()
        startFastMessagePoll()
        startFullSyncJob()
    }

    /**
     * FAST POLL — runs every 300ms when in a chat, 800ms otherwise.
     * Only fetches messages newer than the last seen timestamp using the lightweight
     * /messages/recent endpoint. This gives ~300ms message delivery latency instead
     * of the old 800–1200ms full-sync latency.
     */
    private var lastFastPollMs = 0L

    private fun startFastMessagePoll() {
        viewModelScope.launch {
            while (isActive) {
                val me = _currentUser.value
                val currentScr = _screen.value
                if (me != null) {
                    try {
                        val since = if (lastFastPollMs == 0L) {
                            // First poll: fetch last 30 seconds to catch anything missed
                            System.currentTimeMillis() - 30_000L
                        } else {
                            lastFastPollMs
                        }
                        val recentMsgs = ChatoozCloudApi.fetchRecentMessages(me.id, since)
                        if (recentMsgs.isNotEmpty()) {
                            var gotNew = false
                            var gotNewIncoming = false
                            val deletedIds = storage.getDeletedMessageIds()
                            for (msg in recentMsgs) {
                                if (deletedIds.contains(msg.id)) continue
                                if (msg.deletedForUsers?.split(",")?.map { it.trim() }?.contains(me.id) == true) continue

                                // Adjust isFromMe based on current user (server always stores isFromMe=false)
                                val adjusted = msg.copy(isFromMe = msg.senderId == me.id)
                                val isNew = withContext(Dispatchers.IO) {
                                    val existing = storage.getMessages(msg.chatId).any { it.id == msg.id }
                                    if (!existing) {
                                        storage.addMessage(msg.chatId, adjusted)
                                        true
                                    } else {
                                        if (adjusted.isDeletedForEveryone) {
                                            storage.deleteMessageForEveryone(msg.chatId, msg.id)
                                        }
                                        false
                                    }
                                }
                                if (isNew) {
                                    gotNew = true
                                    if (!adjusted.isFromMe) {
                                        gotNewIncoming = true
                                    }
                                }
                            }
                            // Update last seen timestamp to latest received
                            lastFastPollMs = recentMsgs.maxOf { it.timestamp }

                            if (gotNew) {
                                withContext(Dispatchers.Main) {
                                    if (gotNewIncoming) {
                                        SoundManager.playMessageTone(getApplication())
                                    }
                                    // Refresh current chat if open
                                    if (currentScr is Screen.Chat) {
                                        storage.markMessagesRead(currentScr.chatId, me.id)
                                        _messages.value = storage.getMessages(currentScr.chatId)
                                    }
                                    refreshChats()
                                }
                            }
                        } else if (lastFastPollMs == 0L) {
                            // No recent messages — move cursor forward so next poll is incremental
                            lastFastPollMs = System.currentTimeMillis()
                        }
                    } catch (_: Exception) {}
                }
                val pollDelay = if (currentScr is Screen.Chat) 300L else 800L
                delay(pollDelay)
            }
        }
    }

    /**
     * FULL SYNC — runs every 5 seconds.
     * Syncs ALL data (users, friend requests, messages, blocked users).
     * Handles multi-device consistency, friend request updates, read receipts etc.
     */
    private fun startFullSyncJob() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val success = storage.syncWithCloud()
                    _cloudSyncStatus.value = if (success) "Synced" else "Offline"
                    withContext(Dispatchers.Main) {
                        _allUsers.value = storage.getAllUsers()
                        val me = _currentUser.value
                        if (me != null) {
                            val userStillExists = storage.getUserById(me.id)
                            if (userStillExists == null) {
                                // User account was deleted by admin! Log out immediately and return to Auth screen
                                logout()
                                _authError.value = "Your account was removed by admin. Please create a new account."
                                return@withContext
                            } else {
                                _currentUser.value = userStillExists
                            }

                            refreshHomeData()
                            _blockedUsers.value = storage.getBlockedUsers(me.id)
                            refreshFriendsData()
                            refreshChats()
                            val currentScr = _screen.value
                            if (currentScr is Screen.Chat) {
                                storage.markMessagesRead(currentScr.chatId, me.id)
                                _messages.value = storage.getMessages(currentScr.chatId)
                                _isCurrentChatBlocked.value = storage.isUserBlocked(me.id, currentScr.friendId)
                                _isBlockedByCurrentChat.value = storage.isBlockedBy(me.id, currentScr.friendId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(5000L)  // Full sync every 5 seconds (was 800–1200ms — now fast poll handles messages)
            }
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            _cloudSyncStatus.value = "Syncing..."
            val success = storage.syncWithCloud()
            _cloudSyncStatus.value = if (success) "Synced" else "Offline"
            val me = _currentUser.value
            if (me != null) {
                val fresh = storage.getUserById(me.id)
                if (fresh != null) {
                    _currentUser.value = fresh
                }
                _blockedUsers.value = storage.getBlockedUsers(me.id)
            }
            _allUsers.value = storage.getAllUsers()
            refreshHomeData()
        }
    }

    private fun restoreSession() {
        val userId = storage.getSessionUserId()
        if (userId != null) {
            val user = storage.getUserById(userId)
            if (user != null) {
                _currentUser.value = user
                _allUsers.value = storage.getAllUsers()
                _blockedUsers.value = storage.getBlockedUsers(user.id)
                _screen.value = Screen.Home
                refreshHomeData()
                // Start polling for incoming calls in background
                CallManager.startBackgroundIncomingCallPoll(viewModelScope, user.id)
            } else {
                _screen.value = Screen.Auth
            }
        } else {
            _screen.value = Screen.Auth
        }
    }

    // ─── AUTH ─────────────────────────────────────────────────────────

    fun sendEmailOtp(email: String) {
        val trimmedEmail = email.trim().lowercase()
        if (!isValidGmail(trimmedEmail)) {
            _authError.value = "Please enter a valid Gmail address (must end with @gmail.com)"
            return
        }
        _authError.value = null
        _authSuccessMessage.value = null
        _authLoading.value = true

        viewModelScope.launch {
            val result = ChatoozCloudApi.sendOtpToEmail(trimmedEmail)
            _authLoading.value = false
            result.onSuccess { sendRes ->
                _pendingEmail.value = trimmedEmail
                _suggestedOtp.value = sendRes.otp
                _authSuccessMessage.value = sendRes.message
                _screen.value = Screen.OtpVerification
                startOtpCountdown()
            }.onFailure { err ->
                _authError.value = err.message ?: "Failed to send verification email. Please try again."
            }
        }
    }

    private var otpTimerJob: kotlinx.coroutines.Job? = null

    private fun startOtpCountdown() {
        otpTimerJob?.cancel()
        _otpTimerSeconds.value = 60
        otpTimerJob = viewModelScope.launch {
            while (_otpTimerSeconds.value > 0) {
                delay(1000L)
                _otpTimerSeconds.value -= 1
            }
        }
    }

    fun resendEmailOtp() {
        val email = _pendingEmail.value
        if (email.isBlank() || _otpTimerSeconds.value > 0) return
        sendEmailOtp(email)
    }

    fun verifyEmailOtp(otp: String) {
        val email = _pendingEmail.value
        val cleanOtp = otp.trim()
        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            _authError.value = "Please enter the 6-digit verification code"
            return
        }
        _authError.value = null
        _authLoading.value = true

        viewModelScope.launch {
            val result = ChatoozCloudApi.verifyEmailOtp(email, cleanOtp)
            _authLoading.value = false
            result.onSuccess { verifyRes ->
                if (verifyRes.isNewUser || verifyRes.user == null) {
                    // New user or no profile exists yet -> Go to username/profile setup
                    _screen.value = Screen.Setup
                } else {
                    // Existing user -> log in directly
                    val existingUser = verifyRes.user
                    storage.upsertUser(existingUser)
                    storage.setSessionUserId(existingUser.id)
                    _currentUser.value = existingUser
                    _allUsers.value = storage.getAllUsers()
                    _blockedUsers.value = storage.getBlockedUsers(existingUser.id)
                    _screen.value = Screen.Home
                    refreshHomeData()
                    CallManager.startBackgroundIncomingCallPoll(viewModelScope, existingUser.id)
                    storage.syncWithCloud()
                }
            }.onFailure { err ->
                _authError.value = err.message ?: "Invalid verification code. Please check and try again."
            }
        }
    }

    fun goToAuth() {
        _authError.value = null
        _authSuccessMessage.value = null
        _screen.value = Screen.Auth
    }

    fun loginWithGmail(email: String) {
        sendEmailOtp(email)
    }

    fun createAccount(email: String, displayName: String, username: String, phone: String = "") {
        val cleanUsername = username.trim().lowercase()
        val cleanName = displayName.trim()
        val cleanPhone = phone.trim()

        if (cleanName.isBlank()) {
            _authError.value = "Please enter your name"
            return
        }
        if (!isValidUsername(cleanUsername)) {
            _authError.value = "Username must be 3-20 characters: letters, numbers, underscores only"
            return
        }
        if (storage.isUsernameTaken(cleanUsername)) {
            _authError.value = "Username @$cleanUsername is already taken. Try another!"
            return
        }

        _authError.value = null
        _authLoading.value = true

        viewModelScope.launch {
            val avatarColors = listOf(
                0xFF6366F1L, 0xFF8B5CF6L, 0xFF10B981L, 0xFF06B6D4L,
                0xFFF43F5EL, 0xFFF59E0BL, 0xFFEC4899L, 0xFF14B8A6L
            )
            val user = User(
                id = "user_${System.currentTimeMillis()}",
                name = cleanName,
                username = cleanUsername,
                email = email.lowercase(),
                phone = cleanPhone,
                avatarColor = avatarColors.random()
            )

            // 1. Register to cloud server directly (dispatches admin alert to chatooz.help@gmail.com)
            val regResult = ChatoozCloudApi.registerUserAccount(user)
            _authLoading.value = false

            regResult.onSuccess { registeredUser ->
                storage.upsertUser(registeredUser)
                storage.setSessionUserId(registeredUser.id)
                _currentUser.value = registeredUser
                _allUsers.value = storage.getAllUsers()
                _screen.value = Screen.Home
                refreshHomeData()
                CallManager.startBackgroundIncomingCallPoll(viewModelScope, registeredUser.id)
                // Trigger full sync
                storage.syncWithCloud()
            }.onFailure { err ->
                _authError.value = err.message ?: "Failed to register account. Please check your internet connection."
            }
        }
    }

    fun checkUsernameAvailability(username: String): Boolean {
        val clean = username.trim().lowercase()
        return isValidUsername(clean) && !storage.isUsernameTaken(clean)
    }

    fun updateProfileAvatar(context: android.content.Context, uri: android.net.Uri) {
        val me = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Decode original bitmap
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (originalBitmap == null) return@launch

                // 2. Read EXIF orientation tag from Uri
                var orientation = android.media.ExifInterface.ORIENTATION_NORMAL
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val exif = android.media.ExifInterface(stream)
                        orientation = exif.getAttributeInt(
                            android.media.ExifInterface.TAG_ORIENTATION,
                            android.media.ExifInterface.ORIENTATION_NORMAL
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 3. Apply rotation matrix
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postRotate(90f)
                        matrix.postScale(-1f, 1f)
                    }
                    android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postRotate(270f)
                        matrix.postScale(-1f, 1f)
                    }
                }

                val rotatedBitmap = if (!matrix.isIdentity) {
                    android.graphics.Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )
                } else {
                    originalBitmap
                }

                // 4. Center-square crop for beautiful circle DP
                val minSide = Math.min(rotatedBitmap.width, rotatedBitmap.height)
                val cropX = (rotatedBitmap.width - minSide) / 2
                val cropY = (rotatedBitmap.height - minSide) / 2
                val squareBitmap = android.graphics.Bitmap.createBitmap(rotatedBitmap, cropX, cropY, minSide, minSide)

                // 5. Scale down to 512x512
                val targetDim = Math.min(512, minSide)
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(squareBitmap, targetDim, targetDim, true)

                val baos = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos)
                val imageBytes = baos.toByteArray()
                val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

                // Save locally to cache
                val profileDir = java.io.File(context.cacheDir, "avatar_cache").apply { mkdirs() }
                val localFile = java.io.File(profileDir, "avatar_${me.id}.jpg")
                java.io.FileOutputStream(localFile).use { it.write(imageBytes) }

                // Put in memory cache
                com.chatooz.app.util.AvatarLoader.putCache(localFile.absolutePath, scaledBitmap)

                // Upload to server
                val result = ChatoozCloudApi.uploadAvatar(me.id, base64)
                val avatarUrl = result.getOrNull() ?: localFile.absolutePath
                com.chatooz.app.util.AvatarLoader.putCache(avatarUrl, scaledBitmap)

                val updatedUser = me.copy(avatarUrl = avatarUrl)
                storage.upsertUser(updatedUser)
                withContext(Dispatchers.Main) {
                    _currentUser.value = updatedUser
                    _allUsers.value = storage.getAllUsers()
                    _friendActionFeedback.value = "Profile picture updated! 📸"
                }

                // Push update to cloud sync
                storage.syncWithCloud()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _friendActionFeedback.value = "Failed to update profile picture: ${e.localizedMessage}"
                }
            }
        }
    }

    fun updateProfileAvatarBitmap(context: android.content.Context, croppedBitmap: android.graphics.Bitmap) {
        val me = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetDim = Math.min(512, Math.min(croppedBitmap.width, croppedBitmap.height))
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(croppedBitmap, targetDim, targetDim, true)

                val baos = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos)
                val imageBytes = baos.toByteArray()
                val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

                // Save locally to cache
                val profileDir = java.io.File(context.cacheDir, "avatar_cache").apply { mkdirs() }
                val localFile = java.io.File(profileDir, "avatar_${me.id}.jpg")
                java.io.FileOutputStream(localFile).use { it.write(imageBytes) }

                // Put in memory cache
                com.chatooz.app.util.AvatarLoader.putCache(localFile.absolutePath, scaledBitmap)

                // Upload to server
                val result = ChatoozCloudApi.uploadAvatar(me.id, base64)
                val avatarUrl = result.getOrNull() ?: localFile.absolutePath
                com.chatooz.app.util.AvatarLoader.putCache(avatarUrl, scaledBitmap)

                val updatedUser = me.copy(avatarUrl = avatarUrl)
                storage.upsertUser(updatedUser)
                withContext(Dispatchers.Main) {
                    _currentUser.value = updatedUser
                    _allUsers.value = storage.getAllUsers()
                    _friendActionFeedback.value = "Profile picture updated! 📸"
                }

                // Push update to cloud sync
                storage.syncWithCloud()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _friendActionFeedback.value = "Failed to update profile picture: ${e.localizedMessage}"
                }
            }
        }
    }

    fun updateUserProfileDetails(name: String, phone: String, address: String, bio: String) {
        val me = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedUser = me.copy(
                    name = name.trim().ifBlank { me.name },
                    phone = phone.trim(),
                    address = address.trim(),
                    bio = bio.trim()
                )
                storage.upsertUser(updatedUser)
                withContext(Dispatchers.Main) {
                    _currentUser.value = updatedUser
                    _allUsers.value = storage.getAllUsers()
                    _friendActionFeedback.value = "Profile updated successfully! ✨"
                }

                // Sync directly with server profile update endpoint
                ChatoozCloudApi.updateProfileDetails(
                    userId = me.id,
                    name = updatedUser.name,
                    phone = updatedUser.phone,
                    address = updatedUser.address,
                    bio = updatedUser.bio
                )

                // Push full local state to sync endpoint and reload fresh state
                storage.syncWithCloud()
                val refreshed = storage.getUserById(me.id) ?: updatedUser
                withContext(Dispatchers.Main) {
                    _currentUser.value = refreshed
                    _allUsers.value = storage.getAllUsers()
                    refreshHomeData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _friendActionFeedback.value = "Profile saved locally"
                }
            }
        }
    }

    fun logout() {
        storage.clearSession()
        _currentUser.value = null
        _chats.value = emptyList()
        _friends.value = emptyList()
        _messages.value = emptyList()
        _blockedUsers.value = emptyList()
        _screen.value = Screen.Auth
    }

    fun switchAccount(userId: String) {
        val user = storage.getUserById(userId) ?: return
        storage.setSessionUserId(userId)
        _currentUser.value = user
        _blockedUsers.value = storage.getBlockedUsers(userId)
        _screen.value = Screen.Home
        refreshHomeData()
        CallManager.startBackgroundIncomingCallPoll(viewModelScope, user.id)
    }

    fun clearAuthError() { _authError.value = null }

    // ─── NAVIGATION ──────────────────────────────────────────────────

    fun goHome() {
        refreshHomeData()
        _screen.value = Screen.Home
    }

    fun goFriends() {
        refreshFriendsData()
        _screen.value = Screen.Friends
    }

    fun goStatus() {
        loadStatuses()
        _screen.value = Screen.Status
    }

    fun goProfile() {
        val me = _currentUser.value
        if (me != null) {
            val fresh = storage.getUserById(me.id)
            if (fresh != null) {
                _currentUser.value = fresh
            }
            _blockedUsers.value = storage.getBlockedUsers(me.id)
        }
        _allUsers.value = storage.getAllUsers()
        _screen.value = Screen.Profile
    }

    // ─── STATUS / STORIES ─────────────────────────────────────────────

    fun loadStatuses() {
        viewModelScope.launch(Dispatchers.IO) {
            val local = storage.getStatuses()
            withContext(Dispatchers.Main) {
                _statuses.value = local
            }
            try {
                val cloudStatuses = ChatoozCloudApi.fetchStatuses()
                val meId = _currentUser.value?.id ?: ""
                val localMyStatuses = storage.getStatuses().filter { it.userId == meId }
                val combined = (cloudStatuses + localMyStatuses).distinctBy { it.id }
                storage.saveStatuses(combined)
                val updated = storage.getStatuses()
                withContext(Dispatchers.Main) {
                    _statuses.value = updated
                }
            } catch (e: Exception) {
                val fallback = storage.getStatuses()
                withContext(Dispatchers.Main) {
                    _statuses.value = fallback
                }
            }
        }
    }

    fun createTextStatus(text: String, bgGradientIndex: Int) {
        val me = _currentUser.value ?: return
        if (text.isBlank()) return
        val statusId = "status_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val newStatus = Status(
            id = statusId,
            userId = me.id,
            userName = me.name,
            userUsername = me.username,
            userAvatarColor = me.avatarColor,
            userAvatarUrl = me.avatarUrl ?: "",
            type = "TEXT",
            textContent = text.trim(),
            bgGradientIndex = bgGradientIndex,
            mediaBase64 = null,
            timestamp = System.currentTimeMillis(),
            viewers = emptyList()
        )
        storage.addStatus(newStatus)
        _statuses.value = storage.getStatuses()

        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.createStatus(newStatus)
            loadStatuses()
        }
    }

    fun createImageStatus(imageBytes: ByteArray, caption: String) {
        val me = _currentUser.value ?: return
        val statusId = "status_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val newStatus = Status(
            id = statusId,
            userId = me.id,
            userName = me.name,
            userUsername = me.username,
            userAvatarColor = me.avatarColor,
            userAvatarUrl = me.avatarUrl ?: "",
            type = "IMAGE",
            textContent = caption.trim(),
            bgGradientIndex = 0,
            mediaBase64 = base64,
            timestamp = System.currentTimeMillis(),
            viewers = emptyList()
        )
        storage.addStatus(newStatus)
        _statuses.value = storage.getStatuses()

        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.createStatus(newStatus)
            loadStatuses()
        }
    }

    fun markStatusViewed(statusId: String) {
        val me = _currentUser.value ?: return
        storage.markStatusViewed(statusId, me.id)
        _statuses.value = storage.getStatuses()
        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.markStatusViewed(statusId, me.id)
        }
    }

    fun toggleLikeStatus(statusId: String) {
        val me = _currentUser.value ?: return
        storage.toggleLikeStatus(statusId, me.id)
        _statuses.value = storage.getStatuses()
        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.likeStatus(statusId, me.id)
            loadStatuses()
        }
    }

    fun repostStatus(status: Status) {
        val me = _currentUser.value ?: return
        val statusId = "status_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val newStatus = Status(
            id = statusId,
            userId = me.id,
            userName = me.name,
            userUsername = me.username,
            userAvatarColor = me.avatarColor,
            userAvatarUrl = me.avatarUrl ?: "",
            type = status.type,
            textContent = if (status.textContent.isNotBlank()) status.textContent else "",
            bgGradientIndex = status.bgGradientIndex,
            mediaBase64 = status.mediaBase64,
            timestamp = System.currentTimeMillis(),
            viewers = emptyList(),
            likes = emptyList()
        )
        storage.addStatus(newStatus)
        _statuses.value = storage.getStatuses()
        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.createStatus(newStatus)
            loadStatuses()
        }
    }

    fun replyToStatus(status: Status, replyText: String) {
        val me = _currentUser.value ?: return
        if (replyText.isBlank()) return
        val targetUserId = status.userId
        if (targetUserId == me.id) return

        val chatId = storage.chatIdFor(me.id, targetUserId)
        val msgId = "msg_${System.currentTimeMillis()}"
        val storySnippet = if (status.textContent.isNotBlank()) {
            "\"${status.textContent.take(50)}\""
        } else {
            "📷 Photo Story"
        }
        val fullMsg = "💬 Replied to your story ($storySnippet):\n$replyText"

        val message = Message(
            id = msgId,
            chatId = chatId,
            senderId = me.id,
            text = fullMsg,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = "SENT",
            type = "TEXT"
        )

        storage.addMessage(chatId, message)

        val friendUser = storage.getUserById(targetUserId)
        val chatForMe = Chat(
            id = chatId,
            friendId = targetUserId,
            friendName = friendUser?.name ?: status.userName,
            friendUsername = friendUser?.username ?: status.userUsername,
            friendAvatarColor = friendUser?.avatarColor ?: status.userAvatarColor,
            friendAvatarUrl = friendUser?.avatarUrl ?: status.userAvatarUrl,
            lastMessageText = fullMsg,
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0
        )
        storage.upsertChat(me.id, chatForMe)
        refreshChats()

        viewModelScope.launch(Dispatchers.IO) {
            val directSent = ChatoozCloudApi.sendMessageDirect(message)
            if (!directSent) {
                storage.syncWithCloud()
            }
        }
    }

    fun deleteStatus(statusId: String) {
        val me = _currentUser.value ?: return
        storage.deleteStatus(statusId)
        _statuses.value = storage.getStatuses()
        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.deleteStatus(statusId, me.id)
            val cloudStatuses = ChatoozCloudApi.fetchStatuses()
            storage.saveStatuses(cloudStatuses)
            withContext(Dispatchers.Main) {
                _statuses.value = storage.getStatuses()
            }
        }
    }

    fun openChat(chat: Chat) {
        val me = _currentUser.value ?: return
        val chatId = if (chat.id.startsWith("grp_")) chat.id else storage.chatIdFor(me.id, chat.friendId)
        val msgs = storage.getMessages(chatId)
        _messages.value = msgs
        val isGroup = chatId.startsWith("grp_")
        _isCurrentChatBlocked.value = if (isGroup) false else storage.isUserBlocked(me.id, chat.friendId)
        _isBlockedByCurrentChat.value = if (isGroup) false else storage.isBlockedBy(me.id, chat.friendId)

        val friendUser = if (isGroup) null else storage.getUserById(chat.friendId)
        val fallbackStatus = if (isGroup) null else storage.getStatuses().find { it.userId == chat.friendId }
        val resolvedAvatar = chat.friendAvatarUrl?.ifBlank { null }
            ?: friendUser?.avatarUrl?.ifBlank { null }
            ?: fallbackStatus?.userAvatarUrl?.ifBlank { null }

        // Mark messages as read
        storage.markMessagesRead(chatId, me.id)
        val updatedChat = chat.copy(
            friendName = friendUser?.name?.ifBlank { null } ?: chat.friendName,
            friendUsername = friendUser?.username?.ifBlank { null } ?: chat.friendUsername,
            friendAvatarColor = friendUser?.avatarColor ?: chat.friendAvatarColor,
            friendAvatarUrl = resolvedAvatar,
            unreadCount = 0
        )
        storage.upsertChat(me.id, updatedChat)
        refreshChats()
        _screen.value = Screen.Chat(
            chatId,
            chat.friendId,
            updatedChat.friendName,
            updatedChat.friendUsername,
            updatedChat.friendAvatarColor,
            updatedChat.friendAvatarUrl
        )

        // Sync read status to cloud immediately so sender's device updates ticks
        viewModelScope.launch(Dispatchers.IO) {
            storage.syncWithCloud()
            withContext(Dispatchers.Main) {
                refreshChats()
            }
        }
    }

    fun closeChat() {
        _isTyping.value = false
        goHome()
    }

    // ─── BLOCK / UNBLOCK ──────────────────────────────────────────────

    fun blockUser(targetUserId: String, targetUsername: String, targetName: String = "") {
        val me = _currentUser.value ?: return
        storage.blockUser(me.id, targetUserId, targetUsername, targetName)
        _isCurrentChatBlocked.value = true
        _blockedUsers.value = storage.getBlockedUsers(me.id)
        _friendActionFeedback.value = "Blocked @$targetUsername"
        refreshHomeData()

        viewModelScope.launch {
            storage.syncWithCloud()
        }
    }

    fun unblockUser(targetUserId: String) {
        val me = _currentUser.value ?: return
        storage.unblockUser(me.id, targetUserId)
        _isCurrentChatBlocked.value = false
        _blockedUsers.value = storage.getBlockedUsers(me.id)
        _friendActionFeedback.value = "User unblocked"
        refreshHomeData()

        viewModelScope.launch {
            storage.syncWithCloud()
        }
    }

    // ─── CHAT MESSAGES ────────────────────────────────────────────────

    fun setChatInput(text: String) { _chatInput.value = text }

    fun sendMessage(chatId: String, friendId: String) {
        val text = _chatInput.value.trim()
        if (text.isBlank()) return
        val me = _currentUser.value ?: return

        val isGroup = chatId.startsWith("grp_") || friendId.startsWith("grp_")

        if (!isGroup) {
            if (storage.isUserBlocked(me.id, friendId)) {
                _friendActionFeedback.value = "Cannot send message: you blocked this user"
                return
            }
            if (storage.isBlockedBy(me.id, friendId)) {
                _friendActionFeedback.value = "Cannot send message: you have been blocked by this user"
                return
            }
        }

        _chatInput.value = ""
        val msgId = "msg_${System.currentTimeMillis()}"
        val message = Message(
            id = msgId,
            chatId = chatId,
            senderId = me.id,
            text = text,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = "SENT",
            type = "TEXT"
        )

        storage.addMessage(chatId, message)
        _messages.value = storage.getMessages(chatId)

        // Update chat list
        val myChat = if (isGroup) {
            val grp = storage.getGroupById(chatId)
            Chat(
                id = chatId,
                friendId = chatId,
                friendName = grp?.name ?: "Group",
                friendUsername = "Group • ${grp?.members?.size ?: 0} members",
                friendAvatarColor = grp?.avatarColor ?: 0xFF6366F1L,
                lastMessageText = text,
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0
            )
        } else {
            val friend = storage.getUserById(friendId)
            val fallbackStatus = storage.getStatuses().find { it.userId == friendId }
            Chat(
                id = chatId,
                friendId = friendId,
                friendName = friend?.name ?: "Unknown",
                friendUsername = friend?.username ?: "",
                friendAvatarColor = friend?.avatarColor ?: 0xFF6366F1L,
                friendAvatarUrl = friend?.avatarUrl?.ifBlank { null } ?: fallbackStatus?.userAvatarUrl?.ifBlank { null },
                lastMessageText = text,
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0
            )
        }
        storage.upsertChat(me.id, myChat)
        refreshChats()

        // Push to cloud immediately using direct endpoint (fast path), fallback to sync
        viewModelScope.launch(Dispatchers.IO) {
            val directSent = ChatoozCloudApi.sendMessageDirect(message)
            if (!directSent) {
                storage.syncWithCloud()
            }
            withContext(Dispatchers.Main) {
                _messages.value = storage.getMessages(chatId)
                refreshChats()
            }
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean = false) {
        val chatScreen = _screen.value as? Screen.Chat ?: return
        val me = _currentUser.value ?: return

        if (forEveryone) {
            storage.deleteMessageForEveryone(chatScreen.chatId, messageId)
        } else {
            storage.deleteMessageForMe(chatScreen.chatId, messageId, me.id)
        }
        _messages.value = storage.getMessages(chatScreen.chatId)
        refreshChats()

        viewModelScope.launch(Dispatchers.IO) {
            val mode = if (forEveryone) "FOR_EVERYONE" else "FOR_ME"
            ChatoozCloudApi.deleteMessage(messageId, chatScreen.chatId, me.id, mode)
            storage.syncWithCloud()
            withContext(Dispatchers.Main) {
                _messages.value = storage.getMessages(chatScreen.chatId)
                refreshChats()
            }
        }
    }

    // ─── GROUPS ──────────────────────────────────────────────────────
    fun createGroup(name: String, description: String, memberIds: List<String>) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val me = _currentUser.value ?: return

        val avatarColors = listOf(0xFF6366F1L, 0xFF8B5CF6L, 0xFF10B981L, 0xFF06B6D4L, 0xFFF43F5EL)
        val selectedColor = avatarColors.random()

        viewModelScope.launch {
            val res = ChatoozCloudApi.createGroup(cleanName, description, me.id, memberIds, selectedColor)
            val grp = res.getOrElse {
                Group(
                    id = "grp_${System.currentTimeMillis()}",
                    name = cleanName,
                    description = description,
                    creatorId = me.id,
                    avatarColor = selectedColor,
                    members = (listOf(me.id) + memberIds).distinct().map { GroupMember(userId = it) }
                )
            }
            storage.upsertGroup(grp)
            refreshGroups()
            refreshChats()
            _friendActionFeedback.value = "Group '$cleanName' created! 🎉"

            // Open the new group chat immediately
            val groupChat = Chat(
                id = grp.id,
                friendId = grp.id,
                friendName = grp.name,
                friendUsername = "Group • ${grp.members.size} members",
                friendAvatarColor = grp.avatarColor,
                lastMessageText = if (grp.description.isNotBlank()) grp.description else "Group created 🎉",
                lastMessageTime = grp.createdAt
            )
            storage.upsertChat(me.id, groupChat)
            openChat(groupChat)
        }
    }

    fun refreshGroups() {
        val me = _currentUser.value ?: return
        _userGroups.value = storage.getGroups().filter { g ->
            g.creatorId == me.id || g.members.any { it.userId == me.id }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val remote = ChatoozCloudApi.fetchUserGroups(me.id)
            if (remote.isNotEmpty()) {
                remote.forEach { storage.upsertGroup(it) }
                withContext(Dispatchers.Main) {
                    _userGroups.value = storage.getGroups().filter { g ->
                        g.creatorId == me.id || g.members.any { it.userId == me.id }
                    }
                }
            }
        }
    }

    fun removeGroupMember(groupId: String, targetUserId: String) {
        val me = _currentUser.value ?: return
        storage.removeGroupMember(groupId, targetUserId)
        refreshGroups()
        refreshChats()
        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.removeGroupMember(groupId, me.id, targetUserId)
            storage.syncWithCloud()
            withContext(Dispatchers.Main) {
                refreshGroups()
                refreshChats()
                _friendActionFeedback.value = "Member removed from group"
            }
        }
    }

    fun deleteGroup(groupId: String) {
        val me = _currentUser.value ?: return
        storage.deleteGroup(groupId)
        storage.deleteChat(me.id, groupId)
        refreshGroups()
        refreshChats()
        goHome()
        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.deleteGroup(groupId, me.id)
            storage.syncWithCloud()
            withContext(Dispatchers.Main) {
                refreshGroups()
                refreshChats()
                _friendActionFeedback.value = "Group deleted"
            }
        }
    }

    fun leaveGroup(groupId: String) {
        val me = _currentUser.value ?: return
        storage.deleteChat(me.id, groupId)
        storage.removeGroupMember(groupId, me.id)
        refreshGroups()
        refreshChats()
        goHome()
        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.leaveGroup(groupId, me.id)
            storage.syncWithCloud()
            withContext(Dispatchers.Main) {
                refreshGroups()
                refreshChats()
                _friendActionFeedback.value = "Left group"
            }
        }
    }

    fun clearChat(chatId: String) {
        val me = _currentUser.value ?: return
        storage.clearChatMessages(chatId)
        _messages.value = emptyList()
        val chats = storage.getChats(me.id).toMutableList()
        val idx = chats.indexOfFirst { it.id == chatId }
        if (idx >= 0) {
            chats[idx] = chats[idx].copy(lastMessageText = "", unreadCount = 0)
            storage.saveChats(me.id, chats)
        }
        refreshChats()
        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.clearChatMessages(chatId, me.id)
            withContext(Dispatchers.Main) {
                _friendActionFeedback.value = "Chat cleared"
            }
        }
    }

    fun deleteChat(chatId: String) {
        val me = _currentUser.value ?: return
        storage.deleteChat(me.id, chatId)
        refreshChats()
        if (_screen.value is Screen.Chat) {
            goHome()
        }
        viewModelScope.launch(Dispatchers.IO) {
            ChatoozCloudApi.clearChatMessages(chatId, me.id)
            withContext(Dispatchers.Main) {
                _friendActionFeedback.value = "Chat deleted"
            }
        }
    }

    // ─── FRIENDS ─────────────────────────────────────────────────────

    fun searchUsers(query: String) {
        _searchQuery.value = query
        val me = _currentUser.value ?: return
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        val clean = query.trim().lowercase().removePrefix("@")

        // Show local results immediately for instant feedback
        val localResults = storage.getAllUsers()
            .filter { it.id != me.id }
            .filter {
                it.username.contains(clean, ignoreCase = true) ||
                it.name.contains(clean, ignoreCase = true)
            }
            .take(20)
        _searchResults.value = localResults

        // Then fetch from server to find users on other devices
        viewModelScope.launch {
            try {
                val serverResults = ChatoozCloudApi.searchUsersOnServer(clean, me.id)
                if (serverResults.isNotEmpty()) {
                    // Save new users to local storage so they appear in future local searches
                    serverResults.forEach { storage.upsertUser(it) }
                    // Merge: server results first, then any local-only extras
                    val merged = (serverResults + localResults)
                        .distinctBy { it.id }
                        .filter { it.id != me.id }
                        .take(30)
                    // Only update if query is still the same (user hasn't changed it)
                    if (_searchQuery.value.trim().lowercase().removePrefix("@") == clean) {
                        _searchResults.value = merged
                    }
                }
            } catch (e: Exception) {
                // Server unavailable — local results already shown, no action needed
            }
        }
    }

    fun sendFriendRequest(targetUser: User) {
        val me = _currentUser.value ?: return
        if (storage.isUserBlocked(me.id, targetUser.id)) {
            _friendActionFeedback.value = "Cannot send request: you blocked @${targetUser.username}"
            return
        }
        if (storage.isBlockedBy(me.id, targetUser.id)) {
            _friendActionFeedback.value = "Cannot send request: you are blocked by this user"
            return
        }
        if (storage.areFriends(me.id, targetUser.id)) {
            _friendActionFeedback.value = "You're already friends with @${targetUser.username}!"
            return
        }
        if (storage.hasPendingRequest(me.id, targetUser.id)) {
            _friendActionFeedback.value = "Request already sent to @${targetUser.username}"
            return
        }
        val request = FriendRequest(
            id = "req_${System.currentTimeMillis()}",
            senderId = me.id,
            senderUsername = me.username,
            senderName = me.name,
            senderAvatarColor = me.avatarColor,
            receiverId = targetUser.id,
            receiverUsername = targetUser.username,
            receiverName = targetUser.name,
            receiverAvatarColor = targetUser.avatarColor,
            status = "PENDING"
        )
        storage.sendFriendRequest(request)
        _friendActionFeedback.value = "Friend request sent to @${targetUser.username}! ✉️"
        refreshFriendsData()
        searchUsers(_searchQuery.value)

        viewModelScope.launch(Dispatchers.IO) {
            storage.syncWithCloud()
            withContext(Dispatchers.Main) {
                refreshFriendsData()
            }
        }
    }

    fun acceptRequest(request: FriendRequest) {
        val me = _currentUser.value ?: return
        storage.updateRequestStatus(request.id, "ACCEPTED")

        // Create an initial chat for both sides
        val sender = storage.getUserById(request.senderId)
        val senderAvatarUrl = sender?.avatarUrl?.ifBlank { null } ?: request.senderAvatarUrl?.ifBlank { null }
        val senderName = sender?.name ?: if (request.senderName.isNotBlank()) request.senderName else request.senderUsername
        val senderUsername = sender?.username ?: request.senderUsername
        val senderAvatarColor = sender?.avatarColor ?: request.senderAvatarColor

        val chatId = storage.chatIdFor(me.id, request.senderId)
        val chatForMe = Chat(
            id = chatId,
            friendId = request.senderId,
            friendName = senderName,
            friendUsername = senderUsername,
            friendAvatarColor = senderAvatarColor,
            friendAvatarUrl = senderAvatarUrl,
            lastMessageText = "You are now friends! Say hello 👋",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0
        )
        storage.upsertChat(me.id, chatForMe)

        val chatForSender = Chat(
            id = chatId,
            friendId = me.id,
            friendName = me.name,
            friendUsername = me.username,
            friendAvatarColor = me.avatarColor,
            friendAvatarUrl = me.avatarUrl,
            lastMessageText = "@${me.username} accepted your request! Say hello 👋",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0
        )
        storage.upsertChat(request.senderId, chatForSender)

        _friendActionFeedback.value = "You and @$senderUsername are now friends! 🎉"
        refreshFriendsData()
        refreshChats()

        viewModelScope.launch(Dispatchers.IO) {
            storage.syncWithCloud()
            withContext(Dispatchers.Main) {
                refreshFriendsData()
                refreshChats()
            }
        }
    }

    fun declineRequest(request: FriendRequest) {
        storage.updateRequestStatus(request.id, "DECLINED")
        _friendActionFeedback.value = "Request from @${request.senderUsername} declined"
        refreshFriendsData()

        viewModelScope.launch(Dispatchers.IO) {
            storage.syncWithCloud()
            withContext(Dispatchers.Main) {
                refreshFriendsData()
            }
        }
    }

    fun clearFeedback() { _friendActionFeedback.value = null }

    fun getFriendRequestStatus(targetUserId: String): String {
        val me = _currentUser.value ?: return "NONE"
        if (storage.isUserBlocked(me.id, targetUserId)) return "BLOCKED"
        return when {
            storage.areFriends(me.id, targetUserId) -> "FRIENDS"
            storage.hasPendingRequest(me.id, targetUserId) -> "SENT"
            storage.hasPendingRequest(targetUserId, me.id) -> "RECEIVED"
            else -> "NONE"
        }
    }

    // ─── REFRESH ──────────────────────────────────────────────────────

    fun refreshHomeData() {
        refreshChats()
        refreshFriendsData()
        loadStatuses()
    }

    private fun refreshChats() {
        val me = _currentUser.value ?: return
        val directChats = storage.getChats(me.id).filter { !storage.isAnyBlocked(me.id, it.friendId) }

        val allUsers = storage.getAllUsers().associateBy { it.id }
        val allStatuses = storage.getStatuses().associateBy { it.userId }

        val updatedDirectChats = directChats.map { chat ->
            val friendUser = allUsers[chat.friendId]
            val statusAvatar = allStatuses[chat.friendId]?.userAvatarUrl
            val avatarUrl = friendUser?.avatarUrl?.ifBlank { null }
                ?: statusAvatar?.ifBlank { null }
                ?: chat.friendAvatarUrl?.ifBlank { null }
            val friendName = friendUser?.name?.ifBlank { null } ?: chat.friendName
            val friendUsername = friendUser?.username?.ifBlank { null } ?: chat.friendUsername
            val friendAvatarColor = friendUser?.avatarColor ?: chat.friendAvatarColor

            chat.copy(
                friendName = friendName,
                friendUsername = friendUsername,
                friendAvatarColor = friendAvatarColor,
                friendAvatarUrl = avatarUrl
            )
        }

        val userGroups = storage.getGroups().filter { g ->
            g.creatorId == me.id || g.members.any { it.userId == me.id }
        }

        val groupChats = userGroups.map { g ->
            val msgs = storage.getMessages(g.id).sortedBy { it.timestamp }
            val lastMsg = msgs.lastOrNull()
            val unread = msgs.count { it.senderId != me.id && it.status != "READ" }
            Chat(
                id = g.id,
                friendId = g.id,
                friendName = g.name,
                friendUsername = "Group • ${g.members.size} members",
                friendAvatarColor = g.avatarColor,
                friendAvatarUrl = null,
                lastMessageText = lastMsg?.text ?: if (g.description.isNotBlank()) g.description else "Group created 🎉",
                lastMessageTime = lastMsg?.timestamp ?: g.createdAt,
                unreadCount = unread
            )
        }

        val combined = (updatedDirectChats.filter { !it.id.startsWith("grp_") } + groupChats)
            .distinctBy { it.id }
            .sortedByDescending { it.lastMessageTime }

        _chats.value = combined
    }

    fun inviteToCurrentCall(friend: User) {
        val me = _currentUser.value ?: return
        CallManager.inviteParticipantToCall(
            scope = viewModelScope,
            myUserId = me.id,
            myUserName = me.name,
            myUserUsername = me.username,
            myUserAvatarColor = me.avatarColor,
            remoteUserId = friend.id,
            remoteUsername = friend.username,
            remoteName = friend.name
        )
        _friendActionFeedback.value = "Invited ${friend.name} to conference call! 📞"
    }

    private fun refreshFriendsData() {
        val me = _currentUser.value ?: return
        _friends.value = storage.getFriends(me.id)
        _incomingRequests.value = storage.getIncomingRequests(me.id)
        _outgoingRequests.value = storage.getOutgoingRequests(me.id)
    }

    // ─── CALLING ─────────────────────────────────────────────────────

    fun startAudioCall(friendId: String, friendUsername: String, friendName: String, friendAvatarColor: Long) {
        val me = _currentUser.value ?: return
        CallManager.initiateCall(
            scope = viewModelScope,
            myUserId = me.id,
            myUserName = me.name,
            myUserUsername = me.username,
            myUserAvatarColor = me.avatarColor,
            remoteUserId = friendId,
            remoteUsername = friendUsername,
            remoteName = friendName,
            remoteAvatarColor = friendAvatarColor,
            isVideo = false
        )
    }

    fun startVideoCall(friendId: String, friendUsername: String, friendName: String, friendAvatarColor: Long) {
        val me = _currentUser.value ?: return
        CallManager.initiateCall(
            scope = viewModelScope,
            myUserId = me.id,
            myUserName = me.name,
            myUserUsername = me.username,
            myUserAvatarColor = me.avatarColor,
            remoteUserId = friendId,
            remoteUsername = friendUsername,
            remoteName = friendName,
            remoteAvatarColor = friendAvatarColor,
            isVideo = true
        )
    }

    fun acceptCall() { CallManager.acceptCall(viewModelScope) }

    fun declineCall() { CallManager.declineCall(viewModelScope) }

    fun endCall() { CallManager.endCall(viewModelScope) }

    fun toggleMute()    { CallManager.toggleMute() }
    fun toggleSpeaker() { CallManager.toggleSpeaker() }
    fun toggleCamera()  { CallManager.toggleCamera() }
    fun switchCamera(surfaceTexture: android.graphics.SurfaceTexture?) { CallManager.switchCamera(surfaceTexture) }
    fun startLocalCameraPreview(surfaceTexture: android.graphics.SurfaceTexture) { CallManager.startLocalCameraPreview(surfaceTexture) }

    // ─── VOICE MESSAGES ───────────────────────────────────────────────

    fun toggleVoiceRecorder() { _showVoiceRecorder.value = !_showVoiceRecorder.value }
    fun hideVoiceRecorder()   { _showVoiceRecorder.value = false }

    fun sendVoiceMessage(chatId: String, friendId: String, filePath: String, durationSec: Int) {
        val me = _currentUser.value ?: return
        val isGroup = chatId.startsWith("grp_") || friendId.startsWith("grp_")
        if (!isGroup && (storage.isUserBlocked(me.id, friendId) || storage.isBlockedBy(me.id, friendId))) {
            _friendActionFeedback.value = "Cannot send message"
            return
        }

        val msgId = "msg_${System.currentTimeMillis()}"
        val base64Data = try {
            val audioBytes = java.io.File(filePath).readBytes()
            android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }

        val message = Message(
            id = msgId,
            chatId = chatId,
            senderId = me.id,
            text = "🎤 Voice message",
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = "SENT",
            type = "AUDIO",
            audioDurationSec = durationSec,
            audioFilePath = filePath,
            audioBase64 = base64Data
        )

        storage.addMessage(chatId, message)
        _messages.value = storage.getMessages(chatId)

        val myChat = if (isGroup) {
            val grp = storage.getGroupById(chatId)
            Chat(
                id = chatId,
                friendId = chatId,
                friendName = grp?.name ?: "Group",
                friendUsername = "Group • ${grp?.members?.size ?: 0} members",
                friendAvatarColor = grp?.avatarColor ?: 0xFF6366F1L,
                lastMessageText = "🎤 Voice message",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0
            )
        } else {
            val friend = storage.getUserById(friendId)
            val fallbackStatus = storage.getStatuses().find { it.userId == friendId }
            Chat(
                id = chatId,
                friendId = friendId,
                friendName = friend?.name ?: "Unknown",
                friendUsername = friend?.username ?: "",
                friendAvatarColor = friend?.avatarColor ?: 0xFF6366F1L,
                friendAvatarUrl = friend?.avatarUrl?.ifBlank { null } ?: fallbackStatus?.userAvatarUrl?.ifBlank { null },
                lastMessageText = "🎤 Voice message",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0
            )
        }
        storage.upsertChat(me.id, myChat)
        refreshChats()
        _showVoiceRecorder.value = false

        viewModelScope.launch(Dispatchers.IO) {
            val directSent = ChatoozCloudApi.sendMessageDirect(message)
            if (!directSent) {
                storage.syncWithCloud()
            }
            withContext(Dispatchers.Main) {
                _messages.value = storage.getMessages(chatId)
                refreshChats()
            }
        }
    }

    /**
     * Sends an Image, Video, or generic File message.
     * Extracts byte data, saves locally in chat_media, generates Base64 payload, and pushes to server.
     */
    fun sendMediaMessage(
        chatId: String,
        friendId: String,
        type: String, // "IMAGE", "VIDEO", "FILE"
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        previewBase64: String? = null
    ) {
        val me = _currentUser.value ?: return
        val isGroup = chatId.startsWith("grp_") || friendId.startsWith("grp_")
        if (!isGroup && (storage.isUserBlocked(me.id, friendId) || storage.isBlockedBy(me.id, friendId))) {
            _friendActionFeedback.value = "Cannot send message"
            return
        }

        val msgId = "msg_${System.currentTimeMillis()}"
        val safeExt = when (type) {
            "IMAGE" -> "jpg"
            "VIDEO" -> "mp4"
            else -> fileName.substringAfterLast('.', "bin")
        }

        // Save local copy in chat_media
        val mediaDir = java.io.File(getApplication<Application>().applicationContext.filesDir, "chat_media")
        if (!mediaDir.exists()) mediaDir.mkdirs()
        val localFile = java.io.File(mediaDir, "media_${msgId}.$safeExt")
        try {
            localFile.writeBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        val displayLabel = when (type) {
            "IMAGE" -> "📷 Photo"
            "VIDEO" -> "🎥 Video"
            else -> "📎 $fileName"
        }

        val message = Message(
            id = msgId,
            chatId = chatId,
            senderId = me.id,
            text = displayLabel,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = "SENT",
            type = type,
            mediaBase64 = base64Data,
            mediaFilePath = localFile.absolutePath,
            fileName = fileName,
            fileSize = bytes.size.toLong(),
            mimeType = mimeType,
            thumbnailBase64 = previewBase64
        )

        storage.addMessage(chatId, message)
        _messages.value = storage.getMessages(chatId)

        val myChat = if (isGroup) {
            val grp = storage.getGroupById(chatId)
            Chat(
                id = chatId,
                friendId = chatId,
                friendName = grp?.name ?: "Group",
                friendUsername = "Group • ${grp?.members?.size ?: 0} members",
                friendAvatarColor = grp?.avatarColor ?: 0xFF6366F1L,
                lastMessageText = displayLabel,
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0
            )
        } else {
            val friend = storage.getUserById(friendId)
            val fallbackStatus = storage.getStatuses().find { it.userId == friendId }
            Chat(
                id = chatId,
                friendId = friendId,
                friendName = friend?.name ?: "Unknown",
                friendUsername = friend?.username ?: "",
                friendAvatarColor = friend?.avatarColor ?: 0xFF6366F1L,
                friendAvatarUrl = friend?.avatarUrl?.ifBlank { null } ?: fallbackStatus?.userAvatarUrl?.ifBlank { null },
                lastMessageText = displayLabel,
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0
            )
        }
        storage.upsertChat(me.id, myChat)
        refreshChats()

        viewModelScope.launch(Dispatchers.IO) {
            val directSent = ChatoozCloudApi.sendMessageDirect(message)
            if (!directSent) {
                storage.syncWithCloud()
            }
            withContext(Dispatchers.Main) {
                _messages.value = storage.getMessages(chatId)
                refreshChats()
            }
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────────────

    private fun isValidGmail(email: String): Boolean =
        email.matches(Regex("^[a-zA-Z0-9._%+\\-]+@gmail\\.com$"))

    fun isValidUsername(username: String): Boolean =
        username.matches(Regex("^[a-z0-9_]{3,20}$"))

    fun formatTimestamp(ts: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - ts
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> timeFmt.format(Date(ts))
            else -> dateFmt.format(Date(ts))
        }
    }

    fun toggleDarkMode() { _isDark.value = !_isDark.value }
}
