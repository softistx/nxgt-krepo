package com.softistx.workflow.engine

import com.softistx.workflow.ChildFailedException
import com.softistx.workflow.ChildLostException
import com.softistx.workflow.ChildTimeoutException
import com.softistx.workflow.Workflow
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowException
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.Child
import com.softistx.workflow.store.JournalEntry
import com.softistx.workflow.store.NodeOutcome
import com.softistx.workflow.store.WorkflowRecord
import kotlin.time.Clock

/**
 * Runs another workflow and waits for it.
 *
 * The child's id is derived — `"<parent>/<path>"` — and that is what makes this node replayable. A
 * parent that dies between starting the child and checkpointing that it did comes back, derives the
 * same id, finds the instance already there and carries on with it. Nothing here asks the store
 * whether it has "already started one", because there is nothing to ask: the name answers it.
 *
 * Waiting is a park, not a held coroutine, for the same reason `await` is. A child that takes two
 * days is two days of the parent being a record.
 */
internal suspend fun <C, D> Run<C>.runChild(
    path: String,
    node: Child<C, D>,
) {
    if (record.succeeded(path) != null) return

    val childId = "${record.id}/$path"
    val parked = record.latest(path)?.takeIf { it.outcome == NodeOutcome.Paused }
    val now = Clock.System.now()

    val child =
        if (parked == null) {
            engine.startChild(node.workflow, node.start(scope(path, attempt = 1, startedAt = now)), childId, record.id)
        } else {
            // Gone means gone: a store's retention outlived the parent's patience, and there is no
            // honest way to decide whether the child did its work. It fails the node and the parent
            // unwinds, which is the outcome a person can act on.
            store.load(childId) ?: throw NodeFailed(path, ChildLostException(childId), 1)
        }

    when {
        child.status == WorkflowStatus.Completed -> {
            val result = json.decodeFromJsonElement(node.workflow.serializer, child.context)
            context = node.body(scope(path, attempt = 1, startedAt = now), result)
            journal(path, NodeOutcome.Succeeded, attempts = 1, context = encoded()) {
                it.copy(status = WorkflowStatus.Running, awaiting = null, wakeAt = null)
            }
        }

        child.status.isTerminal -> {
            throw NodeFailed(path, ChildFailedException(childId, node.workflow.name, child.status), 1)
        }

        else -> {
            park(path, node, childId, parked, now)
        }
    }
}

/**
 * Stops the parent until the child is worth looking at again.
 *
 * The status is `Awaiting` and `awaiting` holds the **child's id** rather than a signal's name,
 * because that is what this instance is stopped on and an operator reading the record should be able
 * to follow it. `wakeAt` is a poll rather than a deadline: the child resumes its parent the moment
 * it finishes, so the poll is what covers the process that died between those two writes, not the
 * ordinary path.
 *
 * The deadline is measured from the journal entry that parked, not from `wakeAt`, precisely because
 * `wakeAt` is being spent on the poll. The journal is where "when did this node stop" already lives.
 */
private suspend fun <C, D> Run<C>.park(
    path: String,
    node: Child<C, D>,
    childId: String,
    parked: JournalEntry?,
    now: kotlin.time.Instant,
) {
    if (parked == null) {
        journal(path, NodeOutcome.Paused, attempts = 1) {
            it.copy(status = WorkflowStatus.Awaiting, awaiting = childId, wakeAt = now + engine.childPoll)
        }
        throw Paused()
    }

    node.deadline?.let { deadline ->
        if (now >= parked.at + deadline) throw NodeFailed(path, ChildTimeoutException(childId, deadline), 1)
    }

    // A worker that handed the instance back early costs a load and nothing else. Re-scoring it on
    // every poll would be a write per poll for an instance that has not changed.
    val due = record.wakeAt
    if (due == null || now >= due) {
        checkpoint { it.copy(status = WorkflowStatus.Awaiting, awaiting = childId, wakeAt = now + engine.childPoll) }
    }
    throw Paused()
}

/**
 * Starts a child instance, or picks up the one a previous attempt already started.
 *
 * The id is derived from the parent's, so "did I already start it" is answered by looking rather
 * than by a flag: a parent that died between the child's creation and its own checkpoint finds
 * the child here and carries on with it instead of starting a second.
 */
internal suspend fun <D> WorkflowEngine.startChild(
    workflow: Workflow<D>,
    context: D,
    id: String,
    parent: String,
): WorkflowRecord {
    require(definitions[workflow.name] === workflow) {
        "child workflow '${workflow.name}' is not registered with this engine — an instance it cannot " +
            "look up again is an instance that cannot be resumed after a restart"
    }
    store.load(id)?.let { return advance(workflow, it).record }
    val now = Clock.System.now()
    val record =
        WorkflowRecord(
            id = id,
            workflow = workflow.name,
            status = WorkflowStatus.Running,
            context = json.encodeToJsonElement(workflow.serializer, context),
            parent = parent,
            createdAt = now,
            updatedAt = now,
        )
    store.create(record)
    return advance(workflow, record).record
}

/**
 * Tells a parent its child has finished, best effort.
 *
 * Best effort is the whole design, not a shortcut. The child's terminal state and the parent's
 * progress are two records and cannot be written together, so this is what makes the handoff
 * *prompt* while the parent's poll is what makes it *correct*. It therefore must not be able to
 * fail the child: the child did its job, and an exception thrown on the way to telling somebody
 * would undo work that succeeded.
 *
 * Failing to take the parent's lock is the ordinary case, not an error — it is what happens when
 * the parent is the very run that started this child, and that run reads the result itself
 * rather than waiting to be told.
 */
internal suspend fun WorkflowEngine.wake(parent: String) {
    try {
        resume(parent)
    } catch (_: WorkflowException) {
        // The parent's poll will come back for it.
    }
}
