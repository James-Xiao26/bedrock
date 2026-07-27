package app.bedrock.channel

import android.content.Context
import app.bedrock.BuildConfig
import app.bedrock.blocking.Allowlist
import app.bedrock.blocking.FeedDetector
import app.bedrock.blocking.InstalledApps
import app.bedrock.blocking.Permissions
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

            "getInstalledApps" -> result.success(InstalledApps(context).list())

            // Packages the blocker never blocks regardless of the user's list
            // (dialer, launcher, Settings, Play Store, Bedrock). The picker shows
            // the launchable ones as a fixed, non-removable "always allowed" group.
            "getSystemAllowlist" ->
                result.success(Allowlist(context).resolve(emptySet()).toList())

            "getPermissions" -> result.success(Permissions.status(context))

            "openAccessibilitySettings" -> {
                Permissions.openAccessibilitySettings(context)
                result.success(null)
            }

            "openUsageAccessSettings" -> {
                Permissions.openUsageAccessSettings(context)
                result.success(null)
            }

            "openOverlaySettings" -> {
                Permissions.openOverlaySettings(context)
                result.success(null)
            }

            "openNotificationSettings" -> {
                Permissions.openNotificationSettings(context)
                result.success(null)
            }

            "openBedtimeSettings" -> {
                Permissions.openBedtimeSettings(context)
                result.success(null)
            }

            "isOnboarded" -> result.success(engine.store.isOnboarded())

            "markOnboarded" -> {
                engine.store.markOnboarded()
                result.success(null)
            }

            "getDisplayName" -> result.success(engine.store.displayName())

            "setDisplayName" -> {
                engine.store.setDisplayName(call.argument<String>("name") ?: "")
                result.success(null)
            }

            "getBypassHoldSeconds" -> result.success(engine.store.bypassHoldSeconds())

            "setBypassHoldSeconds" -> {
                // Dart always sends a value; the store floors it to the minimum.
                call.argument<Int>("seconds")?.let { engine.store.setBypassHoldSeconds(it) }
                result.success(null)
            }

            "getStats" -> result.success(engine.stats.summary().toWire())

            "getHardcorePassword" -> result.success(engine.hardcorePasswordView())

            "getSessionState" -> {
                val snapshot = engine.store.snapshot()
                result.success(
                    mapOf(
                        "state" to snapshot.state.name,
                        "blocking" to snapshot.blocking,
                        "windowOpen" to snapshot.window?.openEpochMs,
                        "windowClose" to snapshot.window?.closeEpochMs,
                        "manual" to (snapshot.window?.manual ?: false),
                    ),
                )
            }

            "setManualDowntime" -> {
                engine.setManualDowntime(call.argument<Boolean>("on") ?: false)
                result.success(null)
            }

            "getFeedBlocking" -> result.success(engine.isFeedBlocking())

            "setFeedBlocking" -> {
                engine.setFeedBlocking(call.argument<Boolean>("on") ?: false)
                result.success(null)
            }

            // Capture real view IDs from the app currently on screen, so the
            // fingerprints in FeedRules come from installed versions rather than
            // from guesswork. Debug-only: it is the one call that returns another
            // app's window contents to Dart.
            "dumpWindowIds" -> {
                if (!BuildConfig.DEBUG) {
                    result.error("debug_only", "Window dumps exist only in debug builds", null)
                    return
                }
                result.success(FeedDetector.dumpIds())
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
