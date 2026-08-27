package com.strange.jpa.query

import com.strange.jpa.JpaNotFoundException
import kotlinx.coroutines.future.await
import org.hibernate.reactive.stage.Stage

/**
 * By id, or null.
 *
 * The type comes from the call site — `session.find<Order>(id)` — rather than from a `Class`
 * argument, and the result is nullable because that is what the database answers with. Hibernate's
 * own `find` returns a `CompletionStage<T>` whose `T` is a platform type, so nothing warns a caller
 * that it may be null; this does.
 */
suspend inline fun <reified T : Any> Stage.Session.find(id: Any): T? = find(T::class.java, id).await()

/** By id, or [JpaNotFoundException]. For a caller with nothing sensible to do about a missing row. */
suspend inline fun <reified T : Any> Stage.Session.get(id: Any): T = find<T>(id) ?: throw JpaNotFoundException(T::class, id)

/** By id, or null — the stateless spelling, which returns a detached instance and caches nothing. */
suspend inline fun <reified T : Any> Stage.StatelessSession.find(id: Any): T? = get(T::class.java, id).await()

/** By id, or [JpaNotFoundException]. */
suspend inline fun <reified T : Any> Stage.StatelessSession.get(id: Any): T = find<T>(id) ?: throw JpaNotFoundException(T::class, id)
