package com.strange.graphix.fixture

import com.strange.graphix.schema.Argument
import com.strange.graphix.schema.BatchMapping
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

class DfeBatch(
    val fieldNames: MutableList<String> = mutableListOf(),
) {
    @BatchMapping
    fun notes(
        products: List<Product>,
        dfe: DataFetchingEnvironment,
    ): Map<Product, String> {
        fieldNames += dfe.field.name
        return products.associateWith { dfe.field.name }
    }
}

class DfeFields {
    @SchemaMapping
    fun tagged(
        product: Product,
        @Argument prefix: String = "x",
        dfe: DataFetchingEnvironment,
    ): String {
        val source = dfe.getSource() ?: product
        val fromEnv = dfe.getArgument("prefix") ?: prefix
        return "$fromEnv-${source.name}"
    }
}

class LimitedSnippets(
    val loads: AtomicInteger = AtomicInteger(),
) {
    @BatchMapping
    fun snippets(
        products: List<Product>,
        @Argument limit: Int,
    ): Map<Product, List<String>> {
        loads.incrementAndGet()
        return products.associateWith { (1..limit).map { n -> "s$n" } }
    }
}

class SingularBatch {
    @BatchMapping
    fun reviews(source: Product): Map<Product, List<Review>> = emptyMap()
}
