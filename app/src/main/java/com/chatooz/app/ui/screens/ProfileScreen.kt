package com.chatooz.app.ui.screens

import android.content.Intent
import android.net.Uri
import com.chatooz.app.data.AppConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatooz.app.ui.components.ChatoozAvatar
import com.chatooz.app.ui.components.CropShape
import com.chatooz.app.ui.components.ImageAdjusterDialog
import com.chatooz.app.ui.components.QrCodeDialog
import com.chatooz.app.ui.theme.*
import com.chatooz.app.util.ContactHelper
import com.chatooz.app.viewmodel.ChatoozViewModel

@Composable
fun ProfileScreen(
    viewModel: ChatoozViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsState()
    val friendFeedback by viewModel.friendActionFeedback.collectAsState()
    val isDark by viewModel.isDark.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var rawBitmapForCrop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        rawBitmapForCrop = bmp
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (rawBitmapForCrop != null) {
        ImageAdjusterDialog(
            bitmap = rawBitmapForCrop!!,
            cropShape = CropShape.CIRCLE,
            onDismiss = { rawBitmapForCrop = null },
            onConfirmCrop = { croppedBmp ->
                viewModel.updateProfileAvatarBitmap(context, croppedBmp)
                rawBitmapForCrop = null
            }
        )
    }

    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val card = if (isDark) DarkCard else LightCard
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    val user = currentUser ?: return

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to log in again with your Gmail to continue.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.logout(); showLogoutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAccent)
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            },
            containerColor = surface
        )
    }

    if (showQrDialog) {
        QrCodeDialog(
            user = user,
            isDark = isDark,
            onDismiss = { showQrDialog = false }
        )
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            user = user,
            isDark = isDark,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, phone, address, bio ->
                viewModel.updateUserProfileDetails(name, phone, address, bio)
                showEditProfileDialog = false
            }
        )
    }

    Scaffold(
        containerColor = bg,
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
                    Text("Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textPrim, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Profile Hero
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(IndigoPrimary.copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            ChatoozAvatar(user.name, user.avatarColor, user.avatarUrl, size = 96.dp)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(IndigoPrimary)
                                    .border(2.dp, surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Change Profile Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(user.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textPrim)
                        Text("@${user.username}", fontSize = 15.sp, color = IndigoPrimary, fontWeight = FontWeight.Medium)
                        if (user.bio.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(user.bio, fontSize = 13.sp, color = textSec)
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { showEditProfileDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IndigoPrimary,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(3.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Profile Details", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }
                }
            }

            // Cloud Sync Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = card),
                    elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (cloudSyncStatus == "Synced") EmeraldAccent.copy(0.15f) else AmberAccent.copy(0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (cloudSyncStatus == "Synced") EmeraldAccent else AmberAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multi-Device Cloud Sync", fontWeight = FontWeight.SemiBold, color = textPrim, fontSize = 14.sp)
                            Text(
                                text = when (cloudSyncStatus) {
                                    "Synced" -> "Connected • All accounts synced across devices"
                                    "Syncing..." -> "Syncing with cloud..."
                                    else -> "Working offline • Tap to reconnect"
                                },
                                color = textSec,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { viewModel.triggerManualSync() }) {
                            Icon(Icons.Default.Refresh, "Sync now", tint = IndigoPrimary)
                        }
                    }
                }
            }

            // Account Details
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = card),
                    elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Account Details", fontWeight = FontWeight.Bold, color = textPrim, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        ProfileDetailRow(Icons.Default.Email, "Gmail Address", user.email.ifBlank { "Not linked" }, textPrim, textSec)
                        HorizontalDivider(color = (if (isDark) DarkDivider else LightDivider).copy(0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        ProfileDetailRow(Icons.Default.AlternateEmail, "Chatooz Username", "@${user.username}", textPrim, IndigoPrimary)
                        HorizontalDivider(color = (if (isDark) DarkDivider else LightDivider).copy(0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        ProfileDetailRow(Icons.Default.Phone, "Mobile Number", if (user.phone.isNotBlank()) user.phone else "Tap Edit to add mobile number", textPrim, if (user.phone.isNotBlank()) textPrim else textSec.copy(0.7f))
                        HorizontalDivider(color = (if (isDark) DarkDivider else LightDivider).copy(0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        ProfileDetailRow(Icons.Default.LocationOn, "Address / Location", if (user.address.isNotBlank()) user.address else "Tap Edit to add address", textPrim, if (user.address.isNotBlank()) textPrim else textSec.copy(0.7f))
                        HorizontalDivider(color = (if (isDark) DarkDivider else LightDivider).copy(0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        ProfileDetailRow(Icons.Default.Info, "About & Bio", if (user.bio.isNotBlank()) user.bio else "Hey, I'm on Chatooz! 🚀", textPrim, textSec)
                    }
                }
            }

            // My QR Barcode & Invite Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { showQrDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = card),
                    elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(IndigoPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("My QR Barcode & Invite", fontWeight = FontWeight.Bold, color = textPrim, fontSize = 14.sp)
                            Text("Share barcode to connect & invite friends", color = textSec, fontSize = 11.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = textSec.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                }
            }



            // Blocked Users Management Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = card),
                    elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Block, null, tint = RoseAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Blocked Users (${blockedUsers.size})",
                                fontWeight = FontWeight.Bold,
                                color = textPrim,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (blockedUsers.isEmpty()) {
                            Text(
                                "No blocked users",
                                color = textSec,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            blockedUsers.forEach { bUser ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(RoseAccent.copy(0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                bUser.blockedUsername.firstOrNull()?.uppercase() ?: "?",
                                                color = RoseAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("@${bUser.blockedUsername}", fontWeight = FontWeight.SemiBold, color = textPrim, fontSize = 14.sp)
                                            if (bUser.blockedName.isNotBlank()) {
                                                Text(bUser.blockedName, color = textSec, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = { viewModel.unblockUser(bUser.blockedId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Unblock", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Theme toggle
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { viewModel.toggleDarkMode() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = card),
                    elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = VioletAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Theme", fontWeight = FontWeight.Medium, color = textPrim, fontSize = 15.sp)
                            Text(if (isDark) "Dark mode — tap to switch to light" else "Light mode — tap to switch to dark", color = textSec, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isDark,
                            onCheckedChange = { viewModel.toggleDarkMode() },
                            colors = SwitchDefaults.colors(checkedTrackColor = IndigoPrimary)
                        )
                    }
                }
            }

            // Log out
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { showLogoutDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RoseAccent.copy(alpha = if (isDark) 0.15f else 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Logout, null, tint = RoseAccent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Log out", fontWeight = FontWeight.SemiBold, color = RoseAccent, fontSize = 15.sp)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ProfileDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    textPrim: Color,
    valueColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = textPrim.copy(0.5f), modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 11.sp, color = textPrim.copy(0.5f))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = valueColor)
        }
    }
}

@Composable
private fun EditProfileDialog(
    user: com.chatooz.app.model.User,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, bio: String) -> Unit
) {
    var name by remember(user) { mutableStateOf(user.name) }
    var phone by remember(user) { mutableStateOf(user.phone) }
    var address by remember(user) { mutableStateOf(user.address) }
    var bio by remember(user) { mutableStateOf(user.bio) }

    val surface = if (isDark) DarkSurface else LightSurface
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Edit Profile Details", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textPrim)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, null, tint = IndigoPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = textSec.copy(0.3f),
                        focusedLabelColor = IndigoPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number (Optional)") },
                    placeholder = { Text("e.g. +91 9876543210") },
                    leadingIcon = { Icon(Icons.Default.Phone, null, tint = IndigoPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = textSec.copy(0.3f),
                        focusedLabelColor = IndigoPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Location (Optional)") },
                    placeholder = { Text("e.g. New Delhi, India") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = IndigoPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = textSec.copy(0.3f),
                        focusedLabelColor = IndigoPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio / Status") },
                    placeholder = { Text("Hey, I'm on Chatooz! 🚀") },
                    leadingIcon = { Icon(Icons.Default.Info, null, tint = IndigoPrimary) },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoPrimary,
                        unfocusedBorderColor = textSec.copy(0.3f),
                        focusedLabelColor = IndigoPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone, address, bio) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
            ) {
                Text("Save Changes ✨", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSec)
            }
        },
        containerColor = surface,
        shape = RoundedCornerShape(20.dp)
    )
}

