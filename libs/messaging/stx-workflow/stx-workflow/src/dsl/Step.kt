package com.softistx.workflow.dsl

import kotlin.time.Duration

/**
 * One unit of work, and how to undo it.
 *
 * A step reads the context and returns the next one. It is the only node that writes the context
 * directly — a branch chooses, a fan-out merges — which is what keeps "where did this field come
 * from" answerable by reading the workflow top to bottom.
 */
class Step<C> internal constructor(
    internal override val name: String,
    internal val body: suspend StepScope<C>.() -> C,
) : WorkflowNode<C>() {
    internal var retry: RetryPolicy = RetryPolicy.once
    internal var timeout: Duration? = null
    internal var compensation: (suspend StepScope<C>.() -> Unit)? = null
}

/**
 * Declares a step.
 *
 * ```kotlin
 * step("reserve") { context.copy(reservationId = stock.reserve(context.items)) }
 * ```
 *
 * [name] is how this step appears in the journal, so it is what a resume matches on and what an
 * operator reads. It has to be unique within the workflow, which `workflow { }` checks when it is
 * built rather than when it first runs.
 */
fun <C> NodeSink<C>.step(
    name: String,
    body: suspend StepScope<C>.() -> C,
): Step<C> = add(Step(name, body))

/**
 * How to undo [Step]'s effect, when a later node fails and the workflow unwinds.
 *
 * ```kotlin
 * step("reserve") { context.copy(reservationId = stock.reserve(context.items)) }
 *     .compensate { stock.release(context.reservationId!!) }
 * ```
 *
 * It hangs off the step rather than nesting inside it for two reasons. A step with nothing to undo —
 * most of them — stays one line, instead of every step choosing between two spellings. And a block
 * nested inside the body would be re-declared on every attempt, which reads like code that runs now
 * and is not.
 *
 * **The context a compensation sees is the last one checkpointed**, not a snapshot from when its own
 * step finished. In the ordinary case, where steps only add to the context, that is strictly more
 * information. It does mean a step that *removes* what an earlier step's compensation needs has
 * broken it — so do not; a workflow's context accumulates.
 */
infix fun <C> Step<C>.compensate(block: suspend StepScope<C>.() -> Unit): Step<C> {
    require(compensation == null) { "step '$name' already has a compensation" }
    compensation = block
    return this
}
