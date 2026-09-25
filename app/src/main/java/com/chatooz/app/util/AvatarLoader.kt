package com.chatooz.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import com.chatooz.app.data.AppConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AvatarLoader {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory for cache
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Bypass-Tunnel-Reminder", "true")
                .build()
            chain.proceed(req)
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getCached(key: String): Bitmap? = memoryCache.get(key)

    fun putCache(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }

    fun load(context: Context, urlOrPath: String, onLoaded: (Bitmap?) -> Unit): Job {
        // Quick check memory cache
        val cached = memoryCache.get(urlOrPath)
        if (cached != null) {
            onLoaded(cached)
            return Job().apply { complete() }
        }

        return scope.launch {
            val bitmap = loadBitmapInternal(context, urlOrPath)
            if (bitmap != null) {
                memoryCache.put(urlOrPath, bitmap)
            }
            withContext(Dispatchers.Main) {
                onLoaded(bitmap)
            }
        }
    }

    private fun loadBitmapInternal(context: Context, urlOrPath: String): Bitmap? {
        if (urlOrPath.isBlank()) return null

        try {
            // 1. Direct local file
            val file = File(urlOrPath)
            if (file.exists() && file.isFile) {
                return decodeSampledBitmap(file.absolutePath, 256, 256)
            }

            // 2. Base64 data URI or raw base64 string
            if (urlOrPath.startsWith("data:image") || (urlOrPath.length > 300 && !urlOrPath.startsWith("http") && !urlOrPath.startsWith("/avatar"))) {
                val base64Data = if (urlOrPath.contains(",")) urlOrPath.substringAfter(",") else urlOrPath
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                return if (bmp != null) rotateBitmapIfRequired(bmp, bytes) else null
            }

            // 3. Network URL or relative /avatar/ path
            val fullUrl = if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
                urlOrPath
            } else {
                "${AppConfig.apiBaseUrl.trimEnd('/')}/${urlOrPath.trimStart('/')}"
            }

            // Check disk cache (use path identifier so domain changes don't invalidate cached avatars)
            val cacheKey = if (urlOrPath.contains("/avatar/")) "/avatar/" + urlOrPath.substringAfter("/avatar/") else urlOrPath
            val diskCacheDir = File(context.cacheDir, "avatar_cache").apply { mkdirs() }
            val fileName = "avatar_" + Math.abs(cacheKey.hashCode()) + ".jpg"
            val diskFile = File(diskCacheDir, fileName)

            if (diskFile.exists() && diskFile.length() > 200) {
                val cachedBmp = decodeSampledBitmap(diskFile.absolutePath, 256, 256)
                if (cachedBmp != null) return cachedBmp
            }

            // Fetch from network
            val request = Request.Builder()
                .url(fullUrl)
                .header("Bypass-Tunnel-Reminder", "true")
                .header("User-Agent", "Chatooz-Android/6.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bytes = response.body!!.bytes()
                    if (bytes.size > 200) {
                        try {
                            FileOutputStream(diskFile).use { it.write(bytes) }
                        } catch (e: Exception) {}
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        return if (bmp != null) rotateBitmapIfRequired(bmp, bytes) else null
                    }
                }
            }
        } catch (e: Exception) {
            // Silent fallback
        }
        return null
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        val bmp = BitmapFactory.decodeFile(path, options)
        return if (bmp != null) rotateBitmapIfRequired(bmp, path) else null
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, pathOrBytes: Any): Bitmap {
        try {
            val exif = when (pathOrBytes) {
                is String -> android.media.ExifInterface(pathOrBytes)
                is ByteArray -> android.media.ExifInterface(java.io.ByteArrayInputStream(pathOrBytes))
                else -> return bitmap
            }
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = android.graphics.Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
            if (!matrix.isIdentity) {
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            // ignore
        }
        return bitmap
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
