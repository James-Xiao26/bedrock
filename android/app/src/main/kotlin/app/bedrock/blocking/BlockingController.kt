package app.bedrock.blocking

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import app.bedrock.ui.BlockerActivity
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Shared blocking state + the bounce action. Both foreground-app monitors
 * (accessibility service and usage poller) consult this. State lives in its
 * own prefs file (single writer for active/allowed: the engine) so the
 * accessibility service can read it even if it starts before the engine.
 *
 * Per-app passcode grants are stored here too (package -> grant-until epoch ms)
 * and are persisted so a grant survives a mid-window process restart.
 */
object BlockingController {

    private const val TAG = "BlockingController"
    private const val PREFS = "bedrock_blocking"
    private const val KEY_ACTIVE = "active"
    private const val KEY_ALLOWED = "allowed"
    private const val KEY_GRANTS = "grants"

    private data class State(
        val active: Boolean,
        val allowed: Set<String>,
        val grants: Map<String, Long>,
    )

    private val grantSerializer = MapSerializer(String.serializer(), Long.serializer())

    @Volatile
    private var cached: State? = null

    /** Last foreground package the monitors saw; in-memory only (best-effort). */
    @Volatile
    var lastForeground: String? = null
        private set

    /** Set active/allowed. Always clears grants - both calls are window boundaries. */
    fun update(context: Context, active: Boolean, allowed: Set<String>) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putStringSet(KEY_ALLOWED, allowed)
            .remove(KEY_GRANTS)
            .commit()
        cached = State(active, allowed, emptyMap())
        Log.i(TAG, "blocking=${if (active) "ON" else "OFF"} allowed=${allowed.size} pkgs")
    }

    /** Record a passcode grant for [packageName] until [untilEpochMs]. */
    fun grant(context: Context, packageName: String, untilEpochMs: Long) {
        val next = state(context).grants + (packageName to untilEpochMs)
        writeGrants(context, next)
        Log.i(TAG, "granted $packageName until $untilEpochMs")
    }

    /** Drop grants that have expired as of [nowMs]. */
    fun clearExpiredGrants(context: Context, nowMs: Long) {
        val current = state(context).grants
        val next = current.filterValues { it > nowMs }
        if (next.size != current.size) writeGrants(context, next)
    }

    /** The soonest still-active grant expiry after [nowMs], or null if none. */
    fun earliestGrantExpiry(context: Context, nowMs: Long): Long? =
        state(context).grants.values.filter { it > nowMs }.minOrNull()

    fun isActive(context: Context): Boolean = state(context).active

    fun setLastForeground(packageName: String) {
        lastForeground = packageName
    }

    /** True when [packageName] must bounce to the blocker right now. */
    fun shouldBlock(context: Context, packageName: String): Boolean {
        val s = state(context)
        if (!s.active || packageName in s.allowed) return false
        val grantUntil = s.grants[packageName] ?: return true
        return grantUntil <= System.currentTimeMillis()
    }

    fun bounceToBlocker(context: Context, blockedPackage: String) {
        Log.i(TAG, "bouncing $blockedPackage")
        context.startActivity(
            Intent(context, BlockerActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
                .putExtra(BlockerActivity.EXTRA_PACKAGE, blockedPackage),
        )
    }

    private fun writeGrants(context: Context, grants: Map<String, Long>) {
        prefs(context).edit()
            .putString(KEY_GRANTS, Json.encodeToString(grantSerializer, grants))
            .commit()
        cached = state(context).copy(grants = grants)
    }

    private fun state(context: Context): State =
        cached ?: prefs(context).let { p ->
            State(
                active = p.getBoolean(KEY_ACTIVE, false),
                allowed = p.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet(),
                grants = p.getString(KEY_GRANTS, null)
                    ?.let { Json.decodeFromString(grantSerializer, it) }
                    ?: emptyMap(),
            )
        }.also { cached = it }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
