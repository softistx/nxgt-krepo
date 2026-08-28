package com.strange.jpa.dsl

import jakarta.persistence.criteria.Selection
import org.hibernate.reactive.stage.Stage

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
    block: ProjectScope<T, R>.() -> Selection<R>,
): ProjectScope<T, R> {
    val criteria = builder.createQuery(R::class.javaObjectType)
    val scope = ProjectScope(this, criteria, criteria.from(T::class.java), R::class.javaObjectType)
    criteria.select(scope.block())
    return scope
}
