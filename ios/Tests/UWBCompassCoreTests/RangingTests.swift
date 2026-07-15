import XCTest
@testable import UWBCompassCore

final class MockRangingProviderTests: XCTestCase {

    private func collect(_ provider: MockRangingProvider) async -> [RangingSample] {
        var out: [RangingSample] = []
        for await s in provider.samples() { out.append(s) }
        return out
    }

    func testApproachingConvergesDistanceTowardZero() async {
        // sampleIntervalMs 0 keeps the test fast (no real sleeping).
        let samples = await collect(MockRangingProvider(script: .approaching(steps: 30), sampleIntervalMs: 0))
        XCTAssertEqual(samples.count, 30)
        XCTAssertLessThan(samples.last!.distanceMeters!, samples.first!.distanceMeters!)
        XCTAssertLessThan(samples.last!.distanceMeters!, 2.0)
    }

    func testUwbHasDirectionBleDoesNot() async {
        let uwb = await collect(MockRangingProvider(technology: .UWB, script: .circling(steps: 10), sampleIntervalMs: 0))
        XCTAssertTrue(uwb.allSatisfy { $0.hasDirection })
        let ble = await collect(MockRangingProvider(technology: .BLE, script: .circling(steps: 10), sampleIntervalMs: 0))
        XCTAssertTrue(ble.allSatisfy { !$0.hasDirection })
        XCTAssertTrue(ble.allSatisfy { $0.distanceMeters != nil })
    }

    func testDropoutProducesLostSamples() async {
        let samples = await collect(MockRangingProvider(script: .approachingWithDropout(steps: 50), sampleIntervalMs: 100))
        let lost = samples.filter { $0.quality == .lost }
        XCTAssertFalse(lost.isEmpty)
        for s in lost {
            XCTAssertNil(s.distanceMeters)
            XCTAssertNil(s.azimuthDeg)
        }
    }

    func testQualityBuckets() {
        XCTAssertEqual(MockRangingProvider.quality(for: 2, lost: false, tech: .UWB), .high)
        XCTAssertEqual(MockRangingProvider.quality(for: 25, lost: false, tech: .UWB), .medium)
        XCTAssertEqual(MockRangingProvider.quality(for: 50, lost: false, tech: .UWB), .low)
        XCTAssertEqual(MockRangingProvider.quality(for: 5, lost: false, tech: .BLE), .medium)
    }

    func testSameSeedIsDeterministic() async {
        let a = await collect(MockRangingProvider(sampleIntervalMs: 0, seed: 7)).map { $0.distanceMeters }
        let b = await collect(MockRangingProvider(sampleIntervalMs: 0, seed: 7)).map { $0.distanceMeters }
        XCTAssertEqual(a, b)
    }
}

final class BleDistanceModelTests: XCTestCase {
    func testRssiAtTxPowerGivesOneMeter() {
        XCTAssertEqual(BleDistanceModel.rssiToDistanceMeters(rssi: -59, txPowerDbm: -59), 1, accuracy: 1e-6)
    }
    func testWeakerRssiMeansFarther() {
        let near = BleDistanceModel.rssiToDistanceMeters(rssi: -59)
        let far = BleDistanceModel.rssiToDistanceMeters(rssi: -85)
        XCTAssertLessThan(near, far)
    }
}
