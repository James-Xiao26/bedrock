package app.bedrock.engine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure calendar math: which block window governs "now", and at what instants
 * it opens and closes. A window belongs to the day of its weekday; starts
 * before noon are treated as past-midnight starts of the previous day
 * (start 00:30 on the window keyed Friday happens Saturday 00:30).
 */
object NightPlanner {

    data class PlannedWindow(
        /** Date of the day the window starts on (the plan's weekday). */
        val windowKey: LocalDate,
        val day: Int,
        val enabled: Boolean,
        val openEpochMs: Long,
        val closeEpochMs: Long,
    ) {
        fun toContext(): WindowContext = WindowContext(
            windowKey = windowKey.toString(),
            openEpochMs = openEpochMs,
            closeEpochMs = closeEpochMs,
        )
    }

    /**
     * The window whose close time is still ahead of [nowMs]: the in-progress
     * window if one is running, otherwise the next upcoming one. Returns the
     * first ENABLED window; disabled ones are skipped entirely.
     */
    fun nextNight(nowMs: Long, zone: ZoneId, config: BedrockConfig): PlannedWindow? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        for (offset in -1L..7L) {
            val day = today.plusDays(offset)
            val window = plan(day, zone, config) ?: continue
            if (window.closeEpochMs > nowMs && window.enabled) return window
        }
        return null
    }

    /** The concrete instants for the window starting on [day], if scheduled. */
    fun plan(day: LocalDate, zone: ZoneId, config: BedrockConfig): PlannedWindow? {
        val plan = config.schedule[day.dayOfWeek.value] ?: return null

        val openDate = if (plan.bedMinutes < NOON_MINUTES) day.plusDays(1) else day
        val open = openDate.atTime(LocalTime.ofSecondOfDay(plan.bedMinutes * 60L)).atZone(zone)

        var close = open.toLocalDate()
            .atTime(LocalTime.ofSecondOfDay(plan.wakeMinutes * 60L))
            .atZone(zone)
        if (!close.isAfter(open)) close = close.plusDays(1)

        return PlannedWindow(
            windowKey = day,
            day = day.dayOfWeek.value,
            enabled = plan.enabled,
            openEpochMs = open.toInstant().toEpochMilli(),
            closeEpochMs = close.toInstant().toEpochMilli(),
        )
    }

    private const val NOON_MINUTES = 12 * 60
}
