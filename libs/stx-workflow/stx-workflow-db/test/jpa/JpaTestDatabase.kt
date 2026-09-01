package com.strange.workflow.jpa

import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import com.strange.jpa.SchemaMode
import com.strange.testing.containers.TestNames
import com.strange.testing.containers.postgresContainer
import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking

/**
 * The Postgres the JPA specs talk to: the workspace's own when `POSTGRES_TEST_URI` names it, one
 * started for the run otherwise.
 *
 * **Postgres is the only database these specs run against**, and that is a deliberate limit rather
 * than an oversight. [JpaWorkflowStore] writes no SQL — every statement in it is HQL, and Hibernate
 * Reactive picks its dialect from the URI — so DB2 and MySQL are supported by construction and
 * unverified here. One dialect proves the store's *logic*: that the conditional update matches one
 * row, that the lease pair keeps a second holder out, that the due-time predicate says the same
 * thing as Redis's score. What a second dialect would prove is Hibernate's business.
 *
 * A schema per spec, created before it and dropped after it — `stx-jpa`'s habit, for its reason: a
 * run pointed at a real server must not create `stx_workflow_instance` in whatever `public` already
 * holds, nor take that with it when `create-drop` cleans up.
 */
internal object JpaTestDatabase {
    private val postgres = postgresContainer()
    private val schemas = TestNames("stx_workflow_test", separator = "_")

    val available: Boolean by lazy {
        postgres.available && runCatching { runBlocking { withClient { it.ask("select 1") } } }.isSuccess
    }

    /**
     * A [Jpa] over a schema of its own, with `stx_workflow_instance` created in it and dropped with
     * it, and closed however [block] ends.
     */
    suspend fun withJpa(block: suspend (Jpa) -> Unit) {
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
                            schemaMode = SchemaMode.CREATE_DROP,
                        ),
                        listOf(WorkflowInstanceRow::class),
                    ).use { block(it) }
            } finally {
                client.ask("drop schema if exists $schema cascade")
            }
        }
    }

    /** A pool and the Vert.x behind it, both closed however [block] ends. */
    private suspend fun <T> withClient(block: suspend (Pool) -> T): T {
        val endpoint = postgres.requireEndpoint()
        val vertx = Vertx.vertx()
        val pool =
            PgBuilder
                .pool()
                .connectingTo(
                    PgConnectOptions
                        .fromUri(endpoint.uri)
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
