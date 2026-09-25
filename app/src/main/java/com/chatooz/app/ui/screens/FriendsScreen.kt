package com.chatooz.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.core.content.ContextCompat
import com.chatooz.app.data.ChatoozCloudApi
import com.chatooz.app.model.FriendRequest
import com.chatooz.app.model.User
import com.chatooz.app.ui.components.ChatoozAvatar
import com.chatooz.app.ui.theme.*
import com.chatooz.app.util.ContactHelper
import com.chatooz.app.util.DeviceContact
import com.chatooz.app.viewmodel.ChatoozViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FriendsScreen(
    viewModel: ChatoozViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark by viewModel.isDark.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val outgoingRequests by viewModel.outgoingRequests.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val feedback by viewModel.friendActionFeedback.collectAsState()

    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    val currentUser by viewModel.currentUser.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showQrDialog by remember { mutableStateOf(false) }

    // Show feedback snackbar
    LaunchedEffect(feedback) {
        if (feedback != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearFeedback()
        }
    }

    if (showQrDialog) {
        com.chatooz.app.ui.components.QrCodeDialog(
            user = currentUser,
            isDark = isDark,
            onDismiss = { showQrDialog = false }
        )
    }

    Scaffold(
        containerColor = bg,
        snackbarHost = {
            if (feedback != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else IndigoPrimary)
                    ) {
                        Text(
                            text = feedback ?: "",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        topBar = {
            Surface(color = surface, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp)
                        .height(60.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textPrim)
                    }
                    Text(
                        "Friends & Contacts",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrim,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Default.QrCode2, "Barcode Share", tint = IndigoPrimary)
                    }
                    IconButton(onClick = { ContactHelper.shareApp(context) }) {
                        Icon(Icons.Default.Share, "Invite Friends", tint = IndigoPrimary)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = surface,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isDark) Color(0xFF1E1E2E) else Color(0xFFE9EEF5),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabItems = listOf(
                            Triple("Friends", friends.size, Icons.Default.People),
                            Triple("Add Friend", 0, Icons.Default.PersonAdd),
                            Triple("Requests", incomingRequests.size, Icons.Default.Mail)
                        )

                        tabItems.forEachIndexed { i, (title, count, icon) ->
                            val isSelected = selectedTab == i
                            val tabBg = if (isSelected) {
                                if (isDark) IndigoPrimary else Color.White
                            } else {
                                Color.Transparent
                            }
                            val contentColor = if (isSelected) {
                                if (isDark) Color.White else IndigoPrimary
                            } else {
                                textSec
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tabBg)
                                    .clickable { selectedTab = i }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = title,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (count > 0 && (i == 0 || i == 2)) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(
                                                    if (i == 2) RoseAccent else (if (isSelected && isDark) Color.White.copy(alpha = 0.25f) else IndigoPrimary),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$count",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            when (selectedTab) {
                0 -> MyFriendsTab(friends = friends, isDark = isDark, textPrim = textPrim, textSec = textSec, onMessage = { friend ->
                    val me = viewModel.currentUser.value ?: return@MyFriendsTab
                    val chatId = viewModel.storage.chatIdFor(me.id, friend.id)
                    val chat = viewModel.storage.getChats(me.id).find { it.id == chatId }
                        ?: com.chatooz.app.model.Chat(
                            id = chatId,
                            friendId = friend.id,
                            friendName = friend.name,
                            friendUsername = friend.username,
                            friendAvatarColor = friend.avatarColor,
                            friendAvatarUrl = friend.avatarUrl,
                            lastMessageText = "Start chatting!",
                            lastMessageTime = System.currentTimeMillis()
                        ).also { viewModel.storage.upsertChat(me.id, it) }
                    viewModel.openChat(chat)
                })
                1 -> AddFriendTab(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    viewModel = viewModel,
                    isDark = isDark,
                    textPrim = textPrim,
                    textSec = textSec
                )
                2 -> RequestsTab(
                    incomingRequests = incomingRequests,
                    outgoingRequests = outgoingRequests,
                    viewModel = viewModel,
                    isDark = isDark,
                    textPrim = textPrim,
                    textSec = textSec
                )
            }
        }
    }
}

@Composable
private fun MyFriendsTab(
    friends: List<User>,
    isDark: Boolean,
    textPrim: Color,
    textSec: Color,
    onMessage: (User) -> Unit
) {
    if (friends.isEmpty()) {
        EmptyState(
            icon = { Icon(Icons.Default.People, null, tint = textSec.copy(0.3f), modifier = Modifier.size(72.dp)) },
            title = "No friends yet",
            subtitle = "Switch to \"Add Friend\" to find people by @username",
            isDark = isDark
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(friends, key = { it.id }) { friend ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChatoozAvatar(friend.name, friend.avatarColor, friend.avatarUrl, 48.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(friend.name, fontWeight = FontWeight.SemiBold, color = textPrim, fontSize = 16.sp)
                        Text("@${friend.username}", color = IndigoPrimary, fontSize = 13.sp)
                        Text(friend.bio, color = textSec, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledIconButton(
                        onClick = { onMessage(friend) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = IndigoPrimary.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.Chat, "Message", tint = IndigoPrimary)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 78.dp), color = (if (isDark) DarkDivider else LightDivider).copy(0.5f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun AddFriendTab(
    searchQuery: String,
    searchResults: List<User>,
    viewModel: ChatoozViewModel,
    isDark: Boolean,
    textPrim: Color,
    textSec: Color
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchUsers(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Search by @username or name...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = IndigoPrimary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchUsers("") }) {
                        Icon(Icons.Default.Clear, null, tint = textSec)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IndigoPrimary,
                unfocusedBorderColor = if (isDark) DarkDivider else LightDivider,
                focusedTextColor = textPrim,
                unfocusedTextColor = textPrim,
                focusedContainerColor = if (isDark) DarkCard else LightCard,
                unfocusedContainerColor = if (isDark) DarkCard else LightCard
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            singleLine = true
        )

        if (searchQuery.isBlank()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PersonSearch, null, tint = textSec.copy(0.3f), modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Find your friends", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = textSec)
                Text("Search for their @username to connect", fontSize = 13.sp, color = textSec.copy(0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp))
            }
        } else if (searchResults.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.SearchOff, null, tint = textSec.copy(0.3f), modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No users found for \"$searchQuery\"", fontSize = 15.sp, color = textSec)
                Text("Try a different username", fontSize = 12.sp, color = textSec.copy(0.6f), modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults, key = { it.id }) { user ->
                    val status = viewModel.getFriendRequestStatus(user.id)
                    UserSearchRow(
                        user = user,
                        requestStatus = status,
                        isDark = isDark,
                        textPrim = textPrim,
                        textSec = textSec,
                        onSendRequest = { viewModel.sendFriendRequest(user) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserSearchRow(
    user: User,
    requestStatus: String,
    isDark: Boolean,
    textPrim: Color,
    textSec: Color,
    onSendRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatoozAvatar(user.name, user.avatarColor, user.avatarUrl, 48.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, fontWeight = FontWeight.SemiBold, color = textPrim, fontSize = 15.sp)
            Text("@${user.username}", color = IndigoPrimary, fontSize = 13.sp)
            Text(user.bio, color = textSec, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        when (requestStatus) {
            "BLOCKED" -> AssistChip(
                onClick = {},
                label = { Text("Blocked", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = RoseAccent.copy(0.15f), labelColor = RoseAccent),
                border = AssistChipDefaults.assistChipBorder(false)
            )
            "FRIENDS" -> AssistChip(
                onClick = {},
                label = { Text("Friends ✓", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = EmeraldAccent.copy(0.15f), labelColor = EmeraldAccent),
                border = AssistChipDefaults.assistChipBorder(false)
            )
            "SENT" -> AssistChip(
                onClick = {},
                label = { Text("Sent ✓", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = IndigoPrimary.copy(0.15f), labelColor = IndigoPrimary),
                border = AssistChipDefaults.assistChipBorder(false)
            )
            "RECEIVED" -> AssistChip(
                onClick = {},
                label = { Text("Respond", fontSize = 11.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = AmberAccent.copy(0.2f), labelColor = AmberAccent),
                border = AssistChipDefaults.assistChipBorder(false)
            )
            else -> Button(
                onClick = onSendRequest,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", color = Color.White, fontSize = 12.sp)
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 78.dp), color = (if (isDark) DarkDivider else LightDivider).copy(0.5f), thickness = 0.5.dp)
}

@Composable
private fun RequestsTab(
    incomingRequests: List<FriendRequest>,
    outgoingRequests: List<FriendRequest>,
    viewModel: ChatoozViewModel,
    isDark: Boolean,
    textPrim: Color,
    textSec: Color
) {
    if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
        EmptyState(
            icon = { Icon(Icons.Default.MarkEmailUnread, null, tint = textSec.copy(0.3f), modifier = Modifier.size(72.dp)) },
            title = "No pending requests",
            subtitle = "When someone sends you a friend request, it will appear here",
            isDark = isDark
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (incomingRequests.isNotEmpty()) {
            item {
                Text(
                    "Incoming (${incomingRequests.size})",
                    fontWeight = FontWeight.Bold,
                    color = textPrim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(incomingRequests, key = { it.id }) { req ->
                IncomingRequestRow(
                    request = req,
                    isDark = isDark,
                    textPrim = textPrim,
                    textSec = textSec,
                    onAccept = { viewModel.acceptRequest(req) },
                    onDecline = { viewModel.declineRequest(req) }
                )
            }
        }

        if (outgoingRequests.isNotEmpty()) {
            item {
                Text(
                    "Sent (${outgoingRequests.size})",
                    fontWeight = FontWeight.Bold,
                    color = textPrim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(outgoingRequests, key = { "out_${it.id}" }) { req ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChatoozAvatar(req.senderName, req.senderAvatarColor, req.senderAvatarUrl, 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("@${req.receiverUsername}", fontWeight = FontWeight.SemiBold, color = textPrim, fontSize = 15.sp)
                        Text("Request sent — waiting for response", color = textSec, fontSize = 12.sp)
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("Pending", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = AmberAccent.copy(0.2f), labelColor = AmberAccent),
                        border = AssistChipDefaults.assistChipBorder(false)
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomingRequestRow(
    request: FriendRequest,
    isDark: Boolean,
    textPrim: Color,
    textSec: Color,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightSurface),
        elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatoozAvatar(request.senderName, request.senderAvatarColor, request.senderAvatarUrl, 50.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.senderName, fontWeight = FontWeight.Bold, color = textPrim, fontSize = 15.sp)
                Text("@${request.senderUsername}", color = IndigoPrimary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Accept", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = textSec,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = textSec.copy(0.5f)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Decline", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsTab(
    viewModel: ChatoozViewModel,
    isDark: Boolean,
    textPrim: Color,
    textSec: Color,
    onMessage: (User) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    var allContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var contactFilter by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadContacts() {
        if (!hasPermission) return
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val fetched = ContactHelper.fetchDeviceContacts(context)
            val phones = fetched.map { it.normalizedPhone }.filter { it.isNotBlank() }
            val matchedUsers = if (phones.isNotEmpty()) ChatoozCloudApi.matchContacts(phones) else emptyList()

            val updated = fetched.map { contact ->
                val matched = matchedUsers.find { u ->
                    val uPhone = ContactHelper.normalizePhoneNumber(u.phone)
                    uPhone.isNotBlank() && (uPhone == contact.normalizedPhone || (contact.normalizedPhone.endsWith(uPhone) || uPhone.endsWith(contact.normalizedPhone)))
                }
                if (matched != null) {
                    contact.copy(isOnChatooz = true, matchedUser = matched)
                } else {
                    contact
                }
            }
            withContext(Dispatchers.Main) {
                allContacts = updated
                isLoading = false
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loadContacts()
        }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(IndigoPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Contacts, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(42.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Find Contacts on Chatooz",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textPrim,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Sync your phone contacts to see which of your friends are already on Chatooz, and invite others with one tap to grow your network!",
                fontSize = 14.sp,
                color = textSec,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.PermContactCalendar, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Allow Contacts Access", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { ContactHelper.shareApp(context) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Share, null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share App Link Directly", color = IndigoPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    } else {
        val filtered = if (contactFilter.isBlank()) allContacts else allContacts.filter {
            it.name.contains(contactFilter, ignoreCase = true) || it.rawPhone.contains(contactFilter)
        }
        val onChatoozList = filtered.filter { it.isOnChatooz }
        val inviteList = filtered.filter { !it.isOnChatooz }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Quick Invite Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) DarkCard else IndigoPrimary.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(IndigoPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Invite Friends & Grow 🚀", fontWeight = FontWeight.Bold, color = textPrim, fontSize = 14.sp)
                            Text("Share download link via WhatsApp, SMS, etc.", color = textSec, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { ContactHelper.shareApp(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Share", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = contactFilter,
                    onValueChange = { contactFilter = it },
                    placeholder = { Text("Search contacts by name or phone...", color = textSec, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = textSec) },
                    trailingIcon = {
                        if (contactFilter.isNotBlank()) {
                            IconButton(onClick = { contactFilter = "" }) {
                                Icon(Icons.Default.Clear, null, tint = textSec)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = if (isDark) DarkDivider else LightDivider
                    ),
                    singleLine = true
                )
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = IndigoPrimary)
                    }
                }
            } else {
                // Section: On Chatooz
                if (onChatoozList.isNotEmpty()) {
                    item {
                        Text(
                            "ON CHATOOZ (${onChatoozList.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(onChatoozList, key = { "chatooz_${it.id}_${it.normalizedPhone}" }) { c ->
                        val user = c.matchedUser
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatoozAvatar(user?.name ?: c.name, user?.avatarColor ?: 0xFF6366F1L, user?.avatarUrl, 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.SemiBold, color = textPrim, fontSize = 15.sp)
                                if (user != null) {
                                    Text("@${user.username}", color = IndigoPrimary, fontSize = 13.sp)
                                } else {
                                    Text(c.rawPhone, color = textSec, fontSize = 12.sp)
                                }
                            }
                            Button(
                                onClick = {
                                    user?.let { onMessage(it) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Chat", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = (if (isDark) DarkDivider else LightDivider).copy(0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }

                // Section: Invite Contacts
                if (inviteList.isNotEmpty()) {
                    item {
                        Text(
                            "INVITE TO CHATOOZ (${inviteList.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSec,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(inviteList, key = { "invite_${it.id}_${it.normalizedPhone}" }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) DarkCard else Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    c.name.firstOrNull()?.uppercase() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    color = textPrim,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Medium, color = textPrim, fontSize = 15.sp)
                                Text(c.rawPhone, color = textSec, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { ContactHelper.inviteSingleContact(context, c) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoPrimary)
                            ) {
                                Icon(Icons.Default.Send, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Invite", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = (if (isDark) DarkDivider else LightDivider).copy(0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("No contacts found", color = textSec, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isDark: Boolean
) {
    val textSec = if (isDark) TextSecDark else TextSecLight
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = textSec)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            subtitle,
            fontSize = 13.sp,
            color = textSec.copy(0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

private fun Modifier.tabIndicatorOffset(currentTabPosition: androidx.compose.material3.TabPosition): Modifier {
    return this.then(
        Modifier
            .fillMaxWidth()
            .wrapContentSize(align = Alignment.BottomStart)
            .offset(x = currentTabPosition.left)
            .width(currentTabPosition.width)
    )
}
