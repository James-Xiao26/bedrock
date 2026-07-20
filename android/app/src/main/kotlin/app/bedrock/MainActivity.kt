package app.bedrock

import app.bedrock.channel.EngineEventStreamer
import app.bedrock.channel.EngineMethodHandler
import app.bedrock.engine.BedrockEngine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.renderer.FlutterUiDisplayListener
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        MethodChannel(messenger, "bedrock/engine")
            .setMethodCallHandler(EngineMethodHandler(applicationContext))
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
}
