package com.softistx.r2jdbc

import com.softistx.common.lifecycle.CloseGuard
import com.softistx.r2jdbc.sql.Sql
import io.vertx.core.Vertx
import io.vertx.sqlclient.Pool
import java.util.concurrent.TimeUnit

/**
 * A connection pool, its Vert.x, and the server it is pointed at.
 *
 * ```kotlin
 * val db = R2jdbc.connect(R2jdbcConfig(uri = System.getenv("POSTGRES_URI"), username = …))
 *
 * val rows = db.sql.query("select id, title from books where author_id = ?", authorId)
 * db.transaction { sql -> sql.execute("update books set title = ? where id = ?", title, id) }
 * ```
 *
 * **What this deliberately does not have** is the list that makes it worth existing beside
 * `stx-jpa`: no persistence context, no dirty checking, no cascade, no lazy loading, no entity
 * mapping and no criteria DSL. Six of the seven traps `docs/jpa-mapping.md` records have one cause —
 * a persistence context managing an object graph across a reactive boundary — and a library that
 * maps rows to values has none of them, not because it is cleverer but because it does less. A
 * caller who wants an ORM should use the ORM.
 *
 * **The Vert.x instance is ours unless one is handed in**, which is the rule every integration in
 * this repo follows: a Ktor application that already runs a loop passes it, and closing this then
 * closes the pool and leaves the loop alone.
 */
class R2jdbc internal constructor(
    /** The pool itself, for everything this module has not wrapped. */
    val pool: Pool,
    val config: R2jdbcConfig,
    internal val backend: Backend,
    private val vertx: Vertx,
    private val ownsVertx: Boolean,
) : AutoCloseable {
    private val guard = CloseGuard()

    /**
     * A statement run on the pool: one connection taken for it and handed straight back.
     *
     * The right thing for a single query, and the wrong thing for two that have to see each other —
     * a temporary table, a `set`, MySQL's `last_insert_id()`. Those want [connection], which pins
     * one, or [transaction], which pins one and wraps it.
     */
    val sql: Sql = Sql(pool, backend)

    val isOpen: Boolean get() = !guard.isClosed

    /**
     * Closes the pool, and the Vert.x behind it when this opened it. Calling it again does nothing.
     *
     * It blocks, briefly and boundedly. `close()` cannot suspend, and returning before the pool is
     * actually closed leaves connections open on the server for as long as they take to time out
     * there. It is called from application shutdown and container teardown, never from an event
     * loop.
     */
    override fun close() =
        guard.once {
            runCatching {
                pool
                    .close()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(CLOSE_SECONDS, TimeUnit.SECONDS)
            }
            if (ownsVertx) {
                runCatching {
                    vertx
                        .close()
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(CLOSE_SECONDS, TimeUnit.SECONDS)
                }
            }
        }

    companion object {
        private const val CLOSE_SECONDS = 5L

        /**
         * Opens the pool [config] describes.
         *
         * **Nothing connects here unless [R2jdbcConfig.schema] is set.** On the default the pool
         * opens its first connection when something asks for one, so a wrong password surfaces at
         * the first statement rather than here — the same bargain `Jpa.connect` makes. Naming a
         * schema changes it: the schema is proved to exist now, because the alternative is every
         * later query failing with `relation "…" does not exist` and no hint that the schema was the
         * reason. `SchemaTest` pins both halves.
         */
        suspend fun connect(
            config: R2jdbcConfig,
            vertx: Vertx? = null,
        ): R2jdbc {
            val backend = Backend.of(config.uri)
            val own = vertx == null
            val instance = vertx ?: Vertx.vertx()

            try {
                val db = R2jdbc(backend.pool(instance, config), config, backend, instance, own)
                config.schema?.let { db.proveSchema(it) }
                return db
            } catch (failure: Throwable) {
                // A Vert.x this created and could not hand back would otherwise keep its event loops
                // alive for the life of the JVM.
                if (own) runCatching { instance.close() }
                throw failure
            }
        }
    }

    /**
     * Asks the server whether [schema] is there, and refuses to hand back a pool if it is not.
     *
     * Neither half of this can be left to the connect handler. A handler cannot reject a connection
     * — `Backend.pool` says why — and on Postgres there would be nothing to reject anyway:
     * `set search_path to "nope"` **succeeds**, against a schema that does not exist, and every
     * later query then fails with `relation "…" does not exist`. So the question is asked directly,
     * of a catalog both servers spell the same way.
     */
    private suspend fun proveSchema(schema: String) {
        val found =
            runCatching {
                sql.query("select schema_name from information_schema.schemata where schema_name = ?", schema)
            }.getOrElse { failure ->
                close()
                throw R2jdbcException("could not reach ${config.uri}", failure)
            }
        if (found.isEmpty()) {
            close()
            throw R2jdbcException(
                "schema '$schema' does not exist on ${config.uri} — every connection is put in it as it " +
                    "opens, so nothing this pool handed out would have found a table",
            )
        }
    }
}
