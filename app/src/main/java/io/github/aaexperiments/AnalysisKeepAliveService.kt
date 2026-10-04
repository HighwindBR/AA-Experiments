package io.github.aaexperiments

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Keeps a long DEX analysis alive across screen-off and transient UI/service recreation. */
class AnalysisKeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Catalog analysis", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("AA Experiments")
            .setContentText("Analyzing Android Auto catalog")
            .setOngoing(true)
            .setSilent(true)
            .build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "catalog_analysis"
        private const val NOTIFICATION_ID = 40
    }
}
