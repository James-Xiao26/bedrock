package app.bedrock.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.bedrock.engine.BedrockEngine

/**
 * Re-derive the session when the user edits the wall clock or timezone. The
 * monotonic anchor already governs an active window's real close, so this
 * mainly re-posts the close alarm at the correct instant after a clock jump
 * (a forward jump fires it early; a backward jump would otherwise delay it),
 * and lets a timezone change reshape the next window's boundaries at once.
 */
class ClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BedrockEngine.get(context).evaluate()
    }
}
