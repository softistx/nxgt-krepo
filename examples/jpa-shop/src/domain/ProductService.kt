package com.strange.example.shop.domain

import com.strange.common.page.Page
import com.strange.example.shop.model.EditProduct
import com.strange.example.shop.model.NewProduct
import com.strange.example.shop.model.ProductSummary
import com.strange.jpa.audit.stampedBy
import com.strange.jpa.audit.touchedBy
import com.strange.jpa.criteria.JpaSpec
import com.strange.jpa.criteria.and
import com.strange.jpa.criteria.asc
import com.strange.jpa.criteria.eq
import com.strange.jpa.criteria.get
import com.strange.jpa.query.delete
import com.strange.jpa.query.findAll
import com.strange.jpa.query.findOne
import com.strange.jpa.query.findPage
import com.strange.jpa.query.insert
import com.strange.jpa.session.JpaSession

/**
 * Everything the shop does with a product, in one class over the session.
 *
 * **There is no repository and no CRUD base class**, because `shared-jpa` has nothing to inherit
 * from: `findAll`, `findPage`, `findOne`, `insert`, `delete` and the rest are extensions on the
 * session, and a service that writes its own `create` says what it means in less than one that fills
 * in `buildCreate` and remembers which hooks fire when. What the library still provides is the two
 * things worth not getting wrong by hand — the transaction guard on every write verb, and the audit
 * stamp.
 *
 * **Every method takes the session first.** A session belongs to the event loop that opened it and
 * cannot be held in a field; the unit of work arrives per call, and that is what lets two of these
 * share one transaction. The principal is per-request, which is why the class is built per call.
 */
class ProductService(
    private val principal: String? = null,
) {
    // ─── Reads ────────────────────────────────────────────────────────────────

    /** One page, newest ordering rule first: name, then the identifier so a boundary cannot split a tie. */
    suspend fun page(
        session: JpaSession,
        limit: Int,
        offset: Int,
        spec: JpaSpec<Product>,
    ): Page<Product> =
        session.findPage(limit = limit, offset = offset, spec = spec) { criteria, product ->
            criteria.orderBy(asc(product[Product::name]), asc(product[Product::id]))
        }

    /** Throws `JpaNotFoundException`, which the failure handler turns into a 404. */
    suspend fun byId(
        session: JpaSession,
        id: Long,
    ): Product = session.get(id)

    suspend fun bySku(
        session: JpaSession,
        sku: String,
    ): Product? = session.findOne<Product> { it[Product::sku] eq sku }

    /** Everything on sale under a price — two named restrictions, `and`ed. */
    suspend fun bargains(
        session: JpaSession,
        cents: Long,
    ): List<Product> = session.findAll(ProductSpecs.available and ProductSpecs.upTo(cents))

    /**
     * Three columns, packaged straight into [ProductSummary] — no entity is loaded at all.
     *
     * Nothing about the extensions is a fence: a question they have no shortcut for is written
     * against the session directly, in HQL or as a criteria, right here beside the ones they answer.
     */
    suspend fun summaries(
        session: JpaSession,
        limit: Int,
    ): List<ProductSummary> =
        session
            .query<ProductSummary>("select sku, name, priceInCents from Product where discontinued = false order by name")
            .limit(limit)
            .list()

    // ─── Writes ───────────────────────────────────────────────────────────────

    /**
     * `insert` refuses a session with no transaction, so this cannot quietly write nothing.
     *
     * The flush is not a refresh: it assigns the generated identifier and puts a constraint violation
     * *here*, where the input that caused it can be named, instead of at the end of the transaction.
     * It is not a way to catch one create and continue with the next — a failed flush dooms the
     * transaction, so import a batch with a transaction per input, not a try/catch per input.
     */
    suspend fun create(
        session: JpaSession,
        input: NewProduct,
    ): Product {
        val product =
            Product(
                sku = input.sku,
                name = input.name,
                priceInCents = input.priceInCents,
            ).stampedBy(principal)

        session.insert(product)
        session.flush()
        return product
    }

    /**
     * Assigns onto the managed instance rather than building a statement — Hibernate's dirty check
     * decides what that is worth writing. A field the request left null is not touched, and a field
     * assigned the value it already had produces no SQL.
     */
    suspend fun update(
        session: JpaSession,
        id: Long,
        input: EditProduct,
    ): Product {
        val existing = byId(session, id)

        input.name?.let { existing.name = it }
        input.priceInCents?.let { existing.priceInCents = it }
        input.discontinued?.let { existing.discontinued = it }

        existing.touchedBy(principal)
        session.flush()
        return existing
    }

    suspend fun delete(
        session: JpaSession,
        id: Long,
    ) {
        session.delete(byId(session, id))
        session.flush()
    }
}
