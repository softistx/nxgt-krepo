package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaQuery
import org.hibernate.query.sqm.tree.SqmVisitableNode
import org.hibernate.reactive.stage.Stage

/**
 * A query built against the entity's own properties rather than in an HQL string.
 *
 * ```kotlin
 * jpa.session { session ->
 *     session
 *         .select<Purchase>()
 *         .where { Purchase::total gt 100L }
 *         .where { join(Purchase::customer)[Buyer::name] eq "ada" }
 *         .orderBy { desc(Purchase::total) }
 *         .limit(20)
 *         .list()
 * }
 * ```
 *
 * **The same query, checked a build earlier.** HQL is checked against the mapping when the factory
 * starts, which catches a renamed property before the first request; this catches it before the
 * commit. The value's type is checked too, which HQL cannot do at all — see [Filters].
 *
 * The block is optional and exists for one thing: naming a join that several clauses read from.
 * Joins are remembered, so `join(Purchase::customer)` in two chained lambdas is one join in the SQL
 * and the block is a convenience rather than a requirement.
 *
 * Underneath it is JPA Criteria, which means Hibernate renders the SQL — this is a Kotlin surface
 * over Hibernate's query model, not a second implementation of HQL that would have to learn every
 * dialect's idea of quoting. There is nothing to bind: values reached the query as values, not as
 * `:parameters`.
 */
inline fun <reified T : Any> Stage.QueryProducer.select(block: SelectScope<T>.() -> Unit = {}): SelectScope<T> {
    val criteria = builder.createQuery(T::class.java)
    return SelectScope(this, criteria, criteria.from(T::class.java)).apply(block)
}

/**
 * The query rendered back to HQL, for an exception that has to say which query it was.
 *
 * A criteria query has no source text, so this walks the tree Hibernate built and prints it. It runs
 * only when a terminal is about to throw.
 */
internal fun CriteriaQuery<*>.hql(): String = runCatching { (this as SqmVisitableNode).toHqlString() }.getOrElse { "a criteria query" }
