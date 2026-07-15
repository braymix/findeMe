package com.uwbcompass.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MockRangingProviderTest {

    @Test
    fun `approaching script converges distance toward zero`() = runTest {
        val samples = MockRangingProvider(script = MockScript.approaching(30)).samples().toList()
        assertEquals(30, samples.size)
        val first = samples.first().distanceMeters!!
        val last = samples.last().distanceMeters!!
        assertTrue(last < first, "peer should get closer: $first -> $last")
        assertTrue(last < 2.0, "should end near the device, was $last")
    }

    @Test
    fun `UWB samples carry direction, BLE samples do not`() = runTest {
        val uwb = MockRangingProvider(Technology.UWB, MockScript.circling(10)).samples().toList()
        assertTrue(uwb.all { it.hasDirection }, "UWB must have azimuth")

        val ble = MockRangingProvider(Technology.BLE, MockScript.circling(10)).samples().toList()
        assertTrue(ble.none { it.hasDirection }, "BLE must NOT fake a direction")
        assertTrue(ble.all { it.distanceMeters != null }, "BLE still has distance")
    }

    @Test
    fun `dropout window produces LOST samples with no distance or direction`() = runTest {
        val samples = MockRangingProvider(
            script = MockScript.approachingWithDropout(50),
            sampleIntervalMs = 100,
        ).samples().toList()
        val lost = samples.filter { it.quality == SignalQuality.LOST }
        assertTrue(lost.isNotEmpty(), "expected a loss window")
        lost.forEach {
            assertNull(it.distanceMeters)
            assertNull(it.azimuthDeg)
        }
    }

    @Test
    fun `quality buckets scale with distance and technology`() {
        assertEquals(SignalQuality.HIGH, MockRangingProvider.qualityFor(2.0, false, Technology.UWB))
        assertEquals(SignalQuality.MEDIUM, MockRangingProvider.qualityFor(25.0, false, Technology.UWB))
        assertEquals(SignalQuality.LOW, MockRangingProvider.qualityFor(50.0, false, Technology.UWB))
        // BLE degrades much sooner.
        assertEquals(SignalQuality.MEDIUM, MockRangingProvider.qualityFor(5.0, false, Technology.BLE))
        assertEquals(SignalQuality.LOST, MockRangingProvider.qualityFor(1.0, true, Technology.UWB))
    }

    @Test
    fun `same seed is deterministic`() = runTest {
        val a = MockRangingProvider(seed = 7).samples().toList().map { it.distanceMeters }
        val b = MockRangingProvider(seed = 7).samples().toList().map { it.distanceMeters }
        assertEquals(a, b)
    }
}
