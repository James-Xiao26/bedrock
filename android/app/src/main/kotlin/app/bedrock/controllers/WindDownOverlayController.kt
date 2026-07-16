package app.bedrock.controllers

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager

/**
 * The wind-down cue: a full-screen, untouchable overlay that progressively
 * dims and warms the screen from wind-down start until bedtime. Needs
 * SYSTEM_ALERT_WINDOW; silently does nothing without it (the T-15/T-1
 * notifications still fire).
 */
class WindDownOverlayController(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var startMs = 0L
    private var endMs = 0L

    private val updater = object : Runnable {
        override fun run() {
            val v = view ?: return
            v.setBackgroundColor(colorFor(progress(System.currentTimeMillis())))
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    fun show(windDownStartMs: Long, bedtimeMs: Long) {
        if (view != null) return
        if (!Settings.canDrawOverlays(context)) {
            Log.i(TAG, "no overlay permission; skipping wind-down dim")
            return
        }
        startMs = windDownStartMs
        endMs = bedtimeMs

        val wm = context.getSystemService(WindowManager::class.java)
        val v = View(context)
        v.setBackgroundColor(colorFor(progress(System.currentTimeMillis())))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        wm.addView(v, params)
        view = v
        handler.post(updater)
        Log.i(TAG, "wind-down overlay shown")
    }

    fun hide() {
        handler.removeCallbacks(updater)
        view?.let {
            context.getSystemService(WindowManager::class.java).removeView(it)
            Log.i(TAG, "wind-down overlay removed")
        }
        view = null
    }

    private fun progress(nowMs: Long): Float = when {
        endMs <= startMs -> 1f
        else -> ((nowMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
    }

    /** Warm, darkening veil: transparent at start, amber-black by bedtime. */
    private fun colorFor(progress: Float): Int {
        val alpha = (progress * MAX_ALPHA * 255).toInt()
        return Color.argb(alpha, 40, 20, 0)
    }

    private companion object {
        const val TAG = "WindDownOverlay"
        const val UPDATE_INTERVAL_MS = 30_000L
        const val MAX_ALPHA = 0.55f
    }
}
