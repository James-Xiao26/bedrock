package app.bedrock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.bedrock.MainActivity
import app.bedrock.service.receivers.AlarmReceiver

/**
 * Exact alarms for the night's boundaries. Bedrock is an alarm-clock app, so
 * it declares USE_EXACT_ALARM and uses setAlarmClock(), which is exempt from
 * Doze and doubles as a process-resurrection point: even if the guard service
 * is killed, the next boundary alarm restarts the engine.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleBoundaries(nowMs: Long, vararg boundaries: Pair<String, Long>) {
        for ((action, epochMs) in boundaries) {
            if (epochMs > nowMs) {
                schedule(action, epochMs)
            } else {
                cancel(action)
            }
        }
    }

    fun cancelAll() {
        for (action in ACTIONS) cancel(action)
    }

    private fun schedule(action: String, epochMs: Long) {
        val showApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(epochMs, showApp),
            pendingIntent(action),
        )
    }

    private fun cancel(action: String) {
        alarmManager.cancel(pendingIntent(action))
    }

    private fun pendingIntent(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val ACTION_WINDDOWN = "app.bedrock.alarm.WINDDOWN"
        const val ACTION_BEDTIME = "app.bedrock.alarm.BEDTIME"
        const val ACTION_WAKE = "app.bedrock.alarm.WAKE"
        const val ACTION_WARN_15 = "app.bedrock.alarm.WARN_15"
        const val ACTION_WARN_1 = "app.bedrock.alarm.WARN_1"
        private val ACTIONS =
            listOf(ACTION_WINDDOWN, ACTION_BEDTIME, ACTION_WAKE, ACTION_WARN_15, ACTION_WARN_1)
    }
}
