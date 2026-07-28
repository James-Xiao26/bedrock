package app.bedrock.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.bedrock.billing.BillingManager
import app.bedrock.blocking.Allowlist
import app.bedrock.blocking.BlockingController
import app.bedrock.channel.EngineEventStreamer
import app.bedrock.service.AlarmScheduler
import app.bedrock.service.LockdownForegroundService
import app.bedrock.ui.BlockerActivity
import app.bedrock.widget.BedrockWidget
import app.bedrock.widget.WidgetStatus
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
    private val scheduler = AlarmScheduler(context)
    val billing = BillingManager(context, ::onBypassPurchased)

    /** Set by the blocker while it is showing so a $1 code reset can reveal the new code. */
    @Volatile
    var onCodeReset: ((String) -> Unit)? = null

    /**
     * What a completed $1 purchase should do, set by the blocker that launched
     * it. Null means "rotate and reveal the code", the downtime behaviour and
     * the safe default: [BillingManager.reconcile] settles purchases that
     * completed while no blocker was showing, and that must never strand a
     * paid user.
     */
    @Volatile
    var onPaidBypass: (() -> Unit)? = null

    init {
        // Crash between purchase and consume must never strand a paid user.
        billing.reconcile()
        // Stats are gone; drop the window log older installs still carry.
        context.getSharedPreferences("bedrock_engine", Context.MODE_PRIVATE)
            .edit().remove("window_records").apply()
    }

    @Synchronized
    fun evaluate(nowMs: Long = System.currentTimeMillis()) {
        val snapshot = store.snapshot()
        val config = store.activeConfig()

        if (snapshot.state == SessionState.ACTIVE) {
            val window = snapshot.window
            if (window == null) {
                dispatch(SessionEvent.WindowCloseDue)
                evaluate(nowMs)
                return
            }
            // Real time left, judged against the monotonic anchor so a forward
            // wall-clock edit can't end the window early.
            val remaining = WindowClock.remainingMs(window, nowMs, SystemClock.elapsedRealtime())
            if (remaining <= 0) {
                // A healthy engine closes on time via the WINDOW_CLOSE alarm.
                // Being well past close on a normal evaluate means that alarm
                // never fired - enforcement was down (force-stop cancels alarms) -
                // so the night is a violation, not a clean close. Legitimate
                // reboot/update catch-up is routed through recover() instead.
                val gapped = nowMs - window.closeEpochMs > GAP_SLACK_MS
                dispatch(if (gapped) SessionEvent.ViolationDetected else SessionEvent.WindowCloseDue)
                evaluate(nowMs) // plan the next window
                return
            }
            // Schedule off remaining real time, not the stored wall instant: under
            // a forward clock edit the true close is `remaining` from now.
            scheduler.scheduleBoundaries(nowMs, AlarmScheduler.ACTION_WINDOW_CLOSE to nowMs + remaining)
            return
        }

        // IDLE: find the next (or in-progress) window.
        val next = NightPlanner.nextNight(nowMs, zone(), config)
        if (next == null) {
            scheduler.cancelAll()
            return
        }
        if (nowMs >= next.openEpochMs) {
            dispatch(SessionEvent.WindowOpenDue(anchor(next.toContext(), nowMs)))
            store.snapshot().window?.let {
                scheduler.scheduleBoundaries(nowMs, AlarmScheduler.ACTION_WINDOW_CLOSE to it.closeEpochMs)
            }
        } else {
            scheduler.scheduleBoundaries(
                nowMs,
                AlarmScheduler.ACTION_WINDOW_OPEN to next.openEpochMs,
                AlarmScheduler.ACTION_WINDOW_CLOSE to next.closeEpochMs,
            )
            scheduler.scheduleReminder(
                nowMs,
                next.openEpochMs - AlarmScheduler.REMINDER_LEAD_MS,
            )
        }
    }

    /**
     * Reboot / app-update recovery. The monotonic anchor resets with the uptime
     * clock across a reboot, so re-derive from the (authoritative) boot wall
     * clock: close cleanly if the window already ended while the phone was off,
     * else re-anchor and carry on. Distinct from [evaluate] so the missed-close
     * gap check there never mistakes a legitimate powered-off stretch for a
     * force-stop.
     */
    @Synchronized
    fun recover(nowMs: Long = System.currentTimeMillis()) {
        val snap = store.snapshot()
        val window = snap.window
        if (snap.state == SessionState.ACTIVE && window != null) {
            if (nowMs >= window.closeEpochMs) {
                dispatch(SessionEvent.WindowCloseDue)
            } else {
                store.saveSnapshot(snap.copy(window = anchor(window, nowMs)))
            }
        }
        evaluate(nowMs)
    }

    /**
     * The guard service found it can't enforce (no accessibility service and no
     * usage-access fallback). Flag the running window failed but keep it ACTIVE -
     * ending downtime here would reward disabling the monitor.
     */
    @Synchronized
    fun reportEnforcementGap() {
        if (store.snapshot().state == SessionState.ACTIVE) dispatch(SessionEvent.GapDetected)
    }

    /** Stamp a window's monotonic anchor at the moment it opens. */
    private fun anchor(window: WindowContext, nowMs: Long): WindowContext =
        WindowClock.anchor(window, nowMs, SystemClock.elapsedRealtime())

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
        store.update(requested)
        // The freeze only bites during an active window; when nothing is
        // blocking, loosening changes (allowlist additions) apply immediately.
        if (!store.snapshot().blocking) store.mergePending()
        EngineEventStreamer.emit("configChanged")
        evaluate(nowMs)
        BedrockWidget.refresh(context)
        return ChangeClassifier.Result(store.activeConfig(), store.pendingPatch())
    }

    /**
     * Grant one app more time. Called by the blocker after a correct passcode.
     * Marks the window unclean and (re)arms the expiry alarm.
     *
     * Valid during a downtime window, and also whenever feed blocking is on,
     * since that runs independently of windows and its blocker needs the same
     * escape hatch. A grant taken outside a window dirties no window.
     */
    @Synchronized
    fun grantApp(pkg: String, kind: GrantKind, nowMs: Long = System.currentTimeMillis()) {
        val snapshot = store.snapshot()
        val inWindow = snapshot.state == SessionState.ACTIVE
        if (!inWindow && !BlockingController.isFeedBlocking(context)) return
        val until = when (kind) {
            GrantKind.FIVE_MIN -> nowMs + 5 * 60_000L
            GrantKind.FIFTEEN_MIN -> nowMs + 15 * 60_000L
            GrantKind.REST_OF_WINDOW -> snapshot.window?.closeEpochMs ?: (nowMs + 15 * 60_000L)
        }
        BlockingController.grant(context, pkg, until)
        if (inWindow && !snapshot.grantUsed) store.saveSnapshot(snapshot.copy(grantUsed = true))
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

    /**
     * In-app feed blocking, the user's own switch. Deliberately not part of
     * [BedrockConfig]: it is not schedule state, the freeze rules do not apply,
     * and the accessibility service needs to read it without the engine running.
     * Routed through here anyway so the engine stays the single writer to
     * [BlockingController].
     */
    @Synchronized
    fun setFeedBlocking(on: Boolean) {
        BlockingController.setFeedBlocking(context, on)
    }

    fun isFeedBlocking(): Boolean = BlockingController.isFeedBlocking(context)

    /**
     * Feed blocking for the settings screen: whether it's on right now, plus
     * when a requested switch-off lands (0 when none is pending).
     */
    fun feedBlockingView(): Map<String, Any?> = mapOf(
        "on" to BlockingController.isFeedBlocking(context),
        "offAtMs" to BlockingController.feedOffAt(context),
    )

    private fun rescheduleGrantExpiry(nowMs: Long) {
        BlockingController.earliestGrantExpiry(context, nowMs)?.let {
            scheduler.scheduleOne(AlarmScheduler.ACTION_GRANT_EXPIRY, it)
        }
    }

    /** Passcode check for the blocker. No side effects - the code is reusable. */
    fun checkPasscode(input: String): Boolean =
        // ponytail: debug-only test backdoor; BuildConfig.DEBUG gates it out of release.
        (app.bedrock.BuildConfig.DEBUG && input.trim() == "00000") ||
            HardcorePassword.matches(input, store.hardcorePassword())

    /**
     * The passcode, but only when the app is allowed to reveal it: never while
     * a window is actively blocking (that would defeat the blocker). Returns the
     * flag plus the code (null when hidden) for the settings screen.
     */
    @Synchronized
    fun hardcorePasswordView(): Map<String, Any?> {
        val viewable = passwordViewable()
        return mapOf(
            "viewable" to viewable,
            "password" to if (viewable) store.hardcorePassword() else null,
        )
    }

    /** Rotate the code and reveal it to the waiting blocker; returns the new code. */
    private fun rotateAndReveal(): String {
        val fresh = store.rotateHardcorePassword()
        onCodeReset?.invoke(fresh)
        return fresh
    }

    /** Paid bypass ($1). The feed blocker grants time instead of touching the code. */
    private fun onBypassPurchased() {
        val handler = onPaidBypass
        if (handler != null) handler() else rotateAndReveal()
    }

    /**
     * Free reset: rotate the code and return the new one. Gated in the UI by the
     * [Acknowledgement] transcription, not by payment. Like the paid path this
     * rotates even while blocking - that is the point of a reset.
     */
    @Synchronized
    fun resetCodeFree(): String = rotateAndReveal()

    private fun passwordViewable(): Boolean = !store.snapshot().blocking

    /**
     * On-demand downtime the user toggles from the Downtime screen. Only acts
     * outside a scheduled window: starts blocking now (when IDLE) or ends a
     * running manual session (when ACTIVE and manual). A scheduled window is
     * left untouched - that's what the schedule and escape code govern.
     */
    @Synchronized
    fun setManualDowntime(on: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val snapshot = store.snapshot()
        if (on) {
            if (snapshot.state != SessionState.IDLE) return
            // On-demand downtime ends when the next scheduled window ends (the
            // user's usual wake time), so turning it on early just brings
            // tonight's downtime forward.
            // ponytail: 24h ceiling as the fallback/safety cap - used when no
            // window is scheduled ahead, or one is absurdly far off, so a
            // forgotten manual session can't block forever.
            val cap = nowMs + 24 * 60 * 60_000L
            val close = NightPlanner.nextNight(nowMs, zone(), store.activeConfig())
                ?.closeEpochMs?.coerceAtMost(cap) ?: cap
            val ctx = anchor(
                WindowContext(
                    windowKey = "manual-" + Instant.ofEpochMilli(nowMs),
                    openEpochMs = nowMs,
                    closeEpochMs = close,
                    manual = true,
                ),
                nowMs,
            )
            dispatch(SessionEvent.WindowOpenDue(ctx))
            scheduler.scheduleBoundaries(nowMs, AlarmScheduler.ACTION_WINDOW_CLOSE to ctx.closeEpochMs)
        } else {
            if (snapshot.state != SessionState.ACTIVE || snapshot.window?.manual != true) return
            dispatch(SessionEvent.WindowCloseDue)
            evaluate(nowMs) // hand off to the schedule if a scheduled window is now due
        }
    }

    /** Debug builds only: open a window now and close it after a few seconds. */
    @Synchronized
    fun startDemoSession(windDownSeconds: Int, sleepSeconds: Int) {
        val nowMs = System.currentTimeMillis()
        val ctx = anchor(
            WindowContext(
                windowKey = "demo-" + Instant.ofEpochMilli(nowMs),
                openEpochMs = nowMs,
                closeEpochMs = nowMs + (windDownSeconds + sleepSeconds) * 1000L,
            ),
            nowMs,
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
                BedrockWidget.refresh(context)
            }
            Effect.MergePendingConfig -> {
                store.mergePending()
                EngineEventStreamer.emit("configChanged")
            }
            // ponytail: nothing is persisted - the stats screen is gone and the
            // app keeps no history. Log only, for debugging a window close.
            is Effect.RecordWindow ->
                Log.i(TAG, "window ${effect.window?.windowKey}: ${effect.outcome}")
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

    /** Current window if blocking, else the next planned one - for the home-screen widget. */
    @Synchronized
    fun widgetStatus(nowMs: Long = System.currentTimeMillis()): WidgetStatus {
        val snap = store.snapshot()
        val window = snap.window
        if (snap.state == SessionState.ACTIVE && window != null) {
            return WidgetStatus(active = true, manual = window.manual,
                openMs = window.openEpochMs, closeMs = window.closeEpochMs)
        }
        val next = NightPlanner.nextNight(nowMs, zone(), store.activeConfig())
        return WidgetStatus(active = false, manual = false,
            openMs = next?.openEpochMs, closeMs = next?.closeEpochMs)
    }

    private fun zone(): ZoneId = ZoneId.systemDefault()

    companion object {
        private const val TAG = "BedrockEngine"

        /**
         * How far past a window's close a normal evaluate may land before it
         * counts as a coverage gap rather than clean-close jitter. The exact,
         * Doze-exempt close alarm fires within seconds, so this only forgives
         * scheduling slack; a real force-stop lands minutes-to-hours late.
         */
        private const val GAP_SLACK_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: BedrockEngine? = null

        fun get(context: Context): BedrockEngine =
            instance ?: synchronized(this) {
                instance ?: BedrockEngine(context.applicationContext).also { instance = it }
            }
    }
}
