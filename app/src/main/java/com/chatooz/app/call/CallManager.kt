package com.chatooz.app.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chatooz.app.MainActivity
import com.chatooz.app.data.AppConfig
import com.chatooz.app.data.ChatoozCloudApi
import com.chatooz.app.data.ChatoozStorage
import com.chatooz.app.model.Chat
import com.chatooz.app.model.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class CallState {
    IDLE,
    CALLING,
    RINGING,
    ACCEPTED,
    CONNECTING,
    AUDIO_CONNECTED,
    VIDEO_CONNECTED,
    CONNECTED,
    REJECTED,
    BUSY,
    NO_ANSWER,
    FAILED,
    NETWORK_ERROR,
    ENDED
}

data class CallInfo(
    val callId: String,
    val remoteUserId: String,
    val remoteUsername: String,
    val remoteName: String,
    val remoteAvatarColor: Long,
    val isVideo: Boolean,
    val isIncoming: Boolean
)

data class Signal(val id: Int, val type: String, val payload: String, val senderId: String)

object CallManager {

    private const val TAG = "CallManager"
    private const val CALL_CHANNEL_ID = "chatooz_incoming_calls"
    private const val CALL_NOTIF_ID = 9001
    private const val CALL_TIMEOUT_MS = 35_000L // 35s ring timeout

    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_IN  = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val ENCODING    = AudioFormat.ENCODING_PCM_16BIT
    private const val CHUNK_MS    = 20L

    private var appCtx: Context? = null

    private val signalClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Bypass-Tunnel-Reminder", "true")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // ─── Call State & Flow ────────────────────────────────────────────────────
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callStatusMessage = MutableStateFlow("")
    val callStatusMessage: StateFlow<String> = _callStatusMessage.asStateFlow()

    private val _callInfo = MutableStateFlow<CallInfo?>(null)
    val callInfo: StateFlow<CallInfo?> = _callInfo.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    private val _remoteVideoBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteVideoBitmap: StateFlow<Bitmap?> = _remoteVideoBitmap.asStateFlow()

    // Jobs
    private var pollJob: Job? = null
    private var bgPollJob: Job? = null
    private var timerJob: Job? = null
    private var audioRecordJob: Job? = null
    private var audioPlaybackJob: Job? = null
    private var timeoutJob: Job? = null
    private var ringbackJob: Job? = null

    private val audioPlaybackQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>(60)

    private var currentCallId: String? = null
    private var myUserId: String? = null
    private var lastPollSeq = 0
    private var lastBgSeq = 0

    // Audio & Tone Generators
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLock(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
            releaseWakeLock()
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "Chatooz:IncomingCallWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(35_000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire error: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private var mediaClient: CallMediaClient? = null
    var cameraManager: CameraManager? = null
        private set
    private var currentSurfaceTexture: SurfaceTexture? = null

    fun init(context: Context) {
        appCtx = context.applicationContext
        cameraManager = CameraManager(context.applicationContext)
        createNotificationChannel()
    }

    // ─── Initiate Outgoing Call ───────────────────────────────────────────────
    fun initiateCall(
        scope: CoroutineScope,
        myUserId: String,
        myUserName: String,
        myUserUsername: String,
        myUserAvatarColor: Long,
        remoteUserId: String,
        remoteUsername: String,
        remoteName: String,
        remoteAvatarColor: Long,
        isVideo: Boolean
    ) {
        if (_callState.value != CallState.IDLE) return
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        this.myUserId = myUserId
        currentCallId = callId
        lastPollSeq = 0

        Log.i(TAG, "[CALL] initiateCall: callId=$callId to=$remoteUserId isVideo=$isVideo")

        val info = CallInfo(
            callId = callId,
            remoteUserId = remoteUserId,
            remoteUsername = remoteUsername,
            remoteName = remoteName,
            remoteAvatarColor = remoteAvatarColor,
            isVideo = isVideo,
            isIncoming = false
        )
        _callInfo.value = info
        _callState.value = CallState.CALLING
        _callStatusMessage.value = "Calling @$remoteUsername…"
        _callDurationSeconds.value = 0
        _isSpeakerOn.value = true
        requestAudioFocus()
        configureAudioMode(speaker = true)

        // Play outgoing ringback tone for the caller
        startRingbackTone(scope)

        reportDiagnosticEvent(callId, "CALL_INITIATE", "INFO", "Call initiated by local user: $myUserName")
        // Send offer to direct call channel and incoming channel
        scope.launch(Dispatchers.IO) {
            val offerPayload = JSONObject().apply {
                put("callId", callId)
                put("callerId", myUserId)
                put("calleeId", remoteUserId)
                put("callerName", myUserName.ifBlank { myUserUsername.ifBlank { "User" } })
                put("callerUsername", myUserUsername)
                put("callerAvatarColor", myUserAvatarColor)
                put("isVideo", isVideo)
            }
            sendSignal(callId, "offer", offerPayload.toString(), myUserId)
            sendSignal("incoming_$remoteUserId", "offer", offerPayload.toString(), myUserId)
            withContext(Dispatchers.Main) {
                if (_callState.value == CallState.CALLING) {
                    _callStatusMessage.value = "Call request sent…"
                }
            }
        }

        // 35s Call Timeout (No Answer)
        startTimeoutWatcher(scope)

        // Pre-warm media engine immediately so WebSocket is ready before callee answers (0s delay)
        startMediaEngine(scope, callId, myUserId)

        startPolling(scope, callId, isCallee = false)
    }

    // ─── Accept Incoming Call ─────────────────────────────────────────────────
    fun acceptCall(scope: CoroutineScope) {
        val info = _callInfo.value ?: return
        val callId = info.callId
        currentCallId = callId
        val myId = myUserId ?: ""

        Log.i(TAG, "[CALL] acceptCall: callId=$callId isVideo=${info.isVideo}")

        stopRingtone()
        stopRingbackTone()
        timeoutJob?.cancel()
        cancelIncomingNotification()
        releaseWakeLock()

        requestAudioFocus()

        _callState.value = CallState.CONNECTED
        _callStatusMessage.value = "Connected"
        reportDiagnosticEvent(callId, "CALL_ANSWER_ACCEPTED", "SUCCESS", "Call accepted and audio stream connecting", result = "COMPLETED")
        if (callConnectedStartTimeMs == 0L) callConnectedStartTimeMs = System.currentTimeMillis()
        _isSpeakerOn.value = true
        configureAudioMode(speaker = true)
        startCallTimer(scope)

        scope.launch(Dispatchers.IO) {
            val answerPayload = JSONObject().apply { put("accepted", true) }
            sendSignal(callId, "answer", answerPayload.toString(), myId)
        }

        startMediaEngine(scope, callId, myId)
        startPolling(scope, callId, isCallee = true)
    }

    // ─── Decline Incoming Call ────────────────────────────────────────────────
    fun declineCall(scope: CoroutineScope) {
        val info = _callInfo.value ?: return
        val myId = myUserId ?: ""
        Log.i(TAG, "[CALL] declineCall: callId=${info.callId}")
        stopRingtone()
        stopRingbackTone()
        timeoutJob?.cancel()
        cancelIncomingNotification()
        releaseWakeLock()
        scope.launch(Dispatchers.IO) {
            val declinePayload = JSONObject().apply {
                put("accepted", false)
                put("reason", "rejected")
            }
            sendSignal(info.callId, "answer", declinePayload.toString(), myId)
        }
        endCallWithState(CallState.REJECTED, "Call declined", scope)
    }

    // ─── End Call ─────────────────────────────────────────────────────────────
    fun endCall(scope: CoroutineScope) {
        val callId = currentCallId
        val myId = myUserId ?: ""
        Log.i(TAG, "[CALL] endCall: callId=$callId")
        if (callId != null) {
            scope.launch(Dispatchers.IO) {
                sendSignal(callId, "end", "{\"ended\":true}", myId)
            }
        }
        endCallWithState(CallState.ENDED, "Call ended", scope)
    }

    // ─── Handle Incoming Offer ────────────────────────────────────────────────
    private fun handleIncomingOffer(
        callId: String,
        payload: JSONObject,
        scope: CoroutineScope
    ) {
        // If already in a call, notify caller that user is busy
        if (_callState.value != CallState.IDLE) {
            Log.w(TAG, "[CALL] User busy, rejecting new call: $callId")
            scope.launch(Dispatchers.IO) {
                val busyPayload = JSONObject().apply {
                    put("accepted", false)
                    put("reason", "busy")
                }
                sendSignal(callId, "answer", busyPayload.toString(), myUserId ?: "")
            }
            return
        }

        val callerId          = payload.optString("callerId", "")
        val callerName        = payload.optString("callerName", callerId)
        val callerUsername    = payload.optString("callerUsername", callerName)
        val callerAvatarColor = payload.optLong("callerAvatarColor", 0xFF6366F1L)
        val isVideo           = payload.optBoolean("isVideo", false)
        this.myUserId         = payload.optString("calleeId", myUserId ?: "")
        currentCallId         = callId
        lastPollSeq           = 0

        Log.i(TAG, "[CALL] Incoming offer: callId=$callId from=$callerId isVideo=$isVideo")

        val info = CallInfo(
            callId = callId,
            remoteUserId = callerId,
            remoteUsername = callerUsername,
            remoteName = callerName,
            remoteAvatarColor = callerAvatarColor,
            isVideo = isVideo,
            isIncoming = true
        )
        _callInfo.value = info
        _callState.value = CallState.RINGING
        _callStatusMessage.value = "Incoming call…"
        _isSpeakerOn.value = true

        startRingtoneAndVibration()
        showIncomingCallNotification(callerName, callerUsername)

        appCtx?.let { ctx ->
            acquireWakeLock(ctx)
            try {
                val actIntent = Intent(ctx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("action", "INCOMING_CALL")
                    putExtra("callId", callId)
                    putExtra("callerName", callerName)
                    putExtra("callerUsername", callerUsername)
                    putExtra("isVideo", isVideo)
                }
                ctx.startActivity(actIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch MainActivity on incoming call: ${e.message}")
            }
        }

        // Inform caller that call was delivered & is ringing
        scope.launch(Dispatchers.IO) {
            sendSignal(callId, "delivered", "{\"ringing\":true}", myUserId ?: "")
        }

        startTimeoutWatcher(scope)
        startPolling(scope, callId, isCallee = true)
    }

    // ─── Tone & Feedback ──────────────────────────────────────────────────────
    private fun startRingbackTone(scope: CoroutineScope) {
        ringbackJob?.cancel()
        ringbackJob = scope.launch(Dispatchers.IO) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
                while (isActive && (_callState.value == CallState.CALLING || _callState.value == CallState.RINGING)) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1200)
                    delay(3500L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ringback tone error: ${e.message}")
            }
        }
    }

    private fun stopRingbackTone() {
        ringbackJob?.cancel()
        ringbackJob = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
    }

    private fun startTimeoutWatcher(scope: CoroutineScope) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(CALL_TIMEOUT_MS)
            if (_callState.value == CallState.CALLING || _callState.value == CallState.RINGING) {
                Log.i(TAG, "[CALL] Call timed out (no answer)")
                endCallWithState(CallState.NO_ANSWER, "No answer", scope)
            }
        }
    }

    private var callConnectedStartTimeMs: Long = 0L

    private fun endCallWithState(state: CallState, message: String, scope: CoroutineScope) {
        val info = _callInfo.value
        val durationSec = if (callConnectedStartTimeMs > 0) {
            ((System.currentTimeMillis() - callConnectedStartTimeMs) / 1000).toInt()
        } else {
            0
        }
        callConnectedStartTimeMs = 0L

        _callState.value = state
        _callStatusMessage.value = message
        stopRingbackTone()
        stopRingtone()
        timeoutJob?.cancel()
        cancelIncomingNotification()

        if (info != null) {
            logCallMessage(state, info, durationSec, scope)
            val termReason = when (state) {
                CallState.NO_ANSWER -> "CALL_TIMEOUT"
                CallState.REJECTED -> "REMOTE_ENDED"
                CallState.NETWORK_ERROR -> "NETWORK_ERROR"
                CallState.ENDED -> "LOCAL_ENDED"
                else -> "LOCAL_ENDED"
            }
            val resStr = if (state == CallState.NO_ANSWER || state == CallState.NETWORK_ERROR || state == CallState.FAILED) "FAILED" else "COMPLETED"
            reportDiagnosticEvent(
                callId = info.callId,
                event = "CALL_TERMINATED_${state.name}",
                status = if (state == CallState.ENDED) "INFO" else "WARN",
                details = message,
                result = resStr,
                terminationReason = termReason,
                durationSec = durationSec
            )
        }

        scope.launch {
            delay(1800L) // Brief delay so user sees "Call ended / No answer / Busy"
            resetState()
        }
    }

    private fun logCallMessage(state: CallState, info: CallInfo, durationSec: Int, scope: CoroutineScope) {
        val ctx = appCtx ?: return
        val myId = myUserId ?: return
        val remoteId = info.remoteUserId
        if (remoteId.isBlank() || myId.isBlank()) return

        val storage = ChatoozStorage(ctx)
        val chatId = storage.chatIdFor(myId, remoteId)
        val isVideo = info.isVideo
        val isIncoming = info.isIncoming

        val callText = when {
            durationSec > 0 -> {
                val mins = durationSec / 60
                val secs = durationSec % 60
                val durStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                if (isVideo) "📹 Video call ($durStr)" else "📞 Voice call ($durStr)"
            }
            state == CallState.REJECTED -> {
                if (isIncoming) {
                    if (isVideo) "📹 Declined video call" else "📞 Declined voice call"
                } else {
                    if (isVideo) "📹 Video call declined" else "📞 Voice call declined"
                }
            }
            state == CallState.NO_ANSWER -> {
                if (isIncoming) {
                    if (isVideo) "📹 Missed video call" else "📞 Missed voice call"
                } else {
                    if (isVideo) "📹 Unanswered video call" else "📞 Unanswered voice call"
                }
            }
            else -> {
                if (isIncoming) {
                    if (isVideo) "📹 Missed video call" else "📞 Missed voice call"
                } else {
                    if (isVideo) "📹 Cancelled video call" else "📞 Cancelled voice call"
                }
            }
        }

        val msgId = "call_${System.currentTimeMillis()}"
        val message = Message(
            id = msgId,
            chatId = chatId,
            senderId = if (isIncoming) remoteId else myId,
            text = callText,
            timestamp = System.currentTimeMillis(),
            isFromMe = !isIncoming,
            status = "READ",
            type = "CALL",
            audioDurationSec = durationSec
        )

        storage.addMessage(chatId, message)

        val friend = storage.getUserById(remoteId)
        val fallbackStatus = storage.getStatuses().find { it.userId == remoteId }
        val chat = Chat(
            id = chatId,
            friendId = remoteId,
            friendName = friend?.name ?: info.remoteName,
            friendUsername = friend?.username ?: info.remoteUsername,
            friendAvatarColor = friend?.avatarColor ?: info.remoteAvatarColor,
            friendAvatarUrl = friend?.avatarUrl?.ifBlank { null } ?: fallbackStatus?.userAvatarUrl?.ifBlank { null },
            lastMessageText = callText,
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0
        )
        storage.upsertChat(myId, chat)

        scope.launch(Dispatchers.IO) {
            ChatoozCloudApi.sendMessageDirect(message)
            storage.syncWithCloud()
        }
    }

    // ─── Controls ─────────────────────────────────────────────────────────────
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleSpeaker() {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        configureAudioMode(speaker = next)
    }

    fun toggleCamera() {
        _isCameraOn.value = !_isCameraOn.value
    }

    fun switchCamera(surfaceTexture: SurfaceTexture?) {
        val st = surfaceTexture ?: currentSurfaceTexture
        cameraManager?.switchCamera(st) { jpegBytes ->
            if (_isCameraOn.value) {
                mediaClient?.sendVideo(jpegBytes)
            }
        }
    }

    fun startLocalCameraPreview(surfaceTexture: SurfaceTexture) {
        currentSurfaceTexture = surfaceTexture
        cameraManager?.startCamera(surfaceTexture) { jpegBytes ->
            if (_isCameraOn.value && (_callState.value == CallState.CONNECTED || _callState.value == CallState.CONNECTING)) {
                mediaClient?.sendVideo(jpegBytes)
            }
        }
    }

    // ─── Polling Signaling ────────────────────────────────────────────────────
    private fun startPolling(scope: CoroutineScope, callId: String, isCallee: Boolean) {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            val uid = myUserId ?: ""
            while (isActive && _callState.value != CallState.IDLE) {
                try {
                    val signals = pollSignals(callId, uid, lastPollSeq)
                    for (sig in signals) {
                        if (sig.id > lastPollSeq) {
                            lastPollSeq = sig.id
                        }
                        when (sig.type) {
                            "delivered" -> {
                                withContext(Dispatchers.Main) {
                                    if (_callState.value == CallState.CALLING) {
                                        _callState.value = CallState.RINGING
                                        _callStatusMessage.value = "Ringing…"
                                    }
                                }
                            }
                            "answer" -> {
                                val p = JSONObject(sig.payload)
                                val accepted = p.optBoolean("accepted", false)
                                val reason = p.optString("reason", "")
                                withContext(Dispatchers.Main) {
                                    stopRingbackTone()
                                    timeoutJob?.cancel()
                                    if (accepted && (_callState.value == CallState.CALLING || _callState.value == CallState.RINGING)) {
                                        Log.i(TAG, "[CALL] Call accepted by remote peer")
                                        requestAudioFocus()
                                        _callState.value = CallState.CONNECTED
                                        _callStatusMessage.value = "Connected"
                                        if (callConnectedStartTimeMs == 0L) callConnectedStartTimeMs = System.currentTimeMillis()
                                        _isSpeakerOn.value = true
                                        configureAudioMode(speaker = true)
                                        startCallTimer(scope)
                                        if (mediaClient == null) {
                                            startMediaEngine(scope, callId, uid)
                                        }
                                    } else if (!accepted) {
                                        if (reason == "busy") {
                                            endCallWithState(CallState.BUSY, "User is busy", scope)
                                        } else {
                                            endCallWithState(CallState.REJECTED, "Call declined", scope)
                                        }
                                    }
                                }
                            }
                            "end" -> {
                                withContext(Dispatchers.Main) {
                                    endCallWithState(CallState.ENDED, "Call ended", scope)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                }
                delay(100L)
            }
        }
    }

    fun startBackgroundIncomingCallPoll(scope: CoroutineScope, userId: String) {
        this.myUserId = userId
        val bgChannel = "incoming_$userId"
        bgPollJob?.cancel()
        bgPollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    if (_callState.value == CallState.IDLE) {
                        val signals = pollSignals(bgChannel, userId, lastBgSeq)
                        for (sig in signals) {
                            if (sig.id > lastBgSeq) {
                                lastBgSeq = sig.id
                            }
                            if (sig.type == "offer") {
                                val payload = JSONObject(sig.payload)
                                val calleeId = payload.optString("calleeId", "")
                                if (calleeId == userId) {
                                    val callId = payload.optString("callId", "")
                                    if (callId.isNotBlank()) {
                                        withContext(Dispatchers.Main) {
                                            handleIncomingOffer(callId, payload, scope)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                delay(200L)
            }
        }
    }

    // ─── Media Engine ─────────────────────────────────────────────────────────
    private fun startMediaEngine(scope: CoroutineScope, callId: String, userId: String) {
        if (mediaClient != null) return
        Log.i(TAG, "[CALL] Starting media engine: callId=$callId userId=$userId")

        initAudioTrack()
        audioPlaybackQueue.clear()
        startAudioPlayback(scope)

        val client = CallMediaClient(
            callId = callId,
            myUserId = userId,
            onAudioPacketReceived = { audioBytes ->
                // Feed jitter buffer
                if (!audioPlaybackQueue.offer(audioBytes)) {
                    audioPlaybackQueue.poll()
                    audioPlaybackQueue.offer(audioBytes)
                }
                // Latency cap: bounded to max 12 packets (~240ms) to absorb jitter bursts without loss
                while (audioPlaybackQueue.size > 12) {
                    audioPlaybackQueue.poll()
                }
            },
            onMediaEvent = { event ->
                scope.launch(Dispatchers.Main) {
                    if (_callState.value == CallState.CONNECTING || _callState.value == CallState.CALLING || _callState.value == CallState.RINGING) {
                        _callState.value = CallState.CONNECTED
                        _callStatusMessage.value = "Connected"
                        if (callConnectedStartTimeMs == 0L) callConnectedStartTimeMs = System.currentTimeMillis()
                    }
                }
            }
        )
        mediaClient = client

        client.start(scope)

        scope.launch {
            client.remoteVideoBitmap.collect { bmp ->
                _remoteVideoBitmap.value = bmp
                if (bmp != null && (_callState.value == CallState.CONNECTING || _callState.value == CallState.CALLING)) {
                    _callState.value = CallState.CONNECTED
                    _callStatusMessage.value = "Connected"
                    if (callConnectedStartTimeMs == 0L) callConnectedStartTimeMs = System.currentTimeMillis()
                }
            }
        }

        startAudioRecord(scope, client)

        if (_callInfo.value?.isVideo == true) {
            val st = currentSurfaceTexture
            if (st != null) {
                startLocalCameraPreview(st)
            }
        }
    }

    private fun startAudioPlayback(scope: CoroutineScope) {
        audioPlaybackJob?.cancel()
        audioPlaybackJob = scope.launch(Dispatchers.IO) {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            } catch (_: Exception) {}

            var isPreBuffered = false

            while (isActive && (_callState.value != CallState.IDLE && _callState.value != CallState.ENDED && _callState.value != CallState.REJECTED)) {
                // Adaptive pre-buffer: wait for 2 packets before writing to prevent hardware underrun clicks
                if (!isPreBuffered) {
                    if (audioPlaybackQueue.size >= 2) {
                        isPreBuffered = true
                    } else {
                        delay(5L)
                        continue
                    }
                }

                val packet = audioPlaybackQueue.poll(10, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (packet != null && packet.isNotEmpty()) {
                    audioTrack?.let { track ->
                        try {
                            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                track.play()
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                track.write(packet, 0, packet.size, AudioTrack.WRITE_BLOCKING)
                            } else {
                                track.write(packet, 0, packet.size)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[CALL] AudioTrack write error: ${e.message}")
                        }
                    }
                } else {
                    if (audioPlaybackQueue.isEmpty()) {
                        isPreBuffered = false
                    }
                }
            }
        }
    }

    private fun initAudioTrack() {
        try {
            audioTrack?.release()
            audioTrack = null

            val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            val chunkSize = (SAMPLE_RATE * 2 * CHUNK_MS / 1000).toInt()
            val bufSize = maxOf(minBuf * 4, chunkSize * 10)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .setEncoding(ENCODING)
                .build()

            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    audioAttributes,
                    audioFormat,
                    bufSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            }
            track.play()
            audioTrack = track
            Log.i(TAG, "[CALL] AudioTrack initialized with bufSize=$bufSize")
        } catch (e: Exception) {
            Log.e(TAG, "[CALL] Failed to init AudioTrack: ${e.message}")
        }
    }

    private fun startAudioRecord(scope: CoroutineScope, client: CallMediaClient) {
        audioRecordJob?.cancel()
        audioRecordJob = scope.launch(Dispatchers.IO) {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            } catch (_: Exception) {}

            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
            val chunkSize = (SAMPLE_RATE * 2 * CHUNK_MS / 1000).toInt()
            val bufSize = maxOf(minBuf * 2, chunkSize * 6)

            var recorder: AudioRecord? = null
            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )

            for (src in audioSources) {
                try {
                    val candidate = AudioRecord(src, SAMPLE_RATE, CHANNEL_IN, ENCODING, bufSize)
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        recorder = candidate
                        Log.i(TAG, "[CALL] AudioRecord initialized with source=$src")
                        break
                    } else {
                        candidate.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[CALL] AudioRecord init attempt failed for src $src: ${e.message}")
                }
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "[CALL] AudioRecord initialization failed on all sources")
                return@launch
            }

            val sessionId = recorder.audioSessionId
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                        Log.i(TAG, "[CALL] AcousticEchoCanceler enabled on session $sessionId")
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                        Log.i(TAG, "[CALL] NoiseSuppressor enabled on session $sessionId")
                    }
                }
                if (AutomaticGainControl.isAvailable()) {
                    gainControl = AutomaticGainControl.create(sessionId)?.apply {
                        enabled = true
                        Log.i(TAG, "[CALL] AutomaticGainControl enabled on session $sessionId")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[CALL] AudioFX attach error: ${e.message}")
            }

            audioRecord = recorder
            try {
                recorder.startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "[CALL] startRecording failed: ${e.message}")
                return@launch
            }

            val buf = ByteArray(chunkSize)
            while (isActive && (_callState.value != CallState.IDLE && _callState.value != CallState.ENDED && _callState.value != CallState.REJECTED && _callState.value != CallState.BUSY && _callState.value != CallState.NO_ANSWER)) {
                // If not yet connected or connecting, idle without terminating the recorder
                if (_callState.value != CallState.CONNECTED && _callState.value != CallState.CONNECTING) {
                    delay(15L)
                    continue
                }
                if (_isMuted.value) {
                    delay(CHUNK_MS)
                    continue
                }
                val read = recorder.read(buf, 0, buf.size)
                if (read > 0) {
                    client.sendAudio(buf.copyOf(read))
                }
            }

            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
            } catch (_: Exception) {}
            audioRecord = null
        }
    }

    private fun requestAudioFocus() {
        val ctx = appCtx ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest = focusReq
                am.requestAudioFocus(focusReq)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {}
    }

    private fun releaseAudioFocus() {
        val ctx = appCtx ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun startRingtoneAndVibration() {
        val ctx = appCtx ?: return
        com.chatooz.app.util.SoundManager.startIncomingCallRingtone(ctx)
    }

    private fun stopRingtone() {
        com.chatooz.app.util.SoundManager.stopIncomingCallRingtone()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ctx = appCtx ?: return
            val chan = NotificationChannel(
                CALL_CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chatooz incoming call alerts"
                setShowBadge(false)
            }
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(chan)
        }
    }

    private fun showIncomingCallNotification(callerName: String, callerUsername: String) {
        val ctx = appCtx ?: return
        val isVideo = _callInfo.value?.isVideo ?: false
        val callId = currentCallId ?: ""
        com.chatooz.app.notification.NotificationHelper.showIncomingCallNotification(
            context = ctx,
            callId = callId,
            callerName = callerName,
            callerUsername = callerUsername,
            isVideo = isVideo
        )
    }

    private fun cancelIncomingNotification() {
        val ctx = appCtx ?: return
        com.chatooz.app.notification.NotificationHelper.dismissCallNotification(ctx)
    }

    private fun configureAudioMode(speaker: Boolean) {
        val ctx = appCtx ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val targetType = if (speaker) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                val targetDevice = devices.find { it.type == targetType }
                    ?: devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (targetDevice != null) {
                    am.setCommunicationDevice(targetDevice)
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = speaker
            }
            val streamType = AudioManager.STREAM_VOICE_CALL
            val maxVol = am.getStreamMaxVolume(streamType)
            am.setStreamVolume(streamType, maxVol, 0)
        } catch (e: Exception) {
            Log.w(TAG, "configureAudioMode error: ${e.message}")
        }
    }

    private fun restoreAudioMode() {
        val ctx = appCtx ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
            }
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
    }

    private fun startCallTimer(scope: CoroutineScope) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && (_callState.value == CallState.CONNECTED || _callState.value == CallState.CONNECTING)) {
                delay(1000L)
                _callDurationSeconds.value += 1
            }
        }
    }

    private fun resetState() {
        Log.i(TAG, "[CALL] resetState() called")
        pollJob?.cancel()
        timerJob?.cancel()
        timeoutJob?.cancel()
        audioRecordJob?.cancel()
        audioPlaybackJob?.cancel()
        audioPlaybackQueue.clear()
        stopRingbackTone()
        stopRingtone()
        cancelIncomingNotification()
        releaseWakeLock()
        releaseAudioFocus()
        restoreAudioMode()

        cameraManager?.stopCamera()
        mediaClient?.close()
        mediaClient = null
        currentSurfaceTexture = null

        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            gainControl?.release()
        } catch (_: Exception) {}
        gainControl = null

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.let {
                it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        audioTrack = null

        _callState.value = CallState.IDLE
        _callStatusMessage.value = ""
        _callInfo.value = null
        _isMuted.value = false
        _isSpeakerOn.value = true
        _isCameraOn.value = true
        _callDurationSeconds.value = 0
        _remoteVideoBitmap.value = null
        currentCallId = null
    }

    
    private val diagScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun reportDiagnosticEvent(
        callId: String,
        event: String,
        status: String = "INFO",
        details: String = "",
        result: String? = null,
        terminationReason: String? = null,
        networkType: String? = null,
        durationSec: Int? = null
    ) {
        val signalUrl = AppConfig.callSignalUrl
        val url = if (signalUrl.contains("/call/signal")) {
            signalUrl.substringBefore("/call/signal") + "/api/call-diagnostics/event"
        } else {
            signalUrl.trimEnd('/') + "/api/call-diagnostics/event"
        }
        diagScope.launch {
            try {
                val body = JSONObject().apply {
                    put("callId", callId)
                    put("event", event)
                    put("status", status)
                    put("details", details)
                    result?.let { put("result", it) }
                    terminationReason?.let { put("termination_reason", it) }
                    networkType?.let { put("network_type", it) }
                    durationSec?.let { put("duration_sec", it) }
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Content-Type", "application/json")
                    .build()

                signalClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.d(TAG, "reportDiagnosticEvent error: ${e.message}")
            }
        }
    }

    private fun sendSignal(callId: String, type: String, payload: String, senderId: String) {
        val url = AppConfig.callSignalUrl
        try {
            val body = JSONObject().apply {
                put("callId", callId)
                put("senderId", senderId)
                put("type", type)
                put("payload", payload)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .build()

            val response = signalClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "sendSignal HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendSignal failed: ${e.message}")
        }
    }

    private fun pollSignals(callId: String, userId: String, afterSeq: Int): List<Signal> {
        val url = "${AppConfig.callSignalUrl}?callId=$callId&userId=$userId&afterSeq=$afterSeq"
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = signalClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val responseText = response.body?.string() ?: return emptyList()
            val json = JSONObject(responseText)
            val msgs = json.optJSONArray("messages") ?: JSONArray()
            val result = mutableListOf<Signal>()
            for (i in 0 until msgs.length()) {
                val obj = msgs.getJSONObject(i)
                result.add(
                    Signal(
                        id = obj.optInt("id", 0),
                        type = obj.optString("type", ""),
                        payload = obj.optString("payload", "{}"),
                        senderId = obj.optString("senderId", "")
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun inviteParticipantToCall(
        scope: CoroutineScope,
        myUserId: String,
        myUserName: String,
        myUserUsername: String,
        myUserAvatarColor: Long,
        remoteUserId: String,
        remoteUsername: String,
        remoteName: String
    ) {
        val info = _callInfo.value ?: return
        scope.launch(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("callerId", myUserId)
                put("callerUsername", myUserUsername)
                put("callerName", myUserName)
                put("callerAvatarColor", myUserAvatarColor)
                put("receiverId", remoteUserId)
                put("receiverUsername", remoteUsername)
                put("receiverName", remoteName)
                put("isVideo", info.isVideo)
                put("isConference", true)
            }.toString()
            sendSignal(info.callId, "offer", payload, myUserId)
        }
    }
}
