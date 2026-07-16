package app.bedrock.channel

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel

/**
 * Single sink for engine -> Dart events ("bedrock/events").
 * The engine emits from arbitrary threads; events are trampolined to main.
 * Dart never assumes this stream has been alive since boot - it re-syncs
 * via getSessionState()/getConfig() on every resume.
 */
object EngineEventStreamer : EventChannel.StreamHandler {

    private val main = Handler(Looper.getMainLooper())
    private var sink: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        sink = events
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    fun emit(name: String, payload: Map<String, Any?> = emptyMap()) {
        main.post {
            sink?.success(mapOf("event" to name, "payload" to payload))
        }
    }
}
