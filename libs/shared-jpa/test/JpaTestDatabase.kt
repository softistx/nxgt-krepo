package com.strange.jpa

import com.strange.testing.containers.PostgresEndpoint
import com.strange.testing.containers.postgresContainer
import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Postgres the integration specs talk to: one started for this run, unless `POSTGRES_TEST_URI`
 * and its two credentials name a server that is already up.
 *
 * **Each spec gets a schema of its own**, created before it and dropped after it, which is this
 * library's version of the per-spec database `shared-mongo` uses and the namespace `shared-redis`
 * uses. It matters most when a run is pointed at a real server: a spec that creates its tables in
 * `public` would be writing into whatever else lives there, and `create-drop` would then take that
 * with it on the way out.
 *
 * The schema is managed with the Vert.x client directly rather than through Hibernate, because
 * Hibernate needs the schema to exist before it can be told to use it.
 */
internal object JpaTestDatabase {
    private val postgres = postgresContainer()

    private val schemas = AtomicInteger()

    val endpoint: PostgresEndpoint get() = requireNotNull(postgres.endpoint) { postgres.describe() }

    /** Whether a server answered — checked once, so a machine without one skips instead of hanging. */
    val available: Boolean by lazy {
        postgres.available && runCatching { runBlocking { withClient { it.ask("select 1") } } }.isSuccess
    }

    fun connectOptions(): PgConnectOptions =
        PgConnectOptions
            .fromUri(endpoint.uri)
            .setUser(endpoint.username)
            .setPassword(endpoint.password)

    /**
     * Runs [block] against a schema no other spec is using, and drops it afterwards.
     *
     * `CASCADE` on the way out, because by then the schema holds whatever tables the spec asked
     * Hibernate to create in it.
     */
    suspend fun <T> withSchema(block: suspend (String) -> T): T {
        val schema = "shared_jpa_test_${schemas.incrementAndGet()}"
        return withClient { client ->
            client.ask("drop schema if exists $schema cascade")
            client.ask("create schema $schema")
            try {
                block(schema)
            } finally {
                client.ask("drop schema if exists $schema cascade")
            }
        }
    }

    /** A pool and the Vert.x behind it, both closed however [block] ends. */
    private suspend fun <T> withClient(block: suspend (Pool) -> T): T {
        val vertx = Vertx.vertx()
        val pool =
            PgBuilder
                .pool()
                .connectingTo(connectOptions())
                .with(PoolOptions().setMaxSize(2))
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
