package com.softistx.graphix.spring

import com.softistx.common.serialization.lenientJson
import com.softistx.graphix.Graphix
import com.softistx.graphix.http.SubscriptionProtocol
import com.softistx.graphix.spring.fixture.BoomQueries
import com.softistx.graphix.spring.fixture.GreetingQueries
import com.softistx.graphix.spring.fixture.TickSubscriptions
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

class GraphixHttpTest :
    FeatureSpec({
        fun client(
            vararg roots: Any,
            subscriptions: SubscriptionProtocol = SubscriptionProtocol.Sse,
        ): WebTestClient {
            val engine =
                Graphix {
                    roots.forEach { addController(it) }
                }
            return WebTestClient
                .bindToRouterFunction(
                    GraphixHandler(engine, lenientJson, "/graphql", subscriptions).router(),
                ).build()
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
                    .expectBody<String>()
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
                    .expectBody<String>()
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
                    .expectBody<String>()
                    .value { it shouldContain "malformed GraphQL JSON" }
            }
        }

        feature("POST /graphql subscription") {
            scenario("graphql-ws refuses a POST subscription") {
                client(GreetingQueries(), TickSubscriptions(), subscriptions = SubscriptionProtocol.GraphqlWs)
                    .post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"query":"subscription { ticks }"}""")
                    .exchange()
                    .expectStatus()
                    .isBadRequest
                    .expectBody<String>()
                    .value { it shouldContain "graphql-ws" }
            }

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
                    .expectBody<String>()
                    .value {
                        it shouldContain "ticks"
                        it shouldContain "1"
                    }
            }
        }

        feature("introspection") {
            scenario("POST { __schema } returns the query type") {
                client(GreetingQueries())
                    .post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"query":"{ __schema { queryType { name } } }"}""")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .value { it shouldContain """"name":"Query"""" }
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
                    .expectBody<String>()
                    .value { it shouldContain "world" }
            }
        }
    })
