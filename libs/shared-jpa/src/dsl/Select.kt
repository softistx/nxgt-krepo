package com.strange.jpa.dsl

import com.strange.jpa.query.JpaQuery
import jakarta.persistence.criteria.CriteriaQuery
import org.hibernate.query.sqm.tree.SqmVisitableNode
import org.hibernate.reactive.stage.Stage

/**
 * A query built in Kotlin against the entity's own properties, rather than in an HQL string.
 *
 * ```kotlin
 * jpa.session { session ->
 *     session
 *         .select<Order> {
 *             val customer = join(Order::customer)
 *             where { this[Order::total] gt 100L }
 *             where { customer[Customer::name] eq "ada" }
 *             orderBy { desc(this[Order::placedAt]) }
 *         }.limit(20)
 *         .list()
 * }
 * ```
 *
 * **The same query, checked a build earlier.** HQL is checked against the mapping when the factory
 * starts, which catches a renamed property before the first request; this catches it before the
 * commit. The value's type is checked too, which HQL cannot do at all — see [Paths].
 *
 * It answers with the same [JpaQuery] an HQL query does, so `limit`, `offset`, `readOnly`, `list`,
 * `first`, `single` and `count` are the ones already documented there. There is nothing to bind:
 * values reached the query as values, not as `:parameters`, and Hibernate binds them itself.
 *
 * Underneath it is JPA Criteria, which means Hibernate renders the SQL — this is a Kotlin surface
 * over Hibernate's query model, not a second implementation of HQL that would have to learn every
 * dialect's idea of quoting.
 */
inline fun <reified T : Any> Stage.QueryProducer.select(block: SelectScope<T>.() -> Unit): JpaQuery<T> {
    val criteria = builder.createQuery(T::class.java)
    val scope = SelectScope(criteria, criteria.from(T::class.java)).apply(block)
    val built = scope.build()
    return JpaQuery({ built.hql() }, createQuery(built))
}

/**
 * The query rendered back to HQL, for an exception that has to say which query it was.
 *
 * A criteria query has no source text, so this walks the tree Hibernate built and prints it. It runs
 * only when a terminal is about to throw.
 */
@PublishedApi
internal fun CriteriaQuery<*>.hql(): String = runCatching { (this as SqmVisitableNode).toHqlString() }.getOrElse { "a criteria query" }
