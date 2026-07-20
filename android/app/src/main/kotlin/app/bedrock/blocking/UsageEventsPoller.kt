package app.bedrock.blocking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Fallback foreground-app monitor for users who decline the accessibility
 * service: polls UsageEvents from the guard service while blocking is
 * active. Bounce latency is one poll interval (~800 ms) instead of instant.
 */
class UsageEventsPoller(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastQueryEnd = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            poll()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastQueryEnd = System.currentTimeMillis() - INTERVAL_MS
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun poll() {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQueryEnd, now)
        lastQueryEnd = now

        var latest: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latest = event.packageName
            }
        }
        latest?.let {
            BlockingController.setLastForeground(it)
            if (BlockingController.shouldBlock(context, it)) {
                BlockingController.bounceToBlocker(context, it)
            }
        }
    }

    private companion object {
        const val INTERVAL_MS = 800L
    }
}
