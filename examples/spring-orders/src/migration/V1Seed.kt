package com.softistx.example.orders.migration

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.migrations.db.mongo.MongoMigration
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Component
import java.util.Date

/**
 * Puts three orders in an empty database, so a fresh checkout has something to page through.
 *
 * **The version is declared, not read out of the class name.** `V1Seed` could be renamed to
 * `SeedOrders` without changing what has run — the `1` below is the identity, and the name is for a
 * reader. Two migrations claiming version 1 abort the whole run rather than one of them being picked.
 *
 * **A `@Component` is all the registration there is.** `stx-migrations-spring` collects
 * `MongoMigration` beans by type, so nothing has to be listed anywhere and nothing is skipped for
 * lacking an annotation.
 *
 * It runs once: the ledger records version 1 as `APPLIED`, and the next startup finds it and does
 * nothing — which is why this is a migration and not an `ApplicationReadyEvent` listener checking
 * whether the collection is empty.
 *
 * **Raw [Document]s, and not `Order`.** A migration writes what the database holds, not what the
 * current mapping says it should hold: `Order` may gain a field, lose one or be renamed tomorrow, and
 * this migration must still mean in a year what it meant when it was reviewed. `ref` rather than
 * `reference` for the same reason — the stored name is the one that exists here. `_class` is
 * deliberately absent: reads are typed, so nothing needs it, and pinning a fully-qualified name into
 * a migration is a rename waiting to break.
 */
@Component
class V1Seed : MongoMigration {
    override val version = 1L
    override val description = "seeds three demo orders"

    override suspend fun migrate(context: MongoDatabase) {
        context.getCollection<Document>(ORDERS).insertMany(
            listOf(
                order("A-1001", "ada", "PAID", 24_990),
                order("A-1002", "grace", "PENDING", 4_500),
                order("A-1003", "ada", "PAID", 132_000),
            ),
        )
    }

    private fun order(
        reference: String,
        customer: String,
        status: String,
        total: Long,
    ): Document =
        Document("_id", ObjectId())
            .append("ref", reference)
            .append("customer", customer)
            .append("status", status)
            .append("total", total)
            // Written here, so V2Tags finds nothing to do on a fresh database — which is the
            // ordinary outcome for a backfill and not a reason to skip writing one.
            .append("tags", emptyList<String>())
            // A BSON date, which is what the `Instant` converters `stx.data.mongo` registers read
            // back. A string here would survive the insert and fail the first query.
            .append("placedAt", Date())
}

/** The collection both migrations here write to, under the name `Order` maps to. */
internal const val ORDERS = "orders"
