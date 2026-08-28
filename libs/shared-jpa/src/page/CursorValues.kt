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
 * set of things a sort key can sensibly be is small, and a type this cannot carry is refused when
 * the page is built, by name — see [carriesCursorValue] — instead of round-tripping into something
 * that does not compare.
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

/**
 * Whether the codec can write [type] and read it back.
 *
 * The same table [fromText] parses, asked before the first page rather than discovered on the
 * second: encoding is `toString()` for anything at all, so without this a key the decoder cannot
 * read issues page one and a plausible cursor happily, and fails only when somebody follows it.
 * `CursorValuesTest` round-trips every member, which is what keeps this list and that `when` from
 * drifting apart.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun carriesCursorValue(type: Class<*>): Boolean = type.isEnum || type in cursorTypes

@OptIn(ExperimentalUuidApi::class)
private val cursorTypes: Set<Class<*>> =
    // setOfNotNull because `javaPrimitiveType` is null for anything that has no primitive form.
    setOfNotNull(
        String::class.java,
        Long::class.javaObjectType,
        Long::class.javaPrimitiveType,
        Int::class.javaObjectType,
        Int::class.javaPrimitiveType,
        Short::class.javaObjectType,
        Short::class.javaPrimitiveType,
        Byte::class.javaObjectType,
        Byte::class.javaPrimitiveType,
        Double::class.javaObjectType,
        Double::class.javaPrimitiveType,
        Float::class.javaObjectType,
        Float::class.javaPrimitiveType,
        Boolean::class.javaObjectType,
        Boolean::class.javaPrimitiveType,
        BigDecimal::class.java,
        BigInteger::class.java,
        UUID::class.java,
        Uuid::class.java,
        Instant::class.java,
        java.time.Instant::class.java,
        LocalDate::class.java,
        LocalDateTime::class.java,
        LocalTime::class.java,
        OffsetDateTime::class.java,
        ZonedDateTime::class.java,
    )

/** The reverse, into the type the mapping says the attribute has. */
@OptIn(ExperimentalUuidApi::class)
internal fun fromText(
    type: Class<*>,
    text: String,
): Comparable<*> =
    runCatching {
        when {
            type == String::class.java -> text
            type == Long::class.javaObjectType || type == Long::class.javaPrimitiveType -> text.toLong()
            type == Int::class.javaObjectType || type == Int::class.javaPrimitiveType -> text.toInt()
            type == Short::class.javaObjectType || type == Short::class.javaPrimitiveType -> text.toShort()
            type == Byte::class.javaObjectType || type == Byte::class.javaPrimitiveType -> text.toByte()
            type == Double::class.javaObjectType || type == Double::class.javaPrimitiveType -> text.toDouble()
            type == Float::class.javaObjectType || type == Float::class.javaPrimitiveType -> text.toFloat()
            type == Boolean::class.javaObjectType || type == Boolean::class.javaPrimitiveType -> text.toBoolean()
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
