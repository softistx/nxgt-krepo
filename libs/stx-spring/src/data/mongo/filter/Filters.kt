package com.strange.spring.data.mongo.filter

import com.strange.spring.data.mongo.criteria.all
import com.strange.spring.data.mongo.criteria.any
import com.strange.spring.data.mongo.criteria.containing
import com.strange.spring.data.mongo.criteria.containingIgnoringCase
import com.strange.spring.data.mongo.criteria.eq
import com.strange.spring.data.mongo.criteria.exists
import com.strange.spring.data.mongo.criteria.gt
import com.strange.spring.data.mongo.criteria.gte
import com.strange.spring.data.mongo.criteria.lt
import com.strange.spring.data.mongo.criteria.lte
import com.strange.spring.data.mongo.criteria.ne
import com.strange.spring.data.mongo.criteria.near
import com.strange.spring.data.mongo.criteria.noneOf
import com.strange.spring.data.mongo.criteria.notContaining
import com.strange.spring.data.mongo.criteria.notContainingIgnoringCase
import com.strange.spring.data.mongo.criteria.oneOf
import com.strange.spring.data.mongo.criteria.query
import com.strange.spring.data.mongo.criteria.size
import com.strange.spring.data.mongo.criteria.within
import com.strange.spring.error.ApiException
import org.springframework.data.geo.Circle
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import kotlin.time.Instant

/**
 * A `?filter=` parameter as a Mongo query.
 *
 * ```
 * ?filter=status:eq:PAID;total:gte:100
 * ?filter=or@email:eq:a@b.c;email:eq:d@e.f
 * ```
 *
 * Clauses are `field:operator:value`, separated by `;`, combined with `and` unless the parameter
 * starts with `or@`.
 *
 * **A clause that does not parse is a failure, not a clause that is skipped.** This is the one place
 * where lenience is the wrong instinct: dropping a filter nobody could read returns *more* rows than
 * the caller asked for, and a caller who wrote `status:eq:PIAD` would get the whole collection back
 * rather than an empty page. Sorting can afford to shrug; narrowing cannot.
 */
fun String?.parseFilter(): Query {
    if (isNullOrBlank()) return Criteria().query

    val body = removePrefix(OR_PREFIX)
    val criteria =
        body
            .split(";")
            .filter { it.isNotBlank() }
            .map { clause -> clause.trim().toCriteria() }

    return when {
        criteria.isEmpty() -> Criteria().query
        startsWith(OR_PREFIX) -> any(*criteria.toTypedArray()).query
        else -> all(*criteria.toTypedArray()).query
    }
}

private const val OR_PREFIX = "or@"

/** `field:operator:value`, anchored — see the note on [parseFilter] about scanning versus matching. */
private val CLAUSE = Regex("""([a-zA-Z_][a-zA-Z0-9_.]*):([a-z!]+):(.+)""", RegexOption.DOT_MATCHES_ALL)

private fun String.toCriteria(): Criteria {
    val match = CLAUSE.matchEntire(this) ?: throw invalid(this)
    val (field, token, value) = match.destructured
    val operator = Operator.of(token) ?: throw invalid(this)

    return when (operator) {
        Operator.EQ -> field eq value
        Operator.NE -> field ne value
        Operator.LT -> field lt comparable(value, this)
        Operator.LTE -> field lte comparable(value, this)
        Operator.GT -> field gt comparable(value, this)
        Operator.GTE -> field gte comparable(value, this)
        Operator.BEFORE -> field lt instant(value, this)
        Operator.TO -> field lte instant(value, this)
        Operator.AFTER -> field gt instant(value, this)
        Operator.FROM -> field gte instant(value, this)
        Operator.LIKE -> field containing value
        Operator.NOT_LIKE -> field notContaining value
        Operator.LIKE_IGNORE_CASE -> field containingIgnoringCase value
        Operator.NOT_LIKE_IGNORE_CASE -> field notContainingIgnoringCase value
        Operator.IN -> field oneOf value.split(",")
        Operator.NOT_IN -> field noneOf value.split(",")
        Operator.EXISTS -> field exists boolean(value, this)
        Operator.SIZE -> field size (value.toIntOrNull() ?: throw invalid(this))
        Operator.NEAR -> field near point(value, this)
        Operator.WITHIN -> field within circle(value, this)
    }
}

/**
 * What `lt`, `gt`, `lte` and `gte` compare against.
 *
 * A number if it reads as one, a timestamp if it reads as one, and otherwise the text — Mongo orders
 * strings perfectly well, and a version tag or a name is a reasonable thing to bound.
 *
 * The version this came from coerced anything unparseable to `0.0`, so `price:gte:cheap` quietly
 * became `price >= 0` and matched the entire collection. A filter that means something other than
 * what it says is worse than one that fails.
 */
private fun comparable(
    value: String,
    clause: String,
): Any =
    value.toLongOrNull()
        ?: value.toDoubleOrNull()
        ?: runCatching { Instant.parse(value) }.getOrNull()
        ?: value.ifBlank { throw invalid(clause) }

private fun instant(
    value: String,
    clause: String,
): Instant = runCatching { Instant.parse(value) }.getOrElse { throw invalid(clause) }

private fun boolean(
    value: String,
    clause: String,
): Boolean =
    when (value.lowercase()) {
        "true" -> true

        "false" -> false

        // `toBoolean()` answers false for "yes", "1" and every typo, so a caller asking for
        // documents that *have* a field would get the ones that do not.
        else -> throw invalid(clause)
    }

private fun point(
    value: String,
    clause: String,
): Point {
    val (x, y) =
        value.split(",").map { it.toDoubleOrNull() ?: throw invalid(clause) }.takeIf { it.size == 2 }
            ?: throw invalid(clause)
    return Point(x, y)
}

private fun circle(
    value: String,
    clause: String,
): Circle {
    val parts = value.split(",").map { it.toDoubleOrNull() ?: throw invalid(clause) }
    if (parts.size != 3) throw invalid(clause)
    return Circle(Point(parts[0], parts[1]), parts[2])
}

/** The one failure this file raises, so a caller sees the clause it could not read. */
private fun invalid(clause: String) = ApiException.badRequest(KEY_INVALID_FILTER, mapOf("filter" to clause))

/** What an unreadable filter clause is reported as. */
const val KEY_INVALID_FILTER = "filters.invalid"
