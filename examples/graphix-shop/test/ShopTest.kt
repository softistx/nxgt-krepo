package com.softistx.example.graphix.shop

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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

            scenario("a union query selects with inline fragments, resolved by class name alone") {
                testApplication {
                    application { shop() }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """{"query":"{ search(term: \"co\") { __typename ... on Product { name } ... on Review { body } } }"}""",
                            )
                        }
                    response.status shouldBe HttpStatusCode.OK
                    val body = response.bodyAsText()
                    body shouldContain "\"__typename\":\"Review\""
                    body shouldContain "Holds coffee"
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

        feature("the sandbox") {
            scenario("GET /sandbox is the Apollo page, not JSON") {
                testApplication {
                    application { shop() }
                    val response = client.get("/sandbox")

                    response.status shouldBe HttpStatusCode.OK
                    response.headers[HttpHeaders.ContentType] shouldContain "text/html"
                    response.bodyAsText() shouldContain "embeddable-sandbox.cdn.apollographql.com"
                }
            }
        }
    })
