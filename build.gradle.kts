// Top-level build file. Plugins are declared here (apply false) and applied per-module.
// NOTE: AGP 9 has built-in Kotlin, so there is intentionally NO org.jetbrains.kotlin.android plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
