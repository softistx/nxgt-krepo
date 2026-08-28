package com.strange.example.shop.domain

import com.strange.example.shop.model.EditProduct
import com.strange.example.shop.model.NewProduct
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.session.JpaSession

/**
 * The create/update/delete flow, written out rather than inherited.
 *
 * [create] maps a request into an entity. [update] applies a request *onto* the managed one — it
 * assigns fields rather than building a statement, and Hibernate's dirty check decides what that is
 * worth writing. A field the request left null is not touched, and a field assigned the value it
 * already had produces no SQL and moves no audit timestamp.
 *
 * **Every method here needs a `transaction { }`, not a `session { }`.** A session flushes at the end
 * of a unit of work if and only if there is a transaction, so the same code under a plain session
 * would build an entity, return it, report success and write no row. Nothing in the library checks
 * that for you; the routes are where it is arranged, and it is worth knowing which of the two a
 * handler opened.
 *
 * The principal is per-request, which is why this is built per call rather than held as a singleton.
 * It is also the only thing that fills the audit columns' *who* half: `AuditedEntity` stamps the
 * timestamps from inside the flush and leaves the names to whoever knows them.
 */
class ProductService(
    private val repository: ProductRepository = ProductRepository(),
    private val principal: String? = null,
) {
    suspend fun create(
        session: JpaSession,
        input: NewProduct,
    ): Product {
        val product =
            Product(
                sku = input.sku,
                name = input.name,
                priceInCents = input.priceInCents,
            )

        principal?.let {
            product.createdBy = it
            product.lastModifiedBy = it
        }

        session.persist(product)
        return product
    }

    suspend fun update(
        session: JpaSession,
        id: Long,
        input: EditProduct,
    ): Product {
        val existing = repository.findById(session, id) ?: throw JpaNotFoundException(Product::class, id)

        input.name?.let { existing.name = it }
        input.priceInCents?.let { existing.priceInCents = it }
        input.discontinued?.let { existing.discontinued = it }
        principal?.let { existing.lastModifiedBy = it }

        return existing
    }

    suspend fun delete(
        session: JpaSession,
        id: Long,
    ) {
        val existing = repository.findById(session, id) ?: throw JpaNotFoundException(Product::class, id)
        session.remove(existing)
    }
}
