package app.bedrock.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.bedrock.engine.BedrockEngine
import app.bedrock.service.AlarmScheduler
import app.bedrock.service.ReminderNotifier

/**
 * All boundary alarms funnel here. The grant-expiry alarm re-blocks an app the
 * user is still sitting in; everything else goes through the engine, which
 * re-derives the correct state from persisted snapshot + wall clock, so a
 * late, duplicated, or reordered alarm can never corrupt the session.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_GRANT_EXPIRY -> BedrockEngine.get(context).onGrantExpiry()
            AlarmScheduler.ACTION_WINDOW_REMINDER -> ReminderNotifier.post(context)
            else -> BedrockEngine.get(context).evaluate()
        }
    }
}
