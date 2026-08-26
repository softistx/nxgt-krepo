package com.strange.openapi.models

import com.strange.openapi.render
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ModelEmitterTest :
    FunSpec({

        test("models only: no interfaces, just the schemas") {
            val files = ModelsOnlyEmitter().render()

            files.keys.none { it == "com.example.api.CategoriesApi" } shouldBe true
            files.keys.toList() shouldBe listOf("com.example.api.model.Category")
        }

        test("the kotlinx style is what a client-less module gets by default") {
            val category = ModelsOnlyEmitter().render().getValue("com.example.api.model.Category")

            category shouldContain "@Serializable"
            category shouldContain """@SerialName("created_at")"""
            category shouldContain "import kotlin.time.Instant"
            category shouldContain "meta: JsonObject? = null"
        }

        test("the jackson style swaps the annotations and the types it cannot bind") {
            val category =
                ModelsOnlyEmitter(ModelStyle.Jackson)
                    .render()
                    .getValue("com.example.api.model.Category")

            category shouldNotContain "@Serializable"
            category shouldContain """@JsonProperty("created_at")"""
            category shouldContain "import java.time.Instant"
            category shouldContain "meta: Map<String, Any?>? = null"
        }

        test("both styles agree on everything the spec does pin down") {
            val kotlinx = ModelsOnlyEmitter(ModelStyle.Kotlinx).render().getValue("com.example.api.model.Category")
            val jackson = ModelsOnlyEmitter(ModelStyle.Jackson).render().getValue("com.example.api.model.Category")

            listOf(kotlinx, jackson).forEach { source ->
                source shouldContain "public data class Category"
                source shouldContain "public val id: String"
                // `id` matches its wire name, so neither style annotates it
                source.substringAfter("public val id").substringBefore("\n") shouldNotContain "("
            }
        }
    })
