package com.softistx.telemetry.spring

import com.softistx.telemetry.model.SpanKind
import com.softistx.telemetry.model.SpanStatus
import com.softistx.telemetry.spring.fixture.Collector
import com.softistx.telemetry.spring.fixture.Orders
import com.softistx.telemetry.spring.fixture.collecting
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The claim that decides whether this integration is worth having.
 *
 * A `WebFilter` returns a `Mono`, so anything it puts in scope lives in the Reactor context — which
 * a **suspending** `@RestController` method does not read. `CoWebFilter` is Spring's own answer:
 * it runs the chain inside a coroutine and carries that coroutine's context on to the suspending
 * handler. The first scenario is that claim under test, against a real WebFlux dispatch, and it is
 * the reason the filter is a `CoWebFilter` and not the obvious thing.
 */
class TelemetryWebFilterTest :
    FeatureSpec({
        fun client(
            orders: Orders,
            filter: TelemetryWebFilter,
        ): WebTestClient =
            WebTestClient
                .bindToController(orders)
                .apply { webFilter<WebTestClient.ControllerSpec>(filter) }
                .build()

        feature("the span reaches the handler") {
            scenario("a suspending controller method sees the request's span") {
                val collector = Collector()
                val telemetry = collecting(collector)
                val orders = Orders()

                client(orders, TelemetryWebFilter(telemetry))
                    .get()
                    .uri("/orders/8d1f")
                    .exchange()
                    .expectStatus()
                    .isOk
                telemetry.close()

                val span = collector.span("GET /orders/{id}").shouldNotBeNull()
                orders.seen.shouldNotBeNull().spanId shouldBe span.context.spanId
            }

            scenario("a log written by the handler carries the same trace") {
                val collector = Collector()
                val telemetry = collecting(collector)

                client(Orders(), TelemetryWebFilter(telemetry))
                    .get()
                    .uri("/orders/8d1f")
                    .exchange()
                    .expectStatus()
                    .isOk
                telemetry.close()

                val span = collector.span("GET /orders/{id}").shouldNotBeNull()
                val line = collector.log("fetched").shouldNotBeNull()
                line.span.shouldNotBeNull().traceId shouldBe span.context.traceId
                line.attributes.values["orderId"] shouldBe JsonPrimitive("8d1f")
            }
        }

        feature("what the span records") {
            scenario("the method, the path, the matched route and the status") {
                val collector = Collector()
                val telemetry = collecting(collector)

                client(Orders(), TelemetryWebFilter(telemetry))
                    .get()
                    .uri("/orders/8d1f")
                    .exchange()
                    .expectStatus()
                    .isOk
                telemetry.close()

                val span = collector.span("GET /orders/{id}").shouldNotBeNull()
                span.kind shouldBe SpanKind.Server
                span.status shouldBe SpanStatus.Ok
                span.attributes.values["http.request.method"] shouldBe JsonPrimitive("GET")
                span.attributes.values["url.path"] shouldBe JsonPrimitive("/orders/8d1f")
                span.attributes.values["http.route"] shouldBe JsonPrimitive("/orders/{id}")
                span.attributes.values["http.response.status_code"] shouldBe JsonPrimitive(200)
            }

            scenario("an incoming traceparent is continued") {
                val collector = Collector()
                val telemetry = collecting(collector)

                client(Orders(), TelemetryWebFilter(telemetry))
                    .get()
                    .uri("/orders/8d1f")
                    .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                    .exchange()
                    .expectStatus()
                    .isOk
                telemetry.close()

                val span = collector.span("GET /orders/{id}").shouldNotBeNull()
                span.context.traceId.hex shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
                span.parent?.hex shouldBe "00f067aa0ba902b7"
            }

            scenario("a handler that throws is an error on the span") {
                val collector = Collector()
                val telemetry = collecting(collector)

                client(Orders(), TelemetryWebFilter(telemetry))
                    .get()
                    .uri("/boom")
                    .exchange()
                telemetry.close()

                val span = collector.spans.single()
                span.status shouldBe SpanStatus.Error
                span.error.shouldNotBeNull().message shouldBe "gateway down"
            }
        }

        feature("what is left alone") {
            scenario("an ignored path gets no span and is still served") {
                val collector = Collector()
                val telemetry = collecting(collector)

                client(Orders(), TelemetryWebFilter(telemetry, ignore = listOf("/health")))
                    .get()
                    .uri("/health")
                    .exchange()
                    .expectStatus()
                    .isOk
                telemetry.close()

                collector.spans shouldBe emptyList()
                collector.span("GET /health").shouldBeNull()
            }
        }
    })
