package app.bedrock.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeClassifierTest {

    private val monday = 1
    private val tuesday = 2
    private val base = BedrockConfig() // 23:00-07:00 every day

    private fun classify(
        requested: ConfigPatch,
        active: BedrockConfig = base,
        pending: ConfigPatch = ConfigPatch.EMPTY,
    ) = ChangeClassifier.classify(active, pending, requested)

    // --- schedule: always applies immediately, no freeze ---

    @Test
    fun `any schedule edit for tonight applies now`() {
        // Every kind of loosening (later bed, earlier wake, disable) lands now.
        val requested = patchFor(
            monday,
            NightPlan(bedtimeMinutes = min("23:30"), wakeMinutes = min("06:00"), enabled = false),
        )
        val result = classify(requested)
        assertEquals(
            NightPlan(bedtimeMinutes = min("23:30"), wakeMinutes = min("06:00"), enabled = false),
            result.active.plan(monday),
        )
        assertEquals(null, result.pending.schedule)
    }

    @Test
    fun `a schedule edit clears any stale pending for that day`() {
        val pending = ConfigPatch(schedule = mapOf(monday to base.plan(monday).copy(bedtimeMinutes = min("23:30"))))
        val requested = patchFor(monday, base.plan(monday).copy(bedtimeMinutes = min("22:30")))
        val result = classify(requested, pending = pending)
        assertEquals(min("22:30"), result.active.plan(monday).bedtimeMinutes)
        assertEquals(null, result.pending.schedule)
    }

    @Test
    fun `wind-down lead change applies immediately`() {
        val result = classify(ConfigPatch(windDownMinutes = 30))
        assertEquals(30, result.active.windDownMinutes)
        assertEquals(null, result.pending.windDownMinutes)
    }

    @Test
    fun `changes to another weekday apply immediately`() {
        val requested = patchFor(tuesday, NightPlan(min("01:00"), min("05:00"), enabled = false))
        val result = classify(requested)
        assertEquals(NightPlan(min("01:00"), min("05:00"), enabled = false), result.active.plan(tuesday))
        assertEquals(null, result.pending.schedule)
    }

    // --- allowlist ---

    @Test
    fun `allowlist removals apply now, additions are deferred`() {
        val active = base.copy(allowlist = setOf("com.spotify.music", "com.audible.application"))
        // Remove audible (tighten) and add maps (loosen) in one edit.
        val requested = ConfigPatch(allowlist = setOf("com.spotify.music", "com.google.android.apps.maps"))
        val result = classify(requested, active = active)
        assertEquals(setOf("com.spotify.music"), result.active.allowlist)
        assertEquals(requested.allowlist, result.pending.allowlist)
    }

    @Test
    fun `pure removal clears pending allowlist`() {
        val active = base.copy(allowlist = setOf("a", "b"))
        val result = classify(
            ConfigPatch(allowlist = setOf("a")),
            active = active,
            pending = ConfigPatch(allowlist = setOf("a", "b", "c")),
        )
        assertEquals(setOf("a"), result.active.allowlist)
        assertEquals(null, result.pending.allowlist)
    }

    // --- pending merge round trip ---

    @Test
    fun `pending allowlist applied at the boundary yields the requested config`() {
        val active = base.copy(allowlist = setOf("a"))
        val requested = ConfigPatch(allowlist = setOf("a", "b"))
        val result = classify(requested, active = active)
        // The addition waits; folding pending in yields the full set.
        assertEquals(setOf("a"), result.active.allowlist)
        assertEquals(setOf("a", "b"), result.pending.appliedTo(result.active).allowlist)
    }

    // --- helpers ---

    private fun BedrockConfig.plan(day: Int) = schedule.getValue(day)

    private fun patchFor(day: Int, plan: NightPlan) = ConfigPatch(schedule = mapOf(day to plan))

    private fun min(hhmm: String): Int {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return h * 60 + m
    }
}
