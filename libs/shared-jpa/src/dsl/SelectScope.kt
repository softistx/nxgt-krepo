package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.query.criteria.HibernateCriteriaBuilder

/**
 * The `select { }` block: what is restricted, in what order, and what it joins.
 *
 * ```kotlin
 * session.select<Order> {
 *     val customer = join(Order::customer)
 *     where { this[Order::total] gt 100L }
 *     where { customer[Customer::name] eq "ada" }
 *     orderBy { desc(this[Order::placedAt]) }
 * }.limit(20).list()
 * ```
 *
 * **Every call adds; none replaces.** Two `where` blocks are one `and`, two `orderBy` blocks are two
 * sort keys in the order they were written. That is what makes a query assemblable from conditions
 * the caller only learns one at a time — `if (name != null) where { … }` — which is the thing an HQL
 * string cannot do without string concatenation.
 *
 * Paging and the terminals are not here: `select` answers with a [com.strange.jpa.query.JpaQuery],
 * so `limit`, `offset`, `readOnly`, `list`, `single` and `count` are the same ones an HQL query has,
 * and there is one set of them rather than two.
 */
@JpaDsl
class SelectScope<T : Any>
    @PublishedApi
    internal constructor(
        /** Hibernate's builder, for everything this package has not given a name. */
        val builder: HibernateCriteriaBuilder,
        @PublishedApi internal val query: CriteriaQuery<T>,
        override val from: Root<T>,
    ) : Joins<T> {
        private val restrictions = mutableListOf<Predicate>()
        private val ordering = mutableListOf<Order>()

        /** The root of the query, for the Criteria this does not wrap. */
        val root: Root<T> get() = from

        /**
         * Restricts the query. Called more than once, the restrictions are `and`ed together.
         *
         * The block may answer with null, which adds nothing — so a filter that turns out not to
         * apply needs no `if` around the call, and [all] and [any] over an empty list compose here
         * without a special case.
         */
        fun where(block: SelectScope<T>.() -> Predicate?) {
            block()?.let { restrictions += it }
        }

        /** Adds a sort key, after any already added. */
        fun orderBy(block: SelectScope<T>.() -> Order) {
            ordering += block()
        }

        /**
         * Collapses duplicate rows.
         *
         * Worth reaching for after a [joinEach]: a join to a to-many association returns the owning
         * row once per element, and a query selecting the owner rarely means that.
         */
        fun distinct(distinct: Boolean = true) {
            query.distinct(distinct)
        }

        @PublishedApi
        internal fun build(): CriteriaQuery<T> {
            if (restrictions.isNotEmpty()) query.where(*restrictions.toTypedArray())
            if (ordering.isNotEmpty()) query.orderBy(ordering)
            return query
        }
    }
