package com.runmaster.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.runmaster.app.MainActivity
import com.runmaster.app.R
import com.runmaster.app.RunMasterApplication
import com.runmaster.app.data.db.AppDatabase
import com.runmaster.app.data.db.LapEntity
import com.runmaster.app.data.db.WorkoutEntity
import com.runmaster.app.data.prefs.Prefs
import com.runmaster.app.domain.LapDetectionEngine
import com.runmaster.app.domain.TrackLinePoint
import com.runmaster.app.domain.VoiceAnnouncementManager
import com.runmaster.app.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Foreground service (type = location). Keeps recording GPS while the screen is locked
 * or the app is in the background. A partial wake lock keeps the CPU running.
 */
class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START = "com.runmaster.app.START"
        const val ACTION_PAUSE = "com.runmaster.app.PAUSE"
        const val ACTION_RESUME = "com.runmaster.app.RESUME"
        const val ACTION_STOP = "com.runmaster.app.STOP"

        private const val NOTIFICATION_ID = 1001
        private const val MAX_ACCURACY_M = 25f
        private const val MAX_SPEED_MS = 15f
        private const val MIN_MOVE_M = 1.5f
        private const val MAX_ROUTE_POINTS = 20000

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, LocationTrackingService::class.java).setAction(action))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var prefs: Prefs
    private lateinit var voice: VoiceAnnouncementManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var ticker: Job? = null
    private var engine = LapDetectionEngine()

    private var prev: Location? = null
    private var segmentStart = 0L
    private var accumulatedMs = 0L
    private var startTimeMs = 0L
    private var lastLapDistM = 0.0
    private var lastKmAnnounced = 0
    private var trackMode = false
    private var voiceOn = true
    private var weightKg = 65

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) handleLocation(loc)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        prefs = Prefs(this)
        voice = VoiceAnnouncementManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRun()
            ACTION_PAUSE -> pauseRun()
            ACTION_RESUME -> resumeRun()
            ACTION_STOP -> finishRun()
            else -> if (TrackingState.state.value.status == RunStatus.IDLE) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- run control

    private fun startRun() {
        // startForegroundService() requires startForeground() quickly, so do it first.
        if (!enterForeground()) return
        if (TrackingState.state.value.status != RunStatus.IDLE) return

        trackMode = prefs.trackMode
        voiceOn = prefs.voiceEnabled
        weightKg = prefs.weightKg
        val trackLen = prefs.trackLengthM
        val a = prefs.lineA
        val b = prefs.lineB
        engine = if (trackMode && a != null && b != null) {
            LapDetectionEngine(trackLen.toDouble(), a, b)
        } else {
            LapDetectionEngine(trackLen.toDouble())
        }
        voice.setLanguage(prefs.language)

        prev = null
        accumulatedMs = 0L
        lastLapDistM = 0.0
        lastKmAnnounced = 0
        startTimeMs = System.currentTimeMillis()
        segmentStart = SystemClock.elapsedRealtime()

        TrackingState.set(
            RunState(
                status = RunStatus.RUNNING,
                trackLengthM = trackLen,
                groundName = if (trackMode) prefs.groundName else null
            )
        )

        acquireWakeLock()
        if (!requestUpdates()) return
        startTicker()
        if (voiceOn) voice.announceState(true)
        updateNotification()
    }

    private fun pauseRun() {
        val s = TrackingState.state.value
        if (s.status != RunStatus.RUNNING) return
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStart
        ticker?.cancel()
        fused.removeLocationUpdates(callback)
        prev = null
        TrackingState.update { it.copy(status = RunStatus.PAUSED, speedMs = 0f, elapsedSec = accumulatedMs / 1000) }
        updateNotification()
    }

    private fun resumeRun() {
        if (TrackingState.state.value.status != RunStatus.PAUSED) return
        segmentStart = SystemClock.elapsedRealtime()
        TrackingState.update { it.copy(status = RunStatus.RUNNING) }
        if (!requestUpdates()) return
        startTicker()
        updateNotification()
    }

    private fun finishRun() {
        val s = TrackingState.state.value
        if (s.status == RunStatus.IDLE) {
            stopSelf()
            return
        }
        val elapsed = elapsedSeconds()
        ticker?.cancel()
        fused.removeLocationUpdates(callback)
        releaseWakeLock()
        if (voiceOn) voice.announceState(false)

        val id = UUID.randomUUID().toString()
        val avgSpeed = if (elapsed > 0) (s.distanceM / elapsed).toFloat() else 0f
        val workout = WorkoutEntity(
            id = id,
            date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(startTimeMs)),
            startTime = startTimeMs,
            endTime = System.currentTimeMillis(),
            totalDistanceMeters = s.distanceM,
            totalDurationSeconds = elapsed,
            avgSpeedMs = avgSpeed,
            maxSpeedMs = s.maxSpeedMs,
            totalLaps = s.laps.size,
            calories = Format.calories(s.distanceM, weightKg),
            groundName = s.groundName
        )
        val laps = s.laps.map {
            LapEntity(
                workoutId = id,
                lapNumber = it.number,
                durationSeconds = it.durationSec,
                distanceMeters = it.distanceM
            )
        }
        TrackingState.reset()

        // Stay foreground until the workout is safely written to the database.
        scope.launch {
            withContext(Dispatchers.IO) {
                if (workout.totalDistanceMeters >= 10.0) {
                    AppDatabase.get(applicationContext).workoutDao().saveWorkout(workout, laps)
                }
            }
            if (voiceOn) delay(2500) // let the "workout finished" voice play
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ---------------------------------------------------------------- GPS handling

    private fun handleLocation(loc: Location) {
        val s = TrackingState.state.value
        if (s.status != RunStatus.RUNNING) return

        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) {
            TrackingState.update { it.copy(gpsAccuracyM = loc.accuracy) }
            return
        }

        val p = prev
        if (p == null) {
            prev = loc
            addRoutePoint(loc, s.distanceM, 0f)
            return
        }

        val d = p.distanceTo(loc)
        val dt = (loc.elapsedRealtimeNanos - p.elapsedRealtimeNanos) / 1_000_000_000f
        if (dt <= 0f) return
        val v = d / dt
        if (v > MAX_SPEED_MS) return // GPS jump
        if (d < MIN_MOVE_M) {
            TrackingState.update { it.copy(speedMs = 0f, gpsAccuracyM = loc.accuracy) }
            return
        }

        val newDistance = s.distanceM + d
        prev = loc

        // ---- lap detection
        var lapped = false
        if (trackMode) {
            lapped = if (engine.hasLine) {
                val minLapDistance = s.trackLengthM * 0.6
                engine.checkLineCrossingLap(
                    TrackLinePoint(p.latitude, p.longitude),
                    TrackLinePoint(loc.latitude, loc.longitude),
                    SystemClock.elapsedRealtime()
                ) && (newDistance - lastLapDistM) >= minLapDistance
            } else {
                engine.checkDistanceBasedLap(newDistance)
            }
        }

        val elapsed = elapsedSeconds()
        var laps = s.laps
        var lapStart = s.lapStartSec
        if (lapped) {
            val lapNo = laps.size + 1
            val lapDuration = elapsed - s.lapStartSec
            laps = laps + LapRecord(lapNo, lapDuration, newDistance - lastLapDistM)
            lastLapDistM = newDistance
            lapStart = elapsed
            if (voiceOn) voice.announceLap(lapNo, lapDuration)
        }

        val kmNow = (newDistance / 1000.0).toInt()
        if (!lapped && kmNow > lastKmAnnounced) {
            if (voiceOn) voice.announceKm(kmNow, elapsed)
        }
        if (kmNow > lastKmAnnounced) lastKmAnnounced = kmNow

        val route = if (s.route.size < MAX_ROUTE_POINTS) {
            s.route + TrackLinePoint(loc.latitude, loc.longitude)
        } else s.route

        TrackingState.update {
            it.copy(
                distanceM = newDistance,
                elapsedSec = elapsed,
                speedMs = v,
                maxSpeedMs = maxOf(it.maxSpeedMs, v),
                gpsAccuracyM = loc.accuracy,
                laps = laps,
                lapStartSec = lapStart,
                route = route
            )
        }
        updateNotification()
    }

    private fun addRoutePoint(loc: Location, distance: Double, speed: Float) {
        TrackingState.update {
            it.copy(
                distanceM = distance,
                speedMs = speed,
                gpsAccuracyM = loc.accuracy,
                route = it.route + TrackLinePoint(loc.latitude, loc.longitude)
            )
        }
    }

    private fun requestUpdates(): Boolean {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        return try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            true
        } catch (e: SecurityException) {
            // Location permission missing: abort cleanly.
            TrackingState.reset()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            false
        }
    }

    // ---------------------------------------------------------------- time

    private fun elapsedSeconds(): Long {
        val running = TrackingState.state.value.status == RunStatus.RUNNING
        val extra = if (running) SystemClock.elapsedRealtime() - segmentStart else 0L
        return (accumulatedMs + extra) / 1000
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val e = elapsedSeconds()
                TrackingState.update { it.copy(elapsedSec = e) }
                updateNotification()
            }
        }
    }

    // ---------------------------------------------------------------- foreground / notification / wake lock

    private fun enterForeground(): Boolean {
        return try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: Exception) {
            stopSelf()
            false
        }
    }

    private fun buildNotification(): Notification {
        val s = TrackingState.state.value
        val paused = s.status == RunStatus.PAUSED

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun action(act: String, code: Int) = PendingIntent.getService(
            this, code,
            Intent(this, LocationTrackingService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        var text = "${Format.km(s.distanceM)} km  •  ${Format.duration(s.elapsedSec)}"
        if (s.laps.isNotEmpty()) text += "  •  Laps ${s.laps.size}"

        return NotificationCompat.Builder(this, RunMasterApplication.TRACKING_CHANNEL_ID)
            .setContentTitle(if (paused) "Run Master - Paused" else "Run Master - Tracking")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                if (paused) "Resume" else "Pause",
                action(if (paused) ACTION_RESUME else ACTION_PAUSE, 1)
            )
            .addAction(0, "Stop", action(ACTION_STOP, 2))
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RunMaster:Tracking").apply {
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        releaseWakeLock()
        voice.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
