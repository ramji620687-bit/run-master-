package com.runmaster.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runmaster.app.data.db.AppDatabase
import com.runmaster.app.data.db.LapEntity
import com.runmaster.app.data.db.WorkoutEntity
import com.runmaster.app.ui.theme.MutedText
import com.runmaster.app.ui.theme.NeonLime
import com.runmaster.app.util.Format
import kotlinx.coroutines.launch

@Composable
fun WorkoutHistoryScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).workoutDao() }
    val workouts by dao.observeWorkouts().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Workout History", fontSize = 22.sp, color = NeonLime)
        if (workouts.isEmpty()) {
            Text("No workouts yet. Go for a run!", color = MutedText)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(workouts, key = { it.id }) { w ->
                WorkoutCard(w, onDelete = { scope.launch { dao.deleteWorkout(w) } })
            }
        }
    }
}

@Composable
private fun WorkoutCard(w: WorkoutEntity, onDelete: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val laps by produceState(initialValue = emptyList<LapEntity>(), w.id, expanded) {
        value = if (expanded && w.totalLaps > 0) {
            AppDatabase.get(context).workoutDao().getLaps(w.id)
        } else emptyList()
    }

    SectionCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(w.date, color = MutedText, fontSize = 12.sp)
                Text("${Format.km(w.totalDistanceMeters)} km", fontSize = 24.sp, color = NeonLime)
                if (w.groundName != null) Text(w.groundName, color = MutedText, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MutedText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Time ${Format.duration(w.totalDurationSeconds)}", fontSize = 13.sp)
            Text("Pace ${Format.pace(w.totalDistanceMeters, w.totalDurationSeconds)}/km", fontSize = 13.sp)
            Text("${w.calories} kcal", fontSize = 13.sp)
        }
        if (w.totalLaps > 0) Text("Laps: ${w.totalLaps}  (tap to ${if (expanded) "hide" else "show"} splits)", color = MutedText, fontSize = 12.sp)
        if (expanded) {
            laps.forEach { l ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Lap ${l.lapNumber}", fontSize = 13.sp)
                    Text(Format.duration(l.durationSeconds), fontSize = 13.sp, color = NeonLime)
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).workoutDao() }
    val workouts by dao.observeWorkouts().collectAsState(initial = emptyList())

    val totalMeters = workouts.sumOf { it.totalDistanceMeters }
    val totalSeconds = workouts.sumOf { it.totalDurationSeconds }
    val totalLaps = workouts.sumOf { it.totalLaps }
    val totalCalories = workouts.sumOf { it.calories }
    val bestPace = workouts
        .filter { it.totalDistanceMeters >= 1000.0 && it.totalDurationSeconds > 0 }
        .minOfOrNull { it.totalDurationSeconds / (it.totalDistanceMeters / 1000.0) }
    val longest = workouts.maxOfOrNull { it.totalDistanceMeters } ?: 0.0
    val recent = workouts.take(7).reversed().map { it.totalDistanceMeters / 1000.0 }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Performance Analytics", fontSize = 22.sp, color = NeonLime)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("WORKOUTS", workouts.size.toString(), Modifier.weight(1f))
            StatBox("TOTAL KM", Format.km(totalMeters), Modifier.weight(1f))
            StatBox("TIME", Format.duration(totalSeconds), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("TOTAL LAPS", totalLaps.toString(), Modifier.weight(1f))
            StatBox("KCAL", totalCalories.toString(), Modifier.weight(1f))
            StatBox("LONGEST KM", Format.km(longest), Modifier.weight(1f))
        }
        StatBox(
            "BEST AVERAGE PACE /km (runs over 1 km)",
            bestPace?.let { String.format("%d:%02d", it.toLong() / 60, it.toLong() % 60) } ?: "--:--",
            Modifier.fillMaxWidth()
        )

        SectionCard(title = "Last ${recent.size.coerceAtLeast(1)} workouts (km)") {
            if (recent.isEmpty()) {
                Text("Your chart will appear after the first run.", color = MutedText, fontSize = 13.sp)
            } else {
                val max = recent.max().coerceAtLeast(0.1)
                Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                    val slot = size.width / recent.size
                    val barW = slot * 0.5f
                    recent.forEachIndexed { i, v ->
                        val h = (v / max).toFloat() * size.height
                        drawRect(
                            color = NeonLime,
                            topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - h),
                            size = Size(barW, h)
                        )
                    }
                }
            }
        }
    }
}
