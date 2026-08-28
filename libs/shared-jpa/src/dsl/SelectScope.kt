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
    }
