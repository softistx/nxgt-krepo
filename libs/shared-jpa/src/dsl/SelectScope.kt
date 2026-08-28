package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.hibernate.query.criteria.HibernateCriteriaBuilder

/**
 * The `select { }` block, which returns the entity itself.
 *
 * ```kotlin
 * session.select<Purchase> {
 *     val customer = join(Purchase::customer)
 *     where { this[Purchase::total] gt 100L }
 *     where { customer[Buyer::name] eq "ada" }
 *     orderBy { desc(this[Purchase::total]) }
 * }.limit(20).list()
 * ```
 *
 * Everything it can do is on [QueryScope]; this only fixes the row to the entity. Paging and the
 * terminals are elsewhere again: `select` answers with a [com.strange.jpa.query.JpaQuery], so
 * `limit`, `offset`, `readOnly`, `list`, `single` and `count` are the same ones an HQL query has,
 * and there is one set of them rather than two.
 */
@JpaDsl
class SelectScope<T : Any>
    @PublishedApi
    internal constructor(
        builder: HibernateCriteriaBuilder,
        query: CriteriaQuery<T>,
        from: Root<T>,
    ) : QueryScope<T, T>(builder, query, from)
