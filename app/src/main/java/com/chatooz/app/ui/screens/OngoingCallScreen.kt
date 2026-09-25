package com.chatooz.app.ui.screens

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chatooz.app.call.CallInfo
import com.chatooz.app.call.CallState
import com.chatooz.app.model.User
import com.chatooz.app.ui.components.ChatoozAvatar
import com.chatooz.app.ui.theme.*

@Composable
fun OngoingCallScreen(
    callInfo: CallInfo,
    callState: CallState,
    statusMessage: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isCameraOn: Boolean,
    durationSeconds: Int,
    remoteVideoBitmap: Bitmap?,
    friends: List<User> = emptyList(),
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: (SurfaceTexture?) -> Unit,
    onStartLocalCameraPreview: (SurfaceTexture) -> Unit,
    onAddParticipant: (User) -> Unit = {},
    onEndCall: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var inviteFeedback by remember { mutableStateOf<String?>(null) }
    val formattedDuration = remember(durationSeconds) {
        val m = durationSeconds / 60
        val s = durationSeconds % 60
        "%02d:%02d".format(m, s)
    }

    var localSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }

    val isConnected = callState == CallState.CONNECTED || callState == CallState.AUDIO_CONNECTED || callState == CallState.VIDEO_CONNECTED

    // State badge colors and icons
    val (badgeBg, badgeText, statusText) = when (callState) {
        CallState.CALLING -> Triple(Color(0xFFEAB308), Color.Black, if (statusMessage.isNotBlank()) statusMessage else "Calling…")
        CallState.RINGING -> Triple(Color(0xFFF59E0B), Color.Black, "🔔 Ringing…")
        CallState.CONNECTING -> Triple(Color(0xFF3B82F6), Color.White, "🔵 Connecting…")
        CallState.CONNECTED, CallState.AUDIO_CONNECTED, CallState.VIDEO_CONNECTED -> Triple(EmeraldAccent, Color.White, formattedDuration)
        CallState.REJECTED -> Triple(RoseAccent, Color.White, "Call declined")
        CallState.BUSY -> Triple(Color(0xFFF97316), Color.White, "User is busy")
        CallState.NO_ANSWER -> Triple(Color(0xFF64748B), Color.White, "No answer")
        CallState.FAILED, CallState.NETWORK_ERROR -> Triple(RoseAccent, Color.White, "Call failed")
        CallState.ENDED -> Triple(Color(0xFF64748B), Color.White, "Call ended")
        else -> Triple(IndigoLight, Color.White, statusMessage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
    ) {
        if (callInfo.isVideo) {
            // ── VIDEO CALL VIEW ──────────────────────────────────────
            if (remoteVideoBitmap != null && isConnected) {
                // Fullscreen remote video
                Image(
                    bitmap = remoteVideoBitmap.asImageBitmap(),
                    contentDescription = "Remote video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Video connecting placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ChatoozAvatar(
                            name = callInfo.remoteName,
                            avatarColor = callInfo.remoteAvatarColor,
                            size = 110.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = callInfo.remoteName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = IndigoLight
                        )
                    }
                }
            }

            // Remote user info overlay at top-left
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(badgeBg)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = callInfo.remoteName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusText,
                        color = if (isConnected) EmeraldAccent else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Local Camera Preview PiP Card (top-right)
            if (isCameraOn) {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(end = 16.dp, top = 16.dp)
                        .align(Alignment.TopEnd)
                        .size(width = 110.dp, height = 150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        localSurfaceTexture = st
                                        onStartLocalCameraPreview(st)
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        update = { tv ->
                            if (tv.isAvailable && tv.surfaceTexture != null) {
                                localSurfaceTexture = tv.surfaceTexture
                                onStartLocalCameraPreview(tv.surfaceTexture!!)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = -1f  // mirror horizontally for selfie view
                            }
                    )

                    // Switch camera button on PiP
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(30.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onSwitchCamera(localSurfaceTexture) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Switch camera",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            // ── AUDIO CALL VIEW ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(100.dp))

                // Pulsing avatar
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isConnected) 1.05f else 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(if (isConnected) 1200 else 800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size((130 * pulseScale).dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) IndigoPrimary.copy(alpha = 0.15f)
                            else Color(0xFFF59E0B).copy(alpha = 0.15f)
                        )
                        .padding(12.dp)
                ) {
                    ChatoozAvatar(
                        name = callInfo.remoteName,
                        avatarColor = callInfo.remoteAvatarColor,
                        size = 110.dp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = callInfo.remoteName,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "@${callInfo.remoteUsername}",
                    fontSize = 15.sp,
                    color = TextSecDark
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status & Duration badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg.copy(alpha = 0.18f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) EmeraldAccent else Color(0xFFFDE047)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // ── FLOATING CALL CONTROLS (BOTTOM) ──────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.92f))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    active = isMuted,
                    onClick = onToggleMute
                )

                // Camera Toggle (only in video call)
                if (callInfo.isVideo) {
                    CallControlButton(
                        icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        label = if (isCameraOn) "Cam On" else "Cam Off",
                        active = !isCameraOn,
                        onClick = onToggleCamera
                    )
                }

                // Speaker / Earpiece
                CallControlButton(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    label = if (isSpeakerOn) "Speaker" else "Earpiece",
                    active = isSpeakerOn,
                    onClick = onToggleSpeaker
                )

                // Add to Call (Conference)
                CallControlButton(
                    icon = Icons.Default.PersonAdd,
                    label = "Add",
                    active = showAddDialog,
                    onClick = { showAddDialog = true }
                )

                // End Call button (Red circle)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(RoseAccent)
                        .clickable(onClick = onEndCall),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End call",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Add Participant to Call Dialog
        if (showAddDialog) {
            val eligibleFriends = friends.filter { it.id != callInfo.remoteUserId }
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Call (Conference)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimDark)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Select a friend to invite to this ongoing call:",
                            fontSize = 13.sp,
                            color = TextSecDark,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (eligibleFriends.isEmpty()) {
                            Text(
                                "No other friends available to add.",
                                fontSize = 13.sp,
                                color = TextSecDark,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                            ) {
                                items(eligibleFriends, key = { it.id }) { friend ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ChatoozAvatar(name = friend.name, avatarColor = friend.avatarColor, avatarUrl = friend.avatarUrl, size = 40.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(friend.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimDark)
                                            Text("@${friend.username}", fontSize = 12.sp, color = IndigoLight)
                                        }
                                        Button(
                                            onClick = {
                                                onAddParticipant(friend)
                                                inviteFeedback = "Invited ${friend.name}!"
                                                showAddDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Call, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", color = IndigoLight)
                    }
                },
                containerColor = DarkCard
            )
        }

        if (inviteFeedback != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldAccent.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = inviteFeedback ?: "",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            LaunchedEffect(inviteFeedback) {
                kotlinx.coroutines.delay(2500)
                inviteFeedback = null
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (active) IndigoPrimary.copy(alpha = 0.35f)
                    else Color.White.copy(alpha = 0.12f)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) IndigoLight else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextSecDark, fontSize = 10.sp)
    }
}
