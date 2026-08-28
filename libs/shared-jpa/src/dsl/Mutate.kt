package com.strange.jpa.dsl

import com.strange.jpa.query.criteria
import org.hibernate.reactive.stage.Stage
import kotlin.reflect.KClass

/**
 * A bulk `update` built from the entity's own properties.
 *
 * ```kotlin
 * jpa.transaction { session ->
 *     session
 *         .update<Purchase> { set(Purchase::total, this[Purchase::total] + 10L) }
 *         .where { Purchase::reference like "P-%" }
 *         .execute()
 * }
 * ```
 *
 * The assignments are the block's job and the restrictions are the chain's — see [MutationScope] for
 * what a bulk statement does not do, and for why one with nothing restricting it is refused.
 */
inline fun <reified T : Any> Stage.QueryProducer.update(noinline block: UpdateScope<T>.() -> Unit): UpdateScope<T> =
    updateOn(this, T::class, block)

/**
 * A bulk `delete`, the same way — and with nothing to put in a block, so it has none.
 *
 * ```kotlin
 * session.delete<Purchase>().where { Purchase::total lt 1L }.execute()
 * ```
 */
inline fun <reified T : Any> Stage.QueryProducer.delete(): DeleteScope<T> = deleteOn(this, T::class)

/*
 * The two below take the session as an argument rather than as a receiver, and that is not a style
 * choice. `Stage.StatelessSession` has members called `update` and `delete` that take entities, and
 * a member beats an extension of the same name — inside `JpaStatelessSession`, `raw.update(block)`
 * compiled into `update(Object)` and handed back a `CompletionStage<Void>`. It is the same trap
 * `JpaSession` documents for `flush`, met from the other side. A name with no member to collide with
 * is the only thing that reliably reaches the extension.
 */

/**
 * Builds the `update`, wherever it was called from.
 *
 * The entity arrives as a value rather than as a `reified` type argument for the same reason
 * [com.strange.jpa.dsl.select] has both forms: a caller generic in its entity has a `KClass` and no
 * way to reify it.
 */
fun <T : Any> updateOn(
    producer: Stage.QueryProducer,
    type: KClass<T>,
    block: UpdateScope<T>.() -> Unit,
): UpdateScope<T> {
    val statement = producer.criteria.createCriteriaUpdate(type.java)
    return UpdateScope(producer, type, statement, statement.from(type.java)).apply(block)
}

/** Builds the `delete`, wherever it was called from. */
fun <T : Any> deleteOn(
    producer: Stage.QueryProducer,
    type: KClass<T>,
): DeleteScope<T> {
    val statement = producer.criteria.createCriteriaDelete(type.java)
    return DeleteScope(producer, type, statement, statement.from(type.java))
}
