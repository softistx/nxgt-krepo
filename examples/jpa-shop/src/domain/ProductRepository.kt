package com.strange.example.shop.domain

import com.strange.example.shop.model.ProductSummary
import com.strange.jpa.criteria.eq
import com.strange.jpa.criteria.get
import com.strange.jpa.criteria.ilike
import com.strange.jpa.criteria.le
import com.strange.jpa.repository.JpaRepository
import com.strange.jpa.repository.JpaSpec
import com.strange.jpa.repository.and
import com.strange.jpa.session.JpaSession

/**
 * Restrictions worth a name, because more than one query asks for them.
 *
 * A [JpaSpec] is `(Root<Product>) -> Predicate?` and nothing more — a plain function type over
 * Criteria's own root, so these compose with `and` and drop into a repository call or a hand-written
 * criteria unchanged. Naming a filter needs no framework, only a function that returns one.
 */
object ProductSpecs {
    val available: JpaSpec<Product> = { it[Product::discontinued] eq false }

    fun named(term: String): JpaSpec<Product> = { it[Product::name] ilike "%$term%" }

    fun upTo(cents: Long): JpaSpec<Product> = { it[Product::priceInCents] le cents }
}

/**
 * The catalogue, as an object.
 *
 * Nothing names `Product::class` — `Product::id` already carries which entity this is over. The base
 * class brings `findAll`, `findById`, `requireById`, `findPage`, `count`, `exists`, `insert`,
 * `update`, `delete` and the rest; a subclass exists for the queries that are actually about
 * products.
 *
 * Every method takes the session first, because a session belongs to the event loop that opened it
 * and cannot be held in a field. This object is a singleton; the unit of work arrives per call, and
 * that is what lets two repositories share one transaction.
 */
class ProductRepository : JpaRepository<Product, Long>(Product::id) {
    suspend fun findBySku(
        session: JpaSession,
        sku: String,
    ): Product? = findOne(session) { it[Product::sku] eq sku }

    /** Everything on sale under a price — two named restrictions, `and`ed. */
    suspend fun bargains(
        session: JpaSession,
        cents: Long,
    ): List<Product> = findAll(session, ProductSpecs.available and ProductSpecs.upTo(cents))

    /**
     * Three columns, packaged straight into [ProductSummary] — no entity is loaded at all.
     *
     * The repository is a convenience, not a fence: a question it has no shortcut for is written
     * against the session it was handed, in HQL or as a criteria, right here beside the ones it does.
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
