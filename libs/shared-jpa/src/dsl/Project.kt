package com.strange.jpa.dsl

import com.strange.jpa.query.criteria
import jakarta.persistence.criteria.Selection
import org.hibernate.reactive.stage.Stage
import kotlin.reflect.KClass

/**
 * A query over [T] that returns [R] — a summary, one column, a count — rather than the entity.
 *
 * ```kotlin
 * jpa.session { session ->
 *     session
 *         .project<Purchase, Summary> {
 *             construct(::Summary, Purchase::reference, join(Purchase::customer)[Buyer::name])
 *         }.where { Purchase::total gt 100L }
 *         .orderBy { desc(Purchase::total) }
 *         .list()
 * }
 * ```
 *
 * Both types are written out because both are needed and neither can be inferred: [T] says what the
 * query is over, [R] what a row is. The block's last expression is the row — a [Selection] of [R],
 * which is what `construct` answers with and what a single path already is. That is why this is the
 * one entry point whose block is required.
 *
 * Everything after it is the chain every other query has, and so are the terminals.
 */
inline fun <reified T : Any, reified R : Any> Stage.QueryProducer.project(
    noinline block: ProjectScope<T, R>.() -> Selection<R>,
): ProjectScope<T, R> = project(T::class, R::class, block)

/**
 * The same, with both types as values — how [project] is implemented, and how a caller generic in
 * its entity reaches it. Internal for the reason [com.strange.jpa.dsl.select]'s twin is.
 */
fun <T : Any, R : Any> Stage.QueryProducer.project(
    type: KClass<T>,
    result: KClass<R>,
    block: ProjectScope<T, R>.() -> Selection<R>,
): ProjectScope<T, R> {
    val criteria = criteria.createQuery(result.javaObjectType)
    val scope = ProjectScope(this, criteria, criteria.from(type.java), result.javaObjectType)
    criteria.select(scope.block())
    return scope
}
