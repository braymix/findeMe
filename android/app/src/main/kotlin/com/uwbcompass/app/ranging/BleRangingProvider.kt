package com.uwbcompass.app.ranging

import com.uwbcompass.core.BleDistanceModel
import com.uwbcompass.core.RangingProvider
import com.uwbcompass.core.RangingSample
import com.uwbcompass.core.Technology
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * BLE proximity provider: estimates DISTANCE ONLY from RSSI (no direction — ADR-0003).
 * The UI must render a proximity indicator (hot/cold), never an arrow, for BLE samples.
 *
 * The raw scan wiring (BluetoothLeScanner + advertising the session id) is device-only
 * and provided as an [RssiSource]; the RSSI→distance model and sample mapping below are
 * pure and unit-testable.
 */
class BleRangingProvider(
    private val rssiSource: RssiSource,
    private val txPowerDbm: Int = -59, // measured RSSI at 1 m; VERIFY-ON-DEVICE per phone.
    private val pathLossExponent: Double = 2.0,
) : RangingProvider {

    fun interface RssiSource {
        /** Cold flow of (rssiDbm, timestampMs). Collecting starts the BLE scan. */
        fun rssi(): Flow<Pair<Int, Long>>
    }

    override val technology = Technology.BLE

    override fun samples(): Flow<RangingSample> = rssiSource.rssi().map { (rssi, ts) ->
        val distance = BleDistanceModel.rssiToDistanceMeters(rssi, txPowerDbm, pathLossExponent)
        RangingSample(
            technology = Technology.BLE,
            distanceMeters = distance,
            azimuthDeg = null,
            elevationDeg = null,
            quality = BleDistanceModel.qualityFrom(distance),
            timestampMs = ts,
        )
    }
}
