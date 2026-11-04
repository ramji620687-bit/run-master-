package com.runmaster.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runmaster.app.service.LocationTrackingService
import com.runmaster.app.service.RunStatus
import com.runmaster.app.service.TrackingState
import com.runmaster.app.ui.theme.MutedText
import com.runmaster.app.ui.theme.NeonLime
import com.runmaster.app.util.Format

@Composable
fun LiveRunningScreen(onFinished: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val run by TrackingState.state.collectAsState()
    var confirmFinish by remember { mutableStateOf(false) }
    var wasActive by remember { mutableStateOf(false) }

    // When the run ends (Finish button or the notification's Stop button) go to History.
    LaunchedEffect(run.status) {
        if (run.status != RunStatus.IDLE) wasActive = true
        else if (wasActive) onFinished()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RouteMap(
            route = run.route,
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))
        )

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Format.km(run.distanceM),
                fontSize = 52.sp,
                color = NeonLime
            )
            Text(
                text = when (run.status) {
                    RunStatus.IDLE -> "Starting GPS..."
                    RunStatus.RUNNING -> "KILOMETERS"
                    RunStatus.PAUSED -> "PAUSED"
                },
                color = MutedText, fontSize = 12.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("TIME", Format.duration(run.elapsedSec), Modifier.weight(1f))
            StatBox("PACE /km", Format.pace(run.distanceM, run.elapsedSec), Modifier.weight(1f))
            StatBox("SPEED km/h", Format.speedKmh(run.speedMs), Modifier.weight(1f))
        }

        if (run.groundName != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("LAPS", run.laps.size.toString(), Modifier.weight(1f))
                StatBox("THIS LAP", Format.duration((run.elapsedSec - run.lapStartSec).coerceAtLeast(0)), Modifier.weight(1f))
                StatBox(
                    "LAST LAP",
                    run.laps.lastOrNull()?.let { Format.duration(it.durationSec) } ?: "--:--",
                    Modifier.weight(1f)
                )
            }
        }

        val accuracy = run.gpsAccuracyM
        Text(
            text = if (accuracy == null) "GPS: searching..." else "GPS accuracy: ±${accuracy.toInt()} m",
            color = MutedText, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        if (run.status == RunStatus.IDLE) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Back")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        LocationTrackingService.send(
                            context,
                            if (run.status == RunStatus.RUNNING) LocationTrackingService.ACTION_PAUSE
                            else LocationTrackingService.ACTION_RESUME
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(
                        if (run.status == RunStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null, tint = Color.Black
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(
                        if (run.status == RunStatus.RUNNING) "PAUSE" else "RESUME",
                        color = Color.Black
                    )
                }
                Button(
                    onClick = { confirmFinish = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("FINISH", color = Color.White)
                }
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish workout?") },
            text = { Text("Your run will be saved to History.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    LocationTrackingService.send(context, LocationTrackingService.ACTION_STOP)
                }) { Text("Finish", color = NeonLime) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text("Keep running") }
            }
        )
    }
}
