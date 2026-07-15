package com.uwbcompass.core

import kotlinx.coroutines.flow.Flow

/** Ranging technology in priority order (mirrors the backend & iOS). */
enum class Technology { UWB, BLE, GPS }

/** Coarse quality bucket surfaced in the compass UI. */
enum class SignalQuality { HIGH, MEDIUM, LOW, LOST }

/**
 * A single ranging observation.
 *
 * - [distanceMeters] is present for all technologies (UWB precise, BLE estimated from
 *   RSSI, GPS from two fixes).
 * - [azimuthDeg] / [elevationDeg] are ONLY meaningful for UWB (Angle-of-Arrival). For
 *   BLE/GPS they are null, and the UI must NOT render a directional arrow (ADR-0003 —
 *   "never a fake arrow"). azimuth is relative to the device's forward axis, degrees,
 *   clockwise positive, range (-180, 180].
 */
data class RangingSample(
    val technology: Technology,
    val distanceMeters: Double?,
    val azimuthDeg: Double?,
    val elevationDeg: Double?,
    val quality: SignalQuality,
    val timestampMs: Long,
) {
    val hasDirection: Boolean get() = azimuthDeg != null
}

/**
 * Platform-agnostic ranging source. Implementations (see the :app module):
 *  - UwbRangingProvider  — androidx.core.uwb (VERIFY-ON-DEVICE).
 *  - BleRangingProvider  — RSSI proximity, no direction.
 *  - MockRangingProvider — deterministic simulation for tests & previews.
 *
 * All UI/session logic is written against THIS interface so it is fully testable
 * without UWB hardware (ADR-0004).
 */
interface RangingProvider {
    val technology: Technology

    /** Cold flow of samples. Collecting starts ranging; cancelling stops it. */
    fun samples(): Flow<RangingSample>
}
