# iOS — build & verification

iOS **cannot be built in this Linux environment** — there is no Swift toolchain or Xcode
(the equivalent of Android needing an SDK). All source, tests, and manual steps are below.

## Structure

| Path                    | Type                     | Builds where | Contents |
|-------------------------|--------------------------|--------------|----------|
| `ios/Package.swift` + `ios/Sources/UWBCompassCore` | SwiftPM library | macOS, and mostly Linux (pure Foundation) | `RangingProvider`, `MockRangingProvider`, `CompassFusion`, `DegradationPolicy`, `SessionStateMachine`, `BleDistanceModel`. |
| `ios/Tests/UWBCompassCoreTests` | XCTest | macOS / Linux w/ Swift | Unit tests mirroring the Android core suite. |
| `ios/App` | SwiftUI app sources | **Xcode on macOS only** | `NIRangingProvider` (NearbyInteraction), `BleRangingProvider` (CoreBluetooth), `HeadingSource` (CoreLocation/CoreMotion), `RendezvousClient` (URLSessionWebSocketTask), `CompassViewModel`, SwiftUI views, `Info.plist`. |

The pure logic in `UWBCompassCore` is the exact mirror of `android/core`, so behaviour is
identical across platforms and testable without UWB hardware (ADR-0004).

## Verify the domain logic (needs a Swift toolchain)

```bash
cd ios
swift test        # runs the XCTest suite in Tests/UWBCompassCoreTests
```

This works on macOS (Xcode) and on Linux with the open-source Swift toolchain installed —
`UWBCompassCore` imports only `Foundation`, no Apple UI frameworks. The GitHub Actions
`ios` job runs `swift build` on `macos-14`.

## Build & run the full app (macOS + Xcode 15 required)

There is no committed `.xcodeproj` (it is machine-generated and noisy in git). Create it once:

1. In Xcode: **File ▸ New ▸ Project ▸ iOS App**, product name `UWBPeerCompass`,
   interface **SwiftUI**, minimum deployment **iOS 16**.
2. Delete the generated `ContentView.swift`/`App.swift` and **add the files under `ios/App`**
   to the target (drag the folder in, "Create groups").
3. **Add the local package:** File ▸ Add Package Dependencies ▸ Add Local ▸ select `ios/`
   (the `UWBCompassCore` package), and add it to the app target.
4. Set the target's **Info.plist** to `ios/App/Info.plist` (or copy its keys), and enable the
   **Nearby Interaction** capability. Bluetooth + Location usage strings are already in it.
5. Build & run on **two physical U1/U2 devices** (UWB does not work in the simulator).

Point the app at the backend by editing the base URLs in `RendezvousClient` usage
(`http://<host>:3000`, `ws://<host>:3000/ws`).

## VERIFY-ON-DEVICE markers

Search `ios/App` for `VERIFY-ON-DEVICE`:
- `NIRangingProvider`: `NISession` lifecycle, discovery-token exchange, the local↔peer
  token handoff, and the optional ARKit Extended Directional Measurement fusion.
- The device-local axis convention for converting `NINearbyObject.direction` → azimuth.
- `BleRangingProvider.txPowerDbm`: per-device RSSI-at-1m calibration.

All are captured in `docs/device-testing.md`.
