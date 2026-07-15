pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "uwb-peer-compass-android"

// The pure-Kotlin domain lives in ./core as its own build (so it unit-tests without the
// Android SDK). It is consumed here via a composite build; the app depends on it as
// implementation("com.uwbcompass:uwb-peer-compass-core").
includeBuild("core")

include(":app")
