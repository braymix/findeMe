package com.uwbcompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A contact in the peer list with live presence. */
data class Peer(val id: String, val username: String, val online: Boolean)

@Composable
fun PeerListScreen(
    peers: List<Peer>,
    onSelect: (Peer) -> Unit,
    onAddContact: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Peers", style = MaterialTheme.typography.headlineSmall)
            Text("Add", modifier = Modifier.padding(8.dp).clickableNoIndication(onAddContact))
        }
        Divider()
        LazyColumn {
            items(peers) { peer ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).clickableNoIndication { onSelect(peer) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (peer.online) Color(0xFF43A047) else Color(0xFFBDBDBD)),
                        ) {}
                        Text(peer.username)
                    }
                }
            }
        }
    }
}
