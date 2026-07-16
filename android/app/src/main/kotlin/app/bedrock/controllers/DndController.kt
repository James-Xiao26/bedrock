package app.bedrock.controllers

import android.app.NotificationManager
import android.content.Context
import android.util.Log

/**
 * Turns Do Not Disturb on at bedtime and restores the user's previous state
 * at wake. Calls ring through: we switch to PRIORITY mode and widen the
 * priority policy to allow calls from anyone, then put the user's policy
 * back afterwards. No-ops without notification-policy access (optional
 * permission) or when the user already runs their own DND.
 */
class DndController(private val context: Context) {

    private val prefs = context.getSharedPreferences("bedrock_dnd", Context.MODE_PRIVATE)

    fun enable() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.i(TAG, "no DND access; skipping")
            return
        }
        val current = nm.currentInterruptionFilter
        if (current != NotificationManager.INTERRUPTION_FILTER_ALL) {
            Log.i(TAG, "user already in DND ($current); leaving it alone")
            return
        }
        val previousPolicy = nm.notificationPolicy
        prefs.edit()
            .putBoolean(KEY_CHANGED, true)
            .putInt(KEY_PREV_FILTER, current)
            .putInt(KEY_PREV_CATEGORIES, previousPolicy.priorityCategories)
            .putInt(KEY_PREV_CALL_SENDERS, previousPolicy.priorityCallSenders)
            .putInt(KEY_PREV_MSG_SENDERS, previousPolicy.priorityMessageSenders)
            .commit()

        nm.notificationPolicy = NotificationManager.Policy(
            NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
            NotificationManager.Policy.PRIORITY_SENDERS_ANY,
            previousPolicy.priorityMessageSenders,
        )
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        Log.i(TAG, "DND enabled (calls ring through)")
    }

    fun restore() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        if (!prefs.getBoolean(KEY_CHANGED, false)) return

        nm.notificationPolicy = NotificationManager.Policy(
            prefs.getInt(KEY_PREV_CATEGORIES, 0),
            prefs.getInt(
                KEY_PREV_CALL_SENDERS,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
            ),
            prefs.getInt(
                KEY_PREV_MSG_SENDERS,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
            ),
        )
        nm.setInterruptionFilter(
            prefs.getInt(KEY_PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL),
        )
        prefs.edit().putBoolean(KEY_CHANGED, false).commit()
        Log.i(TAG, "DND restored")
    }

    private companion object {
        const val TAG = "DndController"
        const val KEY_CHANGED = "changed"
        const val KEY_PREV_FILTER = "prev_filter"
        const val KEY_PREV_CATEGORIES = "prev_categories"
        const val KEY_PREV_CALL_SENDERS = "prev_call_senders"
        const val KEY_PREV_MSG_SENDERS = "prev_msg_senders"
    }
}
