package com.strange.jpa.repository

import com.strange.common.page.Page
import com.strange.jpa.dsl.JpaSpec
import com.strange.jpa.dsl.SelectScope
import com.strange.jpa.dsl.eq
import com.strange.jpa.dsl.oneOf
import com.strange.jpa.page.PageRequest
import com.strange.jpa.page.page
import com.strange.jpa.session.JpaSession
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * One entity, as an object.
 *
 * The DSL in `com.strange.jpa.dsl` is the vocabulary; this is a noun that speaks it — the thing a
 * service holds, a test substitutes, and a subclass extends with the two or three queries that are
 * actually specific to an entity. Everything is `open` for exactly that reason, and nothing here has
 * an opinion about *why* a row is being written, which is what keeps it separate from
 * [JpaCrudService].
 *
 * ```kotlin
 * val purchases = jpaRepository(Purchase::id)
 *
 * class PurchaseRepository : JpaRepository<Purchase, Long>(Purchase::class, Purchase::id) {
 *     suspend fun findByBuyer(session: JpaSession, name: String) =
 *         findAll(session) { join(Purchase::customer)[Buyer::name] eq name }
 * }
 * ```
 *
 * **The session is the first argument, not a field.** A Mongo collection is a long-lived object a
 * repository can hold; a Hibernate Reactive session is not — it belongs to the event loop that
 * opened it and does not outlive the block it was handed to, which is the rule this whole library is
 * built around. So a repository is a singleton the container builds once, and the unit of work
 * arrives per call. That is also what lets two repositories share one transaction, which is the
 * ordinary case and the one a repository holding its own session could not serve.
 *
 * **Stateful sessions only.** A [com.strange.jpa.session.JpaStatelessSession] has no persistence
 * context, so `update` would have nothing to merge into and `delete` nothing to cascade from. Bulk
 * loading through a stateless session is a job for the DSL directly.
 *
 * [id] is a property reference rather than a getter function because both halves are needed: the
 * value, for a caller holding an instance, and the *name*, for the queries below that restrict on
 * the identifier column. `Purchase::id` gives both with no `kotlin-reflect` on the classpath.
 *
 * [entity] is a `KClass` because a class cannot have a `reified` type parameter — inside one, `T` is
 * not reifiable and `select<T>()` does not compile, which is the whole reason this needs to be told
 * what it is generic over. A caller does not have to say it twice: [jpaRepository] infers both types
 * from the property reference. A subclass names its entity once, in its `extends` clause.
 */
open class JpaRepository<T : Any, ID : Any>(
    val entity: KClass<T>,
    val id: KProperty1<T, ID>,
) {
    /** The entity's name, for the messages a caller has to write. */
    val name: String get() = entity.simpleName ?: entity.toString()

    /** The identifier of an instance in hand. */
    fun idOf(instance: T): ID = id.get(instance)

    // ─── Reads ────────────────────────────────────────────────────────────────

    open suspend fun findAll(
        session: JpaSession,
        spec: JpaSpec<T>? = null,
    ): List<T> = query(session, spec).list()

    /**
     * One page, cut by keyset. [sort] must end with the identifier, which the DSL enforces.
     *
     * ```kotlin
     * purchases.findPage(session, PageRequest.first(20)) { sortBy(Purchase::id) }
     * ```
     */
    open suspend fun findPage(
        session: JpaSession,
        request: PageRequest,
        spec: JpaSpec<T>? = null,
        sort: SelectScope<T>.() -> Unit,
    ): Page<T> = query(session, spec).apply(sort).page(request)

    open suspend fun findOne(
        session: JpaSession,
        spec: JpaSpec<T>,
    ): T? = query(session, spec).first()

    open suspend fun findById(
        session: JpaSession,
        value: ID,
    ): T? = session.find(entity, value)

    /** [findById], throwing [com.strange.jpa.JpaNotFoundException] instead of answering null. */
    open suspend fun requireById(
        session: JpaSession,
        value: ID,
    ): T = session.get(entity, value)

    open suspend fun findByIds(
        session: JpaSession,
        values: Collection<ID>,
    ): List<T> = if (values.isEmpty()) emptyList() else query(session) { this[id] oneOf values }.list()

    open suspend fun count(
        session: JpaSession,
        spec: JpaSpec<T>? = null,
    ): Long = query(session, spec).count()

    open suspend fun exists(
        session: JpaSession,
        spec: JpaSpec<T>,
    ): Boolean = count(session, spec) > 0

    open suspend fun existsById(
        session: JpaSession,
        value: ID,
    ): Boolean = count(session) { this[id] eq value } > 0

    /**
     * The subset of [values] that exists — one query returning one column, not one row per id and
     * not the entities themselves.
     *
     * The identifier's class comes from Hibernate's metamodel, because a property reference does not
     * carry one without `kotlin-reflect` and this needs a `Class` to project into.
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun existingIds(
        session: JpaSession,
        values: Collection<ID>,
    ): List<ID> {
        if (values.isEmpty()) return emptyList()
        val idType =
            session.raw.factory.metamodel
                .entity(entity.java)
                .idType.javaType.kotlin as KClass<ID>
        return session.project(entity, idType) { this[id] }.where { this[id] oneOf values }.list()
    }

    // ─── Writes ───────────────────────────────────────────────────────────────

    /** Makes it managed. It reaches the database when the session flushes, not here. */
    open suspend fun insert(
        session: JpaSession,
        instance: T,
    ): T = instance.also { session.persist(it) }

    open suspend fun insertAll(
        session: JpaSession,
        instances: Collection<T>,
    ): List<T> {
        // `toTypedArray` is reified and T is not; `persist` takes `Any`, so the array is built as one.
        val all = instances.toList()
        session.persist(*Array<Any>(all.size) { all[it] })
        return all
    }

    /** Copies a detached instance onto the managed one, and answers with that — not with the argument. */
    open suspend fun update(
        session: JpaSession,
        instance: T,
    ): T = session.merge(instance)

    open suspend fun delete(
        session: JpaSession,
        instance: T,
    ) {
        session.remove(instance)
    }

    /**
     * Whether there was a row to delete.
     *
     * It loads the row and removes it, rather than issuing a bulk `delete` on the identifier. A bulk
     * statement goes straight to the database: no cascade fires, no `@PreRemove` runs, and a copy
     * already loaded in this session keeps existing. One extra select buys all three back. The bulk
     * form is still a `delete<T>().where { … }` away for a caller who has measured and wants it.
     */
    open suspend fun deleteById(
        session: JpaSession,
        value: ID,
    ): Boolean = findById(session, value)?.also { delete(session, it) } != null

    /** How many of [values] were actually there. Loads them first, for the reason [deleteById] does. */
    open suspend fun deleteByIds(
        session: JpaSession,
        values: Collection<ID>,
    ): Int = findByIds(session, values).onEach { delete(session, it) }.size

    private fun query(
        session: JpaSession,
        spec: JpaSpec<T>? = null,
    ): SelectScope<T> = session.select(entity).let { if (spec == null) it else it.where(spec) }
}

/**
 * A repository for whatever [id] belongs to: `jpaRepository(Purchase::id)`.
 *
 * Both type arguments come from the property reference, so neither is written and no `::class` is
 * passed. The constructor is still there for a subclass, which has to name its entity in its
 * `extends` clause anyway.
 */
inline fun <reified T : Any, ID : Any> jpaRepository(id: KProperty1<T, ID>): JpaRepository<T, ID> = JpaRepository(T::class, id)
