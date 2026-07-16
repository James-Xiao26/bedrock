package app.bedrock

import app.bedrock.channel.EngineEventStreamer
import app.bedrock.channel.EngineMethodHandler
import app.bedrock.engine.BedrockEngine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
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
        // Catch up and (re)post boundary alarms on every app open.
        BedrockEngine.get(applicationContext).evaluate()
    }
}
