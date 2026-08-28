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

/** One page of them, with what a client needs to ask for the next. */
@Serializable
data class ProductPage(
    val data: List<ProductView>,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean,
)

/**
 * A projection: three columns, packaged by Hibernate into this class on the way back.
 *
 * Hibernate 6 and later take an arbitrary result class with a matching constructor and build the
 * rows into it — `query<ProductSummary>("select sku, name, priceInCents from Product")` and nothing
 * else. No `select new com.…ProductSummary(…)` in the HQL, no constructor expression, no `Tuple` to
 * unpack: the selection list and the constructor's parameters are matched by position and type.
 *
 * A Kotlin `data class` is what a Java record is here, and it is the shape to reach for — small,
 * final, and named after the question rather than after the table. This is the way to read *part* of
 * an entity; loading the whole one and mapping it afterwards reads the columns the query did not
 * need.
 */
@Serializable
data class ProductSummary(
    val sku: String,
    val name: String,
    val priceInCents: Long,
)
