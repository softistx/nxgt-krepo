package com.softistx.graphix.ktor

import com.softistx.graphix.GraphixCustomizer
import com.softistx.graphix.http.GRAPHQL_TRANSPORT_WS
import com.softistx.graphix.http.SubscriptionProtocol
import com.softistx.graphix.ktor.fixture.BoomQueries
import com.softistx.graphix.ktor.fixture.GreetingQueries
import com.softistx.graphix.ktor.fixture.TickSubscriptions
import com.softistx.graphix.scalar.graphQLScalar
import com.softistx.graphix.scalar.scalar
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
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

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

        feature("introspection") {
            scenario("POST { __schema } returns the query type") {
                testApplication {
                    application {
                        install(GraphQL) { schema { query(GreetingQueries()) } }
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
                        install(GraphQL) { schema { query(GreetingQueries()) } }
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
                        install(GraphQL) { schema { query(GreetingQueries()) } }
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
                                query(GreetingQueries())
                                subscription(TickSubscriptions())
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
                                query(GreetingQueries())
                                subscription(TickSubscriptions())
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
                                query(GreetingQueries())
                                subscription(TickSubscriptions())
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
                            schema { query(GreetingQueries()) }
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

            scenario("fromDi applies a GraphixCustomizer") {
                testApplication {
                    application {
                        dependencies {
                            provide<GraphixCustomizer> {
                                GraphixCustomizer {
                                    scalar(graphQLScalar("Money") { serialize { value -> value.toString() } })
                                }
                            }
                        }
                        install(GraphQL) {
                            fromDi = true
                            schema { query(GreetingQueries()) }
                        }
                    }
                    client
                        .post("/graphql") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"query":"{ __type(name: \"Money\") { name } }"}""")
                        }.bodyAsText() shouldContain "Money"
                }
            }
        }

        feature("instance") {
            scenario("an engine built elsewhere is the one the route uses") {
                val engine =
                    com.softistx.graphix.Graphix {
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

        feature("GET /sandbox") {
            scenario("is not served unless asked for") {
                testApplication {
                    application { install(GraphQL) { schema { query(GreetingQueries()) } } }

                    client.get("/sandbox").status shouldBe HttpStatusCode.NotFound
                }
            }

            scenario("sandbox = true serves the Apollo page as HTML") {
                testApplication {
                    application {
                        install(GraphQL) {
                            sandbox = true
                            schema { query(GreetingQueries()) }
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
                            schema { query(GreetingQueries()) }
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
                            schema { query(GreetingQueries()) }
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
                    val engine = com.softistx.graphix.Graphix { query(GreetingQueries()) }
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
