package com.softistx.spring.web

import kotlinx.serialization.Serializable
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.queryParamOrNull

/**
 * One `property:direction` pair from a `?sort=` parameter.
 *
 * **Deliberately not Spring Data's `Sort`.** That type lives in spring-data-commons, and a request
 * helper returning it would put a data-access library on the classpath of every consumer that only
 * wanted to read a query parameter — including the ones talking to Postgres, or to nothing at all.
 * The store-specific packages translate this into whatever their store sorts by; parsing a query
 * string is not a data-access concern and does not need a data-access dependency to happen.
 */
@Serializable
data class SortOrder(
    val property: String,
    val descending: Boolean = false,
)

/**
 * The orders in a `?sort=` parameter: `sort=name:ASC,createdAt:DESC`.
 *
 * A clause the pattern does not accept is dropped rather than rejected, so one malformed clause does
 * not lose the sensible ones beside it — and a `?sort=` nobody can parse is an unsorted result,
 * which is what a caller who omitted the parameter gets anyway.
 */
fun String?.parseSort(): List<SortOrder> =
    orEmpty()
        .split(",")
        .mapNotNull { clause -> SORT_CLAUSE.matchEntire(clause.trim()) }
        .map { match ->
            val (property, direction) = match.destructured
            // `==`, not `===`. The original of this code compared the destructured direction by
            // reference, so every clause fell through to descending and no test noticed, because
            // both orderings return the same rows.
            SortOrder(property, descending = direction.uppercase() == "DESC")
        }

/**
 * One clause, whole.
 *
 * `matchEntire` per comma-separated token rather than `findAll` over the whole parameter, because a
 * scan finds a legal property *inside* an illegal one: `${'$'}where:ASC` contains `where:ASC`, and the
 * scanning version of this sorted by `where` quite happily — which is a Mongo operator, not a field.
 * The property reaches a query as a field name, so the pattern that accepts it is deliberately
 * narrow and has to match end to end.
 */
private val SORT_CLAUSE = Regex("""([a-zA-Z_][a-zA-Z0-9_.]*):(?i:(ASC|DESC))""")

/** The `?sort=` parameter of this request, parsed. Empty when it is absent. */
val ServerRequest.sort: List<SortOrder> get() = queryParamOrNull("sort").parseSort()
