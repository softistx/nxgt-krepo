package com.softistx.workflow.dsl

/** One arm of a [branch]: a name, the question that picks it, and what it declares. */
internal class BranchArm<C>(
    val name: String,
    val condition: (C) -> Boolean,
    val nodes: List<WorkflowNode<C>>,
)

internal class BranchNode<C>(
    override val name: String,
    val arms: List<BranchArm<C>>,
) : WorkflowNode<C>()

/** Where an arm's own nodes are declared. It is a [NodeSink], so every verb works inside it. */
class Arm<C> internal constructor() : NodeSink<C>()

/** Collects the arms of one [branch]. */
class BranchBuilder<C> internal constructor(
    private val branch: String,
) {
    private val arms = mutableListOf<BranchArm<C>>()
    private var fallback = false

    /**
     * An arm, taken when [condition] is the first to answer true.
     *
     * [condition] does not suspend, and that is a rule rather than an oversight: a decision that
     * needs a network call is a step that writes what it learned into the context, followed by a
     * branch on that field. Otherwise the decision is invisible in the journal and unrepeatable.
     */
    fun on(
        name: String,
        condition: (C) -> Boolean,
        block: Arm<C>.() -> Unit,
    ) {
        require(!fallback) { "branch '$branch': 'otherwise' must come last" }
        require(arms.none { it.name == name }) { "branch '$branch' already has an arm named '$name'" }
        arms += BranchArm(name, condition, Arm<C>().apply(block).nodes)
    }

    /** The arm taken when no [on] matched. At most one, and last. */
    fun otherwise(block: Arm<C>.() -> Unit) {
        require(!fallback) { "branch '$branch' already has an 'otherwise'" }
        fallback = true
        arms += BranchArm("otherwise", { true }, Arm<C>().apply(block).nodes)
    }

    internal fun build(): BranchNode<C> {
        require(arms.isNotEmpty()) { "branch '$branch' declares no arms" }
        return BranchNode(branch, arms)
    }
}

/**
 * Declares a fork: the first arm whose condition holds runs, and the others do not.
 *
 * ```kotlin
 * branch("delivery") {
 *     on("express", { it.express }) {
 *         step("notify-express") { context.also(notifier::expressBooked) }
 *     }
 *     otherwise {
 *         step("notify") { context.also(notifier::booked) }
 *     }
 * }
 * ```
 *
 * **The arm is chosen once, and the choice is journaled.** A resume reads it back rather than asking
 * the conditions again — otherwise a workflow that stopped after taking the express arm, and whose
 * context a later step then changed, would come back through the other arm and unwind through steps
 * that never ran. The condition is evaluated against the context as it stood at the fork, once, ever.
 *
 * A branch that matches nothing and has no `otherwise` runs nothing, which is not an error: an
 * `if` with no `else` is a legitimate thing to say.
 */
fun <C> NodeSink<C>.branch(
    name: String,
    block: BranchBuilder<C>.() -> Unit,
): Unit = add(BranchBuilder<C>(name).apply(block).build()).let { }
