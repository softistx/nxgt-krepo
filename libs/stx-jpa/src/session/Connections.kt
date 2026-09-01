package com.softistx.jpa.session

import com.softistx.jpa.Jpa
import com.softistx.jpa.JpaMappingException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.hibernate.reactive.common.spi.Implementor
import org.hibernate.reactive.pool.ReactiveConnection
import org.hibernate.reactive.pool.ReactiveConnectionPool

/**
 * Runs [block] on a connection from the pool Hibernate is already using, and gives it back.
 *
 * ```kotlin
 * jpa.connection { connection ->
 *     connection.executeUnprepared("create table if not exists stx_migrations (version bigint primary key)").await()
 * }
 * ```
 *
 * **The escape below the session**, for the two things a session cannot do: DDL, and a statement
 * Hibernate's parameter recogniser would refuse. [nativeMutate][com.softistx.jpa.query.nativeMutate]
 * is documented for *update, insert or delete*, prepares every statement it is given, and reads a
 * literal `?` as an ordinal parameter — so `create index`, a body with two statements in it and a
 * `jsonb ? 'key'` are all outside it. `NativeDdlTest` is where each of those is measured rather than
 * assumed.
 *
 * **It is not a second pool.** [Implementor] is Hibernate Reactive's own integrator SPI —
 * *"allows access to object that can be useful for integrators"* — and the [ReactiveConnectionPool]
 * behind it is the one every session takes its connection from. A `PgBuilder.pool()` beside the
 * factory would be a second set of connections nobody is sizing and nobody is closing; this borrows
 * one of Hibernate's and hands it straight back.
 *
 * **There is no transaction here.** Each statement commits on its own unless [block] opens one with
 * `beginTransaction`, which is the honest default for schema work: on Postgres DDL is transactional
 * and a caller can wrap what it wants wrapped, and on MySQL it is not and a wrapper would be a
 * promise nothing keeps.
 *
 * The connection is closed however [block] ends, under [NonCancellable] — a cancelled coroutine that
 * skipped the close would leak a connection out of a fixed-size pool, and the second one to do it
 * hangs the application at the next `session { }`.
 *
 * **Two statements on one connection must not overlap.** That is [ReactiveConnection]'s own rule,
 * not this library's: *"it is illegal to perform two non-blocking operations concurrently with a
 * single ReactiveConnection"*. A `block` that awaits each call in turn is inside it; one that opens
 * an `async` per statement is not.
 */
suspend fun <T> Jpa.connection(block: suspend (ReactiveConnection) -> T): T {
    val implementor =
        factory as? Implementor
            ?: throw JpaMappingException(
                "this Stage.SessionFactory is not a Hibernate Reactive Implementor, so its connection pool " +
                    "cannot be reached — jpa.connection { } needs the factory Jpa.connect builds",
            )
    val connection =
        implementor.serviceRegistry
            .requireService(ReactiveConnectionPool::class.java)
            .connection
            .await()
    try {
        return block(connection)
    } finally {
        withContext(NonCancellable) { connection.close().await() }
    }
}
