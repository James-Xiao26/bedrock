package app.bedrock.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Canonical engine configuration. Pure Kotlin (no Android deps) so the
 * freeze rules and state machine are exhaustively unit-testable.
 *
 * Times are serialized as minutes-since-midnight; days as ISO weekday numbers
 * (1 = Monday .. 7 = Sunday) to keep the JSON stable across locales.
 */

@Serializable
data class NightPlan(
    /** The user's chosen bedtime, minutes since midnight. Downtime opens
     *  [BedrockConfig.windDownMinutes] earlier; the planner derives the start. */
    val bedtimeMinutes: Int,
    /** Window end, minutes since midnight. May be earlier than start (crosses midnight). */
    val wakeMinutes: Int,
    val enabled: Boolean = true,
)

@Serializable
data class BedrockConfig(
    // ponytail: bumped so old configs shed removed/renamed keys (the pre-lead
    // bedMinutes shape); ignoreUnknownKeys drops them silently on the next read.
    val schemaVersion: Int = 3,
    /** ISO day-of-week (1=Mon..7=Sun) -> the block window STARTING that day. */
    val schedule: Map<Int, NightPlan> = defaultSchedule,
    /** How long before bedtime downtime begins (the wind-down lead). */
    val windDownMinutes: Int = 30,
    /** Packages the user allows through during a window (system apps are always allowed). */
    val allowlist: Set<String> = emptySet(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        val defaultSchedule: Map<Int, NightPlan> =
            (1..7).associateWith { NightPlan(bedtimeMinutes = 23 * 60, wakeMinutes = 7 * 60) }

        fun fromJson(raw: String): BedrockConfig = json.decodeFromString(serializer(), raw)
    }
}

/**
 * A sparse, user-initiated change to the config. Every field is optional;
 * null means "not touched". The classifier decides which parts apply now
 * and which wait for the next window.
 */
@Serializable
data class ConfigPatch(
    val schedule: Map<Int, NightPlan>? = null,
    val windDownMinutes: Int? = null,
    val allowlist: Set<String>? = null,
) {
    fun isEmpty(): Boolean = this == ConfigPatch()

    fun toJson(): String = json.encodeToString(serializer(), this)

    /** Apply this patch on top of [base], returning the merged config. */
    fun appliedTo(base: BedrockConfig): BedrockConfig = base.copy(
        schedule = schedule?.let { base.schedule + it } ?: base.schedule,
        windDownMinutes = windDownMinutes ?: base.windDownMinutes,
        allowlist = allowlist ?: base.allowlist,
    )

    /** Overlay [other] on top of this patch (later fields win). */
    fun overlaidWith(other: ConfigPatch): ConfigPatch = ConfigPatch(
        schedule = when {
            other.schedule == null -> schedule
            schedule == null -> other.schedule
            else -> schedule + other.schedule
        },
        windDownMinutes = other.windDownMinutes ?: windDownMinutes,
        allowlist = other.allowlist ?: allowlist,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        val EMPTY = ConfigPatch()

        fun fromJson(raw: String): ConfigPatch = json.decodeFromString(serializer(), raw)
    }
}
