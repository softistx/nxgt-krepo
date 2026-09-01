package com.strange.telemetry.slf4j

import com.strange.telemetry.attributesOf
import com.strange.telemetry.model.ErrorInfo
import com.strange.telemetry.model.LogRecord
import com.strange.telemetry.model.Resource
import com.strange.telemetry.model.Severity
import com.strange.telemetry.model.SpanKind
import com.strange.telemetry.model.SpanRecord
import com.strange.telemetry.model.SpanStatus
import com.strange.telemetry.slf4j.fixture.RecordingLoggerFactory
import com.strange.telemetry.trace.SpanContext
import com.strange.telemetry.trace.SpanId
import com.strange.telemetry.trace.TraceId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private val AT = Instant.parse("2026-09-01T10:00:00Z")
private val CONTEXT =
    SpanContext(
        TraceId("4bf92f3577b34da6a3ce929d0e0e4736"),
        SpanId("00f067aa0ba902b7"),
        sampled = true,
    )
private val RESOURCE = Resource("checkout")

class Slf4jExporterTest :
    FeatureSpec({
        feature("a log becomes a line") {
            scenario("the source is the logger's name and the severity is the level") {
                val bound = RecordingLoggerFactory()
                Slf4jExporter(factory = bound).export(
                    RESOURCE,
                    listOf(
                        LogRecord(AT, Severity.Debug, "fine", "com.strange.Checkout"),
                        LogRecord(AT, Severity.Info, "ordinary", "com.strange.Checkout"),
                        LogRecord(AT, Severity.Warn, "odd", "com.strange.Checkout"),
                        LogRecord(AT, Severity.Error, "bad", "com.strange.Outbox"),
                    ),
                )

                bound.at("com.strange.Checkout").map { it.level } shouldBe
                    listOf(Level.DEBUG, Level.INFO, Level.WARN)
                bound.at("com.strange.Outbox").single().level shouldBe Level.ERROR
                bound.lines.map { it.message } shouldBe listOf("fine", "ordinary", "odd", "bad")
            }

            scenario("the ids and the attributes are in the MDC, where a logback pattern reads them") {
                val bound = RecordingLoggerFactory()
                Slf4jExporter(factory = bound).export(
                    RESOURCE,
                    listOf(
                        LogRecord(
                            at = AT,
                            severity = Severity.Info,
                            name = "charging",
                            source = "com.strange.Checkout",
                            attributes = attributesOf("orderId" to "A-91", "amount" to 4999),
                            span = CONTEXT,
                        ),
                    ),
                )

                val line = bound.lines.single()
                line.mdc["traceId"] shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
                line.mdc["spanId"] shouldBe "00f067aa0ba902b7"
                line.mdc["orderId"] shouldBe "A-91"
                // A number reaches an appender as its text, not as a quoted JSON scalar.
                line.mdc["amount"] shouldBe "4999"
            }

            scenario("the MDC is empty again afterwards, so the next batch inherits nothing") {
                val bound = RecordingLoggerFactory()
                Slf4jExporter(factory = bound).export(
                    RESOURCE,
                    listOf(
                        LogRecord(AT, Severity.Info, "first", "a", attributesOf("orderId" to "A-91"), CONTEXT),
                        LogRecord(AT, Severity.Info, "second", "a"),
                    ),
                )

                bound.lines.first().mdc["orderId"] shouldBe "A-91"
                bound.lines.last().mdc shouldBe emptyMap()
                MDC.getCopyOfContextMap().isNullOrEmpty() shouldBe true
            }

            scenario("a failure reaches the appender with the recorded type, message and trace") {
                val bound = RecordingLoggerFactory()
                Slf4jExporter(factory = bound).export(
                    RESOURCE,
                    listOf(
                        LogRecord(
                            at = AT,
                            severity = Severity.Error,
                            name = "charge failed",
                            source = "com.strange.Checkout",
                            error = ErrorInfo("java.io.IOException", "no route", "at com.strange.Checkout"),
                        ),
                    ),
                )

                val failure =
                    bound.lines
                        .single()
                        .failure
                        .shouldNotBeNull()
                failure.toString() shouldContain "java.io.IOException"
                failure.toString() shouldContain "no route"
            }
        }

        feature("a span becomes a line") {
            scenario("its name, how long it took, and its outcome") {
                val bound = RecordingLoggerFactory()
                Slf4jExporter(factory = bound).export(RESOURCE, listOf(span()))

                val line = bound.at("com.strange.telemetry.span").single()
                line.level shouldBe Level.INFO
                line.message shouldContain "charge"
                line.message shouldContain "500ms"
                line.mdc["traceId"] shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
            }

            scenario("a failed one is an error whatever the configured level") {
                val bound = RecordingLoggerFactory()
                Slf4jExporter(spanSeverity = Severity.Debug, factory = bound)
                    .export(RESOURCE, listOf(span(status = SpanStatus.Error)))

                bound.lines.single().level shouldBe Level.ERROR
            }

            scenario("they can be left out entirely") {
                val bound = RecordingLoggerFactory()
                Slf4jExporter(spans = false, factory = bound).export(
                    RESOURCE,
                    listOf(span(), LogRecord(AT, Severity.Info, "kept", "a")),
                )

                bound.lines.map { it.message } shouldBe listOf("kept")
            }
        }

        feature("the two directions together") {
            scenario("exporting to SLF4J while SLF4J is bound to this library is refused, not a loop") {
                // The default factory is the bound one, and in this module's own test runtime that is
                // TelemetryLoggerFactory — so this is the real configuration, not a contrived one.
                LoggerFactory.getILoggerFactory().shouldBeInstanceOf<TelemetryLoggerFactory>()
                val failure = shouldThrow<IllegalStateException> { Slf4jExporter() }
                failure.message.shouldNotBeNull() shouldContain "back into itself"
            }
        }
    })

private fun span(status: SpanStatus = SpanStatus.Ok) =
    SpanRecord(
        name = "charge",
        context = CONTEXT,
        kind = SpanKind.Client,
        startedAt = AT,
        endedAt = AT + 500.milliseconds,
        status = status,
    )
