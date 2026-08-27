package com.strange.mongo.audit

/**
 * An entity that carries its own audit trail.
 *
 * `MongoCrudService` stamps an update only when the document it is updating is one of these — the
 * alternative, stamping everything, quietly adds a `metadata` object to collections that never
 * asked for one, and a schema that grew by accident is worse than one that is missing a field.
 */
interface Audited {
    val metadata: AuditMetadata
}
