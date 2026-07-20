package app.bedrock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.bedrock.engine.BedrockEngine
import app.bedrock.engine.GrantKind
import app.bedrock.engine.SessionEvent

/**
 * Debug-build-only adb hook for E2E scripts (this source set never ships):
 *
 *   adb shell am broadcast -n app.bedrock/.debug.DemoReceiver \
 *     -a app.bedrock.debug.DEMO --ei winddown 5 --ei sleep 60
 *   adb shell am broadcast -n app.bedrock/.debug.DemoReceiver \
 *     -a app.bedrock.debug.EVENT --es event WindowClose|Violation
 *   adb shell am broadcast -n app.bedrock/.debug.DemoReceiver \
 *     -a app.bedrock.debug.GRANT --es pkg com.example --es kind FIVE_MIN
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = BedrockEngine.get(context)
        when (intent.action) {
            "app.bedrock.debug.DEMO" -> engine.startDemoSession(
                windDownSeconds = intent.getIntExtra("winddown", 5),
                sleepSeconds = intent.getIntExtra("sleep", 60),
            )
            "app.bedrock.debug.EVENT" -> when (intent.getStringExtra("event")) {
                "WindowClose" -> engine.dispatch(SessionEvent.WindowCloseDue)
                "Violation" -> engine.dispatch(SessionEvent.ViolationDetected)
            }
            "app.bedrock.debug.GRANT" -> {
                val pkg = intent.getStringExtra("pkg") ?: return
                val kind = runCatching {
                    GrantKind.valueOf(intent.getStringExtra("kind") ?: "FIVE_MIN")
                }.getOrDefault(GrantKind.FIVE_MIN)
                engine.grantApp(pkg, kind)
            }
            "app.bedrock.debug.EVALUATE" -> engine.evaluate()
        }
    }
}
