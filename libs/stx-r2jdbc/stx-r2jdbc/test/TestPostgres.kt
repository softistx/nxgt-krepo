package com.softistx.r2jdbc

import com.softistx.testing.containers.PostgresEndpoint
import com.softistx.testing.containers.TestNames
import com.softistx.testing.containers.postgresContainer
import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking

/**
 * The Postgres this module's specs talk to: one started for this run, unless `POSTGRES_TEST_URI`
 * and its two credentials name a server that is already up.
 *
 * **Each spec gets a schema of its own**, created before it and dropped after it — the rule
 * `stx-jpa` established, and it matters most when a run is pointed at a real server.
 *
 * [withPool] is a *raw* Vert.x pool on purpose. The specs under `test/driver/` are the ones that
 * establish what the driver does, and a probe that went through this library's own pool would be
 * asking the design to confirm itself.
 */
internal object TestPostgres {
    private val postgres = postgresContainer()

    /** A schema per call, and one no other run will pick — see [TestNames]. */
    private val schemas = TestNames("stx_r2jdbc_pg", separator = "_")

    val endpoint: PostgresEndpoint get() = postgres.requireEndpoint()

    /** Whether a server answered — checked once, so a machine without one skips instead of hanging. */
    val available: Boolean by lazy {
        postgres.available && runCatching { runBlocking { withPool { it.ask("select 1") } } }.isSuccess
    }

    /** The URI a caller hands this library. */
    fun uri(): String = endpoint.uri

    /** What a spec connects with, pointed at [schema]. */
    fun config(schema: String?): R2jdbcConfig =
        R2jdbcConfig(
            uri = endpoint.uri,
            username = endpoint.username,
            password = endpoint.password,
            schema = schema,
            poolSize = 4,
        )

    /** An [R2jdbc] over a schema of its own, closed with it — what a spec in this module asks for. */
    suspend fun <T> withR2jdbc(block: suspend (R2jdbc) -> T): T = withSchema { schema -> R2jdbc.connect(config(schema)).use { block(it) } }

    /** Runs [block] against a schema no other spec is using, and drops it afterwards. */
    suspend fun <T> withSchema(block: suspend (String) -> T): T {
        val schema = schemas.next()
        return withPool { pool ->
            pool.ask("drop schema if exists $schema cascade")
            pool.ask("create schema $schema")
            try {
                block(schema)
            } finally {
                pool.ask("drop schema if exists $schema cascade")
            }
        }
    }

    /** A raw driver pool of [size] connections, and the Vert.x behind it, both closed however [block] ends. */
    suspend fun <T> withPool(
        size: Int = 2,
        block: suspend (Pool) -> T,
    ): T {
        val vertx = Vertx.vertx()
        val pool =
            PgBuilder
                .pool()
                .connectingTo(
                    PgConnectOptions
                        .fromUri(endpoint.uri)
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
