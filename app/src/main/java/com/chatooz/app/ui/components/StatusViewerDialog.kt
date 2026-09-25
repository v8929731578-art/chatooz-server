package com.chatooz.app.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chatooz.app.model.Status
import com.chatooz.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusViewerDialog(
    statuses: List<Status>,
    initialIndex: Int = 0,
    currentUserId: String,
    onDismiss: () -> Unit,
    onStatusViewed: (String) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onToggleLike: (String) -> Unit = {},
    onRepostStatus: (Status) -> Unit = {},
    onReplyStatus: (Status, String) -> Unit = { _, _ -> }
) {
    if (statuses.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(0, statuses.size - 1)) }
    val currentStatus = statuses[currentIndex]
    var isPaused by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    var replyText by remember { mutableStateOf("") }
    var showReplyInput by remember { mutableStateOf(false) }
    var showRepostConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var savedStatuses by remember { mutableStateOf(setOf<String>()) }
    var showHeartBurst by remember { mutableStateOf(false) }

    val isLiked = currentStatus.likes.contains(currentUserId)
    val isSaved = savedStatuses.contains(currentStatus.id)
    val isOwner = currentStatus.userId == currentUserId

    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // Notify viewer
    LaunchedEffect(currentStatus.id) {
        onStatusViewed(currentStatus.id)
        progress = 0f
    }

    // Story progress animation timer (adaptive for long text)
    LaunchedEffect(currentIndex, isPaused, showReplyInput, showRepostConfirm, showDeleteConfirmDialog) {
        val paused = isPaused || showReplyInput || showRepostConfirm || showDeleteConfirmDialog
        if (!paused) {
            val stepMs = 50L
            val textLen = currentStatus.textContent.length
            val storyDurationMs = when {
                currentStatus.type == "IMAGE" -> 5000L
                textLen <= 70 -> 5000L
                textLen <= 200 -> 7500L
                else -> 10000L
            }
            val totalSteps = storyDurationMs / stepMs
            while (progress < 1f && !(isPaused || showReplyInput || showRepostConfirm)) {
                delay(stepMs)
                progress += 1f / totalSteps
            }
            if (progress >= 1f && !(isPaused || showReplyInput || showRepostConfirm)) {
                if (currentIndex < statuses.size - 1) {
                    currentIndex++
                    progress = 0f
                } else {
                    onDismiss()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val bgBrush = if (currentStatus.type == "IMAGE") {
            Brush.verticalGradient(listOf(Color.Black, Color.Black))
        } else {
            Brush.verticalGradient(
                StatusGradients.getOrElse(currentStatus.bgGradientIndex) { StatusGradients[0] }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
        ) {
            // Story Content (Text or Image) + Tap detector backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex, statuses.size, showReplyInput) {
                        detectTapGestures(
                            onPress = {
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = { offset ->
                                if (showReplyInput) {
                                    showReplyInput = false
                                    focusManager.clearFocus()
                                    return@detectTapGestures
                                }
                                val width = size.width
                                if (offset.x < width * 0.35f) {
                                    // Previous
                                    if (currentIndex > 0) {
                                        currentIndex--
                                        progress = 0f
                                    }
                                } else if (offset.x > width * 0.65f) {
                                    // Next
                                    if (currentIndex < statuses.size - 1) {
                                        currentIndex++
                                        progress = 0f
                                    } else {
                                        onDismiss()
                                    }
                                }
                            }
                        )
                    }
            ) {
                if (currentStatus.type == "IMAGE" && currentStatus.mediaBase64 != null) {
                    val bitmap = remember(currentStatus.mediaBase64) {
                        try {
                            val bytes = android.util.Base64.decode(currentStatus.mediaBase64, android.util.Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (_: Exception) { null }
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Story photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Optional caption at bottom
                    if (currentStatus.textContent.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (isOwner) 80.dp else 120.dp)
                        ) {
                            Text(
                                text = currentStatus.textContent,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                    }
                } else {
                    // Text status content with dynamic text sizing & scrolling
                    val textLength = currentStatus.textContent.length
                    val (fontSize, lineHeight) = when {
                        textLength <= 70 -> 28.sp to 38.sp
                        textLength <= 160 -> 22.sp to 30.sp
                        textLength <= 400 -> 18.sp to 25.sp
                        else -> 15.sp to 21.sp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 24.dp, end = 72.dp, top = 90.dp, bottom = 110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentStatus.textContent,
                                color = Color.White,
                                fontSize = fontSize,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                lineHeight = lineHeight
                            )
                        }
                    }
                }
            }

            // Heart burst animation overlay on like
            AnimatedVisibility(
                visible = showHeartBurst,
                enter = scaleIn(initialScale = 0.3f) + fadeIn(),
                exit = scaleOut(targetScale = 1.6f) + fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(100.dp)
                )
            }

            // Top Overlay: Progress Bars + Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Progress segments
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    statuses.forEachIndexed { idx, _ ->
                        val segmentProgress = when {
                            idx < currentIndex -> 1f
                            idx == currentIndex -> progress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { segmentProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }

                // Header info (Avatar, Name, Timestamp, Close/Delete)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChatoozAvatar(
                        name = currentStatus.userName,
                        avatarColor = currentStatus.userAvatarColor,
                        avatarUrl = currentStatus.userAvatarUrl,
                        size = 38.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentStatus.userName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = timeFmt.format(Date(currentStatus.timestamp)),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }

                    if (isOwner) {
                        IconButton(
                            onClick = {
                                showDeleteConfirmDialog = true
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Status",
                                tint = RoseAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Right Vertical Floating Action Bar (Like, Comment/Reply, Repost, Share, Bookmark)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ❤️ Like Button
                StoryActionButton(
                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (currentStatus.likes.isNotEmpty()) "${currentStatus.likes.size}" else "Like",
                    tint = if (isLiked) Color(0xFFEF4444) else Color.White,
                    onClick = {
                        onToggleLike(currentStatus.id)
                        if (!isLiked) {
                            showHeartBurst = true
                            coroutineScope.launch {
                                delay(600)
                                showHeartBurst = false
                            }
                        }
                    }
                )

                // 💬 Comment / Reply Button
                StoryActionButton(
                    icon = Icons.Filled.ChatBubbleOutline,
                    label = "Reply",
                    tint = if (showReplyInput) IndigoLight else Color.White,
                    onClick = {
                        showReplyInput = !showReplyInput
                    }
                )

                // 🔁 Repost Button
                StoryActionButton(
                    icon = Icons.Filled.Repeat,
                    label = "Repost",
                    tint = Color.White,
                    onClick = {
                        showRepostConfirm = true
                    }
                )

                // ✈️ Share / Send Button
                StoryActionButton(
                    icon = Icons.Filled.Send,
                    label = "Share",
                    tint = Color.White,
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val shareText = if (currentStatus.textContent.isNotBlank()) {
                                "✨ Story from ${currentStatus.userName} on Chatooz:\n\n\"${currentStatus.textContent}\""
                            } else {
                                "✨ Check out ${currentStatus.userName}'s photo story on Chatooz!"
                            }
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Story"))
                    }
                )

                // 🔖 Bookmark / Save Button
                StoryActionButton(
                    icon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    label = if (isSaved) "Saved" else "Save",
                    tint = if (isSaved) Color(0xFFF59E0B) else Color.White,
                    onClick = {
                        savedStatuses = if (isSaved) savedStatuses - currentStatus.id else savedStatuses + currentStatus.id
                        Toast.makeText(
                            context,
                            if (!isSaved) "🔖 Story saved to bookmarks!" else "Removed from bookmarks",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            // Bottom Section: Reply Bar or Owner Analytics
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                if (isOwner) {
                    // Owner summary bar (Viewers & Likes)
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.Center)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Visibility, contentDescription = "Views", tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("${currentStatus.viewers.size} viewers", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Favorite, contentDescription = "Likes", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                Text("${currentStatus.likes.size} likes", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Interactive Reply & Quick Reactions Bar — ONLY shown when side Reply icon is tapped!
                    AnimatedVisibility(
                        visible = showReplyInput,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Header for reply popup with close icon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Reply to ${currentStatus.userName}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                                IconButton(
                                    onClick = {
                                        showReplyInput = false
                                        focusManager.clearFocus()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Reply",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Quick Emoji Reactions
                            val quickEmojis = listOf("❤️", "🔥", "😂", "😍", "👏", "🙌", "😮", "😢")
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(quickEmojis) { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                onReplyStatus(currentStatus, emoji)
                                                Toast.makeText(context, "Sent $emoji reaction! 💬", Toast.LENGTH_SHORT).show()
                                                showReplyInput = false
                                                focusManager.clearFocus()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 20.sp)
                                    }
                                }
                            }

                            // Reply text input bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 14.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = replyText,
                                    onValueChange = { replyText = it },
                                    placeholder = {
                                        Text(
                                            text = "Type a reply...",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 13.5.sp
                                        )
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = IndigoPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (replyText.isNotBlank()) {
                                                onReplyStatus(currentStatus, replyText)
                                                Toast.makeText(context, "Reply sent! 💬", Toast.LENGTH_SHORT).show()
                                                replyText = ""
                                                showReplyInput = false
                                                focusManager.clearFocus()
                                            }
                                        }
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        if (replyText.isNotBlank()) {
                                            onReplyStatus(currentStatus, replyText)
                                            Toast.makeText(context, "Reply sent! 💬", Toast.LENGTH_SHORT).show()
                                            replyText = ""
                                            showReplyInput = false
                                            focusManager.clearFocus()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (replyText.isNotBlank()) IndigoPrimary else Color.White.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send Reply",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Repost Confirmation Dialog
            if (showRepostConfirm) {
                AlertDialog(
                    onDismissRequest = { showRepostConfirm = false },
                    title = { Text("Repost to My Status", fontWeight = FontWeight.Bold) },
                    text = { Text("Do you want to re-share this story to your 24-hour status?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                onRepostStatus(currentStatus)
                                showRepostConfirm = false
                                Toast.makeText(context, "✨ Reposted to your status!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Text("Repost 🔁", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRepostConfirm = false }) {
                            Text("Cancel")
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Delete Status Confirmation Dialog
            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Delete Status?", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to delete this status update? It will be permanently removed.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val deleteId = currentStatus.id
                                onDeleteStatus(deleteId)
                                showDeleteConfirmDialog = false
                                if (statuses.size <= 1) {
                                    onDismiss()
                                } else {
                                    currentIndex = (currentIndex - 1).coerceAtLeast(0)
                                    progress = 0f
                                }
                                Toast.makeText(context, "Status deleted 🗑️", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
                        ) {
                            Text("Delete 🗑️", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StoryActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

