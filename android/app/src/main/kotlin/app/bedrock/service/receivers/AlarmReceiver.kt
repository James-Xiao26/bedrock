package app.bedrock.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.bedrock.engine.BedrockEngine
import app.bedrock.service.AlarmScheduler
import app.bedrock.service.BedtimeNotifier

/**
 * All boundary alarms funnel here. Warning alarms just post a notification;
 * everything else goes through the engine, which re-derives the correct
 * state from persisted snapshot + wall clock, so a late, duplicated, or
 * reordered alarm can never corrupt the session.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_WARN_15 -> BedtimeNotifier.postWarning(context, 15)
            AlarmScheduler.ACTION_WARN_1 -> BedtimeNotifier.postWarning(context, 1)
            else -> BedrockEngine.get(context).evaluate()
        }
    }
}
