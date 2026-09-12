package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi

/**
 * Every scalar this library ships. GraphQL's own five (`Int`, `Float`, `String`, `Boolean`, `ID`)
 * are graphql-java's; these are the rest of what a Kotlin service actually has fields of.
 *
 * Three groups, and the difference between them is whether a Kotlin type reaches them:
 *
 * | Group | Scalars | How a schema gets one |
 * | --- | --- | --- |
 * | Kotlin types | `Long`, `Short`, `Byte`, `Char`, `Instant`, `Uuid`, `Duration`, `LocalDate`, `LocalTime`, `LocalDateTime`, `Json` | a field of that type, automatically |
 * | JVM types | `BigDecimal`, `BigInteger`, `Url`, `Locale` | a field of that type, automatically |
 * | Constrained | `PositiveInt`, `NegativeInt`, `NonPositiveInt`, `NonNegativeInt`, `PositiveFloat`, `NegativeFloat`, `NonPositiveFloat`, `NonNegativeFloat` | `scalars(…)` on the builder, or `scalar X` in SDL |
 *
 * The first two groups are added to the schema **when a field uses one**, so a service with no
 * dates does not advertise `scalar Instant`. The third has no Kotlin type to be reached by — there
 * is none for "an Int above zero" — so it is opt-in and always explicit.
 *
 * Adding a scalar is one new `<Name>Scalar.kt` and one line in [BuiltInScalars]. Nothing else in
 * the library enumerates them: the type lookup, the SDL wiring and the schema's additional types
 * all read that list.
 */
@OptIn(ExperimentalUuidApi::class)
object Scalars {
    /** GraphQL `Int` is 32-bit. Kotlin `Long` is this scalar, serialized as an integer. */
    val Long: GraphQLScalarType = LongScalar

    /** Kotlin `Short`, 16-bit. */
    val Short: GraphQLScalarType = ShortScalar

    /** Kotlin `Byte`, 8-bit. */
    val Byte: GraphQLScalarType = ByteScalar

    /** `java.math.BigInteger` — an integer of any width. */
    val BigInteger: GraphQLScalarType = BigIntegerScalar

    /** `java.math.BigDecimal` — the scalar for money, which `Float` rounds. */
    val BigDecimal: GraphQLScalarType = BigDecimalScalar

    /** Kotlin `Char`, as a one-character string. */
    val Char: GraphQLScalarType = CharScalar

    /** `kotlin.time.Instant` as an ISO-8601 string. */
    val Instant: GraphQLScalarType = InstantScalar

    /** `kotlin.uuid.Uuid` as the canonical hyphenated string. */
    val Uuid: GraphQLScalarType = UuidScalar

    /** `kotlin.time.Duration` in ISO-8601 form, `PT1H30M`. */
    val Duration: GraphQLScalarType = DurationScalar

    /** `kotlinx.datetime.LocalDate` — a date with no zone. */
    val LocalDate: GraphQLScalarType = LocalDateScalar

    /** `kotlinx.datetime.LocalTime` — a time with no date and no zone. */
    val LocalTime: GraphQLScalarType = LocalTimeScalar

    /** `kotlinx.datetime.LocalDateTime` — both, still with no zone, so still not a moment. */
    val LocalDateTime: GraphQLScalarType = LocalDateTimeScalar

    /** An absolute URL, as a `java.net.URI`. */
    val Url: GraphQLScalarType = UrlScalar

    /** A BCP 47 language tag, as a `java.util.Locale`. */
    val Locale: GraphQLScalarType = LocaleScalar

    /** Arbitrary JSON, as a `JsonElement`. For a document, not for a type you did not model. */
    val Json: GraphQLScalarType = JsonScalar

    /** `Int` above zero. Opt-in. */
    val PositiveInt: GraphQLScalarType = PositiveIntScalar

    /** `Int` below zero. Opt-in. */
    val NegativeInt: GraphQLScalarType = NegativeIntScalar

    /** `Int` of zero or less. Opt-in. */
    val NonPositiveInt: GraphQLScalarType = NonPositiveIntScalar

    /** `Int` of zero or more — a count, a page size. Opt-in. */
    val NonNegativeInt: GraphQLScalarType = NonNegativeIntScalar

    /** `Float` above zero. Opt-in. */
    val PositiveFloat: GraphQLScalarType = PositiveFloatScalar

    /** `Float` below zero. Opt-in. */
    val NegativeFloat: GraphQLScalarType = NegativeFloatScalar

    /** `Float` of zero or less. Opt-in. */
    val NonPositiveFloat: GraphQLScalarType = NonPositiveFloatScalar

    /** `Float` of zero or more. Opt-in. */
    val NonNegativeFloat: GraphQLScalarType = NonNegativeFloatScalar

    /** Every scalar above. An SDL document may declare any of them and needs no wiring. */
    val All: List<GraphQLScalarType> = BuiltInScalars.map { it.type }
}

/**
 * The registry. One line per scalar, and the only place the library enumerates them.
 *
 * [ScalarBinding.serialNames] is the same answer as [ScalarBinding.kotlinTypes] for the walk that
 * goes through a `SerialDescriptor` instead of a `KType`. Both are needed: a resolver's return
 * type arrives as a `KType`, a property inside a `@Serializable` class as a descriptor, and the
 * two do not always agree — a `@Serializable(with = …)` property names whatever its serializer
 * named itself.
 */
@OptIn(ExperimentalUuidApi::class)
internal val BuiltInScalars: List<ScalarBinding> =
    listOf(
        ScalarBinding(LongScalar, listOf(Long::class), listOf("kotlin.Long", "Long")),
        ScalarBinding(ShortScalar, listOf(Short::class), listOf("kotlin.Short", "Short")),
        ScalarBinding(ByteScalar, listOf(Byte::class), listOf("kotlin.Byte", "Byte")),
        ScalarBinding(CharScalar, listOf(Char::class), listOf("kotlin.Char", "Char")),
        ScalarBinding(BigIntegerScalar, listOf(BigInteger::class), listOf("java.math.BigInteger")),
        ScalarBinding(BigDecimalScalar, listOf(BigDecimal::class), listOf("java.math.BigDecimal")),
        ScalarBinding(InstantScalar, listOf(kotlin.time.Instant::class), listOf("kotlin.time.Instant")),
        ScalarBinding(UuidScalar, listOf(kotlin.uuid.Uuid::class), listOf("kotlin.uuid.Uuid")),
        ScalarBinding(DurationScalar, listOf(kotlin.time.Duration::class), listOf("kotlin.time.Duration")),
        ScalarBinding(
            LocalDateScalar,
            listOf(kotlinx.datetime.LocalDate::class),
            listOf("kotlinx.datetime.LocalDate"),
        ),
        ScalarBinding(
            LocalTimeScalar,
            listOf(kotlinx.datetime.LocalTime::class),
            listOf("kotlinx.datetime.LocalTime"),
        ),
        ScalarBinding(
            LocalDateTimeScalar,
            listOf(kotlinx.datetime.LocalDateTime::class),
            listOf("kotlinx.datetime.LocalDateTime"),
        ),
        ScalarBinding(UrlScalar, listOf(URI::class), listOf("java.net.URI")),
        ScalarBinding(LocaleScalar, listOf(java.util.Locale::class), listOf("java.util.Locale")),
        ScalarBinding(
            JsonScalar,
            listOf(JsonElement::class, JsonObject::class, JsonArray::class, JsonPrimitive::class),
            listOf(
                "kotlinx.serialization.json.JsonElement",
                "kotlinx.serialization.json.JsonObject",
                "kotlinx.serialization.json.JsonArray",
                "kotlinx.serialization.json.JsonPrimitive",
            ),
        ),
        ScalarBinding(PositiveIntScalar),
        ScalarBinding(NegativeIntScalar),
        ScalarBinding(NonPositiveIntScalar),
        ScalarBinding(NonNegativeIntScalar),
        ScalarBinding(PositiveFloatScalar),
        ScalarBinding(NegativeFloatScalar),
        ScalarBinding(NonPositiveFloatScalar),
        ScalarBinding(NonNegativeFloatScalar),
    )

/** [BuiltInScalars] by Kotlin class, for the `KType` half of the type walk. */
internal val ScalarsByKotlinType: Map<KClass<*>, GraphQLScalarType> =
    BuiltInScalars.flatMap { binding -> binding.kotlinTypes.map { it to binding.type } }.toMap()

/** [BuiltInScalars] by `SerialDescriptor.serialName`, for the descriptor half. */
internal val ScalarsBySerialName: Map<String, GraphQLScalarType> =
    BuiltInScalars.flatMap { binding -> binding.serialNames.map { it to binding.type } }.toMap()
