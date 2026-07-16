package app.bedrock.engine

import kotlinx.serialization.Serializable

/**
 * The night's lifecycle. Pure Kotlin: transitions are a function of
 * (snapshot, event) returning the new snapshot plus side effects for the
 * engine to execute. No Android dependencies, so every path is unit-testable.
 *
 *   IDLE -> WINDDOWN -> LOCKED -> { ESCAPED | BYPASSED | WAKE_ALARM } -> IDLE
 *
 * Snoozing loops in WAKE_ALARM with blocking still active; wake time or
 * alarm dismissal ends the night and merges pending config.
 */

enum class SessionState { IDLE, WINDDOWN, LOCKED, ESCAPED, BYPASSED, WAKE_ALARM }

enum class NightOutcome { COMPLETED, ESCAPED, BYPASSED, VIOLATED, DISABLED }

/** Everything the engine needs to know about tonight, fixed at wind-down. */
@Serializable
data class NightContext(
    /** Date (ISO yyyy-MM-dd) of the evening the night starts on. */
    val nightKey: String,
    val bedEpochMs: Long,
    val wakeEpochMs: Long,
    val alarmEnabled: Boolean,
    val dndEnabled: Boolean,
    val mode: Mode,
)

@Serializable
data class Snapshot(
    val state: SessionState = SessionState.IDLE,
    val night: NightContext? = null,
    /** True while app launches must bounce to the night clock. */
    val blocking: Boolean = false,
    /** Set when the user left LOCKED early; recorded at night end. */
    val earlyExit: NightOutcome? = null,
)

sealed interface SessionEvent {
    /** Wind-down alarm fired (or engine caught up past that boundary). */
    data class WindDownDue(val night: NightContext) : SessionEvent

    /** Bedtime alarm fired. Carries context for the missed-wind-down path. */
    data class BedtimeDue(val night: NightContext) : SessionEvent

    /** Normal-mode escape flow finished (countdown + phrase). */
    data object EscapeCompleted : SessionEvent

    /** Hardcore bypass purchase verified. */
    data object BypassPurchased : SessionEvent

    /** Wake alarm fired at wake time. */
    data object WakeDue : SessionEvent

    data object AlarmSnoozed : SessionEvent

    data object AlarmDismissed : SessionEvent

    /** Engine detected tampering (force-stop gap, service revoked mid-night). */
    data object ViolationDetected : SessionEvent
}

sealed interface Effect {
    data object StartGuardService : Effect
    data object StopGuardService : Effect
    data object StartWindDown : Effect
    data object StopWindDown : Effect
    data object StartBlocking : Effect
    data object StopBlocking : Effect
    data object ShowNightClock : Effect
    data class SetDnd(val enabled: Boolean) : Effect
    data object StartWakeAlarm : Effect
    data object SnoozeWakeAlarm : Effect
    data object StopWakeAlarm : Effect
    data class RecordNight(val outcome: NightOutcome, val night: NightContext?) : Effect
    data object MergePendingConfig : Effect
    data object PersistAndBroadcast : Effect
}

object SessionStateMachine {

    data class Transition(val snapshot: Snapshot, val effects: List<Effect>)

    fun transition(current: Snapshot, event: SessionEvent): Transition = when (event) {
        is SessionEvent.WindDownDue -> when (current.state) {
            SessionState.IDLE -> Transition(
                Snapshot(SessionState.WINDDOWN, event.night),
                listOf(Effect.StartGuardService, Effect.StartWindDown, Effect.PersistAndBroadcast),
            )
            else -> ignore(current)
        }

        is SessionEvent.BedtimeDue -> when (current.state) {
            // The IDLE arm covers a missed/zero wind-down (e.g. reboot at 23:05).
            SessionState.IDLE, SessionState.WINDDOWN -> {
                val night = current.night ?: event.night
                Transition(
                    Snapshot(SessionState.LOCKED, night, blocking = true),
                    buildList {
                        add(Effect.StartGuardService)
                        add(Effect.StopWindDown)
                        add(Effect.StartBlocking)
                        if (night.dndEnabled) add(Effect.SetDnd(true))
                        add(Effect.ShowNightClock)
                        add(Effect.PersistAndBroadcast)
                    },
                )
            }
            else -> ignore(current)
        }

        SessionEvent.EscapeCompleted -> earlyExitFromLocked(
            current,
            SessionState.ESCAPED,
            NightOutcome.ESCAPED,
        )

        SessionEvent.BypassPurchased -> earlyExitFromLocked(
            current,
            SessionState.BYPASSED,
            NightOutcome.BYPASSED,
        )

        SessionEvent.WakeDue -> when (current.state) {
            SessionState.LOCKED, SessionState.ESCAPED, SessionState.BYPASSED -> {
                val night = current.night
                if (night?.alarmEnabled == true) {
                    Transition(
                        current.copy(state = SessionState.WAKE_ALARM),
                        listOf(Effect.StartWakeAlarm, Effect.PersistAndBroadcast),
                    )
                } else {
                    endNight(current)
                }
            }
            SessionState.WINDDOWN -> endNight(current) // degenerate config; fail open
            else -> ignore(current)
        }

        SessionEvent.AlarmSnoozed -> when (current.state) {
            SessionState.WAKE_ALARM -> Transition(
                current,
                listOf(Effect.SnoozeWakeAlarm),
            )
            else -> ignore(current)
        }

        SessionEvent.AlarmDismissed -> when (current.state) {
            SessionState.WAKE_ALARM -> endNight(current, listOf(Effect.StopWakeAlarm))
            else -> ignore(current)
        }

        SessionEvent.ViolationDetected -> when (current.state) {
            SessionState.IDLE -> ignore(current)
            else -> Transition(
                Snapshot(),
                listOf(
                    Effect.StopWakeAlarm,
                    Effect.StopWindDown,
                    Effect.StopBlocking,
                    Effect.SetDnd(false),
                    Effect.StopGuardService,
                    Effect.RecordNight(NightOutcome.VIOLATED, current.night),
                    Effect.MergePendingConfig,
                    Effect.PersistAndBroadcast,
                ),
            )
        }
    }

    private fun earlyExitFromLocked(
        current: Snapshot,
        state: SessionState,
        outcome: NightOutcome,
    ): Transition = when (current.state) {
        SessionState.LOCKED -> Transition(
            current.copy(state = state, blocking = false, earlyExit = outcome),
            listOf(
                Effect.StopBlocking,
                Effect.SetDnd(false),
                Effect.PersistAndBroadcast,
            ),
        )
        else -> ignore(current)
    }

    private fun endNight(current: Snapshot, prefix: List<Effect> = emptyList()): Transition {
        val outcome = current.earlyExit ?: NightOutcome.COMPLETED
        return Transition(
            Snapshot(),
            prefix + listOf(
                Effect.StopBlocking,
                Effect.SetDnd(false),
                Effect.StopGuardService,
                Effect.RecordNight(outcome, current.night),
                Effect.MergePendingConfig,
                Effect.PersistAndBroadcast,
            ),
        )
    }

    private fun ignore(current: Snapshot) = Transition(current, emptyList())
}
