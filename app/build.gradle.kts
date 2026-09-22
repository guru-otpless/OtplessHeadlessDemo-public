import java.util.Properties

plugins {
    // AGP 9's built-in Kotlin support compiles Kotlin directly, so
    // org.jetbrains.kotlin.android is not needed here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// OTPless APP_ID is kept out of source control. Put it in local.properties
// (which is gitignored) as `OTPLESS_APP_ID=your_app_id`, or export it as the
// ORG_GRADLE_PROJECT_OTPLESS_APP_ID / OTPLESS_APP_ID env var for CI.
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}
val otplessAppId: String =
    (localProperties.getProperty("OTPLESS_APP_ID")
        ?: System.getenv("OTPLESS_APP_ID")
        ?: "YOUR_OTPLESS_APP_ID")

android {
    namespace = "com.otpless.headlessdemo"
    // The only platform installed on this machine is a minor-versioned SDK
    // ("android-37.0"), so the plain `compileSdk = 37` shortcut can't resolve it
    // and the full target hash is needed instead (deprecated in favor of a
    // `compileSdk { ... }` block in AGP 10, but still functional here).
    compileSdkVersion("android-37.0")

    defaultConfig {
        applicationId = "com.otpless.headlessdemo"
        // The OTPless SDK docs list minSdk 21, but current AndroidX Compose/Activity
        // releases (e.g. androidx.navigationevent, pulled in transitively by
        // activity-compose) require 23+, so that's the effective floor here.
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "OTPLESS_APP_ID", "\"$otplessAppId\"")
    }

    buildTypes {
        release {
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // OTPless Android Headless SDK (new SDK, not the deprecated WebView SDK)
    // https://otpless.com/docs/frontend-sdks/app-sdks/android/new/headless/intro
    implementation("io.github.otpless-tech:otpless-headless-sdk:0.9.0")
}
