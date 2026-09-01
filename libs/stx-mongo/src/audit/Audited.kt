package com.softistx.mongo.audit

import org.bson.conversions.Bson

/**
 * An entity that carries its own audit trail.
 *
 * Stamp an update only when the document being updated is one of these — the alternative, stamping
 * everything, quietly adds a `metadata` object to collections that never asked for one, and a schema
 * that grew by accident is worse than one that is missing a field.
 */
interface Audited {
    val metadata: AuditMetadata
}

/**
 * The operators that stamp an update to this document, to combine with the ones the update itself
 * is made of.
 *
 * ```kotlin
 * val changes = Updates.set("text", input.text) + (existing as? Audited)?.updatedBy(principal).orEmpty()
 * ```
 *
 * The `as?` is deliberately the caller's to write: only a collection that opted into [Audited] gets
 * a `metadata` object, and a helper that quietly stamped anything would be the accidental schema
 * this interface exists to prevent.
 *
 * **Nothing to stamp with is nothing to stamp**, so a null [principal] answers an empty list rather
 * than writing an empty string over whoever really did touch the row last. The timestamp travels
 * with the name for the same reason — a `lastModifiedAt` that moved with no `lastModifiedBy` to go
 * with it records that something happened and loses what a reader wanted to know.
 */
fun Audited.updatedBy(principal: String?): List<Bson> = if (principal == null) emptyList() else listOf(AuditMetadata.updateBy(principal))
