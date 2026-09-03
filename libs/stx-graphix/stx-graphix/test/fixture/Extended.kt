package com.softistx.graphix.fixture

import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.QueryMapping
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonElement
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.util.Locale
import kotlin.time.Duration

/**
 * One echo per extended scalar. Echoing is the point: an argument and a field of the same type
 * exercise `parseValue`/`parseLiteral` and `serialize` in one query, and a scalar that only ever
 * went one way is a scalar half of which is untested.
 */
class ExtendedScalarQueries {
    @QueryMapping
    fun date(
        @Argument value: LocalDate,
    ): LocalDate = value

    @QueryMapping
    fun time(
        @Argument value: LocalTime,
    ): LocalTime = value

    @QueryMapping
    fun timestamp(
        @Argument value: LocalDateTime,
    ): LocalDateTime = value

    @QueryMapping
    fun lasts(
        @Argument value: Duration,
    ): Duration = value

    @QueryMapping
    fun amount(
        @Argument value: BigDecimal,
    ): BigDecimal = value

    @QueryMapping
    fun huge(
        @Argument value: BigInteger,
    ): BigInteger = value

    @QueryMapping
    fun tiny(
        @Argument value: Short,
    ): Short = value

    @QueryMapping
    fun octet(
        @Argument value: Byte,
    ): Byte = value

    @QueryMapping
    fun letter(
        @Argument value: Char,
    ): Char = value

    @QueryMapping
    fun link(
        @Argument value: URI,
    ): URI = value

    @QueryMapping
    fun language(
        @Argument value: Locale,
    ): Locale = value

    @QueryMapping
    fun payload(
        @Argument value: JsonElement,
    ): JsonElement = value
}

/** A field typed by a bounded scalar has to be declared in SDL — there is no Kotlin `PositiveInt`. */
class QuantityQueries {
    @QueryMapping
    fun quantity(
        @Argument value: Int,
    ): Int = value
}
