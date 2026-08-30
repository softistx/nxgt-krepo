package com.strange.graphix.spring

import com.strange.common.serialization.lenientJson
import com.strange.graphix.Graphix
import com.strange.graphix.spring.fixture.BoomQueries
import com.strange.graphix.spring.fixture.GreetingQueries
import com.strange.graphix.spring.fixture.TickSubscriptions
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

class GraphixHttpTest :
    FeatureSpec({
        fun client(vararg roots: Any): WebTestClient {
            val engine =
                Graphix {
                    roots.forEach { addController(it) }
                }
            return WebTestClient.bindToRouterFunction(GraphixHandler(engine, lenientJson, "/graphql").router()).build()
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

        feature("POST /graphql subscription") {
            scenario("a subscription is text/event-stream of GraphQL results") {
                client(GreetingQueries(), TickSubscriptions())
                    .post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"query":"subscription { ticks }"}""")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                    .expectBody(String::class.java)
                    .value {
                        it shouldContain "ticks"
                        it shouldContain "1"
                    }
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
