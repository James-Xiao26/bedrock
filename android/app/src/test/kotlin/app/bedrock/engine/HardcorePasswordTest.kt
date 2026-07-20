package app.bedrock.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HardcorePasswordTest {

    @Test
    fun `generates exactly five digits`() {
        repeat(500) {
            val code = HardcorePassword.generate()
            assertEquals(HardcorePassword.LENGTH, code.length)
            assertTrue(code.all(Char::isDigit), "non-digit in $code")
        }
    }

    @Test
    fun `keeps a fixed width even when digits are zero`() {
        // An Int-based generator would collapse "00042" to "42"; building the
        // string digit-by-digit must not. 500 draws will hit leading zeros.
        repeat(500) {
            assertEquals(HardcorePassword.LENGTH, HardcorePassword.generate().length)
        }
    }

    @Test
    fun `matches trims surrounding whitespace and rejects mismatches`() {
        assertTrue(HardcorePassword.matches("  40712 ", "40712"))
        assertFalse(HardcorePassword.matches("40713", "40712"))
        assertFalse(HardcorePassword.matches("", "40712"))
    }
}
