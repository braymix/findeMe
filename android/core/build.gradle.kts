plugins {
    kotlin("jvm") version "1.9.24"
}

group = "com.uwbcompass"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
}

tasks.test {
    useJUnitPlatform()
}

// Target JVM 17 bytecode (Android-compatible) using whatever JDK runs the build, so the
// standalone core build works in CI on Linux without a pinned toolchain install.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
