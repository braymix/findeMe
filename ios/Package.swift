// swift-tools-version:5.9
import PackageDescription

// Pure, platform-independent domain logic for the iOS app — the exact mirror of
// android/core. It has NO dependency on NearbyInteraction/CoreBluetooth/ARKit, so it
// builds and unit-tests with `swift test` on macOS (and mostly on Linux) without UWB
// hardware. The SwiftUI app (ios/App, Xcode project) depends on this package and adds the
// Apple-framework providers. See docs/ios-build.md.
let package = Package(
    name: "UWBCompassCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "UWBCompassCore", targets: ["UWBCompassCore"]),
    ],
    targets: [
        .target(name: "UWBCompassCore"),
        .testTarget(name: "UWBCompassCoreTests", dependencies: ["UWBCompassCore"]),
    ]
)
