package app.bedrock.engine

import android.content.Context
import android.util.Log
import app.bedrock.billing.BillingManager
import app.bedrock.blocking.Allowlist
import app.bedrock.blocking.BlockingController
import app.bedrock.channel.EngineEventStreamer
import app.bedrock.service.AlarmScheduler
import app.bedrock.service.LockdownForegroundService
import app.bedrock.ui.BlockerActivity
import java.time.Instant
import java.time.ZoneId

/** How much time a passcode grant buys for one app. */
enum class GrantKind { FIVE_MIN, FIFTEEN_MIN, REST_OF_WINDOW }

/**
 * Orchestrator: owns the persisted snapshot, feeds events into the pure
 * state machine, executes the resulting effects, and keeps the window
 * boundary alarms posted. Entry points (channel, receivers, alarms) all
 * converge on [evaluate], which re-derives the correct state from wall clock
 * + snapshot, making every path idempotent.
 */
class BedrockEngine private constructor(private val context: Context) {

    val store = ConfigStore(context)
    val stats = StatsStore(context)
    private val scheduler = AlarmScheduler(context)
    val billing = BillingManager(context, ::onBypassPurchased)

    /** Set by the blocker while it is showing so a $1 code reset can reveal the new code. */
    @Volatile
    var onCodeReset: ((String) -> Unit)? = null

    init {
        // Crash between purchase and consume must never strand a paid user.
        billing.reconcile()
    }

    @Synchronized
    fun evaluate(nowMs: Long = System.currentTimeMillis()) {
        val snapshot = store.snapshot()
        val config = store.activeConfig()

        if (snapshot.state == SessionState.ACTIVE) {
            val window = snapshot.window
            if (window == null || nowMs >= window.closeEpochMs) {
                dispatch(SessionEvent.WindowCloseDue)
                evaluate(nowMs) // plan the next window
                return
            }
            scheduler.scheduleBoundaries(nowMs, AlarmScheduler.ACTION_WINDOW_CLOSE to window.closeEpochMs)
            return
        }

        // IDLE: find the next (or in-progress) window.
        val next = NightPlanner.nextNight(nowMs, zone(), config)
        if (next == null) {
            scheduler.cancelAll()
            return
        }
        if (nowMs >= next.openEpochMs) {
            dispatch(SessionEvent.WindowOpenDue(next.toContext()))
            store.snapshot().window?.let {
                scheduler.scheduleBoundaries(nowMs, AlarmScheduler.ACTION_WINDOW_CLOSE to it.closeEpochMs)
            }
        } else {
            scheduler.scheduleBoundaries(
                nowMs,
                AlarmScheduler.ACTION_WINDOW_OPEN to next.openEpochMs,
                AlarmScheduler.ACTION_WINDOW_CLOSE to next.closeEpochMs,
            )
        }
    }

    @Synchronized
    fun dispatch(event: SessionEvent): Snapshot {
        val before = store.snapshot()
        val (after, effects) = SessionStateMachine.transition(before, event)
        Log.i(TAG, "$event: ${before.state} -> ${after.state} effects=$effects")
        for (effect in effects) execute(effect, after)
        return after
    }

    @Synchronized
    fun updateConfig(requested: ConfigPatch, nowMs: Long = System.currentTimeMillis()): ChangeClassifier.Result {
        val config = store.activeConfig()
        val tonight = NightPlanner.nextNight(nowMs, zone(), config)?.day
            ?: Instant.ofEpochMilli(nowMs).atZone(zone()).dayOfWeek.value
        val result = store.update(requested, tonight)
        EngineEventStreamer.emit("configChanged")
        evaluate(nowMs)
        return result
    }

    /**
     * Grant one app more time this window. Called by the blocker after a
     * correct passcode. Marks the window unclean and (re)arms the expiry alarm.
     */
    @Synchronized
    fun grantApp(pkg: String, kind: GrantKind, nowMs: Long = System.currentTimeMillis()) {
        val snapshot = store.snapshot()
        if (snapshot.state != SessionState.ACTIVE) return
        val until = when (kind) {
            GrantKind.FIVE_MIN -> nowMs + 5 * 60_000L
            GrantKind.FIFTEEN_MIN -> nowMs + 15 * 60_000L
            GrantKind.REST_OF_WINDOW -> snapshot.window?.closeEpochMs ?: (nowMs + 15 * 60_000L)
        }
        BlockingController.grant(context, pkg, until)
        if (!snapshot.grantUsed) store.saveSnapshot(snapshot.copy(grantUsed = true))
        rescheduleGrantExpiry(nowMs)
    }

    /** The grant-expiry alarm: re-block an app the user is still sitting in. */
    @Synchronized
    fun onGrantExpiry(nowMs: Long = System.currentTimeMillis()) {
        BlockingController.clearExpiredGrants(context, nowMs)
        val fg = BlockingController.lastForeground
        if (fg != null && BlockingController.shouldBlock(context, fg)) {
            BlockingController.bounceToBlocker(context, fg)
        }
        rescheduleGrantExpiry(nowMs)
    }

    private fun rescheduleGrantExpiry(nowMs: Long) {
        BlockingController.earliestGrantExpiry(context, nowMs)?.let {
            scheduler.scheduleOne(AlarmScheduler.ACTION_GRANT_EXPIRY, it)
        }
    }

    /** Passcode check for the blocker. No side effects - the code is reusable. */
    fun checkPasscode(input: String): Boolean =
        HardcorePassword.matches(input, store.hardcorePassword())

    /**
     * The passcode, but only when the app is allowed to reveal it: before
     * today's cutoff and never while a window is actively blocking. Returns
     * the flag plus the code (null when hidden) for the settings screen.
     */
    @Synchronized
    fun hardcorePasswordView(nowMs: Long = System.currentTimeMillis()): Map<String, Any?> {
        val viewable = passwordViewable(nowMs)
        return mapOf(
            "viewable" to viewable,
            "password" to if (viewable) store.hardcorePassword() else null,
        )
    }

    @Synchronized
    fun regenerateHardcorePassword(nowMs: Long = System.currentTimeMillis()): Map<String, Any?> {
        if (!passwordViewable(nowMs)) return mapOf("viewable" to false, "password" to null)
        return mapOf("viewable" to true, "password" to store.rotateHardcorePassword())
    }

    /** Paid reset ($1): rotate the code and reveal it to the waiting blocker. */
    private fun onBypassPurchased() {
        val fresh = store.rotateHardcorePassword()
        onCodeReset?.invoke(fresh)
    }

    private fun passwordViewable(nowMs: Long): Boolean =
        !store.snapshot().blocking &&
            localMinutes(nowMs) < store.activeConfig().passwordViewCutoffMinutes

    private fun localMinutes(nowMs: Long): Int =
        Instant.ofEpochMilli(nowMs).atZone(zone()).toLocalTime().let { it.hour * 60 + it.minute }

    /** Debug builds only: open a window now and close it after a few seconds. */
    @Synchronized
    fun startDemoSession(windDownSeconds: Int, sleepSeconds: Int) {
        val nowMs = System.currentTimeMillis()
        val ctx = WindowContext(
            windowKey = "demo-" + Instant.ofEpochMilli(nowMs),
            openEpochMs = nowMs,
            closeEpochMs = nowMs + (windDownSeconds + sleepSeconds) * 1000L,
        )
        dispatch(SessionEvent.WindowOpenDue(ctx))
        scheduler.scheduleBoundaries(nowMs, AlarmScheduler.ACTION_WINDOW_CLOSE to ctx.closeEpochMs)
    }

    private fun execute(effect: Effect, snapshot: Snapshot) {
        when (effect) {
            Effect.StartGuardService -> LockdownForegroundService.start(context)
            Effect.StopGuardService -> LockdownForegroundService.stop(context)
            Effect.PersistAndBroadcast -> {
                store.saveSnapshot(snapshot)
                EngineEventStreamer.emit(
                    "sessionStateChanged",
                    mapOf("state" to snapshot.state.name),
                )
            }
            Effect.MergePendingConfig -> {
                store.mergePending()
                EngineEventStreamer.emit("configChanged")
            }
            is Effect.RecordWindow -> {
                Log.i(TAG, "window ${effect.window?.windowKey}: ${effect.outcome}")
                effect.window?.let {
                    stats.record(
                        WindowRecord(it.windowKey, it.openEpochMs, it.closeEpochMs, effect.outcome.name),
                    )
                }
            }
            Effect.StartBlocking -> {
                BlockingController.update(
                    context,
                    active = true,
                    allowed = Allowlist(context).resolve(store.activeConfig().allowlist),
                )
                // Poke the running service so it syncs its monitors.
                LockdownForegroundService.start(context)
            }
            Effect.StopBlocking -> {
                BlockingController.update(context, active = false, allowed = emptySet())
                BlockerActivity.closeIfShowing()
            }
        }
    }

    private fun zone(): ZoneId = ZoneId.systemDefault()

    companion object {
        private const val TAG = "BedrockEngine"

        @Volatile
        private var instance: BedrockEngine? = null

        fun get(context: Context): BedrockEngine =
            instance ?: synchronized(this) {
                instance ?: BedrockEngine(context.applicationContext).also { instance = it }
            }
    }
}
