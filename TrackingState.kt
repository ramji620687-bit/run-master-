package com.runmaster.app.service

import com.runmaster.app.domain.TrackLinePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RunStatus { IDLE, RUNNING, PAUSED }

data class LapRecord(val number: Int, val durationSec: Long, val distanceM: Double)

data class RunState(
    val status: RunStatus = RunStatus.IDLE,
    val distanceM: Double = 0.0,
    val elapsedSec: Long = 0L,
    val speedMs: Float = 0f,
    val maxSpeedMs: Float = 0f,
    val gpsAccuracyM: Float? = null,
    val laps: List<LapRecord> = emptyList(),
    val lapStartSec: Long = 0L,
    val route: List<TrackLinePoint> = emptyList(),
    val trackLengthM: Int = 400,
    val groundName: String? = null
)

/** Process-wide live state, written by the service and observed by the UI. */
object TrackingState {
    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    fun update(block: (RunState) -> RunState) = _state.update(block)
    fun set(value: RunState) { _state.value = value }
    fun reset() { _state.value = RunState() }
}
