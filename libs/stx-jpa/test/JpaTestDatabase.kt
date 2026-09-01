package com.softistx.jpa

import com.softistx.testing.containers.PostgresEndpoint
import com.softistx.testing.containers.TestNames
import com.softistx.testing.containers.postgresContainer
import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

/**
 * The Postgres the integration specs talk to: one started for this run, unless `POSTGRES_TEST_URI`
 * and its two credentials name a server that is already up.
 *
 * **Each spec gets a schema of its own**, created before it and dropped after it, which is this
 * library's version of the per-spec database `stx-mongo` uses and the namespace `stx-redis`
 * uses. It matters most when a run is pointed at a real server: a spec that creates its tables in
 * `public` would be writing into whatever else lives there, and `create-drop` would then take that
 * with it on the way out.
 *
 * The schema is managed with the Vert.x client directly rather than through Hibernate, because
 * Hibernate needs the schema to exist before it can be told to use it.
 */
internal object JpaTestDatabase {
    private val postgres = postgresContainer()

    /**
     * A schema per call, and one no other run will pick — see [TestNames].
     *
     * `stx_jpa_test`, not the name this and [MySqlTestDatabase] both used to issue: two
     * independent counters spelling one prefix means the third schema of each run is two
     * different things the moment both are pointed at a single server.
     */
    private val schemas = TestNames("stx_jpa_test", separator = "_")

    val endpoint: PostgresEndpoint get() = postgres.requireEndpoint()

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
        val schema = schemas.next()
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

    /** A [Jpa] over [schema] that the caller closes — for the specs that are about closing. */
    suspend fun connect(
        schema: String,
        vararg entities: KClass<*>,
    ): Jpa =
        Jpa.connect(
            JpaConfig(
                uri = endpoint.uri,
                username = endpoint.username,
                password = endpoint.password,
                schema = schema,
                schemaMode = SchemaMode.CREATE_DROP,
            ),
            entities.toList(),
        )

    /**
     * A [Jpa] over a schema of its own, with the tables for [entities] created in it and dropped
     * with it.
     *
     * This is what a spec in this module asks for; [withSchema] is underneath it, for the two specs
     * that want the schema without the factory.
     */
    suspend fun <T> withJpa(
        vararg entities: KClass<*>,
        block: suspend (Jpa) -> T,
    ): T =
        withSchema { schema ->
            Jpa
                .connect(
                    JpaConfig(
                        uri = endpoint.uri,
                        username = endpoint.username,
                        password = endpoint.password,
                        schema = schema,
                        schemaMode = SchemaMode.CREATE_DROP,
                    ),
                    entities.toList(),
                ).use { block(it) }
        }

    /**
     * The type Postgres reports for a column, or null when there is no such column.
     *
     * This is the only honest witness to what a converter writes. A spec that persists a value and
     * reads it back is asking one mapping to agree with itself, and a mapping that stores the whole
     * object as bytes agrees with itself perfectly.
     */
    suspend fun columnType(
        schema: String,
        table: String,
        column: String,
    ): String? =
        withClient { client ->
            client
                .preparedQuery(
                    """
                    select data_type from information_schema.columns
                    where table_schema = $1 and table_name = $2 and column_name = $3
                    """.trimIndent(),
                ).execute(Tuple.of(schema, table, column))
                .toCompletionStage()
                .await()
                .firstOrNull()
                ?.getString("data_type")
        }

    /** Every column Hibernate exported for a table, so a spec can assert names it did not choose. */
    suspend fun columns(
        schema: String,
        table: String,
    ): List<String> =
        withClient { client ->
            client
                .preparedQuery(
                    """
                    select column_name from information_schema.columns
                    where table_schema = $1 and table_name = $2 order by column_name
                    """.trimIndent(),
                ).execute(Tuple.of(schema, table))
                .toCompletionStage()
                .await()
                .map { it.getString("column_name") }
        }

    /** How wide Postgres made a character column, or null when it is not one. */
    suspend fun columnMaxLength(
        schema: String,
        table: String,
        column: String,
    ): Int? =
        withClient { client ->
            client
                .preparedQuery(
                    """
                    select character_maximum_length from information_schema.columns
                    where table_schema = $1 and table_name = $2 and column_name = $3
                    """.trimIndent(),
                ).execute(Tuple.of(schema, table, column))
                .toCompletionStage()
                .await()
                .firstOrNull()
                ?.getInteger("character_maximum_length")
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
