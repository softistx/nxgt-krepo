package com.strange.graphix.fixture

import com.strange.graphix.schema.BatchLoading
import com.strange.graphix.schema.Field
import com.strange.graphix.schema.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class ReviewBatch(
    private val reviews: Map<String, List<Review>> =
        mapOf(
            "p1" to listOf(Review("r1", "Nice mug")),
            "p2" to listOf(Review("r2", "Loud")),
        ),
    val loads: AtomicInteger = AtomicInteger(),
) {
    @BatchLoading
    fun reviews(source: List<Product>): Map<Product, List<Review>> {
        loads.incrementAndGet()
        return source.associateWith { reviews[it.id].orEmpty() }
    }
}

class DfeFields {
    @Field
    fun tagged(
        product: Product,
        prefix: String = "x",
        @GraphQLContext dfe: DataFetchingEnvironment,
    ): String {
        val source = dfe.getSource<Product>() ?: product
        val fromEnv = dfe.getArgument<String>("prefix") ?: prefix
        return "$fromEnv-${source.name}"
    }

    @Field
    fun reviews(
        product: Product,
        @GraphQLContext dfe: DataFetchingEnvironment,
    ): CompletableFuture<List<Review>> =
        dfe.getDataLoader<Product, List<Review>>("reviews")?.load(product)
            ?: CompletableFuture.completedFuture(emptyList())
}

class SingularBatch {
    @BatchLoading
    fun reviews(source: Product): Map<Product, List<Review>> = emptyMap()
}
