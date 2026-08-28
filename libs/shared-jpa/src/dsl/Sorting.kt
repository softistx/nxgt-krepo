package com.strange.jpa.dsl

import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Order

/**
 * Ascending, with nulls wherever the database puts them by default.
 *
 * ```kotlin
 * orderBy { asc(this[Order::placedAt]) }
 * ```
 */
fun asc(expression: Expression<*>): Order = expression.builder.asc(expression)

/** Descending, the same way. */
fun desc(expression: Expression<*>): Order = expression.builder.desc(expression)

/**
 * Ascending, saying where the nulls go.
 *
 * Worth spelling out whenever it matters, because the default is the *database's* and they disagree:
 * Postgres sorts nulls last ascending, MySQL sorts them first. A page boundary that lands in the
 * nulls is a different row on each.
 */
fun asc(
    expression: Expression<*>,
    nullsFirst: Boolean,
): Order = expression.builder.asc(expression, nullsFirst)

/** Descending, saying where the nulls go. */
fun desc(
    expression: Expression<*>,
    nullsFirst: Boolean,
): Order = expression.builder.desc(expression, nullsFirst)
