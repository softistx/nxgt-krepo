package com.softistx.telemetry.otlp

import com.softistx.telemetry.Attributes
import com.softistx.telemetry.attributesOf
import com.softistx.telemetry.model.ErrorInfo
import com.softistx.telemetry.model.LogRecord
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Severity
import com.softistx.telemetry.model.Signal
import com.softistx.telemetry.model.SpanEvent
import com.softistx.telemetry.model.SpanKind
import com.softistx.telemetry.model.SpanRecord
import com.softistx.telemetry.model.SpanStatus
import com.softistx.telemetry.otlp.fixture.Answer
import com.softistx.telemetry.otlp.fixture.FakeCollector
import com.softistx.telemetry.trace.SpanContext
import com.softistx.telemetry.trace.SpanId
import com.softistx.telemetry.trace.TraceId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private val AT = Instant.parse("2026-09-01T10:00:00Z")
private val CONTEXT =
    SpanContext(
        TraceId("4bf92f3577b34da6a3ce929d0e0e4736"),
        SpanId("00f067aa0ba902b7"),
        sampled = true,
    )

private val RESOURCE = Resource("checkout", "1.4.0", "production", attributesOf("region" to "eu-west-1"))

private fun log(
    name: String = "charging",
    source: String = "com.softistx.CheckoutService",
    attributes: Attributes = Attributes.EMPTY,
    error: ErrorInfo? = null,
) = LogRecord(AT, Severity.Warn, name, source, attributes, CONTEXT, error)

private fun span(
    name: String = "charge",
    status: SpanStatus = SpanStatus.Ok,
    events: List<SpanEvent> = emptyList(),
    error: ErrorInfo? = null,
) = SpanRecord(
    name = name,
    context = CONTEXT,
    parent = SpanId("aaaaaaaaaaaaaaaa"),
    kind = SpanKind.Client,
    startedAt = AT,
    endedAt = AT + 500.milliseconds,
    status = status,
    attributes = attributesOf("orderId" to "A-91", "amount" to 4999, "ok" to true, "ratio" to 0.25),
    events = events,
    error = error,
)

private fun body(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

class OtlpExporterTest :
    FeatureSpec({
        feature("where a batch goes") {
            scenario("logs and spans are two requests on two paths") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use {
                        it.export(RESOURCE, listOf<Signal>(log(), span()))
                    }

                    collector.requests.map { it.path } shouldBe listOf("/v1/logs", "/v1/traces")
                }
            }

            scenario("a batch of only logs makes only the one request") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use { it.export(RESOURCE, listOf(log())) }

                    collector.requests.map { it.path } shouldBe listOf("/v1/logs")
                }
            }

            scenario("the body is gzipped and says so") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use { it.export(RESOURCE, listOf(log())) }

                    // The fixture decompressed it, which is the assertion: a body that was not
                    // gzipped would have failed to inflate.
                    val request = collector.on("/v1/logs").shouldNotBeNull()
                    request.headers["Content-encoding"] shouldBe "gzip"
                    request.headers["Content-type"] shouldBe "application/json"
                    request.body.startsWith("{\"resourceLogs\"") shouldBe true
                }
            }

            scenario("gzip can be turned off, and the headers say that too") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint, gzip = false).use { it.export(RESOURCE, listOf(log())) }

                    collector
                        .on("/v1/logs")
                        .shouldNotBeNull()
                        .headers["Content-encoding"]
                        .shouldBeNull()
                }
            }

            scenario("configured headers are sent") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint, headers = mapOf("X-Tenant" to "acme")).use {
                        it.export(RESOURCE, listOf(log()))
                    }

                    collector.on("/v1/logs").shouldNotBeNull().headers["X-tenant"] shouldBe "acme"
                }
            }
        }

        feature("the log document") {
            scenario("the resource is the semantic-convention names") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use { it.export(RESOURCE, listOf(log())) }

                    val attributes =
                        body(collector.on("/v1/logs")!!.body)["resourceLogs"]!!
                            .jsonArray[0]
                            .jsonObject["resource"]!!
                            .jsonObject["attributes"]!!
                            .jsonArray
                            .associate {
                                it.jsonObject["key"]!!.jsonPrimitive.content to
                                    it.jsonObject["value"]!!
                                        .jsonObject["stringValue"]!!
                                        .jsonPrimitive.content
                            }

                    attributes shouldBe
                        mapOf(
                            "service.name" to "checkout",
                            "service.version" to "1.4.0",
                            "deployment.environment.name" to "production",
                            "region" to "eu-west-1",
                        )
                }
            }

            scenario("the logger's name is the instrumentation scope, one per source") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use {
                        it.export(
                            RESOURCE,
                            listOf(
                                log(source = "com.softistx.Checkout"),
                                log(source = "com.softistx.Outbox"),
                                log(source = "com.softistx.Checkout"),
                            ),
                        )
                    }

                    val scopes =
                        body(collector.on("/v1/logs")!!.body)["resourceLogs"]!!
                            .jsonArray[0]
                            .jsonObject["scopeLogs"]!!
                            .jsonArray
                    scopes.map {
                        it.jsonObject["scope"]!!
                            .jsonObject["name"]!!
                            .jsonPrimitive.content
                    } shouldBe
                        listOf("com.softistx.Checkout", "com.softistx.Outbox")
                    scopes[0].jsonObject["logRecords"]!!.jsonArray.size shouldBe 2
                }
            }

            scenario("a record carries its severity, its body, its ids and its attributes") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use {
                        it.export(
                            RESOURCE,
                            listOf(log(attributes = attributesOf("orderId" to "A-91", "amount" to 4999))),
                        )
                    }

                    val record =
                        body(collector.on("/v1/logs")!!.body)["resourceLogs"]!!
                            .jsonArray[0]
                            .jsonObject["scopeLogs"]!!
                            .jsonArray[0]
                            .jsonObject["logRecords"]!!
                            .jsonArray[0]
                            .jsonObject

                    record["severityNumber"]!!.jsonPrimitive.int shouldBe 13
                    record["severityText"]!!.jsonPrimitive.content shouldBe "WARN"
                    record["body"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content shouldBe "charging"
                    record["traceId"]!!.jsonPrimitive.content shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
                    record["spanId"]!!.jsonPrimitive.content shouldBe "00f067aa0ba902b7"
                    // 2026-09-01T10:00:00Z, in nanoseconds, quoted because it is an int64.
                    record["timeUnixNano"]!!.jsonPrimitive.content shouldBe "1788256800000000000"

                    val attributes = record["attributes"]!!.jsonArray.associateBy { it.jsonObject["key"]!!.jsonPrimitive.content }
                    attributes["orderId"]!!
                        .jsonObject["value"]!!
                        .jsonObject["stringValue"]!!
                        .jsonPrimitive.content shouldBe "A-91"
                    // An int64 is a string in this encoding, and a receiver that reads a number would
                    // silently lose the low bits of a big one.
                    attributes["amount"]!!
                        .jsonObject["value"]!!
                        .jsonObject["intValue"]!!
                        .jsonPrimitive.content shouldBe "4999"
                }
            }

            scenario("a failure becomes the exception attributes") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use {
                        it.export(RESOURCE, listOf(log(error = ErrorInfo("java.io.IOException", "no route", "at x"))))
                    }

                    val attributes =
                        body(collector.on("/v1/logs")!!.body)["resourceLogs"]!!
                            .jsonArray[0]
                            .jsonObject["scopeLogs"]!!
                            .jsonArray[0]
                            .jsonObject["logRecords"]!!
                            .jsonArray[0]
                            .jsonObject["attributes"]!!
                            .jsonArray
                            .associate {
                                it.jsonObject["key"]!!.jsonPrimitive.content to
                                    it.jsonObject["value"]!!
                                        .jsonObject["stringValue"]!!
                                        .jsonPrimitive.content
                            }

                    attributes["exception.type"] shouldBe "java.io.IOException"
                    attributes["exception.message"] shouldBe "no route"
                    attributes["exception.stacktrace"] shouldBe "at x"
                }
            }
        }

        feature("the trace document") {
            scenario("a span carries its ids, its kind, its window and its status") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use { it.export(RESOURCE, listOf(span())) }

                    val record =
                        body(collector.on("/v1/traces")!!.body)["resourceSpans"]!!
                            .jsonArray[0]
                            .jsonObject["scopeSpans"]!!
                            .jsonArray[0]
                            .jsonObject["spans"]!!
                            .jsonArray[0]
                            .jsonObject

                    record["traceId"]!!.jsonPrimitive.content shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
                    record["parentSpanId"]!!.jsonPrimitive.content shouldBe "aaaaaaaaaaaaaaaa"
                    record["kind"]!!.jsonPrimitive.int shouldBe 3
                    record["startTimeUnixNano"]!!.jsonPrimitive.content shouldBe "1788256800000000000"
                    record["endTimeUnixNano"]!!.jsonPrimitive.content shouldBe "1788256800500000000"
                    record["status"]!!.jsonObject["code"]!!.jsonPrimitive.int shouldBe 1

                    val attributes = record["attributes"]!!.jsonArray.associateBy { it.jsonObject["key"]!!.jsonPrimitive.content }
                    attributes["ok"]!!
                        .jsonObject["value"]!!
                        .jsonObject["boolValue"]!!
                        .jsonPrimitive.content shouldBe "true"
                    attributes["ratio"]!!
                        .jsonObject["value"]!!
                        .jsonObject["doubleValue"]!!
                        .jsonPrimitive.content shouldBe "0.25"
                }
            }

            scenario("a root span has no parentSpanId at all, rather than a null one") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use {
                        it.export(RESOURCE, listOf(span().copy(parent = null)))
                    }

                    val record =
                        body(collector.on("/v1/traces")!!.body)["resourceSpans"]!!
                            .jsonArray[0]
                            .jsonObject["scopeSpans"]!!
                            .jsonArray[0]
                            .jsonObject["spans"]!!
                            .jsonArray[0]
                            .jsonObject

                    record.containsKey("parentSpanId") shouldBe false
                }
            }

            scenario("the three statuses become OTLP's three codes") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use {
                        it.export(
                            RESOURCE,
                            listOf(
                                span(name = "ok", status = SpanStatus.Ok),
                                span(name = "bad", status = SpanStatus.Error, error = ErrorInfo("X", "boom")),
                                span(name = "stopped", status = SpanStatus.Cancelled),
                            ),
                        )
                    }

                    val spans =
                        body(collector.on("/v1/traces")!!.body)["resourceSpans"]!!
                            .jsonArray[0]
                            .jsonObject["scopeSpans"]!!
                            .jsonArray[0]
                            .jsonObject["spans"]!!
                            .jsonArray
                            .associateBy { it.jsonObject["name"]!!.jsonPrimitive.content }

                    spans["ok"]!!
                        .jsonObject["status"]!!
                        .jsonObject["code"]!!
                        .jsonPrimitive.int shouldBe 1
                    spans["bad"]!!
                        .jsonObject["status"]!!
                        .jsonObject["code"]!!
                        .jsonPrimitive.int shouldBe 2
                    spans["bad"]!!
                        .jsonObject["status"]!!
                        .jsonObject["message"]!!
                        .jsonPrimitive.content shouldBe "boom"
                    // Cancelled is `unset`, because a span that stopped is not a span that failed.
                    spans["stopped"]!!
                        .jsonObject["status"]!!
                        .jsonObject["code"]!!
                        .jsonPrimitive.int shouldBe 0
                }
            }

            scenario("events keep their name, their moment and their attributes") {
                FakeCollector().use { collector ->
                    OtlpExporter(collector.endpoint).use {
                        it.export(
                            RESOURCE,
                            listOf(span(events = listOf(SpanEvent("retrying", AT, attributesOf("attempt" to 2))))),
                        )
                    }

                    val event =
                        body(collector.on("/v1/traces")!!.body)["resourceSpans"]!!
                            .jsonArray[0]
                            .jsonObject["scopeSpans"]!!
                            .jsonArray[0]
                            .jsonObject["spans"]!!
                            .jsonArray[0]
                            .jsonObject["events"]!!
                            .jsonArray[0]
                            .jsonObject

                    event["name"]!!.jsonPrimitive.content shouldBe "retrying"
                    event["timeUnixNano"]!!.jsonPrimitive.content shouldBe "1788256800000000000"
                    event["attributes"]!!
                        .jsonArray[0]
                        .jsonObject["value"]!!
                        .jsonObject["intValue"]!!
                        .jsonPrimitive.content shouldBe "2"
                }
            }
        }

        feature("what it does when the collector is unhappy") {
            scenario("a 503 is tried again, and the second answer is taken") {
                FakeCollector({ call -> if (call == 0) Answer(503, "busy") else Answer(200, "") }).use { collector ->
                    OtlpExporter(collector.endpoint, attempts = 3, backoff = 1.milliseconds).use {
                        it.export(RESOURCE, listOf(log()))
                    }

                    collector.requests.size shouldBe 2
                }
            }

            scenario("a 400 is not tried again — the same document would be wrong again") {
                FakeCollector({ Answer(400, "bad field: severityNumber") }).use { collector ->
                    val exporter = OtlpExporter(collector.endpoint, attempts = 3, backoff = 1.milliseconds)
                    val failure = shouldThrow<OtlpRefusedException> { exporter.export(RESOURCE, listOf(log())) }
                    exporter.close()

                    failure.status shouldBe 400
                    failure.body shouldBe "bad field: severityNumber"
                    collector.requests.size shouldBe 1
                }
            }

            scenario("a 503 that never stops gives up with the last answer") {
                FakeCollector({ Answer(503, "busy") }).use { collector ->
                    val exporter = OtlpExporter(collector.endpoint, attempts = 2, backoff = 1.milliseconds)
                    shouldThrow<OtlpRefusedException> { exporter.export(RESOURCE, listOf(log())) }.status shouldBe 503
                    exporter.close()

                    collector.requests.size shouldBe 2
                }
            }

            scenario("a collector that is not there is an unreachable failure, not a hang") {
                val exporter =
                    OtlpExporter("http://127.0.0.1:1", attempts = 2, backoff = 1.milliseconds, timeout = 500.milliseconds)
                shouldThrow<OtlpUnreachableException> { exporter.export(RESOURCE, listOf(log())) }
                exporter.close()
            }
        }

        feature("partial success") {
            scenario("an empty one is a plain success") {
                FakeCollector({ Answer(200, """{"partialSuccess":{}}""") }).use { collector ->
                    OtlpExporter(collector.endpoint).use { it.export(RESOURCE, listOf(log())) }
                    collector.requests.size shouldBe 1
                }
            }

            scenario("a real one is reported and deliberately not retried") {
                val answer = """{"partialSuccess":{"rejectedLogRecords":"2","errorMessage":"attribute too long"}}"""
                FakeCollector({ Answer(200, answer) }).use { collector ->
                    val exporter = OtlpExporter(collector.endpoint, attempts = 3, backoff = 1.milliseconds)
                    val failure = shouldThrow<OtlpRejectedException> { exporter.export(RESOURCE, listOf(log())) }
                    exporter.close()

                    failure.partialSuccess.rejected shouldBe 2
                    failure.partialSuccess.errorMessage shouldBe "attribute too long"
                    // Retrying would deliver the accepted records twice.
                    collector.requests.size shouldBe 1
                }
            }

            scenario("a count written as a number rather than a string is read too") {
                FakeCollector({ Answer(200, """{"partialSuccess":{"rejectedSpans":3}}""") }).use { collector ->
                    var seen: PartialSuccess? = null
                    OtlpExporter(collector.endpoint, onPartialSuccess = { seen = it }).use {
                        it.export(RESOURCE, listOf(span()))
                    }

                    seen.shouldNotBeNull().rejected shouldBe 3
                }
            }

            scenario("a 200 whose body makes no sense is still a 200") {
                FakeCollector({ Answer(200, "OK") }).use { collector ->
                    OtlpExporter(collector.endpoint).use { it.export(RESOURCE, listOf(log())) }
                    collector.requests.size shouldBe 1
                }
            }
        }

        feature("the http client") {
            scenario("one that was handed in is not closed by this exporter") {
                FakeCollector().use { collector ->
                    val client =
                        java.net.http.HttpClient
                            .newHttpClient()
                    OtlpExporter(collector.endpoint, client = client).use { it.export(RESOURCE, listOf(log())) }

                    // Still usable: closing somebody else's client is how an application loses its
                    // outbound HTTP because it turned telemetry on.
                    OtlpExporter(collector.endpoint, client = client).use { it.export(RESOURCE, listOf(log())) }
                    collector.requests.size shouldBe 2
                    client.close()
                }
            }
        }
    })
