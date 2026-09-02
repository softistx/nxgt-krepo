package com.softistx.jpa.criteria

import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate

/**
 * `in (…)`.
 *
 * ```kotlin
 * criteria.where(order[Order::status] oneOf listOf(Draft, Placed))
 * ```
 *
 * An empty collection is a predicate that matches nothing, which is what `in ()` means and is
 * usually what the caller wants when a filter list came back empty.
 */
infix fun <V> Expression<V>.oneOf(values: Collection<V>): Predicate = builder.`in`(this, values)

/** The same, spelled for a handful written out. */
fun <V> Expression<V>.oneOf(vararg values: V): Predicate = oneOf(values.toList())

/**
 * `like`, with the pattern written out — `%` for any run of characters, `_` for one.
 *
 * The pattern is a bound parameter, so a `%` inside a user's search term matches literally nowhere
 * near as much trouble as it would interpolated; escaping the wildcards themselves is still the
 * caller's, since only the caller knows whether a `%` was meant.
 */
infix fun Expression<String>.like(pattern: String): Predicate = builder.like(this, pattern)

/** `not like`. */
infix fun Expression<String>.notLike(pattern: String): Predicate = builder.notLike(this, pattern)

/**
 * `like`, ignoring case.
 *
 * Hibernate renders it as `lower(x) like lower(?)` on databases without a case-insensitive operator,
 * which is correct and does not use an index on `x`. A functional index on `lower(x)` is the fix,
 * and it belongs in the schema rather than here.
 */
infix fun Expression<String>.ilike(pattern: String): Predicate = builder.ilike(this, pattern)
