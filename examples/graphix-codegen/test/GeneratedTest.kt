package com.softistx.example.graphix.codegen

import com.softistx.example.graphix.codegen.apollo.ProductsQuery
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class GeneratedTest :
    FeatureSpec({
        feature("DGS types") {
            scenario("an input is a data class the schema named") {
                val input = addProduct("Anvil", 9000)
                input.name shouldBe "Anvil"
                input.price shouldBe 9000
            }
        }

        feature("Apollo documents") {
            scenario("the generated operation carries the GraphQL document") {
                ProductsQuery.OPERATION_DOCUMENT shouldContain "products"
                productsQuery().name() shouldBe "Products"
            }
        }
    })
