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
)

@Serializable
data class Snapshot(
    val state: SessionState = SessionState.IDLE,
    val window: WindowContext? = null,
    /** True while app launches must bounce to the blocker. */
    val blocking: Boolean = false,
    /** Set when the user spent a passcode grant this window; recorded at close. */
    val grantUsed: Boolean = false,
)

sealed interface SessionEvent {
    /** Window-open alarm fired (or engine caught up past that boundary). */
    data class WindowOpenDue(val window: WindowContext) : SessionEvent

    /** Window-close alarm fired at the end of the window. */
    data object WindowCloseDue : SessionEvent

    /** Engine detected tampering (force-stop gap, service revoked mid-window). */
    data object ViolationDetected : SessionEvent
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
    }

    private fun endWindow(current: Snapshot, forced: WindowOutcome? = null): Transition {
        val outcome = forced
            ?: if (current.grantUsed) WindowOutcome.UNLOCKED else WindowOutcome.CLEAN
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
