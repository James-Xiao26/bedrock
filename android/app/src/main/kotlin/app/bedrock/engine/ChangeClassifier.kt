package app.bedrock.engine

/**
 * Implements the settings freeze for the allowlist: additions only take effect
 * at the next window; removals apply immediately. Schedule edits are NOT frozen
 * - they apply immediately for every weekday, including the current one.
 *
 * Pure function of (active config, pending patch, requested patch). Returns the
 * new active config and the new pending patch. The caller (ConfigStore)
 * persists both and merges pending at the window boundary.
 *
 * Rules per field, judged against the currently effective value:
 * - Schedule: applied now, always.
 * - Loosen (deferred): allowlist ADDITIONS.
 * - Tighten (applied now, clearing any contradicted pending value): allowlist
 *   REMOVALS.
 * - Setting a field back to its active value cancels the pending change.
 */
object ChangeClassifier {

    data class Result(val active: BedrockConfig, val pending: ConfigPatch)

    fun classify(
        active: BedrockConfig,
        pending: ConfigPatch,
        requested: ConfigPatch,
    ): Result {
        var newActive = active
        var newPending = pending

        requested.schedule?.let { days ->
            val activeDays = active.schedule.toMutableMap()
            val pendingDays = (pending.schedule ?: emptyMap()).toMutableMap()
            for ((day, requestedPlan) in days) {
                activeDays[day] = requestedPlan
                pendingDays.remove(day)
            }
            newActive = newActive.copy(schedule = activeDays)
            newPending = newPending.copy(schedule = pendingDays.ifEmpty { null })
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
}
