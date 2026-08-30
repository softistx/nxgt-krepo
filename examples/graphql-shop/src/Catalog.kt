package com.strange.example.graphql.shop

import com.strange.graphql.schema.Mutation
import com.strange.graphql.schema.Query
import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val name: String,
    val price: Long,
)

class Catalog {
    private val products =
        mutableListOf(
            Product("p1", "Mug", 1200),
            Product("p2", "Kettle", 4500),
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
        return created
    }
}
