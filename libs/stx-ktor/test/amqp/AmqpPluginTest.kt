package com.strange.ktor.amqp

import com.strange.amqp.Amqp
import com.strange.amqp.AmqpConfig
import com.strange.testing.containers.rabbitContainer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/** One connection for the application, and channels that belong to whoever opened them. */
class AmqpPluginTest :
    FeatureSpec({

        val broker = rabbitContainer()

        feature("a route reaching for the broker").config(enabled = broker.available) {
            scenario("gets an open connection it can take a channel from") {
                testApplication {
                    application {
                        install(AmqpConnection) { config = AmqpConfig(uri = broker.requireEndpoint(), connectionName = "spec") }
                        routing {
                            get("/") {
                                val queue = call.amqp.withChannel { it.queueDeclare().queue }
                                call.respondText("${call.amqp.isOpen}:${queue.isNotEmpty()}")
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "true:true"
                }
            }

            scenario("and the connection is closed when the application stops") {
                lateinit var captured: Amqp
                testApplication {
                    application {
                        install(AmqpConnection) { config = AmqpConfig(uri = broker.requireEndpoint()) }
                        routing {
                            get("/") {
                                captured = call.amqp
                                call.respondText("ok")
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "ok"
                }

                captured.isOpen shouldBe false
            }
        }

        feature("a connection handed in rather than opened").config(enabled = broker.available) {
            scenario("is the one routes get, and is still open after the application stops") {
                val mine = Amqp.connect(AmqpConfig(uri = broker.requireEndpoint(), connectionName = "adopted"))
                try {
                    lateinit var captured: Amqp
                    testApplication {
                        application {
                            install(AmqpConnection) { instance = mine }
                            routing {
                                get("/") {
                                    captured = call.amqp
                                    call.respondText("ok")
                                }
                            }
                        }
                        client.get("/").bodyAsText() shouldBe "ok"
                    }

                    captured shouldBeSameInstanceAs mine
                    mine.isOpen shouldBe true
                } finally {
                    mine.close()
                }
            }
        }

        feature("reaching for it without installing it") {
            scenario("names the plugin") {
                // Asserted on the accessor rather than through the client: Ktor's test engine turns
                // a handler exception into a 500 page, so going over HTTP would prove only that
                // something went wrong, not that the message says which install is missing.
                testApplication {
                    application {
                        val failure = shouldThrow<IllegalStateException> { amqp }

                        failure.message shouldContain "AmqpConnection"
                    }

                    startApplication()
                }
            }
        }
    })
