package com.softistx.telemetry.mongo

import com.mongodb.client.model.InsertManyOptions
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.common.lifecycle.CloseGuard
import com.softistx.telemetry.export.Exporter
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Signal
import org.bson.Document
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Signals into a MongoDB collection, one `insertMany` per batch.
 *
 * ```kotlin
 * Telemetry("checkout") { export(MongoExporter(client.getDatabase("telemetry"))) }
 * ```
 *
 * The document is what [com.softistx.telemetry.export.signalJson] produces, so the field names are the
 * ones the file and stdout exporters write, plus the resource and with the instants as BSON dates —
 * `Documents.kt` argues both. What that buys is that a query written against a collection reads the
 * same as a `jq` filter written against a file.
 *
 * ## Retention is a TTL index
 *
 * [retention] is an index option, not a job: Mongo expires the documents itself, on the primary,
 * whether or not this process is running. Compare a file, where retention is a count of files this
 * process prunes as it rolls them, and a SQL table, where it is a nightly `DELETE` somebody has to
 * own. Null keeps everything, which is a decision to make deliberately and not a default.
 *
 * ## Whose client it is
 *
 * Built from a [MongoDatabase], it owns nothing and [close] leaves the client alone — the rule the
 * rest of this repository follows. Built with [connecting], it opened the client and closes it.
 *
 * The choice that matters is behind both: a **client separate from the application's** means a burst
 * of telemetry cannot exhaust the pool the business requests are queueing for, and a slow collector
 * cannot become a slow checkout. `stx-telemetry-spring` uses [connecting] for exactly that reason.
 *
 * ## What a failed insert does
 *
 * Nothing but propagate, to the pipeline's `onExportError`. The batch is unordered, so a document
 * Mongo rejects does not stop the ones behind it — a single oversized attribute costs its own record
 * and not the other five hundred.
 */
class MongoExporter private constructor(
    database: MongoDatabase,
    collection: String,
    retention: Duration?,
    private val owned: MongoClient?,
) : Exporter {
    /** Against a database somebody else opened, and closes. */
    constructor(
        database: MongoDatabase,
        collection: String = "telemetry",
        /** How long a signal is kept. Null keeps everything, and means the collection grows for ever. */
        retention: Duration? = 30.days,
    ) : this(database, collection, retention, owned = null)

    private val documents = database.getCollection<Document>(collection)
    private val ttl = retention?.let { TimeToLive(documents, it) }
    private val prepared = AtomicBoolean(false)
    private val guard = CloseGuard()

    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        if (batch.isEmpty()) return
        prepare()
        documents.insertMany(batch.map { it.document(resource) }, UNORDERED)
    }

    /**
     * Builds the index once, on the first batch rather than at construction.
     *
     * Creating it is a suspending call and this is built where nothing suspends — a `Telemetry { }`
     * block, a Spring `@Bean` method. Doing it here also means a database that was not up when the
     * application started is not a permanent failure: the flag is only set once it worked, so the
     * next batch tries again.
     */
    private suspend fun prepare() {
        if (ttl == null || prepared.get()) return
        ttl.ensure()
        prepared.set(true)
    }

    /** Closes the client this opened, if it opened one. Idempotent: a DI container closes twice. */
    override fun close() = guard.once { owned?.close() }

    companion object {
        /** One rejected document costs its own record, not the five hundred behind it. */
        private val UNORDERED: InsertManyOptions = InsertManyOptions().ordered(false)

        /**
         * Opens a client of its own, and closes it.
         *
         * For a caller that has no [MongoDatabase] to hand and should not be made to build one — a
         * `Telemetry { }` block, a Spring `@Bean` method. It is also the form to reach for when the
         * application *does* have a client: telemetry on its own pool is the point, not an
         * inconvenience to work around.
         */
        fun connecting(
            uri: String,
            database: String = "telemetry",
            collection: String = "telemetry",
            retention: Duration? = 30.days,
        ): MongoExporter {
            val client = MongoClient.create(uri)
            return MongoExporter(client.getDatabase(database), collection, retention, owned = client)
        }
    }
}
