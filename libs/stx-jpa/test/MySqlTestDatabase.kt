package com.strange.jpa

import com.strange.testing.containers.ContainerService
import com.strange.testing.containers.MysqlEndpoint
import com.strange.testing.containers.TestNames
import com.strange.testing.containers.mysqlContainer
import io.vertx.core.Vertx
import io.vertx.mysqlclient.MySQLBuilder
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

/**
 * The MySQL the dialect spec talks to: one started for this run, unless `MYSQL_TEST_URI` and its two
 * credentials name a server that is already up.
 *
 * **Each spec gets a database of its own**, not a schema — in MySQL those are the same thing, so the
 * per-spec schema `JpaTestDatabase` creates is a per-spec database here. It is the same rule and the
 * same reason: `create-drop` inside something the spec made cannot take anything else with it.
 */
internal object MySqlTestDatabase {
    private const val ATTEMPTS = 5
    private const val INTERVAL_MILLIS = 2_000L

    private val mysql = mysqlContainer()

    /** A database per call, and one no other run will pick — see [TestNames]. */
    private val databases = TestNames("stx_mysql_test", separator = "_")

    val endpoint: MysqlEndpoint get() = mysql.requireEndpoint()

    /** Whether a server answered — checked once, so a machine without one skips instead of hanging. */
    val available: Boolean get() = probe == null

    /**
     * Why this spec is skipped, or `null` when it is not — and printed, because nothing else will.
     *
     * A skip with no reason has cost an afternoon twice here: a container that timed out under load
     * and a machine without Docker read identically. [ContainerService.describe] answers the first
     * half and the probe answers the second, since a server can be reachable and still refuse a
     * credential.
     *
     * **The printing is not laziness about the framework, it is the framework.** kotest's
     * `enabledOrReasonIf` takes an `Enabled.disabled(reason)` and 6.2.2 renders it as `Reason:` with
     * nothing after it — confirmed by pointing `MYSQL_TEST_URI` at a dead port and watching a skip
     * with a reason print an empty one. So the reason is written where it will actually be read.
     */
    private val probe: String? by lazy {
        val reason =
            if (!mysql.available) {
                mysql.describe()
            } else {
                answers()?.let { "mysql: ${endpoint.uri} answered no query — $it" }
            }
        reason?.also { println("MySQL specs are skipped — $it") }
    }

    /**
     * Why the first `select 1` failed, or null once one succeeds. Tried more than once, deliberately.
     *
     * A MySQL that has just logged *ready for connections* twice can still drop the first connection
     * it is offered, and the exception for that is
     * `ClosedConnectionException: Failed to read any response from the server` — the same words the
     * `caching_sha2_password` failure uses, which is what made this look like an authentication
     * problem for two sessions running. One retry tells them apart: an auth failure fails again.
     */
    private fun answers(): Throwable? {
        var failure: Throwable? = null
        repeat(ATTEMPTS) { attempt ->
            failure =
                runCatching { runBlocking { withClient(endpoint.uri) { it.ask("select 1") } } }
                    .exceptionOrNull() ?: return null
            if (attempt < ATTEMPTS - 1) Thread.sleep(INTERVAL_MILLIS)
        }
        return failure
    }

    /**
     * A [Jpa] over a database of its own, with the tables for [entities] created in it and dropped
     * with it.
     */
    suspend fun <T> withJpa(
        vararg entities: KClass<*>,
        block: suspend (Jpa) -> T,
    ): T {
        val database = databases.next()
        return withClient(endpoint.uri) { client ->
            client.ask("drop database if exists $database")
            client.ask("create database $database")
            try {
                Jpa
                    .connect(
                        JpaConfig(
                            uri = endpoint.uri.substringBeforeLast('/') + "/" + database,
                            username = endpoint.username,
                            password = endpoint.password,
                            schemaMode = SchemaMode.CREATE_DROP,
                        ),
                        entities.toList(),
                    ).use { block(it) }
            } finally {
                client.ask("drop database if exists $database")
            }
        }
    }

    /**
     * The type MySQL reports for a column. The Postgres harness has the same helper for the same
     * reason: a round trip cannot tell a JSON column from a column holding the bytes of an object.
     *
     * Aliased because MySQL names its `information_schema` columns in upper case and the Vert.x row
     * looks them up as written.
     */
    suspend fun columnType(
        database: String,
        table: String,
        column: String,
    ): String? =
        withClient(endpoint.uri) { client ->
            client
                .preparedQuery(
                    """
                    select data_type as data_type from information_schema.columns
                    where table_schema = ? and table_name = ? and column_name = ?
                    """.trimIndent(),
                ).execute(Tuple.of(database, table, column))
                .toCompletionStage()
                .await()
                .firstOrNull()
                ?.getString("data_type")
        }

    private suspend fun <T> withClient(
        uri: String,
        block: suspend (Pool) -> T,
    ): T {
        val vertx = Vertx.vertx()
        val pool =
            MySQLBuilder
                .pool()
                .connectingTo(
                    MySQLConnectOptions
                        .fromUri(uri)
                        .setUser(endpoint.username)
                        .setPassword(endpoint.password),
                ).with(PoolOptions().setMaxSize(2))
                .using(vertx)
                .build()
        try {
            return block(pool)
        } finally {
            pool.close().toCompletionStage().await()
            vertx.close().toCompletionStage().await()
        }
    }

    private suspend fun Pool.ask(sql: String) {
        query(sql).execute().toCompletionStage().await()
    }
}
