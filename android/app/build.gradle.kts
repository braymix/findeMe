plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.uwbcompass.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.uwbcompass.app"
        minSdk = 31 // API 31; UWB is feature-gated at runtime (ADR-0006).
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // Backend base URL; override per build type / flavor as needed.
        buildConfigField("String", "BACKEND_HTTP", "\"http://10.0.2.2:3000\"")
        buildConfigField("String", "BACKEND_WS", "\"ws://10.0.2.2:3000/ws\"")
        // Group mode is stubbed behind this flag (requirement 6 — scaffolding only).
        buildConfigField("boolean", "FEATURE_GROUP_MODE", "false")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation("com.uwbcompass:uwb-peer-compass-core")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    // UWB (Jetpack). VERIFY-ON-DEVICE: exact ranging-params API surface.
    implementation("androidx.core.uwb:uwb:1.0.0-alpha08")

    // Networking (WS) + JSON.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.24")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
}
