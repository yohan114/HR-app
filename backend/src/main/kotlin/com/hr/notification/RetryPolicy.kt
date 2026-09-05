package com.hr.notification

import java.time.Duration
import java.time.Instant
import kotlin.math.pow
import kotlin.random.Random

/**
 * When to try a failed delivery again, and when to stop.
 *
 * Exponential backoff with full jitter. The jitter is not decoration: a provider outage fails every
 * pending delivery at once, and without it every retry re-fires in the same instant, arriving as a
 * thundering herd precisely when the provider is least able to absorb one. Full jitter — a uniform
 * draw over the whole window rather than a small wobble around its edge — spreads a synchronised
 * batch the most for a given ceiling.
 */
object RetryPolicy {
    const val MAX_ATTEMPTS = 5

    private val BASE_DELAY: Duration = Duration.ofSeconds(30)
    private val MAX_DELAY: Duration = Duration.ofHours(2)

    /**
     * The next attempt time, or null when [attempt] has exhausted [MAX_ATTEMPTS] and the delivery
     * should be dead-lettered.
     *
     * @param attempt the attempt that just failed, 1-based.
     */
    fun nextAttemptAt(
        attempt: Int,
        now: Instant,
        random: Random = Random.Default,
    ): Instant? {
        if (attempt >= MAX_ATTEMPTS) return null

        val exponential = BASE_DELAY.seconds * 2.0.pow(attempt - 1)
        val ceiling = minOf(exponential, MAX_DELAY.seconds.toDouble()).toLong()
        // Full jitter: uniform over [0, ceiling]. `nextLong` needs a positive bound.
        val delay = if (ceiling <= 0) 0L else random.nextLong(ceiling + 1)
        return now.plusSeconds(delay)
    }

    /**
     * Whether a provider failure is worth retrying.
     *
     * A dead token or a malformed payload will fail identically five times, and retrying it costs
     * quota, delays the deliveries behind it, and tells us nothing we did not know on the first
     * attempt. Only failures that might plausibly differ next time are retried.
     */
    fun isRetryable(failure: DeliveryFailure): Boolean =
        when (failure) {
            DeliveryFailure.TOKEN_INVALID, DeliveryFailure.PAYLOAD_REJECTED -> false
            DeliveryFailure.PROVIDER_UNAVAILABLE,
            DeliveryFailure.RATE_LIMITED,
            DeliveryFailure.TIMEOUT,
            DeliveryFailure.UNKNOWN,
            -> true
        }
}

/**
 * Why a delivery attempt failed, normalised across providers.
 *
 * [TOKEN_INVALID] is the one with a side effect beyond the retry decision: FCM's `UNREGISTERED` and
 * APNs' `410 Gone` both mean the app is gone from that device, and the token must be cleared so the
 * reachability index stops pointing at it.
 */
enum class DeliveryFailure {
    TOKEN_INVALID,
    PAYLOAD_REJECTED,
    PROVIDER_UNAVAILABLE,
    RATE_LIMITED,
    TIMEOUT,
    UNKNOWN,
}
