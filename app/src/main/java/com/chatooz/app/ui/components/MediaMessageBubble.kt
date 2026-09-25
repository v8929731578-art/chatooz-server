package com.chatooz.app.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.chatooz.app.model.Message
import com.chatooz.app.ui.theme.*
import java.io.File

/**
 * MediaMessageBubble — renders Image, Video, or generic File messages with modern visual styling,
 * preview cards, download/open actions, and size indicators.
 */
@Composable
fun MediaMessageBubble(
    msg: Message,
    isFromMe: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textPrim = if (isFromMe) Color.White else (if (isDark) TextPrimDark else TextPrimLight)
    val textSec = if (isFromMe) Color.White.copy(alpha = 0.75f) else (if (isDark) TextSecDark else TextSecLight)

    val filePath = msg.mediaFilePath
    val file = filePath?.let { File(it) }
    val fileExists = file?.exists() == true

    fun openFile() {
        if (!fileExists || file == null) return
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, msg.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            try {
                val uri = Uri.fromFile(file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, msg.mimeType ?: "*/*")
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    when (msg.type) {
        "IMAGE" -> {
            val bitmap = remember(msg.mediaFilePath, msg.mediaBase64) {
                try {
                    if (fileExists && file != null) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else if (!msg.mediaBase64.isNullOrBlank()) {
                        val bytes = android.util.Base64.decode(msg.mediaBase64, android.util.Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            Column(
                modifier = modifier
                    .widthIn(min = 180.dp, max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = fileExists, onClick = { openFile() })
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Image message",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(if (isDark) DarkCard else LightCard, shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = textSec, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Image", fontSize = 12.sp, color = textSec)
                        }
                    }
                }
                if (msg.text.isNotBlank() && msg.text != "📷 Photo") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = msg.text,
                        color = textPrim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        "VIDEO" -> {
            Column(
                modifier = modifier
                    .widthIn(min = 180.dp, max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = fileExists, onClick = { openFile() })
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("VIDEO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (msg.fileName != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = msg.fileName,
                        color = textPrim,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        else -> {
            // Generic FILE message
            val sizeStr = remember(msg.fileSize) {
                val size = msg.fileSize ?: 0L
                when {
                    size < 1024 -> "$size B"
                    size < 1024 * 1024 -> "${size / 1024} KB"
                    else -> "%.1f MB".format(size.toFloat() / (1024 * 1024))
                }
            }

            val fileIcon = remember(msg.fileName, msg.mimeType) {
                val ext = msg.fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
                when (ext) {
                    "pdf" -> Icons.Default.PictureAsPdf
                    "doc", "docx" -> Icons.Default.Description
                    "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
                    "mp3", "wav", "aac", "ogg" -> Icons.Default.AudioFile
                    "mp4", "mkv", "avi", "mov" -> Icons.Default.VideoFile
                    "apk" -> Icons.Default.Android
                    else -> Icons.Default.InsertDriveFile
                }
            }

            Row(
                modifier = modifier
                    .widthIn(min = 180.dp, max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isFromMe) Color.White.copy(alpha = 0.15f)
                        else (if (isDark) DarkCard else LightCard)
                    )
                    .clickable(enabled = fileExists, onClick = { openFile() })
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFromMe) Color.White.copy(alpha = 0.25f)
                            else IndigoPrimary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = fileIcon,
                        contentDescription = "File",
                        tint = if (isFromMe) Color.White else IndigoPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = msg.fileName ?: "Document",
                        color = textPrim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sizeStr,
                        color = textSec,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = "Open",
                    tint = textSec,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
