package app.bedrock.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.bedrock.MainActivity

/**
 * The heads-up notice a few minutes before a downtime window opens. Its own
 * channel (IMPORTANCE_HIGH) so it alerts, unlike the silent ongoing session
 * notification. Requires POST_NOTIFICATIONS on API 33+; if it is not granted
 * notify() is a no-op, same as the foreground-service notification.
 */
object ReminderNotifier {
    private const val CHANNEL_ID = "bedrock_reminder"
    private const val NOTIFICATION_ID = 2

    fun post(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downtime reminder",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "A few minutes before downtime starts." },
        )
        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Downtime starts soon")
                .setContentText("Apps will be blocked in 5 minutes.")
                .setAutoCancel(true)
                .setContentIntent(tap)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }
}
