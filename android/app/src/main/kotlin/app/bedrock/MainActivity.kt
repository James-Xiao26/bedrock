package app.bedrock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import app.bedrock.channel.EngineEventStreamer
import app.bedrock.channel.EngineMethodHandler
import app.bedrock.engine.BedrockEngine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.renderer.FlutterUiDisplayListener
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    /** Held while the POST_NOTIFICATIONS system dialog is up (see below). */
    private var pendingNotifResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        val engineHandler = EngineMethodHandler(applicationContext)
        // requestNotifications needs the Activity (runtime permission dialog);
        // everything else is context-only and handled by the engine handler.
        MethodChannel(messenger, "bedrock/engine").setMethodCallHandler { call, result ->
            if (call.method == "requestNotifications") {
                requestNotifications(result)
            } else {
                engineHandler.onMethodCall(call, result)
            }
        }
        EventChannel(messenger, "bedrock/events")
            .setStreamHandler(EngineEventStreamer)

        // Catch up and (re)post boundary alarms on every app open, but defer it
        // until the first frame is drawn. Past bedtime this synchronously starts
        // the lockdown foreground service; running it during the contended cold
        // start starves LockdownForegroundService.startForeground() past Android's
        // start-in-time window (ForegroundServiceDidNotStartInTimeException). By
        // first frame the main thread has drained and the service promotes at once.
        // Safe to defer: the Dart UI re-reads session state off the engine event
        // stream, so a frame-later catch-up just refreshes it.
        val renderer = flutterEngine.renderer
        renderer.addIsDisplayingFlutterUiListener(object : FlutterUiDisplayListener {
            override fun onFlutterUiDisplayed() {
                renderer.removeIsDisplayingFlutterUiListener(this)
                BedrockEngine.get(applicationContext).evaluate()
            }

            override fun onFlutterUiNoLongerDisplayed() = Unit
        })
    }

    /**
     * Request POST_NOTIFICATIONS. On API < 33 notifications are on by default,
     * so this is an immediate yes. The result is delivered from
     * [onRequestPermissionsResult]. FlutterActivity is a plain Activity (not an
     * androidx ComponentActivity), so we use the classic permission callback
     * rather than registerForActivityResult.
     */
    private fun requestNotifications(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            result.success(true)
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            result.success(true)
            return
        }
        pendingNotifResult = result
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            pendingNotifResult?.success(granted)
            pendingNotifResult = null
        }
    }

    private companion object {
        const val REQ_NOTIF = 4711
    }
}
