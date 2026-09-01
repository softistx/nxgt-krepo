package com.strange.workflow

import com.strange.workflow.store.WorkflowRecord

/** Everything this library throws on its own behalf. */
sealed class WorkflowException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** No instance under that id — or none this engine has a definition for. */
class WorkflowNotFoundException(
    val id: String,
) : WorkflowException("no workflow instance '$id'")

/** No definition registered under that name. */
class WorkflowUnknownException(
    val workflow: String,
) : WorkflowException("no workflow registered as '$workflow'")

/**
 * Somebody else wrote this instance while this engine was working on it.
 *
 * The instance lock normally keeps two engines apart. This is what happens when it did not — a
 * Redis that failed over to a replica which had not seen the lock yet — and the answer is to stop:
 * the other writer has the newer journal, and continuing would overwrite it.
 */
class WorkflowConflictException(
    val id: String,
) : WorkflowException("workflow instance '$id' was written by somebody else; this engine's copy is stale")

/**
 * The instance did not complete.
 *
 * It carries the record so the caller can see how far it got and what was undone, and the original
 * exception as its [cause] — flattened into [WorkflowError] for the store, kept live here.
 */
class WorkflowFailedException(
    val record: WorkflowRecord,
    cause: Throwable? = null,
) : WorkflowException(
        record.error?.toString() ?: "workflow instance '${record.id}' ended as ${record.status}",
        cause,
    )

/**
 * A signal was delivered to an instance that has already finished.
 *
 * This is the second approval click and the redelivered message, and it is an error rather than a
 * silent no-op because swallowing it would make an approval that landed nowhere look like one that
 * was recorded. Every other timing is accepted: an instance still running takes the payload and
 * keeps it until the wait it belongs to reads it.
 */
class WorkflowNotAwaitingException(
    val id: String,
    val signal: String,
    val status: WorkflowStatus,
) : WorkflowException("workflow instance '$id' is $status, so nothing will ever read '$signal'")

/**
 * A signal was delivered under a name the workflow has no `await` for.
 *
 * It is refused at the door rather than stored, because a delivery is durable here and a name
 * nothing waits on would sit on the record until the instance was purged, having quietly told the
 * caller it landed. The two ways to get here are a typo and a signal renamed on one side only, and
 * both are worth hearing about at the first delivery rather than at the wait that never wakes.
 */
class WorkflowUnknownSignalException(
    val workflow: String,
    val signal: String,
) : WorkflowException("workflow '$workflow' declares no await on '$signal'")

/**
 * A wait reached its deadline with no signal.
 *
 * It surfaces as the failure of the `await` node, so the workflow unwinds through everything before
 * it — which is the point: the deadline exists so that an approval nobody gives does not leave the
 * effects before it stranded.
 */
class AwaitTimeoutException(
    val signal: String,
    val after: kotlin.time.Duration,
) : WorkflowException("no '$signal' signal arrived within $after")

/**
 * Thrown **by a step** to fail without another attempt.
 *
 * A retry policy decides whether a *class* of failure is worth trying again; this is for when only
 * the code that hit the failure can tell. A card declined for insufficient funds will be declined
 * again in two hundred milliseconds.
 */
class NonRetryableException(
    message: String,
    cause: Throwable? = null,
) : WorkflowException(message, cause)

/**
 * A child workflow ended as something other than `Completed`.
 *
 * It surfaces as the failure of the `child` node, so the parent unwinds through everything before
 * it. That is the honest reading of a delegated piece of work that could not be done, and it is the
 * same shape as a step that threw — the parent does not get to inspect *how* the child failed and
 * carry on regardless, because the child has already compensated whatever it did.
 */
class ChildFailedException(
    val id: String,
    val workflow: String,
    val status: WorkflowStatus,
) : WorkflowException("child instance '$id' of workflow '$workflow' ended as $status")

/**
 * A child workflow was not in the store when its parent came back for it.
 *
 * The ordinary cause is retention: the child finished long ago and was purged while the parent was
 * still parked. There is no honest way to decide from here whether its work was done, so the node
 * fails and a person gets an instance to look at.
 */
class ChildLostException(
    val id: String,
) : WorkflowException("child instance '$id' is no longer in the store")

/** A child workflow was still running when its parent's `within` ran out. */
class ChildTimeoutException(
    val id: String,
    val after: kotlin.time.Duration,
) : WorkflowException("child instance '$id' had not finished within $after")

/**
 * `undo` was asked for an instance it has no honest answer for.
 *
 * `undo` reverses a workflow that **succeeded**. A instance still in flight is `cancel`'s business;
 * one already `Compensated` or `Cancelled` has been unwound and undoing it twice would refund a
 * refund; one that is `Failed` is waiting for a person by design, and this is not that person.
 */
class WorkflowNotUndoableException(
    val id: String,
    val status: WorkflowStatus,
) : WorkflowException("workflow instance '$id' is $status; only a Completed instance can be undone")
