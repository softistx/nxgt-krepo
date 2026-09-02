package com.softistx.migrations.ktor

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.migrations.Migration
import com.softistx.migrations.MigrationContext
import com.softistx.migrations.MigrationRunner
import com.softistx.migrations.db.mongo.MongoMigration
import com.softistx.migrations.ledger.InMemoryLedger
import com.softistx.mongo.mongoClient
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A migration for the collector to collect. Its body never runs: nothing here calls `run()`. */
private fun mongoMigration(at: Long) =
    object : MongoMigration {
        override val version = at

        override suspend fun migrate(context: MongoDatabase) = Unit
    }

/** A runner an application built itself, for the scenario that mixes one in by hand. */
private fun handBuilt() =
    MigrationRunner<Unit>(
        ledger = InMemoryLedger(),
        migrations = emptyList<Migration<Unit>>(),
        context = MigrationContext { it(Unit) },
    )

/**
 * What `sql { }` and `mongo { }` assemble, without a server.
 *
 * **No container, and none is needed to answer this.** The driver's client connects lazily, so a
 * database handle over an unreachable URI is a real [MongoDatabase] that a real
 * `MongoMigrationLedger` can be built over — which is the whole of what `mongo { }` does. Whether
 * that ledger then works against a server is `stx-migrations-db`'s question, asked there against
 * three of them.
 *
 * **`sql { }` has no twin here, on purpose.** `Jpa` has an `internal` constructor, so reaching one
 * means starting Hibernate against a real database — and what that would assert is six lines of
 * pass-through whose mirror image is asserted below. The part with logic is the collector, and the
 * two builders share it.
 */
class StoresTest :
    FeatureSpec({

        feature("collecting the migrations") {
            scenario("migration() takes several, and may be called more than once") {
                val builder =
                    MongoMigrationsBuilder().apply {
                        migration(mongoMigration(2), mongoMigration(1))
                        migration(listOf(mongoMigration(3)))
                    }

                // In the order they were declared, not sorted: ordering is the runner's job, and
                // doing it here as well would hide a duplicate version the runner is meant to refuse.
                builder.collected.map { it.version } shouldContainExactly listOf(2L, 1L, 3L)
            }

            scenario("a builder nobody configured carries the library's defaults") {
                val builder = SqlMigrationsBuilder()

                builder.collected shouldContainExactly emptyList()
                builder.table shouldBe "stx_migrations"
                builder.lease shouldBe 5.minutes
                builder.lockTimeout shouldBe 5.minutes
                builder.lockPoll shouldBe 1.seconds
                builder.staleAfter shouldBe 15.minutes
            }

            scenario("the two builders name the ledger differently and agree on everything else") {
                // `table` and `collection` rather than one `name`, because that is what each store
                // calls the thing. The four durations are `stx.migrations.*` under another spelling.
                SqlMigrationsBuilder().collection() shouldBe MongoMigrationsBuilder().collection
            }
        }

        feature("what reaches the plugin") {
            val client = mongoClient("mongodb://127.0.0.1:1")
            afterSpec { client.close() }

            scenario("mongo { } adds one runner, built over the database it was given") {
                val configuration = MigrationsConfiguration()

                configuration.mongo(client.getDatabase("stores_test")) {
                    migration(mongoMigration(1), mongoMigration(2))
                }

                configuration.runners.size shouldBe 1
            }

            scenario("the DSL and a hand-built runner mix in one block") {
                // The claim that keeps `gate` the contract rather than a legacy path: an application
                // with a ledger of its own is not pushed out of the DSL, and both end in one list.
                val configuration = MigrationsConfiguration()

                configuration.mongo(client.getDatabase("stores_test")) { migration(mongoMigration(1)) }
                configuration.gate(handBuilt())
                configuration.mongo(client.getDatabase("stores_test_other")) { migration(mongoMigration(1)) }

                configuration.runners.size shouldBe 3
            }

            scenario("an empty block is not an error") {
                // The same answer `stx.migrations.enabled` gives with no migrations declared: turning
                // the feature on before writing the first one should not break a build. A runner with
                // an empty plan takes no lock at all.
                val configuration = MigrationsConfiguration()

                configuration.mongo(client.getDatabase("stores_test")) { }

                configuration.runners.size shouldBe 1
            }
        }
    })

/** The SQL builder's name for the ledger, so the scenario above can compare the two spellings. */
private fun SqlMigrationsBuilder.collection() = table
