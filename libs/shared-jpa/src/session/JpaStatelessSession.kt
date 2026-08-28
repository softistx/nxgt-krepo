package com.strange.jpa.session

import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.dsl.DeleteScope
import com.strange.jpa.dsl.ProjectScope
import com.strange.jpa.dsl.SelectScope
import com.strange.jpa.dsl.UpdateScope
import com.strange.jpa.dsl.deleteOn
import com.strange.jpa.dsl.project
import com.strange.jpa.dsl.select
import com.strange.jpa.dsl.updateOn
import com.strange.jpa.query.JpaMutation
import com.strange.jpa.query.JpaQuery
import com.strange.jpa.query.mutate
import com.strange.jpa.query.nativeMutate
import com.strange.jpa.query.nativeQuery
import com.strange.jpa.query.query
import jakarta.persistence.criteria.Selection
import kotlinx.coroutines.future.await
import org.hibernate.reactive.stage.Stage

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
    val raw: Stage.StatelessSession,
) {
    /** Whether the session is still usable. */
    val isOpen: Boolean get() = raw.isOpen

    /** By id, or null. The instance is detached — nothing is watching it. */
    suspend inline fun <reified T : Any> find(id: Any): T? = raw.get(T::class.java, id).await()

    /** By id, or [JpaNotFoundException]. */
    suspend inline fun <reified T : Any> get(id: Any): T = find<T>(id) ?: throw JpaNotFoundException(T::class, id)

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
    inline fun <reified R : Any> query(hql: String): JpaQuery<R> = raw.query(hql)

    /** A query built from the entity's own properties instead of an HQL string. */
    inline fun <reified R : Any> select(block: SelectScope<R>.() -> Unit): JpaQuery<R> = raw.select(block)

    /** A query over [R]'s entity returning something else — a summary, one column, a count. */
    inline fun <reified E : Any, reified R : Any> project(block: ProjectScope<E, R>.() -> Selection<R>): JpaQuery<R> = raw.project(block)

    /** SQL, for what HQL cannot say. */
    inline fun <reified R : Any> nativeQuery(sql: String): JpaQuery<R> = raw.nativeQuery(sql)

    /** A bulk `update` built from the entity's own properties. */
    inline fun <reified R : Any> update(block: UpdateScope<R>.() -> Unit): JpaMutation = updateOn(raw, block)

    /** A bulk `delete`, the same way. */
    inline fun <reified R : Any> delete(block: DeleteScope<R>.() -> Unit): JpaMutation = deleteOn(raw, block)

    /** A bulk HQL `update` or `delete`. */
    fun mutate(hql: String): JpaMutation = raw.mutate(hql)

    /** The same in SQL. */
    fun nativeMutate(sql: String): JpaMutation = raw.nativeMutate(sql)
}
