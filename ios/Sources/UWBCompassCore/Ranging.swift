import Foundation

/// Ranging technology in priority order (mirrors the backend & Android).
public enum Technology: String, Codable, Sendable {
    case UWB, BLE, GPS
}

/// Coarse quality bucket surfaced in the compass UI.
public enum SignalQuality: Sendable {
    case high, medium, low, lost
}

/// A single ranging observation. `azimuthDeg`/`elevationDeg` are ONLY meaningful for UWB
/// (NearbyInteraction `direction`). For BLE/GPS they are nil and the UI must not render a
/// directional arrow (ADR-0003). `azimuthDeg` is relative to device forward, degrees,
/// clockwise positive, in (-180, 180].
public struct RangingSample: Sendable, Equatable {
    public let technology: Technology
    public let distanceMeters: Double?
    public let azimuthDeg: Double?
    public let elevationDeg: Double?
    public let quality: SignalQuality
    public let timestampMs: Int64

    public init(
        technology: Technology,
        distanceMeters: Double?,
        azimuthDeg: Double?,
        elevationDeg: Double?,
        quality: SignalQuality,
        timestampMs: Int64
    ) {
        self.technology = technology
        self.distanceMeters = distanceMeters
        self.azimuthDeg = azimuthDeg
        self.elevationDeg = elevationDeg
        self.quality = quality
        self.timestampMs = timestampMs
    }

    public var hasDirection: Bool { azimuthDeg != nil }
}

/// Platform-agnostic ranging source. Implementations (in the app target):
///  - `NIRangingProvider`   — NearbyInteraction (VERIFY-ON-DEVICE).
///  - `BleRangingProvider`  — RSSI proximity, no direction.
///  - `MockRangingProvider` — deterministic simulation for tests & previews.
///
/// All UI/session logic is written against this protocol so it is fully testable without
/// UWB hardware (ADR-0004). Samples are delivered via an `AsyncStream`.
public protocol RangingProvider: Sendable {
    var technology: Technology { get }
    func samples() -> AsyncStream<RangingSample>
}

/// Wrap an angle into (-180, 180].
public func wrap180(_ deg: Double) -> Double {
    var a = deg.truncatingRemainder(dividingBy: 360)
    if a > 180 { a -= 360 }
    if a <= -180 { a += 360 }
    return a
}

/// Smallest signed rotation from `from` to `to`, in (-180, 180].
public func signedAngleDelta(from: Double, to: Double) -> Double {
    wrap180(to - from)
}
