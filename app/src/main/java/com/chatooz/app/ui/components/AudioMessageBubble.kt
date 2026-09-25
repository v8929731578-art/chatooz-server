package com.chatooz.app.ui.components

import android.media.MediaPlayer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatooz.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * AudioMessageBubble — plays a voice message from a local file path.
 * Shows play/pause, animated progress bar, and duration.
 */
@Composable
fun AudioMessageBubble(
    filePath: String,
    durationSec: Int,
    isFromMe: Boolean,
    isDark: Boolean
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var elapsed by remember { mutableStateOf(0) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val textColor = if (isFromMe) Color.White else (if (isDark) TextPrimDark else TextPrimLight)
    val subColor = if (isFromMe) Color.White.copy(alpha = 0.7f) else (if (isDark) TextSecDark else TextSecLight)
    val iconColor = if (isFromMe) Color.White else IndigoPrimary
    val trackColor = if (isFromMe) Color.White.copy(alpha = 0.3f) else (if (isDark) DarkDivider else LightDivider)
    val progressColor = if (isFromMe) Color.White else IndigoPrimary

    // Timer while playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val startElapsed = elapsed
            val startMs = System.currentTimeMillis()
            while (isPlaying) {
                delay(200L)
                val passedSec = ((System.currentTimeMillis() - startMs) / 1000f).toInt()
                elapsed = (startElapsed + passedSec).coerceAtMost(durationSec)
                progress = elapsed.toFloat() / durationSec.toFloat()
                if (elapsed >= durationSec) {
                    isPlaying = false
                    elapsed = 0
                    progress = 0f
                }
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(filePath) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun togglePlayback() {
        if (isPlaying) {
            player?.pause()
            isPlaying = false
        } else {
            if (player == null) {
                try {
                    player = MediaPlayer().apply {
                        setDataSource(filePath)
                        prepare()
                        setOnCompletionListener {
                            isPlaying = false
                            elapsed = 0
                            progress = 0f
                        }
                        start()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return
                }
            } else {
                player?.start()
            }
            isPlaying = true
        }
    }

    Row(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 250.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play / Pause button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isFromMe) Color.White.copy(alpha = 0.2f)
                    else IndigoPrimary.copy(alpha = if (isDark) 0.2f else 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = ::togglePlayback,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(trackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(progressColor)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Duration label
            val m = durationSec / 60
            val s = durationSec % 60
            val elapsedM = elapsed / 60
            val elapsedS = elapsed % 60
            Text(
                text = if (isPlaying) "%02d:%02d / %02d:%02d".format(elapsedM, elapsedS, m, s)
                       else "%02d:%02d".format(m, s),
                fontSize = 11.sp,
                color = subColor
            )
        }
    }
}
