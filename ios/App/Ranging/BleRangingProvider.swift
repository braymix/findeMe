import Foundation
import UWBCompassCore
#if canImport(CoreBluetooth)
import CoreBluetooth
#endif

/// BLE proximity provider: DISTANCE ONLY from RSSI (no direction — ADR-0003). Uses the
/// shared `BleDistanceModel` (unit-tested in the core package). CoreBluetooth scanning is
/// device-only; the RSSI→distance mapping is shared and verified.
final class BleRangingProvider: NSObject, RangingProvider {
    let technology: Technology = .BLE

    /// Measured RSSI at 1 m for the paired hardware (VERIFY-ON-DEVICE calibration).
    private let txPowerDbm: Int
    private let serviceUUIDString: String
    private var continuation: AsyncStream<RangingSample>.Continuation?

    init(serviceUUIDString: String, txPowerDbm: Int = -59) {
        self.serviceUUIDString = serviceUUIDString
        self.txPowerDbm = txPowerDbm
    }

    func samples() -> AsyncStream<RangingSample> {
        AsyncStream { continuation in
            self.continuation = continuation
            #if canImport(CoreBluetooth)
            // VERIFY-ON-DEVICE: CBCentralManager scan for serviceUUIDString; each
            // didDiscover RSSI -> emit(rssi:). Advertise our own session UUID via CBPeripheralManager.
            #else
            continuation.finish()
            #endif
        }
    }

    /// Emit a sample for an observed RSSI. Shared, testable path.
    func emit(rssi: Int, timestampMs: Int64) {
        let distance = BleDistanceModel.rssiToDistanceMeters(rssi: rssi, txPowerDbm: txPowerDbm)
        continuation?.yield(RangingSample(
            technology: .BLE, distanceMeters: distance, azimuthDeg: nil, elevationDeg: nil,
            quality: BleDistanceModel.quality(forDistance: distance), timestampMs: timestampMs
        ))
    }
}
