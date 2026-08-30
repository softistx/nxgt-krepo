package com.strange.graphix.fixture

import com.strange.graphix.schema.BatchMapping
import com.strange.graphix.schema.GraphQLContext
import com.strange.graphix.schema.SchemaMapping
import graphql.schema.DataFetchingEnvironment
import java.util.concurrent.atomic.AtomicInteger

class ReviewBatch(
    private val reviews: Map<String, List<Review>> =
        mapOf(
            "p1" to listOf(Review("r1", "Nice mug")),
            "p2" to listOf(Review("r2", "Loud")),
        ),
    val loads: AtomicInteger = AtomicInteger(),
) {
    @BatchMapping
    fun reviews(source: List<Product>): Map<Product, List<Review>> {
        loads.incrementAndGet()
        return source.associateWith { reviews[it.id].orEmpty() }
    }
}

class DfeFields {
    @SchemaMapping
    fun tagged(
        product: Product,
        prefix: String = "x",
        @GraphQLContext dfe: DataFetchingEnvironment,
    ): String {
        val source = dfe.getSource<Product>() ?: product
        val fromEnv = dfe.getArgument<String>("prefix") ?: prefix
        return "$fromEnv-${source.name}"
    }
}

class SingularBatch {
    @BatchMapping
    fun reviews(source: Product): Map<Product, List<Review>> = emptyMap()
}
