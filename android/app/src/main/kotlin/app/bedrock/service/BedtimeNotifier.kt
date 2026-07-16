package app.bedrock.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.bedrock.MainActivity

/** The T-15 and T-1 minute bedtime warnings during wind-down. */
object BedtimeNotifier {

    private const val CHANNEL_ID = "bedrock_warnings"
    private const val NOTIFICATION_ID = 2

    fun postWarning(context: Context, minutesLeft: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Bedtime warnings",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Heads-up shortly before your bedtime lockdown." },
        )
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (minutesLeft == 1) {
            "Bedtime in 1 minute. Wrap it up."
        } else {
            "Bedtime in $minutesLeft minutes. Time to wind down."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Bedrock")
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(minutesLeft * 60_000L)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }
}
