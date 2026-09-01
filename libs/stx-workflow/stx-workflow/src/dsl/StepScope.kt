package com.softistx.workflow.dsl

import kotlin.time.Instant

/**
 * What a block of a workflow can see about the run it is part of.
 *
 * The metadata is not decoration. **A checkpointed engine is at-least-once**: the checkpoint is
 * written after the effect, so a process that dies in between replays the node when the instance
 * resumes. Correct step code is therefore idempotent, and [idempotencyKey] is what this scope exists
 * to hand it — a string that is the same on every replay of the same node of the same instance, and
 * different for every other one. A block that only received the context could not build one.
 *
 * ```kotlin
 * step("charge") {
 *     val id = payments.charge(context.card, key = idempotencyKey)
 *     context.copy(chargeId = id)
 * }
 * ```
 *
 * The same scope is what a compensation and a `merge` receive, so they can log and key the same way.
 * In a compensation, [attempt] counts that compensation's own attempts, and [context] is the last
 * checkpointed context — the one the workflow had reached when it started unwinding, which is the
 * one holding whatever the step being undone wrote.
 */
class StepScope<C> internal constructor(
    /** The workflow's context as this node found it. */
    val context: C,
    val instanceId: String,
    val workflowName: String,
    /** The node's qualified name — `"provision/charge"` for one leg of the `provision` fan-out. */
    val stepName: String,
    /** 1 on the first try. */
    val attempt: Int,
    /** When this attempt began. */
    val startedAt: Instant,
) {
    /**
     * A key for the remote call this node makes, stable across every replay of it.
     *
     * It is the same string on a retry, on a resume after a crash, and on a second engine picking
     * the instance up — and it differs for every other node, every other instance and every other
     * workflow. That is exactly the contract a payment provider's idempotency key asks for, which is
     * the whole reason this is a property rather than a sentence in the documentation: an
     * interpolation retyped at each call site is one somebody eventually types as `instanceId`
     * alone, and finds out on the day two charges go through.
     *
     * [workflowName] is in it because an id may be the caller's own — `engine.start(flow, ctx, id =
     * orderId)` is a fair thing to do, and two workflows would otherwise collide on one order.
     *
     * **It is tied to the node's name.** Renaming a step changes the keys of every instance still in
     * flight, so a node that has already run somewhere is renamed with the same care as the workflow
     * itself.
     */
    val idempotencyKey: String get() = "$workflowName:$instanceId:$stepName"

    /**
     * [idempotencyKey], for a node that makes **more than one** call that needs one.
     *
     * A step is free to do two things; what it must not do is give them the same key, which is what
     * happens by default and what a provider reads as "this is the call I already handled".
     *
     * ```kotlin
     * step("settle") {
     *     payments.charge(context.card, key = idempotencyKey("charge"))
     *     payments.tip(context.card, key = idempotencyKey("tip"))
     *     context
     * }
     * ```
     */
    fun idempotencyKey(discriminator: String): String = "$idempotencyKey:$discriminator"
}
