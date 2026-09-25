package com.chatooz.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chatooz.app.ui.theme.*
import java.io.ByteArrayOutputStream

val StatusGradients = listOf(
    listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)), // Indigo -> Violet
    listOf(Color(0xFFEC4899), Color(0xFFF43F5E)), // Pink -> Rose
    listOf(Color(0xFF059669), Color(0xFF10B981)), // Emerald -> Green
    listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), // Amber -> Red
    listOf(Color(0xFF0284C7), Color(0xFF2563EB)), // Sky -> Blue
    listOf(Color(0xFF1E293B), Color(0xFF0F172A))  // Dark Slate
)

@Composable
fun CreateStatusDialog(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onPostText: (text: String, gradientIndex: Int) -> Unit,
    onPostImage: (imageBytes: ByteArray, caption: String) -> Unit
) {
    val context = LocalContext.current
    var isImageMode by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var selectedGradientIndex by remember { mutableStateOf(0) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var rawPickedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val original = BitmapFactory.decodeStream(inputStream)
                    if (original != null) {
                        rawPickedBitmap = original
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (rawPickedBitmap != null) {
        ImageAdjusterDialog(
            bitmap = rawPickedBitmap!!,
            cropShape = CropShape.RECTANGLE,
            onDismiss = { rawPickedBitmap = null },
            onConfirmCrop = { adjustedBmp ->
                val stream = ByteArrayOutputStream()
                adjustedBmp.compress(Bitmap.CompressFormat.JPEG, 88, stream)
                selectedImageBytes = stream.toByteArray()
                selectedBitmap = adjustedBmp
                isImageMode = true
                rawPickedBitmap = null
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val bgBrush = if (isImageMode) {
            Brush.verticalGradient(listOf(Color.Black, Color.Black))
        } else {
            Brush.verticalGradient(StatusGradients[selectedGradientIndex])
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
        ) {
            // Top Controls (Close, Switch Mode, Color Palette)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Color Palette Button (Text mode only)
                    if (!isImageMode) {
                        IconButton(
                            onClick = {
                                selectedGradientIndex = (selectedGradientIndex + 1) % StatusGradients.size
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = "Change Color", tint = Color.White)
                        }
                    } else {
                        // Re-adjust Crop/Angle Button (Image mode)
                        IconButton(
                            onClick = {
                                if (selectedBitmap != null) {
                                    rawPickedBitmap = selectedBitmap
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = "Adjust Photo", tint = Color.White)
                        }
                    }

                    // Gallery / Camera Button
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick Photo", tint = Color.White)
                    }
                }
            }

            // Center Content
            if (isImageMode && selectedBitmap != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = "Selected Status Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                // Text status input with dynamic text sizing & scrolling
                val textLength = statusText.length
                val (fontSize, lineHeight) = when {
                    textLength <= 70 -> 28.sp to 38.sp
                    textLength <= 160 -> 22.sp to 30.sp
                    textLength <= 400 -> 18.sp to 25.sp
                    else -> 15.sp to 21.sp
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 85.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        TextField(
                            value = statusText,
                            onValueChange = { if (it.length <= 1500) statusText = it },
                            placeholder = {
                                Text(
                                    "Type a status update… ✨",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 24.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White
                            ),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                color = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        )

                        if (statusText.isNotEmpty()) {
                            Text(
                                text = "${statusText.length} / 1500",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Bar: Caption / Send Action
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isImageMode) {
                        TextField(
                            value = statusText,
                            onValueChange = { if (it.length <= 600) statusText = it },
                            placeholder = { Text("Add a caption…", color = Color.White.copy(alpha = 0.6f)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.15f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White
                            ),
                            maxLines = 3,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    FloatingActionButton(
                        onClick = {
                            if (isImageMode) {
                                val bytes = selectedImageBytes
                                if (bytes != null) {
                                    onPostImage(bytes, statusText)
                                    onDismiss()
                                }
                            } else {
                                if (statusText.isNotBlank()) {
                                    onPostText(statusText, selectedGradientIndex)
                                    onDismiss()
                                }
                            }
                        },
                        containerColor = IndigoPrimary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Post Status")
                    }
                }
            }
        }
    }
}
