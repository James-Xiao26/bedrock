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
        tonight: Int = monday,
    ) = ChangeClassifier.classify(active, pending, requested, tonight)

    // --- bedtime ---

    @Test
    fun `later bedtime tonight is deferred`() {
        val requested = patchFor(monday, base.plan(monday).copy(bedMinutes = min("23:30")))
        val result = classify(requested)
        assertEquals(min("23:00"), result.active.plan(monday).bedMinutes)
        assertEquals(min("23:30"), result.pending.schedule!!.getValue(monday).bedMinutes)
    }

    @Test
    fun `earlier bedtime tonight applies now and clears pending`() {
        val pending = ConfigPatch(schedule = mapOf(monday to base.plan(monday).copy(bedMinutes = min("23:30"))))
        val requested = patchFor(monday, base.plan(monday).copy(bedMinutes = min("22:30")))
        val result = classify(requested, pending = pending)
        assertEquals(min("22:30"), result.active.plan(monday).bedMinutes)
        assertEquals(null, result.pending.schedule)
    }

    @Test
    fun `bedtime past midnight is later than one before midnight`() {
        // 00:30 must count as LATER than 23:00, not earlier.
        val requested = patchFor(monday, base.plan(monday).copy(bedMinutes = min("00:30")))
        val result = classify(requested)
        assertEquals(min("23:00"), result.active.plan(monday).bedMinutes)
        assertEquals(min("00:30"), result.pending.schedule!!.getValue(monday).bedMinutes)
    }

    // --- wake ---

    @Test
    fun `earlier wake tonight is deferred`() {
        val requested = patchFor(monday, base.plan(monday).copy(wakeMinutes = min("06:00")))
        val result = classify(requested)
        assertEquals(min("07:00"), result.active.plan(monday).wakeMinutes)
        assertEquals(min("06:00"), result.pending.schedule!!.getValue(monday).wakeMinutes)
    }

    @Test
    fun `later wake tonight applies now`() {
        val requested = patchFor(monday, base.plan(monday).copy(wakeMinutes = min("08:00")))
        val result = classify(requested)
        assertEquals(min("08:00"), result.active.plan(monday).wakeMinutes)
        assertEquals(null, result.pending.schedule)
    }

    // --- mixed plan: split per field ---

    @Test
    fun `mixed tighten and loosen in one plan splits per field`() {
        // Earlier bed (tighten) + earlier wake (loosen) in a single edit.
        val requested = patchFor(
            monday,
            NightPlan(bedMinutes = min("22:00"), wakeMinutes = min("06:00")),
        )
        val result = classify(requested)
        // Tightened half applies now:
        assertEquals(min("22:00"), result.active.plan(monday).bedMinutes)
        assertEquals(min("07:00"), result.active.plan(monday).wakeMinutes)
        // Full requested plan lands tomorrow:
        assertEquals(
            NightPlan(bedMinutes = min("22:00"), wakeMinutes = min("06:00")),
            result.pending.schedule!!.getValue(monday),
        )
    }

    @Test
    fun `disabling tonight is deferred, enabling applies now`() {
        val disable = patchFor(monday, base.plan(monday).copy(enabled = false))
        val disabled = classify(disable)
        assertEquals(true, disabled.active.plan(monday).enabled)
        assertEquals(false, disabled.pending.schedule!!.getValue(monday).enabled)

        val activeDisabled = base.copy(
            schedule = base.schedule + (monday to base.plan(monday).copy(enabled = false)),
        )
        val enable = patchFor(monday, activeDisabled.plan(monday).copy(enabled = true))
        val enabled = classify(enable, active = activeDisabled)
        assertEquals(true, enabled.active.plan(monday).enabled)
        assertEquals(null, enabled.pending.schedule)
    }

    // --- other weekdays ---

    @Test
    fun `changes to another weekday apply immediately even if loosening`() {
        val requested = patchFor(tuesday, NightPlan(min("01:00"), min("05:00"), enabled = false))
        val result = classify(requested, tonight = monday)
        assertEquals(NightPlan(min("01:00"), min("05:00"), enabled = false), result.active.plan(tuesday))
        assertEquals(null, result.pending.schedule)
    }

    // --- passcode visibility cutoff ---

    @Test
    fun `earlier code cutoff applies now, later cutoff is deferred`() {
        // base default cutoff is 15:00.
        val earlier = classify(ConfigPatch(passwordViewCutoffMinutes = min("13:00")))
        assertEquals(min("13:00"), earlier.active.passwordViewCutoffMinutes)
        assertEquals(null, earlier.pending.passwordViewCutoffMinutes)

        val later = classify(ConfigPatch(passwordViewCutoffMinutes = min("18:00")))
        assertEquals(min("15:00"), later.active.passwordViewCutoffMinutes)
        assertEquals(min("18:00"), later.pending.passwordViewCutoffMinutes)
    }

    @Test
    fun `re-requesting the active cutoff cancels a pending change`() {
        val result = classify(
            ConfigPatch(passwordViewCutoffMinutes = min("15:00")),
            pending = ConfigPatch(passwordViewCutoffMinutes = min("18:00")),
        )
        assertEquals(min("15:00"), result.active.passwordViewCutoffMinutes)
        assertEquals(null, result.pending.passwordViewCutoffMinutes)
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
    fun `pending patch applied at the boundary yields the requested config`() {
        val requested = ConfigPatch(
            schedule = mapOf(monday to NightPlan(min("23:45"), min("06:30"))),
            passwordViewCutoffMinutes = min("18:00"),
        )
        val result = classify(requested)
        val merged = result.pending.appliedTo(result.active)
        assertEquals(NightPlan(min("23:45"), min("06:30")), merged.plan(monday))
        assertEquals(min("18:00"), merged.passwordViewCutoffMinutes)
    }

    // --- helpers ---

    private fun BedrockConfig.plan(day: Int) = schedule.getValue(day)

    private fun patchFor(day: Int, plan: NightPlan) = ConfigPatch(schedule = mapOf(day to plan))

    private fun min(hhmm: String): Int {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return h * 60 + m
    }
}
