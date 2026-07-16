package app.bedrock.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionStateMachineTest {

    private val night = NightContext(
        nightKey = "2026-07-15",
        bedEpochMs = 1_000_000L,
        wakeEpochMs = 2_000_000L,
        alarmEnabled = false,
        dndEnabled = true,
        mode = Mode.NORMAL,
    )

    private fun locked(n: NightContext = night): Snapshot {
        val windDown = SessionStateMachine.transition(Snapshot(), SessionEvent.WindDownDue(n))
        val lockedT = SessionStateMachine.transition(windDown.snapshot, SessionEvent.BedtimeDue(n))
        return lockedT.snapshot
    }

    @Test
    fun `full happy path completes the night and merges pending config`() {
        val t1 = SessionStateMachine.transition(Snapshot(), SessionEvent.WindDownDue(night))
        assertEquals(SessionState.WINDDOWN, t1.snapshot.state)
        assertTrue(Effect.StartWindDown in t1.effects)

        val t2 = SessionStateMachine.transition(t1.snapshot, SessionEvent.BedtimeDue(night))
        assertEquals(SessionState.LOCKED, t2.snapshot.state)
        assertTrue(t2.snapshot.blocking)
        assertTrue(Effect.StartBlocking in t2.effects)
        assertTrue(Effect.SetDnd(true) in t2.effects)
        assertTrue(Effect.ShowNightClock in t2.effects)

        val t3 = SessionStateMachine.transition(t2.snapshot, SessionEvent.WakeDue)
        assertEquals(SessionState.IDLE, t3.snapshot.state)
        assertEquals(Snapshot(), t3.snapshot)
        assertEquals(NightOutcome.COMPLETED, t3.effects.recordedOutcome())
        assertTrue(Effect.MergePendingConfig in t3.effects)
        assertTrue(Effect.StopBlocking in t3.effects)
    }

    @Test
    fun `bedtime after reboot with missed wind-down still locks`() {
        val t = SessionStateMachine.transition(Snapshot(), SessionEvent.BedtimeDue(night))
        assertEquals(SessionState.LOCKED, t.snapshot.state)
        assertEquals(night, t.snapshot.night)
        assertTrue(t.snapshot.blocking)
    }

    @Test
    fun `dnd is not touched when disabled in config`() {
        val noDnd = night.copy(dndEnabled = false)
        val t = SessionStateMachine.transition(Snapshot(), SessionEvent.BedtimeDue(noDnd))
        assertTrue(t.effects.none { it == Effect.SetDnd(true) })
    }

    @Test
    fun `escape unlocks the rest of the night and is recorded at night end`() {
        val t1 = SessionStateMachine.transition(locked(), SessionEvent.EscapeCompleted)
        assertEquals(SessionState.ESCAPED, t1.snapshot.state)
        assertEquals(false, t1.snapshot.blocking)
        assertTrue(Effect.StopBlocking in t1.effects)
        // Not recorded yet - the night is not over.
        assertTrue(t1.effects.none { it is Effect.RecordNight })

        val t2 = SessionStateMachine.transition(t1.snapshot, SessionEvent.WakeDue)
        assertEquals(SessionState.IDLE, t2.snapshot.state)
        assertEquals(NightOutcome.ESCAPED, t2.effects.recordedOutcome())
    }

    @Test
    fun `bypass is recorded as bypassed at night end`() {
        val t1 = SessionStateMachine.transition(locked(), SessionEvent.BypassPurchased)
        assertEquals(SessionState.BYPASSED, t1.snapshot.state)
        val t2 = SessionStateMachine.transition(t1.snapshot, SessionEvent.WakeDue)
        assertEquals(NightOutcome.BYPASSED, t2.effects.recordedOutcome())
    }

    @Test
    fun `wake with alarm enabled rings instead of ending, snooze keeps blocking`() {
        val alarmNight = night.copy(alarmEnabled = true)
        val t1 = SessionStateMachine.transition(locked(alarmNight), SessionEvent.WakeDue)
        assertEquals(SessionState.WAKE_ALARM, t1.snapshot.state)
        assertTrue(t1.snapshot.blocking)
        assertTrue(Effect.StartWakeAlarm in t1.effects)

        val t2 = SessionStateMachine.transition(t1.snapshot, SessionEvent.AlarmSnoozed)
        assertEquals(SessionState.WAKE_ALARM, t2.snapshot.state)
        assertTrue(t2.snapshot.blocking)
        assertTrue(Effect.SnoozeWakeAlarm in t2.effects)

        val t3 = SessionStateMachine.transition(t2.snapshot, SessionEvent.AlarmDismissed)
        assertEquals(SessionState.IDLE, t3.snapshot.state)
        assertTrue(Effect.StopWakeAlarm in t3.effects)
        assertEquals(NightOutcome.COMPLETED, t3.effects.recordedOutcome())
    }

    @Test
    fun `alarm rings even after an escape, without re-blocking`() {
        val alarmNight = night.copy(alarmEnabled = true)
        val escaped = SessionStateMachine.transition(locked(alarmNight), SessionEvent.EscapeCompleted)
        val t = SessionStateMachine.transition(escaped.snapshot, SessionEvent.WakeDue)
        assertEquals(SessionState.WAKE_ALARM, t.snapshot.state)
        assertEquals(false, t.snapshot.blocking)

        val done = SessionStateMachine.transition(t.snapshot, SessionEvent.AlarmDismissed)
        assertEquals(NightOutcome.ESCAPED, done.effects.recordedOutcome())
    }

    @Test
    fun `violation from any active state resets and records`() {
        for (snapshot in listOf(
            locked(),
            SessionStateMachine.transition(Snapshot(), SessionEvent.WindDownDue(night)).snapshot,
            SessionStateMachine.transition(locked(), SessionEvent.EscapeCompleted).snapshot,
        )) {
            val t = SessionStateMachine.transition(snapshot, SessionEvent.ViolationDetected)
            assertEquals(Snapshot(), t.snapshot)
            assertEquals(NightOutcome.VIOLATED, t.effects.recordedOutcome())
            assertTrue(Effect.MergePendingConfig in t.effects)
        }
    }

    @Test
    fun `violation while idle is a no-op`() {
        val t = SessionStateMachine.transition(Snapshot(), SessionEvent.ViolationDetected)
        assertEquals(Snapshot(), t.snapshot)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `stale or duplicate events are ignored`() {
        // Escape when not locked.
        val idleEscape = SessionStateMachine.transition(Snapshot(), SessionEvent.EscapeCompleted)
        assertTrue(idleEscape.effects.isEmpty())

        // Second bedtime while already locked.
        val relock = SessionStateMachine.transition(locked(), SessionEvent.BedtimeDue(night))
        assertTrue(relock.effects.isEmpty())
        assertEquals(SessionState.LOCKED, relock.snapshot.state)

        // Wake events after the night ended.
        val idleWake = SessionStateMachine.transition(Snapshot(), SessionEvent.WakeDue)
        assertTrue(idleWake.effects.isEmpty())

        // Snooze/dismiss without a ringing alarm.
        assertTrue(SessionStateMachine.transition(locked(), SessionEvent.AlarmSnoozed).effects.isEmpty())
        assertTrue(SessionStateMachine.transition(Snapshot(), SessionEvent.AlarmDismissed).effects.isEmpty())
    }

    @Test
    fun `snapshot survives serialization round trip`() {
        val snapshot = locked().copy(earlyExit = NightOutcome.ESCAPED)
        val json = kotlinx.serialization.json.Json.encodeToString(Snapshot.serializer(), snapshot)
        val restored = kotlinx.serialization.json.Json.decodeFromString(Snapshot.serializer(), json)
        assertEquals(snapshot, restored)
    }
}

private fun List<Effect>.recordedOutcome(): NightOutcome? =
    filterIsInstance<Effect.RecordNight>().singleOrNull()?.outcome
