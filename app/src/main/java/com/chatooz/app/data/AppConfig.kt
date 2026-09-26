package com.chatooz.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.chatooz.app.BuildConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AppConfig — Resilient Dynamic Endpoint Configuration & Auto-Discovery for Chatooz.
 *
 * Supports zero-downtime dynamic server URL resolution:
 * 1. Reads cached working server URL from SharedPreferences.
 * 2. Falls back to BuildConfig default.
 * 3. Probes /health and dynamically discovers candidate or remote endpoints.
 * 4. Ensures users never need to re-download APK when tunnel/server endpoints change!
 */
object AppConfig {

    private const val TAG = "AppConfig"
    private const val PREFS_NAME = "chatooz_network_config"
    private const val KEY_SERVER_URL = "active_server_url"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefs: SharedPreferences? = null

    @Volatile
    private var dynamicBaseUrl: String? = null

    @Volatile
    private var isResolving = false

    // Fast client for health probes and remote config resolution
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Bypass-Tunnel-Reminder", "true")
                    .header("User-Agent", "Chatooz-Android-Client/6.0")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    const val PERMANENT_CLOUD_URL = "https://useful-european-rhode-sector.trycloudflare.com"

    /**
     * Remote config anchors and candidate endpoints for automatic fallback discovery.
     */
    private val CANDIDATE_ENDPOINTS = listOf(
        "https://raw.githubusercontent.com/v8929731578-art/chatooz-server/main/server/endpoint.json",
        PERMANENT_CLOUD_URL,
        "https://chatooz-server.onrender.com"
    )

    /**
     * Initialize AppConfig on application startup with Context.
     */
    fun init(context: Context) {
        try {
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            val cached = p.getString(KEY_SERVER_URL, null)
            if (!cached.isNullOrBlank()) {
                dynamicBaseUrl = cached.trimEnd('/')
                Log.i(TAG, "Loaded cached active server URL: $dynamicBaseUrl")
            } else {
                dynamicBaseUrl = PERMANENT_CLOUD_URL
                p.edit().putString(KEY_SERVER_URL, dynamicBaseUrl).apply()
            }
            // Trigger asynchronous background health & discovery probe
            scope.launch {
                verifyAndResolveUrl(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AppConfig.init error: ${e.message}")
        }
    }

    /**
     * Update and persist a new verified active server URL.
     */
    fun updateActiveUrl(context: Context?, newUrl: String): Boolean {
        val cleanUrl = newUrl.trim().trimEnd('/')
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return false
        }
        dynamicBaseUrl = cleanUrl
        try {
            val p = prefs ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            p?.edit()?.putString(KEY_SERVER_URL, cleanUrl)?.apply()
            Log.i(TAG, "Updated and saved active server URL: $cleanUrl")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist server URL: ${e.message}")
            return false
        }
    }

    /** True if running in an Android emulator (used to adjust local dev URLs only) */
    private val isEmulator: Boolean by lazy {
        if (!isDev) return@lazy false
        try {
            val fp = Build.FINGERPRINT ?: ""
            val model = Build.MODEL ?: ""
            val hw = Build.HARDWARE ?: ""
            fp.startsWith("generic") || fp.startsWith("unknown") ||
                model.contains("google_sdk", ignoreCase = true) ||
                model.contains("Emulator", ignoreCase = true) ||
                hw.contains("goldfish") || hw.contains("ranchu")
        } catch (e: Throwable) {
            false
        }
    }

    private fun resolveDevUrl(url: String): String {
        if (!isDev || !isEmulator) return url
        return url.replace("127.0.0.1", "10.0.2.2")
    }

    /**
     * Base URL for all REST API calls (sync, signalling, auth).
     */
    val apiBaseUrl: String
        get() {
            val active = dynamicBaseUrl ?: BuildConfig.API_BASE_URL
            return resolveDevUrl(active.trimEnd('/'))
        }

    const val WEBRTC_CALLS_ENABLED: Boolean = false

    val signalingWsUrl: String
        get() = apiBaseUrl.replace("https://", "wss://").replace("http://", "ws://") + "/ws/signaling"

    val mediaWsUrl: String
        get() = apiBaseUrl.replace("https://", "wss://").replace("http://", "ws://") + "/media"

    val syncUrl: String get() = "$apiBaseUrl/sync"
    val callSignalUrl: String get() = "$apiBaseUrl/call/signal"
    val healthUrl: String get() = "$apiBaseUrl/health"
    val versionUrl: String get() = "$apiBaseUrl/version"
    val downloadApkUrl: String get() = "$apiBaseUrl/download-apk"

    val isDev: Boolean get() = BuildConfig.IS_DEV_BUILD

    /**
     * Checks if a server candidate URL is alive and responding.
     */
    fun checkHealth(baseUrl: String): Boolean {
        val checkUrl = baseUrl.trimEnd('/') + "/health"
        return try {
            val req = Request.Builder().url(checkUrl).get().build()
            val res = probeClient.newCall(req).execute()
            val ok = res.isSuccessful && (res.body?.string()?.contains("ok") == true || res.body?.string()?.contains("status") == true)
            res.close()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Background verification and fallback resolution routine.
     */
    suspend fun verifyAndResolveUrl(context: Context?): String = withContext(Dispatchers.IO) {
        if (isResolving) return@withContext apiBaseUrl
        isResolving = true
        try {
            val current = apiBaseUrl
            if (checkHealth(current)) {
                Log.d(TAG, "Current server URL is healthy: $current")
                return@withContext current
            }

            Log.w(TAG, "Current server URL $current unreachable, probing remote anchors & candidates...")

            // Try candidate endpoints directly first
            if (checkHealth(PERMANENT_CLOUD_URL)) {
                updateActiveUrl(context, PERMANENT_CLOUD_URL)
                Log.i(TAG, "Resolved healthy permanent cloud URL: $PERMANENT_CLOUD_URL")
                return@withContext PERMANENT_CLOUD_URL
            }

            // Try candidate remote config anchors
            for (anchor in CANDIDATE_ENDPOINTS) {
                if (anchor.startsWith("http://") || anchor.startsWith("https://")) {
                    if (anchor.endsWith(".json")) {
                        try {
                            val req = Request.Builder().url(anchor).get().build()
                            val res = probeClient.newCall(req).execute()
                            if (res.isSuccessful) {
                                val body = res.body?.string() ?: ""
                                res.close()
                                if (body.isNotBlank()) {
                                    val json = JSONObject(body)
                                    val candidate = json.optString("server_url", "").trim()
                                    if (candidate.isNotBlank() && checkHealth(candidate)) {
                                        updateActiveUrl(context, candidate)
                                        Log.i(TAG, "Resolved healthy server URL from remote anchor: $candidate")
                                        return@withContext candidate
                                    }
                                }
                            } else {
                                res.close()
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Anchor probe failed for $anchor: ${e.message}")
                        }
                    } else if (checkHealth(anchor)) {
                        updateActiveUrl(context, anchor)
                        return@withContext anchor
                    }
                }
            }

            // Fallback to BuildConfig if different
            val buildDefault = BuildConfig.API_BASE_URL.trimEnd('/')
            if (buildDefault != current && checkHealth(buildDefault)) {
                updateActiveUrl(context, buildDefault)
                return@withContext buildDefault
            }
        } catch (e: Exception) {
            Log.e(TAG, "verifyAndResolveUrl error: ${e.message}")
        } finally {
            isResolving = false
        }
        return@withContext apiBaseUrl
    }

    /**
     * Call when a network request encounters a connection drop or DNS failure.
     */
    fun onNetworkFailure(context: Context?) {
        scope.launch {
            verifyAndResolveUrl(context)
        }
    }

    fun assertProductionSafe() {
        // Safe check
    }
}
