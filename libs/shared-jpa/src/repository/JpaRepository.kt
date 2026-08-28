package com.strange.jpa.repository

import com.strange.common.page.Page
import com.strange.jpa.JpaMappingException
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.JpaOutsideTransactionException
import com.strange.jpa.dsl.JpaEntityGraph
import com.strange.jpa.dsl.JpaSpec
import com.strange.jpa.dsl.SelectScope
import com.strange.jpa.dsl.eq
import com.strange.jpa.dsl.oneOf
import com.strange.jpa.dsl.project
import com.strange.jpa.dsl.select
import com.strange.jpa.page.PageRequest
import com.strange.jpa.page.page
import com.strange.jpa.session.JpaSession
import jakarta.persistence.Entity
import kotlinx.coroutines.future.await
import kotlin.jvm.internal.CallableReference
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
 * val purchases = JpaRepository(Purchase::id)
 *
 * class PurchaseRepository : JpaRepository<Purchase, Long>(Purchase::id) {
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
 * **The queries go through `session.raw`.** `find<Order>(id)` and `select<Purchase>()` are the
 * library's vocabulary and they are `reified`, which is exactly what this class cannot be: inside
 * one, `T` is not reifiable. So the value-typed forms underneath them are reached the way any
 * advanced caller reaches what the wrapper does not spell — through [JpaSession.raw]. That keeps the
 * wrapper's surface the reified vocabulary and nothing else, and it costs this one class an
 * `await()` that its own methods still hide from callers.
 *
 * **Nothing names the entity class, because [id] already does.** A class cannot have a `reified`
 * type parameter, so this has to learn at runtime what it is generic over — and a property reference
 * carries it: `Purchase::id` knows whose it is. That holds for an identifier declared by a
 * `@MappedSuperclass` too, where the reference names the entity referring to it rather than the
 * class that declared the property — which is the answer a repository wants, and one a spec pins
 * because getting it wrong would not fail, it would query the wrong table.
 */
open class JpaRepository<T : Any, ID : Any>(
    val id: KProperty1<T, ID>,
) {
    /** The entity this is over, taken off [id] — see [entityOf]. */
    internal val entity: KClass<T> = entityOf(id)

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

    /**
     * By identifier, loading what [graph] plans when there is one.
     *
     * This is where a fetch plan earns its place over a fetch join: `findById` has no query to hang
     * a join on, and the association it did not load is not slow to read in a reactive session — it
     * throws. [query] is the seam for the same question on the multi-row reads.
     */
    open suspend fun findById(
        session: JpaSession,
        value: ID,
        graph: JpaEntityGraph<T>? = null,
    ): T? =
        if (graph == null) {
            session.raw.find(entity.java, value).await()
        } else {
            session.raw.find(graph.raw, value).await()
        }

    /** [findById], throwing [JpaNotFoundException] instead of answering null. */
    open suspend fun requireById(
        session: JpaSession,
        value: ID,
        graph: JpaEntityGraph<T>? = null,
    ): T = findById(session, value, graph) ?: throw JpaNotFoundException(entity, value)

    open suspend fun findByIds(
        session: JpaSession,
        values: Collection<ID>,
    ): List<T> = if (values.isEmpty()) emptyList() else query(session) { this[id] oneOf values }.list()

    open suspend fun count(
        session: JpaSession,
        spec: JpaSpec<T>? = null,
    ): Long = query(session, spec).count()

    /**
     * Whether anything matches — one row asked for, not a count of all of them.
     *
     * `count(*) > 0` makes the database aggregate the entire match set to answer a boolean; a limit
     * of one lets it stop at the first row it finds. [existingIds] directly below already took the
     * same care, for the same reason.
     */
    open suspend fun exists(
        session: JpaSession,
        spec: JpaSpec<T>,
    ): Boolean = query(session, spec).limit(1).first() != null

    /** Whether the row is there, asked the same cheap way [exists] asks. */
    open suspend fun existsById(
        session: JpaSession,
        value: ID,
    ): Boolean = exists(session) { this[id] eq value }

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
        return session.raw
            .project(entity, idType) { this[id] }
            .where { this[id] oneOf values }
            .list()
    }

    // ─── Writes ───────────────────────────────────────────────────────────────

    /**
     * Every write here refuses a session with no transaction, with
     * [JpaOutsideTransactionException].
     *
     * A session flushes at the end of a unit of work if and only if there is a transaction, so a
     * `persist` inside a plain `session { }` reaches no table — no error, no warning, no row — and
     * `deleteById` would answer `true` for a row it did not delete. The guard used to live only in
     * [JpaCrudService], one layer above, while this class is public, `open`, and what the specs and
     * a custom subclass use directly. The reads are unguarded, because a read outside a transaction
     * is an ordinary thing to want.
     */
    private fun transactional(
        session: JpaSession,
        operation: String,
    ) {
        if (session.raw.currentTransaction() == null) {
            throw JpaOutsideTransactionException(operation, entity)
        }
    }

    /** Makes it managed. It reaches the database when the session flushes, not here. */
    open suspend fun insert(
        session: JpaSession,
        instance: T,
    ): T {
        transactional(session, "insert")
        return instance.also { session.persist(it) }
    }

    open suspend fun insertAll(
        session: JpaSession,
        instances: Collection<T>,
    ): List<T> {
        transactional(session, "insertAll")
        // `toTypedArray` is reified and T is not; `persist` takes `Any`, so the array is built as one.
        val all = instances.toList()
        session.persist(*Array<Any>(all.size) { all[it] })
        return all
    }

    /** Copies a detached instance onto the managed one, and answers with that — not with the argument. */
    open suspend fun update(
        session: JpaSession,
        instance: T,
    ): T {
        transactional(session, "update")
        return session.merge(instance)
    }

    open suspend fun delete(
        session: JpaSession,
        instance: T,
    ) {
        transactional(session, "delete")
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
    ): Boolean {
        transactional(session, "deleteById")
        return findById(session, value)?.also { delete(session, it) } != null
    }

    /** How many of [values] were actually there. Loads them first, for the reason [deleteById] does. */
    open suspend fun deleteByIds(
        session: JpaSession,
        values: Collection<ID>,
    ): Int {
        transactional(session, "deleteByIds")
        return findByIds(session, values).onEach { delete(session, it) }.size
    }

    /**
     * The query the methods above are built from, for what they do not spell.
     *
     * **This is where a fetch goes.** `findAll` answers with entities, so a caller that will read an
     * association has to say so — associations are `LAZY` and Hibernate Reactive has no transparent
     * lazy loading, so an unfetched one throws rather than costing a second select. A [JpaSpec] is a
     * `Joins` receiver and cannot fetch, deliberately: the same spec has to fit a projection, which
     * has nothing to hang a fetch on.
     *
     * ```kotlin
     * purchases.query(session) { Purchase::total gt 100L }
     *     .apply { fetch(Purchase::customer) }
     *     .list()
     * ```
     *
     * `open` like everything else here, so a subclass can give its entity a `withCustomer()` of its
     * own rather than repeating the block at every call site.
     */
    open fun query(
        session: JpaSession,
        spec: JpaSpec<T>? = null,
    ): SelectScope<T> = session.raw.select(entity).let { if (spec == null) it else it.where(spec) }
}

/**
 * The class a property reference belongs to.
 *
 * `KProperty1` carries its owner, and reading it needs no `kotlin-reflect`: a reference compiles to
 * a `CallableReference` whose `owner` is a `ClassReference` from the standard library when the full
 * reflection artifact is absent. It is the same shape of cast `com.strange.jpa.dsl` uses to take a
 * builder off an expression — a fact about the runtime, asserted by a spec rather than assumed.
 *
 * It answers with the class the reference *names*, not the one that declared the property, which is
 * what makes `Ticket::id` a repository over `Ticket` when a `@MappedSuperclass` declared the id.
 */
@Suppress("UNCHECKED_CAST")
private fun <T : Any, ID : Any> entityOf(id: KProperty1<T, ID>): KClass<T> {
    val owner =
        (id as? CallableReference)?.owner as? KClass<T>
            ?: throw JpaMappingException(
                "cannot tell which entity $id belongs to: a repository is built from a property " +
                    "reference written out, such as Purchase::id — not from one obtained reflectively " +
                    "or built by hand, which carries no owner",
            )

    // The reference names whichever class it was written on, which is what makes `Ticket::id` a
    // repository over Ticket when a @MappedSuperclass declared the id — and is also how
    // `JpaRepository(Keyed::id)` type-checks while naming a class no table belongs to. Left
    // unchecked, that surfaces on the first query as a Hibernate UnknownEntityTypeException from
    // inside a CompletionStage, which is the shape this module exists to prevent.
    if (!owner.java.isAnnotationPresent(Entity::class.java)) {
        throw JpaMappingException(
            "${owner.simpleName} is not an @Entity, so there is no table to build a repository over: " +
                "name the entity that declares the mapping, not the class the property was declared on",
        )
    }
    return owner
}
