package com.runmaster.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.runmaster.app.service.RunStatus
import com.runmaster.app.service.TrackingState
import com.runmaster.app.ui.screens.AnalyticsScreen
import com.runmaster.app.ui.screens.GroundCalibrationScreen
import com.runmaster.app.ui.screens.HomeScreen
import com.runmaster.app.ui.screens.LiveRunningScreen
import com.runmaster.app.ui.screens.WorkoutHistoryScreen
import com.runmaster.app.ui.theme.NeonLime
import com.runmaster.app.ui.theme.SurfaceDark

@Composable
fun RunMasterNavigation() {
    val run by TrackingState.state.collectAsState()
    // If the app is re-opened while a run is active (e.g. from the notification) show the live screen.
    var screen by rememberSaveable {
        mutableStateOf(if (TrackingState.state.value.status != RunStatus.IDLE) "live_run" else "home")
    }

    BackHandler(enabled = screen == "live_run") { screen = "home" }

    @Composable
    fun item(key: String, label: String, icon: @Composable () -> Unit, target: String = key) {
        val selected = screen == key || (key == "home" && screen == "live_run")
        NavigationBarItem(
            selected = selected,
            onClick = { screen = target },
            icon = icon,
            label = { Text(label) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = NeonLime,
                indicatorColor = NeonLime
            )
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                item(
                    key = "home", label = "Run",
                    icon = { Icon(Icons.Default.DirectionsRun, contentDescription = "Run") },
                    target = if (run.status != RunStatus.IDLE) "live_run" else "home"
                )
                item("ground", "Ground", { Icon(Icons.Default.SportsScore, contentDescription = "Ground") })
                item("history", "History", { Icon(Icons.Default.History, contentDescription = "History") })
                item("analytics", "Stats", { Icon(Icons.Default.Insights, contentDescription = "Stats") })
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                "home" -> HomeScreen(onOpenLive = { screen = "live_run" })
                "live_run" -> LiveRunningScreen(onFinished = { screen = "history" }, onBack = { screen = "home" })
                "ground" -> GroundCalibrationScreen()
                "history" -> WorkoutHistoryScreen()
                "analytics" -> AnalyticsScreen()
            }
        }
    }
}
