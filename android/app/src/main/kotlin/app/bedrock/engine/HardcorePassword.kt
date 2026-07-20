package app.bedrock.engine

import kotlin.random.Random

/**
 * The hardcore escape code: a short numeric PIN the user saves during the day
 * and must type to unlock a locked night. Pure Kotlin so generation is
 * unit-testable; the store owns persistence and rotation.
 */
object HardcorePassword {

    /** Number of digits in an escape code. */
    const val LENGTH = 5

    /** A fresh random code, e.g. "40712". Zero-padded to [LENGTH] digits. */
    fun generate(random: Random = Random.Default): String =
        buildString(LENGTH) { repeat(LENGTH) { append(random.nextInt(10)) } }

    /** Whether [input] (after trimming) matches [code] exactly. */
    fun matches(input: String, code: String): Boolean = input.trim() == code
}
