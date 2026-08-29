package com.strange.jpa.criteria

import jakarta.persistence.criteria.Expression

/**
 * The aggregates, for a projection with a `groupBy` — or without one, over the whole result.
 *
 * ```kotlin
 * val criteria = session.createQuery<Tally>()
 * val purchase = criteria.from(Purchase::class.java)
 * val buyer = purchase.join(Purchase::customer)
 *
 * criteria.multiselect(buyer[Buyer::name], count(purchase[Purchase::id]))
 * criteria.groupBy(buyer[Buyer::name])
 * criteria.having(count(purchase[Purchase::id]) gt 1L)
 * ```
 *
 * `JpaQuery.count()` is a different thing and worth not confusing with this one: that rewrites the
 * whole query into a count of its rows, which is what a pager needs. This is a column in a row.
 */
fun count(value: Expression<*>): Expression<Long> = value.builder.count(value)

/** How many distinct values, nulls not counted. */
fun countDistinct(value: Expression<*>): Expression<Long> = value.builder.countDistinct(value)

/** `sum`, null over no rows rather than zero — which is SQL's answer and surprises everyone once. */
fun <N : Number> sum(value: Expression<N>): Expression<N> = value.builder.sum(value)

/** `avg`, a double whatever went in. */
fun avg(value: Expression<out Number>): Expression<Double> = value.builder.avg(value)

/** The smallest of the numbers. [least] is the one for dates and strings. */
fun <N : Number> min(value: Expression<N>): Expression<N> = value.builder.min(value)

/** The largest of the numbers. [greatest] is the one for dates and strings. */
fun <N : Number> max(value: Expression<N>): Expression<N> = value.builder.max(value)

/** The smallest of anything ordered — a date, a string. */
fun <V : Comparable<V>> least(value: Expression<V>): Expression<V> = value.builder.least(value)

/** The largest of anything ordered. */
fun <V : Comparable<V>> greatest(value: Expression<V>): Expression<V> = value.builder.greatest(value)
