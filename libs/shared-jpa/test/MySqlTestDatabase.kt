package com.strange.jpa

import com.strange.testing.containers.ContainerService
import com.strange.testing.containers.MysqlEndpoint
import com.strange.testing.containers.mysqlContainer
import io.vertx.core.Vertx
import io.vertx.mysqlclient.MySQLBuilder
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
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
    private val mysql = mysqlContainer()

    private val databases = AtomicInteger()

    val endpoint: MysqlEndpoint get() = requireNotNull(mysql.endpoint) { mysql.describe() }

    /** Whether a server answered — checked once, so a machine without one skips instead of hanging. */
    val available: Boolean get() = probe == null

    /**
     * Why this spec is skipped, or `null` when it is not.
     *
     * A skip with no reason has cost an afternoon twice here: a container that timed out under load
     * and a machine without Docker read identically. [ContainerService.describe] answers the first
     * half and this answers the second, because a server can be reachable and still refuse the
     * credentials, and the probe below swallows that.
     */
    val skip: String? get() = probe

    private val probe: String? by lazy {
        if (!mysql.available) {
            mysql.describe()
        } else {
            runCatching { runBlocking { withClient(endpoint.uri) { it.ask("select 1") } } }
                .exceptionOrNull()
                ?.let { "mysql: ${endpoint.uri} answered no query — $it" }
        }
    }

    /**
     * A [Jpa] over a database of its own, with the tables for [entities] created in it and dropped
     * with it.
     */
    suspend fun <T> withJpa(
        vararg entities: KClass<*>,
        block: suspend (Jpa) -> T,
    ): T {
        val database = "shared_jpa_test_${databases.incrementAndGet()}"
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
