package com.uwbcompass.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwbcompass.core.CompassFusion
import com.uwbcompass.core.DegradationPolicy
import com.uwbcompass.core.RangingProvider
import com.uwbcompass.core.RangingSample
import com.uwbcompass.core.SignalQuality
import com.uwbcompass.core.Technology
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the compass screen from a [RangingProvider] + heading stream, fusing them with
 * the pure :core [CompassFusion]. Emits an immutable [CompassUiState] the Compose screen
 * renders. Because it depends only on the RangingProvider interface, the whole screen is
 * testable by injecting a MockRangingProvider (ADR-0004).
 */
class CompassViewModel : ViewModel() {

    private val fusion = CompassFusion(smoothing = 0.25)
    private var degradation = DegradationPolicy()
    private var latestHeading = 0.0
    private var rangingJob: Job? = null

    private val _state = MutableStateFlow(CompassUiState())
    val state: StateFlow<CompassUiState> = _state.asStateFlow()

    /** Called by the ViewModel host when a technology downgrade should be recommended. */
    var onDowngradeRecommended: ((Technology) -> Unit)? = null

    fun start(provider: RangingProvider, availableTechnologies: Set<Technology>) {
        stop()
        fusion.reset()
        degradation = DegradationPolicy(available = availableTechnologies)
        _state.value = CompassUiState(technology = provider.technology)
        rangingJob = viewModelScope.launch {
            provider.samples().collect { sample -> onSample(sample) }
        }
    }

    fun onHeading(headingDeg: Double) {
        latestHeading = headingDeg
        _state.value = _state.value.copy(arrowDeg = fusion.arrowRelativeDeg(headingDeg))
    }

    private fun onSample(sample: RangingSample) {
        sample.distanceMeters?.let { fusion.onDistance(it) }
        if (sample.azimuthDeg != null) {
            fusion.onDirectionalSample(sample.azimuthDeg!!, latestHeading)
        }
        degradation.observe(sample)?.let { target -> onDowngradeRecommended?.invoke(target) }

        _state.value = _state.value.copy(
            technology = sample.technology,
            distanceMeters = fusion.smoothedDistanceMeters,
            arrowDeg = fusion.arrowRelativeDeg(latestHeading),
            quality = sample.quality,
            hasDirection = sample.hasDirection,
        )
    }

    fun stop() {
        rangingJob?.cancel()
        rangingJob = null
    }

    override fun onCleared() {
        stop()
    }
}

/** Immutable render state for the compass screen. */
data class CompassUiState(
    val technology: Technology = Technology.UWB,
    val distanceMeters: Double? = null,
    /** Screen arrow angle (0 = up). Null → show proximity indicator, not an arrow. */
    val arrowDeg: Double? = null,
    val quality: SignalQuality = SignalQuality.MEDIUM,
    val hasDirection: Boolean = false,
)
