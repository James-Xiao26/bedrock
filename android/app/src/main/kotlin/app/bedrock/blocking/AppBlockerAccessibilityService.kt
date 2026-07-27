package app.bedrock.blocking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Primary foreground-app monitor: event-driven, zero polling cost.
 *
 * Whole-app blocking needs nothing but the foreground package, so the release
 * config still subscribes to window-state events only, with window CONTENT
 * retrieval off - that keeps the Play accessibility declaration surface minimal.
 *
 * Debug builds overlay a config that also enables content retrieval and
 * content-changed events (see `src/debug/res/xml/accessibility_service_config.xml`)
 * for in-app feed blocking. In release those events never arrive and
 * [rootInActiveWindow] is null, so the [FeedDetector] branch below is inert.
 *
 * When blocking is active and a non-allowlisted app comes to the front,
 * the user is bounced to the per-app blocker.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                BlockingController.setLastForeground(pkg)
                FeedDetector.onForegroundChanged(pkg)
                if (BlockingController.shouldBlock(this, pkg)) {
                    BlockingController.bounceToBlocker(this, pkg)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                FeedDetector.onContentChanged(this, pkg)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        FeedDetector.cancel()
        super.onDestroy()
    }

    companion object {
        /** The connected service, or null when the user hasn't enabled it. */
        @Volatile
        var instance: AppBlockerAccessibilityService? = null
            private set
    }
}
