package com.softistx.jpa.convert

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Stores a [kotlin.uuid.Uuid] as Postgres' own `uuid`, not as text and not as a blob.
 *
 * The column type is the point. A `uuid` is sixteen bytes, compares and indexes as one value, and is
 * what every other client of the database expects to find; the `varchar(36)` a naive mapping
 * produces is more than twice the size and sorts by its hyphens. [UuidConverterTest] asserts the
 * type the server reports.
 *
 * Registered automatically by `Jpa.connect`, and opted out of per attribute with
 * `@Convert(disableConversion = true)`.
 */
@OptIn(ExperimentalUuidApi::class)
@Converter(autoApply = true)
class UuidConverter : AttributeConverter<Uuid, UUID> {
    override fun convertToDatabaseColumn(attribute: Uuid?): UUID? = attribute?.toJavaUuid()

    override fun convertToEntityAttribute(dbData: UUID?): Uuid? = dbData?.toKotlinUuid()
}
