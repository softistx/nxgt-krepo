package com.strange.jpa.query

import kotlinx.coroutines.future.await
import org.hibernate.reactive.stage.Stage

/**
 * A bulk `update` or `delete`, written in HQL and awaited.
 *
 * ```kotlin
 * jpa.transaction { session ->
 *     session
 *         .mutation("delete from Order where placedAt < :before")
 *         .parameter("before", cutoff)
 *         .execute()
 * }
 * ```
 *
 * **It goes straight to the database, past everything the session knows.** No cascade fires, no
 * `@PreRemove` runs, and an entity already loaded in this session keeps the values it had — which is
 * why a bulk mutation belongs at the start of a transaction, or in a session that loads nothing
 * else. That is Hibernate's rule rather than this module's, and it is the reason this is a separate
 * type from [JpaQuery] instead of another terminal on it.
 */
class JpaMutation
    @PublishedApi
    internal constructor(
        private val query: Stage.MutationQuery,
    ) {
        /** Binds one named parameter — `:name` in the HQL, without the colon here. */
        fun parameter(
            name: String,
            value: Any?,
        ): JpaMutation = apply { query.setParameter(name, value) }

        /** The same, for all of them at once. */
        fun parameters(vararg values: Pair<String, Any?>): JpaMutation =
            apply { values.forEach { (name, value) -> query.setParameter(name, value) } }

        /** Runs it, and answers with how many rows it touched. */
        suspend fun execute(): Int = query.executeUpdate().await()
    }

/** A bulk `update` or `delete` in HQL. Use [query] for anything that returns rows. */
fun Stage.QueryProducer.mutation(hql: String): JpaMutation = JpaMutation(createMutationQuery(hql))
