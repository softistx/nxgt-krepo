package com.strange.workflow.engine

import com.strange.workflow.dsl.Leg
import com.strange.workflow.dsl.Outcome
import com.strange.workflow.dsl.Outcomes
import com.strange.workflow.dsl.Parallel
import com.strange.workflow.dsl.qualify
import com.strange.workflow.store.JournalEntry
import com.strange.workflow.store.NodeOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

private sealed interface LegResult {
    val leg: String
}

private class LegOk(
    override val leg: String,
    val attempts: Int,
    val value: JsonElement,
) : LegResult

private class LegErr(
    override val leg: String,
    val cause: Throwable,
    val attempts: Int,
) : LegResult

/**
 * Runs every leg at once, records each one that lands, then merges.
 *
 * A `retry` on the fan-out re-runs only what has not succeeded yet, because [runLegsOnce] asks the
 * journal rather than a local list — so the useful place to put a retry is here rather than on each
 * leg, and it means what it looks like it means.
 */
internal suspend fun <C> Run<C>.runParallel(
    path: String,
    node: Parallel<C>,
) {
    if (record.succeeded(path) != null) return

    val startedAt = Clock.System.now()
    try {
        attempt(node.retry, node.timeout) { runLegsOnce(path, node) }
    } catch (failure: NodeFailure) {
        compensateLegs(path, node)
        val leg = failure.cause as? LegFailed
        throw NodeFailed(
            node = leg?.let { qualify(path, it.leg) } ?: path,
            cause = leg?.cause ?: failure.cause,
            attempts = failure.attempts,
        )
    }

    context = node.merge(scope(path, 1, startedAt), Outcomes(legValues(path, node)))
    journal(path, NodeOutcome.Succeeded, attempts = 1, context = encoded())
}

/**
 * One pass over the legs that have not succeeded yet.
 *
 * **A leg whose sibling fails is not cancelled.** It is allowed to finish, and if it succeeds it is
 * recorded and later compensated like any other. Cancelling it would stop it somewhere in the middle
 * of a remote call whose result nobody saw — an effect that is neither journaled nor undoable, which
 * is the one state a compensating engine has no answer for. Waiting costs the time of the slowest
 * leg, once.
 *
 * **Each leg is checkpointed as it lands, and this coroutine is the only one that writes.** The legs
 * report through a channel that the parent drains, which is what buys both halves at once: an
 * instance that dies mid-fan-out keeps the legs that had already finished, and the legs never race
 * each other's version checks the way several coroutines writing one record would.
 *
 * The order they are recorded in is therefore the order they finished, not the order they were
 * declared — which is what the unwind wants, since it undoes newest first.
 */
private suspend fun <C> Run<C>.runLegsOnce(
    path: String,
    node: Parallel<C>,
) {
    val pending = node.legs.filter { record.succeeded(qualify(path, it.key.name)) == null }
    if (pending.isEmpty()) return

    val failures = mutableListOf<LegErr>()
    coroutineScope {
        val results = Channel<LegResult>(Channel.UNLIMITED)

        @Suppress("UNCHECKED_CAST")
        val legs = pending.map { leg -> launch { results.send(runLeg(path, leg as Leg<C, Any?>)) } }
        launch {
            legs.joinAll()
            results.close()
        }

        for (result in results) {
            when (result) {
                is LegOk -> journal(qualify(path, result.leg), NodeOutcome.Succeeded, result.attempts, value = result.value)
                is LegErr -> failures += result
            }
        }
    }

    failures.firstOrNull()?.let { throw LegFailed(it.leg, it.cause, it.attempts) }
}

/**
 * Runs one leg and answers rather than throwing, so that one leg failing does not cancel its
 * siblings through [coroutineScope]. A real cancellation still propagates — [attempt] rethrows it
 * untouched, and this catches only what [attempt] raises on its own behalf.
 */
private suspend fun <C> Run<C>.runLeg(
    path: String,
    leg: Leg<C, Any?>,
): LegResult {
    val name = leg.key.name
    val startedAt = Clock.System.now()
    return try {
        val outcome = attempt(leg.retry, leg.timeout) { n -> leg.body(scope(qualify(path, name), n, startedAt)) }
        LegOk(name, outcome.attempts, json.encodeToJsonElement(leg.key.serializer, outcome.value))
    } catch (failure: NodeFailure) {
        LegErr(name, failure.cause, failure.attempts)
    }
}

private fun <C> Run<C>.legValues(
    path: String,
    node: Parallel<C>,
): Map<String, Any?> =
    node.legs.associate { leg ->
        val name = leg.key.name
        val entry =
            record.succeeded(qualify(path, name))
                ?: error("fan-out '$path' is merging but leg '$name' has no recorded value")

        @Suppress("UNCHECKED_CAST")
        val serializer = (leg.key as Outcome<Any?>).serializer
        name to json.decodeFromJsonElement(serializer, entry.value ?: error("leg '$name' recorded no value"))
    }

/** Undoes the legs of this fan-out that succeeded, newest first, before the failure leaves it. */
private suspend fun <C> Run<C>.compensateLegs(
    path: String,
    node: Parallel<C>,
) {
    val names = node.legs.map { qualify(path, it.key.name) }.toSet()
    while (true) {
        val entry: JournalEntry =
            record.journal.asReversed().firstOrNull { it.node in names && record.succeeded(it.node) != null } ?: return
        if (!compensateNode(entry)) return
    }
}
