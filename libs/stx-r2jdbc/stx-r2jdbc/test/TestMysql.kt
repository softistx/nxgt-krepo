package com.softistx.r2jdbc

import com.softistx.testing.containers.MysqlEndpoint
import com.softistx.testing.containers.TestNames
import com.softistx.testing.containers.mysqlContainer
import io.vertx.core.Vertx
import io.vertx.mysqlclient.MySQLBuilder
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking

/**
 * The MySQL this module's specs talk to, and the reason every claim here is made twice.
 *
 * **Each spec gets a database of its own**, not a schema — in MySQL those are the same thing, so
 * `stx-jpa`'s per-spec schema is a per-spec database here.
 *
 * The retry in [answers] is not defensive padding: a MySQL that has just logged *ready for
 * connections* can still drop the first connection it is offered, and the exception for that is
 * word-for-word the one a `caching_sha2_password` failure raises. One retry tells them apart.
 */
internal object TestMysql {
    private const val ATTEMPTS = 5
    private const val INTERVAL_MILLIS = 2_000L

    private val mysql = mysqlContainer()

    /** A database per call, and one no other run will pick — see [TestNames]. */
    private val databases = TestNames("stx_r2jdbc_my", separator = "_")

    val endpoint: MysqlEndpoint get() = mysql.requireEndpoint()

    /** Whether a server answered — checked once, so a machine without one skips instead of hanging. */
    val available: Boolean get() = probe == null

    /**
     * Why this spec is skipped, or `null` when it is not — and printed, because nothing else will:
     * kotest 6.2 renders `Enabled.disabled(reason)` as a `Reason:` with nothing after it.
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

    private fun answers(): Throwable? {
        var failure: Throwable? = null
        repeat(ATTEMPTS) { attempt ->
            failure =
                runCatching { runBlocking { withPool(endpoint.uri) { it.ask("select 1") } } }
                    .exceptionOrNull() ?: return null
            if (attempt < ATTEMPTS - 1) Thread.sleep(INTERVAL_MILLIS)
        }
        return failure
    }

    /** The URI a caller hands this library, pointed at [database]. */
    fun uri(database: String): String = endpoint.uri.substringBeforeLast('/') + "/" + database

    /**
     * What a spec connects with. The database is named twice — once in the URI's path and once as
     * the schema — because in MySQL those are the same thing, and a spec should exercise the
     * schema handling rather than route around it.
     */
    fun config(database: String): R2jdbcConfig =
        R2jdbcConfig(
            uri = uri(database),
            username = endpoint.username,
            password = endpoint.password,
            schema = database,
            poolSize = 4,
        )

    /** An [R2jdbc] over a database of its own, closed with it. */
    suspend fun <T> withR2jdbc(block: suspend (R2jdbc) -> T): T =
        withDatabase { database -> R2jdbc.connect(config(database)).use { block(it) } }

    /** Runs [block] against a database no other spec is using, and drops it afterwards. */
    suspend fun <T> withDatabase(block: suspend (String) -> T): T {
        val database = databases.next()
        return withPool(endpoint.uri) { pool ->
            pool.ask("drop database if exists $database")
            pool.ask("create database $database")
            try {
                block(database)
            } finally {
                pool.ask("drop database if exists $database")
            }
        }
    }

    /** A raw driver pool of [size] connections onto [uri], closed however [block] ends. */
    suspend fun <T> withPool(
        uri: String,
        size: Int = 2,
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
                ).with(PoolOptions().setMaxSize(size))
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
