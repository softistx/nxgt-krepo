package com.strange.example.shop.model

import com.strange.example.shop.domain.Product
import kotlinx.serialization.Serializable

/** What a create request carries. Not the entity: a client does not get to set an identifier. */
@Serializable
data class NewProduct(
    val sku: String,
    val name: String,
    val priceInCents: Long,
)

/** What an edit request carries. Every field optional — absent means *leave it alone*. */
@Serializable
data class EditProduct(
    val name: String? = null,
    val priceInCents: Long? = null,
    val discontinued: Boolean? = null,
)

/** What goes back out, so the audit columns and the mapping stay the server's business. */
@Serializable
data class ProductView(
    val id: Long,
    val sku: String,
    val name: String,
    val priceInCents: Long,
    val discontinued: Boolean,
    val createdBy: String,
    val lastModifiedAt: String,
)

fun Product.view(): ProductView =
    ProductView(
        id = id,
        sku = sku,
        name = name,
        priceInCents = priceInCents,
        discontinued = discontinued,
        createdBy = createdBy,
        lastModifiedAt = lastModifiedAt.toString(),
    )

/** One page of them, with the cursors a client sends back to ask for the next. */
@Serializable
data class ProductPage(
    val data: List<ProductView>,
    val endCursor: String?,
    val hasNextPage: Boolean,
)
