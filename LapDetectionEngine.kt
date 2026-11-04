package com.runmaster.app.domain

data class TrackLinePoint(val latitude: Double, val longitude: Double)

class LapDetectionEngine(
    private val trackDistanceMeters: Double = 400.0,
    private val startFinishLineP1: TrackLinePoint? = null,
    private val startFinishLineP2: TrackLinePoint? = null
) {
    private var lastLapTimestamp: Long = 0L
    private val lapCooldownMs: Long = 20_000L // minimum gap between two laps (prevents double counting)
    private var lastRecordedLapDistance = 0.0

    val hasLine: Boolean get() = startFinishLineP1 != null && startFinishLineP2 != null

    /** Method A: distance based lap counting. */
    fun checkDistanceBasedLap(currentTotalDistanceMeters: Double): Boolean {
        if (currentTotalDistanceMeters - lastRecordedLapDistance >= trackDistanceMeters) {
            lastRecordedLapDistance += trackDistanceMeters
            return true
        }
        return false
    }

    /** Method B: GPS start/finish line crossing. */
    fun checkLineCrossingLap(
        pPrev: TrackLinePoint,
        pCurr: TrackLinePoint,
        currentTimeMs: Long
    ): Boolean {
        val a = startFinishLineP1 ?: return false
        val b = startFinishLineP2 ?: return false
        if (lastLapTimestamp != 0L && currentTimeMs - lastLapTimestamp < lapCooldownMs) return false

        if (linesIntersect(pPrev, pCurr, a, b)) {
            lastLapTimestamp = currentTimeMs
            return true
        }
        return false
    }

    private fun linesIntersect(
        p1: TrackLinePoint, p2: TrackLinePoint,
        q1: TrackLinePoint, q2: TrackLinePoint
    ): Boolean {
        fun ccw(a: TrackLinePoint, b: TrackLinePoint, c: TrackLinePoint): Boolean {
            return (c.latitude - a.latitude) * (b.longitude - a.longitude) >
                    (b.latitude - a.latitude) * (c.longitude - a.longitude)
        }
        return ccw(p1, q1, q2) != ccw(p2, q1, q2) && ccw(p1, p2, q1) != ccw(p1, p2, q2)
    }
}
