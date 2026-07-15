package com.uwbcompass.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 5 scenario test: drives the MockRangingProvider with a UWB dropout window through
 * the CompassFusion + DegradationPolicy exactly as the ViewModel would, and asserts that
 * the client recommends a UWB->BLE downgrade and that the arrow disappears during the loss.
 */
class DegradationScenarioTest {

    @Test
    fun `uwb dropout triggers a BLE downgrade recommendation and drops the arrow`() = runTest {
        val provider = MockRangingProvider(
            technology = Technology.UWB,
            script = MockScript.approachingWithDropout(50),
            sampleIntervalMs = 100,
        )
        val fusion = CompassFusion(smoothing = 1.0)
        val policy = DegradationPolicy(
            maxConsecutiveLost = 5,
            available = setOf(Technology.UWB, Technology.BLE, Technology.GPS),
        )
        var heading = 0.0
        var recommended: Technology? = null
        var arrowWentNullDuringLoss = false

        provider.samples().toList().forEach { sample ->
            sample.azimuthDeg?.let { fusion.onDirectionalSample(it, heading) }
            val rec = policy.observe(sample)
            if (rec != null && recommended == null) recommended = rec
            if (sample.quality == SignalQuality.LOST) {
                // During a loss there is no fresh direction; the UI shows proximity, not an arrow.
                arrowWentNullDuringLoss = arrowWentNullDuringLoss || !sample.hasDirection
            }
            heading = (heading + 1) % 360
        }

        assertNotNull(recommended, "expected a downgrade recommendation during the dropout")
        assertEquals(Technology.BLE, recommended)
        assertTrue(arrowWentNullDuringLoss, "arrow/direction must be absent while UWB is lost")
    }

    @Test
    fun `ble stream keeps distance but never offers an arrow`() = runTest {
        val samples = MockRangingProvider(Technology.BLE, MockScript.approaching(20), sampleIntervalMs = 0)
            .samples().toList()
        assertTrue(samples.all { it.distanceMeters != null })
        assertTrue(samples.none { it.hasDirection })
    }
}
