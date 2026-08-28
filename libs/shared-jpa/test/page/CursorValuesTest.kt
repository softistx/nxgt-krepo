package com.strange.jpa.page

import com.strange.jpa.JpaPaginationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

private enum class Grade { LOW, HIGH }

/**
 * The cursor codec, one type at a time and with no database in sight.
 *
 * A cursor that does not survive the round trip gives the page *after* it, empty — which looks like
 * the end of the results rather than like a bug, and is why this is a table rather than the two
 * types a pagination spec happens to sort by.
 *
 * It also holds [carriesCursorValue] and [fromText] to each other: the guard is a set and the parser
 * is a `when`, so nothing but this spec stops one growing without the other.
 */
@OptIn(ExperimentalUuidApi::class)
class CursorValuesTest :
    FeatureSpec({

        // One value per branch the parser has, with the boxed and primitive class literals both
        // named where the parser names both.
        val samples: List<Pair<Class<*>, Comparable<*>>> =
            listOf(
                String::class.java to "a value with spaces",
                Long::class.javaObjectType to 9_007_199_254_740_993L,
                Long::class.javaPrimitiveType!! to -1L,
                Int::class.javaObjectType to 42,
                Int::class.javaPrimitiveType!! to -42,
                Short::class.javaObjectType to 7.toShort(),
                Short::class.javaPrimitiveType!! to (-7).toShort(),
                Byte::class.javaObjectType to 3.toByte(),
                Byte::class.javaPrimitiveType!! to (-3).toByte(),
                Double::class.javaObjectType to 1.5,
                Double::class.javaPrimitiveType!! to -1.5,
                Float::class.javaObjectType to 1.5f,
                Float::class.javaPrimitiveType!! to -1.5f,
                Boolean::class.javaObjectType to true,
                Boolean::class.javaPrimitiveType!! to false,
                BigDecimal::class.java to BigDecimal("12345678901234567890.12345"),
                BigInteger::class.java to BigInteger("123456789012345678901234567890"),
                UUID::class.java to UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301"),
                Uuid::class.java to Uuid.parse("3f2504e0-4f89-11d3-9a0c-0305e82c3301"),
                Instant::class.java to Instant.parse("2026-08-28T10:15:30Z"),
                java.time.Instant::class.java to java.time.Instant.parse("2026-08-28T10:15:30Z"),
                LocalDate::class.java to LocalDate.parse("2026-08-28"),
                LocalDateTime::class.java to LocalDateTime.parse("2026-08-28T10:15:30"),
                LocalTime::class.java to LocalTime.parse("10:15:30"),
                OffsetDateTime::class.java to OffsetDateTime.parse("2026-08-28T10:15:30+02:00"),
                ZonedDateTime::class.java to ZonedDateTime.parse("2026-08-28T10:15:30+02:00"),
                Grade::class.java to Grade.HIGH,
            )

        feature("a value the codec carries") {
            scenario("comes back as itself, for every type the parser knows") {
                samples.forEach { (type, value) ->
                    withClue("$type") { fromText(type, asText(value)) shouldBe value }
                }
            }

            scenario("is one the guard also admits, so the set and the when cannot drift") {
                samples.forEach { (type, _) ->
                    withClue("$type") { carriesCursorValue(type) shouldBe true }
                }
            }
        }

        feature("a value the codec does not carry") {
            scenario("is refused by the guard, before a page is ever issued") {
                // The point of the guard: `java.util.Date` is Comparable and mapped happily by
                // Hibernate, so `sortBy` accepts it and only the decoder would have objected.
                carriesCursorValue(java.util.Date::class.java) shouldBe false
                carriesCursorValue(java.time.Duration::class.java) shouldBe false
                carriesCursorValue(java.time.Year::class.java) shouldBe false
            }

            scenario("and by the parser, naming the type rather than the value") {
                shouldThrow<JpaPaginationException> {
                    fromText(java.util.Date::class.java, "whenever")
                }.message shouldContain "cannot be a cursor key"
            }
        }

        feature("text that is not of the type it should be") {
            scenario("says which type it failed to be") {
                shouldThrow<JpaPaginationException> { fromText(Long::class.javaPrimitiveType!!, "not a number") }
                    .message shouldContain "which is not a"
            }

            scenario("including an enum constant that no longer exists") {
                shouldThrow<JpaPaginationException> { fromText(Grade::class.java, "MIDDLING") }
            }
        }
    })
