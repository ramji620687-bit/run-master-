package com.runmaster.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.vector.ImageVector
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
private fun RowScope.NavItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.Black,
            selectedTextColor = NeonLime,
            indicatorColor = NeonLime
        )
    )
}

@Composable
fun RunMasterNavigation() {
    val run by TrackingState.state.collectAsState()
    var screen by rememberSaveable {
        mutableStateOf(if (TrackingState.state.value.status != RunStatus.IDLE) "live_run" else "home")
    }

    BackHandler(enabled = screen == "live_run") { screen = "home" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                NavItem(
                    selected = screen == "home" || screen == "live_run",
                    label = "Run",
                    icon = Icons.Default.DirectionsRun,
                    onClick = { screen = if (run.status != RunStatus.IDLE) "live_run" else "home" }
                )
                NavItem(
                    selected = screen == "ground",
                    label = "Ground",
                    icon = Icons.Default.SportsScore,
                    onClick = { screen = "ground" }
                )
                NavItem(
                    selected = screen == "history",
                    label = "History",
                    icon = Icons.Default.History,
                    onClick = { screen = "history" }
                )
                NavItem(
                    selected = screen == "analytics",
                    label = "Stats",
                    icon = Icons.Default.Insights,
                    onClick = { screen = "analytics" }
                )
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                "home" -> HomeScreen(onOpenLive = { screen = "live_run" })
                "live_run" -> LiveRunningScreen(
                    onFinished = { screen = "history" },
                    onBack = { screen = "home" }
                )
                "ground" -> GroundCalibrationScreen()
                "history" -> WorkoutHistoryScreen()
                "analytics" -> AnalyticsScreen()
            }
        }
    }
}
