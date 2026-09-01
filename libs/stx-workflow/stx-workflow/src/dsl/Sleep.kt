package com.softistx.workflow.dsl

import kotlin.time.Duration

/**
 * A node that stops the workflow until a moment in time.
 *
 * It is not `delay`. A `delay` holds a coroutine, and a coroutine is in a process that will be
 * redeployed on Thursday; this writes the wake-up time to the store and lets the instance go, so a
 * seven-day cool-off costs a record and a score in a sorted set rather than an uptime requirement.
 *
 * It follows that **a sleep needs somebody to come back for it** — a `WorkflowWorker`, or an
 * application scheduler calling `resume`. Nothing wakes an instance that no one is polling for.
 */
class Sleep<C> internal constructor(
    internal override val name: String,
    internal val duration: StepScope<C>.() -> Duration,
) : WorkflowNode<C>()

/**
 * Declares a pause of a fixed length.
 *
 * ```kotlin
 * sleep("cool-off", 10.minutes)
 * ```
 */
fun <C> NodeSink<C>.sleep(
    name: String,
    duration: Duration,
): Sleep<C> = sleep(name) { duration }

/**
 * Declares a pause whose length the context decides.
 *
 * ```kotlin
 * sleep("back-off") { context.retryAfter }
 * ```
 *
 * The block is evaluated **once**, when the instance parks, and the resulting instant is what is
 * stored — so a resume does not re-ask and does not extend the wait. A duration that is zero or
 * negative is a wake-up already due, which is a pause of nothing rather than an error: `retryAfter`
 * computed from a header that has since passed should let the workflow run, not fail it.
 */
fun <C> NodeSink<C>.sleep(
    name: String,
    duration: StepScope<C>.() -> Duration,
): Sleep<C> = add(Sleep(name, duration))
