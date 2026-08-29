package com.strange.spring.data.mongo.audit

import org.javers.core.Javers
import org.slf4j.LoggerFactory
import kotlin.reflect.full.memberProperties

/**
 * Records what a document looked like, what changed, and who changed it.
 *
 * Each call appends one [AuditEntry] at the next version. Nothing is ever updated: a history that
 * can be edited is not one.
 *
 * **A delete is `TERMINAL` and the last word.** Once a document is recorded as deleted, later saves
 * of the same id are ignored rather than continuing its history — a new document reusing an id is a
 * different thing, and stitching the two together would produce a diff between two unrelated
 * objects presented as a change somebody made.
 */
class AuditTrail(
    private val store: AuditStore,
    private val javers: Javers,
) {
    private val log = LoggerFactory.getLogger(AuditTrail::class.java)

    /**
     * Records [entity] as it now stands in [collection].
     *
     * Does nothing when nothing changed. Spring Data emits an `AfterSaveEvent` for every save,
     * including the ones that wrote the same values back, and an audit trail full of versions that
     * differ in nothing is a trail nobody reads.
     */
    suspend fun commit(
        entity: Any,
        collection: String,
        author: String?,
    ): AuditEntry? {
        val oid = entity.identifier() ?: return null.also { log.warn("no id on {}, not audited", entity::class.simpleName) }
        if (store.terminated(oid, collection)) {
            log.warn("{}/{} is already recorded as deleted; not recording a save after it", collection, oid)
            return null
        }

        val previous = store.latest(oid, collection)
        val diff = javers.compare(previous?.state, entity)
        if (!diff.hasChanges()) return null

        val changes =
            diff.changes
                .filterIsInstance<org.javers.core.diff.changetype.PropertyChange<*>>()
                .map {
                    PropertyChange(
                        name = it.propertyName,
                        path = it.propertyNameWithPath,
                        type = PropertyChangeType.valueOf(it.changeType.name),
                        before = it.left,
                        after = it.right,
                    )
                }

        return store.append(
            AuditEntry(
                oid = oid,
                collection = collection,
                state = entity,
                type = if (previous == null) CommitType.INITIAL else CommitType.UPDATE,
                author = author.orEmpty(),
                changes = changes,
                version = (previous?.version ?: -1) + 1,
            ),
        )
    }

    /**
     * Records that a document was deleted.
     *
     * The entry carries the last known [AuditEntry.state] rather than nothing, because the question
     * asked of a deletion is nearly always *what was it when it went*.
     */
    suspend fun terminate(
        oid: String,
        collection: String,
        author: String?,
    ): AuditEntry? {
        val previous =
            store.latest(oid, collection)
                ?: return null.also { log.warn("{}/{} has no history; nothing to close", collection, oid) }
        if (previous.type == CommitType.TERMINAL) return null

        return store.append(
            previous.copy(
                id =
                    org.bson.types
                        .ObjectId()
                        .toHexString(),
                type = CommitType.TERMINAL,
                version = previous.version + 1,
                author = author.orEmpty(),
                changes = emptyList(),
            ),
        )
    }
}

/**
 * The document's own id, read off whatever property carries it.
 *
 * Reflection, because this receives an arbitrary `Any` from a Spring Data lifecycle event and the
 * one thing every audited document has in common is that Mongo gave it an `_id`.
 */
private fun Any.identifier(): String? =
    this::class
        .memberProperties
        .firstOrNull { it.name == "id" }
        ?.call(this)
        ?.toString()
        ?.takeIf { it.isNotBlank() }
