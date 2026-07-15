import Foundation
import SwiftUI
import UWBCompassCore

/// Drives the compass view from a `RangingProvider` + heading stream, fusing them with the
/// pure `CompassFusion` from the core package. Because it depends only on the protocol, the
/// whole screen is testable by injecting a `MockRangingProvider` (ADR-0004).
@MainActor
final class CompassViewModel: ObservableObject {
    @Published var technology: Technology = .UWB
    @Published var distanceMeters: Double?
    @Published var arrowDeg: Double?
    @Published var quality: SignalQuality = .medium
    @Published var hasDirection = false

    private let fusion = CompassFusion(smoothing: 0.25)
    private var degradation = DegradationPolicy()
    private var latestHeading: Double = 0
    private var rangingTask: Task<Void, Never>?

    var onDowngradeRecommended: ((Technology) -> Void)?

    func start(provider: RangingProvider, available: Set<Technology>) {
        stop()
        fusion.reset()
        degradation = DegradationPolicy(available: available)
        technology = provider.technology
        rangingTask = Task { [weak self] in
            for await sample in provider.samples() {
                guard let self else { return }
                await self.onSample(sample)
            }
        }
    }

    func onHeading(_ headingDeg: Double) {
        latestHeading = headingDeg
        arrowDeg = fusion.arrowRelativeDeg(deviceHeadingDeg: headingDeg)
    }

    private func onSample(_ sample: RangingSample) {
        if let d = sample.distanceMeters { fusion.onDistance(d) }
        if let az = sample.azimuthDeg { fusion.onDirectionalSample(relativeAzimuthDeg: az, deviceHeadingDeg: latestHeading) }
        if let target = degradation.observe(sample) { onDowngradeRecommended?(target) }

        technology = sample.technology
        distanceMeters = fusion.smoothedDistanceMeters
        arrowDeg = fusion.arrowRelativeDeg(deviceHeadingDeg: latestHeading)
        quality = sample.quality
        hasDirection = sample.hasDirection
    }

    func stop() {
        rangingTask?.cancel()
        rangingTask = nil
    }
}
