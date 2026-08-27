package com.strange.ktor.kafka

import com.strange.kafka.KafkaConfig
import com.strange.kafka.admin.admin
import com.strange.testing.containers.kafkaContainer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/**
 * The plugin that opens nothing.
 *
 * So what is worth testing is what it does claim: the cluster a route reaches is the one that was
 * configured, it is the same one every time, and an admin client built from it actually talks to a
 * broker — which is the only way to know the bootstrap survived the trip.
 */
class KafkaPluginTest :
    FeatureSpec({

        val cluster = kafkaContainer()

        feature("a route reaching for the cluster") {
            scenario("gets the configuration it was installed with, and the same instance each time") {
                testApplication {
                    application {
                        install(KafkaPlugin) { config = KafkaConfig(bootstrap = "example:9092", clientId = "spec") }
                        routing {
                            get("/") {
                                call.respondText("${call.kafka.bootstrap}:${System.identityHashCode(call.kafka)}")
                            }
                        }
                    }
                    val first = client.get("/").bodyAsText()

                    first.substringBefore(':') shouldBe "example"
                    client.get("/").bodyAsText() shouldBe first
                }
            }
        }

        feature("a cluster that is really there").config(enabled = cluster.available) {
            scenario("an admin client built from it reaches a broker") {
                testApplication {
                    application {
                        install(KafkaPlugin) { config = KafkaConfig(bootstrap = cluster.endpoint!!) }
                        routing {
                            get("/") {
                                // The caller owns what it opens: this admin client is closed here,
                                // by the route that opened it, and not by the plugin. Asking the
                                // broker for its topics is what proves the bootstrap survived the
                                // trip — the set may well be empty, and that is still an answer.
                                val answered = call.kafka.admin().use { runCatching { it.topics() }.isSuccess }

                                call.respondText("$answered")
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "true"
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
                        val failure = shouldThrow<IllegalStateException> { kafka }

                        failure.message shouldContain "KafkaPlugin"
                    }

                    startApplication()
                }
            }
        }
    })
