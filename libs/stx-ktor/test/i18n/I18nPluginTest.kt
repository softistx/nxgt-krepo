package com.softistx.ktor.i18n

import com.softistx.i18n.Messages
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Locale

/**
 * The plugin, against a real server.
 *
 * The interesting behaviour is all at the edge — which locale a request resolves to, and what the
 * route can reach afterwards — so these go through the HTTP client rather than calling the
 * negotiation directly. `stx-i18n` already has specs for the negotiation itself.
 */
class I18nPluginTest :
    FeatureSpec({

        val messages =
            Messages.of(
                Locale.ENGLISH to mapOf("greeting" to "Hello {name}!", "title" to "Orders"),
                Locale.FRENCH to mapOf("greeting" to "Bonjour {name} !", "title" to "Commandes"),
                Locale.CANADA_FRENCH to mapOf("title" to "Commandes du Canada"),
            )

        /** An app with the plugin installed and one route that answers with a translation. */
        fun ApplicationTestBuilder.serve(
            key: String = "title",
            configure: I18nConfiguration.() -> Unit = {},
        ) {
            application {
                install(I18n) {
                    this.messages = messages
                    configure()
                }
                routing { get("/") { call.respondText(call.translate(key)) } }
            }
        }

        feature("the locale a request resolves to") {
            scenario("Accept-Language picks the catalog") {
                testApplication {
                    serve()
                    client.get("/") { header(HttpHeaders.AcceptLanguage, "fr") }.bodyAsText() shouldBe "Commandes"
                }
            }

            scenario("quality values are honoured, as on the header itself") {
                testApplication {
                    serve()
                    client
                        .get("/") { header(HttpHeaders.AcceptLanguage, "fr-CA,fr;q=0.9,en;q=0.8") }
                        .bodyAsText() shouldBe "Commandes du Canada"
                }
            }

            scenario("a request with no header at all gets the fallback") {
                testApplication {
                    serve()
                    client.get("/").bodyAsText() shouldBe "Orders"
                }
            }

            scenario("a key the negotiated catalog lacks still answers, from the fallback") {
                // fr-CA translates `title` and nothing else; `greeting` comes down the chain.
                testApplication {
                    serve(key = "greeting")
                    client
                        .get("/") { header(HttpHeaders.AcceptLanguage, "fr-CA") }
                        .bodyAsText() shouldBe "Bonjour {name} !"
                }
            }
        }

        feature("where the plugin reads the locale from") {
            scenario("a service behind something that rewrites the header can name its own") {
                testApplication {
                    serve { header = "X-Language" }
                    client.get("/") { header("X-Language", "fr") }.bodyAsText() shouldBe "Commandes"
                }
            }

            scenario("the query parameter is off unless it is asked for") {
                testApplication {
                    serve()
                    client.get("/?lang=fr").bodyAsText() shouldBe "Orders"
                }
            }

            scenario("turned on, it beats the header — it is the more deliberate of the two") {
                testApplication {
                    serve { queryParameter = "lang" }
                    client
                        .get("/?lang=fr") { header(HttpHeaders.AcceptLanguage, "en") }
                        .bodyAsText() shouldBe "Commandes"
                }
            }

            scenario("turned on and absent, the header still decides") {
                testApplication {
                    serve { queryParameter = "lang" }
                    client.get("/") { header(HttpHeaders.AcceptLanguage, "fr") }.bodyAsText() shouldBe "Commandes"
                }
            }
        }

        feature("what a route reaches") {
            scenario("the translator is resolved once and shared, not renegotiated per read") {
                testApplication {
                    application {
                        install(I18n) { this.messages = messages }
                        routing {
                            get("/") {
                                call.respondText("${call.translator === call.translator}")
                            }
                        }
                    }
                    client.get("/") { header(HttpHeaders.AcceptLanguage, "fr") }.bodyAsText() shouldBe "true"
                }
            }

            scenario("arguments reach the message, named and positional alike") {
                testApplication {
                    application {
                        install(I18n) { this.messages = messages }
                        routing {
                            get("/") { call.respondText(call.translate("greeting", mapOf("name" to "Ada"))) }
                        }
                    }
                    client
                        .get("/") { header(HttpHeaders.AcceptLanguage, "fr") }
                        .bodyAsText() shouldBe "Bonjour Ada !"
                }
            }
        }

        feature("installing it wrongly") {
            scenario("no messages is a failure to start, not a server that answers in keys") {
                shouldThrow<IllegalArgumentException> {
                    testApplication {
                        application { install(I18n) {} }
                        client.get("/")
                    }
                }
            }
        }
    })
