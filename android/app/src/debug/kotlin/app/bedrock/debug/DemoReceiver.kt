package app.bedrock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.bedrock.engine.BedrockEngine
import app.bedrock.engine.SessionEvent

/**
 * Debug-build-only adb hook for E2E scripts (this source set never ships):
 *
 *   adb shell am broadcast -n app.bedrock/.debug.DemoReceiver \
 *     -a app.bedrock.debug.DEMO --ei winddown 10 --ei sleep 30
 *   adb shell am broadcast -n app.bedrock/.debug.DemoReceiver \
 *     -a app.bedrock.debug.EVENT --es event WakeDue|EscapeCompleted|...
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = BedrockEngine.get(context)
        when (intent.action) {
            "app.bedrock.debug.DEMO" -> engine.startDemoSession(
                windDownSeconds = intent.getIntExtra("winddown", 10),
                sleepSeconds = intent.getIntExtra("sleep", 30),
            )
            "app.bedrock.debug.EVENT" -> when (intent.getStringExtra("event")) {
                "WakeDue" -> engine.dispatch(SessionEvent.WakeDue)
                "EscapeCompleted" -> engine.dispatch(SessionEvent.EscapeCompleted)
                "BypassPurchased" -> engine.dispatch(SessionEvent.BypassPurchased)
                "ViolationDetected" -> engine.dispatch(SessionEvent.ViolationDetected)
                "AlarmDismissed" -> engine.dispatch(SessionEvent.AlarmDismissed)
                "AlarmSnoozed" -> engine.dispatch(SessionEvent.AlarmSnoozed)
            }
            "app.bedrock.debug.EVALUATE" -> engine.evaluate()
        }
    }
}
