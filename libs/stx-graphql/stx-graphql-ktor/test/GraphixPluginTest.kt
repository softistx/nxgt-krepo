package com.strange.graphql.ktor

import com.strange.graphql.ktor.fixture.BoomQueries
import com.strange.graphql.ktor.fixture.GreetingQueries
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication

class GraphixPluginTest :
    FeatureSpec({
        feature("POST /graphql") {
            scenario("a JSON body executes and returns data") {
                testApplication {
                    application {
                        install(GraphQL) { schema { query(GreetingQueries()) } }
                    }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ hello }"}""")
                        }
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain """"hello":"world""""
                }
            }

            scenario("a field error is HTTP 200 with errors[]") {
                testApplication {
                    application {
                        install(GraphQL) { schema { query(BoomQueries()) } }
                    }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ boom }"}""")
                        }
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain "nope"
                }
            }

            scenario("malformed JSON is HTTP 400") {
                testApplication {
                    application {
                        install(GraphQL) { schema { query(GreetingQueries()) } }
                    }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("{")
                        }
                    response.status shouldBe HttpStatusCode.BadRequest
                    response.bodyAsText() shouldContain "malformed GraphQL JSON"
                }
            }
        }

        feature("GET /graphql") {
            scenario("the query parameter executes") {
                testApplication {
                    application {
                        install(GraphQL) { schema { query(GreetingQueries()) } }
                    }
                    val response = client.get("/graphql?query=%7Bhello%7D")
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain """"hello":"world""""
                }
            }
        }

        feature("instance") {
            scenario("an engine built elsewhere is the one the route uses") {
                val engine =
                    com.strange.graphql.Graphix {
                        query(GreetingQueries())
                    }
                testApplication {
                    application {
                        install(GraphQL) { instance = engine }
                    }
                    client
                        .post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ hello }"}""")
                        }.bodyAsText() shouldContain "world"
                }
            }
        }
    })
