package com.strange.example.graphix.shop

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication

class ShopTest :
    FeatureSpec({
        feature("the catalogue") {
            scenario("lists products over POST /graphql") {
                testApplication {
                    application { shop() }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ products { name price reviews { body } } }"}""")
                        }
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain "Mug"
                    response.bodyAsText() shouldContain "Kettle"
                    response.bodyAsText() shouldContain "Holds coffee"
                }
            }

            scenario("a mutation writes and the next query reads it") {
                testApplication {
                    application { shop() }
                    client
                        .post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"mutation { addProduct(name: \"Anvil\", price: 9000) { name } }"}""")
                        }.bodyAsText() shouldContain "Anvil"
                    client
                        .post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ products { name } }"}""")
                        }.bodyAsText() shouldContain "Anvil"
                }
            }
        }
    })
