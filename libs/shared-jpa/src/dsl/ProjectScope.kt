package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * The `project { }` block, which returns something other than the entity.
 *
 * ```kotlin
 * session.project<Purchase, Summary> {
 *     val buyer = join(Purchase::customer)
 *     where { this[Purchase::total] gt 100L }
 *     construct(::Summary, this[Purchase::reference], buyer[Buyer::name])
 * }.list()
 * ```
 *
 * The block's last expression is what a row is, so a projection of one column needs no ceremony at
 * all — `project<Purchase, String> { this[Purchase::reference] }` is a path, and a path is already a
 * selection.
 *
 * A projection reads only the columns it names. That is the reason to reach for one: a list page
 * that shows three fields of a wide entity does not need the other forty, and does not need the
 * persistence context to hold on to them either.
 */
@JpaDsl
class ProjectScope<T : Any, R : Any>
    @PublishedApi
    internal constructor(
        query: CriteriaQuery<R>,
        from: Root<T>,
        /** The class rows are built into — what `construct` hands to Hibernate. */
        val resultType: Class<R>,
    ) : QueryScope<T, R>(query, from) {
        private val groups = mutableListOf<Expression<*>>()
        private val havings = mutableListOf<Predicate>()

        /** Adds a grouping key, after any already added. */
        fun groupBy(block: ProjectScope<T, R>.() -> Expression<*>) {
            groups += block()
        }

        /**
         * Restricts the groups, after they are formed.
         *
         * The difference from [where] is which rows the database has already thrown away: `where`
         * runs before the grouping and `having` after, so a condition on an aggregate can only be
         * said here.
         */
        fun having(block: ProjectScope<T, R>.() -> Predicate?) {
            block()?.let { havings += it }
        }

        @PublishedApi
        override fun build(): CriteriaQuery<R> {
            if (groups.isNotEmpty()) query.groupBy(groups)
            if (havings.isNotEmpty()) query.having(*havings.toTypedArray())
            return super.build()
        }
    }
