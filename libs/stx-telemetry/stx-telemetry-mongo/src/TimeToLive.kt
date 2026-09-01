package com.strange.telemetry.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
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
 * **A changed retention re-creates the index.** Mongo refuses a second index with the same key and
 * different options, so an application that goes from thirty days to seven would otherwise keep the
 * thirty for ever and never be told. The old one is dropped and the new one built, which on a large
 * collection is minutes of background work — the alternative is a setting that silently does nothing.
 */
internal class TimeToLive(
    private val collection: MongoCollection<Document>,
    private val retention: Duration,
) {
    suspend fun ensure() {
        val seconds = retention.inWholeSeconds
        val existing = collection.listIndexes().firstOrNull { it.getString("name") == NAME }
        if (existing != null) {
            if ((existing["expireAfterSeconds"] as? Number)?.toLong() == seconds) return
            collection.dropIndex(NAME)
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
