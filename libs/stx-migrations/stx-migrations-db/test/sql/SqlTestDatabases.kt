package com.softistx.migrations.db.sql

import com.softistx.jpa.Jpa
import com.softistx.jpa.JpaConfig
import com.softistx.jpa.SchemaMode
import com.softistx.testing.containers.TestNames
import com.softistx.testing.containers.mysqlContainer
import com.softistx.testing.containers.postgresContainer
import io.vertx.core.Vertx
import io.vertx.mysqlclient.MySQLBuilder
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking

/**
 * The one entity these specs map, and the only reason it exists.
 *
 * `Jpa.connect` refuses to build a factory with no entity — *"a mapping that maps nothing"* — and the
 * SQL ledger has none: it writes its two tables by hand and reads them by hand. So a migration-only
 * application still hands `SqlMigrations` a `Jpa`, and a `Jpa` still needs something to map. This is
 * that something, and nothing ever queries it.
 *
 * `open` and with no constructor parameters, written out rather than taken from the `noArg` and
 * `allOpen` compiler plugins: this module turns neither on, because one test fixture is not a reason
 * to put two compiler plugins on a library that has no `@Entity` in `src/` at all.
 */
@Entity
@Table(name = "stx_migrations_probe")
open class Probe {
    @Id
    open var id: Long = 0
}

/**
 * PostgreSQL, with a schema per spec — created before it and dropped `cascade` after it.
 *
 * The per-spec schema is `stx-jpa`'s habit and it does a second job here: it is also what proves the
 * ledger qualifies its own tables, since an unqualified `create table` on a borrowed connection lands
 * in `public` and would be found by nothing this harness drops.
 */
internal object PostgresTestDatabase {
    private val postgres = postgresContainer()
    private val schemas = TestNames("stx_migrations_test", separator = "_")

    val available: Boolean by lazy {
        postgres.available && runCatching { runBlocking { withClient { it.ask("select 1") } } }.isSuccess
    }

    suspend fun withJpa(
        poolSize: Int = 4,
        block: suspend (Jpa) -> Unit,
    ) {
        val endpoint = postgres.requireEndpoint()
        val schema = schemas.next()
        withClient { client ->
            client.ask("drop schema if exists $schema cascade")
            client.ask("create schema $schema")
            try {
                Jpa
                    .connect(
                        JpaConfig(
                            uri = endpoint.uri,
                            username = endpoint.username,
                            password = endpoint.password,
                            schema = schema,
                            schemaMode = SchemaMode.NONE,
                            poolSize = poolSize,
                        ),
                        listOf(Probe::class),
                    ).use { block(it) }
            } finally {
                client.ask("drop schema if exists $schema cascade")
            }
        }
    }

    private suspend fun <T> withClient(block: suspend (Pool) -> T): T {
        val endpoint = postgres.requireEndpoint()
        val vertx = Vertx.vertx()
        val pool =
            PgBuilder
                .pool()
                .connectingTo(
                    PgConnectOptions.fromUri(endpoint.uri).setUser(endpoint.username).setPassword(endpoint.password),
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

/**
 * MySQL, with a database per spec — in MySQL a schema and a database are the same thing, so the
 * per-spec schema above is a per-spec database here.
 *
 * MySQL is not a formality. It is the second dialect this ledger is written for and the only witness
 * to the two facts `SqlDialect` exists for: `?` rather than `$1`, and `on duplicate key update`
 * rather than `on conflict do nothing`. A ledger verified on Postgres alone would compile against
 * MySQL and refuse every statement it sent.
 */
internal object MysqlTestDatabase {
    private const val ATTEMPTS = 5
    private const val INTERVAL_MILLIS = 2_000L

    private val mysql = mysqlContainer()
    private val databases = TestNames("stx_migrations_test", separator = "_")

    val available: Boolean by lazy {
        mysql.available && answers()
    }

    /**
     * Tried more than once, for the reason `stx-jpa`'s harness gives: a MySQL that has just logged
     * *ready for connections* can still drop the first connection it is offered, and that failure
     * reads exactly like an authentication failure. One retry tells them apart.
     */
    private fun answers(): Boolean {
        repeat(ATTEMPTS) { attempt ->
            if (runCatching { runBlocking { withClient(mysql.requireEndpoint().uri) { it.ask("select 1") } } }.isSuccess) {
                return true
            }
            if (attempt < ATTEMPTS - 1) Thread.sleep(INTERVAL_MILLIS)
        }
        return false
    }

    suspend fun withJpa(
        poolSize: Int = 4,
        block: suspend (Jpa) -> Unit,
    ) {
        val endpoint = mysql.requireEndpoint()
        val database = databases.next()
        withClient(endpoint.uri) { client ->
            client.ask("drop database if exists $database")
            client.ask("create database $database")
            try {
                Jpa
                    .connect(
                        JpaConfig(
                            uri = endpoint.uri.substringBeforeLast('/') + "/" + database,
                            username = endpoint.username,
                            password = endpoint.password,
                            schemaMode = SchemaMode.NONE,
                            poolSize = poolSize,
                        ),
                        listOf(Probe::class),
                    ).use { block(it) }
            } finally {
                client.ask("drop database if exists $database")
            }
        }
    }

    private suspend fun <T> withClient(
        uri: String,
        block: suspend (Pool) -> T,
    ): T {
        val endpoint = mysql.requireEndpoint()
        val vertx = Vertx.vertx()
        val pool =
            MySQLBuilder
                .pool()
                .connectingTo(
                    MySQLConnectOptions.fromUri(uri).setUser(endpoint.username).setPassword(endpoint.password),
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
