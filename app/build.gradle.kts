import java.io.FileInputStream
import java.util.Properties

// :app — Android UI (Jetpack Compose) + OS integration (PROCESS_TEXT, Quick Settings tile, screen capture).
// AGP 9 built-in Kotlin: no kotlin-android plugin. The Compose compiler plugin is still required.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aipromptbridge"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aipromptbridge"
        minSdk = 30   // Android 11: simplifies WindowMetrics + MediaProjection code paths
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                FileInputStream(keystorePropertiesFile).use { stream ->
                    keystoreProperties.load(stream)
                }
            }

            fun propertyOrEnv(key: String, vararg envKeys: String): String? {
                val prop = keystoreProperties.getProperty(key)
                    ?: (project.findProperty(key) as? String)
                if (!prop.isNullOrBlank()) return prop
                for (envKey in envKeys) {
                    val env = System.getenv(envKey)
                    if (!env.isNullOrBlank()) return env
                }
                return null
            }

            val storeFilePath = propertyOrEnv("storeFile", "KEYSTORE_FILE", "KEYSTORE_PATH", "RELEASE_STORE_FILE")
            val storePass = propertyOrEnv("storePassword", "KEYSTORE_PASSWORD", "RELEASE_STORE_PASSWORD")
            val kAlias = propertyOrEnv("keyAlias", "KEY_ALIAS", "RELEASE_KEY_ALIAS")
            val kPass = propertyOrEnv("keyPassword", "KEY_PASSWORD", "RELEASE_KEY_PASSWORD")

            if (storeFilePath != null && file(storeFilePath).exists() && storePass != null && kAlias != null) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = kAlias
                keyPassword = kPass ?: storePass
            } else {
                // Fallback to debug keystore so release builds are always validly signed and installable
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // Isolated to designsystem/AipbIcons.kt. If this artifact is unavailable/deprecated, replace the icons
    // there with Material Symbols vector assets (Android Studio: Vector Asset Studio).
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
