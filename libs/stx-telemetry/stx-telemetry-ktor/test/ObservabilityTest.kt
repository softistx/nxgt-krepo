package com.softistx.telemetry.ktor

import com.softistx.telemetry.export.Exporter
import com.softistx.telemetry.logger
import com.softistx.telemetry.model.LogRecord
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Severity
import com.softistx.telemetry.model.Signal
import com.softistx.telemetry.model.SpanKind
import com.softistx.telemetry.model.SpanRecord
import com.softistx.telemetry.model.SpanStatus
import com.softistx.telemetry.span
import com.softistx.telemetry.trace.Sampler
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds

private class Collector : Exporter {
    private val signals = ConcurrentLinkedQueue<Signal>()

    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        signals += batch
    }

    val spans: List<SpanRecord> get() = signals.filterIsInstance<SpanRecord>()
    val logs: List<LogRecord> get() = signals.filterIsInstance<LogRecord>()

    fun span(name: String): SpanRecord? = spans.firstOrNull { it.name == name }
}

private fun Application.observed(
    collector: Collector,
    configure: ObservabilityConfiguration.() -> Unit = {},
) {
    install(Observability) {
        service = "spec"
        minimum = Severity.Debug
        linger = 1.milliseconds
        stackTraces = false
        export(collector)
        configure()
    }
}

private val log = logger("route")

class ObservabilityTest :
    FeatureSpec({
        feature("a request is a server span") {
            scenario("named after the matched route, not the path it arrived on") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector)
                        routing { get("/orders/{id}") { call.respondText("ok") } }
                    }
                    client.get("/orders/8d1f").bodyAsText() shouldBe "ok"
                }

                val span = collector.span("GET /orders/{id}").shouldNotBeNull()
                span.kind shouldBe SpanKind.Server
                span.status shouldBe SpanStatus.Ok
                span.attributes.values["http.route"] shouldBe JsonPrimitive("/orders/{id}")
                span.attributes.values["url.path"] shouldBe JsonPrimitive("/orders/8d1f")
                span.attributes.values["http.request.method"] shouldBe JsonPrimitive("GET")
                span.attributes.values["http.response.status_code"] shouldBe JsonPrimitive(200)
            }

            scenario("a request that matches nothing keeps the path, which is what a 404 hunt needs") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector)
                        routing { get("/orders") { call.respondText("ok") } }
                    }
                    client.get("/nowhere")
                }

                val span = collector.span("GET /nowhere").shouldNotBeNull()
                span.attributes.values["http.response.status_code"] shouldBe JsonPrimitive(404)
                // A 404 is the service working, so it is not an error.
                span.status shouldBe SpanStatus.Ok
            }

            scenario("everything the route does is inside the span") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector)
                        routing {
                            get("/orders") {
                                log.info("handling")
                                span("charge") { }
                                call.respondText("ok")
                            }
                        }
                    }
                    client.get("/orders")
                }

                val server = collector.span("GET /orders").shouldNotBeNull()
                val inner = collector.span("charge").shouldNotBeNull()
                inner.context.traceId shouldBe server.context.traceId
                inner.parent shouldBe server.context.spanId

                val line = collector.logs.first { it.name == "handling" }
                line.span.shouldNotBeNull().traceId shouldBe server.context.traceId
            }
        }

        feature("a trace that arrived over the wire") {
            scenario("the traceparent is continued rather than replaced") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector)
                        routing { get("/orders") { call.respondText("ok") } }
                    }
                    client.get("/orders") {
                        header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                    }
                }

                val span = collector.span("GET /orders").shouldNotBeNull()
                span.context.traceId.hex shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
                span.parent?.hex shouldBe "00f067aa0ba902b7"
            }

            scenario("a malformed one starts a fresh trace rather than failing the request") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector)
                        routing { get("/orders") { call.respondText("ok") } }
                    }
                    client.get("/orders") { header("traceparent", "nonsense") }.status shouldBe
                        HttpStatusCode.OK
                }

                collector
                    .span("GET /orders")
                    .shouldNotBeNull()
                    .context.traceId.isValid shouldBe true
            }
        }

        feature("failures") {
            scenario("an unhandled one propagates and the span says Error") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector)
                        routing { get("/orders") { error("gateway down") } }
                    }
                    client.get("/orders").status shouldBe HttpStatusCode.InternalServerError
                }

                collector.span("GET /orders").shouldNotBeNull().status shouldBe SpanStatus.Error
            }

            scenario("one a StatusPages handler turns into a response is recorded before the handler runs") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector)
                        install(StatusPages) {
                            exception<IllegalStateException> { call, _ ->
                                call.respondText("handled", status = HttpStatusCode.BadGateway)
                            }
                        }
                        routing { get("/orders") { error("gateway down") } }
                    }
                    client.get("/orders").bodyAsText() shouldBe "handled"
                }

                val span = collector.span("GET /orders").shouldNotBeNull()
                span.status shouldBe SpanStatus.Error
                span.error.shouldNotBeNull().message shouldBe "gateway down"
                // No status code: the span is written while the exception is unwinding, and the
                // handler produces the response further out. The failure is the part worth having.
                span.attributes.values["http.response.status_code"].shouldBeNull()
            }

            scenario("a 4xx is the service working, a 5xx is not") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector)
                        routing {
                            get("/refused") { call.respondText("no", status = HttpStatusCode.Forbidden) }
                            get("/broken") { call.respondText("no", status = HttpStatusCode.BadGateway) }
                        }
                    }
                    client.get("/refused")
                    client.get("/broken")
                }

                collector.span("GET /refused").shouldNotBeNull().status shouldBe SpanStatus.Ok
                collector.span("GET /broken").shouldNotBeNull().status shouldBe SpanStatus.Error
            }
        }

        feature("what the plugin puts on the application") {
            scenario("the telemetry, from the application and from a call") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector)
                        routing {
                            get("/orders") {
                                call.telemetry shouldBe call.application.telemetry
                                call.span.shouldNotBeNull().sampled shouldBe true
                                call.traceparent.shouldNotBeNull() shouldNotBe ""
                                call.respondText("ok")
                            }
                        }
                    }
                    client.get("/orders")
                }
            }

            scenario("a telemetry built elsewhere is used and not closed") {
                val collector = Collector()
                val mine =
                    com.softistx.telemetry.Telemetry("mine") {
                        linger = 1.milliseconds
                        export(collector)
                    }
                testApplication {
                    application {
                        install(Observability) { instance = mine }
                        routing { get("/orders") { call.respondText("ok") } }
                    }
                    client.get("/orders")
                }
                // The application stopped; ours is still open, so this still ships.
                mine.close()

                collector
                    .span("GET /orders")
                    .shouldNotBeNull()
                    .attributes.values["url.path"] shouldBe
                    JsonPrimitive("/orders")
            }
        }

        feature("choosing what is traced") {
            scenario("a request the filter refuses gets no span and still works") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector) { traced = { it.request.local.uri != "/health" } }
                        routing {
                            get("/health") { call.respondText("up") }
                            get("/orders") { call.respondText("ok") }
                        }
                    }
                    client.get("/health").bodyAsText() shouldBe "up"
                    client.get("/orders")
                }

                collector.spans.map { it.name } shouldBe listOf("GET /orders")
            }

            scenario("a sampler that keeps nothing still runs every request") {
                val collector = Collector()
                testApplication {
                    application {
                        observed(collector) { sampler = Sampler.never }
                        routing { get("/orders") { call.respondText("ok") } }
                    }
                    client.get("/orders").bodyAsText() shouldBe "ok"
                }

                collector.spans shouldBe emptyList()
                collector.span("GET /orders").shouldBeNull()
            }
        }
    })
