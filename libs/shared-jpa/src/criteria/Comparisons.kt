package com.strange.jpa.criteria

import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate

/**
 * `=`, against a value.
 *
 * ```kotlin
 * where { this[Order::status] eq Shipped }
 * ```
 *
 * The receiver is an [Expression] rather than a property reference so that the value's type is
 * checked. `KProperty1<T, V>` is covariant in `V`, so the compiler may widen `V` to `Any` and
 * `Purchase::total eq "nope"` type-checks against a `Long` column; `Path<V>` is invariant, so once
 * the property has been turned into one — `root[Purchase::total]` — the value has nowhere to widen
 * to and the same line is a compile error. Use Criteria's own `isNull()` for null — `eq null`
 * renders `= null`, which is never true in SQL.
 */
infix fun <V> Expression<V>.eq(value: V): Predicate = builder.equal(this, value)

/** `=`, against another expression — a second column, a join's column, a function's result. */
infix fun <V> Expression<V>.eq(other: Expression<out V>): Predicate = builder.equal(this, other)

/** `<>`, against a value. Note that in SQL this is false for a null row, not true. */
infix fun <V> Expression<V>.ne(value: V): Predicate = builder.notEqual(this, value)

/** `<>`, against another expression. */
infix fun <V> Expression<V>.ne(other: Expression<out V>): Predicate = builder.notEqual(this, other)

/** `>`. */
infix fun <V : Comparable<V>> Expression<V>.gt(value: V): Predicate = builder.greaterThan(this, value)

/** `>`, against another expression. */
infix fun <V : Comparable<V>> Expression<V>.gt(other: Expression<out V>): Predicate = builder.greaterThan(this, other)

/** `>=`. */
infix fun <V : Comparable<V>> Expression<V>.ge(value: V): Predicate = builder.greaterThanOrEqualTo(this, value)

/** `>=`, against another expression. */
infix fun <V : Comparable<V>> Expression<V>.ge(other: Expression<out V>): Predicate = builder.greaterThanOrEqualTo(this, other)

/** `<`. */
infix fun <V : Comparable<V>> Expression<V>.lt(value: V): Predicate = builder.lessThan(this, value)

/** `<`, against another expression. */
infix fun <V : Comparable<V>> Expression<V>.lt(other: Expression<out V>): Predicate = builder.lessThan(this, other)

/** `<=`. */
infix fun <V : Comparable<V>> Expression<V>.le(value: V): Predicate = builder.lessThanOrEqualTo(this, value)

/** `<=`, against another expression. */
infix fun <V : Comparable<V>> Expression<V>.le(other: Expression<out V>): Predicate = builder.lessThanOrEqualTo(this, other)

/**
 * `between`, both ends included — which is what a Kotlin [ClosedRange] means too.
 *
 * ```kotlin
 * criteria.where(order[Order::total] within 100L..500L)
 * ```
 */
infix fun <V : Comparable<V>> Expression<V>.within(range: ClosedRange<V>): Predicate =
    builder.between(this, range.start, range.endInclusive)
