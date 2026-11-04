package com.runmaster.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.runmaster.app.data.prefs.Prefs
import com.runmaster.app.domain.TrackLinePoint
import com.runmaster.app.ui.theme.MutedText
import com.runmaster.app.ui.theme.NeonLime
import java.util.Locale

private fun hasLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun fmt(p: TrackLinePoint?): String =
    if (p == null) "not set" else String.format(Locale.US, "%.6f, %.6f", p.latitude, p.longitude)

@Composable
fun GroundCalibrationScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var trackMode by remember { mutableStateOf(prefs.trackMode) }
    var length by remember { mutableStateOf(prefs.trackLengthM) }
    var customText by remember {
        mutableStateOf(if (prefs.trackLengthM in listOf(200, 300, 400)) "" else prefs.trackLengthM.toString())
    }
    var name by remember { mutableStateOf(prefs.groundName) }
    var pointA by remember { mutableStateOf(prefs.lineA) }
    var pointB by remember { mutableStateOf(prefs.lineB) }
    var busy by remember { mutableStateOf(false) }

    fun markPoint(isA: Boolean) {
        busy = true
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc: Location? ->
                    busy = false
                    if (loc == null) {
                        Toast.makeText(context, "Could not get GPS fix, try again", Toast.LENGTH_SHORT).show()
                    } else {
                        val p = TrackLinePoint(loc.latitude, loc.longitude)
                        if (isA) { prefs.lineA = p; pointA = p } else { prefs.lineB = p; pointB = p }
                        Toast.makeText(context, "Saved (GPS ±${loc.accuracy.toInt()} m)", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { busy = false }
        } catch (e: SecurityException) {
            busy = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { Toast.makeText(context, "Now tap the button again", Toast.LENGTH_SHORT).show() }

    val mark: (Boolean) -> Unit = { isA ->
        if (hasLocation(context)) markPoint(isA)
        else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ground & Track Calibration", fontSize = 22.sp, color = NeonLime)

        SectionCard(title = "Stadium track mode") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Count laps automatically", modifier = Modifier.weight(1f))
                Switch(checked = trackMode, onCheckedChange = { trackMode = it; prefs.trackMode = it })
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40); prefs.groundName = name },
                label = { Text("Ground name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard(title = "Track length (one lap)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200, 300, 400).forEach { m ->
                    FilterChip(
                        selected = length == m && customText.isEmpty(),
                        onClick = { length = m; customText = ""; prefs.trackLengthM = m },
                        label = { Text("$m m") }
                    )
                }
            }
            OutlinedTextField(
                value = customText,
                onValueChange = { v ->
                    customText = v.filter { it.isDigit() }.take(4)
                    customText.toIntOrNull()?.let { if (it in 50..5000) { length = it; prefs.trackLengthM = it } }
                },
                label = { Text("Custom length (metres)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Current: $length m per lap", color = MutedText, fontSize = 12.sp)
        }

        SectionCard(title = "Start / finish line (optional, more accurate)") {
            Text(
                "Stand at the LEFT end of the finish line and tap A. Walk to the RIGHT end of the line and tap B. " +
                    "Without a line, laps are counted by distance.",
                color = MutedText, fontSize = 13.sp
            )
            Text("Point A: ${fmt(pointA)}", fontSize = 13.sp)
            Text("Point B: ${fmt(pointB)}", fontSize = 13.sp)
            val a = pointA
            val b = pointB
            if (a != null && b != null) {
                val res = FloatArray(1)
                Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, res)
                Text(
                    "Line width: ${res[0].toInt()} m" + if (res[0] < 3f) "  (too short - set the points again)" else "",
                    color = MutedText, fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { mark(true) }, enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                    modifier = Modifier.weight(1f)
                ) { Text("Mark A", color = Color.Black) }
                Button(
                    onClick = { mark(false) }, enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                    modifier = Modifier.weight(1f)
                ) { Text("Mark B", color = Color.Black) }
            }
            OutlinedButton(
                onClick = { prefs.clearLine(); pointA = null; pointB = null },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear line") }
        }
    }
}
