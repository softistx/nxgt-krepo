package com.strange.spring.data.mongo.audit

import org.bson.types.ObjectId
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.MongoId
import kotlin.time.Clock
import kotlin.time.Instant

/** What happened to a document at one version of its history. */
enum class CommitType {
    /** The first time this document was seen. [AuditEntry.changes] is empty; [AuditEntry.state] is all of it. */
    INITIAL,

    /** A save that changed something. */
    UPDATE,

    /** A delete. Nothing is recorded after one — see [AuditTrail]. */
    TERMINAL,
}

/** How one property changed, in Javers' vocabulary. */
enum class PropertyChangeType {
    PROPERTY_ADDED,
    PROPERTY_REMOVED,
    PROPERTY_VALUE_CHANGED,
}

/** One property, before and after. */
data class PropertyChange(
    val name: String,
    val path: String,
    val type: PropertyChangeType,
    val before: Any? = null,
    val after: Any? = null,
)

/**
 * One version of one document: what it looked like, what changed to get there, and who did it.
 *
 * [state] is the whole document rather than only the diff. That costs storage and buys the thing an
 * audit trail is for — reading a version without replaying every change before it, and still having
 * an answer when the diff is wrong.
 *
 * [COLLECTION] is only the default. The name entries are actually written to is
 * `stx.data.mongo.audit.collection`, and [AuditStore] passes it to every call — see the note there
 * on why this is not the SpEL lookup a configurable `@Document` name is usually written as.
 */
@TypeAlias(AuditEntry.NAME)
@Document(AuditEntry.COLLECTION)
data class AuditEntry(
    @MongoId val id: String = ObjectId().toHexString(),
    /** The `_id` of the document this is a version of. */
    @Indexed val oid: String,
    /** The collection that document lives in — the same `oid` in two collections is two histories. */
    @Indexed val collection: String,
    val version: Long = 0,
    @Indexed val author: String = "",
    val type: CommitType = CommitType.INITIAL,
    val state: Any? = null,
    val changes: List<PropertyChange> = emptyList(),
    @CreatedDate val at: Instant = Clock.System.now(),
) {
    companion object {
        /** Where entries go unless `stx.data.mongo.audit.collection` says otherwise. */
        const val COLLECTION = "audits"
        const val NAME = "AuditEntry"
    }
}
