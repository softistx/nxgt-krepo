package com.strange.telemetry.mongo

import com.strange.telemetry.Attributes
import com.strange.telemetry.export.signalJson
import com.strange.telemetry.model.LogRecord
import com.strange.telemetry.model.Resource
import com.strange.telemetry.model.Severity
import com.strange.telemetry.model.Signal
import com.strange.telemetry.model.SpanKind
import com.strange.telemetry.model.SpanRecord
import com.strange.telemetry.trace.SpanContext
import com.strange.telemetry.trace.SpanId
import com.strange.telemetry.trace.TraceId
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.bson.Document
import java.util.Date
import kotlin.time.Instant

/**
 * The document, checked without a server.
 *
 * The questions here are the ones a running Mongo would answer far more slowly and no more clearly:
 * whether a query written against a collection reads like a `jq` filter written against a file, and
 * whether the fields Mongo has to index are of a type it can index.
 */
class DocumentsTest :
    FeatureSpec({
        val at = Instant.parse("2026-09-01T10:00:00Z")
        val resource = Resource("checkout", version = "1.4.0", environment = "production")
        val span = SpanContext(TraceId("4bf92f3577b34da6a3ce929d0e0e4736"), SpanId("00f067aa0ba902b7"), sampled = true)

        fun log(attributes: Attributes = Attributes.EMPTY) =
            LogRecord(at = at, severity = Severity.Warn, name = "charged", source = "orders", attributes = attributes, span = span)

        feature("the field names are the line format's") {
            scenario("every key signalJson writes is a key of the document") {
                val record = log()

                val document = record.document(resource)

                val json = signalJson.encodeToJsonElement(Signal.serializer(), record).jsonObject
                document.keys shouldContainAll json.keys
                document.getString("type") shouldBe "log"
                document.getString("name") shouldBe "charged"
                document.getString("severity") shouldBe json["severity"]!!.let { (it as JsonPrimitive).content }
            }
        }

        feature("the instants are BSON dates") {
            scenario("a log's at, because a string is not something Mongo will expire") {
                log().document(resource)["at"] shouldBe Date(at.toEpochMilliseconds())
            }

            scenario("a span's three, so a duration can be computed in an aggregation") {
                val record =
                    SpanRecord(
                        name = "charge",
                        context = span,
                        kind = SpanKind.Server,
                        startedAt = at,
                        endedAt = at + kotlin.time.Duration.parse("62ms"),
                    )

                val document = record.document(resource)

                document["at"].shouldBeInstanceOf<Date>()
                document["startedAt"] shouldBe Date(at.toEpochMilliseconds())
                document["endedAt"] shouldBe Date((at + kotlin.time.Duration.parse("62ms")).toEpochMilliseconds())
            }
        }

        feature("the resource, which the line format does not carry") {
            scenario("a collection holds many services, so each document says which") {
                val document = log().document(resource)

                document.getString("service") shouldBe "checkout"
                document.getString("version") shouldBe "1.4.0"
                document.getString("environment") shouldBe "production"
            }

            scenario("what is unset is absent rather than null") {
                val document = log().document(Resource("checkout"))

                document.containsKey("version") shouldBe false
                document.containsKey("environment") shouldBe false
            }
        }

        feature("attribute types survive the trip through JSON") {
            scenario("a whole number is a Long, not a Double") {
                // Otherwise http.response.status_code comes back as 200.0 and an exact-match query
                // becomes a floating-point comparison.
                val document = log(Attributes(mapOf("status" to JsonPrimitive(200)))).document(resource)

                document.get("attributes", Document::class.java)["status"] shouldBe 200L
            }

            scenario("a fractional number stays a Double, and a boolean a Boolean") {
                val attributes = Attributes(mapOf("ratio" to JsonPrimitive(0.25), "cached" to JsonPrimitive(true)))

                val values = log(attributes).document(resource).get("attributes", Document::class.java)

                values["ratio"] shouldBe 0.25
                values["cached"] shouldBe true
            }

            scenario("a nested object is a nested document, not a string") {
                val nested = JsonObject(mapOf("id" to JsonPrimitive("o-1")))
                val document = log(Attributes(mapOf("order" to nested))).document(resource)

                val values = document.get("attributes", Document::class.java)
                values["order"].shouldBeInstanceOf<Document>().getString("id") shouldBe "o-1"
            }
        }
    })
