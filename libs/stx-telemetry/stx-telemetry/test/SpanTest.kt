package com.strange.telemetry

import com.strange.telemetry.context.withTelemetry
import com.strange.telemetry.fixture.Collector
import com.strange.telemetry.fixture.collecting
import com.strange.telemetry.model.SpanKind
import com.strange.telemetry.model.SpanStatus
import com.strange.telemetry.trace.Sampler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

@Serializable
private data class Refused(
    val code: String,
    val attempt: Int,
)

class SpanTest :
    FeatureSpec({
        feature("what a span records") {
            scenario("a child keeps the trace and takes a new span id") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    span("outer") { span("inner") {} }
                }
                telemetry.close()

                val outer = collector.span("outer").shouldNotBeNull()
                val inner = collector.span("inner").shouldNotBeNull()
                inner.context.traceId shouldBe outer.context.traceId
                inner.context.spanId shouldNotBe outer.context.spanId
                inner.parent shouldBe outer.context.spanId
                outer.parent.shouldBeNull()
            }

            scenario("a root gets a fresh trace each time") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    span("first") {}
                    span("second") {}
                }
                telemetry.close()

                collector.span("first")!!.context.traceId shouldNotBe collector.span("second")!!.context.traceId
            }

            scenario("it carries its kind, its attributes and how long it took") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    span("GET /orders", "route" to "/orders", kind = SpanKind.Server) {
                        attribute("status", 200)
                        delay(10)
                    }
                }
                telemetry.close()

                val span = collector.span("GET /orders").shouldNotBeNull()
                span.kind shouldBe SpanKind.Server
                span.status shouldBe SpanStatus.Ok
                span.attributes.values["route"] shouldBe JsonPrimitive("/orders")
                span.attributes.values["status"] shouldBe JsonPrimitive(200)
                (span.endedAt >= span.startedAt + 10.milliseconds) shouldBe true
            }

            scenario("the block can rename it once it knows what it is") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    span("GET /orders/8d1f") { name = "GET /orders/{id}" }
                }
                telemetry.close()

                collector.span("GET /orders/{id}").shouldNotBeNull()
                collector.span("GET /orders/8d1f").shouldBeNull()
            }

            scenario("events are moments inside it, and a typed one is named by its type") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    span("charge") {
                        event("retrying", "attempt" to 2)
                        event(Refused("insufficient_funds", 3))
                    }
                }
                telemetry.close()

                val events = collector.span("charge").shouldNotBeNull().events
                events.map { it.name } shouldBe
                    listOf("retrying", "com.strange.telemetry.Refused")
                events[0].attributes.values["attempt"] shouldBe JsonPrimitive(2)
                events[1].attributes.values["code"] shouldBe JsonPrimitive("insufficient_funds")
            }
        }

        feature("what a span does with a failure") {
            scenario("the exception propagates and the span says Error") {
                val collector = Collector()
                val telemetry = collecting(collector)
                shouldThrow<IllegalStateException> {
                    withTelemetry(telemetry) {
                        span("charge") { error("gateway down") }
                    }
                }
                telemetry.close()

                val span = collector.span("charge").shouldNotBeNull()
                span.status shouldBe SpanStatus.Error
                val failure = span.error.shouldNotBeNull()
                failure.type shouldBe "java.lang.IllegalStateException"
                failure.message shouldBe "gateway down"
            }

            scenario("a cancellation is Cancelled, not Error") {
                val collector = Collector()
                val telemetry = collecting(collector)
                shouldThrow<TimeoutCancellationException> {
                    withTelemetry(telemetry) {
                        withTimeout(20.milliseconds) {
                            span("slow") { delay(10_000) }
                        }
                    }
                }
                telemetry.close()

                collector.span("slow").shouldNotBeNull().status shouldBe SpanStatus.Cancelled
            }

            scenario("a status set by hand is kept when nothing threw") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    span("charge") { status = SpanStatus.Error }
                }
                telemetry.close()

                val span = collector.span("charge").shouldNotBeNull()
                span.status shouldBe SpanStatus.Error
                span.error.shouldBeNull()
            }

            scenario("a thrown failure overrides a status set by hand") {
                val collector = Collector()
                val telemetry = collecting(collector)
                shouldThrow<CancellationException> {
                    withTelemetry(telemetry) {
                        span("charge") {
                            status = SpanStatus.Ok
                            throw CancellationException("stopped")
                        }
                    }
                }
                telemetry.close()

                collector.span("charge").shouldNotBeNull().status shouldBe SpanStatus.Cancelled
            }
        }

        feature("sampling") {
            scenario("a trace that is not kept emits no spans at all") {
                val collector = Collector()
                val telemetry = collecting(collector, sampler = Sampler.never)
                withTelemetry(telemetry) { span("outer") { span("inner") {} } }
                telemetry.close()

                collector.spans shouldBe emptyList()
            }

            scenario("the decision is the root's, and every span under it agrees") {
                val collector = Collector()
                var asked = 0
                val once =
                    Sampler {
                        asked++
                        true
                    }
                val telemetry = collecting(collector, sampler = once)
                withTelemetry(telemetry) {
                    span("outer") { span("middle") { span("inner") {} } }
                }
                telemetry.close()

                asked shouldBe 1
                collector.spans.map { it.context.sampled } shouldBe listOf(true, true, true)
            }
        }

        feature("with nothing installed") {
            scenario("the block still runs, so a library may open spans in an application that has none") {
                var ran = false
                span("orphan") { ran = true }
                ran shouldBe true
            }
        }
    })
