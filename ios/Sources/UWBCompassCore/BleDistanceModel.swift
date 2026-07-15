import Foundation

/// Log-distance path-loss RSSI → distance model, mirror of the Android one. Pure & tested.
///   distance = 10 ^ ((txPowerAt1m - rssi) / (10 * pathLossExponent))
public enum BleDistanceModel {
    public static func rssiToDistanceMeters(rssi: Int, txPowerDbm: Int = -59, pathLossExponent: Double = 2.0) -> Double {
        pow(10.0, Double(txPowerDbm - rssi) / (10.0 * pathLossExponent))
    }

    public static func quality(forDistance meters: Double) -> SignalQuality {
        if meters <= 3 { return .high }
        if meters <= 10 { return .medium }
        return .low
    }
}
