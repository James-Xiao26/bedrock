package app.bedrock.blocking

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import app.bedrock.ui.NightClockActivity

/**
 * Shared blocking state + the bounce action. Both foreground-app monitors
 * (accessibility service and usage poller) consult this. State lives in its
 * own prefs file (single writer: the engine via [update]) so the
 * accessibility service can read it even if it starts before the engine.
 */
object BlockingController {

    private const val TAG = "BlockingController"
    private const val PREFS = "bedrock_blocking"
    private const val KEY_ACTIVE = "active"
    private const val KEY_ALLOWED = "allowed"
    private const val KEY_WAKE_LABEL = "wake_label"

    @Volatile
    private var cached: Pair<Boolean, Set<String>>? = null

    fun update(context: Context, active: Boolean, allowed: Set<String>, wakeLabel: String) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putStringSet(KEY_ALLOWED, allowed)
            .putString(KEY_WAKE_LABEL, wakeLabel)
            .commit()
        cached = active to allowed
        Log.i(TAG, "blocking=${if (active) "ON" else "OFF"} allowed=${allowed.size} pkgs")
    }

    fun isActive(context: Context): Boolean = state(context).first

    /** True when [packageName] must bounce to the night clock. */
    fun shouldBlock(context: Context, packageName: String): Boolean {
        val (active, allowed) = state(context)
        return active && packageName !in allowed
    }

    fun bounceToNightClock(context: Context, blockedPackage: String) {
        Log.i(TAG, "bouncing $blockedPackage")
        context.startActivity(
            Intent(context, NightClockActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
                .putExtra(
                    NightClockActivity.EXTRA_WAKE_LABEL,
                    prefs(context).getString(KEY_WAKE_LABEL, null) ?: "--:--",
                ),
        )
    }

    private fun state(context: Context): Pair<Boolean, Set<String>> =
        cached ?: prefs(context).let {
            (it.getBoolean(KEY_ACTIVE, false) to
                (it.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet()))
        }.also { cached = it }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
