package com.chatooz.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatooz.app.ui.theme.*
import com.chatooz.app.util.AvatarLoader

@Composable
fun ChatoozAvatar(
    name: String,
    avatarColor: Long,
    size: Dp = 48.dp,
    showOnlineBadge: Boolean = false,
    hasStory: Boolean = false,
    hasUnreadStory: Boolean = false,
    modifier: Modifier = Modifier
) {
    ChatoozAvatar(
        name = name,
        avatarColor = avatarColor,
        avatarUrl = null,
        size = size,
        showOnlineBadge = showOnlineBadge,
        hasStory = hasStory,
        hasUnreadStory = hasUnreadStory,
        modifier = modifier
    )
}

@Composable
fun ChatoozAvatar(
    name: String,
    avatarColor: Long,
    avatarUrl: String? = null,
    size: Dp = 48.dp,
    showOnlineBadge: Boolean = false,
    hasStory: Boolean = false,
    hasUnreadStory: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(avatarUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(avatarUrl) {
        if (!avatarUrl.isNullOrBlank()) {
            AvatarLoader.load(context, avatarUrl) { bmp ->
                bitmap = bmp
            }
        } else {
            bitmap = null
        }
    }

    val storyRingModifier = if (hasStory) {
        val ringBrush = if (hasUnreadStory) {
            Brush.sweepGradient(
                colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF59E0B), Color(0xFF6366F1))
            )
        } else {
            Brush.sweepGradient(listOf(Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFF64748B)))
        }
        Modifier
            .border(2.5.dp, ringBrush, CircleShape)
            .padding(3.dp)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(storyRingModifier),
        contentAlignment = Alignment.BottomEnd
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val color = Color(avatarColor)
            val gradientBrush = Brush.linearGradient(
                listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.75f))
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(gradientBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = (size.value * 0.42).sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        if (showOnlineBadge) {
            val badgeSize = (size.value * 0.28).coerceIn(10.0, 16.0).dp
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(OnlineGreen)
                )
            }
        }
    }
}
