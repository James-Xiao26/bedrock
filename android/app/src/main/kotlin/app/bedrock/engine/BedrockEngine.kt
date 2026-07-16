package app.bedrock.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import app.bedrock.billing.BillingManager
import app.bedrock.blocking.Allowlist
import app.bedrock.blocking.BlockingController
import app.bedrock.channel.EngineEventStreamer
import app.bedrock.controllers.DndController
import app.bedrock.controllers.GrayscaleController
import app.bedrock.controllers.WindDownOverlayController
import app.bedrock.service.AlarmScheduler
import app.bedrock.service.LockdownForegroundService
import app.bedrock.ui.EscapeFlowActivity
import app.bedrock.ui.NightClockActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Orchestrator: owns the persisted snapshot, feeds events into the pure
 * state machine, executes the resulting effects, and keeps boundary alarms
 * posted. Entry points (channel, receivers, alarms) all converge on
 * [evaluate], which re-derives the correct state from wall clock + snapshot,
 * making every path idempotent.
 */
class BedrockEngine private constructor(private val context: Context) {

    val store = ConfigStore(context)
    private val scheduler = AlarmScheduler(context)
    private val dnd = DndController(context)
    private val overlay = WindDownOverlayController(context)
    val grayscale = GrayscaleController(context)
    val billing = BillingManager(context) { dispatch(SessionEvent.BypassPurchased) }

    init {
        // Crash between purchase and consume must never strand a paid user.
        billing.reconcile()
    }

    @Synchronized
    fun evaluate(nowMs: Long = System.currentTimeMillis()) {
        var snapshot = store.snapshot()
        val config = store.activeConfig()

        if (snapshot.state == SessionState.IDLE) {
            val night = NightPlanner.nextNight(nowMs, zone(), config)
            if (night == null) {
                scheduler.cancelAll()
                return
            }
            val ctx = night.toContext(config)
            snapshot = when {
                nowMs >= night.bedEpochMs -> dispatch(SessionEvent.BedtimeDue(ctx))
                nowMs >= night.windDownEpochMs -> dispatch(SessionEvent.WindDownDue(ctx))
                else -> snapshot
            }
            scheduleFor(snapshot, nowMs, night.windDownEpochMs, night.bedEpochMs, night.wakeEpochMs)
        }

        // A dispatch above may have started (or a previous run left) an active
        // night; catch up against ITS OWN context, not tonight's plan.
        val night = snapshot.night
        if (snapshot.state != SessionState.IDLE && night != null) {
            if (nowMs >= night.wakeEpochMs && snapshot.state != SessionState.WAKE_ALARM) {
                dispatch(SessionEvent.WakeDue)
                // Night ended (or alarm started ringing); plan the next one.
                if (store.snapshot().state == SessionState.IDLE) {
                    evaluate(nowMs)
                    return
                }
            } else if (snapshot.state == SessionState.WINDDOWN && nowMs >= night.bedEpochMs) {
                dispatch(SessionEvent.BedtimeDue(night))
            }
            val current = store.snapshot()
            current.night?.let {
                scheduleFor(current, nowMs, it.bedEpochMs - windDownMillis(), it.bedEpochMs, it.wakeEpochMs)
            }
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

    /** Debug builds only: run a whole night in about a minute. */
    @Synchronized
    fun startDemoSession(windDownSeconds: Int, sleepSeconds: Int) {
        val nowMs = System.currentTimeMillis()
        val config = store.activeConfig()
        val ctx = NightContext(
            nightKey = "demo-" + Instant.ofEpochMilli(nowMs),
            bedEpochMs = nowMs + windDownSeconds * 1000L,
            wakeEpochMs = nowMs + (windDownSeconds + sleepSeconds) * 1000L,
            alarmEnabled = config.alarmEnabled,
            dndEnabled = config.dndEnabled,
            mode = config.mode,
        )
        dispatch(SessionEvent.WindDownDue(ctx))
        scheduleFor(store.snapshot(), nowMs, ctx.bedEpochMs - 1, ctx.bedEpochMs, ctx.wakeEpochMs)
    }

    private fun scheduleFor(snapshot: Snapshot, nowMs: Long, windDown: Long, bed: Long, wake: Long) {
        when (snapshot.state) {
            SessionState.IDLE, SessionState.WINDDOWN -> scheduler.scheduleBoundaries(
                nowMs,
                AlarmScheduler.ACTION_WINDDOWN to windDown,
                AlarmScheduler.ACTION_WARN_15 to bed - 15 * 60_000L,
                AlarmScheduler.ACTION_WARN_1 to bed - 60_000L,
                AlarmScheduler.ACTION_BEDTIME to bed,
                AlarmScheduler.ACTION_WAKE to wake,
            )
            else -> scheduler.scheduleBoundaries(nowMs, AlarmScheduler.ACTION_WAKE to wake)
        }
    }

    private fun execute(effect: Effect, snapshot: Snapshot) {
        when (effect) {
            Effect.StartGuardService -> LockdownForegroundService.start(context)
            Effect.StopGuardService -> LockdownForegroundService.stop(context)
            Effect.ShowNightClock -> showNightClock(snapshot)
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
            is Effect.RecordNight ->
                // Room-backed stats land in M6; the outcome is still logged.
                Log.i(TAG, "night ${effect.night?.nightKey}: ${effect.outcome}")
            Effect.StartBlocking -> {
                BlockingController.update(
                    context,
                    active = true,
                    allowed = Allowlist(context).resolve(store.activeConfig().allowlist),
                    wakeLabel = wakeLabel(snapshot),
                )
                // Poke the running service so it syncs its monitors.
                LockdownForegroundService.start(context)
            }
            Effect.StopBlocking -> {
                BlockingController.update(context, active = false, allowed = emptySet(), wakeLabel = "")
                grayscale.setGrayscale(false)
                NightClockActivity.closeIfShowing()
                EscapeFlowActivity.closeIfShowing()
            }
            Effect.StartWindDown -> {
                snapshot.night?.let {
                    overlay.show(
                        windDownStartMs = it.bedEpochMs - windDownMillis(),
                        bedtimeMs = it.bedEpochMs,
                    )
                }
                if (store.activeConfig().grayscaleEnabled) grayscale.setGrayscale(true)
            }
            Effect.StopWindDown -> overlay.hide()
            is Effect.SetDnd -> if (effect.enabled) dnd.enable() else dnd.restore()
            // M6 (wake alarm).
            Effect.StartWakeAlarm, Effect.SnoozeWakeAlarm, Effect.StopWakeAlarm,
            -> Log.i(TAG, "effect not yet implemented: $effect")
        }
    }

    private fun wakeLabel(snapshot: Snapshot): String =
        snapshot.night?.let {
            DateTimeFormatter.ofPattern("HH:mm")
                .format(Instant.ofEpochMilli(it.wakeEpochMs).atZone(zone()))
        } ?: "--:--"

    private fun showNightClock(snapshot: Snapshot) {
        val wakeLabel = wakeLabel(snapshot)
        context.startActivity(
            Intent(context, NightClockActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
                .putExtra(NightClockActivity.EXTRA_WAKE_LABEL, wakeLabel),
        )
    }

    private fun windDownMillis(): Long = store.activeConfig().windDownMinutes * 60_000L

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
