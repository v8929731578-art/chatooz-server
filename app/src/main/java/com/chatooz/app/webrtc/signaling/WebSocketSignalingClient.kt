package com.chatooz.app.webrtc.signaling

import android.util.Log
import com.chatooz.app.data.AppConfig
import com.chatooz.app.webrtc.model.SignalingMessage
import com.chatooz.app.webrtc.model.SignalingType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketSignalingClient(
    private val scope: CoroutineScope
) : WebRtcSignalingClient {

    companion object {
        private const val TAG = "WebRtcSignaling"
    }

    private val _incomingMessages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<SignalingMessage> = _incomingMessages.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null
    private var currentUserId: String? = null
    private var isRunning = false
    private var connectionJob: Job? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Bypass-Tunnel-Reminder", "true")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override suspend fun connect(userId: String) {
        currentUserId = userId
        isRunning = true
        connectionJob?.cancel()
        connectionJob = scope.launch(Dispatchers.IO) {
            var backoff = 1000L
            while (isActive && isRunning) {
                try {
                    connectInternal(userId)
                } catch (e: Exception) {
                    Log.w(TAG, "Signaling connection error: ${e.message}")
                }
                if (isActive && isRunning) {
                    _isConnected.value = false
                    delay(backoff)
                    backoff = minOf(backoff * 2, 15_000L)
                }
            }
        }
    }

    private suspend fun connectInternal(userId: String) {
        val url = AppConfig.signalingWsUrl
        Log.i(TAG, "Connecting signaling WebSocket to $url for user $userId")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Chatooz-WebRTC/1.0")
            .header("Bypass-Tunnel-Reminder", "true")
            .build()

        val closeSignal = CompletableDeferred<Unit>()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Signaling WebSocket open, registering user $userId")
                this@WebSocketSignalingClient.webSocket = webSocket
                _isConnected.value = true

                // Send registration frame
                val regFrame = JSONObject().apply {
                    put("type", "REGISTER")
                    put("userId", userId)
                }.toString()
                webSocket.send(regFrame)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val jsonObj = JSONObject(text)
                    val typeStr = jsonObj.optString("type", "")
                    if (typeStr == "REGISTERED") {
                        Log.i(TAG, "Signaling registered successfully on server")
                        return
                    }
                    val msg = json.decodeFromString(SignalingMessage.serializer(), text)
                    _incomingMessages.tryEmit(msg)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode signaling message: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Signaling WebSocket closed: $code $reason")
                _isConnected.value = false
                this@WebSocketSignalingClient.webSocket = null
                closeSignal.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Signaling WebSocket failure: ${t.message}")
                _isConnected.value = false
                this@WebSocketSignalingClient.webSocket = null
                closeSignal.complete(Unit)
            }
        }

        httpClient.newWebSocket(request, listener)
        closeSignal.await()
    }

    override suspend fun sendMessage(message: SignalingMessage): Boolean = withContext(Dispatchers.IO) {
        val ws = webSocket
        if (ws != null && _isConnected.value) {
            try {
                val jsonText = json.encodeToString(SignalingMessage.serializer(), message)
                return@withContext ws.send(jsonText)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending signaling message: ${e.message}")
            }
        }
        return@withContext false
    }

    override suspend fun disconnect() {
        isRunning = false
        connectionJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected.value = false
    }
}
