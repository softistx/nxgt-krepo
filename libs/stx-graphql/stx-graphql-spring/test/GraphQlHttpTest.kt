package com.strange.graphql.spring

import com.strange.common.serialization.lenientJson
import com.strange.graphql.GraphQl
import com.strange.graphql.spring.fixture.BoomQueries
import com.strange.graphql.spring.fixture.GreetingQueries
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class GraphQlHttpTest :
    FeatureSpec({
        fun client(vararg roots: Any): WebTestClient {
            val engine =
                GraphQl {
                    roots.forEach { addController(it) }
                }
            return WebTestClient.bindToRouterFunction(GraphQlHandler(engine, lenientJson, "/graphql").router()).build()
        }

        feature("POST /graphql") {
            scenario("a JSON body executes and returns data") {
                client(GreetingQueries())
                    .post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"query":"{ hello }"}""")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody(String::class.java)
                    .value { it shouldContain "world" }
            }

            scenario("a field error is HTTP 200 with errors[]") {
                client(BoomQueries())
                    .post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"query":"{ boom }"}""")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody(String::class.java)
                    .value { it shouldContain "nope" }
            }

            scenario("malformed JSON is HTTP 400") {
                client(GreetingQueries())
                    .post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{")
                    .exchange()
                    .expectStatus()
                    .isBadRequest
                    .expectBody(String::class.java)
                    .value { it shouldContain "malformed GraphQL JSON" }
            }
        }

        feature("GET /graphql") {
            scenario("the query parameter executes") {
                client(GreetingQueries())
                    .get()
                    .uri("/graphql?query={q}", "{hello}")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody(String::class.java)
                    .value { it shouldContain "world" }
            }
        }
    })
