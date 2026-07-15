import XCTest
@testable import UWBCompassCore

final class SessionStateMachineTests: XCTestCase {
    func testOutgoingHappyPath() throws {
        let m = SessionStateMachine()
        try m.transition(to: .invited)
        try m.transition(to: .accepted)
        try m.transition(to: .negotiated)
        try m.transition(to: .active)
        try m.transition(to: .ended)
        XCTAssertTrue(m.isTerminal)
    }

    func testIllegalTransitionThrows() {
        let m = SessionStateMachine()
        XCTAssertThrowsError(try m.transition(to: .active))
    }

    func testTerminalResets() throws {
        let m = SessionStateMachine()
        try m.transition(to: .incoming)
        try m.transition(to: .declined)
        XCTAssertTrue(m.isTerminal)
        XCTAssertFalse(m.canTransition(to: .active))
        m.resetIfTerminal()
        if case .idle = m.state {} else { XCTFail("expected idle") }
    }

    func testActiveCanFail() throws {
        let m = SessionStateMachine()
        try m.transition(to: .invited)
        try m.transition(to: .accepted)
        try m.transition(to: .negotiated)
        try m.transition(to: .active)
        try m.transition(to: .failed)
        XCTAssertTrue(m.isTerminal)
    }
}

final class DegradationPolicyTests: XCTestCase {
    private func sample(_ q: SignalQuality, _ tech: Technology = .UWB) -> RangingSample {
        RangingSample(technology: tech, distanceMeters: q == .lost ? nil : 5,
                      azimuthDeg: nil, elevationDeg: nil, quality: q, timestampMs: 0)
    }

    func testRecommendsBleAfterConsecutiveLosses() {
        let p = DegradationPolicy(maxConsecutiveLost: 3)
        XCTAssertNil(p.observe(sample(.lost)))
        XCTAssertNil(p.observe(sample(.lost)))
        XCTAssertEqual(p.observe(sample(.lost)), .BLE)
    }

    func testGoodSampleResetsCounter() {
        let p = DegradationPolicy(maxConsecutiveLost: 3)
        _ = p.observe(sample(.lost))
        _ = p.observe(sample(.lost))
        XCTAssertNil(p.observe(sample(.high)))
        XCTAssertNil(p.observe(sample(.lost)))
    }

    func testSkipsUnavailableTechnology() {
        let p = DegradationPolicy(maxConsecutiveLost: 1, available: [.UWB, .GPS])
        XCTAssertEqual(p.observe(sample(.lost)), .GPS)
    }

    func testNoDowngradeBelowGps() {
        let p = DegradationPolicy(maxConsecutiveLost: 1)
        XCTAssertNil(p.observe(sample(.lost, .GPS)))
    }
}
