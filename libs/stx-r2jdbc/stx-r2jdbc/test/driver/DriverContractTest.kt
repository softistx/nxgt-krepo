package com.softistx.r2jdbc.driver

import com.softistx.r2jdbc.TestMysql
import com.softistx.r2jdbc.TestPostgres
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await

/**
 * What the Vert.x SQL client actually does, asked of both servers, below anything this library adds.
 *
 * **Every design decision in `stx-r2jdbc` points at a scenario in this file**, which is the rule the
 * plan for this library set itself: three predictions were measured wrong in the session that
 * produced it, and each wrong one was the useful part. Two of these corrected an assumption on the
 * first run — the placeholder failure on Postgres is a *type inference* error and not a syntax one,
 * and an update that changes nothing still reports a matched row on Postgres, not only on MySQL.
 *
 * It talks to a raw pool on purpose. A probe routed through this library's own `Sql` would be asking
 * the design to confirm itself.
 */
class DriverContractTest :
    FeatureSpec({

        feature("postgres").config(enabled = TestPostgres.available) {
            scenario("counts its parameters, and refuses an anonymous one") {
                TestPostgres.withPool { pool ->
                    pool.one("select \$1::int as v", Tuple.of(7)) shouldBe 7
                    // The failure a caller writing MySQL's spelling gets, and the reason
                    // `Placeholders` exists rather than a note in a README.
                    shouldThrowAny { pool.one("select ? as v", Tuple.of(7)) }
                }
            }

            scenario("infers a bare parameter's type as text, which is not a syntax error") {
                TestPostgres.withPool { pool ->
                    // `select $1 as v` parses. It then refuses an Int, because Postgres has nothing
                    // to infer the type from — a confusing failure, and one worth pinning, because
                    // it is what a caller sees when they cast nothing.
                    shouldThrowAny { pool.one("select \$1 as v", Tuple.of(7)) }
                }
            }

            scenario("folds an unquoted column alias to lower case") {
                TestPostgres.withPool { pool ->
                    pool.columns("select 1 as FooBar") shouldBe listOf("foobar")
                }
            }

            scenario("scopes a transaction to its connection, and undoes it on rollback") {
                TestPostgres.withSchema { schema ->
                    TestPostgres.withPool { pool -> pool.transactionContract(schema) }
                }
            }

            scenario("serialises two statements issued at once on one connection") {
                TestPostgres.withPool { pool -> pool.overlapContract() }
            }

            scenario("counts a matched row whether or not the update changed it") {
                TestPostgres.withSchema { schema ->
                    TestPostgres.withPool { pool -> pool.rowCountContract(schema) }
                }
            }
        }

        feature("mysql").config(enabled = TestMysql.available) {
            scenario("takes an anonymous parameter, and refuses a numbered one") {
                TestMysql.withDatabase { database ->
                    TestMysql.withPool(TestMysql.uri(database)) { pool ->
                        pool.one("select ? as v", Tuple.of(7)) shouldBe 7
                        shouldThrowAny { pool.one("select \$1 as v", Tuple.of(7)) }
                    }
                }
            }

            scenario("keeps an unquoted column alias as written") {
                TestMysql.withDatabase { database ->
                    TestMysql.withPool(TestMysql.uri(database)) { pool ->
                        pool.columns("select 1 as FooBar") shouldBe listOf("FooBar")
                    }
                }
            }

            scenario("scopes a transaction to its connection, and undoes it on rollback") {
                TestMysql.withDatabase { database ->
                    TestMysql.withPool(TestMysql.uri(database)) { pool -> pool.transactionContract(database) }
                }
            }

            scenario("serialises two statements issued at once on one connection") {
                TestMysql.withDatabase { database ->
                    TestMysql.withPool(TestMysql.uri(database)) { pool -> pool.overlapContract() }
                }
            }

            scenario("counts a matched row whether or not the update changed it") {
                TestMysql.withDatabase { database ->
                    TestMysql.withPool(TestMysql.uri(database)) { pool -> pool.rowCountContract(database) }
                }
            }
        }
    })

private suspend fun Pool.one(
    sql: String,
    parameters: Tuple,
): Int? =
    preparedQuery(sql)
        .execute(parameters)
        .toCompletionStage()
        .await()
        .first()
        .getInteger("v")

private suspend fun Pool.columns(sql: String): List<String> =
    query(sql)
        .execute()
        .toCompletionStage()
        .await()
        .columnsNames()

private suspend fun Pool.ask(sql: String) {
    query(sql).execute().toCompletionStage().await()
}

private suspend fun Pool.count(sql: String): Int? =
    query(sql)
        .execute()
        .toCompletionStage()
        .await()
        .first()
        .getInteger("n")

/**
 * The whole of `transaction { }`: a connection, a `begin`, and two facts about what the rest of the
 * pool can see. Nothing here is this library's — it is why this library needs no more than this.
 */
private suspend fun Pool.transactionContract(namespace: String) {
    ask("create table $namespace.ledger (id int primary key)")
    val connection = connection.toCompletionStage().await()
    val transaction = connection.begin().toCompletionStage().await()

    connection
        .query("insert into $namespace.ledger values (1)")
        .execute()
        .toCompletionStage()
        .await()
    // Another connection from the same pool, mid-transaction: the row is not there.
    count("select count(*) as n from $namespace.ledger") shouldBe 0

    transaction.rollback().toCompletionStage().await()
    count("select count(*) as n from $namespace.ledger") shouldBe 0
    // And the connection is usable again, which is what lets the pool take it back.
    connection
        .query("select 1 as n")
        .execute()
        .toCompletionStage()
        .await()
        .first()
        .getInteger("n") shouldBe 1
    connection.close().toCompletionStage().await()
}

/**
 * Two statements launched at once down one connection.
 *
 * They both return, in order, correct. So `transaction { }` need not forbid an `async` inside it —
 * and need not promise anything either, because the driver queues them rather than running them at
 * once. `stx-jpa`'s `connection { }` has the opposite rule, and it is Hibernate's, not the driver's.
 */
private suspend fun Pool.overlapContract() {
    val connection = connection.toCompletionStage().await()
    val answers =
        coroutineScope {
            (1..2)
                .map { n ->
                    async {
                        connection
                            .query("select $n as n")
                            .execute()
                            .toCompletionStage()
                            .await()
                            .first()
                            .getInteger("n")
                    }
                }.awaitAll()
        }
    answers shouldBe listOf(1, 2)
    connection.close().toCompletionStage().await()
}

/**
 * What `rowCount()` counts, which is the same on both servers and is not what the folklore says.
 *
 * `SqlDialect` in `stx-migrations` records MySQL reporting a matched-but-unchanged row as one
 * affected, with `CLIENT_FOUND_ROWS` as the reason. Postgres does the same, for its own reason — it
 * writes a new tuple version regardless — so `Sql.execute` can promise *matched* on both.
 */
private suspend fun Pool.rowCountContract(namespace: String) {
    ask("create table $namespace.tallies (id int primary key, label varchar(40))")
    ask("insert into $namespace.tallies values (1, 'a')")

    query("update $namespace.tallies set label = 'a' where id = 1")
        .execute()
        .toCompletionStage()
        .await()
        .rowCount() shouldBe 1
    query("update $namespace.tallies set label = 'z' where id = 99")
        .execute()
        .toCompletionStage()
        .await()
        .rowCount() shouldBe 0
}
