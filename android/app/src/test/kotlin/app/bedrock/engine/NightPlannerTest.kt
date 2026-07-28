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
    // Lead zeroed so bedtime == window open; the wind-down lead is exercised
    // separately below. This keeps the calendar-math assertions lead-agnostic.
    private val base = BedrockConfig(windDownMinutes = 0) // opens 23:00, closes 07:00

    private fun at(date: String, time: String): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    // Wednesday 2026-07-15 is the reference day; ISO day 3.

    @Test
    fun `before the window plans today`() {
        val window = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, base)
        assertNotNull(window)
        assertEquals(LocalDate.parse("2026-07-15"), window.windowKey)
        assertEquals(at("2026-07-15", "23:00"), window.openEpochMs)
        assertEquals(at("2026-07-16", "07:00"), window.closeEpochMs)
    }

    @Test
    fun `after midnight mid-window still resolves yesterday's window`() {
        val window = NightPlanner.nextNight(at("2026-07-16", "02:30"), zone, base)
        assertNotNull(window)
        assertEquals(LocalDate.parse("2026-07-15"), window.windowKey)
        assertEquals(at("2026-07-16", "07:00"), window.closeEpochMs)
    }

    @Test
    fun `after the window closes plans the next day`() {
        val window = NightPlanner.nextNight(at("2026-07-16", "07:00"), zone, base)
        assertNotNull(window)
        assertEquals(LocalDate.parse("2026-07-16"), window.windowKey)
        assertEquals(at("2026-07-16", "23:00"), window.openEpochMs)
    }

    @Test
    fun `past-midnight start lands on the next calendar day`() {
        val lateStart = base.copy(
            schedule = base.schedule + (3 to NightPlan(bedtimeMinutes = 30, wakeMinutes = 8 * 60)),
        )
        val window = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, lateStart)
        assertNotNull(window)
        assertEquals(LocalDate.parse("2026-07-15"), window.windowKey)
        assertEquals(at("2026-07-16", "00:30"), window.openEpochMs)
        assertEquals(at("2026-07-16", "08:00"), window.closeEpochMs)
    }

    @Test
    fun `disabled windows are skipped to the next enabled one`() {
        val wedOff = base.copy(
            schedule = base.schedule + (3 to base.schedule.getValue(3).copy(enabled = false)),
        )
        val window = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, wedOff)
        assertNotNull(window)
        assertEquals(LocalDate.parse("2026-07-16"), window.windowKey)
    }

    @Test
    fun `all windows disabled yields null`() {
        val allOff = base.copy(
            schedule = base.schedule.mapValues { it.value.copy(enabled = false) },
        )
        assertNull(NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, allOff))
    }

    @Test
    fun `spring-forward DST window keeps wall-clock times`() {
        // US DST began 2026-03-08 02:00 in America/New_York.
        val window = NightPlanner.nextNight(at("2026-03-07", "20:00"), zone, base)
        assertNotNull(window)
        assertEquals(at("2026-03-07", "23:00"), window.openEpochMs)
        // Wall clock 07:00 next morning; the window is one hour shorter in real time.
        assertEquals(at("2026-03-08", "07:00"), window.closeEpochMs)
        val durationHours = (window.closeEpochMs - window.openEpochMs) / 3_600_000.0
        assertEquals(7.0, durationHours)
    }

    @Test
    fun `fall-back DST window keeps wall-clock times and is one hour longer`() {
        // US DST ended 2026-11-01 02:00 in America/New_York.
        val window = NightPlanner.nextNight(at("2026-10-31", "20:00"), zone, base)
        assertNotNull(window)
        val durationHours = (window.closeEpochMs - window.openEpochMs) / 3_600_000.0
        assertEquals(9.0, durationHours)
    }

    @Test
    fun `wind-down lead opens the window before bedtime`() {
        // Default 30-min lead: bedtime 23:00 -> downtime opens 22:30.
        val leadCfg = BedrockConfig() // windDownMinutes = 30
        val window = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, leadCfg)
        assertNotNull(window)
        assertEquals(LocalDate.parse("2026-07-15"), window.windowKey)
        assertEquals(at("2026-07-15", "22:30"), window.openEpochMs)
        assertEquals(at("2026-07-16", "07:00"), window.closeEpochMs)
    }

    @Test
    fun `a custom lead shifts the open time`() {
        val leadCfg = base.copy(windDownMinutes = 90) // bedtime 23:00 -> open 21:30
        val window = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, leadCfg)
        assertNotNull(window)
        assertEquals(at("2026-07-15", "21:30"), window.openEpochMs)
    }

    @Test
    fun `lead wrapping past midnight stays on the bedtime's day`() {
        // Bedtime 00:30 with a 60-min lead opens at 23:30 the evening before.
        val wrapCfg = base.copy(
            windDownMinutes = 60,
            schedule = base.schedule + (3 to NightPlan(bedtimeMinutes = 30, wakeMinutes = 8 * 60)),
        )
        val window = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, wrapCfg)
        assertNotNull(window)
        assertEquals(LocalDate.parse("2026-07-15"), window.windowKey)
        assertEquals(at("2026-07-15", "23:30"), window.openEpochMs)
        assertEquals(at("2026-07-16", "08:00"), window.closeEpochMs)
    }

    @Test
    fun `close equal to open crosses to the next day`() {
        val oddConfig = base.copy(
            schedule = base.schedule + (3 to NightPlan(bedtimeMinutes = 23 * 60, wakeMinutes = 23 * 60)),
        )
        val window = NightPlanner.nextNight(at("2026-07-15", "20:00"), zone, oddConfig)
        assertNotNull(window)
        assertEquals(at("2026-07-16", "23:00"), window.closeEpochMs)
    }
}
