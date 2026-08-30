package com.strange.openapi.emit

import com.strange.openapi.ApiGroup
import com.strange.openapi.ApiModel
import com.strange.openapi.Operation
import com.strange.openapi.SAMPLE_MODEL
import com.strange.openapi.TypeRef
import com.strange.openapi.ktorfit.KtorfitEmitter
import com.strange.openapi.models.ModelsOnlyEmitter
import com.strange.openapi.render
import com.strange.openapi.spring.SpringEmitter
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private const val FILE = "com.example.api.Endpoints"

class EndpointsTest :
    FeatureSpec({

        val endpoints = SpringEmitter().render().getValue(FILE)

        feature("the constants") {
            scenario("one per operation, named for the verb and the path") {
                // Declaration and initializer asserted apart, because KotlinPoet wraps a long
                // initializer onto its own line and where it chooses to is not this file's contract.
                endpoints shouldContain "public val GET_CATEGORIES_ID: Endpoint"
                endpoints shouldContain """Endpoint("GET", "/categories/{id}", "findCategory","""
                endpoints shouldContain "public val POST_CATEGORIES: Endpoint"
                endpoints shouldContain "public val PUT_CATEGORIES_ID_PHOTO: Endpoint"
            }

            scenario("the path carries the leading slash the IR drops") {
                // `Operation.path` is stored slash-less because both clients feed it straight into
                // an annotation. A route is written with the slash, so it is put back here — and the
                // annotations must not change with it.
                endpoints shouldContain """"/categories/{id}""""
                endpoints shouldNotContain """Endpoint("GET", "categories/{id}""""
            }

            scenario("the operationId is carried, so the document stays greppable from the constant") {
                endpoints shouldContain """"findCategory""""
            }

            scenario("a summary the document gives is carried, and a missing one is null") {
                endpoints shouldContain """"Get category by ID""""
                // `createCategory` has no summary in the fixture.
                endpoints shouldContain """Endpoint("POST", "/categories", "createCategory", null)"""
            }

            scenario("`all` lists every one of them") {
                endpoints shouldContain "public val all: List<Endpoint> = listOf("
                listOf("GET_CATEGORIES_ID", "POST_CATEGORIES", "PUT_CATEGORIES_ID_PHOTO").forEach {
                    endpoints shouldContain it
                }
            }
        }

        feature("the Endpoint record") {
            scenario("label is computed, so the format has one definition") {
                endpoints shouldContain "public val label: String"
                endpoints shouldContain """get() = ${"\"\"\""}[${'$'}method] ${'$'}value${"\"\"\""}"""
            }

            scenario("it is a data class, because a value class holds one property and this holds four") {
                endpoints shouldContain "public data class Endpoint("
            }

            scenario("path fills the template and refuses the wrong number of values") {
                endpoints shouldContain "public fun Endpoint.path(vararg values: String): String"
                endpoints shouldContain "require(slots == values.size)"
            }
        }

        feature("every client style gets it") {
            scenario("the same file, byte for byte, from all three emitters") {
                // The point of putting it in `emit/`: it depends on the IR and on nothing a client
                // style decides, so a Ktorfit consumer and a Spring one describe the same routes.
                val ktorfit = KtorfitEmitter().render().getValue(FILE)
                val modelsOnly = ModelsOnlyEmitter().render().getValue(FILE)

                ktorfit shouldBe endpoints
                modelsOnly shouldBe endpoints
            }

            scenario("it lands in the root package, not under utils") {
                // `.utils` is the machinery a caller mostly does not read. This is the opposite.
                SpringEmitter().render() shouldContainKey FILE
            }
        }

        feature("an operation naming its own constant") {
            scenario("x-kotlin-endpoint wins over the derivation") {
                val model =
                    ApiModel(
                        groups =
                            listOf(
                                ApiGroup(
                                    name = "OrdersApi",
                                    operations =
                                        listOf(
                                            Operation(
                                                id = "findOrder",
                                                name = "findOrder",
                                                httpMethod = "GET",
                                                path = "orders/{id}",
                                                parameters = emptyList(),
                                                returnType = TypeRef.UnitRef,
                                                constant = "ONE_ORDER",
                                            ),
                                        ),
                                ),
                            ),
                        models = emptyList(),
                    )

                val named = SpringEmitter().render(model).getValue(FILE)

                named shouldContain "public val ONE_ORDER: Endpoint"
                // The declaration specifically: `GET_ORDERS_ID` also appears in `path`'s KDoc as
                // the worked example, and a bare `shouldNotContain` matches that instead.
                named shouldNotContain "public val GET_ORDERS_ID"
            }
        }
    })
