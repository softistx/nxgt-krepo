package com.softistx.migrations.db.sql

import com.softistx.jpa.Jpa
import com.softistx.jpa.session.connection
import com.softistx.migrations.MigrationStatus
import com.softistx.migrations.db.ledgerContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.future.await
import kotlin.time.Duration.Companion.minutes

/** The single scalar [sql] selects, on a connection borrowed from the pool. */
private suspend fun Jpa.scalar(sql: String): Any? = connection { it.select(sql).await().next()[0] }

/**
 * The SQL ledger, on both dialects it claims to support.
 *
 * The contract runs twice — a ledger verified on Postgres alone would compile against MySQL and
 * refuse every statement it sent, because the two disagree about how a bind parameter is spelled and
 * about how an insert that may already exist is written. Those two facts are all of [SqlDialect], and
 * this is where each half is measured.
 */
class SqlMigrationLedgerTest :
    FeatureSpec({

        ledgerContract("PostgreSQL", PostgresTestDatabase.available) { lease, block ->
            PostgresTestDatabase.withJpa { jpa -> block { SqlMigrationLedger(jpa, "stx_migrations", lease) } }
        }

        ledgerContract("MySQL", MysqlTestDatabase.available) { lease, block ->
            MysqlTestDatabase.withJpa { jpa -> block { SqlMigrationLedger(jpa, "stx_migrations", lease) } }
        }

        feature("the tables it writes").config(enabled = PostgresTestDatabase.available) {
            scenario("go in JpaConfig.schema and not in the connection's search_path") {
                // The finding NativeDdlTest pinned, applied: nothing renders these statements from the
                // mapping, so `hibernate.default_schema` does not reach them and the ledger qualifies
                // its own names. Unqualified, both tables would land in `public`.
                PostgresTestDatabase.withJpa { jpa ->
                    SqlMigrationLedger(jpa, "stx_migrations", 1.minutes).prepare()

                    val schema = jpa.config.schema
                    jpa.scalar(
                        "select count(*) from information_schema.tables where table_schema = '$schema' " +
                            "and table_name in ('stx_migrations', 'stx_migrations_lock')",
                    ) shouldBe 2L
                }
            }

            scenario("prepare is idempotent against a table that is already there and holds rows") {
                PostgresTestDatabase.withJpa { jpa ->
                    val ledger = SqlMigrationLedger(jpa, "stx_migrations", 1.minutes)
                    ledger.prepare()
                    ledger.claim(
                        com.softistx.migrations.db
                            .record(1),
                    )

                    ledger.prepare()

                    ledger.all().map { it.version } shouldContainExactly listOf(1L)
                }
            }

            scenario("the lock is a second table, so it is never read as a version that ran") {
                PostgresTestDatabase.withJpa { jpa ->
                    val ledger = SqlMigrationLedger(jpa, "stx_migrations", 1.minutes)
                    ledger.prepare()

                    ledger.all().shouldBeEmpty()
                    jpa.scalar("select count(*) from ${jpa.config.schema}.stx_migrations_lock") shouldBe 1L
                }
            }

            scenario("a held lock names its owner, and gives the row back afterwards") {
                PostgresTestDatabase.withJpa { jpa ->
                    val ledger = SqlMigrationLedger(jpa, "stx_migrations", 1.minutes, "host/9/deadbeef")
                    ledger.prepare()
                    val locks = "${jpa.config.schema}.stx_migrations_lock"

                    ledger.guarded { jpa.scalar("select locked_by from $locks") shouldBe "host/9/deadbeef" }

                    jpa.scalar("select locked_by from $locks") shouldBe null
                }
            }
        }

        feature("a lease nobody renews").config(enabled = PostgresTestDatabase.available) {
            scenario("expires, so a process that died holding the lock does not hold it forever") {
                // The recovery path the contract cannot ask for: it needs a lock row left behind by a
                // holder that never came back, which only a direct write can make.
                PostgresTestDatabase.withJpa { jpa ->
                    val ledger = SqlMigrationLedger(jpa, "stx_migrations", 1.minutes)
                    ledger.prepare()
                    val locks = "${jpa.config.schema}.stx_migrations_lock"

                    jpa.connection {
                        it.executeUnprepared("update $locks set locked_by = 'host/1/gone', locked_until = 1").await()
                    }

                    ledger.guarded { "taken over" } shouldBe "taken over"
                }
            }

            scenario("and a lease that has not expired is still somebody else's") {
                PostgresTestDatabase.withJpa { jpa ->
                    val ledger = SqlMigrationLedger(jpa, "stx_migrations", 1.minutes)
                    ledger.prepare()
                    val locks = "${jpa.config.schema}.stx_migrations_lock"
                    val far = System.currentTimeMillis() + 10.minutes.inWholeMilliseconds

                    jpa.connection {
                        it.executeUnprepared("update $locks set locked_by = 'host/1/alive', locked_until = $far").await()
                    }

                    ledger.guarded { "taken over" } shouldBe null
                }
            }
        }

        feature("building a runner").config(enabled = PostgresTestDatabase.available) {
            scenario("a pool of one is refused, with the deadlock named rather than met at startup") {
                // A run holds one connection for the migration and needs another for the watchdog
                // renewing the lock underneath it. On a pool of one nothing fails: it hangs.
                PostgresTestDatabase.withJpa(poolSize = 1) { jpa ->
                    val failure = shouldThrow<IllegalArgumentException> { SqlMigrations(jpa, emptyList()) }

                    failure.message.shouldNotBeNull() shouldContain "lease watchdog"
                }
            }
        }

        feature("a migration run end to end, on PostgreSQL").config(enabled = PostgresTestDatabase.available) {
            scenario("applies DDL a session could not have sent, records it, and does nothing the second time") {
                PostgresTestDatabase.withJpa { jpa ->
                    val schema = jpa.config.schema
                    val create =
                        object : SqlMigration {
                            override val version = 1L
                            override val description = "the orders table"

                            override suspend fun migrate(context: SqlMigrationSession) {
                                // two statements in one call, which nothing on the session accepts
                                context.execute(
                                    "create table $schema.orders (id bigint primary key, total numeric(12, 2)); " +
                                        "create index orders_total on $schema.orders (total)",
                                )
                                context.update("insert into $schema.orders (id, total) values (1, 9.99)") shouldBe 1
                            }
                        }

                    SqlMigrations(jpa, listOf(create)).run()
                    val second = SqlMigrations(jpa, listOf(create)).run()

                    second.single().status shouldBe MigrationStatus.APPLIED
                    second.single().description shouldBe "the orders table"
                    second.single().durationMillis.shouldNotBeNull()
                    jpa.scalar("select count(*) from $schema.orders") shouldBe 1L
                }
            }

            scenario("a failing migration records FAILED and the next run refuses to do anything") {
                PostgresTestDatabase.withJpa { jpa ->
                    val broken =
                        object : SqlMigration {
                            override val version = 1L

                            override suspend fun migrate(context: SqlMigrationSession) {
                                context.execute("create table nowhere.orders (id bigint primary key)")
                            }
                        }

                    runCatching { SqlMigrations(jpa, listOf(broken)).run() }

                    val ledger = SqlMigrationLedger(jpa, "stx_migrations", 1.minutes)
                    ledger.find(1L).shouldNotBeNull().status shouldBe MigrationStatus.FAILED
                    ledger.blocking(15.minutes).shouldNotBeNull().version shouldBe 1L
                }
            }
        }

        feature("a migration run end to end, on MySQL").config(enabled = MysqlTestDatabase.available) {
            scenario("applies and records the same way, which is the point of running the contract twice") {
                MysqlTestDatabase.withJpa { jpa ->
                    val create =
                        object : SqlMigration {
                            override val version = 1L
                            override val description = "the orders table"

                            override suspend fun migrate(context: SqlMigrationSession) {
                                context.execute("create table orders (id bigint primary key, total decimal(12, 2))")
                                context.update("insert into orders (id, total) values (1, 9.99)") shouldBe 1
                            }
                        }

                    SqlMigrations(jpa, listOf(create)).run().single().status shouldBe MigrationStatus.APPLIED

                    jpa.scalar("select count(*) from orders") shouldBe 1L
                }
            }
        }
    })
