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
        "I understand that my phone is designed to be addictive. Choosing to use it " +
            "right now can worsen my mood, drain my energy tomorrow, and pull me away " +
            "from what I actually care about. I am resetting my code on purpose, and I " +
            "take responsibility for how I spend the rest of my night."

    /** Whether [typed] matches [TEXT] once both are normalized. */
    fun accepts(typed: String): Boolean = normalize(typed) == normalize(TEXT)

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
