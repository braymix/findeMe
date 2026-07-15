package com.uwbcompass.core

/**
 * Fuses the peer's device-relative UWB azimuth with the phone's own heading (from the
 * rotation-vector sensor) to produce a WORLD-anchored bearing to the peer, then projects
 * that back to a screen-relative arrow angle at the current heading.
 *
 * Why world-anchor: UWB samples arrive at ~1-10 Hz, but the phone can rotate much faster.
 * By storing the peer's bearing in world coordinates we can re-render the arrow at the
 * (fast) sensor rate — `arrowRelativeDeg(heading)` — so the arrow stays glued to the
 * peer's real-world position as the user turns the phone.
 *
 * All angles are degrees. Screen arrow convention: 0° = straight up / device forward,
 * clockwise positive.
 *
 * Pure and fully unit-tested — no Android sensor dependency here (ADR-0004).
 */
class CompassFusion(
    /** 0 < smoothing <= 1. Lower = smoother/laggier; higher = snappier/noisier. */
    private val smoothing: Double = 0.25,
) {
    init {
        require(smoothing > 0.0 && smoothing <= 1.0) { "smoothing must be in (0,1]" }
    }

    var worldBearingToPeer: Double? = null
        private set

    private var distanceEma: Double? = null

    /**
     * Feed a directional (UWB) sample. [relativeAzimuthDeg] is the peer azimuth relative
     * to device forward; [deviceHeadingDeg] is the phone heading (0 = North) at the sample
     * instant. Applies a circular low-pass so jitter is damped.
     */
    fun onDirectionalSample(relativeAzimuthDeg: Double, deviceHeadingDeg: Double) {
        val measured = wrap180(deviceHeadingDeg + relativeAzimuthDeg)
        val prev = worldBearingToPeer
        worldBearingToPeer = if (prev == null) {
            measured
        } else {
            wrap180(prev + smoothing * signedAngleDelta(prev, measured))
        }
    }

    /** Feed a distance-only reading (any technology) for EMA smoothing. */
    fun onDistance(meters: Double) {
        val prev = distanceEma
        distanceEma = if (prev == null) meters else prev + smoothing * (meters - prev)
    }

    val smoothedDistanceMeters: Double? get() = distanceEma

    /**
     * Screen arrow angle at the given heading, or null when there is no directional fix
     * (BLE/GPS) — the UI must then fall back to a proximity indicator, never a fake arrow.
     */
    fun arrowRelativeDeg(deviceHeadingDeg: Double): Double? =
        worldBearingToPeer?.let { wrap180(it - deviceHeadingDeg) }

    fun reset() {
        worldBearingToPeer = null
        distanceEma = null
    }
}
