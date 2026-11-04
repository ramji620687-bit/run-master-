package com.runmaster.app.data.prefs

import android.content.Context
import com.runmaster.app.domain.TrackLinePoint

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("run_master_prefs", Context.MODE_PRIVATE)

    var language: String
        get() = sp.getString("language", "en") ?: "en"
        set(v) = sp.edit().putString("language", v).apply()

    var voiceEnabled: Boolean
        get() = sp.getBoolean("voice", true)
        set(v) = sp.edit().putBoolean("voice", v).apply()

    var trackMode: Boolean
        get() = sp.getBoolean("track_mode", false)
        set(v) = sp.edit().putBoolean("track_mode", v).apply()

    var trackLengthM: Int
        get() = sp.getInt("track_length", 400)
        set(v) = sp.edit().putInt("track_length", v).apply()

    var groundName: String
        get() = sp.getString("ground_name", "My Ground") ?: "My Ground"
        set(v) = sp.edit().putString("ground_name", v).apply()

    var weightKg: Int
        get() = sp.getInt("weight_kg", 65)
        set(v) = sp.edit().putInt("weight_kg", v).apply()

    var lineA: TrackLinePoint?
        get() = getPoint("line_a")
        set(v) = putPoint("line_a", v)

    var lineB: TrackLinePoint?
        get() = getPoint("line_b")
        set(v) = putPoint("line_b", v)

    fun clearLine() {
        lineA = null
        lineB = null
    }

    private fun getPoint(key: String): TrackLinePoint? {
        if (!sp.contains(key + "_lat") || !sp.contains(key + "_lon")) return null
        return TrackLinePoint(
            Double.fromBits(sp.getLong(key + "_lat", 0L)),
            Double.fromBits(sp.getLong(key + "_lon", 0L))
        )
    }

    private fun putPoint(key: String, p: TrackLinePoint?) {
        val e = sp.edit()
        if (p == null) {
            e.remove(key + "_lat").remove(key + "_lon")
        } else {
            e.putLong(key + "_lat", p.latitude.toRawBits()).putLong(key + "_lon", p.longitude.toRawBits())
        }
        e.apply()
    }
}
