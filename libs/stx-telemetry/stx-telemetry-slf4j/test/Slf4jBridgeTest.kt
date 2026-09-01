package com.softistx.telemetry.slf4j

import com.softistx.telemetry.Telemetry
import com.softistx.telemetry.context.withAttributes
import com.softistx.telemetry.context.withTelemetry
import com.softistx.telemetry.model.Severity
import com.softistx.telemetry.slf4j.fixture.Collector
import com.softistx.telemetry.span
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.time.Duration.Companion.milliseconds

private fun collecting(collector: Collector) =
    Telemetry("spec") {
        minimum = Severity.Debug
        linger = 1.milliseconds
        stackTraces = false
        export(collector)
    }

/**
 * The inbound half: a third-party library logging through SLF4J lands in this pipeline.
 *
 * These specs are also the proof that the SPI wiring works at all — `LoggerFactory.getLogger` returns
 * one of ours only because `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` names the provider
 * and SLF4J found it. A file that resource-packaging quietly dropped would fail here rather than in
 * somebody's staging environment.
 */
class Slf4jBridgeTest :
    FeatureSpec({
        feature("SLF4J is bound to this library") {
            scenario("a logger from LoggerFactory is one of ours") {
                LoggerFactory.getLogger("anything").shouldBeInstanceOf<TelemetryLogger>()
            }

            scenario("the same name gives the same instance, as SLF4J requires") {
                LoggerFactory.getLogger("repeat") shouldBe LoggerFactory.getLogger("repeat")
            }
        }

        feature("a third-party log becomes a signal") {
            scenario("it keeps its name, its level and its message") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    LoggerFactory.getLogger("io.lettuce.core.RedisClient").warn("connection reset")
                }
                telemetry.close()

                val record = collector.log("connection reset").shouldNotBeNull()
                record.severity shouldBe Severity.Warn
                record.source shouldBe "io.lettuce.core.RedisClient"
            }

            scenario("placeholders are filled the way SLF4J fills them") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    LoggerFactory.getLogger("driver").info("connected to {} in {}ms", "mongo1", 42)
                }
                telemetry.close()

                collector.log("connected to mongo1 in 42ms").shouldNotBeNull()
            }

            scenario("a trailing throwable is the failure, not an argument") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    LoggerFactory
                        .getLogger("driver")
                        .error("lost {}", "connection", java.io.IOException("no route"))
                }
                telemetry.close()

                val record = collector.log("lost connection").shouldNotBeNull()
                record.error.shouldNotBeNull().type shouldBe "java.io.IOException"
                record.error!!.message shouldBe "no route"
            }

            scenario("trace and debug both become Debug, because there is no fifth level") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    LoggerFactory.getLogger("driver").trace("fine")
                    LoggerFactory.getLogger("driver").debug("coarse")
                }
                telemetry.close()

                collector.logs.map { it.severity } shouldBe listOf(Severity.Debug, Severity.Debug)
            }

            scenario("the severity floor turns the level check off, so the caller builds nothing") {
                val collector = Collector()
                val telemetry =
                    Telemetry("spec") {
                        minimum = Severity.Warn
                        linger = 1.milliseconds
                        export(collector)
                    }
                withTelemetry(telemetry) {
                    val logger = LoggerFactory.getLogger("driver")
                    logger.isDebugEnabled shouldBe false
                    logger.isWarnEnabled shouldBe true
                    logger.info("dropped")
                    logger.warn("kept")
                }
                telemetry.close()

                collector.logs.map { it.name } shouldBe listOf("kept")
            }
        }

        feature("what a bridged log inherits") {
            scenario("the span it was written inside") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    span("charge") {
                        // A driver called from inside a span has no idea there is one.
                        LoggerFactory.getLogger("driver").info("querying")
                    }
                }
                telemetry.close()

                val record = collector.log("querying").shouldNotBeNull()
                record.span.shouldNotBeNull().traceId shouldBe
                    collector.logs
                        .first()
                        .span!!
                        .traceId
            }

            scenario("the fields in scope") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    withAttributes("tenant" to "acme") {
                        LoggerFactory.getLogger("driver").info("querying")
                    }
                }
                telemetry.close()

                collector
                    .log("querying")
                    .shouldNotBeNull()
                    .attributes.values["tenant"] shouldBe JsonPrimitive("acme")
            }

            scenario("whatever the caller put in the MDC, which is the only context it has") {
                val collector = Collector()
                val telemetry = collecting(collector)
                try {
                    MDC.put("callId", "c-7")
                    withTelemetry(telemetry) { LoggerFactory.getLogger("driver").info("querying") }
                } finally {
                    MDC.clear()
                }
                telemetry.close()

                collector
                    .log("querying")
                    .shouldNotBeNull()
                    .attributes.values["callId"] shouldBe JsonPrimitive("c-7")
            }

            scenario("a marker becomes an attribute") {
                val collector = Collector()
                val telemetry = collecting(collector)
                withTelemetry(telemetry) {
                    LoggerFactory
                        .getLogger("driver")
                        .info(org.slf4j.MarkerFactory.getMarker("AUDIT"), "querying")
                }
                telemetry.close()

                collector
                    .log("querying")
                    .shouldNotBeNull()
                    .attributes.values["marker"] shouldBe JsonPrimitive("AUDIT")
            }
        }

        feature("with no telemetry installed") {
            scenario("a bridged log is dropped rather than throwing, because logging starts first") {
                Telemetry.installed.shouldBeNull()
                LoggerFactory.getLogger("early").error("before anybody configured anything")
            }
        }
    })
