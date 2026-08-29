package com.strange.example.orders.migration

import com.strange.spring.data.mongo.criteria.exists
import com.strange.spring.data.mongo.criteria.query
import com.strange.spring.data.mongo.migration.Migration
import com.strange.spring.data.mongo.migration.MigrationUnit
import com.strange.spring.data.mongo.template.updates
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * Gives every order that predates the field an empty `tags` array.
 *
 * The shape a backfill actually takes: a query for the documents that lack the field, one
 * `updateMulti`, and no entity loaded — `updates { }` builds the `Update` and the collection is
 * named as a string, because a migration runs against the database as it was, not as the current
 * mapping says it should be.
 *
 * Order 2, so it runs after [V1Seed] and finds nothing to do on a fresh database. That is the
 * ordinary outcome for a backfill and not a reason to skip writing one.
 */
@MigrationUnit("backfills tags on orders written before the field existed")
class V2Tags(
    private val template: ReactiveMongoTemplate,
) : Migration {
    override suspend fun migrate() {
        template
            .updateMulti(
                ("tags" exists false).query,
                updates({ it.set("tags", emptyList<String>()) }),
                "orders",
            ).awaitFirstOrNull()
    }
}
