package com.softistx.workflow.dsl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.time.Duration

/**
 * The name and type of what one leg of a fan-out produces.
 *
 * ```kotlin
 * private val CHARGE = outcome<String>("charge")
 * ```
 *
 * A key rather than a plain name because a fan-out is the one place a single typed context cannot
 * express what is happening: two legs cannot both return `C`, and merging two `C`s by field is
 * either reflection or last-writer-wins, both of which are wrong quietly. So each leg produces a
 * value of its own type, and the merge — ordinary Kotlin, checked by the compiler — is what folds
 * them into the context.
 *
 * The key carries the serializer because a leg's result is checkpointed: a resume after a crash
 * must not charge the card again just because a sibling leg had not finished.
 */
class Outcome<T> internal constructor(
    val name: String,
    internal val serializer: KSerializer<T>,
)

inline fun <reified T> outcome(name: String): Outcome<T> = outcome(name, serializer())

/** The same key, named by its serializer — for a call site whose `T` cannot be reified. */
fun <T> outcome(
    name: String,
    serializer: KSerializer<T>,
): Outcome<T> = Outcome(name, serializer)

/** What every leg of a fan-out produced, read back by key. */
class Outcomes internal constructor(
    private val values: Map<String, Any?>,
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Outcome<T>): T {
        require(key.name in values) { "no leg named '${key.name}' in this fan-out" }
        return values[key.name] as T
    }
}

/** One leg of a fan-out. */
class Leg<C, T> internal constructor(
    internal val key: Outcome<T>,
    internal val body: suspend StepScope<C>.() -> T,
) {
    internal var retry: RetryPolicy = RetryPolicy.once
    internal var timeout: Duration? = null
    internal var compensation: (suspend StepScope<C>.(T) -> Unit)? = null
}

/**
 * How to undo one leg.
 *
 * Unlike a step's compensation this one is handed the value the leg produced, because that value is
 * what identifies the effect and it may never have reached the context — a leg whose sibling failed
 * is compensated before the merge ever runs.
 */
infix fun <C, T> Leg<C, T>.compensate(block: suspend StepScope<C>.(T) -> Unit): Leg<C, T> {
    require(compensation == null) { "leg '${key.name}' already has a compensation" }
    compensation = block
    return this
}

class Parallel<C> internal constructor(
    override val name: String,
    internal val legs: List<Leg<C, *>>,
    internal val merge: suspend StepScope<C>.(Outcomes) -> C,
) : WorkflowNode<C>() {
    internal var retry: RetryPolicy = RetryPolicy.once
    internal var timeout: Duration? = null
}

/** Collects the legs of one fan-out and the merge that folds them back into the context. */
class ParallelBuilder<C> internal constructor(
    private val parallel: String,
) {
    private val legs = mutableListOf<Leg<C, *>>()
    private var merge: (suspend StepScope<C>.(Outcomes) -> C)? = null

    /** A leg, producing the value [key] names. */
    fun <T> branch(
        key: Outcome<T>,
        body: suspend StepScope<C>.() -> T,
    ): Leg<C, T> {
        require(legs.none { it.key.name == key.name }) { "fan-out '$parallel' already has a leg named '${key.name}'" }
        return Leg(key, body).also { legs += it }
    }

    /**
     * Folds every leg's value into the next context. Runs only once they have all succeeded, and
     * does not suspend for long — it is not a step, and it has no compensation of its own.
     */
    fun merge(block: suspend StepScope<C>.(Outcomes) -> C) {
        require(merge == null) { "fan-out '$parallel' already has a merge" }
        merge = block
    }

    internal fun build(): Parallel<C> {
        require(legs.isNotEmpty()) { "fan-out '$parallel' declares no legs" }
        val merge = merge ?: error("fan-out '$parallel' has no merge — say what its legs do to the context")
        return Parallel(parallel, legs, merge)
    }
}

/**
 * Declares a fan-out: every leg runs at once, and [ParallelBuilder.merge] folds their results into
 * the context.
 *
 * ```kotlin
 * parallel("provision") {
 *     branch(CHARGE) { payments.charge(context.card, key = idempotencyKey) }
 *         .compensate { id -> payments.refund(id) }
 *     branch(COURIER) { courier.book(context.items) }
 *         .compensate { booking -> courier.cancel(booking.id) }
 *
 *     merge { out -> context.copy(chargeId = out[CHARGE], booking = out[COURIER]) }
 * }
 * ```
 *
 * **Each leg is checkpointed on its own**, under the qualified name `"provision/charge"`. That is
 * what makes a fan-out resumable: an instance that died with the charge through and the courier
 * still in flight comes back and books the courier, without charging twice.
 *
 * **When a leg fails, its siblings are cancelled and every leg that had succeeded is compensated**,
 * newest first — including the ones the store already recorded on a previous attempt. Only then does
 * the failure leave the fan-out and the workflow start unwinding the steps before it.
 *
 * A `retry` on the fan-out re-runs only the legs that have not succeeded yet, which is what makes it
 * the useful place to put one; a `retry` on a single leg covers that leg's own attempts.
 */
fun <C> NodeSink<C>.parallel(
    name: String,
    block: ParallelBuilder<C>.() -> Unit,
): Parallel<C> = add(ParallelBuilder<C>(name).apply(block).build())
