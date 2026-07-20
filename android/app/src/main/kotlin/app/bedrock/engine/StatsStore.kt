package app.bedrock.engine

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Local usage stats: one small record per block window, appended at window
 * end. Kotlin is the single writer; Dart reads only the aggregated
 * [StatsSummary] over the channel. Stored as a JSON list in the same
 * SharedPreferences file as the config so it survives with Flutter dead.
 *
 * ponytail: a prefs-backed JSON list, not Room - one row per window (~365/yr),
 * read only as aggregates. Move to Room only if the log ever needs real queries.
 */
class StatsStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("bedrock_engine", Context.MODE_PRIVATE)

    /** Append this window's record, replacing any existing row for the same window. */
    @Synchronized
    fun record(record: WindowRecord) {
        val next = (load().filterNot { it.windowKey == record.windowKey } + record)
            .takeLast(MAX_RECORDS)
        prefs.edit()
            .putString(KEY, Json.encodeToString(ListSerializer(WindowRecord.serializer()), next))
            .commit()
    }

    @Synchronized
    fun summary(): StatsSummary = StatsSummary.from(load())

    private fun load(): List<WindowRecord> =
        prefs.getString(KEY, null)
            ?.let { Json.decodeFromString(ListSerializer(WindowRecord.serializer()), it) }
            ?: emptyList()

    private companion object {
        const val KEY = "window_records"

        // ponytail: keep ~6 months; the summary only needs the streak + recents.
        const val MAX_RECORDS = 180
    }
}

@Serializable
data class WindowRecord(
    /** Date (ISO yyyy-MM-dd) of the day the window started on. */
    val windowKey: String,
    val openEpochMs: Long,
    val closeEpochMs: Long,
    /** A [WindowOutcome] name. */
    val outcome: String,
)

data class RecentWindow(val windowKey: String, val outcome: String)

/** Read-only aggregate the stats screen renders. */
data class StatsSummary(
    val currentStreak: Int,
    val windowsKept: Int,
    val totalWindows: Int,
    val recent: List<RecentWindow>,
) {
    fun toWire(): Map<String, Any?> = mapOf(
        "currentStreak" to currentStreak,
        "windowsKept" to windowsKept,
        "totalWindows" to totalWindows,
        "recent" to recent.map { mapOf("windowKey" to it.windowKey, "outcome" to it.outcome) },
    )

    companion object {
        /** Pure so the streak math is unit-testable without Android. */
        fun from(records: List<WindowRecord>): StatsSummary {
            val ordered = records.sortedBy { it.windowKey }
            val clean = ordered.count { it.outcome == WindowOutcome.CLEAN.name }
            var streak = 0
            for (r in ordered.asReversed()) {
                if (r.outcome == WindowOutcome.CLEAN.name) streak++ else break
            }
            val recent = ordered.takeLast(RECENT).map { RecentWindow(it.windowKey, it.outcome) }
            return StatsSummary(streak, clean, ordered.size, recent)
        }

        private const val RECENT = 7
    }
}
