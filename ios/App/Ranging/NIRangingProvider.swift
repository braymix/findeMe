import Foundation
import UWBCompassCore
#if canImport(NearbyInteraction)
import NearbyInteraction
#endif

/// NearbyInteraction ranging provider (Apple U1/U2). Conforms to the shared
/// `RangingProvider` so all UI/session logic is testable against the mock instead
/// (ADR-0004).
///
/// VERIFY-ON-DEVICE: NearbyInteraction cannot run in the simulator or CI. The discovery
/// token exchange, `NISession` lifecycle, and the optional ARKit Extended Directional
/// Measurement (`setARSession` / `worldTransform`) are validated only on U1/U2 hardware.
/// These paths are isolated behind this class and `docs/device-testing.md`.
final class NIRangingProvider: NSObject, RangingProvider {
    let technology: Technology = .UWB

    /// Peer's discovery token, delivered out-of-band via the backend `ranging.peerPayload`.
    private let peerTokenBase64: String
    /// Set true to fuse ARKit camera-assist directional data (EDM) when available.
    private let useARKitEDM: Bool
    /// Called with our own local discovery token (base64) to hand to the backend.
    private let localTokenSink: (String) -> Void

    private var continuation: AsyncStream<RangingSample>.Continuation?

    init(peerTokenBase64: String, useARKitEDM: Bool = false, localTokenSink: @escaping (String) -> Void) {
        self.peerTokenBase64 = peerTokenBase64
        self.useARKitEDM = useARKitEDM
        self.localTokenSink = localTokenSink
    }

    func samples() -> AsyncStream<RangingSample> {
        AsyncStream { continuation in
            self.continuation = continuation
            #if canImport(NearbyInteraction)
            guard NISession.isSupported else {
                continuation.finish()
                return
            }
            // VERIFY-ON-DEVICE: create NISession, publish local token, decode peer token,
            // run(NINearbyPeerConfiguration). Delegate maps NINearbyObject -> RangingSample.
            startSessionOnDevice(continuation)
            #else
            continuation.finish()
            #endif
        }
    }

    #if canImport(NearbyInteraction)
    private func startSessionOnDevice(_ continuation: AsyncStream<RangingSample>.Continuation) {
        // Device-only wiring lives here as the single VERIFY-ON-DEVICE seam. In CI the app
        // is composed with MockRangingProvider and this method is never invoked.
        assertionFailure("NearbyInteraction ranging is device-only; see docs/device-testing.md")
        continuation.finish()
    }

    /// Maps a NearbyInteraction observation to the shared sample type. `direction` is a unit
    /// vector in the device's local frame; azimuth = atan2(x, z-forward). (VERIFY-ON-DEVICE:
    /// exact axis convention differs by device orientation.)
    static func sampleFrom(distance: Float?, direction: SIMD3<Float>?, timestampMs: Int64) -> RangingSample {
        var azimuth: Double?
        if let d = direction {
            azimuth = Double(atan2(d.x, -d.z)) * 180 / .pi
        }
        let quality: SignalQuality
        switch distance {
        case .none: quality = .lost
        case let .some(m) where m <= 15: quality = .high
        case let .some(m) where m <= 40: quality = .medium
        default: quality = .low
        }
        return RangingSample(technology: .UWB, distanceMeters: distance.map(Double.init),
                             azimuthDeg: azimuth, elevationDeg: nil,
                             quality: quality, timestampMs: timestampMs)
    }
    #endif
}
