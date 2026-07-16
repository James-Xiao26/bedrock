package app.bedrock.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.bedrock.engine.BedrockEngine

/**
 * All boundary alarms funnel here. The engine re-derives the correct state
 * from persisted snapshot + wall clock, so a late, duplicated, or reordered
 * alarm can never corrupt the session.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BedrockEngine.get(context).evaluate()
    }
}
