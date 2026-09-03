package com.softistx.graphix.ktor

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixCustomizer
import com.softistx.graphix.error.GraphixErrorType.BAD_REQUEST
import com.softistx.graphix.error.on
import com.softistx.graphix.error.withErrorType
import com.softistx.graphix.error.withMessage
import com.softistx.graphix.http.GRAPHQL_TRANSPORT_WS
import com.softistx.graphix.http.SubscriptionProtocol
import com.softistx.graphix.ktor.fixture.BoomQueries
import com.softistx.graphix.ktor.fixture.ExpiryQueries
import com.softistx.graphix.ktor.fixture.GreetingQueries
import com.softistx.graphix.ktor.fixture.TickSubscriptions
import com.softistx.graphix.scalar.graphQLScalar
import com.softistx.graphix.scalar.scalar
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

class GraphixPluginTest :
    FeatureSpec({
        feature("Accept-Language") {
            scenario("the header decides the language a coercion error comes back in") {
                testApplication {
                    application {
                        install(GraphQL) { schema { resolvers(ExpiryQueries()) } }
                    }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            header(HttpHeaders.AcceptLanguage, "fr-CA,fr;q=0.9,en;q=0.8")
                            setBody("""{"query":"{ expiry(at: \"the 2nd\") }"}""")
                        }
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain "ne peut pas analyser"
                }
            }

            scenario("no header is the engine's own default, not a failed request") {
                testApplication {
                    application {
                        install(GraphQL) { schema { resolvers(ExpiryQueries()) } }
                    }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ expiry(at: \"2026-09-02\") }"}""")
                        }
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain """"expiry":"2026-09-02""""
                }
            }
        }

        feature("POST /graphql") {
            scenario("a JSON body executes and returns data") {
                testApplication {
                    application {
                        install(GraphQL) { schema { resolvers(GreetingQueries()) } }
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
                        install(GraphQL) { schema { resolvers(BoomQueries()) } }
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
                        install(GraphQL) { schema { resolvers(GreetingQueries()) } }
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

        feature("introspection") {
            scenario("POST { __schema } returns the query type") {
                testApplication {
                    application {
                        install(GraphQL) { schema { resolvers(GreetingQueries()) } }
                    }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ __schema { queryType { name } } }"}""")
                        }
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain """"name":"Query""""
                }
            }

            scenario("GET query={ __schema } is the same") {
                testApplication {
                    application {
                        install(GraphQL) { schema { resolvers(GreetingQueries()) } }
                    }
                    val response = client.get("/graphql?query=%7B__schema%7BqueryType%7Bname%7D%7D%7D")
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain """"name":"Query""""
                }
            }
        }

        feature("GET /graphql") {
            scenario("the query parameter executes") {
                testApplication {
                    application {
                        install(GraphQL) { schema { resolvers(GreetingQueries()) } }
                    }
                    val response = client.get("/graphql?query=%7Bhello%7D")
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain """"hello":"world""""
                }
            }
        }

        feature("POST /graphql subscription") {
            scenario("a subscription is text/event-stream of GraphQL results") {
                testApplication {
                    application {
                        install(GraphQL) {
                            schema {
                                resolvers(GreetingQueries())
                                resolvers(TickSubscriptions())
                            }
                        }
                    }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"subscription { ticks }"}""")
                        }
                    response.status shouldBe HttpStatusCode.OK
                    response.headers[HttpHeaders.ContentType] shouldContain "text/event-stream"
                    val body = response.bodyAsText()
                    body shouldContain "data:"
                    body shouldContain """"ticks":1"""
                    body shouldContain """"ticks":3"""
                }
            }
        }

        feature("graphql-ws") {
            scenario("POST of a subscription is 400 when the protocol is graphql-ws") {
                testApplication {
                    application {
                        install(GraphQL) {
                            subscriptions = SubscriptionProtocol.GraphqlWs
                            schema {
                                resolvers(GreetingQueries())
                                resolvers(TickSubscriptions())
                            }
                        }
                    }
                    val response =
                        client.post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"subscription { ticks }"}""")
                        }
                    response.status shouldBe HttpStatusCode.BadRequest
                    response.bodyAsText() shouldContain "graphql-ws"
                }
            }

            scenario("a WebSocket subscription emits next then complete") {
                testApplication {
                    application {
                        install(GraphQL) {
                            subscriptions = SubscriptionProtocol.GraphqlWs
                            schema {
                                resolvers(GreetingQueries())
                                resolvers(TickSubscriptions())
                            }
                        }
                    }
                    val wsClient = createClient { install(WebSockets) }
                    wsClient.webSocket("/graphql", request = {
                        header(HttpHeaders.SecWebSocketProtocol, GRAPHQL_TRANSPORT_WS)
                    }) {
                        send(Frame.Text("""{"type":"connection_init"}"""))
                        incoming.receive().let { (it as Frame.Text).readText() shouldContain "connection_ack" }
                        send(Frame.Text("""{"id":"1","type":"subscribe","payload":{"query":"subscription { ticks }"}}"""))
                        val first = (incoming.receive() as Frame.Text).readText()
                        first shouldContain "next"
                        first shouldContain """"ticks":1"""
                    }
                }
            }
        }

        feature("customize and DI") {
            scenario("customize { } registers a scalar") {
                testApplication {
                    application {
                        install(GraphQL) {
                            schema { resolvers(GreetingQueries()) }
                            customize {
                                scalar(
                                    graphQLScalar("Money") { serialize { value -> value.toString() } },
                                )
                            }
                        }
                    }
                    val sdl =
                        client
                            .post("/graphql") {
                                contentType(ContentType.Application.Json)
                                setBody("""{"query":"{ __type(name: \"Money\") { name } }"}""")
                            }.bodyAsText()
                    sdl shouldContain "Money"
                }
            }
        }

        feature("instance") {
            scenario("an engine built elsewhere is the one the route uses") {
                val engine = Graphix { resolvers(GreetingQueries()) }
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

        feature("errors { }") {
            scenario("a handler installed with the plugin decides what a throw becomes") {
                testApplication {
                    application {
                        install(GraphQL) {
                            schema { resolvers(BoomQueries()) }
                            errors {
                                on<IllegalStateException> { failure ->
                                    error.withMessage("caught ${failure.message}").withErrorType(BAD_REQUEST)
                                }
                            }
                        }
                    }
                    val body =
                        client
                            .post("/graphql") {
                                contentType(ContentType.Application.Json)
                                setBody("""{"query":"{ boom }"}""")
                            }.bodyAsText()

                    body shouldContain "caught nope"
                    // The classification reaches the wire through extensions, which is the only
                    // place the GraphQL spec has for it.
                    body shouldContain "BAD_REQUEST"
                }
            }

            scenario("with an adopted instance it is refused, the way intercept { } is") {
                val engine = Graphix { resolvers(GreetingQueries()) }

                shouldThrow<IllegalStateException> {
                    testApplication {
                        application {
                            install(GraphQL) {
                                instance = engine
                                errors { fallback { error.withMessage("never") } }
                            }
                        }
                        client.get("/graphql")
                    }
                }.message shouldContain "register handlers where that engine is built"
            }
        }

        feature("GET /sandbox") {
            scenario("is not served unless asked for") {
                testApplication {
                    application { install(GraphQL) { schema { resolvers(GreetingQueries()) } } }

                    client.get("/sandbox").status shouldBe HttpStatusCode.NotFound
                }
            }

            scenario("sandbox = true serves the Apollo page as HTML") {
                testApplication {
                    application {
                        install(GraphQL) {
                            sandbox = true
                            schema { resolvers(GreetingQueries()) }
                        }
                    }
                    val response = client.get("/sandbox")

                    response.status shouldBe HttpStatusCode.OK
                    response.headers[HttpHeaders.ContentType] shouldContain "text/html"
                    response.bodyAsText() shouldContain "embeddable-sandbox.cdn.apollographql.com"
                }
            }

            scenario("the page resolves the GraphQL path against its own origin") {
                testApplication {
                    application {
                        install(GraphQL) {
                            path = "/api/graphql"
                            sandbox = true
                            schema { resolvers(GreetingQueries()) }
                        }
                    }

                    client.get("/sandbox").bodyAsText() shouldContain
                        """new URL("/api/graphql", window.location.origin)"""
                }
            }

            scenario("sandboxPath moves it, and sandboxEndpoint pins the URL") {
                testApplication {
                    application {
                        install(GraphQL) {
                            sandbox = true
                            sandboxPath = "/explorer"
                            sandboxEndpoint = "https://api.example.test/graphql"
                            schema { resolvers(GreetingQueries()) }
                        }
                    }

                    client.get("/sandbox").status shouldBe HttpStatusCode.NotFound
                    val response = client.get("/explorer")
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain """const configured = "https://api.example.test/graphql";"""
                }
            }

            scenario("an adopted engine still gets the page: the plugin serves it, not the engine") {
                testApplication {
                    val engine = com.softistx.graphix.Graphix { resolvers(GreetingQueries()) }
                    application {
                        install(GraphQL) {
                            instance = engine
                            sandbox = true
                        }
                    }

                    client.get("/sandbox").status shouldBe HttpStatusCode.OK
                }
            }
        }
    })
