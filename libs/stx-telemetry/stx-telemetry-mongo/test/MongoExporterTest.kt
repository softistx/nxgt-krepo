package com.strange.telemetry.mongo

import com.strange.telemetry.Attributes
import com.strange.telemetry.model.LogRecord
import com.strange.telemetry.model.Resource
import com.strange.telemetry.model.Severity
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonPrimitive
import org.bson.Document
import java.util.Date
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The exporter against a real server, which is where the two claims that matter can be checked: that
 * a batch is one round trip, and that retention is Mongo's job rather than this process's.
 */
class MongoExporterTest :
    FeatureSpec({
        val at = Instant.parse("2026-09-01T10:00:00Z")
        val resource = Resource("checkout", version = "1.4.0", environment = "production")

        fun log(name: String) =
            LogRecord(
                at = at,
                severity = Severity.Info,
                name = name,
                source = "orders",
                attributes = Attributes(mapOf("orderId" to JsonPrimitive("o-1"), "status" to JsonPrimitive(200))),
            )

        suspend fun index(collection: com.mongodb.kotlin.client.coroutine.MongoCollection<Document>): Document? =
            collection.listIndexes().firstOrNull { it.getString("name") == TimeToLive.NAME }

        feature("writing").config(enabled = MongoTestServer.available) {
            scenario("a batch lands as documents a query can read like the file format") {
                MongoTestServer.withDatabase { database ->
                    MongoExporter(database).export(resource, listOf(log("charged"), log("refunded")))

                    val documents = database.getCollection<Document>("telemetry").find().toList()
                    documents shouldHaveSize 2
                    documents.map { it.getString("name") }.sorted() shouldBe listOf("charged", "refunded")
                    documents.first().getString("service") shouldBe "checkout"
                    documents.first()["at"] shouldBe Date(at.toEpochMilliseconds())
                    documents.first().get("attributes", Document::class.java)["status"] shouldBe 200L
                }
            }

            scenario("the collection is a parameter, for a deployment that keeps two") {
                MongoTestServer.withDatabase { database ->
                    MongoExporter(database, collection = "signals").export(resource, listOf(log("charged")))

                    database.getCollection<Document>("signals").find().toList() shouldHaveSize 1
                }
            }

            scenario("an empty batch is not a round trip, and creates nothing") {
                MongoTestServer.withDatabase { database ->
                    MongoExporter(database).export(resource, emptyList())

                    database.listCollectionNames().toList() shouldBe emptyList()
                }
            }
        }

        feature("whose client it is").config(enabled = MongoTestServer.available) {
            scenario("connecting opens one and closing takes it down with the exporter") {
                val name = MongoTestServer.next()
                try {
                    val exporter = MongoExporter.connecting(MongoTestServer.uri, database = name)
                    exporter.export(resource, listOf(log("charged")))

                    exporter.close()
                    // Twice, because a DI container closes what it hands out and cannot be told not to.
                    exporter.close()

                    runCatching { exporter.export(resource, listOf(log("late"))) }.exceptionOrNull().shouldNotBeNull()
                } finally {
                    MongoTestServer.drop(name)
                }
            }

            scenario("a database handed in is left open, because somebody else closes it") {
                MongoTestServer.withDatabase { database ->
                    val exporter = MongoExporter(database)
                    exporter.export(resource, listOf(log("charged")))

                    exporter.close()

                    // The client is the harness's, and it still works.
                    database.getCollection<Document>("telemetry").find().toList() shouldHaveSize 1
                }
            }
        }

        feature("retention").config(enabled = MongoTestServer.available) {
            scenario("a TTL index is built on the first batch, with the retention asked for") {
                MongoTestServer.withDatabase { database ->
                    MongoExporter(database, retention = 7.days).export(resource, listOf(log("charged")))

                    val ttl = index(database.getCollection("telemetry")).shouldNotBeNull()
                    (ttl["expireAfterSeconds"] as Number).toLong() shouldBe 7.days.inWholeSeconds
                    ttl.get("key", Document::class.java).keys shouldBe setOf(TimeToLive.FIELD)
                }
            }

            scenario("a changed retention rebuilds it, rather than keeping the old one for ever") {
                MongoTestServer.withDatabase { database ->
                    MongoExporter(database, retention = 30.days).export(resource, listOf(log("first")))
                    MongoExporter(database, retention = 12.hours).export(resource, listOf(log("second")))

                    val ttl = index(database.getCollection("telemetry")).shouldNotBeNull()
                    (ttl["expireAfterSeconds"] as Number).toLong() shouldBe 12.hours.inWholeSeconds
                }
            }

            scenario("null keeps everything, and builds no index to say so") {
                MongoTestServer.withDatabase { database ->
                    MongoExporter(database, retention = null).export(resource, listOf(log("charged")))

                    index(database.getCollection("telemetry")) shouldBe null
                }
            }
        }
    })
