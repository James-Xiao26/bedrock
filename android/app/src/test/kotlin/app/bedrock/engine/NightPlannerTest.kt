package app.bedrock.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NightPlannerTest {

    private val zone = ZoneId.of("America/New_York")
    private val base = BedrockConfig() // 23:00-07:00 nightly, 30 min wind-down

    private fun at(date: String, time: String): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    // Wednesday 2026-07-15 is the reference evening; ISO day 3.

    @Test
    fun `evening before bedtime plans tonight`() {
        val night = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, base)
        assertNotNull(night)
        assertEquals(LocalDate.parse("2026-07-15"), night.nightKey)
        assertEquals(at("2026-07-15", "22:30"), night.windDownEpochMs)
        assertEquals(at("2026-07-15", "23:00"), night.bedEpochMs)
        assertEquals(at("2026-07-16", "07:00"), night.wakeEpochMs)
    }

    @Test
    fun `after midnight mid-lockdown still resolves yesterday's night`() {
        val night = NightPlanner.nextNight(at("2026-07-16", "02:30"), zone, base)
        assertNotNull(night)
        assertEquals(LocalDate.parse("2026-07-15"), night.nightKey)
        assertEquals(at("2026-07-16", "07:00"), night.wakeEpochMs)
    }

    @Test
    fun `after wake time plans the next evening`() {
        val night = NightPlanner.nextNight(at("2026-07-16", "07:00"), zone, base)
        assertNotNull(night)
        assertEquals(LocalDate.parse("2026-07-16"), night.nightKey)
        assertEquals(at("2026-07-16", "23:00"), night.bedEpochMs)
    }

    @Test
    fun `past-midnight bedtime lands on the next calendar day`() {
        val lateNight = base.copy(
            schedule = base.schedule + (3 to NightPlan(bedMinutes = 30, wakeMinutes = 8 * 60)),
        )
        val night = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, lateNight)
        assertNotNull(night)
        assertEquals(LocalDate.parse("2026-07-15"), night.nightKey)
        assertEquals(at("2026-07-16", "00:30"), night.bedEpochMs)
        assertEquals(at("2026-07-16", "08:00"), night.wakeEpochMs)
    }

    @Test
    fun `disabled nights are skipped to the next enabled one`() {
        val wedOff = base.copy(
            schedule = base.schedule + (3 to base.schedule.getValue(3).copy(enabled = false)),
        )
        val night = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, wedOff)
        assertNotNull(night)
        assertEquals(LocalDate.parse("2026-07-16"), night.nightKey)
    }

    @Test
    fun `all nights disabled yields null`() {
        val allOff = base.copy(
            schedule = base.schedule.mapValues { it.value.copy(enabled = false) },
        )
        assertNull(NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, allOff))
    }

    @Test
    fun `spring-forward DST night keeps wall-clock times`() {
        // US DST began 2026-03-08 02:00 in America/New_York.
        val night = NightPlanner.nextNight(at("2026-03-07", "20:00"), zone, base)
        assertNotNull(night)
        assertEquals(at("2026-03-07", "23:00"), night.bedEpochMs)
        // Wall clock 07:00 next morning; the night is one hour shorter in real time.
        assertEquals(at("2026-03-08", "07:00"), night.wakeEpochMs)
        val durationHours = (night.wakeEpochMs - night.bedEpochMs) / 3_600_000.0
        assertEquals(7.0, durationHours)
    }

    @Test
    fun `fall-back DST night keeps wall-clock times and is one hour longer`() {
        // US DST ended 2026-11-01 02:00 in America/New_York.
        val night = NightPlanner.nextNight(at("2026-10-31", "20:00"), zone, base)
        assertNotNull(night)
        val durationHours = (night.wakeEpochMs - night.bedEpochMs) / 3_600_000.0
        assertEquals(9.0, durationHours)
    }

    @Test
    fun `wake equal to bedtime crosses to the next day`() {
        val oddConfig = base.copy(
            schedule = base.schedule + (3 to NightPlan(bedMinutes = 23 * 60, wakeMinutes = 23 * 60)),
        )
        val night = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, oddConfig)
        assertNotNull(night)
        assertEquals(at("2026-07-16", "23:00"), night.wakeEpochMs)
    }
}
