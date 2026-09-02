package com.softistx.jpa.convert

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import java.time.Instant as JavaInstant

/**
 * Stores a [kotlin.time.Instant] as the timestamp Postgres already understands.
 *
 * JPA's basic types are the `java.time` ones; the Kotlin one is an unknown class, and an unknown
 * class with no converter is mapped as a serialized blob rather than refused — which is the whole
 * danger. Everything succeeds, and what lands in the column is bytes no other client can read,
 * compare or index. Converting to [java.time.Instant] keeps the column a real `timestamp with time
 * zone`, and `InstantConverterTest` asserts that against `information_schema` rather than trusting a
 * round trip to agree with itself.
 *
 * Registered automatically by `Jpa.connect`, and applied to every `Instant` attribute without a
 * `@Convert` on it. An attribute that wants something else — a `bigint` of epoch millis, say — opts
 * out with `@Convert(disableConversion = true)` and maps itself.
 */
@Converter(autoApply = true)
class InstantConverter : AttributeConverter<Instant, JavaInstant> {
    override fun convertToDatabaseColumn(attribute: Instant?): JavaInstant? = attribute?.toJavaInstant()

    override fun convertToEntityAttribute(dbData: JavaInstant?): Instant? = dbData?.toKotlinInstant()
}
