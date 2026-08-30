package com.strange.graphql.fixture

import com.strange.graphql.schema.GraphQLContext
import com.strange.graphql.schema.GraphQLDescription
import com.strange.graphql.schema.GraphQLIgnore
import com.strange.graphql.schema.GraphQLName
import com.strange.graphql.schema.Mutation
import com.strange.graphql.schema.Query
import kotlinx.serialization.Serializable
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
    val name: String,
    val tags: List<String> = emptyList(),
)

class ProductQueries(
    private val products: MutableList<Product> = mutableListOf(Product("p1", "Mug", listOf("kitchen"))),
) {
    @Query
    @GraphQLDescription("A product by id, or null.")
    suspend fun product(id: String): Product? = products.find { it.id == id }

    @Query
    fun products(): List<Product> = products

    @Query
    fun sizes(): List<Size> = Size.entries
}

class ProductMutations(
    private val products: MutableList<Product>,
) {
    @Mutation
    suspend fun createProduct(input: CreateProductInput): Product {
        val created = Product(id = "p${products.size + 1}", name = input.name, tags = input.tags)
        products += created
        return created
    }
}

class GreetingQueries {
    @Query
    fun hello(): String = "world"

    @Query
    @GraphQLName("shout")
    fun loud(name: String = "stranger"): String = name.uppercase()
}

data class Caller(
    val locale: String,
)

class ContextQueries {
    @Query
    fun who(
        @GraphQLContext caller: Caller,
    ): String = caller.locale
}

class BoomQueries {
    @Query
    fun boom(): String = throw IllegalStateException("nope")
}

@OptIn(ExperimentalUuidApi::class)
class ScalarQueries {
    @Query
    fun epoch(): Instant = Instant.fromEpochMilliseconds(0)

    @Query
    fun id(): Uuid = Uuid.parse("00112233-4455-6677-8899-aabbccddeeff")

    @Query
    fun big(n: Long): Long = n + 1
}

class NotSerializable

class BadQueries {
    @Query
    fun bad(): NotSerializable = NotSerializable()
}
