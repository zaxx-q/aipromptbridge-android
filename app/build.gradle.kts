// :app — Android UI (Jetpack Compose) + OS integration (PROCESS_TEXT, Quick Settings tile, screen capture).
// AGP 9 built-in Kotlin: no kotlin-android plugin. The Compose compiler plugin is still required.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aipromptbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aipromptbridge"
        minSdk = 30   // Android 11: simplifies WindowMetrics + MediaProjection code paths
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
