package app.bedrock.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.bedrock.engine.BedrockEngine

/**
 * Recovers the session after reboot or app update: reload the persisted
 * snapshot, catch up past any missed boundaries, re-post alarms, and restart
 * the guard service if a night is in progress.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> BedrockEngine.get(context).recover()
        }
    }
}
