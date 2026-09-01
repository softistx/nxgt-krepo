package com.softistx.spring.testing

import com.softistx.spring.data.mongo.migration.MigrationEntry
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Empties [collections], so a scenario starts from a state it can name.
 *
 * The documents go and the collection stays, indexes included — `remove` and not `drop`, because
 * rebuilding an index per scenario costs more than the documents did. The context is shared across a
 * module's specs, so this is the discipline that replaces isolation.
 *
 * **Never name the migrations collection here.** It is what records that a migration ran, and a
 * migration does not run twice: emptying it would leave the seed gone and the record of it gone too.
 */
suspend fun ReactiveMongoTemplate.clear(vararg collections: String) {
    collections.forEach { remove(Query(), it).awaitSingle() }
}

/**
 * Suspends until [expected] migrations have been recorded.
 *
 * `MigrationRunner` listens for `ApplicationReadyEvent` and suspends, and **Spring does not wait for
 * a suspending listener** — so the port is already open while the seed is still being written.
 * Without this gate a seeded document lands in the middle of whichever scenario went first, after its
 * [clear], and the suite is flaky on a fast machine and green on a slow one.
 *
 * [collection] is the migration store's, which `stx.data.mongo.migration.collection` can move.
 */
suspend fun ReactiveMongoTemplate.awaitMigrations(
    expected: Int,
    timeout: Duration = 10.seconds,
    collection: String = MigrationEntry.COLLECTION,
) {
    eventually(timeout) {
        count(Query(), collection).awaitSingle() shouldBe expected.toLong()
    }
}
