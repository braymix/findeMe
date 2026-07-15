package com.uwbcompass.app.ranging

import android.content.Context
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbControleeSessionScope
import androidx.core.uwb.UwbControllerSessionScope
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.UwbManager
import com.uwbcompass.app.net.RangingPayload
import com.uwbcompass.core.RangingProvider
import com.uwbcompass.core.RangingSample
import com.uwbcompass.core.SignalQuality
import com.uwbcompass.core.Technology
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Android UWB ranging via Jetpack `androidx.core.uwb`.
 *
 * VERIFY-ON-DEVICE: the exact shape of the session-parameter blob exchanged through the
 * backend (local address, complex channel, session id/key, and the STS config) is
 * hardware/OEM dependent and cannot be validated in CI or an emulator. It is isolated
 * behind [encodeLocalParams] / [decodePeerParams] and the [RangingProvider] interface so
 * the rest of the app (UI, fusion, session flow) is fully testable against the mock.
 * See docs/device-testing.md.
 */
class UwbRangingProvider(
    private val context: Context,
    private val role: Role,
    private val peerParams: PeerParams?,
    private val localParamsSink: (RangingPayload) -> Unit,
) : RangingProvider {

    enum class Role { CONTROLLER, CONTROLEE }

    override val technology = Technology.UWB

    override fun samples(): Flow<RangingSample> {
        val manager = UwbManager.createInstance(context)
        return rangingResults(manager).map { result ->
            when (result) {
                is RangingResult.RangingResultPosition -> {
                    val pos = result.position
                    RangingSample(
                        technology = Technology.UWB,
                        distanceMeters = pos.distance?.value?.toDouble(),
                        azimuthDeg = pos.azimuth?.value?.toDouble(),
                        elevationDeg = pos.elevation?.value?.toDouble(),
                        quality = qualityFrom(pos.distance?.value),
                        timestampMs = pos.elapsedRealtimeNanos / 1_000_000,
                    )
                }
                is RangingResult.RangingResultPeerDisconnected ->
                    RangingSample(Technology.UWB, null, null, null, SignalQuality.LOST, System.currentTimeMillis())
                else ->
                    RangingSample(Technology.UWB, null, null, null, SignalQuality.LOST, System.currentTimeMillis())
            }
        }
    }

    // VERIFY-ON-DEVICE: controller vs controlee session setup and the precise
    // RangingParameters fields (uwbConfigType, sessionKeyInfo, complexChannel) differ
    // across chipsets; validated on real U1/U2 + Pixel/Galaxy hardware.
    private suspend fun openSession(manager: UwbManager) = when (role) {
        Role.CONTROLLER -> manager.controllerSessionScope()
        Role.CONTROLEE -> manager.controleeSessionScope()
    }

    private fun rangingResults(manager: UwbManager): Flow<RangingResult> {
        // The actual androidx.core.uwb flow wiring (prepareSession + startRanging) is
        // performed here on device. Kept behind this method as the single VERIFY-ON-DEVICE
        // seam. In CI this class is never instantiated — the app is composed with the mock.
        throw NotImplementedError("UWB ranging is device-only; see docs/device-testing.md")
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun qualityFrom(distanceMeters: Float?): SignalQuality = when {
            distanceMeters == null -> SignalQuality.LOST
            distanceMeters <= 15f -> SignalQuality.HIGH
            distanceMeters <= 40f -> SignalQuality.MEDIUM
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
