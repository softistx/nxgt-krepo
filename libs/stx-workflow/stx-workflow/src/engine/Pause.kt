package com.softistx.workflow.engine

import com.softistx.workflow.AwaitTimeoutException
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.Await
import com.softistx.workflow.dsl.Sleep
import com.softistx.workflow.store.NodeOutcome
import kotlin.time.Clock

/**
 * Thrown to leave the declaration when the instance has stopped on purpose.
 *
 * It is not a failure and must never reach a caller: [advance] catches it and returns, because the
 * record was already checkpointed — with its status, its wake-up time and its journal entry — by
 * whichever node parked. Unwinding the Kotlin stack this way is what lets an `await` nested three
 * branch arms deep stop the whole run without every level in between having a return value to say so.
 *
 * No stack trace: nothing reads it, and a workflow that parks a thousand instances an hour should
 * not pay to fill in a trace a thousand times.
 */
internal class Paused : Exception(null, null, false, false)

/**
 * Waits for a signal, or takes the one that arrived.
 *
 * Three states, in the order they are checked. The wait is **already over** — the node has a
 * `Succeeded` entry — and there is nothing to do. A payload addressed to this node's signal is
 * **sitting in the record**, and the node consumes it. Or nothing has arrived, and the instance
 * parks — or, if it was already parked and its deadline has passed, fails.
 *
 * The payload is looked up by name, so it does not matter whether it was delivered to an instance
 * already parked here or to one that had not got here yet: a provider that calls back before the
 * step which asked it to has even returned is answered by the same line of code as an operator
 * approving something tomorrow.
 *
 * The delivered payload is journalled beside the node it fed. A signal is the one input to a
 * workflow that came from outside it, so an operator asking six months later why this instance
 * refunded a card should not have to find the answer in another system's log.
 */
internal suspend fun <C, T> Run<C>.runAwait(
    path: String,
    node: Await<C, T>,
) {
    if (record.succeeded(path) != null) return

    val name = node.signal.name
    val delivered = record.signals[name]
    if (delivered != null) {
        val payload = json.decodeFromJsonElement(node.signal.serializer, delivered)
        context = node.body(scope(path, attempt = 1, startedAt = Clock.System.now()), payload)
        journal(path, NodeOutcome.Succeeded, attempts = 1, value = delivered, context = encoded()) {
            it.copy(status = WorkflowStatus.Running, awaiting = null, signals = it.signals - name, wakeAt = null)
        }
        return
    }

    if (record.paused(path)) {
        val deadline = record.wakeAt
        if (deadline != null && Clock.System.now() >= deadline) {
            throw NodeFailed(path, AwaitTimeoutException(node.signal.name, node.deadline ?: kotlin.time.Duration.ZERO), 1)
        }
        throw Paused()
    }

    val now = Clock.System.now()
    journal(path, NodeOutcome.Paused, attempts = 1) {
        it.copy(
            status = WorkflowStatus.Awaiting,
            awaiting = node.signal.name,
            wakeAt = node.deadline?.let { deadline -> now + deadline },
        )
    }
    throw Paused()
}

/**
 * Stops until a moment, then goes on.
 *
 * The moment is computed once and stored, so a resume reads the same `wakeAt` it wrote rather than
 * asking the context again — which would make every restart extend the pause, and a restart loop an
 * instance that never wakes.
 *
 * A store may hand the instance back a little early or a little late; a pause that is not yet over
 * parks again without a write, which costs a poll and keeps the wake-up honest.
 */
internal suspend fun <C> Run<C>.runSleep(
    path: String,
    node: Sleep<C>,
) {
    if (record.succeeded(path) != null) return

    val now = Clock.System.now()
    if (record.paused(path)) {
        val until = record.wakeAt
        if (until != null && now < until) throw Paused()
        journal(path, NodeOutcome.Succeeded, attempts = 1) {
            it.copy(status = WorkflowStatus.Running, wakeAt = null)
        }
        return
    }

    val duration = node.duration(scope(path, attempt = 1, startedAt = now))
    // A pause of nothing is nothing. Parking it would cost a checkpoint and a poll to arrive back
    // here having waited zero milliseconds, and `sleep { context.retryAfter }` on a `Retry-After`
    // that has already passed is an ordinary Tuesday.
    if (!duration.isPositive()) {
        journal(path, NodeOutcome.Succeeded, attempts = 1)
        return
    }
    journal(path, NodeOutcome.Paused, attempts = 1) {
        it.copy(status = WorkflowStatus.Sleeping, wakeAt = now + duration)
    }
    throw Paused()
}
