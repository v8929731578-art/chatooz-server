package com.chatooz.app.call

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.chatooz.app.data.AppConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue

/**
 * CallMediaClient — Internet-capable binary WebSocket client for audio+video streaming.
 */
class CallMediaClient(
    private val callId: String,
    private val myUserId: String,
    private val onAudioPacketReceived: (ByteArray) -> Unit,
    private val onMediaEvent: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "CallMediaClient"
        private const val TYPE_HANDSHAKE: Byte = 0x01
        private const val TYPE_AUDIO: Byte     = 0x02
        private const val TYPE_VIDEO: Byte     = 0x03
        private const val TYPE_PING: Byte      = 0x04

        private const val AUDIO_BUFFER_MAX = 30
        private const val VIDEO_BUFFER_MAX = 5
    }

    private val _remoteVideoBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteVideoBitmap: StateFlow<Bitmap?> = _remoteVideoBitmap.asStateFlow()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var isRunning = false
    @Volatile private var isConnected = false
    @Volatile private var isHandshakeComplete = false

    private val audioQueue = LinkedBlockingQueue<ByteArray>(AUDIO_BUFFER_MAX)
    private val videoQueue = LinkedBlockingQueue<ByteArray>(VIDEO_BUFFER_MAX)

    private var connectionJob: Job? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Bypass-Tunnel-Reminder", "true")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    fun start(scope: CoroutineScope) {
        isRunning = true
        connectionJob?.cancel()
        connectionJob = scope.launch(Dispatchers.IO) {
            var backoffMs = 1000L
            while (isActive && isRunning) {
                Log.i(TAG, "[CALL-AUDIO] Connecting WebSocket to ${AppConfig.mediaWsUrl} (callId=$callId)")
                connectWebSocket(scope)
                if (isActive && isRunning) {
                    Log.i(TAG, "[CALL-AUDIO] WebSocket closed, reconnecting in ${backoffMs}ms...")
                    isHandshakeComplete = false
                    delay(backoffMs)
                    backoffMs = minOf(backoffMs * 2, 15_000L)
                }
            }
        }
    }

    private suspend fun connectWebSocket(scope: CoroutineScope) {
        val wsUrl = AppConfig.mediaWsUrl
        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", "Chatooz-Android/2.0")
            .header("Bypass-Tunnel-Reminder", "true")
            .build()

        val connectionClosedSignal = CompletableDeferred<Unit>()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "[CALL-AUDIO] WebSocket opened (callId=$callId, userId=$myUserId)")
                webSocket = ws
                isConnected = true
                isHandshakeComplete = true

                val hsJson = JSONObject().apply {
                    put("callId", callId)
                    put("userId", myUserId)
                }.toString().toByteArray(Charsets.UTF_8)
                sendPacket(ws, TYPE_HANDSHAKE, hsJson)
                drainQueues(ws)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                handleIncomingPacket(bytes.toByteArray())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "[CALL-AUDIO] Text WS frame: $text")
                if (!isHandshakeComplete) {
                    isHandshakeComplete = true
                    Log.i(TAG, "[CALL-AUDIO] Handshake complete (text ack), draining queues")
                    drainQueues(ws)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "[CALL-AUDIO] WebSocket closing: $code $reason")
                ws.close(1000, null)
                isConnected = false
                isHandshakeComplete = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "[CALL-AUDIO] WebSocket closed: $code $reason")
                isConnected = false
                isHandshakeComplete = false
                webSocket = null
                connectionClosedSignal.complete(Unit)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (isRunning) {
                    Log.w(TAG, "[CALL-AUDIO] WebSocket failure: ${t.message}")
                }
                isConnected = false
                isHandshakeComplete = false
                webSocket = null
                connectionClosedSignal.complete(Unit)
            }
        }

        httpClient.newWebSocket(request, listener)
        connectionClosedSignal.await()
    }

    private var firstAudioReported = false
    private var firstVideoReported = false

    private fun handleIncomingPacket(data: ByteArray) {
        if (data.size < 5) return

        val buf = ByteBuffer.wrap(data)
        val pkgType = buf.get()
        val length = buf.int

        if (length <= 0 || length > 2 * 1024 * 1024) return
        if (data.size < 5 + length) return

        val payload = ByteArray(length)
        buf.get(payload)

        when (pkgType) {
            TYPE_HANDSHAKE -> {
                Log.i(TAG, "[CALL-AUDIO] Handshake ACK from server")
                isHandshakeComplete = true
                val ws = webSocket
                if (ws != null) {
                    drainQueues(ws)
                }
            }
            TYPE_AUDIO -> {
                if (!firstAudioReported) {
                    firstAudioReported = true
                    Log.i(TAG, "[CALL-AUDIO] First audio packet received!")
                    onMediaEvent?.invoke("AUDIO_CONNECTED")
                }
                onAudioPacketReceived(payload)
            }
            TYPE_VIDEO -> {
                val bmp = BitmapFactory.decodeByteArray(payload, 0, payload.size)
                if (bmp != null) {
                    if (!firstVideoReported) {
                        firstVideoReported = true
                        Log.i(TAG, "[CALL-VIDEO] First video packet received!")
                        onMediaEvent?.invoke("VIDEO_CONNECTED")
                    }
                    _remoteVideoBitmap.value = bmp
                }
            }
            TYPE_PING -> {
            }
        }
    }

    private fun drainQueues(ws: WebSocket) {
        var audioDrained = 0
        while (true) {
            val frame = audioQueue.poll() ?: break
            sendPacketDirect(ws, TYPE_AUDIO, frame)
            audioDrained++
        }
        var videoDrained = 0
        while (true) {
            val frame = videoQueue.poll() ?: break
            sendPacketDirect(ws, TYPE_VIDEO, frame)
            videoDrained++
        }
        if (audioDrained > 0 || videoDrained > 0) {
            Log.i(TAG, "[CALL-AUDIO] Drained $audioDrained audio + $videoDrained video frames after handshake")
        }
    }

    private fun sendPacket(ws: WebSocket, type: Byte, payload: ByteArray) {
        sendPacketDirect(ws, type, payload)
    }

    private fun sendPacketDirect(ws: WebSocket, type: Byte, payload: ByteArray) {
        try {
            val buf = ByteArray(5 + payload.size)
            buf[0] = type
            val len = payload.size
            buf[1] = (len ushr 24).toByte()
            buf[2] = (len ushr 16).toByte()
            buf[3] = (len ushr 8).toByte()
            buf[4] = len.toByte()
            System.arraycopy(payload, 0, buf, 5, payload.size)
            ws.send(buf.toByteString())
        } catch (e: Exception) {
            Log.w(TAG, "[CALL-AUDIO] sendPacket error: ${e.message}")
        }
    }

    fun sendAudio(pcmBytes: ByteArray) {
        val ws = webSocket
        if (ws != null && isConnected) {
            try {
                val buf = ByteArray(5 + pcmBytes.size)
                buf[0] = TYPE_AUDIO
                val len = pcmBytes.size
                buf[1] = (len ushr 24).toByte()
                buf[2] = (len ushr 16).toByte()
                buf[3] = (len ushr 8).toByte()
                buf[4] = len.toByte()
                System.arraycopy(pcmBytes, 0, buf, 5, pcmBytes.size)
                ws.send(buf.toByteString())
            } catch (e: Exception) {
                Log.w(TAG, "[CALL-AUDIO] sendAudio dropped: ${e.message}")
            }
        } else if (isRunning) {
            if (!audioQueue.offer(pcmBytes)) {
                audioQueue.poll()
                audioQueue.offer(pcmBytes)
            }
        }
    }

    fun sendVideo(jpegBytes: ByteArray) {
        val ws = webSocket
        if (ws != null && isConnected) {
            try {
                val buf = ByteArray(5 + jpegBytes.size)
                buf[0] = TYPE_VIDEO
                val len = jpegBytes.size
                buf[1] = (len ushr 24).toByte()
                buf[2] = (len ushr 16).toByte()
                buf[3] = (len ushr 8).toByte()
                buf[4] = len.toByte()
                System.arraycopy(jpegBytes, 0, buf, 5, jpegBytes.size)
                ws.send(buf.toByteString())
            } catch (e: Exception) {
                Log.w(TAG, "[CALL-VIDEO] sendVideo dropped: ${e.message}")
            }
        } else if (isRunning) {
            videoQueue.clear()
            videoQueue.offer(jpegBytes)
        }
    }

    fun close() {
        isRunning = false
        isConnected = false
        isHandshakeComplete = false
        firstAudioReported = false
        firstVideoReported = false
        audioQueue.clear()
        videoQueue.clear()
        connectionJob?.cancel()
        connectionJob = null
        try { webSocket?.close(1000, "Call ended") } catch (_: Exception) {}
        webSocket = null
        _remoteVideoBitmap.value = null
        Log.i(TAG, "[CALL-AUDIO] CallMediaClient closed")
    }
}
