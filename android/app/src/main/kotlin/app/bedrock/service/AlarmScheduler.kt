package app.bedrock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.bedrock.MainActivity
import app.bedrock.service.receivers.AlarmReceiver

/**
 * Alarms for the night's boundaries. Uses setAlarmClock(), which is exact and
 * Doze-exempt WITHOUT the exact-alarm permission (Play bars that for non
 * calendar/alarm apps), and doubles as a process-resurrection point: even if
 * the guard service is killed, the next boundary alarm restarts the engine.
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

    /** Post a single exact alarm (e.g. the snooze re-ring). */
    fun scheduleOne(action: String, epochMs: Long) = schedule(action, epochMs)

    /**
     * The pre-downtime reminder. Not an alarm-clock event (no status-bar alarm
     * icon, no system clock entry) - a late fire under Doze is harmless for a
     * heads-up, so use the inexact setAndAllowWhileIdle, which needs no
     * exact-alarm permission.
     */
    fun scheduleReminder(nowMs: Long, epochMs: Long) {
        if (epochMs > nowMs) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochMs,
                pendingIntent(ACTION_WINDOW_REMINDER),
            )
        } else {
            cancel(ACTION_WINDOW_REMINDER)
        }
    }

    private fun schedule(action: String, epochMs: Long) {
        val showApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // setAlarmClock() is documented exempt from the exact-alarm permission,
        // but some OEM ROMs (seen on MediaTek Motorola) enforce it anyway and
        // throw. A late boundary alarm is a degraded backstop - the guard
        // service is the real enforcer - so fall back to inexact rather than
        // crash the process on launch. ponytail: keep exact where allowed.
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(epochMs, showApp),
                pendingIntent(action),
            )
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochMs,
                pendingIntent(action),
            )
        }
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
        const val ACTION_WINDOW_OPEN = "app.bedrock.alarm.WINDOW_OPEN"
        const val ACTION_WINDOW_CLOSE = "app.bedrock.alarm.WINDOW_CLOSE"
        const val ACTION_WINDOW_REMINDER = "app.bedrock.alarm.WINDOW_REMINDER"
        const val ACTION_GRANT_EXPIRY = "app.bedrock.alarm.GRANT_EXPIRY"

        /** How far ahead of downtime the reminder fires. */
        const val REMINDER_LEAD_MS = 5 * 60 * 1000L

        private val ACTIONS = listOf(
            ACTION_WINDOW_OPEN,
            ACTION_WINDOW_CLOSE,
            ACTION_WINDOW_REMINDER,
            ACTION_GRANT_EXPIRY,
        )
    }
}
