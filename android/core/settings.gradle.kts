// Standalone, pure-Kotlin build for the platform-independent Android logic
// (RangingProvider abstraction, mock provider, sensor-fusion math, session state
// machine). It has NO Android SDK dependency, so it builds and unit-tests in CI on
// Linux. The Android :app module consumes it via a Gradle composite build
// (see ../settings.gradle.kts -> includeBuild("core")).
rootProject.name = "uwb-peer-compass-core"
