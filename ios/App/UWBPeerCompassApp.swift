import SwiftUI
import UWBCompassCore

/// App entry point. The full navigation graph (Login → permission priming → peer list →
/// compass) and the wiring of RendezvousClient + HeadingSource + provider selection into
/// CompassViewModel is assembled on device. Capability detection (NISession.isSupported)
/// is done at runtime so non-UWB devices fall straight into the BLE/GPS fallback.
///
/// Group mode is stubbed behind `FeatureFlags.groupMode` (requirement 6 — scaffolding only).
@main
struct UWBPeerCompassApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

enum FeatureFlags {
    static let groupMode = false
}

struct RootView: View {
    // Thin placeholder root so the individual screens remain the unit of review. Wiring is
    // done on device — see docs/ios-build.md.
    var body: some View {
        LoginView(error: nil, onLogin: { _, _ in }, onRegister: { _, _, _ in })
    }
}
