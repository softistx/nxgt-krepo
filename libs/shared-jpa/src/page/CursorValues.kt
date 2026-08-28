package com.strange.jpa.page

import com.strange.jpa.JpaPaginationException
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A sort key value as text, and back.
 *
 * A cursor has to survive a round trip through a URL and come back as the *same* value, or the page
 * after it is empty rather than wrong — which is worse, because empty looks like the end of the
 * results. Text and an explicit table of types rather than serialization of whatever was there: the
 * set of things a sort key can sensibly be is small, and a type this cannot carry should be refused
 * when the page is built, by name, instead of round-tripping into something that does not compare.
 *
 * The type comes from the JPA metamodel, not from the property — Hibernate knows what a converted
 * attribute really is and `KProperty1` would need `kotlin-reflect` to say.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun asText(value: Comparable<*>): String =
    when (value) {
        is Enum<*> -> value.name
        else -> value.toString()
    }

/** The reverse, into the type the mapping says the attribute has. */
@OptIn(ExperimentalUuidApi::class)
internal fun fromText(
    type: Class<*>,
    text: String,
): Comparable<*> =
    runCatching {
        when {
            type == String::class.java -> text
            type == java.lang.Long::class.java || type == Long::class.java -> text.toLong()
            type == Integer::class.java || type == Int::class.java -> text.toInt()
            type == java.lang.Short::class.java || type == Short::class.java -> text.toShort()
            type == java.lang.Byte::class.java || type == Byte::class.java -> text.toByte()
            type == java.lang.Double::class.java || type == Double::class.java -> text.toDouble()
            type == java.lang.Float::class.java || type == Float::class.java -> text.toFloat()
            type == java.lang.Boolean::class.java || type == Boolean::class.java -> text.toBoolean()
            type == BigDecimal::class.java -> BigDecimal(text)
            type == BigInteger::class.java -> BigInteger(text)
            type == UUID::class.java -> UUID.fromString(text)
            type == Uuid::class.java -> Uuid.parse(text)
            type == Instant::class.java -> Instant.parse(text)
            type == java.time.Instant::class.java -> java.time.Instant.parse(text)
            type == LocalDate::class.java -> LocalDate.parse(text)
            type == LocalDateTime::class.java -> LocalDateTime.parse(text)
            type == LocalTime::class.java -> LocalTime.parse(text)
            type == OffsetDateTime::class.java -> OffsetDateTime.parse(text)
            type == ZonedDateTime::class.java -> ZonedDateTime.parse(text)
            type.isEnum -> enumOf(type, text)
            else -> throw JpaPaginationException("a ${type.name} cannot be a cursor key")
        }
    }.getOrElse { failure ->
        if (failure is JpaPaginationException) throw failure
        throw JpaPaginationException("cursor holds '$text', which is not a ${type.simpleName}")
    }

@Suppress("UNCHECKED_CAST")
private fun enumOf(
    type: Class<*>,
    text: String,
): Comparable<*> = java.lang.Enum.valueOf(type as Class<out Enum<*>>, text) as Comparable<*>
