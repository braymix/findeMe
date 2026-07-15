import Foundation

/// A scripted relative trajectory of the peer.
public struct MockPoint: Sendable {
    public let distanceMeters: Double
    public let azimuthDeg: Double
    public init(distanceMeters: Double, azimuthDeg: Double) {
        self.distanceMeters = distanceMeters
        self.azimuthDeg = azimuthDeg
    }
}

public struct MockScript: Sendable {
    public let points: [MockPoint]
    public let lossWindowsMs: [ClosedRange<Int64>]

    public init(points: [MockPoint], lossWindowsMs: [ClosedRange<Int64>] = []) {
        self.points = points
        self.lossWindowsMs = lossWindowsMs
    }

    /// Peer starts 20 m away at +60° and walks straight toward the device.
    public static func approaching(steps: Int = 50) -> MockScript {
        let pts = (0..<steps).map { i -> MockPoint in
            let frac = Double(i) / Double(steps - 1)
            return MockPoint(distanceMeters: 20 * (1 - frac) + 0.5, azimuthDeg: 60 * (1 - frac))
        }
        return MockScript(points: pts)
    }

    /// Approaching, but UWB drops out for a mid window (drives downgrade tests).
    public static func approachingWithDropout(steps: Int = 50) -> MockScript {
        MockScript(points: approaching(steps: steps).points, lossWindowsMs: [1500...2500])
    }

    /// Peer circles the device at a fixed radius.
    public static func circling(steps: Int = 60, radius: Double = 5) -> MockScript {
        let pts = (0..<steps).map { i -> MockPoint in
            MockPoint(distanceMeters: radius, azimuthDeg: wrap180(Double(i) / Double(steps) * 360))
        }
        return MockScript(points: pts)
    }
}

/// Deterministic simulated ranging provider — the exact mirror of the Android mock. Drives
/// all hardware-free tests and SwiftUI previews.
public struct MockRangingProvider: RangingProvider {
    public let technology: Technology
    private let script: MockScript
    private let sampleIntervalMs: Int64
    private let distanceStd: Double
    private let azimuthStd: Double
    private let seed: UInt64

    public init(
        technology: Technology = .UWB,
        script: MockScript = .approaching(),
        sampleIntervalMs: Int64 = 100,
        distanceStd: Double = 0.05,
        azimuthStd: Double = 2.0,
        seed: UInt64 = 42
    ) {
        self.technology = technology
        self.script = script
        self.sampleIntervalMs = sampleIntervalMs
        self.distanceStd = distanceStd
        self.azimuthStd = azimuthStd
        self.seed = seed
    }

    public func samples() -> AsyncStream<RangingSample> {
        AsyncStream { continuation in
            let task = Task {
                var rng = SplitMix64(seed: seed)
                var t: Int64 = 0
                for point in script.points {
                    if Task.isCancelled { break }
                    let lost = script.lossWindowsMs.contains { $0.contains(t) }
                    let sample: RangingSample
                    if lost {
                        sample = RangingSample(technology: technology, distanceMeters: nil,
                                               azimuthDeg: nil, elevationDeg: nil,
                                               quality: .lost, timestampMs: t)
                    } else {
                        let dist = max(0, point.distanceMeters + rng.gaussian() * distanceStd)
                        let az = technology == .UWB
                            ? wrap180(point.azimuthDeg + rng.gaussian() * azimuthStd)
                            : nil
                        sample = RangingSample(technology: technology, distanceMeters: dist,
                                               azimuthDeg: az, elevationDeg: nil,
                                               quality: MockRangingProvider.quality(for: point.distanceMeters, lost: false, tech: technology),
                                               timestampMs: t)
                    }
                    continuation.yield(sample)
                    t += sampleIntervalMs
                    try? await Task.sleep(nanoseconds: UInt64(sampleIntervalMs) * 1_000_000)
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    public static func quality(for distanceMeters: Double, lost: Bool, tech: Technology) -> SignalQuality {
        if lost { return .lost }
        let hi: Double, mid: Double
        switch tech {
        case .UWB: (hi, mid) = (15, 40)
        case .BLE: (hi, mid) = (3, 10)
        case .GPS: (hi, mid) = (20, 100)
        }
        if distanceMeters <= hi { return .high }
        if distanceMeters <= mid { return .medium }
        return .low
    }
}

/// Small deterministic PRNG so simulated runs are reproducible across platforms.
struct SplitMix64 {
    private var state: UInt64
    init(seed: UInt64) { state = seed }

    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }

    mutating func nextDouble() -> Double {
        Double(next() >> 11) * (1.0 / 9007199254740992.0)
    }

    /// Box-Muller standard normal.
    mutating func gaussian() -> Double {
        let u1 = max(nextDouble(), 1e-9)
        let u2 = nextDouble()
        return (-2 * Foundation.log(u1)).squareRoot() * Foundation.cos(2 * Double.pi * u2)
    }
}
