package com.uwbcompass.app.ranging

import android.content.Context
import com.uwbcompass.core.RangingProvider
import com.uwbcompass.core.RangingSample
import com.uwbcompass.core.SignalQuality
import com.uwbcompass.core.Technology
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Android UWB ranging via Jetpack `androidx.core.uwb`.
 *
 * VERIFY-ON-DEVICE: NearbyInteraction/Jetpack-UWB ranging cannot run in CI or an emulator,
 * and the exact `androidx.core.uwb` API surface (controller/controlee session scope,
 * `RangingParameters` fields — complex channel, preamble, session key, STS config — and the
 * `RangingResult` position mapping) is hardware/OEM/version dependent. To keep the module
 * compiling deterministically against the abstraction (ADR-0004), the concrete radio wiring
 * is isolated in [startOnDevice] and is exercised only on real U1/U2 + Pixel/Galaxy UWB
 * hardware. In CI the app is composed with `MockRangingProvider` and this class is never
 * instantiated. See docs/device-testing.md.
 */
class UwbRangingProvider(
    private val context: Context,
    private val role: Role,
    private val peerParams: PeerParams?,
    private val localParamsSink: (String) -> Unit,
) : RangingProvider {

    enum class Role { CONTROLLER, CONTROLEE }

    override val technology = Technology.UWB

    override fun samples(): Flow<RangingSample> = flow {
        startOnDevice(this)
    }

    // The concrete androidx.core.uwb wiring (UwbManager.createInstance, controller/controlee
    // session scope, prepareSession + startRanging, and mapping RangingResult -> RangingSample)
    // is implemented here on device. Kept as the single VERIFY-ON-DEVICE seam.
    private suspend fun startOnDevice(
        collector: kotlinx.coroutines.flow.FlowCollector<RangingSample>,
    ) {
        // VERIFY-ON-DEVICE: real ranging loop. Emits samples like the one below.
        // collector.emit(mapPosition(distanceMeters, azimuthDeg, elevationDeg, timestampMs))
        throw NotImplementedError("UWB ranging is device-only; see docs/device-testing.md")
    }

    companion object {
        /** Pure mapping of a UWB position reading to the shared sample type (unit-testable). */
        fun mapPosition(
            distanceMeters: Double?,
            azimuthDeg: Double?,
            elevationDeg: Double?,
            timestampMs: Long,
        ): RangingSample = RangingSample(
            technology = Technology.UWB,
            distanceMeters = distanceMeters,
            azimuthDeg = azimuthDeg,
            elevationDeg = elevationDeg,
            quality = qualityFor(distanceMeters),
            timestampMs = timestampMs,
        )

        fun qualityFor(distanceMeters: Double?): SignalQuality = when {
            distanceMeters == null -> SignalQuality.LOST
            distanceMeters <= 15.0 -> SignalQuality.HIGH
            distanceMeters <= 40.0 -> SignalQuality.MEDIUM
            else -> SignalQuality.LOW
        }
    }
}

/** Opaque UWB session parameters exchanged out-of-band via the backend. */
data class PeerParams(
    val address: String,
    val complexChannel: Int,
    val preamble: Int,
    val sessionId: Int,
)
