package com.chatooz.app.data

import android.util.Log
import com.chatooz.app.model.BlockedUser
import com.chatooz.app.model.FriendRequest
import com.chatooz.app.model.Message
import com.chatooz.app.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "ChatoozCloudApi"

@Serializable
data class CloudDbPayload(
    val users: List<User> = emptyList(),
    val friend_requests: List<FriendRequest> = emptyList(),
    val messages: List<Message> = emptyList(),
    val blocked_users: List<BlockedUser> = emptyList(),
    val groups: List<com.chatooz.app.model.Group> = emptyList(),
    val deleted_user_ids: List<String> = emptyList(),
    val deleted_message_ids: List<String> = emptyList()
)

@Serializable
data class CloudObjectResponse(
    val id: String? = null,
    val name: String? = null,
    val data: CloudDbPayload? = null
)

@Serializable
data class CloudObjectRequest(
    val name: String = "chatooz_cloud_sync_db",
    val data: CloudDbPayload
)

object ChatoozCloudApi {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = false
    }

    /**
     * Shared OkHttpClient — handles connection pooling, timeouts, and
     * TLS for production HTTPS connections automatically.
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Bypass-Tunnel-Reminder", "true")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Fetches current cloud database state from the configured server.
     * URL is set by AppConfig (build-time BuildConfig injection).
     * Production: HTTPS only. Debug: may use HTTP local server.
     */
    suspend fun fetchCloudData(): CloudDbPayload? = withContext(Dispatchers.IO) {
        val url = AppConfig.syncUrl
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString<CloudObjectResponse>(body)
                return@withContext parsed.data ?: CloudDbPayload()
            } else {
                Log.w(TAG, "fetchCloudData HTTP ${response.code} from $url")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchCloudData failed: ${e.message}")
            AppConfig.onNetworkFailure(null)
            null
        }
    }

    /**
     * Uploads local dataset to the cloud server and returns the merged state.
     * The server performs a conflict-aware merge and returns the result.
     */
    suspend fun pushCloudData(payload: CloudDbPayload): CloudDbPayload? = withContext(Dispatchers.IO) {
        val url = AppConfig.syncUrl
        val requestObj = CloudObjectRequest(data = payload)
        val jsonString = json.encodeToString(requestObj)

        try {
            val request = Request.Builder()
                .url(url)
                .post(jsonString.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext payload
                return@withContext try {
                    val parsed = json.decodeFromString<CloudObjectResponse>(body)
                    parsed.data ?: payload
                } catch (e: Exception) {
                    payload
                }
            } else {
                Log.w(TAG, "pushCloudData HTTP ${response.code} from $url")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "pushCloudData failed: ${e.message}")
            AppConfig.onNetworkFailure(null)
            null
        }
    }

    /**
     * Checks if the server is reachable (used for connectivity diagnostics).
     */
    suspend fun isServerReachable(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url(AppConfig.healthUrl)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Searches users by username or name on the server.
     * Returns matching users from all registered devices globally.
     */
    @kotlinx.serialization.Serializable
    private data class UserSearchResponse(val users: List<User> = emptyList())

    suspend fun searchUsersOnServer(query: String, excludeId: String): List<User> = withContext(Dispatchers.IO) {
        return@withContext try {
            val encodedQuery   = java.net.URLEncoder.encode(query, "UTF-8")
            val encodedExclude = java.net.URLEncoder.encode(excludeId, "UTF-8")
            val url = "${AppConfig.apiBaseUrl}/users/search?q=$encodedQuery&exclude=$encodedExclude"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.0")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString<UserSearchResponse>(body).users
            } else {
                Log.w(TAG, "searchUsers HTTP ${response.code}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchUsers failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches only messages newer than [sinceMs] for chats involving [userId].
     * Much faster than a full sync — used for 300ms low-latency message polling.
     * Returns empty list on any error (graceful degradation to full sync).
     */
    @kotlinx.serialization.Serializable
    private data class RecentMessagesResponse(
        val messages: List<Message> = emptyList(),
        val serverTime: Long = 0
    )

    suspend fun fetchRecentMessages(userId: String, sinceMs: Long): List<Message> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/messages/recent?userId=${java.net.URLEncoder.encode(userId, "UTF-8")}&since=$sinceMs"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.0")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString<RecentMessagesResponse>(body).messages
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Pushes a single message to the server immediately (fast path, no full sync).
     * Returns true on success.
     */
    suspend fun sendMessageDirect(message: Message): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/messages"
            val body = json.encodeToString(message)
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.0")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "sendMessageDirect failed: ${e.message}")
            false
        }
    }

    /**
     * Sends OTP to the given email address.
     */
    suspend fun sendOtpToEmail(email: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/auth/send-otp"
            val payload = org.json.JSONObject().apply { put("email", email) }.toString()
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.0")
                .build()
            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success("OTP sent to $email")
            } else {
                val errorMsg = try { org.json.JSONObject(respBody).optString("error", "Failed to send OTP") } catch (_: Exception) { "Failed to send OTP" }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies the OTP code for the given email address.
     */
    @Serializable
    data class VerifyOtpResult(
        val isNewUser: Boolean,
        val user: User? = null,
        val email: String? = null
    )

    suspend fun verifyEmailOtp(email: String, otp: String): Result<VerifyOtpResult> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/auth/verify-otp"
            val payload = org.json.JSONObject().apply {
                put("email", email)
                put("otp", otp)
            }.toString()
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.0")
                .build()
            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonResult = org.json.JSONObject(respBody)
                val isNew = jsonResult.optBoolean("isNewUser", true)
                val userObj = jsonResult.optJSONObject("user")
                val user = if (userObj != null) {
                    User(
                        id = userObj.optString("id"),
                        name = userObj.optString("name"),
                        username = userObj.optString("username"),
                        email = userObj.optString("email"),
                        phone = userObj.optString("phone", ""),
                        avatarColor = userObj.optLong("avatarColor", 0xFF6366F1L),
                        avatarUrl = userObj.optString("avatarUrl", "").ifBlank { null },
                        bio = userObj.optString("bio", ""),
                        createdAt = userObj.optLong("createdAt", 0)
                    )
                } else null

                Result.success(VerifyOtpResult(isNewUser = isNew, user = user, email = email))
            } else {
                val errorMsg = try { org.json.JSONObject(respBody).optString("error", "Verification failed") } catch (_: Exception) { "Verification failed" }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registers a new user on the central server and triggers email alerts.
     */
    suspend fun registerUserAccount(user: User): Result<User> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/auth/register"
            val body = json.encodeToString(user)
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.1")
                .build()
            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(user)
            } else {
                val errorMsg = try { org.json.JSONObject(respBody).optString("error", "Failed to register account") } catch (_: Exception) { "Failed to register account" }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete message either for me or for everyone on server.
     */
    suspend fun deleteMessage(messageId: String, chatId: String, userId: String, mode: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/messages/delete"
            val payload = org.json.JSONObject().apply {
                put("messageId", messageId)
                put("chatId", chatId)
                put("userId", userId)
                put("mode", mode)
            }.toString()
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Create a group on server.
     */
    suspend fun createGroup(
        name: String,
        description: String,
        creatorId: String,
        memberIds: List<String>,
        avatarColor: Long
    ): Result<com.chatooz.app.model.Group> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/groups/create"
            val payload = org.json.JSONObject().apply {
                put("name", name)
                put("description", description)
                put("creatorId", creatorId)
                put("memberIds", org.json.JSONArray(memberIds))
                put("avatarColor", avatarColor)
            }.toString()
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jobj = org.json.JSONObject(body).optJSONObject("group")
                if (jobj != null) {
                    val grp = com.chatooz.app.model.Group(
                        id = jobj.optString("id"),
                        name = jobj.optString("name"),
                        description = jobj.optString("description", ""),
                        creatorId = jobj.optString("creatorId"),
                        avatarColor = jobj.optLong("avatarColor", 0xFF6366F1L),
                        createdAt = jobj.optLong("createdAt", System.currentTimeMillis()),
                        members = memberIds.map { com.chatooz.app.model.GroupMember(userId = it) }
                    )
                    Result.success(grp)
                } else {
                    Result.failure(Exception("Invalid group response"))
                }
            } else {
                Result.failure(Exception("Failed to create group"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch user's groups from server.
     */
    suspend fun fetchUserGroups(userId: String): List<com.chatooz.app.model.Group> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/groups?userId=${java.net.URLEncoder.encode(userId, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonArr = org.json.JSONObject(body).optJSONArray("groups") ?: org.json.JSONArray()
                val list = mutableListOf<com.chatooz.app.model.Group>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val mArr = obj.optJSONArray("members") ?: org.json.JSONArray()
                    val members = mutableListOf<com.chatooz.app.model.GroupMember>()
                    for (j in 0 until mArr.length()) {
                        val mo = mArr.getJSONObject(j)
                        members.add(
                            com.chatooz.app.model.GroupMember(
                                userId = mo.optString("userId"),
                                role = mo.optString("role", "MEMBER"),
                                joinedAt = mo.optLong("joinedAt", 0),
                                userName = mo.optString("userName", null),
                                username = mo.optString("username", null),
                                avatarColor = mo.optLong("avatarColor", 0)
                            )
                        )
                    }
                    list.add(
                        com.chatooz.app.model.Group(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            description = obj.optString("description", ""),
                            creatorId = obj.optString("creatorId"),
                            avatarColor = obj.optLong("avatarColor", 0xFF6366F1L),
                            createdAt = obj.optLong("createdAt", 0),
                            members = members
                        )
                    )
                }
                list
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Match device phone contacts against registered users on server.
     */
    suspend fun matchContacts(phones: List<String>): List<User> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/contacts/match"
            val jsonBody = org.json.JSONObject().apply {
                val arr = org.json.JSONArray()
                phones.forEach { arr.put(it) }
                put("phones", arr)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonArr = org.json.JSONObject(body).optJSONArray("matchedUsers") ?: org.json.JSONArray()
                val list = mutableListOf<User>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    list.add(
                        User(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            username = obj.optString("username"),
                            email = obj.optString("email", ""),
                            phone = obj.optString("phone", ""),
                            avatarColor = obj.optLong("avatarColor", 0xFF6366F1L),
                            avatarUrl = obj.optString("avatarUrl", "").ifBlank { null },
                            bio = obj.optString("bio", ""),
                            createdAt = obj.optLong("createdAt", 0)
                        )
                    )
                }
                list
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Upload user profile avatar to server. Returns the avatar URL if successful.
     */
    suspend fun uploadAvatar(userId: String, base64Image: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/api/profile/avatar"
            val jsonBody = org.json.JSONObject().apply {
                put("userId", userId)
                put("avatarBase64", base64Image)
                put("mimeType", "image/jpeg")
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.4")
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val jsonRes = org.json.JSONObject(body)
                val avatarUrl = jsonRes.optString("avatarUrl")
                if (avatarUrl.isNotBlank()) {
                    Result.success(avatarUrl)
                } else {
                    Result.failure(Exception("Invalid server response"))
                }
            } else {
                Result.failure(Exception("Server returned ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user profile details (Name, Phone, Address, Bio) on server.
     */
    suspend fun updateProfileDetails(
        userId: String,
        name: String,
        phone: String,
        address: String,
        bio: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/api/profile/update"
            val jsonBody = org.json.JSONObject().apply {
                put("userId", userId)
                put("name", name)
                put("phone", phone)
                put("address", address)
                put("bio", bio)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.4")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val body = response.body?.string() ?: ""
                Result.failure(Exception("Server returned ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeGroupMember(groupId: String, adminId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/groups/remove-member"
            val jsonBody = org.json.JSONObject().apply {
                put("groupId", groupId)
                put("adminId", adminId)
                put("userId", userId)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.4")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }

    suspend fun deleteGroup(groupId: String, adminId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/groups/delete"
            val jsonBody = org.json.JSONObject().apply {
                put("groupId", groupId)
                put("adminId", adminId)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.4")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }

    suspend fun leaveGroup(groupId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/groups/leave"
            val jsonBody = org.json.JSONObject().apply {
                put("groupId", groupId)
                put("userId", userId)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.4")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }

    suspend fun clearChatMessages(chatId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/chats/clear"
            val jsonBody = org.json.JSONObject().apply {
                put("chatId", chatId)
                put("userId", userId)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("User-Agent", "Chatooz-Android/2.4")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }

    suspend fun fetchStatuses(): List<com.chatooz.app.model.Status> = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/api/statuses"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val bodyStr = response.body?.string() ?: return@withContext emptyList()
            val jsonObj = org.json.JSONObject(bodyStr)
            val arr = jsonObj.optJSONArray("statuses") ?: org.json.JSONArray()
            val list = mutableListOf<com.chatooz.app.model.Status>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val viewersArr = item.optJSONArray("viewers") ?: org.json.JSONArray()
                val viewersList = mutableListOf<String>()
                for (j in 0 until viewersArr.length()) {
                    viewersList.add(viewersArr.getString(j))
                }
                val likesArr = item.optJSONArray("likes") ?: org.json.JSONArray()
                val likesList = mutableListOf<String>()
                for (k in 0 until likesArr.length()) {
                    likesList.add(likesArr.getString(k))
                }
                list.add(
                    com.chatooz.app.model.Status(
                        id = item.optString("id", ""),
                        userId = item.optString("userId", ""),
                        userName = item.optString("userName", ""),
                        userUsername = item.optString("userUsername", ""),
                        userAvatarColor = item.optLong("userAvatarColor", 0xFF6366F1L),
                        userAvatarUrl = item.optString("userAvatarUrl", ""),
                        type = item.optString("type", "TEXT"),
                        textContent = item.optString("textContent", ""),
                        bgGradientIndex = item.optInt("bgGradientIndex", 0),
                        mediaBase64 = if (item.isNull("mediaBase64")) null else item.optString("mediaBase64", null),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                        viewers = viewersList,
                        likes = likesList
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "fetchStatuses error: ${e.message}")
            emptyList()
        }
    }

    suspend fun createStatus(status: com.chatooz.app.model.Status): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/api/statuses/create"
            val jsonBody = org.json.JSONObject().apply {
                put("id", status.id)
                put("userId", status.userId)
                put("userName", status.userName)
                put("userUsername", status.userUsername)
                put("userAvatarColor", status.userAvatarColor)
                put("userAvatarUrl", status.userAvatarUrl)
                put("type", status.type)
                put("textContent", status.textContent)
                put("bgGradientIndex", status.bgGradientIndex)
                if (status.mediaBase64 != null) {
                    put("mediaBase64", status.mediaBase64)
                }
                put("timestamp", status.timestamp)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "createStatus error: ${e.message}")
            false
        }
    }

    suspend fun markStatusViewed(statusId: String, viewerUserId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/api/statuses/view"
            val jsonBody = org.json.JSONObject().apply {
                put("statusId", statusId)
                put("viewerUserId", viewerUserId)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }

    suspend fun likeStatus(statusId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/api/statuses/like"
            val jsonBody = org.json.JSONObject().apply {
                put("statusId", statusId)
                put("userId", userId)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }

    suspend fun deleteStatus(statusId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${AppConfig.apiBaseUrl}/api/statuses/delete"
            val jsonBody = org.json.JSONObject().apply {
                put("statusId", statusId)
                put("userId", userId)
            }
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }
}


