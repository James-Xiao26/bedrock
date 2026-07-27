package app.bedrock.engine

/**
 * Monotonic-clock defense against manual system-clock edits.
 *
 * A block window's real duration is fixed at open. If the engine trusted only
 * the wall clock, a user could set the clock past the window's end and the
 * close alarm would fire early, ending downtime. Instead the window carries a
 * monotonic anchor ([SystemClock.elapsedRealtime], which counts real time since
 * boot and is not user-settable), and the engine measures remaining time
 * against that.
 *
 * Pure so the tamper math is unit-testable without Android. The engine passes
 * `System.currentTimeMillis()` and `SystemClock.elapsedRealtime()` in.
 */
object WindowClock {

    /**
     * Stamp [window] with a monotonic anchor + deadline at open. The deadline
     * counts from [monoNowMs], so a late catch-up open (opened well after the
     * scheduled start) correctly gets only the wall time still left in the
     * window, never the full duration.
     */
    fun anchor(window: WindowContext, nowMs: Long, monoNowMs: Long): WindowContext =
        window.copy(
            openElapsedMs = monoNowMs,
            closeElapsedMs = monoNowMs + (window.closeEpochMs - nowMs).coerceAtLeast(0),
        )

    /**
     * Real milliseconds left in [window]. Uses the monotonic deadline when the
     * anchor is valid; falls back to the wall clock when there is no anchor
     * (legacy snapshot) or the monotonic clock ran backwards vs the anchor
     * (a reboot reset it) - both cases the anchor can't be trusted.
     */
    fun remainingMs(window: WindowContext, nowMs: Long, monoNowMs: Long): Long {
        val openMono = window.openElapsedMs
        val closeMono = window.closeElapsedMs
        if (openMono == null || closeMono == null || monoNowMs < openMono) {
            return window.closeEpochMs - nowMs
        }
        return closeMono - monoNowMs
    }
}
