package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.hibernate.reactive.stage.Stage

/**
 * A query returning the entity itself.
 *
 * Everything it can do is on [QueryScope]; this only fixes the row to the entity. `select` answers
 * with one of these, so the restrictions, the ordering, the paging and the terminals are all reached
 * by chaining onto it — and, for a join that wants a name, by the block it was started with.
 *
 * It is also the one scope that can [Fetches.fetch]: a query answering with the entity has an owner
 * in its select list to hang a fetch on, and a projection does not.
 */
@JpaDsl
class SelectScope<T : Any>
    @PublishedApi
    internal constructor(
        producer: Stage.QueryProducer,
        query: CriteriaQuery<T>,
        from: Root<T>,
    ) : QueryScope<T, T, SelectScope<T>>(producer, query, from),
        Fetches<T> {
        override val joins: JoinRegistry<T> = JoinRegistry()

        /**
         * Loads what [graph] plans, rather than saying it inline with [Fetches.fetch].
         *
         * The two do the same thing to one query and are worth having both of: a fetch reads better
         * where it is used once, and a plan is a value — named, held, and applied to a `find` and a
         * `select` that then cannot disagree about what a "purchase with its buyer" means.
         *
         * A plan that loads a collection makes `limit`, `offset` and `page` refuse here exactly as
         * `fetchEach` does, and for the same measured reason.
         *
         * Only here, and not on a projection: `setPlan` takes an `EntityGraph` of the query's own
         * result type, and a projection's rows are not the entity — the type says so before the
         * runtime has to.
         */
        fun graph(graph: JpaEntityGraph<T>): SelectScope<T> =
            also {
                fetchPlan = graph.raw
                if (graph.holdsCollection) joins.collectionFetched = true
            }
    }
