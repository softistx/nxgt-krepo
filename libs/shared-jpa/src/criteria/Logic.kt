package com.strange.jpa.criteria

import jakarta.persistence.criteria.Predicate

/**
 * `and`.
 *
 * ```kotlin
 * criteria.where((order[Order::total] gt 100L) and (order[Order::status] eq Shipped))
 * ```
 *
 * The parentheses are Kotlin's: infix calls all bind at the same precedence, so `a gt 1 and b eq 2`
 * would parse as `a gt (1 and b) eq 2` and not compile. Criteria's own `where(vararg Predicate)`
 * `and`s its arguments for free and needs none of them.
 */
infix fun Predicate.and(other: Predicate): Predicate = builder.and(this, other)

/** `or`. */
infix fun Predicate.or(other: Predicate): Predicate = builder.or(this, other)

/**
 * Every one of them, `and`ed — for a list of conditions built up before the query was.
 *
 * Empty means true, which is what `and` over nothing means and what a caller filtering by an empty
 * set of filters wants.
 */
fun all(predicates: List<Predicate>): Predicate? = if (predicates.isEmpty()) null else predicates.reduce(Predicate::and)

/** Any one of them, `or`ed. Empty means nothing matches, so there is nothing to add to the query. */
fun any(predicates: List<Predicate>): Predicate? = if (predicates.isEmpty()) null else predicates.reduce(Predicate::or)
