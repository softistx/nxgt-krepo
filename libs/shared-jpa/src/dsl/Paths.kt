package com.strange.jpa.dsl

import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Path
import kotlin.reflect.KProperty1

/**
 * Somewhere paths hang off — the query's root, or a join taken from it.
 *
 * ```kotlin
 * val customer = join(Order::customer)
 * where { (this[Order::total] gt 100L) and (customer[Customer::name] eq "ada") }
 * ```
 *
 * **Indexing rather than `Order::total eq 100L`, and the reason is not taste.** `KProperty1<T, V>`
 * is covariant in `V`, so the compiler is free to widen `V` to `Any` and `Order::total eq "nope"`
 * type-checks against a `Long` column — the mistake the DSL exists to catch, passed straight through
 * to a runtime failure inside Hibernate. `Path<V>` is invariant, so once the property has been
 * turned into one the value has nowhere to widen to and the same line is a compile error. Kotlin's
 * standard library solves this with `@OnlyInputTypes`, which is internal to it.
 *
 * The same indexing works on a join, which is the other half of the reason: one idiom, whether the
 * attribute belongs to the entity being selected or to something it points at.
 */
@JpaDsl
sealed interface Paths<T : Any> {
    /** The root or join the paths are taken from. */
    val from: From<*, T>

    /** The path to an attribute — the DSL's primitive, and the only typed way in. */
    operator fun <V> get(property: KProperty1<T, V>): Path<V> = from.get(property.name)

    /**
     * Joins a to-one association, and answers with something to index.
     *
     * The property may be nullable — a to-one association usually is in Kotlin, and an inner join
     * over one is exactly how a query says *only the ones that have a customer*. The join is on the
     * entity either way, so the nullability is dropped from what comes back.
     *
     * Held as a value rather than scoped to a lambda, so one join serves the `where`, the `orderBy`
     * and — from the next slice — the projection, instead of being re-declared and re-joined for
     * each. A second `join` of the same attribute is a second join in the SQL.
     */
    fun <V : Any> join(
        property: KProperty1<T, V?>,
        type: JoinType = JoinType.INNER,
    ): JoinScope<T, V> = JoinScope(from.join(property.name, type))

    /**
     * Joins a to-many association, once per element.
     *
     * The element type comes out of `KProperty1<T, out Collection<E>>` and needs no reflection at
     * runtime — the compiler already knows what a `List<Line>` holds. A row per element is what a
     * join means, so a query that selects the owning entity through one wants [SelectScope.distinct]
     * unless it wants duplicates.
     */
    fun <E : Any> joinEach(
        property: KProperty1<T, out Collection<E>>,
        type: JoinType = JoinType.INNER,
    ): JoinScope<T, E> = JoinScope(from.join(property.name, type))
}
