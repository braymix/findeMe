package com.uwbcompass.core

import kotlin.math.pow

/**
 * Log-distance path-loss RSSI → distance model, shared by the BLE provider and its tests.
 * Pure so it is unit-tested without hardware.
 *
 *   distance = 10 ^ ((txPowerAt1m - rssi) / (10 * pathLossExponent))
 *
 * [txPowerDbm] is the measured RSSI at 1 m (VERIFY-ON-DEVICE per phone); [pathLossExponent]
 * is ~2.0 in free space, higher indoors.
 */
object BleDistanceModel {
    fun rssiToDistanceMeters(rssi: Int, txPowerDbm: Int = -59, pathLossExponent: Double = 2.0): Double =
        10.0.pow((txPowerDbm - rssi) / (10.0 * pathLossExponent))

    fun qualityFrom(distanceMeters: Double): SignalQuality = when {
        distanceMeters <= 3.0 -> SignalQuality.HIGH
        distanceMeters <= 10.0 -> SignalQuality.MEDIUM
        else -> SignalQuality.LOW
    }
}
