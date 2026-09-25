package com.chatooz.app.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chatooz.app.ui.theme.IndigoPrimary
import kotlin.math.max
import kotlin.math.min

enum class CropShape {
    CIRCLE,     // For Profile Avatar DP
    RECTANGLE   // For Story / Image Status
}

@Composable
fun ImageAdjusterDialog(
    bitmap: Bitmap,
    cropShape: CropShape = CropShape.RECTANGLE,
    onDismiss: () -> Unit,
    onConfirmCrop: (Bitmap) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    // Dimensions of view
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Top Bar
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
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }

                Text(
                    text = if (cropShape == CropShape.CIRCLE) "Adjust Profile Photo" else "Adjust Story Photo",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = {
                        rotationAngle = (rotationAngle + 90f) % 360f
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White)
                }
            }

            // Interactive Cropping Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 80.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    canvasSize = size
                    val cWidth = size.width
                    val cHeight = size.height

                    // Define crop frame region
                    val cropRect = if (cropShape == CropShape.CIRCLE) {
                        val cropRadius = min(cWidth, cHeight) * 0.42f
                        Rect(
                            center = Offset(cWidth / 2f, cHeight / 2f),
                            radius = cropRadius
                        )
                    } else {
                        // Vertical 9:16 or fits canvas nicely
                        val frameWidth = cWidth * 0.88f
                        val frameHeight = min(cHeight * 0.85f, frameWidth * (16f / 9f))
                        Rect(
                            left = (cWidth - frameWidth) / 2f,
                            top = (cHeight - frameHeight) / 2f,
                            right = (cWidth + frameWidth) / 2f,
                            bottom = (cHeight + frameHeight) / 2f
                        )
                    }

                    // 1. Draw Image with user transform
                    val imageBitmap = bitmap.asImageBitmap()
                    val bmpWidth = bitmap.width.toFloat()
                    val bmpHeight = bitmap.height.toFloat()

                    // Fit image into initial frame
                    val initialFitScale = max(cropRect.width / bmpWidth, cropRect.height / bmpHeight)
                    val currentScale = initialFitScale * scale

                    val imageCenter = Offset(
                        cWidth / 2f + offset.x,
                        cHeight / 2f + offset.y
                    )

                    // Draw the image
                    drawContext.canvas.save()
                    drawContext.canvas.translate(imageCenter.x, imageCenter.y)
                    drawContext.canvas.rotate(rotationAngle)
                    drawContext.canvas.scale(currentScale, currentScale)
                    drawContext.canvas.translate(-bmpWidth / 2f, -bmpHeight / 2f)

                    drawImage(imageBitmap)
                    drawContext.canvas.restore()

                    // 2. Draw Dimmed Mask outside the crop area
                    val fullPath = Path().apply {
                        addRect(Rect(0f, 0f, cWidth, cHeight))
                    }
                    val cropPath = Path().apply {
                        if (cropShape == CropShape.CIRCLE) {
                            addOval(cropRect)
                        } else {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    rect = cropRect,
                                    radiusX = 16f,
                                    radiusY = 16f
                                )
                            )
                        }
                    }

                    // Subtract crop area from mask
                    val maskPath = Path.combine(PathOperation.Difference, fullPath, cropPath)
                    drawPath(maskPath, color = Color.Black.copy(alpha = 0.65f))

                    // 3. Draw Crop Frame Outline
                    if (cropShape == CropShape.CIRCLE) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f),
                            radius = cropRect.width / 2f,
                            center = cropRect.center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                        )
                    } else {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.9f),
                            topLeft = cropRect.topLeft,
                            size = cropRect.size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }
            }

            // Bottom Floating Confirmation Controls
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                            rotationAngle = 0f
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            // Render cropped bitmap
                            val cropped = renderCroppedBitmap(
                                bitmap = bitmap,
                                cropShape = cropShape,
                                scale = scale,
                                offset = offset,
                                rotation = rotationAngle,
                                canvasSize = canvasSize
                            )
                            onConfirmCrop(cropped)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set Photo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/**
 * Computes exact pixels based on user pan/zoom/rotate on canvas and outputs high-quality cropped Bitmap.
 */
private fun renderCroppedBitmap(
    bitmap: Bitmap,
    cropShape: CropShape,
    scale: Float,
    offset: Offset,
    rotation: Float,
    canvasSize: Size
): Bitmap {
    val cWidth = if (canvasSize.width > 0) canvasSize.width else 1080f
    val cHeight = if (canvasSize.height > 0) canvasSize.height else 1920f

    val cropRect = if (cropShape == CropShape.CIRCLE) {
        val cropRadius = min(cWidth, cHeight) * 0.42f
        Rect(center = Offset(cWidth / 2f, cHeight / 2f), radius = cropRadius)
    } else {
        val frameWidth = cWidth * 0.88f
        val frameHeight = min(cHeight * 0.85f, frameWidth * (16f / 9f))
        Rect(
            left = (cWidth - frameWidth) / 2f,
            top = (cHeight - frameHeight) / 2f,
            right = (cWidth + frameWidth) / 2f,
            bottom = (cHeight + frameHeight) / 2f
        )
    }

    val bmpWidth = bitmap.width.toFloat()
    val bmpHeight = bitmap.height.toFloat()
    val initialFitScale = max(cropRect.width / bmpWidth, cropRect.height / bmpHeight)
    val currentScale = initialFitScale * scale

    val imageCenter = Offset(cWidth / 2f + offset.x, cHeight / 2f + offset.y)

    // Output target dimension
    val targetWidth = if (cropShape == CropShape.CIRCLE) 512 else min(1080, cropRect.width.toInt())
    val targetHeight = if (cropShape == CropShape.CIRCLE) 512 else min(1920, (cropRect.height * (targetWidth / cropRect.width)).toInt())

    val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(resultBitmap)

    val matrix = Matrix()
    // Map Canvas cropRect to (0, 0, targetWidth, targetHeight)
    val ratioX = targetWidth.toFloat() / cropRect.width
    val ratioY = targetHeight.toFloat() / cropRect.height

    matrix.postTranslate(-cropRect.left, -cropRect.top)
    matrix.postScale(ratioX, ratioY)

    // Apply Image Matrix inside crop space
    val imgMatrix = Matrix()
    imgMatrix.postTranslate(-bmpWidth / 2f, -bmpHeight / 2f)
    imgMatrix.postRotate(rotation)
    imgMatrix.postScale(currentScale, currentScale)
    imgMatrix.postTranslate(imageCenter.x, imageCenter.y)

    val finalMatrix = Matrix()
    finalMatrix.set(imgMatrix)
    finalMatrix.postTranslate(-cropRect.left, -cropRect.top)
    finalMatrix.postScale(ratioX, ratioY)

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(bitmap, finalMatrix, paint)

    return resultBitmap
}
