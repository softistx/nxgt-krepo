package com.softistx.workflow.dsl

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How long to wait before attempt number [attempt] — which is 2 for the first retry. */
fun interface Backoff {
    fun before(attempt: Int): Duration
}

/** The same wait every time. */
fun fixed(delay: Duration): Backoff = Backoff { delay }

/**
 * [initial], then [initial] × [factor] each time, never more than [max].
 *
 * The ceiling is not optional in practice: without it the fifth retry of a step configured with a
 * second's initial delay waits sixteen, and the instance's lock outlives the usefulness of holding it.
 */
fun exponential(
    initial: Duration,
    factor: Double = 2.0,
    max: Duration = 30.seconds,
): Backoff {
    require(factor >= 1.0) { "factor must be at least 1.0, was $factor" }
    return Backoff { attempt ->
        val scaled = initial * factor.pow(attempt - 2)
        if (scaled > max) max else scaled
    }
}

private fun Double.pow(n: Int): Double {
    var result = 1.0
    repeat(n.coerceAtLeast(0)) { result *= this }
    return result
}

/**
 * How many times a node is tried, how long between tries, and what is not worth trying again.
 *
 * The default is [once] — no retry at all. Retrying is a decision with a cost (a step that is not
 * idempotent, tried twice, charges twice), so it is asked for rather than assumed.
 */
class RetryPolicy internal constructor(
    /** Total attempts, including the first. 1 means no retry. */
    val times: Int,
    val backoff: Backoff,
    internal val retryable: (Throwable) -> Boolean,
) {
    init {
        require(times >= 1) { "times must be at least 1, was $times" }
    }

    companion object {
        /** One attempt, no retry. */
        val once: RetryPolicy = RetryPolicy(1, fixed(Duration.ZERO)) { true }
    }
}

/**
 * `retry { times = 3; backoff = exponential(100.milliseconds) }`.
 *
 * [unless] is the declared half of "do not try this again"; [com.softistx.workflow.NonRetryableException]
 * is the thrown half. Both exist because the two live in different places: whether a *policy*
 * considers a class of failure permanent is a property of the workflow, and whether *this* failure
 * is permanent is something only the code that hit it knows.
 */
class RetryBuilder internal constructor() {
    /** Total attempts, including the first. */
    var times: Int = 3

    var backoff: Backoff = fixed(100.milliseconds)

    private var permanent: (Throwable) -> Boolean = { false }

    /** Failures matching [predicate] are final: the node fails on the spot, with no further attempt. */
    fun unless(predicate: (Throwable) -> Boolean) {
        val previous = permanent
        permanent = { previous(it) || predicate(it) }
    }

    internal fun build(): RetryPolicy {
        val permanent = this.permanent
        return RetryPolicy(times, backoff) { !permanent(it) }
    }
}

/**
 * `step("charge") { … } retry { times = 3 }`
 *
 * These are written once per node type rather than over a shared supertype. A common base would
 * have to be public for the infix functions to reach its properties, which would put `retry` and
 * `timeout` on a type nobody names — a page of API surface bought to save four lines.
 */
infix fun <C> Step<C>.retry(block: RetryBuilder.() -> Unit): Step<C> = apply { retry = RetryBuilder().apply(block).build() }

/** The policy, when it was built elsewhere and is shared between nodes. */
infix fun <C> Step<C>.retry(policy: RetryPolicy): Step<C> = apply { retry = policy }

/**
 * `step("confirm") { … } timeout 10.seconds`
 *
 * The clock covers **one attempt**, not the node: a step with three attempts and a ten-second
 * timeout may take thirty seconds plus its backoff. Timing the node as a whole would mean each
 * retry inheriting what the previous attempt had already spent, which makes the last attempt the
 * one that is always cut short.
 */
infix fun <C> Step<C>.timeout(duration: Duration): Step<C> = apply { timeout = duration.requirePositive() }

/** As [retry], for one leg of a fan-out. */
infix fun <C, T> Leg<C, T>.retry(block: RetryBuilder.() -> Unit): Leg<C, T> = apply { retry = RetryBuilder().apply(block).build() }

infix fun <C, T> Leg<C, T>.retry(policy: RetryPolicy): Leg<C, T> = apply { retry = policy }

/** As [timeout], for one leg of a fan-out. */
infix fun <C, T> Leg<C, T>.timeout(duration: Duration): Leg<C, T> = apply { timeout = duration.requirePositive() }

/** As [retry], for a whole fan-out — a retry re-runs only the legs that have not succeeded yet. */
infix fun <C> Parallel<C>.retry(block: RetryBuilder.() -> Unit): Parallel<C> = apply { retry = RetryBuilder().apply(block).build() }

infix fun <C> Parallel<C>.retry(policy: RetryPolicy): Parallel<C> = apply { retry = policy }

/** As [timeout], for a whole fan-out — the clock covers every leg together. */
infix fun <C> Parallel<C>.timeout(duration: Duration): Parallel<C> = apply { timeout = duration.requirePositive() }

private fun Duration.requirePositive(): Duration {
    require(this > Duration.ZERO) { "timeout must be positive, was $this" }
    return this
}
