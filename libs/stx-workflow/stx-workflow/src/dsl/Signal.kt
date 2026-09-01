package com.strange.workflow.dsl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.time.Duration

/**
 * The name and type of something the outside world will tell this workflow.
 *
 * ```kotlin
 * private val APPROVAL = signal<Approval>("approval")
 * ```
 *
 * A key rather than a bare string for the same reason [Outcome] is one: the payload is persisted
 * before the workflow reads it, so somebody has to hold its serializer, and the alternative is every
 * `await` restating a type the delivering call site must then guess right.
 *
 * [name] is what an operator, an HTTP handler or a message consumer names when it delivers —
 * `engine.signal(id, APPROVAL, Approval(by = "ops"))` — so it is part of the workflow's contract with
 * the outside and has to stay stable across deploys.
 */
class Signal<T> internal constructor(
    val name: String,
    internal val serializer: KSerializer<T>,
)

inline fun <reified T> signal(name: String): Signal<T> = signal(name, serializer())

/** The same key, named by its serializer — for a call site whose `T` cannot be reified. */
fun <T> signal(
    name: String,
    serializer: KSerializer<T>,
): Signal<T> = Signal(name, serializer)

/**
 * A node that stops the workflow until somebody delivers [signal].
 *
 * This is where the process being killed stops being a problem to survive and becomes the *point*:
 * an instance waiting on a human approval is going to outlive every process that ever touches it,
 * and it costs a record in a store rather than a coroutine parked in someone's heap for two days.
 */
class Await<C, T> internal constructor(
    internal override val name: String,
    internal val signal: Signal<T>,
    internal val body: suspend StepScope<C>.(T) -> C,
) : WorkflowNode<C>() {
    internal var deadline: Duration? = null
}

/**
 * Declares a wait for a signal, and what its payload does to the context.
 *
 * ```kotlin
 * await(APPROVAL) { approval -> context.copy(approvedBy = approval.by) } within 24.hours
 * ```
 *
 * The block is the same shape as a step's — it returns the next context — but it is handed the
 * payload, because that payload is the whole reason the workflow stopped. It runs **once**, when the
 * signal is delivered, in the process that delivered it.
 *
 * An await declares no compensation. Undoing it would mean un-approving something, which is not an
 * effect this library performed and not one it can reverse; what the approval *caused* is the steps
 * after it, and those compensate themselves.
 *
 * [name] defaults to the signal's, which is right whenever a workflow waits on a given signal once.
 * A workflow that waits on the same signal twice has to name the two waits apart, and the duplicate
 * check in `workflow { }` says so at build time rather than on some later resume.
 */
fun <C, T> NodeSink<C>.await(
    signal: Signal<T>,
    name: String = signal.name,
    body: suspend StepScope<C>.(T) -> C,
): Await<C, T> = add(Await(name, signal, body))

/**
 * How long to wait before giving up on the signal.
 *
 * Measured from the moment the instance parked, not from when the workflow started, and **an expiry
 * is a failure**: the node fails with [com.strange.workflow.AwaitTimeoutException] and the workflow
 * unwinds, exactly as it would for a step that threw. That is the honest default — a checkout whose
 * approval never came must release the stock it reserved — and it is the one an operator can reason
 * about without reading the engine.
 *
 * A wait with no deadline waits forever, which is a real answer and not an oversight: such an
 * instance leaves the due-time index entirely, so no worker polls it and it costs nothing but the
 * record until somebody signals or cancels it.
 */
infix fun <C, T> Await<C, T>.within(deadline: Duration): Await<C, T> {
    require(this.deadline == null) { "await '$name' already has a deadline" }
    require(deadline.isPositive()) { "await '$name' has a deadline of $deadline; it must be positive" }
    this.deadline = deadline
    return this
}
