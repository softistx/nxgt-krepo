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
 * A signal was delivered to an instance that is not waiting for it.
 *
 * It is an error rather than a silent no-op because the two ways to get here are both worth
 * hearing about: the instance already moved on — a second approval click, a redelivered message —
 * or the caller has the wrong id. Swallowing it would turn the first into an approval that appears
 * to have been recorded and was not.
 */
class WorkflowNotAwaitingException(
    val id: String,
    val signal: String,
    val status: WorkflowStatus,
) : WorkflowException("workflow instance '$id' is $status, not awaiting '$signal'")

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
