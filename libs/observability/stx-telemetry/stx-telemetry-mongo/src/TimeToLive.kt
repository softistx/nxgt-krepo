package com.softistx.telemetry.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Retention, as an index rather than as a job.
 *
 * A TTL index is the whole of it: Mongo's own background task deletes what has aged out, on the
 * primary, without this process being up. That is the reason a database exporter is worth having at
 * all where a table would need a nightly `DELETE` — and the reason nothing here has a scheduler,
 * a coroutine of its own, or an opinion about when the deleting happens.
 *
 * **A changed retention is a `collMod`.** Mongo refuses a second index with the same key and
 * different options, so an application that goes from thirty days to seven would otherwise keep the
 * thirty for ever and never be told. `collMod` rewrites `expireAfterSeconds` in place, with no rebuild.
 * **No retention** (null, or under a second) drops the index this left behind, since a collection
 * that was told to keep everything must not go on expiring at the old age.
 */
internal class TimeToLive(
    private val database: MongoDatabase,
    private val collection: MongoCollection<Document>,
    private val retention: Duration?,
) {
    suspend fun ensure() {
        val seconds = retention?.inWholeSeconds?.takeIf { it > 0 }
        val existing = collection.listIndexes().firstOrNull { it.getString("name") == NAME }
        if (seconds == null) {
            if (existing != null) collection.dropIndex(NAME)
            return
        }
        if (existing != null) {
            if ((existing["expireAfterSeconds"] as? Number)?.toLong() == seconds) return
            database.runCommand(
                Document("collMod", collection.namespace.collectionName)
                    .append("index", Document("name", NAME).append("expireAfterSeconds", seconds)),
            )
            return
        }
        collection.createIndex(
            Indexes.ascending(FIELD),
            IndexOptions().name(NAME).expireAfter(seconds, TimeUnit.SECONDS),
        )
    }

    internal companion object {
        /** The instant every signal has, and the only one worth expiring on. */
        const val FIELD = "at"

        /** Named, so this can recognise its own index among whatever else the collection carries. */
        const val NAME = "stx_telemetry_ttl"
    }
}
