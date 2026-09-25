package com.chatooz.app.webrtc.session

import android.content.Context
import android.util.Log
import com.chatooz.app.webrtc.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * AndroidWebRtcSession — Production WebRTC Session managing PeerConnection,
 * Audio/Video Tracks, ICE exchange, and candidate queueing.
 */
class AndroidWebRtcSession(
    private val context: Context,
    override val sessionId: String,
    private val myUserId: String,
    private val remoteUserId: String,
    private val onRemoteAudioTrack: ((AudioTrack) -> Unit)? = null,
    private val onRemoteVideoTrack: ((VideoTrack) -> Unit)? = null,
    private val onIceCandidateGenerated: ((SignalingMessage) -> Unit)? = null
) : WebRtcSession {

    companion object {
        private const val TAG = "AndroidWebRtcSession"
        private var factoryInitialized = false

        fun initializeFactory(context: Context) {
            if (!factoryInitialized) {
                val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                factoryInitialized = true
                Log.i(TAG, "PeerConnectionFactory globally initialized")
            }
        }
    }

    private val _diagnostics = MutableStateFlow(WebRtcDiagnostics())
    override val diagnostics: StateFlow<WebRtcDiagnostics> = _diagnostics.asStateFlow()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var rootEglBase: EglBase? = null

    private val queuedIceCandidates = ConcurrentLinkedQueue<IceCandidate>()
    private var isRemoteDescriptionSet = false

    override suspend fun initialize(config: WebRtcConfig) = withContext(Dispatchers.IO) {
        initializeFactory(context)
        rootEglBase = EglBase.create()

        val audioDeviceModule = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase?.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase?.eglBaseContext)

        val factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        peerConnectionFactory = factory

        // Build RTCConfiguration
        val iceServersList = config.iceServers.map { server ->
            val builder = PeerConnection.IceServer.builder(server.uri)
            if (!server.username.isNullOrBlank()) builder.setUsername(server.username)
            if (!server.credential.isNullOrBlank()) builder.setPassword(server.credential)
            builder.createIceServer()
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServersList).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pcObserver = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                _diagnostics.value = _diagnostics.value.copy(signalingState = state.name)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.i(TAG, "ICE connection state changed: $state")
                _diagnostics.value = _diagnostics.value.copy(iceConnectionState = state.name)
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.i(TAG, "ICE connection receiving changed: $receiving")
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                Log.i(TAG, "PeerConnection state changed: $state")
                _diagnostics.value = _diagnostics.value.copy(connectionState = state.name)
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                _diagnostics.value = _diagnostics.value.copy(iceGatheringState = state.name)
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                Log.i(TAG, "Local ICE Candidate generated: ${candidate.sdpMid} -> ${candidate.sdp}")
                val sdpLower = candidate.sdp.lowercase()
                val isRelay = sdpLower.contains("typ relay")
                val isSrflx = sdpLower.contains("typ srflx")
                val isHost = sdpLower.contains("typ host")

                _diagnostics.value = _diagnostics.value.copy(
                    localRelayCandidateCount = _diagnostics.value.localRelayCandidateCount + (if (isRelay) 1 else 0),
                    localSrflxCandidateCount = _diagnostics.value.localSrflxCandidateCount + (if (isSrflx) 1 else 0),
                    localHostCandidateCount = _diagnostics.value.localHostCandidateCount + (if (isHost) 1 else 0),
                    isTurnRelay = _diagnostics.value.isTurnRelay || isRelay
                )

                val payload = JSONObject().apply {
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("sdp", candidate.sdp)
                }.toString()

                val sigMsg = SignalingMessage(
                    callId = sessionId,
                    senderId = myUserId,
                    receiverId = remoteUserId,
                    type = SignalingType.ICE_CANDIDATE,
                    payload = payload
                )
                onIceCandidateGenerated?.invoke(sigMsg)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is AudioTrack) {
                    Log.i(TAG, "Remote Audio Track received")
                    _diagnostics.value = _diagnostics.value.copy(remoteAudioTrackActive = true)
                    onRemoteAudioTrack?.invoke(track)
                } else if (track is VideoTrack) {
                    Log.i(TAG, "Remote Video Track received")
                    _diagnostics.value = _diagnostics.value.copy(remoteVideoTrackActive = true)
                    onRemoteVideoTrack?.invoke(track)
                }
            }

            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }

        val pc = factory.createPeerConnection(rtcConfig, pcObserver)
        peerConnection = pc

        // Setup local audio track
        if (config.enableAudio) {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            val audioSource = factory.createAudioSource(audioConstraints)
            val audioTrack = factory.createAudioTrack("audio_${myUserId}", audioSource)
            audioTrack.setEnabled(true)
            localAudioSource = audioSource
            localAudioTrack = audioTrack
            pc?.addTrack(audioTrack, listOf("stream_${myUserId}"))
            _diagnostics.value = _diagnostics.value.copy(localAudioTrackEnabled = true)
        }
    }

    override suspend fun createOffer(): SignalingMessage = withContext(Dispatchers.IO) {
        val pc = checkNotNull(peerConnection) { "PeerConnection not initialized" }
        val sdpDeferred = CompletableDeferred<SessionDescription>()

        val sdpObserver = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local SDP offer set successfully")
                        sdpDeferred.complete(desc)
                    }
                    override fun onSetFailure(err: String?) {
                        sdpDeferred.completeExceptionally(RuntimeException("setLocalDescription failed: $err"))
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }
            override fun onCreateFailure(err: String?) {
                sdpDeferred.completeExceptionally(RuntimeException("createOffer failed: $err"))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }

        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createOffer(sdpObserver, mediaConstraints)
        val localDesc = sdpDeferred.await()

        val payload = JSONObject().apply {
            put("type", localDesc.type.canonicalForm())
            put("sdp", localDesc.description)
        }.toString()

        return@withContext SignalingMessage(
            callId = sessionId,
            senderId = myUserId,
            receiverId = remoteUserId,
            type = SignalingType.OFFER,
            payload = payload
        )
    }

    override suspend fun handleRemoteOffer(offer: SignalingMessage): SignalingMessage = withContext(Dispatchers.IO) {
        val pc = checkNotNull(peerConnection) { "PeerConnection not initialized" }
        val offerObj = JSONObject(offer.payload)
        val sdpStr = offerObj.getString("sdp")
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, sdpStr)

        val setRemoteDeferred = CompletableDeferred<Unit>()
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                isRemoteDescriptionSet = true
                drainQueuedIceCandidates()
                setRemoteDeferred.complete(Unit)
            }
            override fun onSetFailure(err: String?) {
                setRemoteDeferred.completeExceptionally(RuntimeException("setRemoteDescription failed: $err"))
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remoteDesc)

        setRemoteDeferred.await()

        val sdpDeferred = CompletableDeferred<SessionDescription>()
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local SDP answer set successfully")
                        sdpDeferred.complete(desc)
                    }
                    override fun onSetFailure(err: String?) {
                        sdpDeferred.completeExceptionally(RuntimeException("setLocalDescription failed: $err"))
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }
            override fun onCreateFailure(err: String?) {
                sdpDeferred.completeExceptionally(RuntimeException("createAnswer failed: $err"))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, mediaConstraints)

        val answerDesc = sdpDeferred.await()
        val payload = JSONObject().apply {
            put("type", answerDesc.type.canonicalForm())
            put("sdp", answerDesc.description)
        }.toString()

        return@withContext SignalingMessage(
            callId = sessionId,
            senderId = myUserId,
            receiverId = remoteUserId,
            type = SignalingType.ANSWER,
            payload = payload
        )
    }

    override suspend fun handleRemoteAnswer(answer: SignalingMessage): Unit = withContext(Dispatchers.IO) {
        val pc = checkNotNull(peerConnection) { "PeerConnection not initialized" }
        val ansObj = JSONObject(answer.payload)
        val sdpStr = ansObj.getString("sdp")
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)

        val setDeferred = CompletableDeferred<Unit>()
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                isRemoteDescriptionSet = true
                drainQueuedIceCandidates()
                setDeferred.complete(Unit)
            }
            override fun onSetFailure(err: String?) {
                setDeferred.completeExceptionally(RuntimeException("setRemoteDescription answer failed: $err"))
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remoteDesc)

        setDeferred.await()
    }

    override suspend fun addIceCandidate(candidate: SignalingMessage): Unit = withContext(Dispatchers.IO) {
        val candObj = JSONObject(candidate.payload)
        val sdpMid = candObj.optString("sdpMid")
        val sdpMLineIndex = candObj.optInt("sdpMLineIndex", 0)
        val sdp = candObj.getString("sdp")
        val sdpLower = sdp.lowercase()
        val isRelay = sdpLower.contains("typ relay")
        val isSrflx = sdpLower.contains("typ srflx")
        val isHost = sdpLower.contains("typ host")

        _diagnostics.value = _diagnostics.value.copy(
            remoteRelayCandidateCount = _diagnostics.value.remoteRelayCandidateCount + (if (isRelay) 1 else 0),
            remoteSrflxCandidateCount = _diagnostics.value.remoteSrflxCandidateCount + (if (isSrflx) 1 else 0),
            remoteHostCandidateCount = _diagnostics.value.remoteHostCandidateCount + (if (isHost) 1 else 0),
            isTurnRelay = _diagnostics.value.isTurnRelay || isRelay
        )

        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        if (isRemoteDescriptionSet) {
            peerConnection?.addIceCandidate(iceCandidate)
            Log.i(TAG, "Added remote ICE candidate directly: $sdpMid")
        } else {
            queuedIceCandidates.offer(iceCandidate)
            Log.i(TAG, "Queued remote ICE candidate prior to remote description: $sdpMid")
        }
    }

    private fun drainQueuedIceCandidates() {
        while (true) {
            val cand = queuedIceCandidates.poll() ?: break
            peerConnection?.addIceCandidate(cand)
            Log.i(TAG, "Drained queued ICE candidate: ${cand.sdpMid}")
        }
    }

    override suspend fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        _diagnostics.value = _diagnostics.value.copy(localAudioTrackEnabled = enabled)
    }

    override suspend fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        _diagnostics.value = _diagnostics.value.copy(localVideoTrackEnabled = enabled)
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        try { videoCapturer?.dispose() } catch (_: Exception) {}
        videoCapturer = null

        localAudioTrack?.dispose()
        localAudioTrack = null
        localAudioSource?.dispose()
        localAudioSource = null

        localVideoTrack?.dispose()
        localVideoTrack = null
        localVideoSource?.dispose()
        localVideoSource = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        rootEglBase?.release()
        rootEglBase = null

        isRemoteDescriptionSet = false
        queuedIceCandidates.clear()
        Log.i(TAG, "WebRTC session $sessionId closed and disposed")
    }
}
