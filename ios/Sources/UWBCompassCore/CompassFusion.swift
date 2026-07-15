import Foundation

/// Fuses the peer's device-relative UWB azimuth with the phone's own heading (CoreMotion)
/// to produce a WORLD-anchored bearing to the peer, then projects it back to a
/// screen-relative arrow angle at the current heading. Exact mirror of the Android
/// `CompassFusion`. Pure & unit-tested (ADR-0004).
///
/// Screen arrow convention: 0° = straight up / device forward, clockwise positive.
public final class CompassFusion {
    /// 0 < smoothing <= 1. Lower = smoother/laggier; higher = snappier/noisier.
    private let smoothing: Double
    public private(set) var worldBearingToPeer: Double?
    private var distanceEma: Double?

    public init(smoothing: Double = 0.25) {
        precondition(smoothing > 0 && smoothing <= 1, "smoothing must be in (0,1]")
        self.smoothing = smoothing
    }

    /// Feed a directional (UWB) sample. `relativeAzimuthDeg` is the peer azimuth relative
    /// to device forward; `deviceHeadingDeg` is the phone heading (0 = North).
    public func onDirectionalSample(relativeAzimuthDeg: Double, deviceHeadingDeg: Double) {
        let measured = wrap180(deviceHeadingDeg + relativeAzimuthDeg)
        if let prev = worldBearingToPeer {
            worldBearingToPeer = wrap180(prev + smoothing * signedAngleDelta(from: prev, to: measured))
        } else {
            worldBearingToPeer = measured
        }
    }

    /// Feed a distance-only reading (any technology) for EMA smoothing.
    public func onDistance(_ meters: Double) {
        if let prev = distanceEma {
            distanceEma = prev + smoothing * (meters - prev)
        } else {
            distanceEma = meters
        }
    }

    public var smoothedDistanceMeters: Double? { distanceEma }

    /// Screen arrow angle at the given heading, or nil when there is no directional fix
    /// (BLE/GPS) — the UI must then show a proximity indicator, never a fake arrow.
    public func arrowRelativeDeg(deviceHeadingDeg: Double) -> Double? {
        guard let world = worldBearingToPeer else { return nil }
        return wrap180(world - deviceHeadingDeg)
    }

    public func reset() {
        worldBearingToPeer = nil
        distanceEma = nil
    }
}
