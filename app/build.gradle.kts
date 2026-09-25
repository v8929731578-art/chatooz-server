import java.util.Properties

// ── Load local.properties so CHATOOZ_API_URL is available as project property ──
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
    localProps.forEach { key, value ->
        if (!project.hasProperty(key.toString())) {
            project.ext.set(key.toString(), value.toString())
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionProps = Properties()
val versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { versionProps.load(it) }
}
val appVersionCode = versionProps.getProperty("VERSION_CODE", "2").toInt()
val appVersionName = versionProps.getProperty("VERSION_NAME", "1.1")

android {
    namespace = "com.chatooz.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.chatooz.app"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Placeholders — overridden per build type below.
        // Production build MUST NOT point to localhost.
        buildConfigField("String", "API_BASE_URL", "\"https://chatooz-server.railway.app\"")
        buildConfigField("String", "MEDIA_WS_URL", "\"wss://chatooz-server.railway.app/media\"")
        buildConfigField("boolean", "IS_DEV_BUILD", "false")
    }

    buildTypes {
        // ── ONLINE (debug with tunnel URL set) OR LOCAL (no tunnel URL) ────────
        debug {
            // Check if CHATOOZ_API_URL is set (by go_online.sh via local.properties)
            // If set → use that internet URL (cloudflare tunnel or real server)
            // If not set → fall back to local ADB reverse tunnel (127.0.0.1)
            val onlineUrl = project.findProperty("CHATOOZ_API_URL") as String?
                ?: System.getenv("CHATOOZ_API_URL")

            val apiUrl: String
            val wsUrl: String
            val isDevBuild: Boolean
            val cleartextOk: String

            if (onlineUrl != null && onlineUrl.isNotBlank()) {
                // Online mode — cloudflare tunnel / real cloud server
                apiUrl    = onlineUrl.trimEnd('/')
                wsUrl     = apiUrl.replace("https://", "wss://").replace("http://", "ws://") + "/media"
                isDevBuild = false
                cleartextOk = if (apiUrl.startsWith("https://") || apiUrl.startsWith("wss://")) "false" else "true"
            } else {
                // Local dev mode — ADB reverse tunnel or emulator
                // HTTP on port 8080 (sync_server.py), WS on port 8081 (sync_server.py WS relay)
                apiUrl    = "http://127.0.0.1:8080"
                wsUrl     = "ws://127.0.0.1:8080/media"
                isDevBuild = true
                cleartextOk = "true"
            }

            buildConfigField("String",  "API_BASE_URL", "\"$apiUrl\"")
            buildConfigField("String",  "MEDIA_WS_URL", "\"$wsUrl\"")
            buildConfigField("boolean", "IS_DEV_BUILD", "$isDevBuild")
            manifestPlaceholders["usesCleartextTraffic"] = cleartextOk
        }
        release {
            // ── PRODUCTION ──────────────────────────────────────────────────
            // Uses HTTPS/WSS endpoint set via CHATOOZ_API_URL gradle property.
            val prodApiUrl = project.findProperty("CHATOOZ_API_URL") as String?
                ?: System.getenv("CHATOOZ_API_URL")
                ?: "https://chatooz-server.railway.app"
            val prodWsUrl = prodApiUrl.trimEnd('/')
                .replace("https://", "wss://")
                .replace("http://", "ws://") + "/media"

            buildConfigField("String",  "API_BASE_URL", "\"${prodApiUrl.trimEnd('/')}\"")
            buildConfigField("String",  "MEDIA_WS_URL", "\"$prodWsUrl\"")
            buildConfigField("boolean", "IS_DEV_BUILD", "false")
            manifestPlaceholders["usesCleartextTraffic"] = "false"

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(25)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)

    // OkHttp — provides HTTP client + WebSocket for internet-wide media relay
    implementation(libs.okhttp)
    // WebRTC Android Distribution
    implementation(libs.webrtc)
    // ZXing Barcode / QR Code Generator (100% ISO/IEC Standard & Scannable by Google Lens / Cameras)
    implementation(libs.zxing.core)
    // Uncomment for debug network logging:
    // debugImplementation(libs.okhttp.logging.interceptor)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
