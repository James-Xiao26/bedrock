package app.bedrock.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsSummaryTest {

    private fun window(key: String, outcome: WindowOutcome) =
        WindowRecord(key, 0, 0, outcome.name)

    @Test
    fun `empty history is all zeros`() {
        val s = StatsSummary.from(emptyList())
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.windowsKept)
        assertEquals(0, s.totalWindows)
        assertEquals(emptyList(), s.recent)
    }

    @Test
    fun `unbroken run of clean windows streaks`() {
        val s = StatsSummary.from(
            listOf(
                window("2026-07-17", WindowOutcome.CLEAN),
                window("2026-07-18", WindowOutcome.CLEAN),
                window("2026-07-19", WindowOutcome.CLEAN),
            ),
        )
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.windowsKept)
        assertEquals(3, s.totalWindows)
    }

    @Test
    fun `an unlocked window breaks the streak but not the kept count`() {
        val s = StatsSummary.from(
            listOf(
                window("2026-07-16", WindowOutcome.CLEAN),
                window("2026-07-17", WindowOutcome.UNLOCKED),
                window("2026-07-18", WindowOutcome.CLEAN),
                window("2026-07-19", WindowOutcome.CLEAN),
            ),
        )
        assertEquals(2, s.currentStreak) // only the two most recent clean windows
        assertEquals(3, s.windowsKept)
        assertEquals(4, s.totalWindows)
    }

    @Test
    fun `streak counts from the latest window regardless of input order`() {
        val s = StatsSummary.from(
            listOf(
                window("2026-07-19", WindowOutcome.CLEAN),
                window("2026-07-17", WindowOutcome.CLEAN),
                window("2026-07-18", WindowOutcome.VIOLATED),
            ),
        )
        // Sorted: 17 clean, 18 violated, 19 clean -> latest run is just the 19th.
        assertEquals(1, s.currentStreak)
        assertEquals("2026-07-19", s.recent.last().windowKey)
    }
}
