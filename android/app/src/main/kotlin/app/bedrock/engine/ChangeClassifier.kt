package app.bedrock.engine

/**
 * Implements the settings freeze: changes that make the CURRENT window easier
 * only take effect at the next window; tightening applies immediately.
 *
 * Pure function of (active config, pending patch, requested patch, current
 * window's weekday). Returns the new active config and the new pending patch.
 * The caller (ConfigStore) persists both and merges pending at the window
 * boundary.
 *
 * Rules per field, judged against the currently effective value:
 * - Loosen (deferred): later start, earlier end, disabling the window, later
 *   passcode cutoff, allowlist ADDITIONS.
 * - Tighten (applied now, clearing any contradicted pending value): earlier
 *   start, later end, enabling, earlier passcode cutoff, allowlist REMOVALS.
 * - Neutral (applied now): any schedule change for a weekday other than the
 *   current window's.
 * - Setting a field back to its active value cancels the pending change.
 */
object ChangeClassifier {

    data class Result(val active: BedrockConfig, val pending: ConfigPatch)

    fun classify(
        active: BedrockConfig,
        pending: ConfigPatch,
        requested: ConfigPatch,
        tonightDay: Int,
    ): Result {
        var newActive = active
        var newPending = pending

        requested.schedule?.let { days ->
            val activeDays = active.schedule.toMutableMap()
            val pendingDays = (pending.schedule ?: emptyMap()).toMutableMap()
            for ((day, requestedPlan) in days) {
                if (day != tonightDay) {
                    // Other weekdays never loosen the current window; apply directly.
                    activeDays[day] = requestedPlan
                    pendingDays.remove(day)
                    continue
                }
                val current = active.schedule.getValue(day)
                val tightened = current.copy(
                    bedMinutes = if (isEarlierBed(requestedPlan.bedMinutes, current.bedMinutes)) {
                        requestedPlan.bedMinutes
                    } else {
                        current.bedMinutes
                    },
                    wakeMinutes = maxOf(requestedPlan.wakeMinutes, current.wakeMinutes),
                    enabled = current.enabled || requestedPlan.enabled,
                )
                if (tightened != current) activeDays[day] = tightened
                if (requestedPlan != tightened) {
                    pendingDays[day] = requestedPlan
                } else {
                    pendingDays.remove(day)
                }
            }
            newActive = newActive.copy(schedule = activeDays)
            newPending = newPending.copy(schedule = pendingDays.ifEmpty { null })
        }

        requested.passwordViewCutoffMinutes?.let { cutoff ->
            // A LATER cutoff means more chances to peek at the code before the
            // window, so it loosens (waits); an earlier cutoff tightens.
            if (cutoff == active.passwordViewCutoffMinutes) {
                newPending = newPending.copy(passwordViewCutoffMinutes = null)
            } else if (cutoff < active.passwordViewCutoffMinutes) {
                newActive = newActive.copy(passwordViewCutoffMinutes = cutoff)
                newPending = newPending.copy(passwordViewCutoffMinutes = null)
            } else {
                newPending = newPending.copy(passwordViewCutoffMinutes = cutoff)
            }
        }

        requested.allowlist?.let { requestedSet ->
            // Removals tighten (apply now); additions loosen (wait).
            val tightened = active.allowlist intersect requestedSet
            if (tightened != active.allowlist) {
                newActive = newActive.copy(allowlist = tightened)
            }
            newPending = if (requestedSet != tightened) {
                newPending.copy(allowlist = requestedSet)
            } else {
                newPending.copy(allowlist = null)
            }
        }

        return Result(newActive, newPending)
    }

    /**
     * Window starts cluster around midnight; a start before noon is treated as
     * belonging to the NEXT calendar day (00:30 is later than 23:00).
     */
    private fun isEarlierBed(candidate: Int, reference: Int): Boolean =
        normalizeBed(candidate) < normalizeBed(reference)

    private fun normalizeBed(minutes: Int): Int =
        if (minutes < 12 * 60) minutes + 24 * 60 else minutes
}
