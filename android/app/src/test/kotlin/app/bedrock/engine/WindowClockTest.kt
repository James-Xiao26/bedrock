package app.bedrock.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowClockTest {

    // A one-hour window opening at wall 1_000_000, anchored at monotonic 500_000.
    private val open = 1_000_000L
    private val close = open + HOUR
    private val mono = 500_000L
    private val window = WindowClock.anchor(
        WindowContext(windowKey = "2026-07-15", openEpochMs = open, closeEpochMs = close),
        nowMs = open,
        monoNowMs = mono,
    )

    @Test
    fun `on-time open anchors the full duration`() {
        assertEquals(mono, window.openElapsedMs)
        assertEquals(mono + HOUR, window.closeElapsedMs)
        // Half the monotonic time gone -> half the window left, wall clock untouched.
        assertEquals(HOUR / 2, WindowClock.remainingMs(window, nowMs = open, monoNowMs = mono + HOUR / 2))
    }

    @Test
    fun `forward wall-clock jump does not shorten the window`() {
        // User slams the clock past close, but only a minute of real time passed.
        val remaining = WindowClock.remainingMs(
            window,
            nowMs = close + HOUR, // wall clock says long past close
            monoNowMs = mono + MINUTE, // monotonic: only a minute in
        )
        assertEquals(HOUR - MINUTE, remaining)
        assertTrue(remaining > 0, "window must stay open despite the clock edit")
    }

    @Test
    fun `late catch-up open only counts the remaining wall time`() {
        // Opened 50 minutes late (10 left), anchored now.
        val late = WindowClock.anchor(
            WindowContext(windowKey = "k", openEpochMs = open, closeEpochMs = close),
            nowMs = open + 50 * MINUTE,
            monoNowMs = mono,
        )
        assertEquals(10 * MINUTE, WindowClock.remainingMs(late, nowMs = open + 50 * MINUTE, monoNowMs = mono))
    }

    @Test
    fun `reboot resets monotonic clock so it falls back to the wall clock`() {
        // Monotonic ran backwards vs the anchor (uptime counter reset): trust wall.
        val remaining = WindowClock.remainingMs(
            window,
            nowMs = open + HOUR / 2,
            monoNowMs = 10L, // < openElapsedMs -> reboot
        )
        assertEquals(close - (open + HOUR / 2), remaining)
    }

    @Test
    fun `legacy window without an anchor uses the wall clock`() {
        val legacy = WindowContext(windowKey = "k", openEpochMs = open, closeEpochMs = close)
        assertEquals(HOUR, WindowClock.remainingMs(legacy, nowMs = open, monoNowMs = 999L))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
