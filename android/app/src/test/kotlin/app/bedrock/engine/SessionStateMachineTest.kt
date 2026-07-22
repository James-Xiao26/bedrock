package app.bedrock.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionStateMachineTest {

    private val window = WindowContext(
        windowKey = "2026-07-15",
        openEpochMs = 1_000_000L,
        closeEpochMs = 2_000_000L,
    )

    private fun active(w: WindowContext = window): Snapshot =
        SessionStateMachine.transition(Snapshot(), SessionEvent.WindowOpenDue(w)).snapshot

    @Test
    fun `opening a window starts blocking`() {
        val t = SessionStateMachine.transition(Snapshot(), SessionEvent.WindowOpenDue(window))
        assertEquals(SessionState.ACTIVE, t.snapshot.state)
        assertEquals(window, t.snapshot.window)
        assertTrue(t.snapshot.blocking)
        assertTrue(Effect.StartGuardService in t.effects)
        assertTrue(Effect.StartBlocking in t.effects)
    }

    @Test
    fun `a clean window is recorded CLEAN and merges pending config`() {
        val t = SessionStateMachine.transition(active(), SessionEvent.WindowCloseDue)
        assertEquals(SessionState.IDLE, t.snapshot.state)
        assertEquals(Snapshot(), t.snapshot)
        assertEquals(WindowOutcome.CLEAN, t.effects.recordedOutcome())
        assertTrue(Effect.StopBlocking in t.effects)
        assertTrue(Effect.MergePendingConfig in t.effects)
    }

    @Test
    fun `a window where a grant was used is recorded UNLOCKED`() {
        val used = active().copy(grantUsed = true)
        val t = SessionStateMachine.transition(used, SessionEvent.WindowCloseDue)
        assertEquals(WindowOutcome.UNLOCKED, t.effects.recordedOutcome())
    }

    @Test
    fun `violation from an active window resets and records VIOLATED`() {
        val t = SessionStateMachine.transition(active(), SessionEvent.ViolationDetected)
        assertEquals(Snapshot(), t.snapshot)
        assertEquals(WindowOutcome.VIOLATED, t.effects.recordedOutcome())
        assertTrue(Effect.MergePendingConfig in t.effects)
    }

    @Test
    fun `violation while idle is a no-op`() {
        val t = SessionStateMachine.transition(Snapshot(), SessionEvent.ViolationDetected)
        assertEquals(Snapshot(), t.snapshot)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `stale or duplicate events are ignored`() {
        // Second open while already active.
        val reopen = SessionStateMachine.transition(active(), SessionEvent.WindowOpenDue(window))
        assertTrue(reopen.effects.isEmpty())
        assertEquals(SessionState.ACTIVE, reopen.snapshot.state)

        // Close while already idle.
        val idleClose = SessionStateMachine.transition(Snapshot(), SessionEvent.WindowCloseDue)
        assertTrue(idleClose.effects.isEmpty())
    }

    @Test
    fun `a manual window is not recorded in stats when it closes`() {
        val manual = active(window.copy(manual = true))
        val t = SessionStateMachine.transition(manual, SessionEvent.WindowCloseDue)
        assertEquals(SessionState.IDLE, t.snapshot.state)
        assertTrue(Effect.StopBlocking in t.effects)
        // RecordWindow still fires (for MergePendingConfig ordering) but with no window.
        assertEquals(null, t.effects.filterIsInstance<Effect.RecordWindow>().single().window)
    }

    @Test
    fun `snapshot survives serialization round trip`() {
        val snapshot = active().copy(grantUsed = true)
        val json = kotlinx.serialization.json.Json.encodeToString(Snapshot.serializer(), snapshot)
        val restored = kotlinx.serialization.json.Json.decodeFromString(Snapshot.serializer(), json)
        assertEquals(snapshot, restored)
    }
}

private fun List<Effect>.recordedOutcome(): WindowOutcome? =
    filterIsInstance<Effect.RecordWindow>().singleOrNull()?.outcome
