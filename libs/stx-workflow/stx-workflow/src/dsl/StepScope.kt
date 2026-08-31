package com.strange.workflow.dsl

import kotlin.time.Instant

/**
 * What a block of a workflow can see about the run it is part of.
 *
 * The metadata is not decoration. **A checkpointed engine is at-least-once**: the checkpoint is
 * written after the effect, so a process that dies in between replays the node when the instance
 * resumes. Correct step code is therefore idempotent, and the usual way to make a remote call
 * idempotent is to give it a key that is stable across replays of the same node of the same
 * instance — which is exactly `"$instanceId:$stepName"`. A block that only received the context
 * would have no way to build one.
 *
 * ```kotlin
 * step("charge") {
 *     val id = payments.charge(context.card, key = "$instanceId:$stepName")
 *     context.copy(chargeId = id)
 * }
 * ```
 *
 * The same scope is what a compensation and a `merge` receive, so they can log and key the same
 * way. In a compensation, [attempt] counts that compensation's own attempts, and [context] is the
 * last checkpointed context — the one the workflow had reached when it started unwinding, which is
 * the one holding whatever the step being undone wrote.
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
)
