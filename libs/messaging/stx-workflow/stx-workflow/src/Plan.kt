package com.softistx.workflow

import com.softistx.workflow.dsl.Await
import com.softistx.workflow.dsl.BranchNode
import com.softistx.workflow.dsl.Child
import com.softistx.workflow.dsl.Leg
import com.softistx.workflow.dsl.Parallel
import com.softistx.workflow.dsl.RetryPolicy
import com.softistx.workflow.dsl.Sleep
import com.softistx.workflow.dsl.Step
import com.softistx.workflow.dsl.WorkflowNode
import com.softistx.workflow.dsl.qualify

/**
 * Walks a declaration once, when it is built, to do the two things that must not wait until it runs.
 *
 * It **checks that every qualified name is unique**, because the journal is keyed on them: two nodes
 * called `charge` would make "has this already run" unanswerable, and the failure would show up as a
 * skipped step on some later resume rather than here, next to the mistake.
 *
 * It **collects the signals the declaration waits on**, so that a delivery naming one the workflow
 * has no `await` for is refused at the door rather than stored for a wait that will never come.
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
    signals: MutableSet<String>,
    children: MutableMap<String, Workflow<*>>,
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
            // on them — but neither has an effect of its own, so neither has anything to undo. An
            // await does have a name the outside world delivers under, and that is collected here.
            is Await<C, *> -> {
                signals += node.signal.name
            }

            // A child node always has something to undo — the child instance — so it is always in
            // the undo map. Its block is never called: the unwind finds the path in `children`
            // first and undoes the instance instead, which is the only thing that knows how.
            is Child<C, *> -> {
                children[path] = node.workflow
                undo[path] = Undo(RetryPolicy.once, timeout = null) { _, _ -> }
            }

            is Sleep -> {
                Unit
            }

            is BranchNode -> {
                for (arm in node.arms) {
                    val armPath = qualify(path, arm.name)
                    require(seen.add(armPath)) { "workflow '$workflow' declares '$armPath' twice" }
                    index(workflow, armPath, arm.nodes, seen, undo, signals, children)
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
