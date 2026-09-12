package com.softistx.telemetry.trace

import com.softistx.telemetry.context.currentTraceparent
import com.softistx.telemetry.context.withTelemetry
import com.softistx.telemetry.continuing
import com.softistx.telemetry.fixture.Collector
import com.softistx.telemetry.fixture.collecting
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TraceTest :
    FeatureSpec({
        feature("W3C traceparent") {
            scenario("it makes the round trip") {
                val context = SpanContext(TraceId.random(), SpanId.random(), sampled = true)
                val back = SpanContext.traceparent(context.traceparent()).shouldNotBeNull()

                back.traceId shouldBe context.traceId
                back.spanId shouldBe context.spanId
                back.sampled shouldBe true
                back.remote shouldBe true
            }

            scenario("the sampled bit survives being false") {
                val context = SpanContext(TraceId.random(), SpanId.random(), sampled = false)
                context.traceparent().endsWith("-00") shouldBe true
                SpanContext.traceparent(context.traceparent())!!.sampled shouldBe false
            }

            scenario("a header from the specification's own example reads back") {
                val header = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
                val parsed = SpanContext.traceparent(header).shouldNotBeNull()

                parsed.traceId.hex shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
                parsed.spanId.hex shouldBe "00f067aa0ba902b7"
                parsed.traceparent() shouldBe header
            }

            scenario("a future version is read rather than refused") {
                val parsed =
                    SpanContext.traceparent(
                        "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-something-new",
                    )
                parsed.shouldNotBeNull().traceId.hex shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
            }

            scenario("anything unusable is null, not an exception") {
                SpanContext.traceparent(null).shouldBeNull()
                SpanContext.traceparent("").shouldBeNull()
                SpanContext.traceparent("garbage").shouldBeNull()
                SpanContext.traceparent("ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").shouldBeNull()
                SpanContext.traceparent("00-00000000000000000000000000000000-00f067aa0ba902b7-01").shouldBeNull()
                SpanContext.traceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01").shouldBeNull()
                SpanContext.traceparent("00-4bf-00f067aa0ba902b7-01").shouldBeNull()
            }
        }

        feature("continuing a trace that arrived") {
            scenario("the incoming trace id is kept and the incoming span becomes the parent") {
                val collector = Collector()
                val telemetry = collecting(collector)
                val header = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
                withTelemetry(telemetry) {
                    continuing(header, "GET /orders") {}
                }
                telemetry.close()

                val span = collector.span("GET /orders").shouldNotBeNull()
                span.context.traceId.hex shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
                span.parent?.hex shouldBe "00f067aa0ba902b7"
                span.context.spanId.hex shouldNotBe "00f067aa0ba902b7"
            }

            scenario("a missing header starts a fresh trace rather than failing the request") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) { continuing(null, "GET /orders") {} }
                telemetry.close()

                val span = collector.span("GET /orders").shouldNotBeNull()
                span.context.traceId.isValid shouldBe true
                span.parent.shouldBeNull()
            }

            scenario("the header to send onwards is the current span's, not the one that arrived") {
                val telemetry = collecting(Collector())
                telemetry.use {
                    withTelemetry(telemetry) {
                        continuing("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", "GET /orders") {
                            val outgoing = currentTraceparent().shouldNotBeNull()
                            outgoing.startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-") shouldBe true
                            outgoing shouldNotBe "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
                        }
                    }
                }
            }
        }

        feature("the sampler") {
            scenario("always and never mean what they say") {
                val id = TraceId.random()
                Sampler.always.sample(id) shouldBe true
                Sampler.never.sample(id) shouldBe false
            }

            scenario("a ratio keeps roughly that share, and the same ids every time") {
                val ids = List(10_000) { TraceId.random() }
                val kept = ids.count { Sampler.ratio(0.25).sample(it) }
                (kept in 2_000..3_000) shouldBe true

                val sampler = Sampler.ratio(0.25)
                ids.all { sampler.sample(it) == sampler.sample(it) } shouldBe true
            }

            scenario("the edges collapse to always and never") {
                Sampler.ratio(1.0) shouldBe Sampler.always
                Sampler.ratio(0.0) shouldBe Sampler.never
            }
        }

        feature("ids") {
            scenario("a fresh one is valid and hexadecimal") {
                val trace = TraceId.random()
                trace.isValid shouldBe true
                trace.hex.length shouldBe 32
                trace.hex.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true

                val span = SpanId.random()
                span.isValid shouldBe true
                span.hex.length shouldBe 16
            }

            scenario("all zeroes is the invalid one, which is what the specification says") {
                TraceId.INVALID.isValid shouldBe false
                SpanId.INVALID.isValid shouldBe false
            }
        }
    })
