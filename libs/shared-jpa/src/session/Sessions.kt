package com.strange.jpa.session

import com.strange.jpa.Jpa
import kotlinx.coroutines.future.await
import org.hibernate.reactive.stage.Stage

/**
 * Runs [block] in a session, and closes it afterwards.
 *
 * ```kotlin
 * val order = jpa.session { session -> session.find(Order::class.java, id).await() }
 * ```
 *
 * **The block runs on a Vert.x event loop, so it must not block.** That is not this library's rule
 * but the driver's, and it is the price of never parking a thread on a query. Work that blocks —
 * a file, an HTTP call, anything CPU-bound — belongs outside the block, or inside a
 * `withContext(Dispatchers.IO)` that does not touch the session.
 */
suspend fun <T> Jpa.session(block: suspend (Stage.Session) -> T): T {
    val caller = callerContext()
    return factory.withSession { session -> confined(caller) { block(session) } }.await()
}

/**
 * Runs [block] in a session with a transaction around it, committing when it returns and rolling
 * back when it throws.
 *
 * ```kotlin
 * jpa.transaction { session -> session.persist(order).await() }
 * ```
 *
 * The same rule as [session] applies, and more sharply: a blocked event loop inside a transaction
 * holds a database connection as well as the loop.
 */
suspend fun <T> Jpa.transaction(block: suspend (Stage.Session) -> T): T {
    val caller = callerContext()
    return factory.withTransaction { session -> confined(caller) { block(session) } }.await()
}

/**
 * The same as [session], without a persistence context.
 *
 * A stateless session does no dirty checking, keeps no first-level cache and cascades nothing —
 * which makes it the right one for a bulk import or a report reading a million rows, and the wrong
 * one everywhere else. What looks like a faster session is a session that has stopped doing the
 * work an ORM is for.
 */
suspend fun <T> Jpa.statelessSession(block: suspend (Stage.StatelessSession) -> T): T {
    val caller = callerContext()
    return factory.withStatelessSession { session -> confined(caller) { block(session) } }.await()
}

/** A stateless session with a transaction around it. See [statelessSession] for when to want one. */
suspend fun <T> Jpa.statelessTransaction(block: suspend (Stage.StatelessSession) -> T): T {
    val caller = callerContext()
    return factory.withStatelessTransaction { session -> confined(caller) { block(session) } }.await()
}
