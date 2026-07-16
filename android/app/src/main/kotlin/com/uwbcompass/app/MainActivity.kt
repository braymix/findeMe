package com.uwbcompass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwbcompass.app.ui.AddContactDialog
import com.uwbcompass.app.ui.CompassScreen
import com.uwbcompass.app.ui.IncomingInviteDialog
import com.uwbcompass.app.ui.LoginScreen
import com.uwbcompass.app.ui.PeerListScreen

/** Marker implemented by AppViewModel so onStop can stop ranging without a DI graph. */
interface CompassSessionController {
    fun stopRanging()
}

/**
 * Single-activity Compose host. Renders Login → Peer list → Compass from AppViewModel's
 * state. Capability detection and all network wiring live in AppViewModel.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Requirement: stop ranging when the app goes to background.
        AppStateHolder.instance?.stopRanging()
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    when (state.screen) {
        Screen.LOGIN -> LoginScreen(
            error = state.error,
            onLogin = vm::login,
            onRegister = vm::register,
        )
        Screen.PEERS -> PeerListScreen(
            peers = state.peers,
            onSelect = vm::invite,
            onAddContact = { showAdd = true },
        )
        Screen.COMPASS -> CompassScreen(
            state = state.compass,
            peerName = state.peerName,
            onEnd = vm::endSession,
        )
    }

    // Overlays.
    if (showAdd) {
        AddContactDialog(
            onAdd = {
                vm.addContact(it)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    state.incoming?.let { inc ->
        IncomingInviteDialog(
            fromUsername = inc.fromUsername,
            onAccept = vm::acceptIncoming,
            onDecline = vm::declineIncoming,
        )
    }
    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
