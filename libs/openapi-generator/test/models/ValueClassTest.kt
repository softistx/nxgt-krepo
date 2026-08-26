package com.strange.openapi.models

import com.strange.openapi.ApiModel
import com.strange.openapi.Field
import com.strange.openapi.ObjectType
import com.strange.openapi.TypeRef
import com.strange.openapi.ValueClassType
import com.strange.openapi.render
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private val MODEL =
    ApiModel(
        groups = emptyList(),
        models =
            listOf(
                ValueClassType(name = "OrderId", base = TypeRef.StringRef, doc = "Identifies one order."),
                ValueClassType(name = "Attempt", base = TypeRef.IntRef),
                ObjectType(
                    name = "Order",
                    fields =
                        listOf(
                            Field("id", "id", TypeRef.ModelRef("OrderId"), required = true),
                            Field("total", "total", TypeRef.ExternalRef("com.example.money.Money"), required = false),
                        ),
                ),
            ),
    )

private fun render(style: ModelStyle) = ModelsOnlyEmitter(style).render(MODEL)

/**
 * `OrderId` instead of `String` costs nothing at runtime and stops an order id being passed where a
 * customer id belongs. Both libraries bind the shape below with no annotation beyond the style's
 * own — round-tripped through each before this emitter was written, rather than assumed.
 */
class ValueClassTest :
    FeatureSpec({

        feature("the declaration") {
            scenario("it is an inline value class over the scalar the document described") {
                val source = render(ModelStyle.Kotlinx).getValue("com.example.api.model.OrderId")

                source shouldContain "@JvmInline"
                source shouldContain "public value class OrderId"
                source shouldContain "Identifies one order."
            }

            scenario("toString is the value underneath, so it works as a path or query argument") {
                // Ktorfit converts an argument with toString, and the default would send
                // `OrderId(value=o-1)`.
                render(ModelStyle.Kotlinx).getValue("com.example.api.model.OrderId") shouldContain
                    "override fun toString(): String ="
                render(ModelStyle.Kotlinx).getValue("com.example.api.model.Attempt") shouldContain
                    "override fun toString(): String ="
            }
        }

        feature("what each style adds") {
            scenario("kotlinx marks it serializable") {
                render(ModelStyle.Kotlinx).getValue("com.example.api.model.OrderId") shouldContain "@Serializable"
            }

            scenario("jackson needs nothing at all: its Kotlin module binds value classes itself") {
                val source = render(ModelStyle.Jackson).getValue("com.example.api.model.OrderId")

                source shouldContain "@JvmInline"
                source shouldNotContain "@Serializable"
            }
        }

        feature("a type the consumer owns") {
            scenario("it is imported and used, and no file is generated for it") {
                val files = render(ModelStyle.Kotlinx)

                files.getValue("com.example.api.model.Order") shouldContain "com.example.money.Money"
                files.keys.none { it.endsWith(".Money") } shouldBe true
            }
        }
    })
