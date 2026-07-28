package app.bedrock.blocking

import app.bedrock.blocking.BlockingController.FEED_OFF_DELAY_MS
import app.bedrock.blocking.BlockingController.feedBlockingInForce
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Turning feed blocking off is a loosening change, so it lands a day late.
 * Without the delay, one tap in Settings would be a permanently cheaper escape
 * than the per-session gate on the blocker, which is the exploit this prevents.
 */
class FeedOffDelayTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `no pending switch-off means simply on or off`() {
        assertTrue(feedBlockingInForce(enabled = true, offAtMs = 0L, nowMs = now))
        assertFalse(feedBlockingInForce(enabled = false, offAtMs = 0L, nowMs = now))
    }

    @Test
    fun `feeds stay blocked right up to the deadline`() {
        val offAt = now + FEED_OFF_DELAY_MS
        assertTrue(feedBlockingInForce(true, offAt, now))
        assertTrue(feedBlockingInForce(true, offAt, offAt - 1))
    }

    @Test
    fun `feeds unblock once the deadline passes`() {
        val offAt = now + FEED_OFF_DELAY_MS
        assertFalse(feedBlockingInForce(true, offAt, offAt))
        assertFalse(feedBlockingInForce(true, offAt, offAt + 1))
    }

    @Test
    fun `the delay is a full day`() {
        assertTrue(FEED_OFF_DELAY_MS == 24 * 60 * 60_000L)
    }
}
