package app.bedrock.channel

import android.content.Context
import android.content.Intent
import app.bedrock.BuildConfig
import app.bedrock.engine.BedrockEngine
import app.bedrock.engine.ConfigPatch
import app.bedrock.ui.NightClockActivity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Dart-facing API of the native engine ("bedrock/engine").
 * Config payloads cross the channel as JSON strings; the engine's
 * kotlinx-serialization model is the single schema.
 */
class EngineMethodHandler(private val context: Context) : MethodChannel.MethodCallHandler {

    private val engine: BedrockEngine get() = BedrockEngine.get(context)

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "ping" -> result.success("pong")

            "getConfig" -> result.success(
                mapOf(
                    "active" to engine.store.activeConfig().toJson(),
                    "pending" to engine.store.pendingPatch().toJson(),
                ),
            )

            "updateConfig" -> {
                val patch = ConfigPatch.fromJson(call.argument<String>("patch")!!)
                val outcome = engine.updateConfig(patch)
                result.success(
                    mapOf(
                        "active" to outcome.active.toJson(),
                        "pending" to outcome.pending.toJson(),
                    ),
                )
            }

            "getSessionState" -> {
                val snapshot = engine.store.snapshot()
                result.success(
                    mapOf(
                        "state" to snapshot.state.name,
                        "blocking" to snapshot.blocking,
                        "plannedBedtime" to snapshot.night?.bedEpochMs,
                        "plannedWake" to snapshot.night?.wakeEpochMs,
                    ),
                )
            }

            "startDemoSession" -> {
                if (!BuildConfig.DEBUG) {
                    result.error("debug_only", "Demo sessions exist only in debug builds", null)
                    return
                }
                engine.startDemoSession(
                    windDownSeconds = call.argument<Int>("windDownSeconds") ?: 15,
                    sleepSeconds = call.argument<Int>("sleepSeconds") ?: 30,
                )
                result.success(null)
            }

            "showNightClock" -> {
                // Debug shortcut; the engine normally launches the clock itself.
                context.startActivity(
                    Intent(context, NightClockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(
                            NightClockActivity.EXTRA_WAKE_LABEL,
                            call.argument<String>("wakeLabel") ?: "--:--",
                        ),
                )
                result.success(null)
            }

            else -> result.notImplemented()
        }
    }
}
