package com.chatooz.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatooz.app.call.CallState
import com.chatooz.app.data.AutoUpdateManager
import com.chatooz.app.data.VersionInfo
import com.chatooz.app.ui.screens.*
import com.chatooz.app.ui.theme.ChatoozTheme
import com.chatooz.app.viewmodel.ChatoozViewModel
import com.chatooz.app.viewmodel.Screen
import kotlinx.coroutines.launch

@Composable
fun ChatoozApp(
    viewModel: ChatoozViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── Auto-update state ─────────────────────────────────────────────────────
    var pendingUpdate  by remember { mutableStateOf<VersionInfo?>(null) }
    var showUpdateDlg  by remember { mutableStateOf(false) }
    var isDownloading  by remember { mutableStateOf(false) }
    var dlProgress     by remember { mutableStateOf(0f) }

    // Check for update periodically while app is running (non-blocking)
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val info = AutoUpdateManager.checkForUpdate(context)
                if (info != null && !showUpdateDlg && !isDownloading) {
                    pendingUpdate = info
                    showUpdateDlg = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            kotlinx.coroutines.delay(15_000)
        }
    }


    // Update dialog
    if (showUpdateDlg && pendingUpdate != null) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showUpdateDlg = false },
            title   = { Text("🔄 Update Available!") },
            text    = {
                Column {
                    Text("Version ${pendingUpdate!!.versionName} is ready.")
                    Spacer(Modifier.height(6.dp))
                    Text(pendingUpdate!!.changelog)
                    if (isDownloading) {
                        Spacer(Modifier.height(12.dp))
                        Text("Downloading… ${(dlProgress * 100).toInt()}%")
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { dlProgress },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (!isDownloading) {
                    TextButton(onClick = {
                        isDownloading = true
                        scope.launch {
                            AutoUpdateManager.downloadAndInstall(
                                context    = context,
                                onProgress = { p -> dlProgress = p },
                                onComplete = { _ ->
                                    isDownloading = false
                                    showUpdateDlg = false
                                }
                            )
                        }
                    }) { Text("Download & Install") }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDlg = false }) { Text("Later") }
                }
            }
        )
    }

    val isDark            by viewModel.isDark.collectAsState()
    val screen            by viewModel.screen.collectAsState()
    val friends           by viewModel.friends.collectAsState()
    val callState         by viewModel.callState.collectAsState()
    val callInfo          by viewModel.callInfo.collectAsState()
    val isMuted           by viewModel.isMuted.collectAsState()
    val isSpeaker         by viewModel.isSpeakerOn.collectAsState()
    val isCameraOn        by viewModel.isCameraOn.collectAsState()
    val callSecs          by viewModel.callDurationSeconds.collectAsState()
    val remoteVideoBitmap by viewModel.remoteVideoBitmap.collectAsState()

    val callStatusMsg     by viewModel.callStatusMessage.collectAsState()

    ChatoozTheme(darkTheme = isDark) {

        // ── Call overlays take priority over any screen ──────────────
        when (callState) {
            CallState.RINGING -> {
                callInfo?.let { info ->
                    if (info.isIncoming) {
                        IncomingCallScreen(
                            callInfo  = info,
                            onAccept  = { viewModel.acceptCall() },
                            onDecline = { viewModel.declineCall() }
                        )
                    } else {
                        OngoingCallScreen(
                            callInfo                  = info,
                            callState                 = callState,
                            statusMessage             = callStatusMsg,
                            isMuted                   = isMuted,
                            isSpeakerOn               = isSpeaker,
                            isCameraOn                = isCameraOn,
                            durationSeconds           = 0,
                            remoteVideoBitmap         = remoteVideoBitmap,
                            friends                   = friends,
                            onToggleMute              = { viewModel.toggleMute() },
                            onToggleSpeaker           = { viewModel.toggleSpeaker() },
                            onToggleCamera            = { viewModel.toggleCamera() },
                            onSwitchCamera            = { viewModel.switchCamera(it) },
                            onStartLocalCameraPreview = { viewModel.startLocalCameraPreview(it) },
                            onAddParticipant          = { viewModel.inviteToCurrentCall(it) },
                            onEndCall                 = { viewModel.endCall() }
                        )
                    }
                }
            }
            CallState.CALLING,
            CallState.ACCEPTED,
            CallState.CONNECTING,
            CallState.AUDIO_CONNECTED,
            CallState.VIDEO_CONNECTED,
            CallState.CONNECTED,
            CallState.REJECTED,
            CallState.BUSY,
            CallState.NO_ANSWER,
            CallState.FAILED,
            CallState.NETWORK_ERROR,
            CallState.ENDED -> {
                callInfo?.let { info ->
                    OngoingCallScreen(
                        callInfo                  = info,
                        callState                 = callState,
                        statusMessage             = callStatusMsg,
                        isMuted                   = isMuted,
                        isSpeakerOn               = isSpeaker,
                        isCameraOn                = isCameraOn,
                        durationSeconds           = callSecs,
                        remoteVideoBitmap         = remoteVideoBitmap,
                        friends                   = friends,
                        onToggleMute              = { viewModel.toggleMute() },
                        onToggleSpeaker           = { viewModel.toggleSpeaker() },
                        onToggleCamera            = { viewModel.toggleCamera() },
                        onSwitchCamera            = { viewModel.switchCamera(it) },
                        onStartLocalCameraPreview = { viewModel.startLocalCameraPreview(it) },
                        onAddParticipant          = { viewModel.inviteToCurrentCall(it) },
                        onEndCall                 = { viewModel.endCall() }
                    )
                }
            }

            else -> {
                // ── Normal screen navigation ─────────────────────────
                when (val s = screen) {
                    Screen.Auth -> {
                        AuthScreen(viewModel = viewModel)
                    }

                    Screen.OtpVerification -> {
                        BackHandler { viewModel.goToAuth() }
                        OtpVerificationScreen(viewModel = viewModel)
                    }

                    Screen.Setup -> {
                        BackHandler { viewModel.logout() }
                        SetupScreen(viewModel = viewModel)
                    }

                    Screen.Home -> {
                        HomeScreen(
                            viewModel = viewModel,
                            onOpenChat = { chat -> viewModel.openChat(chat) },
                            onFriends  = { viewModel.goFriends() },
                            onProfile  = { viewModel.goProfile() }
                        )
                    }

                    is Screen.Chat -> {
                        BackHandler { viewModel.closeChat() }
                        ChatDetailScreen(viewModel = viewModel)
                    }

                    Screen.Status -> {
                        BackHandler { viewModel.goHome() }
                        StatusScreen(
                            viewModel = viewModel,
                            onChats   = { viewModel.goHome() },
                            onFriends = { viewModel.goFriends() },
                            onProfile = { viewModel.goProfile() }
                        )
                    }

                    Screen.Friends -> {
                        BackHandler { viewModel.goHome() }
                        FriendsScreen(
                            viewModel = viewModel,
                            onBack    = { viewModel.goHome() }
                        )
                    }

                    Screen.Profile -> {
                        BackHandler { viewModel.goHome() }
                        ProfileScreen(
                            viewModel = viewModel,
                            onBack    = { viewModel.goHome() }
                        )
                    }
                }
            }
        }
    }
}
