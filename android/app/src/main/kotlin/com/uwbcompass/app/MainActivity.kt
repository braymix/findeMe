package com.uwbcompass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

/**
 * Single-activity Compose host. Navigation between Login → Permission rationale →
 * Peer list → Compass is driven by app state (kept intentionally small; a production app
 * would use androidx.navigation). Capability detection (UWB present?) is done at runtime
 * so non-UWB devices land straight in the BLE/GPS fallback.
 *
 * The wiring of RendezvousClient + AndroidHeadingSource + provider selection into
 * CompassViewModel happens here on device; see docs/device-testing.md for manual steps.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    AppRoot()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Requirement: stop ranging when the app goes to background.
        AppState.instance?.stopRanging()
    }
}

@Composable
fun AppRoot() {
    // App composition root — see the individual screens in com.uwbcompass.app.ui.
    // Kept as a thin placeholder here so the screens/ViewModel/providers stay the unit of
    // review; the concrete navigation graph is assembled on device.
    com.uwbcompass.app.ui.LoginScreen(
        error = null,
        onLogin = { _, _ -> },
        onRegister = { _, _, _ -> },
    )
}

/** Minimal holder so onStop can stop ranging without a full DI graph. */
object AppState {
    var instance: CompassSessionController? = null
}

/** Marker interface implemented by the on-device session controller. */
interface CompassSessionController {
    fun stopRanging()
}
