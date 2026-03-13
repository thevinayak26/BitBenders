package com.bitchat.android.sos

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SosScreen(vm: SosViewModel) {
    val ui by vm.uiState.collectAsState()
    val context = LocalContext.current

    val pulse = rememberInfiniteTransition(label = "sos_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (ui.state) {
            SosState.IDLE -> {
                Text("RescueMesh", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Monitoring active", color = Color(0xFF27AE60), fontSize = 16.sp)
            }
            SosState.WAITING_ACK -> {
                Text("SOS SENT", fontSize = 28.sp, color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold)
                Text("Broadcasting every 5 seconds...", color = Color(0xFFE74C3C))
                Text("Waiting for rescue team to accept", color = Color.Gray, fontSize = 14.sp)
            }
            SosState.CONFIRMED -> {
                Text("HELP IS COMING", fontSize = 28.sp, color = Color(0xFF27AE60), fontWeight = FontWeight.Bold)
                ui.confirmedByRescuer?.let {
                    Text("Accepted by: $it", color = Color(0xFF27AE60))
                }
            }
            else -> {
            }
        }

        Spacer(Modifier.height(12.dp))
        val riskColor = when (ui.disasterRisk) {
            DisasterRisk.HIGH -> Color(0xFFC62828)
            DisasterRisk.ELEVATED -> Color(0xFFF57C00)
            DisasterRisk.LOW -> Color(0xFF2E7D32)
        }
        Text(
            text = "Predicted risk: ${ui.disasterRisk.name}",
            color = riskColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Text(
            text = ui.latestInstructionText,
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(48.dp))

        val buttonColor = if (ui.state == SosState.CONFIRMED) Color(0xFF27AE60) else Color(0xFFE74C3C)
        val buttonScale = if (ui.state == SosState.WAITING_ACK) scale else 1f

        Button(
            onClick = {
                if (ui.state == SosState.IDLE || ui.state == SosState.CONFIRMED) {
                    vm.triggerManualSos()
                } else {
                    vm.cancelSos()
                }
            },
            modifier = Modifier.size(180.dp).scale(buttonScale),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (ui.state) {
                        SosState.IDLE -> "SOS"
                        SosState.WAITING_ACK -> "CANCEL"
                        else -> "RESEND"
                    },
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                if (ui.state == SosState.IDLE) {
                    Text("Press for help", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gateway mode (I have internet)", fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            Switch(checked = ui.isGatewayMode, onCheckedChange = { vm.setGatewayMode(it) })
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (ui.isGatewayMode) {
                "ON: Sends SOS to server directly from this phone."
            } else {
                "OFF: Mesh-only mode. Requires a nearby gateway device to relay SOS."
            },
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val lat = ui.latestLatitude
                val lon = ui.latestLongitude
                if (lat != null && lon != null) {
                    val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            },
            enabled = ui.latestLatitude != null && ui.latestLongitude != null
        ) {
            Text("Open Last SOS Location in Google Maps")
        }
    }
}
