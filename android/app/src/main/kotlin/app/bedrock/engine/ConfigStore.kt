package app.bedrock.engine

import android.content.Context
import android.content.SharedPreferences

/**
 * Canonical persistence for config + session snapshot. Kotlin is the single
 * writer; Dart only sees projections over the channel. SharedPreferences so
 * receivers and the widget can read synchronously with the Flutter engine
 * dead. All writes are synchronous (commit) because the process may be
 * killed right after a transition.
 */
class ConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bedrock_engine", Context.MODE_PRIVATE)

    @Synchronized
    fun activeConfig(): BedrockConfig =
        prefs.getString(KEY_ACTIVE, null)?.let { BedrockConfig.fromJson(it) } ?: BedrockConfig()

    @Synchronized
    fun pendingPatch(): ConfigPatch =
        prefs.getString(KEY_PENDING, null)?.let { ConfigPatch.fromJson(it) } ?: ConfigPatch.EMPTY

    @Synchronized
    fun snapshot(): Snapshot =
        prefs.getString(KEY_SNAPSHOT, null)
            ?.let { kotlinx.serialization.json.Json.decodeFromString(Snapshot.serializer(), it) }
            ?: Snapshot()

    @Synchronized
    fun saveSnapshot(snapshot: Snapshot) {
        prefs.edit()
            .putString(
                KEY_SNAPSHOT,
                kotlinx.serialization.json.Json.encodeToString(Snapshot.serializer(), snapshot),
            )
            .commit()
    }

    /**
     * Route a user-requested change through the freeze rules. Returns the
     * resulting (active, pending) pair after persisting both.
     */
    @Synchronized
    fun update(requested: ConfigPatch, tonightDay: Int): ChangeClassifier.Result {
        val result = ChangeClassifier.classify(activeConfig(), pendingPatch(), requested, tonightDay)
        persist(result.active, result.pending)
        return result
    }

    /** Morning boundary: fold pending loosenings into the active config. */
    @Synchronized
    fun mergePending(): BedrockConfig {
        val pending = pendingPatch()
        val merged = pending.appliedTo(activeConfig())
        persist(merged, ConfigPatch.EMPTY)
        return merged
    }

    private fun persist(active: BedrockConfig, pending: ConfigPatch) {
        prefs.edit()
            .putString(KEY_ACTIVE, active.toJson())
            .putString(KEY_PENDING, pending.toJson())
            .commit()
    }

    private companion object {
        const val KEY_ACTIVE = "config_active"
        const val KEY_PENDING = "config_pending"
        const val KEY_SNAPSHOT = "session_snapshot"
    }
}
