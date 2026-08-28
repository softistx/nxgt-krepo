package com.strange.example.shop.domain

import com.strange.example.shop.model.EditProduct
import com.strange.example.shop.model.NewProduct
import com.strange.jpa.service.JpaCrudService
import com.strange.jpa.session.JpaSession

/**
 * The create/update/delete flow, with only the two parts that are about products written out.
 *
 * [buildCreate] maps a request into an entity. [applyUpdate] applies a request *onto* the managed
 * one — it assigns fields rather than building a statement, and Hibernate's dirty check decides what
 * that is worth writing. A field the request left null is not touched, and a field assigned the value
 * it already had produces no SQL and moves no audit timestamp.
 *
 * The principal is per-request, which is why this is built per call rather than held as a singleton.
 */
class ProductService(
    repository: ProductRepository = ProductRepository(),
    principal: String? = null,
) : JpaCrudService<Product, Long, NewProduct, EditProduct>(repository, principal) {
    override suspend fun buildCreate(input: NewProduct): Product =
        Product(
            sku = input.sku,
            name = input.name,
            priceInCents = input.priceInCents,
        )

    override suspend fun applyUpdate(
        existing: Product,
        input: EditProduct,
    ) {
        input.name?.let { existing.name = it }
        input.priceInCents?.let { existing.priceInCents = it }
        input.discontinued?.let { existing.discontinued = it }
    }

    /** A hook, here only to show where one goes: the same transaction as the write that triggered it. */
    override suspend fun afterCreate(
        created: Product,
        session: JpaSession,
    ) {
        // e.g. session.persist(StockLevel(created.id, 0)) — it commits or rolls back with the create
    }
}
