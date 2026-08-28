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
 *
 * **Every member here is one line onto its [Expression] twin, and that is deliberate.** These used
 * to call `HibernateCriteriaBuilder` directly, in parallel with `Comparisons` and `Matching` —
 * fifteen operators whose semantics had to be changed in two places with nothing to notice when they
 * were not. The copy had already drifted: `eq` and `ne` had both a value and an expression form
 * while `gt`, `ge`, `lt` and `le` had only the value one, so `this[Purchase::total] gt
 * this[Purchase::id]` compiled and `Purchase::total gt this[Purchase::id]` did not, for no reason a
 * caller could work out. Delegating makes the property form sugar over the expression form rather
 * than a second implementation of it, and the four missing overloads came for free.
 */
@JpaDsl
sealed interface Filters<T : Any> : Paths<T> {
    /** `=`. Use [isNull] for null; `eq null` renders `= null`, which is never true in SQL. */
    infix fun <V> KMutableProperty1<T, V>.eq(value: V): Predicate = this@Filters[this] eq value

    /** `=`, against an expression — another column, a join's column, a function's result. */
    infix fun <V> KMutableProperty1<T, V>.eq(other: Expression<out V>): Predicate = this@Filters[this] eq other

    /** `<>`. False for a null row in SQL, not true. */
    infix fun <V> KMutableProperty1<T, V>.ne(value: V): Predicate = this@Filters[this] ne value

    /** `<>`, against an expression. */
    infix fun <V> KMutableProperty1<T, V>.ne(other: Expression<out V>): Predicate = this@Filters[this] ne other

    /** `>`. */
    infix fun <V : Comparable<V>> KMutableProperty1<T, V>.gt(value: V): Predicate = this@Filters[this] gt value

    /** `>`, against an expression. */
    infix fun <V : Comparable<V>> KMutableProperty1<T, V>.gt(other: Expression<out V>): Predicate = this@Filters[this] gt other

    /** `>=`. */
    infix fun <V : Comparable<V>> KMutableProperty1<T, V>.ge(value: V): Predicate = this@Filters[this] ge value

    /** `>=`, against an expression. */
    infix fun <V : Comparable<V>> KMutableProperty1<T, V>.ge(other: Expression<out V>): Predicate = this@Filters[this] ge other

    /** `<`. */
    infix fun <V : Comparable<V>> KMutableProperty1<T, V>.lt(value: V): Predicate = this@Filters[this] lt value

    /** `<`, against an expression. */
    infix fun <V : Comparable<V>> KMutableProperty1<T, V>.lt(other: Expression<out V>): Predicate = this@Filters[this] lt other

    /** `<=`. */
    infix fun <V : Comparable<V>> KMutableProperty1<T, V>.le(value: V): Predicate = this@Filters[this] le value

    /** `<=`, against an expression. */
    infix fun <V : Comparable<V>> KMutableProperty1<T, V>.le(other: Expression<out V>): Predicate = this@Filters[this] le other

    /** `between`, both ends included — which is what a Kotlin [ClosedRange] means too. */
    infix fun <V : Comparable<V>> KMutableProperty1<T, V>.within(range: ClosedRange<V>): Predicate = this@Filters[this] within range

    /** `in (…)`. An empty collection matches nothing, which is what an empty filter list means. */
    infix fun <V> KMutableProperty1<T, V>.oneOf(values: Collection<V>): Predicate = this@Filters[this] oneOf values

    /** The same, for a handful written out. */
    fun <V> KMutableProperty1<T, V>.oneOf(vararg values: V): Predicate = oneOf(values.toList())

    /** `like` — `%` for any run of characters, `_` for one. The pattern is bound, not pasted. */
    infix fun KMutableProperty1<T, String>.like(pattern: String): Predicate = this@Filters[this] like pattern

    /** `not like`. */
    infix fun KMutableProperty1<T, String>.notLike(pattern: String): Predicate = this@Filters[this] notLike pattern

    /** `like`, ignoring case. Rendered as `lower(x) like lower(?)` where the database has no operator. */
    infix fun KMutableProperty1<T, String>.ilike(pattern: String): Predicate = this@Filters[this] ilike pattern

    /** `is null` — the one comparison [eq] cannot make, since `= null` is never true. */
    fun KMutableProperty1<T, *>.isNull(): Predicate = this@Filters[this].isNull()

    /** `is not null`. */
    fun KMutableProperty1<T, *>.isNotNull(): Predicate = this@Filters[this].isNotNull()
}
