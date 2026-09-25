package com.chatooz.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chatooz.app.BuildConfig
import com.chatooz.app.model.User
import com.chatooz.app.ui.theme.*
import com.chatooz.app.util.QrCodeGenerator

@Composable
fun QrCodeDialog(
    user: User?,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val surface = if (isDark) DarkSurface else LightSurface
    val card = if (isDark) DarkCard else LightCard
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    val currentVersion = BuildConfig.VERSION_NAME
    val downloadUrl = "${com.chatooz.app.data.AppConfig.apiBaseUrl}/download"
    val qrContent = downloadUrl

    val qrBitmap = remember(qrContent) {
        QrCodeGenerator.generateQrBitmap(
            content = qrContent,
            size = 600,
            fgColor = android.graphics.Color.BLACK,
            bgColor = android.graphics.Color.WHITE
        )
    }

    var showCopiedFeedback by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Share Chatooz Barcode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrim
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textSec)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // User Avatar and Name
                ChatoozAvatar(
                    name = user?.name ?: "User",
                    avatarColor = user?.avatarColor ?: 0xFF6366F1L,
                    avatarUrl = user?.avatarUrl,
                    size = 56.dp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = user?.name ?: "Chatooz User",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrim
                )
                Text(
                    text = "@${user?.username ?: "chatooz"}",
                    fontSize = 12.5.sp,
                    color = IndigoPrimary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // QR Code White Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(220.dp)
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Chatooz QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Version Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = IndigoPrimary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "⚡ Chatooz v$currentVersion • Direct Connect",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndigoPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Scan this barcode with any camera to download Chatooz v$currentVersion & connect instantly!",
                    fontSize = 12.sp,
                    color = textSec,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Copy Link Button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Chatooz Link", downloadUrl)
                            clipboard.setPrimaryClip(clip)
                            showCopiedFeedback = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (showCopiedFeedback) "Copied! ✅" else "Copy Link",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndigoPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Share Button
                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "🚀 Connect with me (${user?.name ?: "Chatooz User"} - @${user?.username ?: "chatooz"}) on Chatooz v$currentVersion!\n\n📲 Download & install the latest app here:\n$downloadUrl"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Chatooz v$currentVersion"))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Share",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
