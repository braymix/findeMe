package com.uwbcompass.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

/**
 * Deterministic simulated ranging provider. Drives all hardware-free tests and Compose
 * previews. It models a peer moving along a scripted path relative to the device and
 * adds configurable Gaussian noise plus optional signal-loss windows.
 */
class MockRangingProvider(
    override val technology: Technology = Technology.UWB,
    private val script: MockScript = MockScript.approaching(),
    private val sampleIntervalMs: Long = 100,
    private val noise: Noise = Noise(),
    private val seed: Long = 42,
) : RangingProvider {

    data class Noise(val distanceStdMeters: Double = 0.05, val azimuthStdDeg: Double = 2.0)

    override fun samples(): Flow<RangingSample> = flow {
        val rng = Random(seed)
        var t = 0L
        for (point in script.points) {
            val lost = script.lossWindows.any { t in it }
            val quality = qualityFor(point.distanceMeters, lost, technology)
            val sample = if (lost) {
                RangingSample(technology, null, null, null, SignalQuality.LOST, t)
            } else {
                val dist = (point.distanceMeters + gaussian(rng) * noise.distanceStdMeters)
                    .coerceAtLeast(0.0)
                // Direction is only available for UWB (ADR-0003).
                val az = if (technology == Technology.UWB) {
                    wrap180(point.azimuthDeg + gaussian(rng) * noise.azimuthStdDeg)
                } else {
                    null
                }
                RangingSample(technology, dist, az, null, quality, t)
            }
            emit(sample)
            t += sampleIntervalMs
            delay(sampleIntervalMs)
        }
    }

    private fun gaussian(rng: Random): Double {
        // Box-Muller.
        val u1 = rng.nextDouble().coerceAtLeast(1e-9)
        val u2 = rng.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    companion object {
        fun qualityFor(distanceMeters: Double, lost: Boolean, tech: Technology): SignalQuality {
            if (lost) return SignalQuality.LOST
            // UWB stays HIGH far longer than BLE; GPS is coarse.
            val (hi, mid) = when (tech) {
                Technology.UWB -> 15.0 to 40.0
                Technology.BLE -> 3.0 to 10.0
                Technology.GPS -> 20.0 to 100.0
            }
            return when {
                distanceMeters <= hi -> SignalQuality.HIGH
                distanceMeters <= mid -> SignalQuality.MEDIUM
                else -> SignalQuality.LOW
            }
        }
    }
}

/** A scripted relative trajectory of the peer. */
data class MockPoint(val distanceMeters: Double, val azimuthDeg: Double)

data class MockScript(
    val points: List<MockPoint>,
    val lossWindows: List<LongRange> = emptyList(),
) {
    companion object {
        /** Peer starts 20 m away at +60° and walks straight toward the device. */
        fun approaching(steps: Int = 50): MockScript {
            val pts = (0 until steps).map { i ->
                val frac = i.toDouble() / (steps - 1)
                MockPoint(distanceMeters = 20.0 * (1 - frac) + 0.5, azimuthDeg = 60.0 * (1 - frac))
            }
            return MockScript(pts)
        }

        /** Approaching, but UWB drops out for a mid window (drives downgrade tests). */
        fun approachingWithDropout(steps: Int = 50): MockScript {
            val base = approaching(steps)
            return base.copy(lossWindows = listOf(1500L..2500L))
        }

        /** Peer circles the device at a fixed radius. */
        fun circling(steps: Int = 60, radius: Double = 5.0): MockScript {
            val pts = (0 until steps).map { i ->
                val ang = wrap180(i.toDouble() / steps * 360.0)
                MockPoint(radius, ang)
            }
            return MockScript(pts)
        }
    }
}

/** Wrap an angle into (-180, 180]. Shared with fusion math. */
fun wrap180(deg: Double): Double {
    var a = deg % 360.0
    if (a > 180.0) a -= 360.0
    if (a <= -180.0) a += 360.0
    return a
}

internal fun angularDistance(a: Double, b: Double): Double = abs(wrap180(a - b))

/** Smallest signed rotation from [from] to [to], in (-180, 180]. */
fun signedAngleDelta(from: Double, to: Double): Double {
    val d = wrap180(to - from)
    return d
}

internal fun bearingFromComponents(x: Double, y: Double): Double =
    Math.toDegrees(atan2(y, x))
