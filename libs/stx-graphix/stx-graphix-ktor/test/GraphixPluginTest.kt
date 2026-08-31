package com.strange.graphix.ktor

import com.strange.graphix.http.GRAPHQL_TRANSPORT_WS
import com.strange.graphix.http.SubscriptionProtocol
import com.strange.graphix.ktor.fixture.BoomQueries
import com.strange.graphix.ktor.fixture.GreetingQueries
import com.strange.graphix.ktor.fixture.TickSubscriptions
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

        feature("instance") {
            scenario("an engine built elsewhere is the one the route uses") {
                val engine =
                    com.strange.graphix.Graphix {
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
