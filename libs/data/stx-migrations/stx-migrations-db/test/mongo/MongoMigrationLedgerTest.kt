package com.softistx.migrations.db.mongo

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.migrations.MigrationStatus
import com.softistx.migrations.db.ledgerContract
import com.softistx.migrations.db.record
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The MongoDB ledger: the shared contract, and the four things only this store can be asked.
 */
class MongoMigrationLedgerTest :
    FeatureSpec({

        ledgerContract("MongoDB", MongoTestCluster.available) { lease, block ->
            MongoTestCluster.withDatabase { database ->
                block { MongoMigrationLedger(database, "stx_migrations", lease) }
            }
        }

        suspend fun documents(
            database: MongoDatabase,
            collection: String,
        ) = database.getCollection<Document>(collection).find().toList()

        feature("what the documents actually hold").config(enabled = MongoTestCluster.available) {
            scenario("the version is the _id, so uniqueness is the primary key and there is no second index") {
                // The index this ledger does not create is the one that could collide with somebody
                // else's — which is the failure the runner it replaces had to name its index to avoid.
                MongoTestCluster.withDatabase { database ->
                    val ledger = MongoMigrationLedger(database, "stx_migrations", 1.minutes)
                    ledger.prepare()
                    ledger.claim(record(7))

                    documents(database, "stx_migrations").single().getLong("_id") shouldBe 7L

                    database
                        .getCollection<Document>("stx_migrations")
                        .listIndexes()
                        .map { it.getString("name") }
                        .toList() shouldContainExactly listOf("_id_")
                }
            }

            scenario("the instants are BSON dates, because a string is not something Mongo can index") {
                MongoTestCluster.withDatabase { database ->
                    val ledger = MongoMigrationLedger(database, "stx_migrations", 1.minutes)
                    ledger.prepare()
                    ledger.claim(record(1))

                    val document = documents(database, "stx_migrations").single()
                    document["startedAt"].shouldBeInstanceOf<Date>()
                    document["updatedAt"].shouldBeInstanceOf<Date>()
                }
            }

            scenario("the lock lives in its own collection, so it can never be read as a version that ran") {
                MongoTestCluster.withDatabase { database ->
                    val ledger = MongoMigrationLedger(database, "stx_migrations", 1.minutes)
                    ledger.prepare()
                    ledger.claim(record(1))

                    ledger.all().map { it.version } shouldContainExactly listOf(1L)
                    documents(database, "stx_migrations_lock").single()["_id"] shouldBe "migrations"
                }
            }

            scenario("a held lock names its owner and its expiry, and gives both back afterwards") {
                // What an operator looking at a stuck migration reads, and the only witness that the
                // release really clears the document rather than leaving it to expire.
                MongoTestCluster.withDatabase { database ->
                    val ledger = MongoMigrationLedger(database, "stx_migrations", 1.minutes, "host/9/deadbeef")
                    ledger.prepare()

                    ledger.guarded {
                        val lock = documents(database, "stx_migrations_lock").single()
                        lock.getString("lockedBy") shouldBe "host/9/deadbeef"
                        lock.getDate("lockedUntil").shouldNotBeNull()
                    }

                    val afterwards = documents(database, "stx_migrations_lock").single()
                    afterwards["lockedBy"] shouldBe null
                    afterwards["lockedUntil"] shouldBe null
                }
            }
        }

        feature("a lease nobody renews").config(enabled = MongoTestCluster.available) {
            scenario("expires, so a process that died holding the lock does not hold it forever") {
                // The recovery path, and the one the contract cannot ask for: it needs a lock document
                // left behind by a holder that never came back, which only a direct write can make.
                MongoTestCluster.withDatabase { database ->
                    val ledger = MongoMigrationLedger(database, "stx_migrations", 1.minutes)
                    ledger.prepare()

                    val locks = database.getCollection<Document>("stx_migrations_lock")
                    locks.replaceOne(
                        Document("_id", "migrations"),
                        Document("_id", "migrations")
                            .append("lockedBy", "host/1/gone")
                            .append("lockedUntil", Date((Clock.System.now() - 1.minutes).toEpochMilliseconds())),
                    )

                    ledger.guarded { "taken over" } shouldBe "taken over"
                }
            }

            scenario("and a lease that has not expired is still somebody else's") {
                MongoTestCluster.withDatabase { database ->
                    val ledger = MongoMigrationLedger(database, "stx_migrations", 1.minutes)
                    ledger.prepare()

                    val locks = database.getCollection<Document>("stx_migrations_lock")
                    locks.replaceOne(
                        Document("_id", "migrations"),
                        Document("_id", "migrations")
                            .append("lockedBy", "host/1/alive")
                            .append("lockedUntil", Date((Clock.System.now() + 10.minutes).toEpochMilliseconds())),
                    )

                    ledger.guarded { "taken over" } shouldBe null
                }
            }
        }

        feature("a migration run end to end").config(enabled = MongoTestCluster.available) {
            scenario("applies, records, and does nothing the second time") {
                MongoTestCluster.withDatabase { database ->
                    val seeded = mutableListOf<String>()
                    val seed =
                        object : MongoMigration {
                            override val version = 1L
                            override val description = "seeds the orders"

                            override suspend fun migrate(context: MongoDatabase) {
                                context.getCollection<Document>("orders").insertOne(Document("_id", seeded.size))
                                seeded += "ran"
                            }
                        }

                    MongoMigrations(database, listOf(seed)).run()
                    val second = MongoMigrations(database, listOf(seed)).run()

                    seeded shouldContainExactly listOf("ran")
                    second.single().status shouldBe MigrationStatus.APPLIED
                    second.single().description shouldBe "seeds the orders"
                    second.single().appliedBy.shouldNotBeNull()
                    documents(database, "orders").size shouldBe 1
                }
            }

            scenario("a failing migration records FAILED and the next run refuses to do anything") {
                MongoTestCluster.withDatabase { database ->
                    val broken =
                        object : MongoMigration {
                            override val version = 1L

                            override suspend fun migrate(context: MongoDatabase): Unit = error("no such collection")
                        }

                    runCatching { MongoMigrations(database, listOf(broken)).run() }

                    val ledger = MongoMigrationLedger(database, "stx_migrations", 1.minutes)
                    ledger.find(1L).shouldNotBeNull().status shouldBe MigrationStatus.FAILED
                    ledger.find(1L).shouldNotBeNull().failure shouldBe "no such collection"
                    ledger.blocking(15.minutes).shouldNotBeNull().version shouldBe 1L
                }
            }

            scenario("with nothing to run, it writes no ledger at all") {
                MongoTestCluster.withDatabase { database ->
                    MongoMigrations(database, emptyList()).run().shouldBeEmpty()

                    database.getCollection<Document>("stx_migrations").find().firstOrNull() shouldBe null
                }
            }
        }
    })
