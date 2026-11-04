package com.runmaster.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.runmaster.app.data.prefs.Prefs
import com.runmaster.app.service.LocationTrackingService
import com.runmaster.app.service.RunStatus
import com.runmaster.app.service.TrackingState
import com.runmaster.app.ui.theme.MutedText
import com.runmaster.app.ui.theme.NeonLime

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun HomeScreen(onOpenLive: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val run by TrackingState.state.collectAsState()

    var language by remember { mutableStateOf(prefs.language) }
    var voice by remember { mutableStateOf(prefs.voiceEnabled) }
    var weight by remember { mutableStateOf(prefs.weightKg.toString()) }
    var batteryOk by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    val beginRun: () -> Unit = {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            Toast.makeText(context, "Please turn ON GPS / Location", Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            LocationTrackingService.start(context)
            onOpenLive()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) beginRun()
        else Toast.makeText(context, "Location permission is required to track runs", Toast.LENGTH_LONG).show()
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { batteryOk = isIgnoringBatteryOptimizations(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("RUN MASTER", fontSize = 32.sp, color = NeonLime)
        Text("Android GPS Running & Stadium Tracker", color = MutedText)
        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                if (run.status != RunStatus.IDLE) onOpenLive()
                else if (hasLocationPermission(context)) beginRun()
                else permissionLauncher.launch(permissions)
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                if (run.status != RunStatus.IDLE) "OPEN LIVE WORKOUT" else "START RUNNING",
                color = androidx.compose.ui.graphics.Color.Black,
                fontSize = 18.sp
            )
        }

        if (!batteryOk) {
            SectionCard(title = "Keep tracking alive in background") {
                Text(
                    "Some phones stop apps when the screen is locked. Allow Run Master to run without battery restrictions " +
                        "so GPS keeps recording in your pocket.",
                    color = MutedText, fontSize = 13.sp
                )
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:" + context.packageName))
                        batteryLauncher.launch(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Allow background running", color = androidx.compose.ui.graphics.Color.Black) }
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:" + context.packageName))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open app settings (Xiaomi / Oppo / Vivo: enable Autostart)") }
            }
        }

        SectionCard(title = "Voice & profile") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice announcements", modifier = Modifier.weight(1f))
                Switch(checked = voice, onCheckedChange = { voice = it; prefs.voiceEnabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = language == "en",
                    onClick = { language = "en"; prefs.language = "en" },
                    label = { Text("English") }
                )
                FilterChip(
                    selected = language == "hi",
                    onClick = { language = "hi"; prefs.language = "hi" },
                    label = { Text("हिन्दी") }
                )
            }
            OutlinedTextField(
                value = weight,
                onValueChange = { v ->
                    weight = v.filter { it.isDigit() }.take(3)
                    weight.toIntOrNull()?.let { if (it in 20..250) prefs.weightKg = it }
                },
                label = { Text("Body weight (kg) for calories") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Hindi voice needs a Hindi Text-to-Speech voice installed on your phone; otherwise English is used.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
            )
        }
    }
}
