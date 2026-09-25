package com.chatooz.app.ui.components

import android.Manifest
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatooz.app.ui.theme.*
import java.io.File

/**
 * VoiceMessageRecorder — press and hold Mic button to record, release to send.
 * Emits the file path of the recorded audio via [onRecordingComplete].
 * Shows an animated waveform and elapsed duration while recording.
 */
@Composable
fun VoiceMessageRecorder(
    modifier: Modifier = Modifier,
    onRecordingComplete: (filePath: String, durationSec: Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var startTimeMs by remember { mutableStateOf(0L) }

    // Pulsing animation while recording
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    // Timer while recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            startTimeMs = System.currentTimeMillis()
            while (isRecording) {
                kotlinx.coroutines.delay(1000L)
                recordingSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
                // Auto-stop at 60s
                if (recordingSeconds >= 60) {
                    isRecording = false
                }
            }
        }
    }

    fun startRecording() {
        val voiceDir = File(context.filesDir, "voice_msgs")
        if (!voiceDir.exists()) voiceDir.mkdirs()
        val file = File(voiceDir, "rec_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
                mediaRecorder = this
                isRecording = true
            } catch (e: Exception) {
                e.printStackTrace()
                release()
            }
        }
    }

    fun stopAndSend() {
        val rec = mediaRecorder ?: return
        val file = outputFile ?: return
        val duration = recordingSeconds.coerceAtLeast(1)
        try {
            rec.stop()
            rec.release()
        } catch (e: Exception) { e.printStackTrace() }
        mediaRecorder = null
        isRecording = false
        if (file.exists() && file.length() > 0) {
            onRecordingComplete(file.absolutePath, duration)
        }
    }

    fun cancelRecording() {
        val rec = mediaRecorder
        try {
            rec?.stop()
            rec?.release()
        } catch (e: Exception) { }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
        isRecording = false
        onCancel()
    }

    if (isRecording) {
        // Recording UI overlay
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF1A1040), Color(0xFF0B0F19))),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel
            IconButton(onClick = ::cancelRecording) {
                Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = RoseAccent)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Waveform placeholder
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val heights = remember { listOf(8, 14, 10, 18, 12, 20, 10, 16, 8, 14, 12) }
                heights.forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(IndigoPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Duration
            val m = recordingSeconds / 60
            val s = recordingSeconds % 60
            Text(
                text = "%02d:%02d".format(m, s),
                color = RoseAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Pulsing send button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(micScale)
                    .clip(CircleShape)
                    .background(EmeraldAccent)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { stopAndSend() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    } else {
        // Mic button (press to record)
        Box(
            modifier = modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(IndigoPrimary, VioletAccent)))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { startRecording() },
                        onTap = { startRecording() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Hold to record", tint = Color.White)
        }
    }
}
