import Foundation

/// Client-side runtime degradation policy (Phase 5), mirror of the Android one. Recommends
/// a downgrade DOWN the ladder UWB -> BLE -> GPS after enough consecutive LOST samples.
public final class DegradationPolicy {
    private let maxConsecutiveLost: Int
    private let available: Set<Technology>
    private var consecutiveLost = 0

    public init(maxConsecutiveLost: Int = 5,
                available: Set<Technology> = [.UWB, .BLE, .GPS]) {
        self.maxConsecutiveLost = maxConsecutiveLost
        self.available = available
    }

    /// - Returns: the technology to downgrade to, or nil if the current one is fine.
    public func observe(_ sample: RangingSample) -> Technology? {
        if sample.quality == .lost {
            consecutiveLost += 1
        } else {
            consecutiveLost = 0
            return nil
        }
        guard consecutiveLost >= maxConsecutiveLost else { return nil }
        return nextBelow(sample.technology)
    }

    public func reset() { consecutiveLost = 0 }

    private func nextBelow(_ current: Technology) -> Technology? {
        let ladder: [Technology] = [.UWB, .BLE, .GPS]
        guard let idx = ladder.firstIndex(of: current) else { return nil }
        for i in (idx + 1)..<ladder.count where available.contains(ladder[i]) {
            return ladder[i]
        }
        return nil
    }
}
