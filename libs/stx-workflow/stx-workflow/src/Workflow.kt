package com.strange.workflow

import com.strange.workflow.dsl.NodeSink
import com.strange.workflow.dsl.RetryPolicy
import com.strange.workflow.dsl.StepScope
import com.strange.workflow.dsl.WorkflowNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.time.Duration

/**
 * A workflow: an ordered declaration of what to do, and of how to undo each part of it.
 *
 * It holds no state and runs nothing. One of these is built once, at startup, and handed to a
 * [WorkflowEngine] which runs many instances of it — so a step body must close over the collaborators
 * it needs, exactly as a `@QueryMapping` instance does in `stx-graphix`, and must not close over
 * anything that belongs to one run.
 */
class Workflow<C> internal constructor(
    val name: String,
    internal val serializer: KSerializer<C>,
    internal val nodes: List<WorkflowNode<C>>,
    /** Every node that declared a compensation, by qualified name. */
    internal val undo: Map<String, Undo<C>>,
)

/**
 * How to undo one node, flattened out of the declaration so the unwind can find it by the name it
 * reads in the journal without walking the tree again.
 *
 * [block] takes the value the node recorded — a leg's result, or null for a step — and the [Json] to
 * decode it with, which is the engine's rather than the declaration's.
 */
internal class Undo<C>(
    val retry: RetryPolicy,
    val timeout: Duration?,
    val block: suspend StepScope<C>.(JsonElement?, Json) -> Unit,
)

/**
 * Declares a workflow.
 *
 * ```kotlin
 * val checkout = workflow<Checkout>("checkout") {
 *     step("reserve") { context.copy(reservationId = stock.reserve(context.items)) }
 *         .compensate { stock.release(context.reservationId!!) }
 *     step("charge") { context.copy(chargeId = payments.charge(context.card)) }
 *         .compensate { payments.refund(context.chargeId!!) }
 * }
 * ```
 *
 * [name] is what the store records and what an engine looks a definition up by, so it has to be
 * stable across deploys: renaming a workflow orphans every instance of it that is still in flight.
 *
 * `C` is the context — one `@Serializable` type threaded through every step, and the whole of what
 * survives a restart. Two rules follow from it being persisted, and both belong in review rather
 * than in a runtime check: **every field added to it later needs a default**, or an instance written
 * by the previous deploy will not decode; and it holds *references* to large things, never the
 * things — a document's storage key, not its bytes, because the whole context is written again after
 * every node.
 */
inline fun <reified C> workflow(
    name: String,
    noinline block: WorkflowBuilder<C>.() -> Unit,
): Workflow<C> = workflow(name, serializer(), block)

/** The same declaration, named by its serializer — for a call site whose `C` cannot be reified. */
fun <C> workflow(
    name: String,
    serializer: KSerializer<C>,
    block: WorkflowBuilder<C>.() -> Unit,
): Workflow<C> = WorkflowBuilder<C>(name, serializer).apply(block).build()

/**
 * Where a workflow's nodes are declared.
 *
 * It carries no verbs of its own. `step`, `branch` and `parallel` are extension functions on
 * [NodeSink] declared in `com.strange.workflow.dsl`, which is what lets each verb live in the file
 * that owns it, lets an arm of a branch accept the same vocabulary as the workflow itself, and will
 * let a later annotation front end declare through this same single door rather than growing an
 * entry point beside it.
 */
class WorkflowBuilder<C> internal constructor(
    private val name: String,
    private val serializer: KSerializer<C>,
) : NodeSink<C>() {
    internal fun build(): Workflow<C> {
        require(nodes.isNotEmpty()) { "workflow '$name' declares no nodes" }
        val undo = mutableMapOf<String, Undo<C>>()
        index(name, prefix = "", nodes = nodes, seen = mutableSetOf(), undo = undo)
        return Workflow(name, serializer, nodes, undo)
    }
}
