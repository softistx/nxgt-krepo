package com.strange.mongo

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.BsonDocument
import kotlin.time.Instant

// Not `private`: a file-private class is one the driver's codec cannot reach by reflection, and it
// says so with an IllegalAccessException that names neither the class nor the reason.
@Serializable
internal data class Stamped(
    @SerialName("_id") val id: String,
    @Contextual val at: Instant,
)

/**
 * What [mongoClient] promises over `MongoClient.create` — the codec registry, applied.
 *
 * Read back as a [BsonDocument] rather than as a [Stamped], because a round trip through the same
 * codec would agree with itself whatever the codec did. What matters is the shape *on the server*:
 * an `Instant` has to be a BSON date, or Mongo cannot compare or sort it and an index on that field
 * is worth nothing.
 */
class MongoClientsTest :
    FeatureSpec({

        feature("a client from the factory").config(enabled = MongoTestCluster.available) {
            scenario("stores an Instant as a BSON date, not as a string") {
                mongoClient(MongoTestCluster.uri).use { client ->
                    val database = client.getDatabase("shared-mongo-factory-spec")
                    try {
                        val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
                        database.collection<Stamped>("stamped").insertOne(Stamped("s1", at))

                        val stored = database.getCollection<BsonDocument>("stamped").find().first()

                        stored["at"]?.isDateTime shouldBe true
                        stored["at"]?.asDateTime()?.value shouldBe at.toEpochMilliseconds()
                    } finally {
                        database.drop()
                    }
                }
            }

            scenario("and configure reaches the builder") {
                val settings = mongoClientSettings(MongoTestCluster.uri) { applicationName("spec") }

                settings.applicationName shouldBe "spec"
            }
        }
    })
