package com.uwbcompass.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwbcompass.app.CompassUiState
import com.uwbcompass.core.SignalQuality
import com.uwbcompass.core.Technology

/**
 * Adaptive compass (requirement 4):
 *  - UWB with a directional fix → a precise rotating arrow.
 *  - BLE/GPS (no direction) → a hot/cold proximity ring, NEVER a fake arrow.
 * Always shows distance (with accuracy hint), a technology badge, and a signal-quality badge.
 */
@Composable
fun CompassScreen(state: CompassUiState, peerName: String, onEnd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(peerName, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TechnologyBadge(state.technology)
                QualityBadge(state.quality)
            }
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
            if (state.hasDirection && state.arrowDeg != null) {
                DirectionalArrow(state.arrowDeg)
            } else {
                ProximityRing(state.quality, state.distanceMeters)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.distanceMeters?.let { "%.1f m".format(it) } ?: "—",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(accuracyHint(state.technology, state.quality), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text("End session") }
        }
    }
}

@Composable
private fun DirectionalArrow(arrowDeg: Double) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        rotate(degrees = arrowDeg.toFloat(), pivot = Offset(cx, cy)) {
            val path = Path().apply {
                moveTo(cx, cy - size.minDimension * 0.42f) // tip
                lineTo(cx - size.minDimension * 0.16f, cy + size.minDimension * 0.20f)
                lineTo(cx, cy + size.minDimension * 0.08f)
                lineTo(cx + size.minDimension * 0.16f, cy + size.minDimension * 0.20f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun ProximityRing(quality: SignalQuality, distance: Double?) {
    // Hot/cold ring: warmer & thicker as the peer gets closer. No direction is implied.
    val warmth = when (quality) {
        SignalQuality.HIGH -> Color(0xFFE53935) // hot (near)
        SignalQuality.MEDIUM -> Color(0xFFFB8C00)
        SignalQuality.LOW -> Color(0xFF1E88E5) // cold (far)
        SignalQuality.LOST -> Color(0xFF9E9E9E)
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = when (quality) {
            SignalQuality.HIGH -> 40f
            SignalQuality.MEDIUM -> 28f
            SignalQuality.LOW -> 16f
            SignalQuality.LOST -> 8f
        }
        drawCircle(
            color = warmth,
            radius = size.minDimension * 0.4f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
    }
}

@Composable
private fun TechnologyBadge(tech: Technology) {
    Card(shape = RoundedCornerShape(50)) {
        Text(
            text = tech.name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QualityBadge(quality: SignalQuality) {
    val label = when (quality) {
        SignalQuality.HIGH -> "Signal: high"
        SignalQuality.MEDIUM -> "Signal: medium"
        SignalQuality.LOW -> "Signal: low"
        SignalQuality.LOST -> "Signal: lost"
    }
    Card(shape = RoundedCornerShape(50)) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

private fun accuracyHint(tech: Technology, quality: SignalQuality): String = when (tech) {
    Technology.UWB -> if (quality == SignalQuality.HIGH) "±10 cm · precise direction" else "UWB · direction may be coarse"
    Technology.BLE -> "approximate proximity (no direction)"
    Technology.GPS -> "coarse outdoor estimate"
}
