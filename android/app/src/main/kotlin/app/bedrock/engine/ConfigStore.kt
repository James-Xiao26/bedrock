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

    private val snapshotJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @Synchronized
    fun activeConfig(): BedrockConfig =
        prefs.getString(KEY_ACTIVE, null)?.let { BedrockConfig.fromJson(it) } ?: BedrockConfig()

    @Synchronized
    fun pendingPatch(): ConfigPatch =
        prefs.getString(KEY_PENDING, null)?.let { ConfigPatch.fromJson(it) } ?: ConfigPatch.EMPTY

    @Synchronized
    fun snapshot(): Snapshot =
        prefs.getString(KEY_SNAPSHOT, null)
            ?.let {
                // A stale snapshot from an older schema (e.g. the pre-pivot
                // night/earlyExit shape) just resets to IDLE - harmless.
                runCatching { snapshotJson.decodeFromString(Snapshot.serializer(), it) }.getOrNull()
            }
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
    fun update(requested: ConfigPatch): ChangeClassifier.Result {
        val result = ChangeClassifier.classify(activeConfig(), pendingPatch(), requested)
        persist(result.active, result.pending)
        return result
    }

    /**
     * The current hardcore escape code, generating and persisting one on first
     * read. Kept out of [BedrockConfig] on purpose: it is a rotating credential,
     * not a schedule setting, and must never flow through the freeze rules.
     */
    @Synchronized
    fun hardcorePassword(): String =
        prefs.getString(KEY_PASSWORD, null) ?: rotateHardcorePassword()

    /** Generate, persist, and return a fresh escape code. */
    @Synchronized
    fun rotateHardcorePassword(): String {
        val next = HardcorePassword.generate()
        prefs.edit().putString(KEY_PASSWORD, next).commit()
        return next
    }

    /** Whether the user has finished first-run onboarding. Not engine-critical
     *  (the engine runs on defaults regardless), just gates the first-run UI. */
    @Synchronized
    fun isOnboarded(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    @Synchronized
    fun markOnboarded() = prefs.edit().putBoolean(KEY_ONBOARDED, true).commit()

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
        const val KEY_PASSWORD = "hardcore_password"
        const val KEY_ONBOARDED = "onboarded"
    }
}
