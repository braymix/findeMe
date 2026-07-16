package com.uwbcompass.app

import android.app.Application
import android.content.pm.PackageManager
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uwbcompass.app.net.ApiClient
import com.uwbcompass.app.net.ApiException
import com.uwbcompass.app.net.Capabilities
import com.uwbcompass.app.net.RangingPayload
import com.uwbcompass.app.net.RendezvousClient
import com.uwbcompass.app.net.ServerEvent
import com.uwbcompass.app.ranging.AndroidHeadingSource
import com.uwbcompass.app.ui.Peer
import com.uwbcompass.core.CompassFusion
import com.uwbcompass.core.MockRangingProvider
import com.uwbcompass.core.MockScript
import com.uwbcompass.core.Technology
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class Screen { LOGIN, PEERS, COMPASS }

data class Incoming(val sessionId: String, val fromUsername: String)

data class AppState(
    val screen: Screen = Screen.LOGIN,
    val loading: Boolean = false,
    val error: String? = null,
    val meUsername: String? = null,
    val peers: List<Peer> = emptyList(),
    val incoming: Incoming? = null,
    val peerName: String = "",
    val compass: CompassUiState = CompassUiState(),
)

/**
 * Application controller: real login/registration, contact list, presence, and the ranging
 * session lifecycle. The compass is driven by the shared MockRangingProvider (a labelled
 * simulation) because real UWB ranging requires two U1/U2-class phones and cannot be wired
 * generically — the network flow (login → peers → invite → accept → compass) is fully real.
 */
class AppViewModel(app: Application) : AndroidViewModel(app), CompassSessionController {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }
    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val api = ApiClient(BuildConfig.BACKEND_HTTP, http, json)
    private val heading = AndroidHeadingSource(
        app.getSystemService(SensorManager::class.java),
    )
    private val fusion = CompassFusion(smoothing = 0.25)

    private var tokens: com.uwbcompass.app.net.AuthTokens? = null
    private var client: RendezvousClient? = null
    private var sessionId: String? = null
    private var rangingJob: Job? = null
    private var headingJob: Job? = null

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        AppStateHolder.instance = this
    }

    // ---- auth ----

    fun login(email: String, password: String) = runAuth { api.login(email.trim(), password) }
    fun register(username: String, email: String, password: String) =
        runAuth { api.register(username.trim(), email.trim(), password) }

    private fun runAuth(block: suspend () -> com.uwbcompass.app.net.AuthTokens) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val t = block()
                tokens = t
                api.tokenProvider = { tokens?.accessToken }
                onAuthenticated(t)
            } catch (e: ApiException) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Network error: ${e.message}")
            }
        }
    }

    private suspend fun onAuthenticated(t: com.uwbcompass.app.net.AuthTokens) {
        val c = RendezvousClient(BuildConfig.BACKEND_WS, t.accessToken, viewModelScope, http)
        client = c
        viewModelScope.launch { c.events.collect { onServerEvent(it) } }
        c.connect(capabilities())
        _state.value = _state.value.copy(loading = false, meUsername = t.user.username, screen = Screen.PEERS)
        refreshContacts()
    }

    private fun capabilities(): Capabilities {
        val uwb = getApplication<Application>().packageManager.hasSystemFeature("android.hardware.uwb")
        return Capabilities(uwb = uwb, ble = true, gps = true)
    }

    // ---- contacts ----

    fun refreshContacts() {
        viewModelScope.launch {
            try {
                val peers = api.listContacts().map { Peer(it.id, it.username, it.online) }
                _state.value = _state.value.copy(peers = peers)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Couldn't load contacts: ${e.message}")
            }
        }
    }

    fun addContact(username: String) {
        if (username.isBlank()) return
        viewModelScope.launch {
            try {
                api.addContact(username.trim())
                refreshContacts()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Couldn't add contact: ${e.message}")
            }
        }
    }

    // ---- session ----

    fun invite(peer: Peer) {
        _state.value = _state.value.copy(peerName = peer.username, error = null)
        client?.invite(peer.id)
    }

    fun acceptIncoming() {
        val inc = _state.value.incoming ?: return
        sessionId = inc.sessionId
        _state.value = _state.value.copy(peerName = inc.fromUsername, incoming = null)
        client?.accept(inc.sessionId)
    }

    fun declineIncoming() {
        val inc = _state.value.incoming ?: return
        client?.decline(inc.sessionId)
        _state.value = _state.value.copy(incoming = null)
    }

    fun endSession() {
        sessionId?.let { client?.end(it) }
        stopRanging()
        _state.value = _state.value.copy(screen = Screen.PEERS)
        refreshContacts()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun onServerEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.PresenceUpdate -> _state.value = _state.value.copy(
                peers = _state.value.peers.map { if (it.id == event.peerId) it.copy(online = event.online) else it },
            )
            is ServerEvent.SessionIncoming -> {
                sessionId = event.sessionId
                _state.value = _state.value.copy(incoming = Incoming(event.sessionId, event.fromUsername))
            }
            is ServerEvent.SessionState -> sessionId = event.sessionId
            is ServerEvent.Negotiated -> {
                sessionId = event.sessionId
                // Real UWB token exchange is device-only; send a placeholder so the server
                // completes the handshake, then drive the compass with the simulation.
                client?.rangingReady(event.sessionId, RangingPayload(event.technology, "demo"))
                startCompass(event.technology)
            }
            is ServerEvent.Downgrade -> startCompass(event.technology)
            is ServerEvent.Ended -> {
                stopRanging()
                _state.value = _state.value.copy(
                    screen = Screen.PEERS,
                    incoming = null,
                    error = if (event.reason == "declined") "Peer declined" else null,
                )
            }
            is ServerEvent.Error -> _state.value = _state.value.copy(error = event.message)
            else -> Unit
        }
    }

    // ---- compass (simulated ranging) ----

    private fun startCompass(technology: Technology) {
        stopRanging()
        fusion.reset()
        _state.value = _state.value.copy(screen = Screen.COMPASS, compass = CompassUiState(technology = technology))

        headingJob = viewModelScope.launch {
            heading.headingDegrees().collect { h ->
                _state.value = _state.value.copy(
                    compass = _state.value.compass.copy(arrowDeg = fusion.arrowRelativeDeg(h)),
                )
            }
        }
        rangingJob = viewModelScope.launch {
            var currentHeading = 0.0
            while (isActive) {
                val provider = MockRangingProvider(technology = technology, script = MockScript.circling(120, radius = 6.0))
                provider.samples().collect { sample ->
                    sample.distanceMeters?.let { fusion.onDistance(it) }
                    sample.azimuthDeg?.let { fusion.onDirectionalSample(it, currentHeading) }
                    currentHeading = (currentHeading + 1) % 360
                    _state.value = _state.value.copy(
                        compass = _state.value.compass.copy(
                            technology = sample.technology,
                            distanceMeters = fusion.smoothedDistanceMeters,
                            arrowDeg = fusion.arrowRelativeDeg(currentHeading),
                            quality = sample.quality,
                            hasDirection = sample.hasDirection,
                        ),
                    )
                }
            }
        }
    }

    override fun stopRanging() {
        rangingJob?.cancel(); rangingJob = null
        headingJob?.cancel(); headingJob = null
    }

    override fun onCleared() {
        stopRanging()
        client?.close()
    }
}

/** Lets MainActivity.onStop stop ranging when backgrounded (requirement). */
object AppStateHolder {
    var instance: CompassSessionController? = null
}
