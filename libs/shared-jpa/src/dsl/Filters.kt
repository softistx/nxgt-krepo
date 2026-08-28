package com.strange.jpa.dsl

import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import kotlin.reflect.KMutableProperty1

/**
 * Predicates written straight off a property: `Purchase::total gt 100L`.
 *
 * ```kotlin
 * where { (Purchase::total gt 100L) and (Purchase::reference like "P-%") }
 * ```
 *
 * **The receiver is `KMutableProperty1`, not `KProperty1`, and that is the whole design.**
 * `KProperty1<T, out V>` is covariant in the value, so the compiler is free to widen `V` to `Any`
 * and `Purchase::total eq "nope"` type-checks against a `Long` column — the mistake a DSL exists to
 * catch, passed through to a runtime failure inside Hibernate. `KMutableProperty1<T, V>` declares
 * `V` invariantly, because it has a setter to accept one, so the same line is a compile error:
 * *actual type is 'String', but 'Long' was expected*. Kotlin's standard library solves the same
 * problem with `@OnlyInputTypes`, which is internal to it.
 *
 * An entity's attributes are `var` — Hibernate needs to write them — so this is the ordinary case
 * and not a restriction. A `val` attribute, or anything reached through a join or a function, goes
 * through [Paths.get] and the operators on [Expression] instead: `this[Thing::checksum] eq value`,
 * `buyer[Buyer::name] eq "ada"`, `lower(this[Buyer::name]) eq term`.
 */
@JpaDsl
sealed interface Filters<T : Any> : Paths<T> {
    /** `=`. Use [isNull] for null; `eq null` renders `= null`, which is never true in SQL. */
    infix fun <V> KMutableProperty1<T, V>.eq(value: V): Predicate = builder.equal(this@Filters[this], value)

    /** `=`, against an expression — another column, a join's column, a function's result. */
    infix fun <V> KMutableProperty1<T, V>.eq(other: Expression<out V>): Predicate = builder.equal(this@Filters[this], other)

    /** `<>`. False for a null row in SQL, not true. */
    infix fun <V> KMutableProperty1<T, V>.ne(value: V): Predicate = builder.notEqual(this@Filters[this], value)

    /** `<>`, against an expression. */
    infix fun <V> KMutableProperty1<T, V>.ne(other: Expression<out V>): Predicate = builder.notEqual(this@Filters[this], other)

    /** `>`. */
    infix fun <V : Comparable<in V>> KMutableProperty1<T, V>.gt(value: V): Predicate = builder.greaterThan(this@Filters[this], value)

    /** `>=`. */
    infix fun <V : Comparable<in V>> KMutableProperty1<T, V>.ge(value: V): Predicate =
        builder.greaterThanOrEqualTo(this@Filters[this], value)

    /** `<`. */
    infix fun <V : Comparable<in V>> KMutableProperty1<T, V>.lt(value: V): Predicate = builder.lessThan(this@Filters[this], value)

    /** `<=`. */
    infix fun <V : Comparable<in V>> KMutableProperty1<T, V>.le(value: V): Predicate = builder.lessThanOrEqualTo(this@Filters[this], value)

    /** `between`, both ends included — which is what a Kotlin [ClosedRange] means too. */
    infix fun <V : Comparable<in V>> KMutableProperty1<T, V>.within(range: ClosedRange<V>): Predicate =
        builder.between(this@Filters[this], range.start, range.endInclusive)

    /** `in (…)`. An empty collection matches nothing, which is what an empty filter list means. */
    infix fun <V> KMutableProperty1<T, V>.oneOf(values: Collection<V>): Predicate = builder.`in`(this@Filters[this], values)

    /** The same, for a handful written out. */
    fun <V> KMutableProperty1<T, V>.oneOf(vararg values: V): Predicate = oneOf(values.toList())

    /** `like` — `%` for any run of characters, `_` for one. The pattern is bound, not pasted. */
    infix fun KMutableProperty1<T, String>.like(pattern: String): Predicate = builder.like(this@Filters[this], pattern)

    /** `not like`. */
    infix fun KMutableProperty1<T, String>.notLike(pattern: String): Predicate = builder.notLike(this@Filters[this], pattern)

    /** `like`, ignoring case. Rendered as `lower(x) like lower(?)` where the database has no operator. */
    infix fun KMutableProperty1<T, String>.ilike(pattern: String): Predicate = builder.ilike(this@Filters[this], pattern)

    /** `is null` — the one comparison [eq] cannot make, since `= null` is never true. */
    fun KMutableProperty1<T, *>.isNull(): Predicate = builder.isNull(this@Filters[this])

    /** `is not null`. */
    fun KMutableProperty1<T, *>.isNotNull(): Predicate = builder.isNotNull(this@Filters[this])
}
