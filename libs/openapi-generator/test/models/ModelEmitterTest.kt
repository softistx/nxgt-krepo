package com.strange.openapi.models

import com.strange.openapi.ApiModel
import com.strange.openapi.Field
import com.strange.openapi.ObjectType
import com.strange.openapi.TypeRef
import com.strange.openapi.render
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ModelEmitterTest :
    FeatureSpec({

        feature("emitting models without a client") {
            scenario("models only: no interfaces, just the schemas") {
                val files = ModelsOnlyEmitter().render()

                files.keys.none { it == "com.example.api.CategoriesApi" } shouldBe true
                files.keys.toList() shouldBe listOf("com.example.api.model.Category")
            }

            scenario("the kotlinx style is what a client-less module gets by default") {
                val category = ModelsOnlyEmitter().render().getValue("com.example.api.model.Category")

                category shouldContain "@Serializable"
                category shouldContain """@SerialName("created_at")"""
                category shouldContain "import kotlin.time.Instant"
                category shouldContain "meta: JsonObject? = null"
            }
        }

        feature("model styles") {
            scenario("the jackson style swaps the annotations and the types it cannot bind") {
                val category =
                    ModelsOnlyEmitter(ModelStyle.Jackson)
                        .render()
                        .getValue("com.example.api.model.Category")

                category shouldNotContain "@Serializable"
                category shouldContain """@JsonProperty("created_at")"""
                category shouldContain "import java.time.Instant"
                category shouldContain "meta: Map<String, Any?>? = null"
            }

            scenario("both styles agree on everything the spec does pin down") {
                val kotlinx = ModelsOnlyEmitter(ModelStyle.Kotlinx).render().getValue("com.example.api.model.Category")
                val jackson = ModelsOnlyEmitter(ModelStyle.Jackson).render().getValue("com.example.api.model.Category")

                listOf(kotlinx, jackson).forEach { source ->
                    source shouldContain "public data class Category"
                    source shouldContain "public val id: String"
                    // `id` matches its wire name, so neither style annotates it
                    source.substringAfter("public val id").substringBefore("\n") shouldNotContain "("
                }
            }
        }

        feature("optionality in the emitted source") {
            scenario("requiredness and nullability are separate switches") {
                val source = render(OPTIONALITY_MODEL)

                // required, not nullable: no `?`, no default
                source shouldContain "public val id: String,"
                // required *and* nullable: the caller must pass something, and null is something
                source shouldContain "public val note: String?,"
                // optional with nothing to fall back on
                source shouldContain "public val alias: String? = null,"
            }

            scenario("a document default is emitted instead of null, and keeps the type non-null") {
                val source = render(OPTIONALITY_MODEL)

                source shouldContain "public val size: Int = 20,"
                source shouldContain "public val label: String = \"\","
                source shouldContain "public val active: Boolean = true,"
            }

            scenario("a default that cannot be written falls back to null rather than to broken source") {
                // A list default would have to be constructed, not written; a wrong one is worse
                // than none, so the property stays nullable and defaults to null.
                render(OPTIONALITY_MODEL) shouldContain "public val tags: List<String>? = null,"
            }

            scenario("additionalProperties reaches the source as a typed map") {
                render(OPTIONALITY_MODEL) shouldContain "public val counts: Map<String, Long>? = null,"
            }
        }
    })

private val OPTIONALITY_MODEL =
    ApiModel(
        groups = emptyList(),
        models =
            listOf(
                ObjectType(
                    name = "Thing",
                    fields =
                        listOf(
                            Field("id", "id", TypeRef.StringRef, required = true),
                            Field("note", "note", TypeRef.StringRef, required = true, nullable = true),
                            Field("alias", "alias", TypeRef.StringRef, required = false),
                            Field("size", "size", TypeRef.IntRef, required = false, default = "20"),
                            Field("label", "label", TypeRef.StringRef, required = false, default = ""),
                            Field("active", "active", TypeRef.BooleanRef, required = false, default = "true"),
                            Field("tags", "tags", TypeRef.ListRef(TypeRef.StringRef), required = false, default = "[]"),
                            Field("counts", "counts", TypeRef.MapRef(TypeRef.LongRef), required = false),
                        ),
                ),
            ),
    )

private fun render(model: ApiModel) = ModelsOnlyEmitter().render(model).getValue("com.example.api.model.Thing")
