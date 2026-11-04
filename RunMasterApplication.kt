package com.runmaster.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

class RunMasterApplication : Application() {
    companion object {
        const val TRACKING_CHANNEL_ID = "run_master_tracking_channel"
    }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            load(this@RunMasterApplication, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            TRACKING_CHANNEL_ID,
            "Run Master Active Workout",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows live distance, time and laps while a workout is active"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
