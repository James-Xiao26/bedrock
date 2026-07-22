package app.bedrock.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AcknowledgementTest {

    @Test
    fun `accepts the exact text`() {
        assertTrue(Acknowledgement.accepts(Acknowledgement.TEXT))
    }

    @Test
    fun `forgives casing, punctuation, and spacing`() {
        val messy = Acknowledgement.TEXT
            .uppercase()
            .replace(".", "")
            .replace(",", "")
            .replace(" ", "\n  ")
        assertTrue(Acknowledgement.accepts(messy))
    }

    @Test
    fun `rejects a missing or changed word`() {
        assertFalse(Acknowledgement.accepts(Acknowledgement.TEXT.replace("addictive", "great")))
        assertFalse(Acknowledgement.accepts(Acknowledgement.TEXT.replaceFirst("phone", "")))
    }

    @Test
    fun `rejects empty input`() {
        assertFalse(Acknowledgement.accepts(""))
        assertFalse(Acknowledgement.accepts("   "))
    }
}
