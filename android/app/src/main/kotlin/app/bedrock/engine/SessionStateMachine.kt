package app.bedrock.engine

import kotlinx.serialization.Serializable

/**
 * The block-window lifecycle. Pure Kotlin: transitions are a function of
 * (snapshot, event) returning the new snapshot plus side effects for the
 * engine to execute. No Android dependencies, so every path is unit-testable.
 *
 *   IDLE -> ACTIVE -> IDLE
 *
 * ACTIVE means the scheduled downtime window is in effect and non-allowlisted
 * apps are blocked. Per-app passcode grants are handled by the engine +
 * BlockingController and never change the session state; they only set the
 * [Snapshot.grantUsed] flag, which decides the recorded outcome at window end.
 */

enum class SessionState { IDLE, ACTIVE }

enum class WindowOutcome { CLEAN, UNLOCKED, VIOLATED }

/** Everything the engine needs to know about the current window, fixed at open. */
@Serializable
data class WindowContext(
    /** Date (ISO yyyy-MM-dd) of the day the window opens on. */
    val windowKey: String,
    val openEpochMs: Long,
    val closeEpochMs: Long,
    /** User-started on-demand downtime, not a scheduled night: not recorded in stats. */
    val manual: Boolean = false,
    /**
     * Monotonic ([android.os.SystemClock.elapsedRealtime]) anchor stamped at open,
     * and the matching monotonic deadline. These let the engine judge the real
     * time left in the window independently of the wall clock, so setting the
     * system clock forward can't skip downtime. Null on legacy snapshots and
     * across reboots (the monotonic clock resets), where the engine falls back
     * to the wall clock. See [WindowClock].
     */
    val openElapsedMs: Long? = null,
    val closeElapsedMs: Long? = null,
)

@Serializable
data class Snapshot(
    val state: SessionState = SessionState.IDLE,
    val window: WindowContext? = null,
    /** True while app launches must bounce to the blocker. */
    val blocking: Boolean = false,
    /** Set when the user spent a passcode grant this window; recorded at close. */
    val grantUsed: Boolean = false,
    /**
     * Set when the engine caught a coverage gap this window (force-stop, or no
     * usable foreground monitor). Sticky until close, where it records VIOLATED.
     * Unlike [SessionEvent.ViolationDetected] this keeps the window ACTIVE - we
     * never reward losing enforcement by ending downtime early.
     */
    val violated: Boolean = false,
)

sealed interface SessionEvent {
    /** Window-open alarm fired (or engine caught up past that boundary). */
    data class WindowOpenDue(val window: WindowContext) : SessionEvent

    /** Window-close alarm fired at the end of the window. */
    data object WindowCloseDue : SessionEvent

    /** Engine detected tampering (force-stop gap, service revoked mid-window). */
    data object ViolationDetected : SessionEvent

    /**
     * Coverage gap noticed while the window is still running (no usable
     * foreground monitor). Flags the window failed but leaves it ACTIVE so
     * re-granting resumes enforcement. Recorded VIOLATED at close.
     */
    data object GapDetected : SessionEvent
}

sealed interface Effect {
    data object StartGuardService : Effect
    data object StopGuardService : Effect
    data object StartBlocking : Effect
    data object StopBlocking : Effect
    data class RecordWindow(val outcome: WindowOutcome, val window: WindowContext?) : Effect
    data object MergePendingConfig : Effect
    data object PersistAndBroadcast : Effect
}

object SessionStateMachine {

    data class Transition(val snapshot: Snapshot, val effects: List<Effect>)

    fun transition(current: Snapshot, event: SessionEvent): Transition = when (event) {
        is SessionEvent.WindowOpenDue -> when (current.state) {
            SessionState.IDLE -> Transition(
                Snapshot(SessionState.ACTIVE, event.window, blocking = true),
                listOf(Effect.StartGuardService, Effect.StartBlocking, Effect.PersistAndBroadcast),
            )
            else -> ignore(current)
        }

        SessionEvent.WindowCloseDue -> when (current.state) {
            SessionState.ACTIVE -> endWindow(current)
            else -> ignore(current)
        }

        SessionEvent.ViolationDetected -> when (current.state) {
            SessionState.IDLE -> ignore(current)
            else -> endWindow(current, forced = WindowOutcome.VIOLATED)
        }

        SessionEvent.GapDetected -> when {
            current.state == SessionState.ACTIVE && !current.violated ->
                Transition(current.copy(violated = true), listOf(Effect.PersistAndBroadcast))
            else -> ignore(current)
        }
    }

    private fun endWindow(current: Snapshot, forced: WindowOutcome? = null): Transition {
        val outcome = forced ?: when {
            current.violated -> WindowOutcome.VIOLATED
            current.grantUsed -> WindowOutcome.UNLOCKED
            else -> WindowOutcome.CLEAN
        }
        // Manual on-demand downtime isn't a slept night; keep it out of stats.
        val recorded = current.window?.takeUnless { it.manual }
        return Transition(
            Snapshot(),
            listOf(
                Effect.StopBlocking,
                Effect.StopGuardService,
                Effect.RecordWindow(outcome, recorded),
                Effect.MergePendingConfig,
                Effect.PersistAndBroadcast,
            ),
        )
    }

    private fun ignore(current: Snapshot) = Transition(current, emptyList())
}
