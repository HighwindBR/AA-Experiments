package io.github.highwindbr.aaxp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.highwindbr.aaxp.core.AnalysisWakeLockLease

/**
 * Foreground analysis host. A bounded PARTIAL_WAKE_LOCK keeps the CPU running
 * across backgrounding and screen-off without forcing the display to stay on.
 *
 * The ViewModel still owns the scan coroutine. A process kill, user force-stop,
 * reboot or task removal cannot be resumed by this service.
 */
class AnalysisKeepAliveService : Service() {
    private var cpuLease: AnalysisWakeLockLease? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Catalog analysis", NotificationManager.IMPORTANCE_LOW)
        )
        val returnToApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("AA Experiments")
            .setContentText("Android Auto analysis is running")
            .setContentIntent(returnToApp)
            .setOngoing(true)
            .setSilent(true)
            .build())

        val powerManager = getSystemService(PowerManager::class.java)
        val lock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "AAExperiments:CatalogAnalysis"
        ).apply { setReferenceCounted(false) }
        cpuLease = AnalysisWakeLockLease(object : AnalysisWakeLockLease.PowerLock {
            override val isHeld: Boolean get() = lock.isHeld
            override fun acquire(timeoutMillis: Long) { lock.acquire(timeoutMillis) }
            override fun release() { lock.release() }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cpuLease?.start()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // This architecture keeps the coroutine in the ViewModel. Removing the
        // app task destroys that owner, so do not leave an orphaned CPU lease.
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            cpuLease?.stop()
            cpuLease = null
        } finally {
            super.onDestroy()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "catalog_analysis"
        private const val NOTIFICATION_ID = 40
    }
}
