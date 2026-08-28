package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.reactive.stage.Stage
import kotlin.reflect.KProperty1

/**
 * A query returning something other than the entity.
 *
 * ```kotlin
 * session
 *     .project<Purchase, Summary> {
 *         construct(::Summary, Purchase::reference, join(Purchase::customer)[Buyer::name])
 *     }.where { Purchase::total gt 100L }
 *     .list()
 * ```
 *
 * The block's last expression is what a row is, so a projection of one column needs no ceremony at
 * all — `project<Purchase, String> { this[Purchase::reference] }` is a path, and a path is already a
 * selection. That is also why this is the one entry point whose block is not optional: without it
 * there is nothing to say what a row holds.
 *
 * A projection reads only the columns it names. That is the reason to reach for one: a list page
 * that shows three fields of a wide entity does not need the other forty, and does not need the
 * persistence context to hold on to them either.
 */
@JpaDsl
class ProjectScope<T : Any, R : Any>
    @PublishedApi
    internal constructor(
        producer: Stage.QueryProducer,
        query: CriteriaQuery<R>,
        from: Root<T>,
        /** The class rows are built into — what `construct` hands to Hibernate. */
        val resultType: Class<R>,
    ) : QueryScope<T, R, ProjectScope<T, R>>(producer, query, from) {
        override val joins: JoinRegistry<T> = JoinRegistry()

        private val groups = mutableListOf<Expression<*>>()
        private val havings = mutableListOf<Predicate>()

        /** Adds a grouping key, after any already added. */
        fun groupBy(block: ProjectScope<T, R>.() -> Expression<*>): ProjectScope<T, R> = apply { groups += block() }

        /** The same, named by a property — the common case, and one that needs no block. */
        fun groupBy(property: KProperty1<T, *>): ProjectScope<T, R> = apply { groups += this[property] }

        /**
         * Restricts the groups, after they are formed.
         *
         * The difference from `where` is which rows the database has already thrown away: `where`
         * runs before the grouping and `having` after, so a condition on an aggregate can only be
         * said here.
         */
        fun having(block: ProjectScope<T, R>.() -> Predicate?): ProjectScope<T, R> = apply { block()?.let { havings += it } }

        override fun build(extra: Predicate?): CriteriaQuery<R> {
            if (groups.isNotEmpty()) query.groupBy(groups)
            if (havings.isNotEmpty()) query.having(*havings.toTypedArray())
            return super.build(extra)
        }
    }
