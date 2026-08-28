package com.strange.jpa.service

import com.strange.common.page.Page
import com.strange.jpa.JpaOutsideTransactionException
import com.strange.jpa.audit.AuditedEntity
import com.strange.jpa.repository.JpaRepository
import com.strange.jpa.repository.JpaSpec
import com.strange.jpa.session.JpaSession
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root

/**
 * The create/update/delete flow every entity-backed service repeats, with the parts that differ left
 * as hooks.
 *
 * A service maps a request into an entity and a request onto one; it does not repeat "read it, check
 * it exists, write it, stamp who did it". [buildCreate] and [applyUpdate] are the two methods a
 * subclass must write, and they are the two that are genuinely about this entity.
 *
 * ```kotlin
 * class PurchaseService(repository: JpaRepository<Purchase, Long>, principal: String? = null) :
 *     JpaCrudService<Purchase, Long, NewPurchase, EditPurchase>(repository, principal) {
 *     override suspend fun buildCreate(input: NewPurchase) = Purchase(input.id, input.reference)
 *     override suspend fun applyUpdate(existing: Purchase, input: EditPurchase) {
 *         input.reference?.let { existing.reference = it }
 *     }
 * }
 * ```
 *
 * **An update mutates the managed entity; it does not build a statement.** That is the whole
 * difference from `MongoCrudService`, where the hook returns update operators and an empty list
 * means "write nothing". Here the persistence context already knows what changed, so a hook that
 * assigns the value a column already has writes nothing — Hibernate's dirty check decides, and it is
 * better at it than a hook comparing fields.
 *
 * **A write outside a transaction is refused.** A session flushes only when there is one, so
 * `create` in a plain `session { }` would return an entity, report success, and write no row.
 * [JpaOutsideTransactionException] rather than that.
 */
abstract class JpaCrudService<T : Any, ID : Any, C : Any, U : Any>(
    protected val repository: JpaRepository<T, ID>,
    protected val principal: String? = null,
) {
    // ─── Reads ────────────────────────────────────────────────────────────────

    open suspend fun findAll(
        session: JpaSession,
        spec: JpaSpec<T>? = null,
    ): List<T> = repository.findAll(session, spec)

    open suspend fun findPage(
        session: JpaSession,
        limit: Int,
        offset: Int = 0,
        spec: JpaSpec<T>? = null,
        shape: (CriteriaQuery<T>, Root<T>) -> Unit = { _, _ -> },
    ): Page<T> = repository.findPage(session, limit, offset, spec, shape)

    /** Throws `JpaNotFoundException`: a service asked for an entity by id has a caller. */
    open suspend fun findById(
        session: JpaSession,
        id: ID,
    ): T = repository.requireById(session, id)

    open suspend fun findByIdOrNull(
        session: JpaSession,
        id: ID,
    ): T? = repository.findById(session, id)

    // ─── Writes ───────────────────────────────────────────────────────────────

    open suspend fun create(
        session: JpaSession,
        input: C,
    ): T {
        transactional(session, "create")
        beforeCreate(input)
        val created = buildCreate(input).also(::stampCreated)
        repository.insert(session, created)

        /* Flush rather than wait for the commit: it assigns a generated identifier, and it puts a
           constraint violation here — where the caller can say which input caused it — instead of
           at the end of the transaction. It is not a refresh: a default the database applied is not
           read back, and afterCreate is where that belongs.

           Naming the input is all it buys. It is not a way to catch one create and continue with the
           next: a failed flush dooms the transaction, so a caller looping over inputs with a
           try/catch inside gets one exception it swallowed and a rollback at the end. Import a batch
           with a transaction per input, not a try/catch per input. */
        session.flush()

        return created.also { afterCreate(it, session) }
    }

    open suspend fun update(
        session: JpaSession,
        id: ID,
        input: U,
    ): T {
        transactional(session, "update")
        val existing = repository.requireById(session, id)
        beforeUpdate(existing, input)

        /* Mutating the managed entity is the update. Nothing is written for a hook that assigns
           what is already there — the dirty check decides, which is why there is no "changes are
           empty" branch here the way there is in the Mongo service. */
        applyUpdate(existing, input)
        stampUpdated(existing)
        session.flush()

        return existing.also { afterUpdate(it, session) }
    }

    /** Throws `JpaNotFoundException` when there is nothing to delete. */
    open suspend fun delete(
        session: JpaSession,
        id: ID,
    ) {
        transactional(session, "delete")
        val existing = repository.requireById(session, id)
        beforeDelete(listOf(id), session)
        repository.delete(session, existing)
        session.flush()
        afterDelete(listOf(id), session)
    }

    /** How many were actually there. Deleting nothing is not an error here — [delete] is for that. */
    open suspend fun deleteAll(
        session: JpaSession,
        ids: Collection<ID>,
    ): Int {
        transactional(session, "deleteAll")
        // The ids that are really there, not the ones that were asked for. A hook publishes an event
        // or evicts a cache entry per id, and doing that for a row that never existed is a lie told
        // inside the transaction that correctly reports how many went. `existingIds` is one query
        // returning one column, so knowing costs a great deal less than getting it wrong.
        val present = repository.existingIds(session, ids)
        beforeDelete(present, session)
        val deleted = repository.deleteByIds(session, present)
        session.flush()
        afterDelete(present, session)
        return deleted
    }

    private fun transactional(
        session: JpaSession,
        operation: String,
    ) {
        if (session.raw.currentTransaction() == null) {
            throw JpaOutsideTransactionException(operation, repository.entity)
        }
    }

    // ─── Hooks ────────────────────────────────────────────────────────────────

    /** The entity [input] becomes. */
    protected abstract suspend fun buildCreate(input: C): T

    /** [input], applied to the managed entity. What it does not touch, it does not write. */
    protected abstract suspend fun applyUpdate(
        existing: T,
        input: U,
    )

    /**
     * Records who created it, for an [AuditedEntity] and a known [principal].
     *
     * Only the service knows the principal, so *who* is stamped here; *when* belongs to the entity,
     * where `@PrePersist` and `@PreUpdate` stamp it inside the flush — the one place that can tell a
     * real write from an update the dirty check turned into a no-op.
     */
    protected open fun stampCreated(entity: T) {
        if (entity is AuditedEntity && principal != null) {
            entity.createdBy = principal
            entity.lastModifiedBy = principal
        }
    }

    /**
     * The same, for an update.
     *
     * Worth knowing: assigning [AuditedEntity.lastModifiedBy] *is* a change when the principal
     * differs from the one on the row, so an update that touches nothing else still fires
     * `@PreUpdate` and moves `lastModifiedAt`. That is the intended reading — the row records who
     * touched it last, and somebody did — but it means "a no-op moves nothing" holds only while the
     * principal is unchanged. Override this to guard it if that is not what a caller wants.
     */
    protected open fun stampUpdated(entity: T) {
        if (entity is AuditedEntity && principal != null) {
            entity.lastModifiedBy = principal
        }
    }

    protected open suspend fun beforeCreate(input: C) = Unit

    /**
     * **Runs before the commit, like every `after*` hook here.** The transaction the caller opened
     * is still open, so a hook that publishes to Kafka, AMQP or an HTTP endpoint publishes a fact a
     * later rollback unmakes. That is the right place for a cascade or an outbox row — both are
     * writes in the same transaction, and both are undone with it — and the wrong place for anything
     * that leaves the database. Send those after `transaction { }` returns.
     */
    protected open suspend fun afterCreate(
        created: T,
        session: JpaSession,
    ) = Unit

    protected open suspend fun beforeUpdate(
        existing: T,
        input: U,
    ) = Unit

    /**
     * The entity as it now stands, before the commit — see [afterCreate] for what that rules out.
     *
     * There is no "previous" argument, unlike the Mongo service: the managed instance was mutated in
     * place, so the state before the update is no longer anywhere to hand over. A hook that needs it
     * should copy what it cares about in [beforeUpdate].
     */
    protected open suspend fun afterUpdate(
        updated: T,
        session: JpaSession,
    ) = Unit

    /** Where a cascade belongs: it runs in the same transaction as the delete that triggered it. */
    protected open suspend fun beforeDelete(
        ids: Collection<ID>,
        session: JpaSession,
    ) = Unit

    /**
     * The ids that were actually deleted, before the commit — see [afterCreate] for what that rules
     * out.
     *
     * *Actually* deleted: [deleteAll] resolves the ids that were really there before it deletes
     * them, so a hook publishing one event per id does not announce a row that never existed.
     */
    protected open suspend fun afterDelete(
        ids: Collection<ID>,
        session: JpaSession,
    ) = Unit
}
