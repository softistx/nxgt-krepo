package com.strange.jpa.dsl

import com.strange.jpa.query.JpaQuery
import jakarta.persistence.criteria.Selection
import org.hibernate.reactive.stage.Stage

/**
 * A query over [T] that returns [R] — a summary, one column, a count — rather than the entity.
 *
 * ```kotlin
 * jpa.session { session ->
 *     session
 *         .project<Purchase, Summary> {
 *             val buyer = join(Purchase::customer)
 *             where { this[Purchase::total] gt 100L }
 *             orderBy { desc(this[Purchase::total]) }
 *             construct(::Summary, this[Purchase::reference], buyer[Buyer::name])
 *         }.list()
 * }
 * ```
 *
 * Both types are written out because both are needed and neither can be inferred: [T] says what the
 * query is over, [R] what a row is. The block's last expression is the row — a [Selection] of [R],
 * which is what [construct] answers with and what a single path already is.
 *
 * It answers with the same [JpaQuery] everything else here does, so the terminals and the paging are
 * unchanged. A projection reads only the columns it names and puts nothing in the persistence
 * context, which is the point of one.
 */
inline fun <reified T : Any, reified R : Any> Stage.QueryProducer.project(block: ProjectScope<T, R>.() -> Selection<R>): JpaQuery<R> {
    val criteria = builder.createQuery(R::class.javaObjectType)
    val scope = ProjectScope(criteria, criteria.from(T::class.java), R::class.javaObjectType)
    criteria.select(scope.block())
    val built = scope.build()
    return JpaQuery({ built.hql() }, createQuery(built))
}
