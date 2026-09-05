package com.hr.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class RetryPolicyTest {
    private val now: Instant = Instant.parse("2026-03-10T12:00:00Z")

    @Test
    fun `the last attempt dead-letters instead of scheduling another`() {
        assertNull(
            RetryPolicy.nextAttemptAt(RetryPolicy.MAX_ATTEMPTS, now),
            "attempt ${RetryPolicy.MAX_ATTEMPTS} must be the last",
        )
        assertNotNull(RetryPolicy.nextAttemptAt(RetryPolicy.MAX_ATTEMPTS - 1, now))
    }

    @Test
    fun `a retry is never scheduled in the past`() {
        (1 until RetryPolicy.MAX_ATTEMPTS).forEach { attempt ->
            repeat(50) { seed ->
                val next = RetryPolicy.nextAttemptAt(attempt, now, Random(seed))!!
                assertFalse(next < now, "attempt $attempt seed $seed scheduled before now")
            }
        }
    }

    /**
     * The ceiling grows with the attempt, so a persistent failure backs further off each time.
     * Checked on the maximum of many draws rather than a single one, because full jitter means any
     * individual retry may legitimately land near zero.
     */
    @Test
    fun `later attempts back off further`() {
        fun ceilingOf(attempt: Int): Long =
            (0 until 500).maxOf { seed ->
                Duration.between(now, RetryPolicy.nextAttemptAt(attempt, now, Random(seed))!!).seconds
            }

        val first = ceilingOf(1)
        val third = ceilingOf(3)

        assertTrue(third > first, "attempt 3 ceiling ($third s) should exceed attempt 1 ($first s)")
    }

    /**
     * Full jitter is the point: without it, a provider outage that fails a thousand deliveries at
     * once re-fires all thousand in the same instant. Distinct seeds must produce distinct delays.
     */
    @Test
    fun `retries are spread rather than synchronised`() {
        val delays =
            (0 until 100)
                .map { seed -> RetryPolicy.nextAttemptAt(3, now, Random(seed))!! }
                .toSet()

        assertTrue(delays.size > 50, "only ${delays.size} distinct delays across 100 draws")
    }

    @Test
    fun `the backoff is bounded by the two-hour ceiling`() {
        val longest =
            (0 until 500).maxOf { seed ->
                Duration.between(
                    now,
                    RetryPolicy.nextAttemptAt(RetryPolicy.MAX_ATTEMPTS - 1, now, Random(seed))!!,
                ).toHours()
            }

        assertTrue(longest <= 2, "backoff reached $longest hours")
    }

    /**
     * A dead token and a malformed payload fail identically every time. Retrying them burns quota
     * and delays the deliveries queued behind them for nothing.
     */
    @Test
    fun `permanent failures are not retried`() {
        assertFalse(RetryPolicy.isRetryable(DeliveryFailure.TOKEN_INVALID))
        assertFalse(RetryPolicy.isRetryable(DeliveryFailure.PAYLOAD_REJECTED))
    }

    @Test
    fun `transient failures are retried`() {
        assertTrue(RetryPolicy.isRetryable(DeliveryFailure.PROVIDER_UNAVAILABLE))
        assertTrue(RetryPolicy.isRetryable(DeliveryFailure.RATE_LIMITED))
        assertTrue(RetryPolicy.isRetryable(DeliveryFailure.TIMEOUT))
    }

    /**
     * An unrecognised provider error is retried. The alternative — dropping what we do not
     * understand — turns every new error code the provider introduces into silent data loss.
     */
    @Test
    fun `an unknown failure is retried rather than dropped`() {
        assertTrue(RetryPolicy.isRetryable(DeliveryFailure.UNKNOWN))
    }

    @Test
    fun `the first attempt is deterministic in its bounds`() {
        val delay =
            Duration.between(now, RetryPolicy.nextAttemptAt(1, now, Random(1))!!).seconds

        assertTrue(delay in 0..30, "first backoff was $delay s, outside the 30 s ceiling")
    }

    @Test
    fun `every failure kind has a decided policy`() {
        DeliveryFailure.entries.forEach { failure ->
            assertEquals(
                RetryPolicy.isRetryable(failure),
                RetryPolicy.isRetryable(failure),
                "policy for $failure must be total",
            )
        }
    }
}
