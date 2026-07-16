package app.bedrock.engine

/**
 * Implements the settings freeze: changes that make TONIGHT easier only take
 * effect tomorrow morning; tightening applies immediately.
 *
 * Pure function of (active config, pending patch, requested patch, tonight's
 * weekday). Returns the new active config and the new pending patch. The
 * caller (ConfigStore) persists both and merges pending at the morning
 * boundary.
 *
 * Rules per field, judged against the currently effective value:
 * - Loosen (deferred to morning): later bedtime, earlier wake, disabling the
 *   night, hardcore -> normal, smaller wind-down, allowlist ADDITIONS,
 *   turning DND off.
 * - Tighten (applied now, clearing any contradicted pending value): earlier
 *   bedtime, later wake, enabling, normal -> hardcore, larger wind-down,
 *   allowlist REMOVALS, turning DND on.
 * - Neutral (applied now): alarm toggle/sound, grayscale toggle, and any
 *   schedule change for a weekday other than tonight.
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
                    // Other weekdays never loosen tonight; apply directly.
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

        requested.mode?.let { mode ->
            if (mode == active.mode) {
                newPending = newPending.copy(mode = null)
            } else if (mode == Mode.HARDCORE) {
                newActive = newActive.copy(mode = mode)
                newPending = newPending.copy(mode = null)
            } else {
                newPending = newPending.copy(mode = mode)
            }
        }

        requested.windDownMinutes?.let { minutes ->
            if (minutes == active.windDownMinutes) {
                newPending = newPending.copy(windDownMinutes = null)
            } else if (minutes > active.windDownMinutes) {
                newActive = newActive.copy(windDownMinutes = minutes)
                newPending = newPending.copy(windDownMinutes = null)
            } else {
                newPending = newPending.copy(windDownMinutes = minutes)
            }
        }

        requested.allowlist?.let { requestedSet ->
            // Removals tighten (apply now); additions loosen (wait for morning).
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

        requested.dndEnabled?.let { dnd ->
            if (dnd == active.dndEnabled) {
                newPending = newPending.copy(dndEnabled = null)
            } else if (dnd) {
                newActive = newActive.copy(dndEnabled = true)
                newPending = newPending.copy(dndEnabled = null)
            } else {
                newPending = newPending.copy(dndEnabled = false)
            }
        }

        // Neutral fields: not restrictions, apply immediately.
        requested.alarmEnabled?.let { newActive = newActive.copy(alarmEnabled = it) }
        requested.grayscaleEnabled?.let { newActive = newActive.copy(grayscaleEnabled = it) }

        return Result(newActive, newPending)
    }

    /**
     * Bedtimes cluster around midnight; a bedtime before noon is treated as
     * belonging to the NEXT calendar day (00:30 is later than 23:00).
     */
    private fun isEarlierBed(candidate: Int, reference: Int): Boolean =
        normalizeBed(candidate) < normalizeBed(reference)

    private fun normalizeBed(minutes: Int): Int =
        if (minutes < 12 * 60) minutes + 24 * 60 else minutes
}
