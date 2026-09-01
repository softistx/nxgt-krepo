package com.softistx.example.graphix.shop

import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.BatchMapping
import com.softistx.graphix.schema.MutationMapping
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.SubscriptionMapping
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

    @QueryMapping
    fun product(
        @Argument id: String,
    ): Product? = products.find { it.id == id }

    @QueryMapping
    fun products(): List<Product> = products.toList()

    /**
     * A `union SearchResult = Product | Review`. Kotlin has no union type, so the return type is
     * `List<Any>` — on the SDL path the document is the schema, so a resolver's Kotlin type is
     * never read. Graphix resolves each row by its class name, with nothing registered.
     */
    @QueryMapping
    fun search(
        @Argument term: String,
    ): List<Any> {
        val matches = term.lowercase()
        return products.filter { it.name.lowercase().contains(matches) } +
            reviews.values.flatten().filter { it.body.lowercase().contains(matches) }
    }

    @MutationMapping
    fun addProduct(
        @Argument name: String,
        @Argument price: Long,
    ): Product {
        val created = Product(id = "p${products.size + 1}", name = name, price = price)
        products += created
        added.tryEmit(created)
        return created
    }

    @SubscriptionMapping
    fun productAdded(): Flow<Product> = added

    @BatchMapping
    fun reviews(products: List<Product>): Map<Product, List<Review>> = products.associateWith { reviews[it.id].orEmpty() }
}
