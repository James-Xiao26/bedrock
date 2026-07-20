package app.bedrock.channel

import android.content.Context
import app.bedrock.BuildConfig
import app.bedrock.engine.BedrockEngine
import app.bedrock.engine.ConfigPatch
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

            "getStats" -> result.success(engine.stats.summary().toWire())

            "getHardcorePassword" -> result.success(engine.hardcorePasswordView())

            "regenerateHardcorePassword" -> result.success(engine.regenerateHardcorePassword())

            "getSessionState" -> {
                val snapshot = engine.store.snapshot()
                result.success(
                    mapOf(
                        "state" to snapshot.state.name,
                        "blocking" to snapshot.blocking,
                        "windowOpen" to snapshot.window?.openEpochMs,
                        "windowClose" to snapshot.window?.closeEpochMs,
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

            else -> result.notImplemented()
        }
    }
}
