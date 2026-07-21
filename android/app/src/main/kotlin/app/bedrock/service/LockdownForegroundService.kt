package app.bedrock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.bedrock.MainActivity
import app.bedrock.blocking.BlockingController
import app.bedrock.blocking.Permissions
import app.bedrock.blocking.UsageEventsPoller

/**
 * Keeps the enforcement engine alive while a block window is active. It runs
 * the UsageEvents poller when the accessibility service is off. The alarms
 * scheduled via setAlarmClock() are independent resurrection points if the
 * system still kills us. Unlike a takeover, it does not touch the home screen
 * - only opening a blocked app triggers the blocker.
 */
class LockdownForegroundService : Service() {

    private var poller: UsageEventsPoller? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Promote to foreground at the earliest possible moment: the window
        // between startForegroundService() and startForeground() is a crash
        // (ForegroundServiceDidNotStartInTimeException) if the main thread
        // is busy, e.g. during a cold start at bedtime.
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        syncMonitors()
        return START_STICKY
    }

    private fun promoteToForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    override fun onDestroy() {
        poller?.stop()
        poller = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Called on every (re)start; the engine pokes us at each transition. */
    private fun syncMonitors() {
        val blocking = BlockingController.isActive(this)
        if (blocking && !Permissions.accessibilityEnabled(this)) {
            (poller ?: UsageEventsPoller(this).also { poller = it }).start()
        } else {
            poller?.stop()
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Downtime is on")
            .setContentText("Bedrock is blocking apps during your window.")
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downtime session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while a block window is running."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "bedrock_session"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, LockdownForegroundService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockdownForegroundService::class.java))
        }
    }
}
