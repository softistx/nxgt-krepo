package com.strange.mongo.audit

import com.mongodb.client.model.Updates
import com.strange.mongo.codec.InstantAsBsonDateTime
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Who wrote this document, and when — embedded in the entity rather than kept in a parallel
 * collection, because the one question it is ever asked is about *this* document.
 *
 * The timestamps are bound to [InstantAsBsonDateTime] on the property rather than left to a
 * contextual serializer, so an entity carrying this needs nothing registered: stored as a BSON
 * date, served as an ISO-8601 string.
 */
@Serializable
data class AuditMetadata(
    val createdBy: String = "",
    val lastModifiedBy: String = "",
    @Serializable(with = InstantAsBsonDateTime::class)
    val createdAt: Instant = Clock.System.now(),
    @Serializable(with = InstantAsBsonDateTime::class)
    val lastModifiedAt: Instant = Clock.System.now(),
) {
    /** The same metadata, as of now, for [by]. */
    fun touchedBy(by: String?): AuditMetadata = copy(lastModifiedBy = by.orEmpty(), lastModifiedAt = Clock.System.now())

    companion object {
        /** A document being created by [by] — both halves start out the same. */
        fun by(by: String?): AuditMetadata = AuditMetadata(createdBy = by.orEmpty(), lastModifiedBy = by.orEmpty())

        /**
         * The `$set` that stamps an update, as operators rather than as a replacement value — an
         * update touches one field and must not carry the rest of the metadata back with it.
         *
         * The timestamp goes through the driver as a `kotlin.time.Instant`, which is only encodable
         * because `mongoCodecRegistry` carries `InstantCodec`. A client built without it fails here.
         */
        fun updateBy(
            by: String?,
            field: String = FIELD,
        ): Bson =
            Updates.combine(
                Updates.set("$field.lastModifiedBy", by.orEmpty()),
                Updates.set("$field.lastModifiedAt", Clock.System.now()),
            )

        /** Where an audited entity keeps this. */
        const val FIELD: String = "metadata"
    }
}
