package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.query.criteria.HibernateCriteriaBuilder

/**
 * What every selection has: restrictions, an order, and something to take paths from.
 *
 * [SelectScope] returns the entity, [ProjectScope] returns something built from its columns, and
 * neither adds anything to this that the other would want. It is the receiver of the `where` and
 * `orderBy` blocks in both, so a predicate written for one reads the same in the other.
 *
 * **Every call adds; none replaces.** Two `where` blocks are one `and`, two `orderBy` blocks are two
 * sort keys in the order they were written. That is what makes a query assemblable from conditions
 * the caller only learns one at a time — `if (name != null) where { … }` — which is the thing an HQL
 * string cannot do without string concatenation.
 */
@JpaDsl
abstract class QueryScope<T : Any, R : Any> internal constructor(
    /** Hibernate's builder, for everything this package has not given a name. */
    val builder: HibernateCriteriaBuilder,
    @PublishedApi internal val query: CriteriaQuery<R>,
    final override val from: Root<T>,
) : Joins<T> {
    private val restrictions = mutableListOf<Predicate>()
    private val ordering = mutableListOf<Order>()

    /** The root of the query, for the Criteria this does not wrap. */
    val root: Root<T> get() = from

    /**
     * Restricts the query. Called more than once, the restrictions are `and`ed together.
     *
     * The block may answer with null, which adds nothing — so a filter that turns out not to apply
     * needs no `if` around the call, and [all] and [any] over an empty list compose here without a
     * special case.
     */
    fun where(block: QueryScope<T, R>.() -> Predicate?) {
        block()?.let { restrictions += it }
    }

    /** Adds a sort key, after any already added. */
    fun orderBy(block: QueryScope<T, R>.() -> Order) {
        ordering += block()
    }

    /**
     * Collapses duplicate rows.
     *
     * Worth reaching for after a [joinEach]: a join to a to-many association returns the owning row
     * once per element, and a query selecting the owner rarely means that.
     */
    fun distinct(distinct: Boolean = true) {
        query.distinct(distinct)
    }

    @PublishedApi
    internal open fun build(): CriteriaQuery<R> {
        if (restrictions.isNotEmpty()) query.where(*restrictions.toTypedArray())
        if (ordering.isNotEmpty()) query.orderBy(ordering)
        return query
    }
}
