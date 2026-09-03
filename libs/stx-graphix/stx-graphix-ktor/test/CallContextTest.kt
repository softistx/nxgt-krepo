package com.softistx.graphix.ktor

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixResult
import com.softistx.graphix.http.GRAPHQL_TRANSPORT_WS
import com.softistx.graphix.http.SubscriptionProtocol
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.intercept.put
import com.softistx.graphix.ktor.fixture.Caller
import com.softistx.graphix.ktor.fixture.CallerQueries
import com.softistx.graphix.ktor.fixture.CallerSubscriptions
import com.softistx.graphix.ktor.fixture.ContextQueries
import com.softistx.graphix.ktor.fixture.GreetingQueries
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What a resolver and an interceptor can see of the HTTP request behind a GraphQL operation.
 *
 * The point of the three transports below is that they are the *same* point: the plugin puts the
 * `ApplicationCall` in the operation context on every route it registers, so one resolver signature
 * works over POST, over SSE and over a graphql-ws socket without knowing which it is on.
 */
class CallContextTest :
    FeatureSpec({

        feature("the call as a resolver parameter") {
            scenario("a query reads a header through it") {
                testApplication {
                    application {
                        install(GraphQL) { schema { resolvers(CallerQueries()) } }
                    }
                    client
                        .post("/graphql") {
                            contentType(ContentType.Application.Json)
                            header("X-User", "ada")
                            setBody("""{"query":"{ me }"}""")
                        }.bodyAsText() shouldContain """"me":"ada""""
                }
            }

            scenario("an SSE subscription reads the same header, per event") {
                testApplication {
                    application {
                        install(GraphQL) {
                            schema {
                                resolvers(CallerQueries())
                                resolvers(CallerSubscriptions())
                            }
                        }
                    }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            header("X-User", "grace")
                            setBody("""{"query":"subscription { callers }"}""")
                        }
                    response.headers[HttpHeaders.ContentType] shouldContain "text/event-stream"
                    response.bodyAsText() shouldContain """"callers":"grace""""
                }
            }

            scenario("a graphql-ws operation reads it from the handshake, the only request a socket has") {
                testApplication {
                    application {
                        install(GraphQL) {
                            subscriptions = SubscriptionProtocol.GraphqlWs
                            schema {
                                resolvers(CallerQueries())
                                resolvers(CallerSubscriptions())
                            }
                        }
                    }
                    val wsClient = createClient { install(WebSockets) }
                    wsClient.webSocket("/graphql", request = {
                        header(HttpHeaders.SecWebSocketProtocol, GRAPHQL_TRANSPORT_WS)
                        header("X-User", "hopper")
                    }) {
                        send(Frame.Text("""{"type":"connection_init"}"""))
                        (incoming.receive() as Frame.Text).readText() shouldContain "connection_ack"
                        send(Frame.Text("""{"id":"1","type":"subscribe","payload":{"query":"subscription { callers }"}}"""))
                        (incoming.receive() as Frame.Text).readText() shouldContain """"callers":"hopper""""
                    }
                }
            }
        }

        feature("intercept { }") {
            scenario("reads the call and puts what a resolver asks for in the context") {
                testApplication {
                    application {
                        install(GraphQL) {
                            intercept {
                                put(Caller(call.request.headers["X-User"] ?: "anonymous"))
                                proceed()
                            }
                            schema { resolvers(ContextQueries()) }
                        }
                    }
                    client
                        .post("/graphql") {
                            contentType(ContentType.Application.Json)
                            header("X-User", "turing")
                            setBody("""{"query":"{ who }"}""")
                        }.bodyAsText() shouldContain """"who":"turing""""
                }
            }

            scenario("not calling proceed() is a request the engine never runs") {
                testApplication {
                    application {
                        install(GraphQL) {
                            intercept {
                                if (call.request.headers["X-User"] == null) {
                                    flowOf(GraphixResult(data = null, errors = listOf(GraphixError("unauthenticated"))))
                                } else {
                                    proceed()
                                }
                            }
                            schema { resolvers(GreetingQueries()) }
                        }
                    }
                    val refused =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ hello }"}""")
                        }
                    // Still HTTP 200: a GraphQL error is a body, not a status.
                    refused.status shouldBe HttpStatusCode.OK
                    refused.bodyAsText() shouldContain "unauthenticated"

                    client
                        .post("/graphql") {
                            contentType(ContentType.Application.Json)
                            header("X-User", "ada")
                            setBody("""{"query":"{ hello }"}""")
                        }.bodyAsText() shouldContain """"hello":"world""""
                }
            }

            scenario("blocks run outermost-first, in the order they are written") {
                val order = mutableListOf<String>()
                testApplication {
                    application {
                        install(GraphQL) {
                            intercept {
                                order += "first"
                                proceed().map { result ->
                                    order += "first-back"
                                    result
                                }
                            }
                            intercept {
                                order += "second"
                                proceed()
                            }
                            schema { resolvers(GreetingQueries()) }
                        }
                    }
                    client.post("/graphql") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"query":"{ hello }"}""")
                    }
                }
                order shouldBe listOf("first", "second", "first-back")
            }

            scenario("fromDi takes them from the container too") {
                testApplication {
                    application {
                        dependencies {
                            provide<GraphixInterceptor> {
                                GraphixInterceptor {
                                    put(Caller("from-di"))
                                    proceed()
                                }
                            }
                        }
                        install(GraphQL) {
                            fromDi = true
                            schema { resolvers(ContextQueries()) }
                        }
                    }
                    client
                        .post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ who }"}""")
                        }.bodyAsText() shouldContain """"who":"from-di""""
                }
            }
        }

        feature("instance") {
            scenario("refuses to be combined with intercept { } rather than ignoring it") {
                val engine = Graphix { resolvers(GreetingQueries()) }
                val failure =
                    shouldThrowAny {
                        testApplication {
                            application {
                                install(GraphQL) {
                                    instance = engine
                                    intercept { proceed() }
                                }
                            }
                            client.post("/graphql") {
                                contentType(ContentType.Application.Json)
                                setBody("""{"query":"{ hello }"}""")
                            }
                        }
                    }
                // Ktor may wrap an install failure, so the message is looked for down the causes.
                generateSequence(failure) { it.cause }.joinToString { it.message.orEmpty() } shouldContain "intercept"
            }
        }
    })
