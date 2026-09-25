package com.chatooz.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.chatooz.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatooz.app.model.Chat
import com.chatooz.app.model.Status
import com.chatooz.app.ui.components.ChatoozAvatar
import com.chatooz.app.ui.components.CreateStatusDialog
import com.chatooz.app.ui.components.QrCodeDialog
import com.chatooz.app.ui.components.StatusViewerDialog
import com.chatooz.app.ui.theme.*
import com.chatooz.app.util.ContactHelper
import com.chatooz.app.viewmodel.ChatoozViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatoozViewModel,
    onOpenChat: (Chat) -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val allStatuses by viewModel.statuses.collectAsState()
    val isDark by viewModel.isDark.collectAsState()

    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val card = if (isDark) DarkCard else LightCard
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    val totalUnreadReqs = incomingRequests.size
    val friends by viewModel.friends.collectAsState()
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showCreateStatusDialog by remember { mutableStateOf(false) }
    var activeViewerStatuses by remember { mutableStateOf<List<Status>?>(null) }
    var viewerInitialIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val myUserId = currentUser?.id ?: ""

    LaunchedEffect(Unit) {
        viewModel.loadStatuses()
    }

    if (showCreateStatusDialog) {
        CreateStatusDialog(
            isDark = isDark,
            onDismiss = { showCreateStatusDialog = false },
            onPostText = { text, gradientIdx ->
                viewModel.createTextStatus(text, gradientIdx)
            },
            onPostImage = { imageBytes, caption ->
                viewModel.createImageStatus(imageBytes, caption)
            }
        )
    }

    if (activeViewerStatuses != null) {
        StatusViewerDialog(
            statuses = activeViewerStatuses ?: emptyList(),
            initialIndex = viewerInitialIndex,
            currentUserId = myUserId,
            onDismiss = { activeViewerStatuses = null },
            onStatusViewed = { statusId ->
                viewModel.markStatusViewed(statusId)
            },
            onDeleteStatus = { statusId ->
                viewModel.deleteStatus(statusId)
            },
            onToggleLike = { statusId ->
                viewModel.toggleLikeStatus(statusId)
            },
            onRepostStatus = { status ->
                viewModel.repostStatus(status)
            },
            onReplyStatus = { status, text ->
                viewModel.replyToStatus(status, text)
            }
        )
    }

    if (showQrDialog) {
        QrCodeDialog(
            user = currentUser,
            isDark = isDark,
            onDismiss = { showQrDialog = false }
        )
    }

    if (showCreateGroupDialog) {
        com.chatooz.app.ui.components.CreateGroupDialog(
            friends = friends,
            isDark = isDark,
            onDismiss = { showCreateGroupDialog = false },
            onCreateGroup = { name, desc, memberIds ->
                viewModel.createGroup(name, desc, memberIds)
            }
        )
    }

    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats else {
            chats.filter {
                it.friendName.contains(searchQuery, ignoreCase = true) ||
                it.lastMessageText.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        containerColor = bg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = IndigoPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "New Group", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Group", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
            }
        },
        bottomBar = {
            Surface(
                color = surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isDark) DarkGlassBorder else LightGlassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding().height(68.dp)
                ) {
                    val totalUnread = chats.sumOf { it.unreadCount }
                    NavigationBarItem(
                        selected = true,
                        onClick = { viewModel.goHome() },
                        icon = {
                            BadgedBox(badge = {
                                if (totalUnread > 0) {
                                    Badge(containerColor = IndigoPrimary) {
                                        Text("$totalUnread", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.ChatBubble, contentDescription = "Chats", modifier = Modifier.size(22.dp))
                            }
                        },
                        label = { Text("Chats", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IndigoPrimary,
                            selectedTextColor = IndigoPrimary,
                            indicatorColor = IndigoPrimary.copy(alpha = 0.12f),
                            unselectedIconColor = textSec,
                            unselectedTextColor = textSec
                        )
                    )

                    NavigationBarItem(
                        selected = false,
                        onClick = onFriends,
                        icon = {
                            BadgedBox(badge = {
                                if (totalUnreadReqs > 0) {
                                    Badge(containerColor = RoseAccent) {
                                        Text("$totalUnreadReqs", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.PeopleAlt, contentDescription = "Friends", modifier = Modifier.size(22.dp))
                            }
                        },
                        label = { Text("Friends", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IndigoPrimary,
                            selectedTextColor = IndigoPrimary,
                            indicatorColor = IndigoPrimary.copy(alpha = 0.12f),
                            unselectedIconColor = textSec,
                            unselectedTextColor = textSec
                        )
                    )

                    NavigationBarItem(
                        selected = false,
                        onClick = onProfile,
                        icon = {
                            Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(22.dp))
                        },
                        label = { Text("Profile", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IndigoPrimary,
                            selectedTextColor = IndigoPrimary,
                            indicatorColor = IndigoPrimary.copy(alpha = 0.12f),
                            unselectedIconColor = textSec,
                            unselectedTextColor = textSec
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ─── Header: Modern Glass App Bar ──────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    IndigoPrimary.copy(alpha = if (isDark) 0.18f else 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_chatooz_logo),
                                contentDescription = "Chatooz",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Chatooz",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrim,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(OnlineGreen)
                                    )
                                }
                                Text(
                                    text = "⚡ v6.0 • CrystalVoice",
                                    fontSize = 11.sp,
                                    color = IndigoLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { isSearchExpanded = !isSearchExpanded },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = textPrim,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showQrDialog = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Barcode",
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleDarkMode() },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle theme",
                                    tint = textSec,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            currentUser?.let { user ->
                                ChatoozAvatar(
                                    name = user.name,
                                    avatarColor = user.avatarColor,
                                    avatarUrl = user.avatarUrl,
                                    size = 36.dp,
                                    modifier = Modifier
                                        .clickable { onProfile() }
                                        .padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    // Expandable Search Bar
                    AnimatedVisibility(visible = isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search messages, friends...", color = textSec, fontSize = 13.5.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = textSec, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = card,
                                unfocusedContainerColor = card,
                                focusedBorderColor = IndigoPrimary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        )
                    }
                }
            }

            // ─── Stories Carousel Strip ──────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // "My Story" / Add Status Tile
                        item {
                            val myStatuses = allStatuses.filter { it.userId == myUserId }
                            val hasMyStatus = myStatuses.isNotEmpty()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        if (hasMyStatus) {
                                            activeViewerStatuses = myStatuses
                                            viewerInitialIndex = 0
                                        } else {
                                            showCreateStatusDialog = true
                                        }
                                    }
                                    .width(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    ChatoozAvatar(
                                        name = currentUser?.name ?: "Me",
                                        avatarColor = currentUser?.avatarColor ?: 0xFF6366F1L,
                                        avatarUrl = currentUser?.avatarUrl,
                                        size = 56.dp,
                                        hasStory = hasMyStatus,
                                        hasUnreadStory = false
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(IndigoPrimary)
                                            .border(2.dp, bg, CircleShape)
                                            .clickable { showCreateStatusDialog = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Your Story",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Friends' Statuses Carousel
                        val friendStatuses = allStatuses.filter { it.userId != myUserId }.distinctBy { it.userId }
                        items(friendStatuses, key = { it.id }) { status ->
                            val isUnread = !status.viewers.contains(myUserId)
                            val userStatuses = allStatuses.filter { it.userId == status.userId }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        activeViewerStatuses = userStatuses
                                        viewerInitialIndex = 0
                                    }
                                    .width(68.dp)
                            ) {
                                ChatoozAvatar(
                                    name = status.userName,
                                    avatarColor = status.userAvatarColor,
                                    avatarUrl = status.userAvatarUrl,
                                    size = 56.dp,
                                    hasStory = true,
                                    hasUnreadStory = isUnread
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = status.userName.split(" ").firstOrNull() ?: status.userName,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isUnread) textPrim else textSec,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // ─── Pending requests banner ────────────────────────────────────
            if (totalUnreadReqs > 0) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onFriends() },
                        shape = RoundedCornerShape(18.dp),
                        color = IndigoPrimary.copy(alpha = if (isDark) 0.15f else 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndigoPrimary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(IndigoPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "$totalUnreadReqs new friend ${if (totalUnreadReqs == 1) "request" else "requests"}",
                                    fontWeight = FontWeight.Bold,
                                    color = textPrim,
                                    fontSize = 13.5.sp
                                )
                                Text("Tap to view & connect", color = textSec, fontSize = 11.5.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ─── Chats Section Header ───────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Messages",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrim,
                        letterSpacing = (-0.3).sp
                    )
                    if (chats.isNotEmpty()) {
                        Text(
                            text = "${filteredChats.size} chats",
                            fontSize = 12.sp,
                            color = textSec,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ─── Chat List Rows ─────────────────────────────────────────────
            if (filteredChats.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(IndigoPrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Forum,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No chats matching \"$searchQuery\"" else "No conversations yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrim
                        )
                        Text(
                            text = "Add contacts or friends to start chatting!",
                            fontSize = 13.sp,
                            color = textSec,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onFriends,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            elevation = ButtonDefaults.buttonElevation(4.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Find Friends & Contacts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(filteredChats, key = { it.id }) { chat ->
                    ModernChatItemRow(
                        chat = chat,
                        isDark = isDark,
                        viewModel = viewModel,
                        textPrim = textPrim,
                        textSec = textSec,
                        onClick = { onOpenChat(chat) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernChatItemRow(
    chat: Chat,
    isDark: Boolean,
    viewModel: ChatoozViewModel,
    textPrim: Color,
    textSec: Color,
    onClick: () -> Unit
) {
    var showChatOptionsDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val currentUser by viewModel.currentUser.collectAsState()
    val isGroup = chat.id.startsWith("grp_") || chat.friendId.startsWith("grp_")

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear messages?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to clear all messages in this chat?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChat(chat.id)
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
                ) {
                    Text("Clear", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (isGroup) "Delete / Exit Group?" else "Delete conversation?", fontWeight = FontWeight.Bold) },
            text = { Text(if (isGroup) "Are you sure you want to remove or exit this group?" else "Delete chat history permanently?") },
            confirmButton = {
                Button(
                    onClick = {
                        if (isGroup) {
                            val group = viewModel.storage.getGroupById(chat.id) ?: viewModel.storage.getGroupById(chat.friendId)
                            if (group != null && group.creatorId == currentUser?.id) {
                                viewModel.deleteGroup(group.id)
                            } else {
                                viewModel.leaveGroup(group?.id ?: chat.id)
                            }
                        } else {
                            viewModel.deleteChat(chat.id)
                        }
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showChatOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showChatOptionsDialog = false },
            title = { Text(chat.friendName, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            showChatOptionsDialog = false
                            showClearConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = textPrim)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Clear Messages", color = textPrim, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    TextButton(
                        onClick = {
                            showChatOptionsDialog = false
                            showDeleteConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = RoseAccent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isGroup) "Delete / Exit Group" else "Delete Chat", color = RoseAccent, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChatOptionsDialog = false }) {
                    Text("Close", color = IndigoPrimary)
                }
            }
        )
    }

    val allStatuses by viewModel.statuses.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val liveFriend = allUsers.find { it.id == chat.friendId } ?: viewModel.storage.getUserById(chat.friendId)
    val fallbackAvatar = allStatuses.find { it.userId == chat.friendId }?.userAvatarUrl
    val resolvedAvatarUrl = liveFriend?.avatarUrl?.ifBlank { null }
        ?: chat.friendAvatarUrl?.ifBlank { null }
        ?: fallbackAvatar?.ifBlank { null }
    val resolvedAvatarColor = liveFriend?.avatarColor ?: chat.friendAvatarColor
    val resolvedFriendName = liveFriend?.name?.ifBlank { null } ?: chat.friendName

    val isUnread = chat.unreadCount > 0
    val rowBg = if (isUnread) {
        if (isDark) IndigoPrimary.copy(alpha = 0.08f) else IndigoPrimary.copy(alpha = 0.04f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(rowBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showChatOptionsDialog = true }
            )
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatoozAvatar(
                name = resolvedFriendName,
                avatarColor = resolvedAvatarColor,
                avatarUrl = resolvedAvatarUrl,
                size = 52.dp,
                showOnlineBadge = false
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.friendName,
                        fontSize = 15.5.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        color = textPrim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = if (chat.lastMessageTime > 0) viewModel.formatTimestamp(chat.lastMessageTime) else "",
                        fontSize = 11.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                        color = if (isUnread) IndigoPrimary else textSec
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.lastMessageText.ifEmpty { "Tap to open chat" },
                        fontSize = 13.5.sp,
                        color = if (isUnread) textPrim else textSec,
                        fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 22.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(IndigoPrimary, VioletAccent)
                                    )
                                )
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${chat.unreadCount}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp, end = 16.dp),
        color = (if (isDark) DarkDivider else LightDivider).copy(alpha = 0.25f),
        thickness = 0.5.dp
    )
}
