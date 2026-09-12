package com.softistx.jpa.session

import com.softistx.jpa.JpaNotFoundException
import com.softistx.jpa.query.JpaQuery
import com.softistx.jpa.query.nativeQuery
import com.softistx.jpa.query.query
import jakarta.persistence.EntityGraph
import kotlinx.coroutines.future.await
import org.hibernate.reactive.stage.Stage
import org.intellij.lang.annotations.Language

/**
 * The same convention over a stateless session: everything suspends, nothing returns a stage.
 *
 * The vocabulary is Hibernate's own and it is deliberately not the stateful one. There is no
 * persistence context here, so there is nothing to make managed and nothing to flush: [insert],
 * [update] and [delete] each go to the database when they are called. That is the whole trade — a
 * bulk load holds nothing in memory between rows, and gives up identity, cascades and dirty
 * checking to do it.
 */
class JpaStatelessSession internal constructor(
    /** The session underneath, for everything not wrapped here. */
    override val raw: Stage.StatelessSession,
) : JpaQueries {
    /** Whether the session is still usable. */
    val isOpen: Boolean get() = raw.isOpen

    /** By id, or null. The instance is detached — nothing is watching it. */
    suspend inline fun <reified T : Any> find(id: Any): T? = raw.get(T::class.java, id).await()

    /** By id, or [JpaNotFoundException]. */
    suspend inline fun <reified T : Any> get(id: Any): T = find<T>(id) ?: throw JpaNotFoundException(T::class, id)

    /**
     * By id, loading what [graph] plans.
     *
     * Worth more here than on a stateful session, not less: a stateless one has no persistence
     * context, so nothing can be initialised after the fact at all.
     */
    suspend fun <T : Any> find(
        id: Any,
        graph: EntityGraph<T>,
    ): T? = raw.get(graph, id).await()

    /** The same, or [JpaNotFoundException]. */
    suspend inline fun <reified T : Any> get(
        id: Any,
        graph: EntityGraph<T>,
    ): T = find(id, graph) ?: throw JpaNotFoundException(T::class, id)

    /** Inserts them, one statement each, now. */
    suspend fun insert(vararg entities: Any) {
        raw.insert(*entities).await()
    }

    /** Updates them by identifier, now. */
    suspend fun update(vararg entities: Any) {
        raw.update(*entities).await()
    }

    /** Deletes them by identifier, now. */
    suspend fun delete(vararg entities: Any) {
        raw.delete(*entities).await()
    }

    /** Inserts or updates, letting the database decide which. */
    suspend fun upsert(vararg entities: Any) {
        raw.upsert(*entities).await()
    }

    /** An HQL query returning [R]. */
    inline fun <reified R : Any> query(
        @Language("HQL") hql: String,
    ): JpaQuery<R> = raw.query(hql)

    /** SQL, for what HQL cannot say. */
    inline fun <reified R : Any> nativeQuery(
        @Language("SQL") sql: String,
    ): JpaQuery<R> = raw.nativeQuery(sql)
}
