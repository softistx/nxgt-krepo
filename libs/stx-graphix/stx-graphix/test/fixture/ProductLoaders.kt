package com.strange.graphix.fixture

import com.strange.graphix.schema.Field
import com.strange.graphix.schema.Load
import com.strange.graphix.schema.Loader
import com.strange.graphix.schema.Query
import java.util.concurrent.atomic.AtomicInteger

class ProductLoaders(
    private val products: List<Product> = listOf(Product("p1", "Mug"), Product("p2", "Kettle")),
    private val reviews: Map<String, List<Review>> =
        mapOf(
            "p1" to listOf(Review("r1", "Nice mug")),
            "p2" to listOf(Review("r2", "Loud")),
        ),
    val productLoads: AtomicInteger = AtomicInteger(),
    val reviewLoads: AtomicInteger = AtomicInteger(),
) {
    @Loader
    fun product(ids: List<String>): Map<String, Product> {
        productLoads.incrementAndGet()
        return products.filter { it.id in ids }.associateBy { it.id }
    }

    @Loader
    fun reviews(ids: List<String>): Map<String, List<Review>> {
        reviewLoads.incrementAndGet()
        return ids.associateWith { reviews[it].orEmpty() }
    }
}

class LoadedQueries(
    private val items: List<Product> = listOf(Product("p1", "Mug"), Product("p2", "Kettle")),
) {
    @Query
    fun products(): List<Product> = items

    @Query
    fun product(
        id: String,
        @Load("product") loaded: Product?,
    ): Product? = loaded
}

class ReviewFields {
    @Field
    fun reviews(
        product: Product,
        @Load("reviews", from = "id") loaded: List<Review>,
    ): List<Review> = loaded
}

class MissingLoadQueries {
    @Query
    fun product(
        id: String,
        @Load("nope") loaded: Product?,
    ): Product? = loaded
}
