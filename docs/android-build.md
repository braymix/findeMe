# Android — build & verification

## Modules

| Module        | Type                    | Builds in CI (Linux)? | Contents |
|---------------|-------------------------|-----------------------|----------|
| `android/core`| Pure Kotlin (`jvm`)     | ✅ yes                 | `RangingProvider` abstraction, `MockRangingProvider`, `CompassFusion`, `DegradationPolicy`, `SessionStateMachine`, `BleDistanceModel`. Fully unit-tested (24 tests). |
| `android/app` | Android application     | 🔒 needs Android SDK   | Compose UI, `UwbRangingProvider` (Jetpack UWB), `BleRangingProvider`, `AndroidHeadingSource`, `RendezvousClient` (WS), `CompassViewModel`. |

The pure logic is deliberately isolated in `core` so **all** UI/session/fusion behaviour is
verifiable without UWB hardware or the Android SDK (ADR-0004). `core` is consumed by `app`
via a Gradle **composite build** (`includeBuild("core")`).

## Verifying the domain logic here (no SDK required)

```bash
cd android/core
gradle test          # or ../gradlew :uwb-peer-compass-core:test on a networked machine
```

This compiles the shared logic and runs 24 JUnit tests (fusion math, mock provider
trajectories/noise/dropout, RSSI→distance, degradation policy, client state machine).

## Building the full app (requires Android SDK)

```bash
# Set ANDROID_HOME / sdk.dir first (Android SDK 34, build-tools).
cd android
./gradlew assembleDebug        # build the APK
./gradlew testDebugUnitTest    # app-module unit tests
./gradlew ktlintCheck detekt   # lint
```

> **Why it can't build in this environment:** there is no Android SDK installed on the CI
> Linux box (no `ANDROID_HOME`), exactly as iOS cannot build without macOS. The `app`
> module applies the `com.android.application` plugin which requires the SDK at
> configuration time. The GitHub Actions `android` job runs these commands with the SDK
> available.

> **Gradle wrapper note:** `gradle/wrapper/gradle-wrapper.jar` is committed, but this
> sandbox blocks `services.gradle.org`, so `./gradlew` cannot download the distribution
> here. On a networked machine `./gradlew` works normally, or run `gradle wrapper` once to
> refresh it. Locally we verified `core` with the system Gradle (8.14.3).

## VERIFY-ON-DEVICE markers

Search the app module for `VERIFY-ON-DEVICE`. These are the UWB code paths that cannot be
exercised without hardware:
- `UwbRangingProvider`: controller/controlee session setup and the exact
  `RangingParameters` fields (complex channel, preamble, session key, STS config).
- The opaque session-parameter blob format exchanged via the backend.
- `BleRangingProvider.txPowerDbm`: per-phone RSSI calibration at 1 m.

All are listed in `docs/device-testing.md`.
