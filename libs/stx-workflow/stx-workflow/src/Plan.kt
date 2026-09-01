package com.strange.workflow

import com.strange.workflow.dsl.Await
import com.strange.workflow.dsl.BranchNode
import com.strange.workflow.dsl.Leg
import com.strange.workflow.dsl.Parallel
import com.strange.workflow.dsl.Sleep
import com.strange.workflow.dsl.Step
import com.strange.workflow.dsl.WorkflowNode
import com.strange.workflow.dsl.qualify

/**
 * Walks a declaration once, when it is built, to do the two things that must not wait until it runs.
 *
 * It **checks that every qualified name is unique**, because the journal is keyed on them: two nodes
 * called `charge` would make "has this already run" unanswerable, and the failure would show up as a
 * skipped step on some later resume rather than here, next to the mistake.
 *
 * And it **flattens the compensations into one map**. The unwind reads names out of the journal and
 * has to find the matching block; without this it would walk the tree for each entry, and it would
 * have to know how a leg's recorded value is decoded — which is knowledge that belongs where the
 * `Outcome` and its serializer are, not in the engine.
 */
internal fun <C> index(
    workflow: String,
    prefix: String,
    nodes: List<WorkflowNode<C>>,
    seen: MutableSet<String>,
    undo: MutableMap<String, Undo<C>>,
) {
    for (node in nodes) {
        val path = qualify(prefix, node.name)
        require(seen.add(path)) { "workflow '$workflow' declares '$path' twice" }
        when (node) {
            is Step -> {
                node.compensation?.let { block -> undo[path] = Undo(node.retry, node.timeout) { _, _ -> block() } }
            }

            is Parallel -> {
                @Suppress("UNCHECKED_CAST")
                node.legs.forEach { leg -> indexLeg(workflow, path, leg as Leg<C, Any?>, seen, undo) }
            }

            // An await and a sleep are named — the journal is keyed on them, and a resume matches
            // on them — but neither has an effect of its own, so neither has anything to undo.
            is Await<C, *>, is Sleep -> {
                Unit
            }

            is BranchNode -> {
                for (arm in node.arms) {
                    val armPath = qualify(path, arm.name)
                    require(seen.add(armPath)) { "workflow '$workflow' declares '$armPath' twice" }
                    index(workflow, armPath, arm.nodes, seen, undo)
                }
            }
        }
    }
}

private fun <C> indexLeg(
    workflow: String,
    prefix: String,
    leg: Leg<C, Any?>,
    seen: MutableSet<String>,
    undo: MutableMap<String, Undo<C>>,
) {
    val path = qualify(prefix, leg.key.name)
    require(seen.add(path)) { "workflow '$workflow' declares '$path' twice" }
    val block = leg.compensation ?: return
    undo[path] =
        Undo(leg.retry, leg.timeout) { value, json ->
            requireNotNull(value) { "leg '$path' succeeded but recorded no value to compensate with" }
            block(json.decodeFromJsonElement(leg.key.serializer, value))
        }
}
