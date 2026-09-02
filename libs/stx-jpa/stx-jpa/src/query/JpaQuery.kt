package com.softistx.jpa.query

import com.softistx.jpa.JpaNoResultException
import com.softistx.jpa.JpaNonUniqueResultException
import jakarta.persistence.EntityGraph
import jakarta.persistence.NoResultException
import jakarta.persistence.NonUniqueResultException
import kotlinx.coroutines.future.await
import org.hibernate.reactive.stage.Stage
import org.intellij.lang.annotations.Language

/**
 * An HQL selection, built up in Kotlin and awaited rather than chained.
 *
 * ```kotlin
 * jpa.session { session ->
 *     session
 *         .query<Order>("from Order where customer = :customer order by placedAt desc")
 *         .parameters("customer" to id)
 *         .limit(20)
 *         .list()
 * }
 * ```
 *
 * Every terminal is `suspend`, so a result is a value and not a `CompletionStage` the caller has to
 * remember to await. The builders are not: they are ordinary calls on Hibernate's own query object,
 * which this holds rather than copies — so a query is configured and then run once, and reusing one
 * after a terminal reruns it with whatever was set last.
 *
 * **Bind parameters; do not interpolate.** A Kotlin template makes `"where name = '$name'"` look as
 * natural as the safe spelling, and it is an injection. [parameter] is the only way values should
 * reach a query here.
 */
class JpaQuery<R>
    @PublishedApi
    internal constructor(
        // Called only when a terminal has to name the query in an exception. A criteria query has no
        // source text, so naming it means rendering the tree back to HQL — worth doing to say which
        // query found nothing, and not worth doing for every query that finds something.
        private val describe: () -> String,
        private val query: Stage.SelectionQuery<R>,
    ) {
        /** Binds one named parameter — `:name` in the HQL, without the colon here. */
        fun parameter(
            name: String,
            value: Any?,
        ): JpaQuery<R> = apply { query.setParameter(name, value) }

        /** The same, for all of them at once: `parameters("customer" to id, "since" to at)`. */
        fun parameters(vararg values: Pair<String, Any?>): JpaQuery<R> =
            apply { values.forEach { (name, value) -> query.setParameter(name, value) } }

        /** The fetch plan to load with — see [com.softistx.jpa.criteria.entityGraph]. */
        fun plan(graph: EntityGraph<R>): JpaQuery<R> = apply { query.setPlan(graph) }

        /** At most this many rows. */
        fun limit(count: Int): JpaQuery<R> = apply { query.setMaxResults(count) }

        /** Skips this many rows first. Meaningless without an `order by`, since nothing else fixes the order. */
        fun offset(count: Int): JpaQuery<R> = apply { query.setFirstResult(count) }

        /**
         * Marks the results read-only, which is worth doing whenever they are.
         *
         * A stateful session keeps a snapshot of every entity it loads so it can work out at flush time
         * what changed. Read-only results skip the snapshot: half the memory, and no dirty check.
         */
        fun readOnly(readOnly: Boolean = true): JpaQuery<R> = apply { query.setReadOnly(readOnly) }

        /** Every matching row. */
        suspend fun list(): List<R> = query.resultList.await()

        /** The first row, or null — [limit] of one, so the database stops looking after it. */
        suspend fun first(): R? = limit(1).list().firstOrNull()

        /** The one row there is, or [JpaNoResultException] / [JpaNonUniqueResultException]. */
        suspend fun single(): R = translated { query.singleResult.await() }

        /** The one row there is, or null. More than one is still a [JpaNonUniqueResultException]. */
        suspend fun singleOrNull(): R? = translated { query.singleResultOrNull.await() }

        /**
         * How many rows this would return, without returning them.
         *
         * Hibernate rewrites the selection into a count, so [limit] and [offset] are not applied — this
         * is the total a pager needs, not the size of the page.
         */
        suspend fun count(): Long = query.resultCount.await()

        private suspend fun <T> translated(block: suspend () -> T): T =
            try {
                block()
            } catch (failure: NoResultException) {
                throw JpaNoResultException(describe(), failure)
            } catch (failure: NonUniqueResultException) {
                throw JpaNonUniqueResultException(describe(), failure)
            }
    }

/**
 * An HQL query returning [R], against a session or a stateless one.
 *
 * `R` is the shape of a row, which is the entity for `from Order` and something else entirely for a
 * projection — `query<Long>("select count(o) from Order o")`, `query<String>("select o.reference …")`.
 */
inline fun <reified R> Stage.QueryProducer.query(
    @Language("HQL") hql: String,
): JpaQuery<R> = JpaQuery({ hql }, createQuery(hql, R::class.java))
