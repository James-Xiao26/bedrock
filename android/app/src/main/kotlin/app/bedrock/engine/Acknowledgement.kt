package app.bedrock.engine

/**
 * The free code-reset gate: instead of paying $1, the user transcribes a fixed
 * acknowledgement paragraph. Pure Kotlin so the match logic is unit-testable;
 * the UI shows [TEXT] and enables reset only when [accepts] returns true.
 *
 * Normalization forgives casing, punctuation, and spacing so a stray comma
 * doesn't make people rage-quit - but every word must still be typed in order,
 * which is the deterrent.
 */
object Acknowledgement {

    /** The paragraph the user must copy out to reset their code for free. */
    const val TEXT =
        "I understand these apps are designed to be addictive. The feed will not make " +
            "me happy - it takes my time and gives me nothing back. I am choosing to " +
            "unblock it right now, on purpose, and I take responsibility for how I " +
            "spend the rest of my day."

    /** Whether [typed] matches [TEXT] once both are normalized. */
    fun accepts(typed: String): Boolean = normalize(typed) == normalize(TEXT)

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
