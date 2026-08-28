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
import com.strange.jpa.query.criteria
import com.strange.jpa.query.mutate
import com.strange.jpa.query.nativeMutate
import com.strange.jpa.query.nativeQuery
import com.strange.jpa.query.query
import jakarta.persistence.LockModeType
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Selection
import kotlinx.coroutines.future.await
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.stage.Stage

/**
 * A Hibernate Reactive session with Kotlin's calling convention: every operation suspends, and none
 * of them hands back a `CompletionStage`.
 *
 * ```kotlin
 * jpa.transaction { session ->
 *     val order = session.get<Order>(id)
 *     order.status = Shipped
 *     session.flush()
 * }
 * ```
 *
 * **A wrapper rather than extension functions, and not by preference.** `persist`, `merge`,
 * `remove`, `refresh` and `flush` are all members of `Stage.Session` already, and a member always
 * beats an extension of the same name and arity — a `suspend fun Stage.Session.flush()` compiles and
 * is then unreachable, with `session.flush()` still resolving to the one that returns
 * `CompletionStage<Void>`. There is a spec-shaped proof of that in this library's history; the only
 * way to keep the JPA vocabulary *and* suspend is to be a different receiver.
 *
 * Everything this does not wrap is on [raw]. The wrapper is a convenience over Hibernate's API, not
 * a fence around it.
 *
 * The confinement rule is unchanged and still the important one: this belongs to the Vert.x context
 * that opened it, it must not block, and it does not outlive the block it was handed to.
 */
class JpaSession internal constructor(
    /** The session underneath, for everything not wrapped here. */
    val raw: Stage.Session,
) {
    /** Whether the session is still usable. False once the block that owns it has ended. */
    val isOpen: Boolean get() = raw.isOpen

    // ─── By identifier ────────────────────────────────────────────────────────

    /** By id, or null — which is what the database answers, and what Hibernate's own signature hides. */
    suspend inline fun <reified T : Any> find(id: Any): T? = raw.find(T::class.java, id).await()

    /** By id, or [JpaNotFoundException]. For a caller with nothing sensible to do about a missing row. */
    suspend inline fun <reified T : Any> get(id: Any): T = find<T>(id) ?: throw JpaNotFoundException(T::class, id)

    /** By id, with a lock taken as it is read. */
    suspend inline fun <reified T : Any> find(
        id: Any,
        lock: LockModeType,
    ): T? = raw.find(T::class.java, id, lock).await()

    // ─── Writing ──────────────────────────────────────────────────────────────

    /** Makes new instances managed. They are written when the session flushes, not here. */
    suspend fun persist(vararg entities: Any) {
        raw.persist(*entities).await()
    }

    /** Copies a detached instance's state onto the managed one, and answers with that. */
    suspend fun <T : Any> merge(entity: T): T = raw.merge(entity).await()

    /** Deletes managed instances. */
    suspend fun remove(vararg entities: Any) {
        raw.remove(*entities).await()
    }

    /** Re-reads them, discarding whatever this session had. */
    suspend fun refresh(vararg entities: Any) {
        raw.refresh(*entities).await()
    }

    /** Takes a database lock on an instance this session already has. */
    suspend fun lock(
        entity: Any,
        mode: LockModeType,
    ) {
        raw.lock(entity, mode).await()
    }

    /**
     * Writes everything pending, now.
     *
     * A transaction flushes on its way out, so this is for ordering rather than for saving: a bulk
     * `mutate` that has to see what the session has already changed needs it, and so does anything
     * that wants a constraint violation *here* rather than at commit. Outside a transaction it is
     * the only thing that writes at all.
     */
    suspend fun flush() {
        raw.flush().await()
    }

    // ─── Querying ─────────────────────────────────────────────────────────────

    /** An HQL query returning [R] — an entity, or a projection. */
    inline fun <reified R : Any> query(hql: String): JpaQuery<R> = raw.query(hql)

    /** A query built from the entity's own properties instead of an HQL string. */
    inline fun <reified R : Any> select(noinline block: SelectScope<R>.() -> Unit = {}): SelectScope<R> = raw.select(block)

    /** A query over [R]'s entity returning something else — a summary, one column, a count. */
    inline fun <reified E : Any, reified R : Any> project(noinline block: ProjectScope<E, R>.() -> Selection<R>): ProjectScope<E, R> =
        raw.project(block)

    /** SQL, for what HQL cannot say. Remember it is not schema-qualified for you. */
    inline fun <reified R : Any> nativeQuery(sql: String): JpaQuery<R> = raw.nativeQuery(sql)

    /** A bulk `update` built from the entity's own properties. */
    inline fun <reified R : Any> update(noinline block: UpdateScope<R>.() -> Unit): UpdateScope<R> = updateOn(raw, R::class, block)

    /** A bulk `delete`, the same way. */
    inline fun <reified R : Any> delete(): DeleteScope<R> = deleteOn(raw, R::class)

    /**
     * Hibernate's criteria builder, for a query written against the Criteria API directly.
     *
     * The way out of the DSL, for the queries it has no spelling for — subqueries, set operations,
     * window functions, `insert … select`. What comes back runs through [query] or [mutate], so a
     * criteria built by hand still ends in a suspending terminal rather than a `CompletionStage`.
     */
    val criteria: HibernateCriteriaBuilder get() = raw.criteria

    /** A criteria query, run through this module's terminals. */
    fun <R> query(criteria: CriteriaQuery<R>): JpaQuery<R> = raw.query(criteria)

    /** A criteria `update`. */
    fun mutate(criteria: CriteriaUpdate<*>): JpaMutation = raw.mutate(criteria)

    /** A criteria `delete`. */
    fun mutate(criteria: CriteriaDelete<*>): JpaMutation = raw.mutate(criteria)

    /** A criteria `insert` — `insert … select`, or `insert … values`. */
    fun mutate(criteria: JpaCriteriaInsert<*>): JpaMutation = raw.mutate(criteria)

    /** A bulk HQL `update` or `delete`. */
    fun mutate(hql: String): JpaMutation = raw.mutate(hql)

    /** The same in SQL. */
    fun nativeMutate(sql: String): JpaMutation = raw.nativeMutate(sql)
}
