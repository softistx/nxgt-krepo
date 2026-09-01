package com.softistx.jpa.session

import com.softistx.jpa.Jpa
import com.softistx.jpa.JpaConfig
import com.softistx.jpa.JpaTestDatabase
import com.softistx.jpa.entity.Thing
import com.softistx.jpa.query.nativeMutate
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.hibernate.reactive.pool.ReactiveConnection
import kotlin.time.Duration.Companion.seconds

/**
 * What a statement sent below the session survives, and what the session's own native verbs refuse.
 *
 * This is the spec `stx-migrations` is designed against, kept here rather than run once as a scratch
 * project: a migration library needs DDL, needs a statement with a literal `?` in it, and needs to
 * know which schema an unqualified name lands in — and every one of those was a guess until this
 * file measured it. An assertion that pins a refusal is worth as much as one that pins a success,
 * because the refusals are what [connection] exists to route around.
 */
class NativeDdlTest :
    FeatureSpec({

        /** A [Jpa] over a schema of its own, with [poolSize] connections and nothing created in it. */
        suspend fun <T> withJpa(
            poolSize: Int = 10,
            block: suspend (Jpa, String) -> T,
        ): T =
            JpaTestDatabase.withSchema { schema ->
                Jpa
                    .connect(
                        JpaConfig(
                            uri = JpaTestDatabase.endpoint.uri,
                            username = JpaTestDatabase.endpoint.username,
                            password = JpaTestDatabase.endpoint.password,
                            schema = schema,
                            poolSize = poolSize,
                        ),
                        listOf(Thing::class),
                    ).use { block(it, schema) }
            }

        /** The single scalar [sql] selects, read off the positional row a [ReactiveConnection] answers with. */
        suspend fun ReactiveConnection.scalar(sql: String): Any? = select(sql).await().next()[0]

        suspend fun ReactiveConnection.tablesNamed(
            schema: String,
            table: String,
        ): Long =
            scalar(
                "select count(*) from information_schema.tables " +
                    "where table_schema = '$schema' and table_name = '$table'",
            ) as Long

        feature("a connection borrowed from Hibernate's own pool").config(enabled = JpaTestDatabase.available) {
            scenario("runs the DDL a session has no verb for") {
                withJpa { jpa, schema ->
                    jpa.connection { connection ->
                        connection.executeUnprepared("create table $schema.ledger (version bigint primary key)").await()
                        connection.tablesNamed(schema, "ledger") shouldBe 1L
                    }
                }
            }

            scenario("binds positional parameters and answers with the row count") {
                withJpa { jpa, schema ->
                    jpa.connection { connection ->
                        connection.executeUnprepared("create table $schema.ledger (version bigint primary key, note text)").await()

                        connection
                            .update(
                                "insert into $schema.ledger (version, note) values ($1, $2)",
                                arrayOf<Any?>(1L, "first"),
                            ).await() shouldBe
                            1
                        connection
                            .update(
                                "update $schema.ledger set note = $1 where version = $2",
                                arrayOf<Any?>("second", 1L),
                            ).await() shouldBe
                            1
                        connection
                            .update(
                                "update $schema.ledger set note = $1 where version = $2",
                                arrayOf<Any?>("third", 9L),
                            ).await() shouldBe
                            0

                        connection.scalar("select note from $schema.ledger where version = 1") shouldBe "second"
                    }
                }
            }

            scenario("sends the statement as written, so a literal ? is an operator and not a parameter") {
                // Postgres spells `jsonb contains key` as `?`, and it is the reason `executeUnprepared`
                // exists rather than being a stylistic choice — see the session's refusal below.
                withJpa { jpa, schema ->
                    jpa.connection { connection ->
                        connection
                            .executeUnprepared("""create table $schema.probe as select 1 as n where '{"a": 1}'::jsonb ? 'a'""")
                            .await()

                        connection.scalar("select count(*) from $schema.probe") shouldBe 1L
                    }
                }
            }

            scenario("takes more than one statement in a single call") {
                withJpa { jpa, schema ->
                    jpa.connection { connection ->
                        connection
                            .executeUnprepared(
                                "create table $schema.one (id int); create table $schema.two (id int)",
                            ).await()

                        connection.tablesNamed(schema, "one") shouldBe 1L
                        connection.tablesNamed(schema, "two") shouldBe 1L
                    }
                }
            }
        }

        feature("which schema an unqualified name lands in").config(enabled = JpaTestDatabase.available) {
            scenario("is the connection's search_path, and not JpaConfig.schema") {
                // The finding a ledger has to be written around: `hibernate.default_schema` is applied
                // when Hibernate renders a statement from the mapping, and nothing renders this one.
                // A migration that writes `create table stx_migrations` creates it wherever the server
                // happens to point, which on a shared database is somebody else's table.
                withJpa { jpa, schema ->
                    jpa.connection { connection ->
                        connection.scalar("select current_schema()") shouldNotBe schema
                    }
                }
            }

            scenario("so a qualified name is what puts it where the caller meant") {
                withJpa { jpa, schema ->
                    jpa.connection { connection ->
                        connection.executeUnprepared("create table $schema.qualified (id int)").await()
                        connection.tablesNamed(schema, "qualified") shouldBe 1L
                    }
                }
            }

            scenario("and `set search_path` moves it for the rest of the connection") {
                withJpa { jpa, schema ->
                    jpa.connection { connection ->
                        connection.executeUnprepared("set search_path to $schema").await()

                        connection.scalar("select current_schema()") shouldBe schema
                        connection.executeUnprepared("create table unqualified (id int)").await()
                        connection.tablesNamed(schema, "unqualified") shouldBe 1L
                    }
                }
            }
        }

        feature("what the session's own native verbs refuse").config(enabled = JpaTestDatabase.available) {
            scenario("nativeMutate reads a literal ? as an ordinal parameter") {
                withJpa { jpa, schema ->
                    shouldThrowAny {
                        jpa.transaction { session ->
                            session.nativeMutate("""select 1 where '{"a": 1}'::jsonb ? 'a'""").execute()
                        }
                    }

                    // and the same statement, one layer down, is fine
                    jpa.connection { connection ->
                        connection.executeUnprepared("""create table $schema.ok as select 1 where '{"a": 1}'::jsonb ? 'a'""").await()
                    }
                }
            }

            scenario("nativeMutate takes one statement, not two") {
                withJpa { jpa, schema ->
                    shouldThrowAny {
                        jpa.transaction { session ->
                            session.nativeMutate("create table $schema.a (id int); create table $schema.b (id int)").execute()
                        }
                    }
                }
            }
        }

        feature("giving the connection back").config(enabled = JpaTestDatabase.available) {
            scenario("happens when the block returns, so a pool of one serves call after call") {
                withJpa(poolSize = 1) { jpa, _ ->
                    withTimeout(20.seconds) {
                        repeat(3) { jpa.connection { it.scalar("select 1") shouldBe 1 } }
                    }
                }
            }

            scenario("happens when the block throws, which is the leak nobody would see until the pool ran dry") {
                withJpa(poolSize = 1) { jpa, _ ->
                    shouldThrowAny { jpa.connection { error("the migration failed") } }

                    withTimeout(20.seconds) { jpa.connection { it.scalar("select 1") shouldBe 1 } }
                }
            }
        }

        feature("a transaction opened on the connection").config(enabled = JpaTestDatabase.available) {
            scenario("rolls DDL back on Postgres, which is what makes a failed migration leave no table") {
                withJpa { jpa, schema ->
                    jpa.connection { connection ->
                        connection.beginTransaction().await()
                        connection.executeUnprepared("create table $schema.half (id int)").await()
                        connection.rollbackTransaction().await()

                        connection.tablesNamed(schema, "half") shouldBe 0L
                    }
                }
            }
        }
    })
