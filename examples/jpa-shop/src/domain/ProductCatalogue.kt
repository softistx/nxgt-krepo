package com.strange.example.shop.domain

import com.strange.example.shop.model.ProductSummary
import com.strange.jpa.criteria.all
import com.strange.jpa.criteria.asc
import com.strange.jpa.criteria.eq
import com.strange.jpa.criteria.get
import com.strange.jpa.criteria.ilike
import com.strange.jpa.criteria.le
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.createQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * Restrictions worth a name, because more than one query asks for them.
 *
 * A restriction is a [Predicate] and nothing more — the value Criteria's own `where` already takes,
 * so these compose with `and` — or with [all], over however many of them a request turned out to
 * want — and drop into any query with a `Root<Product>` in hand. That is the whole trick: naming a
 * filter needs no framework, only a function that returns one.
 */
object ProductFilters {
    fun available(product: Root<Product>): Predicate = product[Product::discontinued] eq false

    fun named(
        product: Root<Product>,
        term: String,
    ): Predicate = product[Product::name] ilike "%$term%"

    fun upTo(
        product: Root<Product>,
        cents: Long,
    ): Predicate = product[Product::priceInCents] le cents
}

/**
 * The catalogue, as an object.
 *
 * Every method takes the session first, because a session belongs to the event loop that opened it
 * and cannot be held in a field. This object is a singleton; the unit of work arrives per call, and
 * that is what lets two of these share one transaction.
 *
 * There is no base class doing the reading. `shared-jpa` hands out extensions — `createQuery`, `[]`,
 * `eq`, `and`, `query` — and a repository is whatever assembling them into named questions turns out
 * to look like for this domain. A query that HQL says more plainly is written in HQL, which is also
 * the point: nothing here forces one shape on the other.
 */
class ProductRepository {
    suspend fun findById(
        session: JpaSession,
        id: Long,
    ): Product? = session.find<Product>(id)

    suspend fun findBySku(
        session: JpaSession,
        sku: String,
    ): Product? =
        session
            .query<Product>("from Product where sku = :sku")
            .parameter("sku", sku)
            .singleOrNull()

    /**
     * The catalogue query the route serves, with whichever filters the query string asked for.
     *
     * The filters are collected into a list and `and`ed, so a search with nothing to narrow it is
     * the same code path with fewer predicates rather than a second query.
     */
    suspend fun search(
        session: JpaSession,
        term: String?,
        under: Long?,
        limit: Int,
        offset: Int,
    ): List<Product> {
        val criteria = session.createQuery<Product>()
        val product = criteria.from(Product::class.java)

        val filters =
            buildList {
                add(ProductFilters.available(product))
                term?.let { add(ProductFilters.named(product, it)) }
                under?.let { add(ProductFilters.upTo(product, it)) }
            }

        all(filters)?.let(criteria::where)
        criteria.orderBy(asc(product[Product::name]), asc(product[Product::id]))

        return session
            .query(criteria)
            .limit(limit)
            .offset(offset)
            .list()
    }

    /**
     * The same filters, counted.
     *
     * `JpaQuery.count()` rewrites the selection into a count of the rows the query would return, so
     * the restrictions are written once and asked twice.
     */
    suspend fun countMatching(
        session: JpaSession,
        term: String?,
        under: Long?,
    ): Long {
        val criteria = session.createQuery<Product>()
        val product = criteria.from(Product::class.java)

        val filters =
            buildList {
                add(ProductFilters.available(product))
                term?.let { add(ProductFilters.named(product, it)) }
                under?.let { add(ProductFilters.upTo(product, it)) }
            }

        all(filters)?.let(criteria::where)
        return session.query(criteria).count()
    }

    /**
     * Three columns, packaged straight into [ProductSummary] — no entity is loaded at all.
     *
     * The result class is passed to the query and Hibernate matches the selection list to its
     * constructor by position and type. That is all a projection needs here; [ProductSummary] has
     * the rest of the reasoning.
     */
    suspend fun summaries(
        session: JpaSession,
        limit: Int,
    ): List<ProductSummary> =
        session
            .query<ProductSummary>("select sku, name, priceInCents from Product where discontinued = false order by name")
            .limit(limit)
            .list()
}
