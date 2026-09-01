package com.strange.example.orders

import com.strange.example.orders.api.apis.IHealthService
import com.strange.example.orders.api.apis.IOrdersService
import com.strange.example.orders.api.models.ChangeStatusRequest
import com.strange.example.orders.api.models.OrderStatus
import com.strange.example.orders.api.models.PlaceOrderRequest
import com.strange.example.orders.api.utils.ErrorResponseException
import com.strange.spring.client.withClient
import com.strange.spring.testing.MongoSpec
import com.strange.spring.testing.awaitMigrations
import com.strange.spring.testing.clear
import com.strange.spring.testing.mongoAvailable
import com.strange.telemetry.Attributes
import com.strange.telemetry.model.Severity
import com.strange.telemetry.model.SpanKind
import com.strange.telemetry.model.SpanRecord
import com.strange.telemetry.model.SpanStatus
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import kotlin.time.Duration.Companion.seconds

/** What an attribute reads as once it has been through JSON, which is where every one of them has been. */
private fun Attributes.text(name: String): String? = (values[name] as? JsonPrimitive)?.content

/** The same, for the numbers — a status code arrives as a `JsonPrimitive`, not an `Int`. */
private fun Attributes.number(name: String): Int? = (values[name] as? JsonPrimitive)?.content?.toIntOrNull()

/**
 * The telemetry this application is configured with, asserted on the signals it emits.
 *
 * Everything here comes out of [TelemetryCollector] — an `Exporter` bean, which is the seam an
 * application with a destination of its own uses. The Mongo exporter the demo runs with is off under
 * the `test` profile, so what these scenarios prove is the half that is the same either way: what is
 * emitted, what it is named, and what is on it.
 *
 * **`eventually` here is not flake-proofing.** A batch lingers up to `stx.telemetry.linger` — a
 * second, by default — before it ships, because shipping one signal at a time is the thing an
 * exporter exists to avoid. Asserting straight after a request would be asserting on the timer.
 *
 * **Every scenario places an order under its own reference and matches on it.** The collector is one
 * bean in a context Spring caches for the whole module, so it sees what the other specs do too, and
 * a batch that lingered arrives after this spec has cleared it. `TelemetryCollector` argues that at
 * the finders.
 */
class TelemetryTest(
    template: ReactiveMongoTemplate,
    signals: TelemetryCollector,
    json: Json,
) : MongoSpec({

        val orders = apiFactory(json).withClient<IOrdersService>()

        /** The `place order` span for one reference — this spec's handle on its own request. */
        fun placing(reference: String) = { span: SpanRecord -> span.attributes.text("reference") == reference }

        beforeSpec { if (mongoAvailable) template.awaitMigrations(expected = 2) }
        beforeEach {
            if (mongoAvailable) template.clear("orders", "audits")
            signals.clear()
        }

        feature("every request is a server span").config(enabled = mongoAvailable) {
            scenario("named for the route pattern, not the path it arrived on") {
                val id = orders.placeOrder(PlaceOrderRequest(reference = "T-1", customer = "ada", total = 100)).data.id

                orders.findOrder(id)

                eventually(5.seconds) {
                    // `GET /orders/{id}`, not `GET /orders/68b2…`. The pattern is only known once
                    // routing has run, so the filter renames the span before writing it — the
                    // difference between a name a backend can group by and one per order ever placed.
                    val span = signals.span("GET /orders/{id}") { it.attributes.text("url.path") == "/orders/$id" }
                    span.shouldNotBeNull()
                    span.kind shouldBe SpanKind.Server
                    span.attributes.text("http.route") shouldBe "/orders/{id}"
                    span.attributes.number("http.response.status_code") shouldBe 200
                }
            }

            scenario("the service's own span nests inside it, on the same trace") {
                orders.placeOrder(PlaceOrderRequest(reference = "T-2", customer = "ada", total = 100))

                eventually(5.seconds) {
                    val inner = signals.span("place order", placing("T-2")).shouldNotBeNull()
                    val server = signals.spans.firstOrNull { it.context.spanId == inner.parent }.shouldNotBeNull()

                    // Nothing was passed down to make this true. The span context is a
                    // `CoroutineContext.Element` and `CoWebFilter` runs the handler inside its own
                    // coroutine, so a suspending service method is already inside the request's span.
                    server.name shouldBe "POST /orders"
                    inner.context.traceId shouldBe server.context.traceId
                }
            }

            scenario("a 4xx is not an error, because a caller being told no is the service working") {
                orders.placeOrder(PlaceOrderRequest(reference = "T-3", customer = "ada", total = 100))
                shouldThrow<ErrorResponseException> {
                    orders.placeOrder(PlaceOrderRequest(reference = "T-3", customer = "grace", total = 200))
                }

                eventually(5.seconds) {
                    // The refused attempt, found by the span that recorded the failure rather than by
                    // its status code — two `place order` spans carry T-3 and only one of them threw.
                    val refused = signals.span("place order") { placing("T-3")(it) && it.status == SpanStatus.Error }
                    refused.shouldNotBeNull()
                    val server = signals.spans.firstOrNull { it.context.spanId == refused.parent }.shouldNotBeNull()

                    server.attributes.number("http.response.status_code") shouldBe 409
                    // 5xx is this service failing; 4xx is a caller being told no. Colouring both red
                    // makes a dashboard useless, so only the inner span the exception passed through
                    // is an error.
                    server.status shouldBe SpanStatus.Ok
                }
            }

            scenario("/health gets none, because it is a trace nobody will read") {
                val health = apiFactory(json).withClient<IHealthService>()

                health.health().data.status shouldBe "UP"

                // `stx.telemetry.ignore` means the filter never opened one, so there is nothing to
                // wait for — and nothing any other spec here could have contributed either.
                eventually(2.seconds) { signals.spans.none { it.name.endsWith("/health") } shouldBe true }
            }
        }

        feature("what the service logs").config(enabled = mongoAvailable) {
            scenario("a typed event, named by its serial name rather than by its package") {
                orders.placeOrder(PlaceOrderRequest(reference = "T-4", customer = "ada", total = 4_200))

                eventually(5.seconds) {
                    val placed = signals.log("orders.placed") { it.attributes.text("reference") == "T-4" }
                    placed.shouldNotBeNull()
                    placed.severity shouldBe Severity.Info
                    placed.source shouldBe "com.strange.example.orders.service.OrderService"
                    placed.attributes.number("total") shouldBe 4_200
                }
            }

            scenario("the customer is nowhere in it, because OrderPlaced does not declare one") {
                orders.placeOrder(PlaceOrderRequest(reference = "T-5", customer = "ada", total = 100))

                eventually(5.seconds) {
                    val placed = signals.log("orders.placed") { it.attributes.text("reference") == "T-5" }
                    // The guard against logging a field nobody meant to log is that the event type has
                    // to name it. This is that guard, asserted rather than described.
                    placed
                        .shouldNotBeNull()
                        .attributes.values.keys
                        .contains("customer") shouldBe false
                }
            }

            scenario("a log carries the span it was written inside, with nothing passed to it") {
                orders.placeOrder(PlaceOrderRequest(reference = "T-6", customer = "ada", total = 100))

                eventually(5.seconds) {
                    val placed = signals.log("orders.placed") { it.attributes.text("reference") == "T-6" }
                    val inner = signals.span("place order", placing("T-6")).shouldNotBeNull()

                    // `reference` is on the log because it was given to `span(...)`, which makes it
                    // inherited; `orderId` is on the span alone, because `attribute(...)` on the
                    // receiver is not.
                    placed.shouldNotBeNull().span shouldBe inner.context
                    inner.attributes.text("orderId").shouldNotBeNull()
                    placed.attributes.values.keys
                        .contains("orderId") shouldBe false
                }
            }

            scenario("a transition names both ends, which is what makes it worth a record") {
                val id = orders.placeOrder(PlaceOrderRequest(reference = "T-7", customer = "ada", total = 100)).data.id

                orders.changeStatus(id, ChangeStatusRequest(status = OrderStatus.PAID))

                eventually(5.seconds) {
                    val changed = signals.log("orders.status-changed") { it.attributes.text("reference") == "T-7" }
                    changed.shouldNotBeNull()
                    changed.attributes.text("from") shouldBe "PENDING"
                    changed.attributes.text("to") shouldBe "PAID"
                }
            }

            scenario("a refused reference is a warning, not an error") {
                orders.placeOrder(PlaceOrderRequest(reference = "T-8", customer = "ada", total = 100))
                shouldThrow<ErrorResponseException> {
                    orders.placeOrder(PlaceOrderRequest(reference = "T-8", customer = "grace", total = 200))
                }

                eventually(5.seconds) {
                    val taken = signals.log("orders.reference-taken") { it.attributes.text("reference") == "T-8" }
                    taken.shouldNotBeNull().severity shouldBe Severity.Warn
                }
            }
        }

        feature("the resource every signal reports").config(enabled = mongoAvailable) {
            scenario("the service name falls back to spring.application.name") {
                orders.placeOrder(PlaceOrderRequest(reference = "T-9", customer = "ada", total = 100))

                eventually(5.seconds) {
                    val resource = signals.resource.shouldNotBeNull()
                    // Nothing sets `stx.telemetry.service`. It is the attribute every backend groups
                    // by, so it falls back to the application's own name and not to a placeholder.
                    resource.service shouldBe "spring-orders"
                    resource.environment shouldBe "development"
                }
            }
        }
    })
