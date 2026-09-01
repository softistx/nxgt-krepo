package com.softistx.example.orders.migration

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.migrations.db.mongo.MongoMigration
import org.bson.Document
import org.springframework.stereotype.Component

/**
 * Gives every order that predates the field an empty `tags` array.
 *
 * The shape a backfill actually takes: a filter for the documents that lack the field, one
 * `updateMany`, and no entity loaded either side of it.
 *
 * Version 2, so it runs after [V1Seed] — and finds nothing to do on a fresh database, because that
 * one already writes `tags`. A backfill that is a no-op on a new deployment and the whole point on an
 * old one is the normal case, not a sign the migration was unnecessary.
 *
 * **This is also why a migration must be safe to attempt twice.** A process killed between the write
 * and the ledger update leaves version 2 `RUNNING`, and the next startup refuses to continue until
 * somebody looks — but running this statement again would change nothing either way.
 */
@Component
class V2Tags : MongoMigration {
    override val version = 2L
    override val description = "backfills tags on orders written before the field existed"

    override suspend fun migrate(context: MongoDatabase) {
        context
            .getCollection<Document>(ORDERS)
            .updateMany(Filters.exists("tags", false), Updates.set("tags", emptyList<String>()))
    }
}
