package app.bedrock.blocking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Primary foreground-app monitor: event-driven, zero polling cost.
 * Watches window changes only; window CONTENT is never requested
 * (canRetrieveWindowContent=false in the service XML) - this keeps the
 * Play accessibility declaration surface minimal.
 *
 * When blocking is active and a non-allowlisted app comes to the front,
 * the user is bounced to the per-app blocker.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        BlockingController.setLastForeground(pkg)
        if (BlockingController.shouldBlock(this, pkg)) {
            BlockingController.bounceToBlocker(this, pkg)
        }
    }

    override fun onInterrupt() = Unit
}
