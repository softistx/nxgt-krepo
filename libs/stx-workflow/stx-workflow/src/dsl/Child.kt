package com.softistx.workflow.dsl

import com.softistx.workflow.Workflow
import kotlin.time.Duration

/**
 * A node that runs another workflow and waits for it.
 *
 * The child is an instance in its own right — its own record, its own journal, its own
 * compensations — not a subroutine. That is the whole point: a fulfilment that takes two days and
 * parks on a courier's callback cannot be a function call inside the parent's step, because the
 * parent's step would have to stay in memory for two days.
 *
 * Its id is derived from the parent's and the node's path, so it is the same on every replay of the
 * node: a parent that dies between starting the child and checkpointing that it did finds the child
 * already there when it comes back, rather than starting a second one.
 */
class Child<C, D> internal constructor(
    internal override val name: String,
    internal val workflow: Workflow<D>,
    internal val start: StepScope<C>.() -> D,
    internal val body: suspend StepScope<C>.(D) -> C,
) : WorkflowNode<C>() {
    internal var deadline: Duration? = null
}

/**
 * Declares a child workflow, and what its result does to the parent's context.
 *
 * ```kotlin
 * child("fulfil", fulfilment, with = { Fulfilment(orderId = context.orderId) }) { fulfilled ->
 *     context.copy(trackingId = fulfilled.trackingId)
 * }
 * ```
 *
 * [with] builds the child's starting context from the parent's, and runs **once** — when the child
 * is started. [body] receives the child's **final context** and returns the parent's next one, the
 * same shape as `await`, because a child that finished is a payload that arrived.
 *
 * Only a child that reaches `Completed` feeds [body]. One that compensated, failed or was cancelled
 * fails this node instead, and the parent unwinds through everything before it — the honest reading
 * of "the thing I delegated could not be done".
 *
 * **The compensation is implicit and is not [body]'s business.** Undoing this node means undoing the
 * child, which is `undo` on the child instance: its own compensations, in its own reverse order,
 * checkpointed in its own journal. Writing that by hand is the version that forgets a case.
 *
 * The child must be registered with the same engine. An instance nobody can look up again is one
 * nobody can resume, and a child outlives the process that started it exactly as a parent does.
 */
fun <C, D> NodeSink<C>.child(
    name: String,
    workflow: Workflow<D>,
    with: StepScope<C>.() -> D,
    body: suspend StepScope<C>.(D) -> C,
): Child<C, D> = add(Child(name, workflow, with, body))

/**
 * How long to wait for the child before giving up on it.
 *
 * Measured from the moment the parent parked, and an expiry **fails this node**, exactly as a
 * missed `await` deadline does. Nothing is cancelled *here*: undoing the child is the parent's
 * unwind's business, and it reaches this node on its way back — a child node owes its compensation
 * from the moment it started the instance, not from the moment it succeeded, precisely so that a
 * deadline does not leave a running instance behind with nobody left to stop it.
 */
infix fun <C, D> Child<C, D>.within(deadline: Duration): Child<C, D> {
    require(this.deadline == null) { "child '$name' already has a deadline" }
    require(deadline.isPositive()) { "child '$name' has a deadline of $deadline; it must be positive" }
    this.deadline = deadline
    return this
}
