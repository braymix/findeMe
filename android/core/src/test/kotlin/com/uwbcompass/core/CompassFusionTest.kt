package com.uwbcompass.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompassFusionTest {

    @Test
    fun `arrow points at peer relative to forward when heading is north`() {
        val f = CompassFusion(smoothing = 1.0) // no smoothing -> exact
        f.onDirectionalSample(relativeAzimuthDeg = 30.0, deviceHeadingDeg = 0.0)
        assertEquals(30.0, f.arrowRelativeDeg(0.0)!!, 1e-6)
    }

    @Test
    fun `rotating the phone rotates the arrow oppositely, keeping world bearing fixed`() {
        val f = CompassFusion(smoothing = 1.0)
        // Peer is due North (world bearing 0) while phone faces North.
        f.onDirectionalSample(relativeAzimuthDeg = 0.0, deviceHeadingDeg = 0.0)
        assertEquals(0.0, f.arrowRelativeDeg(0.0)!!, 1e-6)
        // Now the user turns the phone 90° clockwise (heading 90). Peer hasn't moved, so
        // the arrow must swing to -90 (i.e. point to the phone's left).
        assertEquals(-90.0, f.arrowRelativeDeg(90.0)!!, 1e-6)
    }

    @Test
    fun `world bearing combines heading and relative azimuth`() {
        val f = CompassFusion(smoothing = 1.0)
        f.onDirectionalSample(relativeAzimuthDeg = 45.0, deviceHeadingDeg = 90.0)
        assertEquals(135.0, f.worldBearingToPeer!!, 1e-6)
    }

    @Test
    fun `wrap handles the 180 boundary`() {
        val f = CompassFusion(smoothing = 1.0)
        f.onDirectionalSample(relativeAzimuthDeg = 170.0, deviceHeadingDeg = 170.0)
        // 340 wraps to -20
        assertEquals(-20.0, f.worldBearingToPeer!!, 1e-6)
    }

    @Test
    fun `smoothing damps a jump but converges over samples`() {
        val f = CompassFusion(smoothing = 0.5)
        f.onDirectionalSample(0.0, 0.0) // world 0
        f.onDirectionalSample(80.0, 0.0) // measured 80; half-step -> 40
        assertEquals(40.0, f.worldBearingToPeer!!, 1e-6)
        repeat(10) { f.onDirectionalSample(80.0, 0.0) }
        assertTrue(kotlin.math.abs(f.worldBearingToPeer!! - 80.0) < 0.5)
    }

    @Test
    fun `no directional fix yields null arrow`() {
        val f = CompassFusion()
        assertNull(f.arrowRelativeDeg(0.0))
    }

    @Test
    fun `distance EMA smooths toward the input`() {
        val f = CompassFusion(smoothing = 0.5)
        f.onDistance(10.0)
        f.onDistance(0.0)
        assertEquals(5.0, f.smoothedDistanceMeters!!, 1e-6)
    }
}
