package com.softistx.amqp.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How long to wait before each attempt, and therefore how many there are.
 *
 * The list *is* the policy: three delays means three retries after the first failure, and the
 * fourth failure parks the message. Written this way because the two questions a policy is asked in
 * an incident — how many times, and how long until it gives up — are both read straight off it.
 *
 * The delays should be far apart. A retry a second later is a retry against the same outage; the
 * point of the last one being minutes away is to survive a dependency's restart rather than to
 * hammer it while it comes back.
 */
data class RetryPolicy(
    val delays: List<Duration>,
) {
    init {
        require(delays.isNotEmpty()) { "a retry policy has at least one delay" }
        require(delays.all { it > Duration.ZERO }) { "a retry delay is positive: $delays" }
    }

    val attempts: Int get() = delays.size

    /** How long a message can live in the retry path before it is parked. */
    val window: Duration get() = delays.reduce(Duration::plus)

    companion object {
        /** Seconds, then half a minute, then five: an outage, a restart, and a deploy. */
        val Default = RetryPolicy(listOf(5.seconds, 30.seconds, 5.minutes))

        /** [attempts] delays, each [factor] times the last, never longer than [max]. */
        fun backoff(
            attempts: Int = 3,
            first: Duration = 5.seconds,
            factor: Double = 6.0,
            max: Duration = 1.hours,
        ): RetryPolicy {
            require(attempts > 0) { "a positive number of attempts, not $attempts" }
            require(factor >= 1.0) { "a backoff grows: $factor" }
            var delay = first
            return RetryPolicy(
                List(attempts) {
                    delay.coerceAtMost(max).also { delay = delay.times(factor) }
                },
            )
        }

        /** The same delay every time — for a dependency whose recovery time is known. */
        fun fixed(
            attempts: Int = 3,
            delay: Duration = 1.minutes,
        ): RetryPolicy = RetryPolicy(List(attempts) { delay })
    }
}
