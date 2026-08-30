package com.strange.example.graphix.shop

import com.strange.graphix.schema.Batch
import com.strange.graphix.schema.Mutation
import com.strange.graphix.schema.Query
import com.strange.graphix.schema.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable

/** A catalogue item. [price] is minor units, a GraphQL `Long`. */
@Serializable
data class Product(
    val id: String,
    val name: String,
    val price: Long,
)

@Serializable
data class Review(
    val id: String,
    val body: String,
)

/** In-memory products. Query, mutation, subscription and type fields live on the same instance. */
class Catalog {
    private val products =
        mutableListOf(
            Product("p1", "Mug", 1200),
            Product("p2", "Kettle", 4500),
        )
    private val added = MutableSharedFlow<Product>(extraBufferCapacity = 16)
    private val reviews =
        mutableMapOf(
            "p1" to mutableListOf(Review("r1", "Holds coffee")),
            "p2" to mutableListOf(Review("r2", "Boils fast")),
        )

    @Query
    fun product(id: String): Product? = products.find { it.id == id }

    @Query
    fun products(): List<Product> = products.toList()

    @Mutation
    fun addProduct(
        name: String,
        price: Long,
    ): Product {
        val created = Product(id = "p${products.size + 1}", name = name, price = price)
        products += created
        added.tryEmit(created)
        return created
    }

    @Subscription
    fun productAdded(): Flow<Product> = added

    @Batch
    fun reviews(products: List<Product>): Map<Product, List<Review>> = products.associateWith { reviews[it.id].orEmpty() }
}
