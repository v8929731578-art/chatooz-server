package com.chatooz.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "AutoUpdateManager"

@Serializable
data class VersionInfo(
    val versionCode: Long = 1,
    val versionName: String = "1.0",
    val downloadUrl: String = "/download-apk",
    val changelog: String = "New updates available!"
)

object AutoUpdateManager {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Bypass-Tunnel-Reminder", "true")
                .build()
            chain.proceed(req)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(context: Context): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            // Do not prompt in-app self APK update if installed via Google Play Store (Play Store handles updates)
            val isPlayStoreInstall = try {
                val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(context.packageName)
                }
                installer == "com.android.vending"
            } catch (_: Exception) {
                false
            }

            if (isPlayStoreInstall) {
                return@withContext null
            }

            val url = AppConfig.versionUrl
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val versionInfo = json.decodeFromString<VersionInfo>(body)
                
                // Get installed app version code
                val currentVersionCode = try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pInfo.versionCode.toLong()
                    }
                } catch (e: Exception) {
                    1L
                }

                // If server's version code is greater than installed, update is available
                if (versionInfo.versionCode > currentVersionCode && versionInfo.versionCode > 1) {
                    return@withContext versionInfo
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "No update check failure: ${e.message}")
        }
        null
    }

    suspend fun downloadAndInstall(
        context: Context,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val url = AppConfig.downloadApkUrl
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }

            val body = response.body ?: run {
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }

            val totalBytes = body.contentLength()
            val apkFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                if (totalBytes > 0) {
                    val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                    withContext(Dispatchers.Main) { onProgress(progress) }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            withContext(Dispatchers.Main) {
                onComplete(true)
                triggerInstall(context, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            withContext(Dispatchers.Main) { onComplete(false) }
        }
    }

    private fun triggerInstall(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install trigger failed: ${e.message}")
        }
    }
}
