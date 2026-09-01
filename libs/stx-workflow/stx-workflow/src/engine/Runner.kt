package com.strange.workflow.engine

import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.Await
import com.strange.workflow.dsl.BranchNode
import com.strange.workflow.dsl.Sleep
import com.strange.workflow.dsl.Step
import com.strange.workflow.dsl.WorkflowNode
import com.strange.workflow.dsl.qualify
import com.strange.workflow.store.NodeOutcome
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

/**
 * Walks the declaration, running what the journal says has not run yet.
 *
 * There is no cursor. A node is skipped when its last journal entry says it succeeded, which is the
 * one rule that works at every level — a top-level step, a step inside a branch arm, one leg of a
 * fan-out — where an index into a flat list only works for the first of the three.
 */
internal suspend fun <C> Run<C>.advance() {
    // An instance that was already unwinding resumes its unwind. Running the declaration forward
    // again would find the nodes it had just compensated no longer marked as succeeded, take that
    // for "never ran", and do them a second time — which is how a refunded charge gets charged again.
    if (record.status == WorkflowStatus.Compensating) {
        unwind()
        return
    }
    try {
        runNodes(prefix = "", nodes = workflow.nodes)
    } catch (_: Paused) {
        // Stopped on purpose, and already checkpointed by whichever node stopped. Nothing to write
        // and nothing to report: the instance is exactly as correct as a completed one.
        return
    } catch (failure: NodeFailed) {
        journal(
            node = failure.node,
            outcome = NodeOutcome.Failed,
            attempts = failure.attempts,
            error =
                com.strange.workflow.WorkflowError
                    .of(failure.node, failure.cause, failure.attempts),
        )
        checkpoint {
            it.copy(
                status = WorkflowStatus.Compensating,
                error = it.journal.last().error,
                awaiting = null,
                signals = emptyMap(),
                wakeAt = null,
            )
        }
        unwind()
        return
    }
    checkpoint { it.copy(status = WorkflowStatus.Completed) }
}

internal suspend fun <C> Run<C>.runNodes(
    prefix: String,
    nodes: List<WorkflowNode<C>>,
) {
    for (node in nodes) {
        when (node) {
            is Step -> runStep(qualify(prefix, node.name), node)
            is BranchNode -> runBranch(qualify(prefix, node.name), node)
            is com.strange.workflow.dsl.Parallel -> runParallel(qualify(prefix, node.name), node)
            is Await<C, *> -> runAwait(qualify(prefix, node.name), node)
            is Sleep -> runSleep(qualify(prefix, node.name), node)
        }
    }
}

private suspend fun <C> Run<C>.runStep(
    path: String,
    node: Step<C>,
) {
    if (record.succeeded(path) != null) return

    val startedAt = Clock.System.now()
    val outcome =
        try {
            attempt(node.retry, node.timeout) { n -> node.body(scope(path, n, startedAt)) }
        } catch (failure: NodeFailure) {
            throw NodeFailed(path, failure.cause, failure.attempts)
        }

    context = outcome.value
    journal(path, NodeOutcome.Succeeded, outcome.attempts, context = encoded())
}

/**
 * Takes the fork, or reads back the one that was already taken.
 *
 * **The decision is written before the arm runs, not after.** An instance that stopped halfway
 * through the express arm and then resumed would otherwise ask the conditions again — against a
 * context its own steps have since changed — take the other arm, and leave the engine unwinding
 * through steps that never happened. Recording it first costs one write and makes the fork a fact
 * rather than a guess.
 *
 * The branch node's own `Succeeded` entry therefore means "this fork has been decided", not "the
 * arm finished". Nothing conflates the two, because a branch declares no compensation and so never
 * appears in the unwind.
 */
private suspend fun <C> Run<C>.runBranch(
    path: String,
    node: BranchNode<C>,
) {
    val decided = record.succeeded(path)
    val armName =
        if (decided != null) {
            decided.value?.jsonPrimitive?.contentOrNull
        } else {
            val chosen = node.arms.firstOrNull { it.condition(context) }?.name
            journal(path, NodeOutcome.Succeeded, attempts = 1, value = JsonPrimitive(chosen))
            chosen
        }

    val arm = node.arms.firstOrNull { it.name == armName } ?: return
    runNodes(qualify(path, arm.name), arm.nodes)
}
