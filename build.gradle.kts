plugins {
    // AGP 9's built-in Kotlin support compiles Kotlin directly, so
    // org.jetbrains.kotlin.android is not needed. The Compose compiler is a
    // separate Kotlin compiler plugin and is still required when compose is on.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
