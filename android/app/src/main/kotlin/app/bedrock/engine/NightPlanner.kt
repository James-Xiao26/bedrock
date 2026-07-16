package app.bedrock.engine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure calendar math: which night governs "now", and at what instants does
 * it wind down, lock, and wake. A plan belongs to the evening of its weekday;
 * bedtimes before noon are treated as past-midnight bedtimes of the previous
 * evening (bed 00:30 on the night keyed Friday happens Saturday 00:30).
 */
object NightPlanner {

    data class PlannedNight(
        /** Date of the evening the night starts on (the plan's weekday). */
        val nightKey: LocalDate,
        val day: Int,
        val enabled: Boolean,
        val windDownEpochMs: Long,
        val bedEpochMs: Long,
        val wakeEpochMs: Long,
    ) {
        fun toContext(config: BedrockConfig): NightContext = NightContext(
            nightKey = nightKey.toString(),
            bedEpochMs = bedEpochMs,
            wakeEpochMs = wakeEpochMs,
            alarmEnabled = config.alarmEnabled,
            dndEnabled = config.dndEnabled,
            mode = config.mode,
        )
    }

    /**
     * The night whose wake time is still ahead of [nowMs]: the in-progress
     * night if one is running, otherwise the next upcoming one. Returns the
     * first ENABLED night; disabled nights are skipped entirely.
     */
    fun nextNight(nowMs: Long, zone: ZoneId, config: BedrockConfig): PlannedNight? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        for (offset in -1L..7L) {
            val evening = today.plusDays(offset)
            val night = plan(evening, zone, config) ?: continue
            if (night.wakeEpochMs > nowMs && night.enabled) return night
        }
        return null
    }

    /** The concrete instants for the night starting on [evening], if scheduled. */
    fun plan(evening: LocalDate, zone: ZoneId, config: BedrockConfig): PlannedNight? {
        val plan = config.schedule[evening.dayOfWeek.value] ?: return null

        val bedDate = if (plan.bedMinutes < NOON_MINUTES) evening.plusDays(1) else evening
        val bed = bedDate.atTime(LocalTime.ofSecondOfDay(plan.bedMinutes * 60L)).atZone(zone)

        var wake = bed.toLocalDate()
            .atTime(LocalTime.ofSecondOfDay(plan.wakeMinutes * 60L))
            .atZone(zone)
        if (!wake.isAfter(bed)) wake = wake.plusDays(1)

        val windDown = bed.minusMinutes(config.windDownMinutes.toLong())

        return PlannedNight(
            nightKey = evening,
            day = evening.dayOfWeek.value,
            enabled = plan.enabled,
            windDownEpochMs = windDown.toInstant().toEpochMilli(),
            bedEpochMs = bed.toInstant().toEpochMilli(),
            wakeEpochMs = wake.toInstant().toEpochMilli(),
        )
    }

    private const val NOON_MINUTES = 12 * 60
}
