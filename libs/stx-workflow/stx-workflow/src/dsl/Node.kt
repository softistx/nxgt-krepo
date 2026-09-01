package com.softistx.workflow.dsl

/**
 * One declared thing in a workflow: a step, a branch, or a fan-out.
 *
 * The class is public because [Step] and [Parallel] are — a caller holds one long enough to write
 * `retry { }` after it — but it has nothing public on it. There is no `WorkflowNode` a consumer can
 * write; the constructor is internal and the hierarchy is sealed, so the only nodes that exist are
 * the ones the verbs in this package build.
 */
sealed class WorkflowNode<C> {
    internal abstract val name: String
}

/**
 * Somewhere nodes are declared — a workflow, or one arm of a branch.
 *
 * This is what lets `step` and `parallel` be extension functions written once and usable at both
 * levels, and what will let a later annotation front end declare through the same single door
 * rather than growing an entry point of its own.
 *
 * It is abstract rather than sealed only because Kotlin requires a sealed hierarchy to live in one
 * package, and `WorkflowBuilder` belongs beside `Workflow`. The internal constructor is what closes
 * it: there is no third sink a consumer can write.
 */
abstract class NodeSink<C> internal constructor() {
    private val declared = mutableListOf<WorkflowNode<C>>()

    internal val nodes: List<WorkflowNode<C>> get() = declared

    internal fun <N : WorkflowNode<C>> add(node: N): N {
        declared += node
        return node
    }
}

/**
 * `"provision/charge"` — a node's name under the node that contains it.
 *
 * The journal is keyed on these, so they are what a resume matches and what an operator reads. The
 * separator is a slash rather than a colon because a Redis key already uses colons, and a node name
 * ending up inside one should not look like another level of keyspace.
 */
internal fun qualify(
    prefix: String,
    name: String,
): String = if (prefix.isEmpty()) name else "$prefix/$name"
