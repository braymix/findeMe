import XCTest
@testable import UWBCompassCore

final class CompassFusionTests: XCTestCase {

    func testArrowPointsAtPeerRelativeToForwardWhenHeadingNorth() {
        let f = CompassFusion(smoothing: 1.0)
        f.onDirectionalSample(relativeAzimuthDeg: 30, deviceHeadingDeg: 0)
        XCTAssertEqual(f.arrowRelativeDeg(deviceHeadingDeg: 0)!, 30, accuracy: 1e-6)
    }

    func testRotatingPhoneRotatesArrowOppositely() {
        let f = CompassFusion(smoothing: 1.0)
        f.onDirectionalSample(relativeAzimuthDeg: 0, deviceHeadingDeg: 0) // world bearing 0
        XCTAssertEqual(f.arrowRelativeDeg(deviceHeadingDeg: 0)!, 0, accuracy: 1e-6)
        XCTAssertEqual(f.arrowRelativeDeg(deviceHeadingDeg: 90)!, -90, accuracy: 1e-6)
    }

    func testWorldBearingCombinesHeadingAndAzimuth() {
        let f = CompassFusion(smoothing: 1.0)
        f.onDirectionalSample(relativeAzimuthDeg: 45, deviceHeadingDeg: 90)
        XCTAssertEqual(f.worldBearingToPeer!, 135, accuracy: 1e-6)
    }

    func testWrapAt180Boundary() {
        let f = CompassFusion(smoothing: 1.0)
        f.onDirectionalSample(relativeAzimuthDeg: 170, deviceHeadingDeg: 170)
        XCTAssertEqual(f.worldBearingToPeer!, -20, accuracy: 1e-6)
    }

    func testSmoothingDampsJumpButConverges() {
        let f = CompassFusion(smoothing: 0.5)
        f.onDirectionalSample(relativeAzimuthDeg: 0, deviceHeadingDeg: 0)
        f.onDirectionalSample(relativeAzimuthDeg: 80, deviceHeadingDeg: 0)
        XCTAssertEqual(f.worldBearingToPeer!, 40, accuracy: 1e-6)
        for _ in 0..<10 { f.onDirectionalSample(relativeAzimuthDeg: 80, deviceHeadingDeg: 0) }
        XCTAssertLessThan(abs(f.worldBearingToPeer! - 80), 0.5)
    }

    func testNoDirectionalFixYieldsNilArrow() {
        XCTAssertNil(CompassFusion().arrowRelativeDeg(deviceHeadingDeg: 0))
    }

    func testDistanceEmaSmoothsTowardInput() {
        let f = CompassFusion(smoothing: 0.5)
        f.onDistance(10)
        f.onDistance(0)
        XCTAssertEqual(f.smoothedDistanceMeters!, 5, accuracy: 1e-6)
    }
}
