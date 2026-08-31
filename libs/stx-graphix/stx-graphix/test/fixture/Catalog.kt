package com.strange.graphix.fixture

import com.strange.graphix.schema.Argument
import com.strange.graphix.schema.BatchMapping
import com.strange.graphix.schema.GraphQLContext
import com.strange.graphix.schema.GraphQLDescription
import com.strange.graphix.schema.GraphQLIgnore
import com.strange.graphix.schema.GraphQLName
import com.strange.graphix.schema.MutationMapping
import com.strange.graphix.schema.QueryMapping
import com.strange.graphix.schema.SchemaMapping
import com.strange.graphix.schema.SubscriptionMapping
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.serialization.Serializable
import org.reactivestreams.Publisher
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Product(
    val id: String,
    val name: String,
    val tags: List<String> = emptyList(),
    @GraphQLIgnore val secret: String = "hidden",
)

@Serializable
enum class Size { S, M, L }

@Serializable
data class CreateProductInput(
    @Argument val name: String,
    @Argument val tags: List<String> = emptyList(),
)

class ProductQueries(
    private val products: MutableList<Product> = mutableListOf(Product("p1", "Mug", listOf("kitchen"))),
) {
    @QueryMapping
    @GraphQLDescription("A product by id, or null.")
    suspend fun product(
        @Argument id: String,
    ): Product? = products.find { it.id == id }

    @QueryMapping
    fun products(): List<Product> = products

    @QueryMapping
    fun sizes(): List<Size> = Size.entries
}

@Serializable
data class Review(
    val id: String,
    val body: String,
)

class ProductFields(
    private val reviews: Map<String, List<Review>> =
        mapOf("p1" to listOf(Review("r1", "Nice mug"))),
    val loads: AtomicInteger = AtomicInteger(),
) {
    @SchemaMapping
    fun extra(product: Product): String = product.name.uppercase()

    @SchemaMapping
    fun tagged(
        product: Product,
        @Argument prefix: String = "x",
    ): String = "$prefix-${product.name}"

    @BatchMapping
    fun reviews(products: List<Product>): Map<Product, List<Review>> {
        loads.incrementAndGet()
        return products.associateWith { reviews[it.id].orEmpty() }
    }
}

class DuplicateNameFields {
    @SchemaMapping
    fun name(product: Product): String = product.name
}

class BothMappings {
    @SchemaMapping
    @BatchMapping
    fun reviews(products: List<Product>): Map<Product, List<Review>> = emptyMap()
}

class NamedSchemaFields {
    @SchemaMapping(typeName = "Product", field = "nick")
    fun unused(product: Product): String = product.name
}

class BadBatchFields {
    @BatchMapping
    fun reviews(
        products: List<Product>,
        limit: Int,
    ): Map<Product, List<Review>> = emptyMap()
}

class ProductMutations(
    private val products: MutableList<Product>,
) {
    @MutationMapping
    suspend fun createProduct(
        @Argument input: CreateProductInput,
    ): Product {
        val created = Product(id = "p${products.size + 1}", name = input.name, tags = input.tags)
        products += created
        return created
    }
}

class GreetingQueries {
    @QueryMapping
    fun hello(): String = "world"

    @QueryMapping
    @GraphQLName("shout")
    fun loud(
        @Argument name: String = "stranger",
    ): String = name.uppercase()
}

data class Caller(
    val locale: String,
)

class ContextQueries {
    @QueryMapping
    fun who(
        @GraphQLContext caller: Caller,
    ): String = caller.locale
}

class BoomQueries {
    @QueryMapping
    fun boom(): String = throw IllegalStateException("nope")
}

class TickSubscriptions {
    @SubscriptionMapping
    fun ticks(): Flow<Int> = flowOf(1, 2, 3)
}

class HangSubscriptions {
    @SubscriptionMapping
    fun hang(): Flow<Int> = flow { awaitCancellation() }
}

class TickPublisherSubscriptions {
    @SubscriptionMapping
    fun ticks(): Publisher<Int> = flowOf(1, 2).asPublisher()
}

class ContextSubscriptions {
    @SubscriptionMapping
    fun who(
        @GraphQLContext caller: Caller,
    ): Flow<String> = flowOf(caller.locale)
}

class BadSubscriptions {
    @SubscriptionMapping
    fun ticks(): Int = 1
}

@OptIn(ExperimentalUuidApi::class)
class ScalarQueries {
    @QueryMapping
    fun epoch(): Instant = Instant.fromEpochMilliseconds(0)

    @QueryMapping
    fun id(): Uuid = Uuid.parse("00112233-4455-6677-8899-aabbccddeeff")

    @QueryMapping
    fun big(
        @Argument n: Long,
    ): Long = n + 1
}

class NotSerializable

class BadQueries {
    @QueryMapping
    fun bad(): NotSerializable = NotSerializable()
}

@Serializable
data class UnmarkedInput(
    val name: String,
)

class BadInputQueries {
    @QueryMapping
    fun echo(
        @Argument input: UnmarkedInput,
    ): String = input.name
}
