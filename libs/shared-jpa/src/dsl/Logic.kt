package com.strange.jpa.dsl

import jakarta.persistence.criteria.Predicate

/**
 * `and`.
 *
 * ```kotlin
 * where { (this[Order::total] gt 100L) and (this[Order::status] eq Shipped) }
 * ```
 *
 * The parentheses are Kotlin's, not this DSL's: infix calls all bind at the same precedence, so
 * `a gt 1 and b eq 2` would parse as `a gt (1 and b) eq 2` and not compile. Several `where` blocks
 * are `and`ed for free and need none of them.
 */
infix fun Predicate.and(other: Predicate): Predicate = builder.and(this, other)

/** `or`. */
infix fun Predicate.or(other: Predicate): Predicate = builder.or(this, other)

/** `not`. */
operator fun Predicate.not(): Predicate = builder.not(this)

/**
 * Every one of them, `and`ed — for a list of conditions built up before the query was.
 *
 * Empty means true, which is what `and` over nothing means and what a caller filtering by an empty
 * set of filters wants.
 */
fun all(predicates: List<Predicate>): Predicate? = if (predicates.isEmpty()) null else predicates.reduce(Predicate::and)

/** Any one of them, `or`ed. Empty means nothing matches, so there is nothing to add to the query. */
fun any(predicates: List<Predicate>): Predicate? = if (predicates.isEmpty()) null else predicates.reduce(Predicate::or)
