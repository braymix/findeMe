package com.uwbcompass.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BleDistanceModelTest {

    @Test
    fun `rssi equal to tx power at 1m gives 1 meter`() {
        assertEquals(1.0, BleDistanceModel.rssiToDistanceMeters(rssi = -59, txPowerDbm = -59), 1e-6)
    }

    @Test
    fun `weaker rssi means larger distance (monotonic)`() {
        val near = BleDistanceModel.rssiToDistanceMeters(-59)
        val mid = BleDistanceModel.rssiToDistanceMeters(-72)
        val far = BleDistanceModel.rssiToDistanceMeters(-85)
        assertTrue(near < mid && mid < far, "distance must grow as rssi weakens: $near < $mid < $far")
    }

    @Test
    fun `quality buckets match distance`() {
        assertEquals(SignalQuality.HIGH, BleDistanceModel.qualityFrom(2.0))
        assertEquals(SignalQuality.MEDIUM, BleDistanceModel.qualityFrom(7.0))
        assertEquals(SignalQuality.LOW, BleDistanceModel.qualityFrom(20.0))
    }
}
