package com.runmaster.app.util

import java.util.Locale

object Format {
    fun km(meters: Double): String = String.format(Locale.US, "%.2f", meters / 1000.0)

    fun duration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** Average pace in min/km, or "--:--" when there is not enough data. */
    fun pace(distanceMeters: Double, seconds: Long): String {
        if (distanceMeters < 20.0 || seconds <= 0L) return "--:--"
        val secPerKm = (seconds / (distanceMeters / 1000.0)).toLong()
        if (secPerKm > 99 * 60) return "--:--"
        return String.format(Locale.US, "%d:%02d", secPerKm / 60, secPerKm % 60)
    }

    fun speedKmh(ms: Float): String = String.format(Locale.US, "%.1f", ms * 3.6f)

    fun calories(distanceMeters: Double, weightKg: Int): Int =
        (distanceMeters / 1000.0 * weightKg * 1.036).toInt()
}
