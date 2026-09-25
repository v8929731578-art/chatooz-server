package com.chatooz.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatooz.app.call.CallInfo
import com.chatooz.app.ui.components.ChatoozAvatar
import com.chatooz.app.ui.theme.*

@Composable
fun IncomingCallScreen(
    callInfo: CallInfo,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // Pulsing ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1A1040),
                        Color(0xFF0B0F19),
                        Color(0xFF0B0F19)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Pulsing avatar ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.25f))
                )
                ChatoozAvatar(
                    name = callInfo.remoteName,
                    avatarColor = callInfo.remoteAvatarColor,
                    size = 96.dp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = callInfo.remoteName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "@${callInfo.remoteUsername}",
                fontSize = 15.sp,
                color = TextSecDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Call type label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (callInfo.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = null,
                    tint = VioletLight,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (callInfo.isVideo) "Incoming video call…" else "Incoming call…",
                    fontSize = 14.sp,
                    color = VioletLight
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Accept / Decline buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(60.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(RoseAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onDecline) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Decline", color = TextSecDark, fontSize = 12.sp)
                }

                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(EmeraldAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onAccept) {
                            Icon(
                                imageVector = if (callInfo.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Accept", color = TextSecDark, fontSize = 12.sp)
                }
            }
        }
    }
}
