package com.uwbcompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Pre-permission explanation screen shown BEFORE the OS permission dialog (requirement:
 * "schermate di spiegazione prima della richiesta di sistema"). Explains why each
 * permission is needed and reassures the user that no location is sent to the server.
 */
@Composable
fun PermissionRationale(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Before we start", style = MaterialTheme.typography.headlineSmall)
        Text(
            "• Nearby devices (UWB): to measure the precise direction and distance to your peer.",
            modifier = Modifier.padding(top = 16.dp),
        )
        Text("• Bluetooth: fallback proximity when UWB isn't available on both phones.", modifier = Modifier.padding(top = 8.dp))
        Text("• Location: required by Android for Bluetooth scanning and the outdoor GPS fallback.", modifier = Modifier.padding(top = 8.dp))
        Text(
            "Your distance, direction and position never leave your phone — the server only helps you find each other.",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text("Continue")
        }
    }
}

/** Dialog shown when a peer invites us to a ranging session (explicit consent). */
@Composable
fun IncomingInviteDialog(fromUsername: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Find request") },
        text = { Text("$fromUsername wants to locate you. Share your direction & distance with them?") },
        confirmButton = { Button(onClick = onAccept) { Text("Accept") } },
        dismissButton = { Button(onClick = onDecline) { Text("Decline") } },
    )
}
