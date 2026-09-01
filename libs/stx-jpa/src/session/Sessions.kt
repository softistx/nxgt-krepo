package com.softistx.jpa.session

import com.softistx.jpa.Jpa
import kotlinx.coroutines.future.await

/**
 * Runs [block] in a session, and closes it afterwards.
 *
 * ```kotlin
 * val order = jpa.session { session -> session.find<Order>(id) }
 * ```
 *
 * **The block runs on a Vert.x event loop, so it must not block.** That is not this library's rule
 * but the driver's, and it is the price of never parking a thread on a query. Work that blocks —
 * a file, an HTTP call, anything CPU-bound — belongs outside the block, or inside a
 * `withContext(Dispatchers.IO)` that does not touch the session.
 *
 * **Nothing is flushed here.** A session flushes at the end of a unit of work if and only if there
 * is a transaction, so a `persist` or a change to a loaded entity inside this block is discarded
 * without a word when the block returns. That is Hibernate's rule and it is quiet enough to be worth
 * repeating: this is for reads. Use [transaction] to write, or call `session.flush()` and
 * accept that each statement is then its own transaction.
 */
suspend fun <T> Jpa.session(block: suspend (JpaSession) -> T): T {
    val caller = callerContext()
    return factory.withSession { session -> confined(caller) { block(JpaSession(session)) } }.await()
}

/**
 * Runs [block] in a session with a transaction around it, committing when it returns and rolling
 * back when it throws.
 *
 * ```kotlin
 * jpa.transaction { session -> session.persist(order) }
 * ```
 *
 * The same rule as [session] applies, and more sharply: a blocked event loop inside a transaction
 * holds a database connection as well as the loop.
 */
suspend fun <T> Jpa.transaction(block: suspend (JpaSession) -> T): T {
    val caller = callerContext()
    return factory.withTransaction { session -> confined(caller) { block(JpaSession(session)) } }.await()
}

/**
 * The same as [session], without a persistence context.
 *
 * A stateless session does no dirty checking, keeps no first-level cache and cascades nothing —
 * which makes it the right one for a bulk import or a report reading a million rows, and the wrong
 * one everywhere else. What looks like a faster session is a session that has stopped doing the
 * work an ORM is for.
 */
suspend fun <T> Jpa.statelessSession(block: suspend (JpaStatelessSession) -> T): T {
    val caller = callerContext()
    return factory.withStatelessSession { session -> confined(caller) { block(JpaStatelessSession(session)) } }.await()
}

/** A stateless session with a transaction around it. See [statelessSession] for when to want one. */
suspend fun <T> Jpa.statelessTransaction(block: suspend (JpaStatelessSession) -> T): T {
    val caller = callerContext()
    return factory
        .withStatelessTransaction { session -> confined(caller) { block(JpaStatelessSession(session)) } }
        .await()
}
