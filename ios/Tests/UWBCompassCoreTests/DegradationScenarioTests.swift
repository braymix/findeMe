import XCTest
@testable import UWBCompassCore

/// Phase 5 scenario test (mirror of the Android one): a UWB dropout drives a BLE downgrade
/// recommendation and the arrow disappears during the loss.
final class DegradationScenarioTests: XCTestCase {

    func testUwbDropoutTriggersBleDowngradeAndDropsArrow() async {
        let provider = MockRangingProvider(technology: .UWB,
                                           script: .approachingWithDropout(steps: 50),
                                           sampleIntervalMs: 100)
        let fusion = CompassFusion(smoothing: 1.0)
        let policy = DegradationPolicy(maxConsecutiveLost: 5, available: [.UWB, .BLE, .GPS])
        var heading = 0.0
        var recommended: Technology?
        var arrowWentNilDuringLoss = false

        for await sample in provider.samples() {
            if let az = sample.azimuthDeg { fusion.onDirectionalSample(relativeAzimuthDeg: az, deviceHeadingDeg: heading) }
            if let rec = policy.observe(sample), recommended == nil { recommended = rec }
            if sample.quality == .lost, !sample.hasDirection { arrowWentNilDuringLoss = true }
            heading = (heading + 1).truncatingRemainder(dividingBy: 360)
        }

        XCTAssertEqual(recommended, .BLE)
        XCTAssertTrue(arrowWentNilDuringLoss)
    }
}
