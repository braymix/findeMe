package com.uwbcompass.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DegradationPolicyTest {

    private fun sample(q: SignalQuality, tech: Technology = Technology.UWB) =
        RangingSample(tech, if (q == SignalQuality.LOST) null else 5.0, null, null, q, 0)

    @Test
    fun `recommends BLE after enough consecutive UWB losses`() {
        val p = DegradationPolicy(maxConsecutiveLost = 3)
        assertNull(p.observe(sample(SignalQuality.LOST)))
        assertNull(p.observe(sample(SignalQuality.LOST)))
        assertEquals(Technology.BLE, p.observe(sample(SignalQuality.LOST)))
    }

    @Test
    fun `a good sample resets the loss counter`() {
        val p = DegradationPolicy(maxConsecutiveLost = 3)
        p.observe(sample(SignalQuality.LOST))
        p.observe(sample(SignalQuality.LOST))
        assertNull(p.observe(sample(SignalQuality.HIGH)))
        // counter reset -> two more losses are not yet enough
        assertNull(p.observe(sample(SignalQuality.LOST)))
        assertNull(p.observe(sample(SignalQuality.LOST)))
    }

    @Test
    fun `skips unavailable technology and lands on the next legal one`() {
        val p = DegradationPolicy(maxConsecutiveLost = 1, available = setOf(Technology.UWB, Technology.GPS))
        assertEquals(Technology.GPS, p.observe(sample(SignalQuality.LOST)))
    }

    @Test
    fun `no downgrade below GPS`() {
        val p = DegradationPolicy(maxConsecutiveLost = 1)
        assertNull(p.observe(sample(SignalQuality.LOST, Technology.GPS)))
    }
}
