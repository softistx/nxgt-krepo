package com.strange.example.shop.domain

import com.strange.jpa.dsl.JpaSpec
import com.strange.jpa.dsl.and
import com.strange.jpa.dsl.eq
import com.strange.jpa.dsl.ilike
import com.strange.jpa.dsl.le
import com.strange.jpa.repository.JpaRepository
import com.strange.jpa.session.JpaSession

/**
 * Restrictions worth a name, because more than one query asks for them.
 *
 * A `JpaSpec<T>` is the type `where` already takes, so these compose with `and` and `or` and drop
 * into a repository call, a bare `select`, or a projection unchanged.
 */
object ProductSpecs {
    val available: JpaSpec<Product> = { Product::discontinued eq false }

    fun named(term: String): JpaSpec<Product> = { Product::name ilike "%$term%" }

    fun upTo(cents: Long): JpaSpec<Product> = { Product::priceInCents le cents }
}

/**
 * The catalogue, as an object.
 *
 * Nothing names `Product::class` — `Product::id` already carries which entity this is over. The
 * base class brings `findAll`, `findById`, `requireById`, `findPage`, `count`, `insert`, `update`
 * and the rest; a subclass exists for the queries that are actually about products.
 *
 * Every method takes the session first, because a session belongs to the event loop that opened it
 * and cannot be held in a field. This object is a singleton; the unit of work arrives per call, and
 * that is what lets two repositories share one transaction.
 */
class ProductRepository : JpaRepository<Product, Long>(Product::id) {
    suspend fun findBySku(
        session: JpaSession,
        sku: String,
    ): Product? = findOne(session) { Product::sku eq sku }

    /** Everything on sale under a price — two named restrictions, `and`ed. */
    suspend fun bargains(
        session: JpaSession,
        cents: Long,
    ): List<Product> = findAll(session, ProductSpecs.available and ProductSpecs.upTo(cents))
}
