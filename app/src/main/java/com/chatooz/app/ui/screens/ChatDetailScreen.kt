package com.chatooz.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatooz.app.model.Group
import com.chatooz.app.model.Message
import com.chatooz.app.model.User
import com.chatooz.app.ui.components.AudioMessageBubble
import com.chatooz.app.ui.components.ChatoozAvatar
import com.chatooz.app.ui.components.MediaMessageBubble
import com.chatooz.app.ui.components.ReadReceipt
import com.chatooz.app.ui.components.VoiceMessageRecorder
import com.chatooz.app.ui.theme.*
import com.chatooz.app.viewmodel.ChatoozViewModel
import com.chatooz.app.viewmodel.Screen

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "file_${System.currentTimeMillis()}"
    val returnCursor = context.contentResolver.query(uri, null, null, null, null)
    if (returnCursor != null) {
        val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && returnCursor.moveToFirst()) {
            name = returnCursor.getString(nameIndex) ?: name
        }
        returnCursor.close()
    }
    return name
}

@Composable
fun ChatDetailScreen(
    viewModel: ChatoozViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenState = viewModel.screen.collectAsState().value as? Screen.Chat ?: return
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val chatInput by viewModel.chatInput.collectAsState()
    val isDark by viewModel.isDark.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val userGroups by viewModel.userGroups.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val isBlocked by viewModel.isCurrentChatBlocked.collectAsState()
    val isBlockedByOther by viewModel.isBlockedByCurrentChat.collectAsState()

    val isGroup = screenState.chatId.startsWith("grp_") || screenState.friendId.startsWith("grp_")
    val currentGroup = remember(screenState.chatId, userGroups) {
        userGroups.find { it.id == screenState.chatId || it.id == screenState.friendId }
            ?: viewModel.storage.getGroupById(screenState.chatId)
            ?: viewModel.storage.getGroupById(screenState.friendId)
    }

    var showMenu by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showGroupInfoDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }

    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    val listState = rememberLazyListState()

    // Activity result launchers for media & file sharing
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                val fileName = getFileNameFromUri(context, uri)
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.sendMediaMessage(
                        chatId = screenState.chatId,
                        friendId = screenState.friendId,
                        type = "IMAGE",
                        fileName = fileName,
                        mimeType = mimeType,
                        bytes = bytes
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                val fileName = getFileNameFromUri(context, uri)
                val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.sendMediaMessage(
                        chatId = screenState.chatId,
                        friendId = screenState.friendId,
                        type = "VIDEO",
                        fileName = fileName,
                        mimeType = mimeType,
                        bytes = bytes
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                val fileName = getFileNameFromUri(context, uri)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.sendMediaMessage(
                        chatId = screenState.chatId,
                        friendId = screenState.friendId,
                        type = "FILE",
                        fileName = fileName,
                        mimeType = mimeType,
                        bytes = bytes
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { Text("Block @${screenState.friendUsername}?") },
            text = { Text("Blocked users cannot send you messages or friend requests.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.blockUser(screenState.friendId, screenState.friendUsername, screenState.friendName)
                        showBlockConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
                ) {
                    Text("Block", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = surface
        )
    }

    if (showDeleteGroupDialog && currentGroup != null) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = { Text("Delete Group '${currentGroup.name}'?") },
            text = { Text("This will permanently remove the group and all its messages for all members.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(currentGroup.id)
                        showDeleteGroupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
                ) {
                    Text("Delete Group", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = false }) { Text("Cancel") }
            },
            containerColor = surface
        )
    }

    if (showLeaveGroupDialog && currentGroup != null) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupDialog = false },
            title = { Text("Exit Group '${currentGroup.name}'?") },
            text = { Text("You will no longer receive messages or be a member of this group.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.leaveGroup(currentGroup.id)
                        showLeaveGroupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
                ) {
                    Text("Exit Group", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupDialog = false }) { Text("Cancel") }
            },
            containerColor = surface
        )
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Clear all messages?") },
            text = { Text("This will remove all messages from this chat on your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChat(screenState.chatId)
                        showClearChatDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
                ) {
                    Text("Clear Chat", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) { Text("Cancel") }
            },
            containerColor = surface
        )
    }

    if (showDeleteChatDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatDialog = false },
            title = { Text("Delete this chat?") },
            text = { Text("This will delete the conversation and message history.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (isGroup && currentGroup != null) {
                            if (currentGroup.creatorId == currentUser?.id) {
                                viewModel.deleteGroup(currentGroup.id)
                            } else {
                                viewModel.leaveGroup(currentGroup.id)
                            }
                        } else {
                            viewModel.deleteChat(screenState.chatId)
                        }
                        showDeleteChatDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatDialog = false }) { Text("Cancel") }
            },
            containerColor = surface
        )
    }

    if (showGroupInfoDialog && currentGroup != null) {
        GroupInfoDialog(
            group = currentGroup,
            currentUser = currentUser,
            allUsers = allUsers,
            isDark = isDark,
            textPrim = textPrim,
            textSec = textSec,
            onRemoveMember = { userId ->
                viewModel.removeGroupMember(currentGroup.id, userId)
            },
            onDeleteGroup = {
                showGroupInfoDialog = false
                showDeleteGroupDialog = true
            },
            onLeaveGroup = {
                showGroupInfoDialog = false
                showLeaveGroupDialog = true
            },
            onDismiss = { showGroupInfoDialog = false }
        )
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            Surface(
                color = if (isDark) DarkSurface else LightSurface,
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isDark) DarkGlassBorder else LightGlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.closeChat() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrim
                        )
                    }

                    val liveFriend = allUsers.find { it.id == screenState.friendId }
                    val liveAvatarUrl = liveFriend?.avatarUrl?.ifBlank { null } ?: screenState.friendAvatarUrl
                    val liveAvatarColor = liveFriend?.avatarColor ?: screenState.friendAvatarColor
                    val liveFriendName = liveFriend?.name?.ifBlank { null } ?: screenState.friendName

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (isGroup) showGroupInfoDialog = true
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChatoozAvatar(
                            name = if (isGroup) screenState.friendName else liveFriendName,
                            avatarColor = if (isGroup) screenState.friendAvatarColor else liveAvatarColor,
                            avatarUrl = if (isGroup) screenState.friendAvatarUrl else liveAvatarUrl,
                            size = 42.dp,
                            showOnlineBadge = !isGroup && !isBlocked && !isBlockedByOther
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isGroup) screenState.friendName else liveFriendName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = textPrim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isGroup -> "${currentGroup?.members?.size ?: 0} members • Tap for info"
                                    isBlocked -> "Blocked"
                                    isBlockedByOther -> "Unavailable"
                                    isTyping -> "typing..."
                                    else -> "@${screenState.friendUsername} • Active"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = if (isTyping) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isBlocked || isBlockedByOther -> RoseAccent
                                    isTyping -> IndigoPrimary
                                    else -> textSec
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!isGroup) {
                        IconButton(
                            onClick = {
                                viewModel.startAudioCall(
                                    screenState.friendId,
                                    screenState.friendUsername,
                                    screenState.friendName,
                                    screenState.friendAvatarColor
                                )
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = {
                                viewModel.startVideoCall(
                                    screenState.friendId,
                                    screenState.friendUsername,
                                    screenState.friendName,
                                    screenState.friendAvatarColor
                                )
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video", tint = IndigoPrimary, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        IconButton(onClick = { showGroupInfoDialog = true }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "Group Info", tint = IndigoPrimary, modifier = Modifier.size(22.dp))
                        }
                    }


                    // 3-dot overflow menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = textPrim)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isGroup) {
                                DropdownMenuItem(
                                    text = { Text("Group Info & Members") },
                                    leadingIcon = { Icon(Icons.Default.Info, null, tint = IndigoPrimary) },
                                    onClick = {
                                        showMenu = false
                                        showGroupInfoDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Messages") },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = textSec) },
                                    onClick = {
                                        showMenu = false
                                        showClearChatDialog = true
                                    }
                                )
                                if (currentGroup?.creatorId == currentUser?.id) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Group", color = RoseAccent) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = RoseAccent) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteGroupDialog = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Exit Group", color = RoseAccent) },
                                        leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = RoseAccent) },
                                        onClick = {
                                            showMenu = false
                                            showLeaveGroupDialog = true
                                        }
                                    )
                                }
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Clear Messages") },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = textSec) },
                                    onClick = {
                                        showMenu = false
                                        showClearChatDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Chat", color = RoseAccent) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = RoseAccent) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteChatDialog = true
                                    }
                                )
                                if (!isBlocked) {
                                    DropdownMenuItem(
                                        text = { Text("Block @${screenState.friendUsername}", color = RoseAccent) },
                                        leadingIcon = { Icon(Icons.Default.Block, null, tint = RoseAccent) },
                                        onClick = {
                                            showMenu = false
                                            showBlockConfirmDialog = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Unblock @${screenState.friendUsername}", color = IndigoPrimary) },
                                        leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = IndigoPrimary) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.unblockUser(screenState.friendId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = surface, shadowElevation = 4.dp) {
                if (isBlocked) {
                    // Blocked banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Block, null, tint = RoseAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "You have blocked this contact.",
                                color = textSec,
                                fontSize = 13.sp
                            )
                        }
                        Button(
                            onClick = { viewModel.unblockUser(screenState.friendId) },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Unblock", color = Color.White, fontSize = 12.sp)
                        }
                    }
                } else if (isBlockedByOther) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "You cannot message this user.",
                            color = textSec,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val showVoiceRecorder by viewModel.showVoiceRecorder.collectAsState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        // Voice recorder overlay
                        if (showVoiceRecorder) {
                            VoiceMessageRecorder(
                                modifier = Modifier.fillMaxWidth(),
                                onRecordingComplete = { filePath, duration ->
                                    viewModel.sendVoiceMessage(
                                        screenState.chatId,
                                        screenState.friendId,
                                        filePath,
                                        duration
                                    )
                                },
                                onCancel = { viewModel.hideVoiceRecorder() }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Text input and attachment row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Attachment button & popup menu
                            Box {
                                IconButton(
                                    onClick = { showAttachmentMenu = true },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = "Attach",
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showAttachmentMenu,
                                    onDismissRequest = { showAttachmentMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Photo & Gallery", color = textPrim) },
                                        leadingIcon = { Icon(Icons.Default.Image, null, tint = IndigoPrimary) },
                                        onClick = {
                                            showAttachmentMenu = false
                                            imagePickerLauncher.launch("image/*")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Video", color = textPrim) },
                                        leadingIcon = { Icon(Icons.Default.VideoFile, null, tint = VioletAccent) },
                                        onClick = {
                                            showAttachmentMenu = false
                                            videoPickerLauncher.launch("video/*")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Document / File", color = textPrim) },
                                        leadingIcon = { Icon(Icons.Default.InsertDriveFile, null, tint = EmeraldAccent) },
                                        onClick = {
                                            showAttachmentMenu = false
                                            docPickerLauncher.launch("*/*")
                                        }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { viewModel.setChatInput(it) },
                                placeholder = { Text("Type a message...", color = textSec, fontSize = 14.sp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = IndigoPrimary,
                                    unfocusedBorderColor = if (isDark) DarkDivider else LightDivider,
                                    focusedTextColor = textPrim,
                                    unfocusedTextColor = textPrim,
                                    focusedContainerColor = if (isDark) DarkCard else LightCard,
                                    unfocusedContainerColor = if (isDark) DarkCard else LightCard
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = { viewModel.sendMessage(screenState.chatId, screenState.friendId) }
                                ),
                                maxLines = 4
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            val hasTxt = chatInput.isNotBlank()
                            if (hasTxt) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(listOf(IndigoPrimary, VioletAccent))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(
                                        onClick = {
                                            viewModel.sendMessage(screenState.chatId, screenState.friendId)
                                        },
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) DarkCard else LightCard),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(
                                        onClick = { viewModel.toggleVoiceRecorder() },
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = "Voice",
                                            tint = if (showVoiceRecorder) RoseAccent else IndigoPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    if (isDark) Brush.verticalGradient(
                        listOf(DarkBg, DarkBg, DarkSurface.copy(alpha = 0.3f))
                    ) else Brush.verticalGradient(
                        listOf(LightBg, LightBg)
                    )
                ),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Chat, null, tint = textSec.copy(0.3f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Say hello to ${screenState.friendName}! 👋", color = textSec, fontSize = 15.sp)
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val myUserId = currentUser?.id
                val isMsgFromMe = (msg.senderId == myUserId) || (msg.isFromMe && myUserId == null)
                MessageBubble(
                    msg = msg,
                    isFromMe = isMsgFromMe,
                    isDark = isDark,
                    screenState = screenState,
                    viewModel = viewModel
                )
            }

            if (isTyping && !isBlocked && !isBlockedByOther) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                .background(if (isDark) DarkCard else LightCard)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                repeat(3) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(textSec))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: Message,
    isFromMe: Boolean,
    isDark: Boolean,
    screenState: Screen.Chat,
    viewModel: ChatoozViewModel
) {
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete message?") },
            text = {
                Text(
                    if (isFromMe) "Choose whether to delete this message for everyone or only for yourself."
                    else "This message will be removed from your chat history."
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isFromMe && !msg.isDeletedForEveryone) {
                        Button(
                            onClick = {
                                viewModel.deleteMessage(msg.id, forEveryone = true)
                                showDeleteDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoseAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete for Everyone", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.deleteMessage(msg.id, forEveryone = false)
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFromMe) IndigoPrimary.copy(alpha = 0.15f) else RoseAccent,
                            contentColor = if (isFromMe) IndigoPrimary else Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete for Me", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("Cancel", color = textSec)
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        val bubbleBg = if (isFromMe) {
            Brush.linearGradient(
                listOf(Color(0xFF6366F1), Color(0xFF7C3AED))
            )
        } else {
            Brush.linearGradient(
                if (isDark) listOf(Color(0xFF1E293B), Color(0xFF162032))
                else listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))
            )
        }
        val shape = if (isFromMe) {
            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
        } else {
            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
        }

        Box(
            modifier = Modifier
                .widthIn(min = 52.dp, max = 300.dp)
                .clip(shape)
                .background(bubbleBg)
                .then(
                    if (!isFromMe) {
                        Modifier.border(
                            0.5.dp,
                            if (isDark) Color(0x22FFFFFF) else Color(0x15000000),
                            shape
                        )
                    } else Modifier
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showDeleteDialog = true }
                )
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
        ) {
            Column(
                horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
            ) {
                if (msg.isDeletedForEveryone) {
                    // ── Deleted message tombstone ─────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = if (isFromMe) Color.White.copy(alpha = 0.7f) else textSec,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "This message was deleted",
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = if (isFromMe) Color.White.copy(alpha = 0.8f) else textSec,
                            fontSize = 13.5.sp
                        )
                    }
                } else if (msg.type in listOf("IMAGE", "VIDEO", "FILE")) {
                    // ── Media / File message ──────────────────────────────
                    MediaMessageBubble(
                        msg = msg,
                        isFromMe = isFromMe,
                        isDark = isDark
                    )
                } else if (msg.type == "AUDIO") {
                    // ── Audio voice message ───────────────────────────
                    AudioMessageBubble(
                        filePath = msg.audioFilePath ?: "",
                        durationSec = msg.audioDurationSec ?: 0,
                        isFromMe = isFromMe,
                        isDark = isDark
                    )
                } else if (msg.type == "CALL") {
                    // ── Call Log Message (WhatsApp Style) ─────────────
                    CallLogBubble(
                        msg = msg,
                        isFromMe = isFromMe,
                        isDark = isDark,
                        screenFriendId = screenState.friendId,
                        screenFriendUsername = screenState.friendUsername,
                        screenFriendName = screenState.friendName,
                        screenFriendAvatarColor = screenState.friendAvatarColor,
                        viewModel = viewModel
                    )
                } else {
                    // ── Text message ───────────────────────────────────
                    Text(
                        text = msg.text,
                        color = if (isFromMe) Color.White else textPrim,
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 1.dp, end = if (isFromMe) 2.dp else 0.dp)
                    )
                }

                // ── Timestamp + read receipt ───────────────────────────
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                ) {
                    Text(
                        text = viewModel.formatTimestamp(msg.timestamp),
                        fontSize = 10.sp,
                        color = if (isFromMe) Color.White.copy(alpha = 0.72f) else textSec.copy(alpha = 0.85f)
                    )
                    if (isFromMe) {
                        Spacer(modifier = Modifier.width(3.dp))
                        ReadReceipt(status = msg.status, size = 12.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogBubble(
    msg: Message,
    isFromMe: Boolean,
    isDark: Boolean,
    screenFriendId: String,
    screenFriendUsername: String,
    screenFriendName: String,
    screenFriendAvatarColor: Long,
    viewModel: ChatoozViewModel
) {
    val isVideo = msg.text.contains("Video", ignoreCase = true) || msg.text.contains("📹")
    val isMissed = msg.text.contains("Missed", ignoreCase = true) ||
                   msg.text.contains("Declined", ignoreCase = true) ||
                   msg.text.contains("Unanswered", ignoreCase = true) ||
                   msg.text.contains("Cancelled", ignoreCase = true)

    val iconColor = if (isMissed) RoseAccent else Color(0xFF10B981)
    val textPrim = if (isFromMe) Color.White else (if (isDark) TextPrimDark else TextPrimLight)
    val textSec = if (isFromMe) Color.White.copy(alpha = 0.75f) else (if (isDark) TextSecDark else TextSecLight)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = if (isFromMe) 0.25f else 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = if (isFromMe) Color.White else iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = msg.text.replace("📹 ", "").replace("📞 ", ""),
                color = textPrim,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = if (isMissed) "Tap to call back" else "Call ended",
                color = textSec,
                fontSize = 11.5.sp
            )
        }

        IconButton(
            onClick = {
                if (isVideo) {
                    viewModel.startVideoCall(screenFriendId, screenFriendUsername, screenFriendName, screenFriendAvatarColor)
                } else {
                    viewModel.startAudioCall(screenFriendId, screenFriendUsername, screenFriendName, screenFriendAvatarColor)
                }
            },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "Call back",
                tint = if (isFromMe) Color.White else IndigoPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun GroupInfoDialog(
    group: Group,
    currentUser: User?,
    allUsers: List<User>,
    isDark: Boolean,
    textPrim: Color,
    textSec: Color,
    onRemoveMember: (String) -> Unit,
    onDeleteGroup: () -> Unit,
    onLeaveGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    val isCreator = group.creatorId == currentUser?.id
    val surface = if (isDark) DarkSurface else LightSurface

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatoozAvatar(
                    name = group.name,
                    avatarColor = group.avatarColor,
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(group.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textPrim)
                    Text("${group.members.size} members", fontSize = 12.sp, color = textSec)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                Text(
                    "Members",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = textSec,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(group.members) { member ->
                        val matchedUser = allUsers.find { it.id == member.userId }
                        val displayName: String = if (member.userId == currentUser?.id) "You" else (matchedUser?.name ?: member.userName ?: "Member")
                        val username: String = matchedUser?.username ?: member.username ?: member.userId
                        val isMemberCreator = member.userId == group.creatorId
                        val avatarColor: Long = matchedUser?.avatarColor ?: member.avatarColor ?: 0xFF8B5CF6L
                        val avatarUrl: String? = matchedUser?.avatarUrl

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isDark) Color(0xFF1E1E2E) else Color(0xFFF3F4F6),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatoozAvatar(
                                name = displayName,
                                avatarColor = avatarColor,
                                avatarUrl = avatarUrl,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        displayName,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = textPrim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isMemberCreator) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = IndigoPrimary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "Admin",
                                                color = IndigoPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "@$username",
                                    fontSize = 11.sp,
                                    color = textSec,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isCreator && member.userId != currentUser?.id) {
                                IconButton(
                                    onClick = { onRemoveMember(member.userId) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PersonRemove,
                                        contentDescription = "Remove member",
                                        tint = RoseAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = if (isDark) Color(0xFF2E2E3E) else Color(0xFFE5E7EB))
                Spacer(modifier = Modifier.height(12.dp))

                if (isCreator) {
                    Button(
                        onClick = onDeleteGroup,
                        colors = ButtonDefaults.buttonColors(containerColor = RoseAccent),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Group", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onLeaveGroup,
                        colors = ButtonDefaults.buttonColors(containerColor = RoseAccent),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exit Group", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = IndigoPrimary)
            }
        },
        containerColor = surface
    )
}

