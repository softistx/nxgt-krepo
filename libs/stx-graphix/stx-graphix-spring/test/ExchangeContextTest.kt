package com.softistx.graphix.spring

import com.softistx.common.serialization.lenientJson
import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixResult
import com.softistx.graphix.http.SubscriptionProtocol
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.intercept.intercept
import com.softistx.graphix.intercept.put
import com.softistx.graphix.schema.contextParameter
import com.softistx.graphix.spring.fixture.Caller
import com.softistx.graphix.spring.fixture.ContextQueries
import com.softistx.graphix.spring.fixture.ExchangeQueries
import com.softistx.graphix.spring.fixture.ExchangeSubscriptions
import com.softistx.graphix.spring.fixture.GreetingQueries
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.flowOf
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.server.ServerWebExchange

/**
 * What a `@GraphQLController` method and a [GraphixInterceptor] can see of the HTTP request behind a
 * GraphQL operation.
 *
 * The harness builds the engine the way [GraphixAutoConfiguration] does — `contextParameter` and
 * interceptors included — because that registration is exactly what makes an exchange-taking method
 * a legal resolver rather than one missing an `@Argument`.
 */
class ExchangeContextTest :
    FeatureSpec({
        fun client(
            vararg roots: Any,
            subscriptions: SubscriptionProtocol = SubscriptionProtocol.Sse,
            interceptors: List<GraphixInterceptor> = emptyList(),
        ): WebTestClient {
            val engine =
                Graphix {
                    contextParameter(ServerWebExchange::class)
                    resolvers(roots.asList())
                    interceptors.forEach { intercept(it) }
                }
            return WebTestClient
                .bindToRouterFunction(
                    GraphixHandler(engine, lenientJson, "/graphql", subscriptions).router(),
                ).build()
        }

        feature("the exchange as a resolver parameter") {
            scenario("a query reads a header through it") {
                client(ExchangeQueries())
                    .post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User", "ada")
                    .bodyValue("""{"query":"{ me }"}""")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .value { it shouldContain """"me":"ada"""" }
            }

            scenario("an SSE subscription reads the same header, per event") {
                client(ExchangeQueries(), ExchangeSubscriptions())
                    .post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User", "grace")
                    .bodyValue("""{"query":"subscription { callers }"}""")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .value { it shouldContain """"callers":"grace"""" }
            }
        }

        feature("an interceptor") {
            scenario("reads the exchange and puts what a resolver asks for in the context") {
                client(
                    ContextQueries(),
                    interceptors =
                        listOf(
                            GraphixInterceptor {
                                put(Caller(exchange.request.headers.getFirst("X-User") ?: "anonymous"))
                                proceed()
                            },
                        ),
                ).post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User", "turing")
                    .bodyValue("""{"query":"{ who }"}""")
                    .exchange()
                    .expectBody<String>()
                    .value { it shouldContain """"who":"turing"""" }
            }

            scenario("not calling proceed() is a request the engine never runs") {
                client(
                    GreetingQueries(),
                    interceptors =
                        listOf(
                            GraphixInterceptor {
                                if (exchange.request.headers.getFirst("X-User") == null) {
                                    flowOf(GraphixResult(data = null, errors = listOf(GraphixError("unauthenticated"))))
                                } else {
                                    proceed()
                                }
                            },
                        ),
                ).post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"query":"{ hello }"}""")
                    .exchange()
                    // Still HTTP 200: a GraphQL error is a body, not a status.
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .value { it shouldContain "unauthenticated" }
            }
        }
    })
