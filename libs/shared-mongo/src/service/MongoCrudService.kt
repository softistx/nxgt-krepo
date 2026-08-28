package com.strange.mongo.service

import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCluster
import com.strange.common.page.Page
import com.strange.mongo.audit.AuditMetadata
import com.strange.mongo.audit.Audited
import com.strange.mongo.page.PaginationOptions
import com.strange.mongo.repository.MongoCrudRepository
import com.strange.mongo.withTransaction
import kotlinx.coroutines.flow.Flow
import org.bson.BsonDocument
import org.bson.conversions.Bson

/**
 * The create/update/delete flow every collection-backed service repeats, with the parts that differ
 * left as hooks.
 *
 * A service maps a request into a document and a request into update operators; it does not repeat
 * "read it, check it exists, write it, read it back, stamp who did it". [buildCreate] and
 * [buildUpdate] are the two methods a subclass must write, and they are the two that are genuinely
 * about this collection.
 *
 * ```kotlin
 * class NoteService(repository: NoteRepository, principal: String?) :
 *     MongoCrudService<Note, String, NewNote, EditNote>(repository, principal) {
 *     override suspend fun buildCreate(input: NewNote) = Note(ObjectId().toHexString(), input.text)
 *     override suspend fun buildUpdate(existing: Note, input: EditNote) = listOf(Updates.set("text", input.text))
 * }
 * ```
 *
 * **Transactions are opt-in.** Pass a [MongoCluster] and every write that is not already in a
 * session opens one; leave it out and writes run as they come. That is not laziness: a
 * single-document update is atomic in Mongo on its own, so the transaction only starts to matter
 * once a hook writes something else — and until it does, it costs a session and a replica set.
 *
 * **Auditing is opt-in too**, by the entity: an update is stamped only when the document implements
 * [Audited] and a [principal] is known. Creation-time metadata belongs in [buildCreate], where the
 * entity is being built anyway — `AuditMetadata.by(principal)`.
 */
abstract class MongoCrudService<T : Any, ID : Any, C : Any, U : Any>(
    protected val repository: MongoCrudRepository<T, ID>,
    protected val principal: String? = null,
    private val transactions: MongoCluster? = null,
) {
    // ─── Reads ────────────────────────────────────────────────────────────────

    open fun findAll(
        filter: Bson = BsonDocument(),
        session: ClientSession? = null,
    ): Flow<T> = repository.findAll(filter, session)

    open suspend fun findPage(
        options: PaginationOptions,
        session: ClientSession? = null,
    ): Page<T> = repository.findPage(options, session)

    /** Throws `DocumentNotFoundException`: a service asked for a document by id has a caller. */
    open suspend fun findById(
        id: ID,
        session: ClientSession? = null,
    ): T = repository.requireById(id, session)

    open suspend fun findByIdOrNull(
        id: ID,
        session: ClientSession? = null,
    ): T? = repository.findById(id, session)

    // ─── Writes ───────────────────────────────────────────────────────────────

    open suspend fun create(
        input: C,
        session: ClientSession? = null,
    ): T =
        write(session) { inSession ->
            beforeCreate(input)
            val document = buildCreate(input)
            repository.insert(document, inSession)

            /* Read back rather than return what was built: a default applied by the collection,
               or by a hook writing alongside, is part of the document the caller now has. */
            repository.requireById(repository.idOf(document), inSession).also { afterCreate(it, inSession) }
        }

    open suspend fun update(
        id: ID,
        input: U,
        session: ClientSession? = null,
    ): T =
        write(session) { inSession ->
            val existing = repository.requireById(id, inSession)
            beforeUpdate(existing, input)

            /* An update that changes nothing writes nothing — and is not stamped either, since
               nobody modified the document. The audit trail is for changes, not for requests. */
            val changes = buildUpdate(existing, input)
            if (changes.isEmpty()) {
                return@write existing
            }

            val updated =
                repository.updateById(id, Updates.combine(changes + auditUpdates(existing)), inSession)
                    ?: repository.requireById(id, inSession)
            updated.also { afterUpdate(existing, it, inSession) }
        }

    /** Throws `DocumentNotFoundException` when there is nothing to delete. */
    open suspend fun delete(
        id: ID,
        session: ClientSession? = null,
    ) {
        write(session) { inSession ->
            repository.requireById(id, inSession)
            deleteInSession(listOf(id), inSession)
        }
    }

    /** How many were actually there. Deleting nothing is not an error here — [delete] is for that. */
    open suspend fun deleteAll(
        ids: Collection<ID>,
        session: ClientSession? = null,
    ): Long = write(session) { inSession -> deleteInSession(ids, inSession) }

    private suspend fun deleteInSession(
        ids: Collection<ID>,
        session: ClientSession?,
    ): Long {
        beforeDelete(ids, session)
        val deleted = repository.deleteByIds(ids, session)
        afterDelete(ids, session)
        return deleted
    }

    /**
     * Runs [block] in the caller's session, in a new transaction when this service was given a
     * cluster, or in neither.
     */
    private suspend fun <R> write(
        session: ClientSession?,
        block: suspend (ClientSession?) -> R,
    ): R =
        when {
            session != null -> block(session)
            transactions != null -> transactions.withTransaction { block(it) }
            else -> block(null)
        }

    // ─── Hooks ────────────────────────────────────────────────────────────────

    /** The document [input] becomes. Where an [Audited] entity gets its `AuditMetadata.by`. */
    protected abstract suspend fun buildCreate(input: C): T

    /** The update operators [input] becomes. Empty means there is nothing to write. */
    protected abstract suspend fun buildUpdate(
        existing: T,
        input: U,
    ): List<Bson>

    /** Stamped only for an [Audited] entity, and only when we know who is acting. */
    protected open fun auditUpdates(existing: T): List<Bson> =
        if (existing is Audited && principal != null) listOf(AuditMetadata.updateBy(principal)) else emptyList()

    protected open suspend fun beforeCreate(input: C) = Unit

    protected open suspend fun afterCreate(
        created: T,
        session: ClientSession?,
    ) = Unit

    protected open suspend fun beforeUpdate(
        existing: T,
        input: U,
    ) = Unit

    protected open suspend fun afterUpdate(
        previous: T,
        updated: T,
        session: ClientSession?,
    ) = Unit

    /** Where a cascade belongs: it runs in the same session as the delete that triggered it. */
    protected open suspend fun beforeDelete(
        ids: Collection<ID>,
        session: ClientSession?,
    ) = Unit

    protected open suspend fun afterDelete(
        ids: Collection<ID>,
        session: ClientSession?,
    ) = Unit
}
