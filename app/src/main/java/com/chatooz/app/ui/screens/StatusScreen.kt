package com.chatooz.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatooz.app.model.Status
import com.chatooz.app.ui.components.ChatoozAvatar
import com.chatooz.app.ui.components.CreateStatusDialog
import com.chatooz.app.ui.components.StatusViewerDialog
import com.chatooz.app.ui.theme.*
import com.chatooz.app.viewmodel.ChatoozViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatusScreen(
    viewModel: ChatoozViewModel,
    onChats: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val allStatuses by viewModel.statuses.collectAsState()
    val isDark by viewModel.isDark.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val incomingRequests by viewModel.incomingRequests.collectAsState()

    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val card = if (isDark) DarkCard else LightCard
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    var showCreateDialog by remember { mutableStateOf(false) }
    var viewingUserId by remember { mutableStateOf<String?>(null) }
    var viewerInitialIndex by remember { mutableIntStateOf(0) }

    val myUserId = currentUser?.id ?: ""
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val activeViewingStatuses = remember(allStatuses, viewingUserId) {
        if (viewingUserId != null) {
            allStatuses.filter { it.userId == viewingUserId }
        } else {
            emptyList()
        }
    }

    // Separate statuses into My Statuses, Recent (Unviewed), and Viewed
    val myStatuses = remember(allStatuses, myUserId) {
        allStatuses.filter { it.userId == myUserId }
    }

    val friendsStatusesGrouped = remember(allStatuses, myUserId) {
        allStatuses.filter { it.userId != myUserId }
            .groupBy { it.userId }
            .values
            .toList()
    }

    val unviewedGroups = remember(friendsStatusesGrouped, myUserId) {
        friendsStatusesGrouped.filter { group ->
            group.any { !it.viewers.contains(myUserId) }
        }
    }

    val viewedGroups = remember(friendsStatusesGrouped, myUserId) {
        friendsStatusesGrouped.filter { group ->
            group.all { it.viewers.contains(myUserId) }
        }
    }

    if (showCreateDialog) {
        CreateStatusDialog(
            isDark = isDark,
            onDismiss = { showCreateDialog = false },
            onPostText = { text, gradientIdx ->
                viewModel.createTextStatus(text, gradientIdx)
            },
            onPostImage = { imageBytes, caption ->
                viewModel.createImageStatus(imageBytes, caption)
            }
        )
    }

    if (viewingUserId != null) {
        if (activeViewingStatuses.isNotEmpty()) {
            StatusViewerDialog(
                statuses = activeViewingStatuses,
                initialIndex = viewerInitialIndex,
                currentUserId = myUserId,
                onDismiss = { viewingUserId = null },
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
        } else {
            LaunchedEffect(Unit) {
                viewingUserId = null
            }
        }
    }

    Scaffold(
        containerColor = bg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = IndigoPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Status", modifier = Modifier.size(28.dp))
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = onChats,
                    icon = {
                        val totalUnread = chats.sumOf { it.unreadCount }
                        BadgedBox(badge = {
                            if (totalUnread > 0) Badge(containerColor = IndigoPrimary) { Text("$totalUnread") }
                        }) {
                            Icon(Icons.Default.Chat, contentDescription = "Chats")
                        }
                    },
                    label = { Text("Chats") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndigoPrimary,
                        selectedTextColor = IndigoPrimary,
                        unselectedIconColor = textSec,
                        unselectedTextColor = textSec,
                        indicatorColor = IndigoPrimary.copy(alpha = 0.12f)
                    )
                )

                NavigationBarItem(
                    selected = true,
                    onClick = { viewModel.loadStatuses() },
                    icon = {
                        val hasUnviewed = unviewedGroups.isNotEmpty()
                        BadgedBox(badge = {
                            if (hasUnviewed) Badge(containerColor = EmeraldPrimary) {}
                        }) {
                            Icon(Icons.Default.MotionPhotosOn, contentDescription = "Status")
                        }
                    },
                    label = { Text("Status") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndigoPrimary,
                        selectedTextColor = IndigoPrimary,
                        unselectedIconColor = textSec,
                        unselectedTextColor = textSec,
                        indicatorColor = IndigoPrimary.copy(alpha = 0.12f)
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = onFriends,
                    icon = {
                        val totalUnreadReqs = incomingRequests.size
                        BadgedBox(badge = {
                            if (totalUnreadReqs > 0) Badge(containerColor = RoseAccent) { Text("$totalUnreadReqs") }
                        }) {
                            Icon(Icons.Default.People, contentDescription = "Friends")
                        }
                    },
                    label = { Text("Friends") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndigoPrimary,
                        selectedTextColor = IndigoPrimary,
                        unselectedIconColor = textSec,
                        unselectedTextColor = textSec,
                        indicatorColor = IndigoPrimary.copy(alpha = 0.12f)
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = onProfile,
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndigoPrimary,
                        selectedTextColor = IndigoPrimary,
                        unselectedIconColor = textSec,
                        unselectedTextColor = textSec,
                        indicatorColor = IndigoPrimary.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Status",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrim
                        )
                        Text(
                            text = "Stories disappear after 24 hours ⏳",
                            fontSize = 12.5.sp,
                            color = textSec
                        )
                    }

                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(IndigoPrimary.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Text Status", tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // My Status Item
            item {
                val hasMyStatus = myStatuses.isNotEmpty()
                val latestMyStatus = myStatuses.lastOrNull()

                Surface(
                    color = card,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            if (hasMyStatus) {
                                viewingUserId = myUserId
                                viewerInitialIndex = 0
                            } else {
                                showCreateDialog = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .then(
                                        if (hasMyStatus) {
                                            Modifier.border(
                                                width = 2.5.dp,
                                                brush = Brush.sweepGradient(listOf(IndigoPrimary, EmeraldPrimary, IndigoPrimary)),
                                                shape = CircleShape
                                            )
                                        } else Modifier
                                    )
                                    .padding(if (hasMyStatus) 3.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ChatoozAvatar(
                                    name = currentUser?.name ?: "User",
                                    avatarColor = currentUser?.avatarColor ?: 0xFF6366F1L,
                                    avatarUrl = currentUser?.avatarUrl,
                                    size = 50.dp
                                )
                            }

                            if (!hasMyStatus) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(IndigoPrimary)
                                        .border(2.dp, card, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "My Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = textPrim
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (hasMyStatus) {
                                    "${myStatuses.size} updates • ${timeFmt.format(Date(latestMyStatus!!.timestamp))}"
                                } else {
                                    "Tap to add status update"
                                },
                                fontSize = 13.sp,
                                color = textSec
                            )
                        }

                        if (hasMyStatus) {
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Add More", tint = IndigoPrimary)
                            }
                        }
                    }
                }
            }

            // Recent / Unviewed Updates Section
            if (unviewedGroups.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Updates",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndigoPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }

                items(unviewedGroups) { group ->
                    val firstStatus = group.first()
                    val unviewedIdx = group.indexOfFirst { !it.viewers.contains(myUserId) }.coerceAtLeast(0)

                    StatusGroupCard(
                        group = group,
                        isViewed = false,
                        cardColor = card,
                        textPrim = textPrim,
                        textSec = textSec,
                        timeFmt = timeFmt,
                        onClick = {
                            viewingUserId = firstStatus.userId
                            viewerInitialIndex = unviewedIdx
                        }
                    )
                }
            }

            // Viewed Updates Section
            if (viewedGroups.isNotEmpty()) {
                item {
                    Text(
                        text = "Viewed Updates",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSec,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }

                items(viewedGroups) { group ->
                    val firstStatus = group.first()
                    StatusGroupCard(
                        group = group,
                        isViewed = true,
                        cardColor = card,
                        textPrim = textPrim,
                        textSec = textSec,
                        timeFmt = timeFmt,
                        onClick = {
                            viewingUserId = firstStatus.userId
                            viewerInitialIndex = 0
                        }
                    )
                }
            }

            // Empty placeholder if no friend statuses
            if (friendsStatusesGrouped.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.MotionPhotosOn,
                            contentDescription = null,
                            tint = textSec.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No recent status updates",
                            color = textSec,
                            fontSize = 14.5.sp
                        )
                        Text(
                            text = "Status updates from your friends will appear here",
                            color = textSec.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusGroupCard(
    group: List<Status>,
    isViewed: Boolean,
    cardColor: Color,
    textPrim: Color,
    textSec: Color,
    timeFmt: SimpleDateFormat,
    onClick: () -> Unit
) {
    val latest = group.last()
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .border(
                        width = 2.5.dp,
                        brush = if (isViewed) Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f)))
                        else Brush.sweepGradient(listOf(IndigoPrimary, EmeraldPrimary, IndigoPrimary)),
                        shape = CircleShape
                    )
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                ChatoozAvatar(
                    name = latest.userName,
                    avatarColor = latest.userAvatarColor,
                    avatarUrl = latest.userAvatarUrl,
                    size = 46.dp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = latest.userName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = textPrim
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${group.size} updates • ${timeFmt.format(Date(latest.timestamp))}",
                    fontSize = 12.5.sp,
                    color = textSec
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = textSec.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
