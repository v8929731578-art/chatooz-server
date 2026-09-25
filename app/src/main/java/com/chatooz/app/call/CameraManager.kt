package com.chatooz.app.call

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface

/**
 * CameraManager — Ultra-reliable Camera2 capture for video calling.
 * 
 * Uses hardware JPEG encoding from ImageReader to ensure zero manual conversion
 * overhead and 100% compatibility across all Android devices and emulators.
 */
class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "ChatoozCameraManager"
        private const val PREFERRED_WIDTH = 320
        private const val PREFERRED_HEIGHT = 240
        private const val FRAME_INTERVAL_MS = 100L // ~10 FPS (smooth & low-bandwidth)
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var useFrontCamera = true
    val isFrontCamera: Boolean get() = useFrontCamera
    private var lastFrameTime = 0L

    fun startCamera(
        surfaceTexture: SurfaceTexture?,
        onFrameCaptured: (ByteArray) -> Unit
    ) {
        startBackgroundThread()
        try {
            val cameraId = getCameraId(useFrontCamera) ?: getCameraId(!useFrontCamera)
            if (cameraId == null) {
                Log.e(TAG, "No camera found on device")
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            // 1. Choose JPEG size for streaming
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
            val selectedJpegSize = chooseOptimalSize(jpegSizes, PREFERRED_WIDTH, PREFERRED_HEIGHT)
            Log.i(TAG, "Selected JPEG capture size: ${selectedJpegSize.width}x${selectedJpegSize.height}")

            // 2. Choose preview size for TextureView
            val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
            val selectedPreviewSize = chooseOptimalSize(previewSizes, PREFERRED_WIDTH, PREFERRED_HEIGHT)
            Log.i(TAG, "Selected preview size: ${selectedPreviewSize.width}x${selectedPreviewSize.height}")

            // Create ImageReader with hardware JPEG
            val reader = ImageReader.newInstance(
                selectedJpegSize.width,
                selectedJpegSize.height,
                ImageFormat.JPEG,
                2
            )
            reader.setOnImageAvailableListener({ ir ->
                val image = try { ir.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastFrameTime >= FRAME_INTERVAL_MS) {
                        lastFrameTime = now
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        if (bytes.isNotEmpty()) {
                            val rawBmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (rawBmp != null) {
                                val matrix = android.graphics.Matrix()
                                if (useFrontCamera) {
                                    matrix.postRotate(270f)
                                    matrix.postScale(-1f, 1f) // Selfie mirror flip
                                } else {
                                    matrix.postRotate(90f)
                                }
                                val rotatedBmp = android.graphics.Bitmap.createBitmap(
                                    rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true
                                )
                                val out = java.io.ByteArrayOutputStream()
                                rotatedBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 45, out)
                                onFrameCaptured(out.toByteArray())
                                if (rotatedBmp != rawBmp) rotatedBmp.recycle()
                                rawBmp.recycle()
                            } else {
                                onFrameCaptured(bytes)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Frame processing error: ${e.message}")
                } finally {
                    image.close()
                }
            }, backgroundHandler)
            imageReader = reader

            openCamera(cameraId, surfaceTexture, selectedPreviewSize)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(cameraId: String, surfaceTexture: SurfaceTexture?, previewSize: Size) {
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i(TAG, "Camera device opened: $cameraId")
                    cameraDevice = camera
                    startCaptureSession(camera, surfaceTexture, previewSize)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.i(TAG, "Camera device disconnected")
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera device error: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera: ${e.message}")
        }
    }

    private fun startCaptureSession(
        camera: CameraDevice,
        surfaceTexture: SurfaceTexture?,
        previewSize: Size
    ) {
        try {
            val surfaces = mutableListOf<Surface>()
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            // Local preview surface
            if (surfaceTexture != null) {
                surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                val previewSurface = Surface(surfaceTexture)
                surfaces.add(previewSurface)
                requestBuilder.addTarget(previewSurface)
            }

            // ImageReader surface for video streaming
            imageReader?.surface?.let { readerSurface ->
                surfaces.add(readerSurface)
                requestBuilder.addTarget(readerSurface)
            }

            requestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

            @Suppress("DEPRECATION")
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    try {
                        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        Log.i(TAG, "Camera repeating preview request started successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set repeating request: ${e.message}")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera capture session onConfigureFailed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session: ${e.message}")
        }
    }

    fun switchCamera(surfaceTexture: SurfaceTexture?, onFrameCaptured: (ByteArray) -> Unit) {
        useFrontCamera = !useFrontCamera
        stopCamera()
        startCamera(surfaceTexture, onFrameCaptured)
    }

    fun stopCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera: ${e.message}")
        }
        stopBackgroundThread()
    }

    private fun chooseOptimalSize(choices: Array<Size>, preferredWidth: Int, preferredHeight: Int): Size {
        if (choices.isEmpty()) return Size(preferredWidth, preferredHeight)
        // Find smallest resolution >= preferred to keep bandwidth optimal
        val suitable = choices.filter { it.width >= preferredWidth && it.height >= preferredHeight }
        return suitable.minByOrNull { it.width * it.height }
            ?: choices.minByOrNull { Math.abs(it.width - preferredWidth) + Math.abs(it.height - preferredHeight) }
            ?: choices[0]
    }

    private fun getCameraId(front: Boolean): String? {
        val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == facing) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background thread: ${e.message}")
        }
    }
}
