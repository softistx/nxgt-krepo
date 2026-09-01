package com.strange.telemetry

import com.strange.telemetry.context.withAttributes
import com.strange.telemetry.context.withTelemetry
import com.strange.telemetry.fixture.Collector
import com.strange.telemetry.fixture.collecting
import com.strange.telemetry.model.Severity
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
@SerialName("checkout.charged")
private data class Charged(
    val orderId: String,
    val amount: Long,
)

private class CheckoutService

class LoggerTest :
    FeatureSpec({
        val log = logger<CheckoutService>()

        feature("what a log carries") {
            scenario("written inside a span, it carries the trace and the span") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    span("charge") { log.info("charging") }
                }
                telemetry.close()

                val record = collector.log("charging").shouldNotBeNull()
                val span = collector.span("charge").shouldNotBeNull()
                val on = record.span.shouldNotBeNull()
                on.traceId shouldBe span.context.traceId
                on.spanId shouldBe span.context.spanId
                record.source shouldBe "com.strange.telemetry.CheckoutService"
            }

            scenario("written outside one, it has no span and is still emitted") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) { log.info("starting") }
                telemetry.close()

                collector
                    .log("starting")
                    .shouldNotBeNull()
                    .span
                    .shouldBeNull()
            }

            scenario("a typed event is named by its serial name and its fields are the attributes") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) { log.info(Charged("A-91", 4999)) }
                telemetry.close()

                val record = collector.log("checkout.charged").shouldNotBeNull()
                record.attributes.values["orderId"] shouldBe JsonPrimitive("A-91")
                record.attributes.values["amount"] shouldBe JsonPrimitive(4999)
            }

            scenario("it inherits the fields in scope") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    withAttributes("tenant" to "acme") {
                        log.warn("slow", "millis" to 1200)
                    }
                }
                telemetry.close()

                val record = collector.log("slow").shouldNotBeNull()
                record.severity shouldBe Severity.Warn
                record.attributes.values["tenant"] shouldBe JsonPrimitive("acme")
                record.attributes.values["millis"] shouldBe JsonPrimitive(1200)
            }

            scenario("a failure becomes its type and message") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    log.error("charge failed", IllegalArgumentException("no card"))
                }
                telemetry.close()

                val error =
                    collector
                        .log("charge failed")
                        .shouldNotBeNull()
                        .error
                        .shouldNotBeNull()
                error.type shouldBe "java.lang.IllegalArgumentException"
                error.message shouldBe "no card"
                // The fixture asks for none; a stack trace is a deployment's choice, not a default here.
                error.stackTrace.shouldBeNull()
            }
        }

        feature("the severity floor") {
            scenario("below it, nothing is emitted") {
                val collector = Collector()
                val telemetry = collecting(collector, minimum = Severity.Warn)
                withTelemetry(telemetry) {
                    log.debug("noise")
                    log.info("noise")
                    log.warn("kept")
                }
                telemetry.close()

                collector.logs.map { it.name } shouldBe listOf("kept")
            }

            scenario("the lazy form is not called below the floor") {
                val collector = Collector()
                val telemetry = collecting(collector, minimum = Severity.Info)
                var built = 0
                withTelemetry(telemetry) {
                    log.debug {
                        built++
                        "expensive"
                    }
                    log.info {
                        built++
                        "cheap"
                    }
                }
                telemetry.close()

                built shouldBe 1
                collector.logs.map { it.name } shouldBe listOf("cheap")
            }
        }

        feature("with nothing installed") {
            scenario("a log is a silent no-op rather than a failure") {
                logger("orphan").error("nobody is listening", IllegalStateException("boom"))
            }
        }
    })
